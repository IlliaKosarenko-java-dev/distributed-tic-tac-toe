# Distributed Tic Tac Toe

Two Spring Boot microservices play tic-tac-toe against each other while a browser watches the
game unfold, move by move. Nobody clicks a square.

- **Game Engine Service** owns the rules: board state, move validation, win and draw detection.
- **Game Session Service** owns sessions: it chooses moves for both players and drives the game
  by calling the engine over HTTP.
- **UI** renders the board live from a server-sent event stream.
- **API Gateway** gives the browser a single origin, so there is no CORS configuration anywhere.

---

## Quick start

```bash
docker compose up --build
```

Then open **http://localhost:8080** and press *Start Simulation*.

That is the whole setup. A clean clone needs only Docker — the images build the services from
source, so no JDK or Maven is required to run the system.

### Or drive it from the command line

```bash
SID=$(curl -s -X POST localhost:8080/api/sessions \
        -H 'Content-Type: application/json' \
        -d '{"xStrategy":"RULE_BASED","oStrategy":"RANDOM","moveDelayMs":0}' \
      | python3 -c 'import sys,json;print(json.load(sys.stdin)["sessionId"])')

curl -s -X POST "localhost:8080/api/sessions/$SID/simulate?mode=sync"
```

```
status  : FINISHED
outcome : X_WON
moves   : X@4 O@5 X@0 O@6 X@8

          X . .
          . X O
          O . X
```

The engine, asked independently, agrees:

```
GET /api/games/{id}  ->  status=X_WON moveCount=5 version=5 winningLine=[0,4,8]
```

### Watch the moves arrive

```bash
curl -N "localhost:8080/api/sessions/$SID/events"
```

```
event: snapshot   {"status":"CREATED","board":[null,...],"moveCount":0}
event: status     {"status":"RUNNING"}
event: move       {"seq":1,"player":"X","position":4,"board":[...]}
event: move       {"seq":2,"player":"O","position":5,"board":[...]}
...
event: finished   {"outcome":"X_WON","moveCount":5,"winningLine":[0,4,8]}
```

---

## Running without Docker

Requires JDK 17+. Each service runs with **no infrastructure at all** on its default profile,
which keeps an in-memory store behind the same interface MongoDB implements.

```bash
mvn -pl game-engine-service  spring-boot:run     # :8081
mvn -pl game-session-service spring-boot:run     # :8082
python3 -m http.server 3000 --directory ui       # the UI has no build step
UI_URL=http://localhost:3000 mvn -pl api-gateway spring-boot:run   # :8080
```

To run against MongoDB instead, start one and add `-Dspring-boot.run.profiles=mongo`.

---

## Testing

```bash
mvn verify
```

**325 tests.** Docker is required only for the tests that use Testcontainers.

| Module | Tests | What they cover |
|---|---:|---|
| `game-engine-service` | 142 | Rules (all 8 winning lines × both players, draws, illegal moves), the HTTP error contract, both store adapters against a shared contract test, and a 9-thread concurrency test |
| `game-session-service` | 162 | Session lifecycle, both move strategies, the engine client's retry and circuit-breaker behaviour against WireMock, the simulation loop, and the web contract |
| `api-gateway` | 4 | Route definitions, including that the UI catch-all cannot outrank `/api/**` |
| `integration-tests` | 17 | Both services on random ports against real MongoDB — full games, the SSE stream, and durability across a restart |

Three tests are worth knowing about, because they encode the properties everything else rests on:

- **`theMoveHistoryReplaysToTheSameBoardBothServicesReport`** — replaying the session's move log
  must reconstruct the board the *engine* holds. Any drift between the two services breaks it.
- **`onlyOneOfManyConcurrentWritersAtTheSameVersionWins`** — nine threads submit a move at the
  same version; exactly one succeeds. Runs against both the in-memory and MongoDB adapters.
- **`aRuleBasedPlayerNeverLosesToItself`** — two rule-based players must always draw. Perfect
  play has exactly one outcome, which makes this an oracle rather than a smoke test.

---

## Architecture

```
                    ┌──────────────────────────────────────────┐
  browser ─────────►│  api-gateway  :8080   — routing only     │
                    │    /  → ui          /api/**  → services  │
                    └───┬─────────────┬──────────────┬─────────┘
                        │             │              │
          ┌─────────────▼──┐  ┌───────▼────────────┐ │
          │ ui   nginx :80 │  │ game-session-svc   │ │
          │ static assets  │  │        :8082       │ │
          │ no build step  │  │ • session lifecycle│ │
          └────────────────┘  │ • move strategies  │ │
                              │ • simulation loop  │ │
                              │ • SSE registry     │ │
                              └───┬────────────┬───┘ │
                                  │            │REST │
                                  │         ┌──▼─────▼───────────────┐
                                  │         │ game-engine-svc :8081  │
                                  │         │ • Game aggregate       │
                                  │         │ • move validation      │
                                  │         │ • win / draw detection │
                                  │         │ • optimistic CAS       │
                                  │         └──────────┬─────────────┘
                                  │                    │
                            ┌─────▼────────────────────▼─────┐
                            │  mongodb :27017                │
                            │    db tictactoe-sessions       │ ← session svc only
                            │    db tictactoe-games          │ ← engine svc only
                            └────────────────────────────────┘
```

Each service owns one database and connects with a user scoped to that database alone, so
neither can read the other's collections. That is enforced, not conventional:

```
$ mongosh -u session ... --eval 'db.getSiblingDB("tictactoe-games").games.countDocuments()'
MongoServerError: not authorized on tictactoe-games to execute command
```

### Package layout

Both services use the same conventional layering:

```
domain/       the rules and the aggregate — zero framework imports
controller/   HTTP in, plus the RFC-7807 error mapping
dto/          request and response shapes
service/      orchestration; holds no rules of its own
mapper/       domain ↔ stored shape
repository/   the port, with inmemory/ and mongo/ adapters behind it
```

---

## API

Everything below is reachable through the gateway under `/api`.

### Session service

```
POST /sessions                        create a session   -> 201
     { "xStrategy": "RULE_BASED", "oStrategy": "RANDOM", "moveDelayMs": 400 }

POST /sessions/{id}/simulate          run it             -> 202  (async, streams over SSE)
POST /sessions/{id}/simulate?mode=sync                   -> 200  (plays out, then responds)
                                                         -> 409  already claimed by a runner

GET  /sessions/{id}                   session, board and full move history
GET  /sessions?status=&outcome=&xStrategy=&limit=        newest first
GET  /sessions/{id}/events            text/event-stream: snapshot | status | move | finished | error
```

### Engine service

```
POST /games                           create a game (idempotent on gameId)
GET  /games/{id}                      current board and status
POST /games/{id}/move                 { "player": "X", "position": 4, "expectedVersion": 3 }
```

Both services return RFC-7807 problem responses carrying a machine-readable `code`:

```json
{ "type": "https://flamingo.example/errors/cell-occupied",
  "title": "Move rejected", "status": 409,
  "detail": "Position 4 is already taken by X",
  "code": "CELL_OCCUPIED", "instance": "/games/abc/move" }
```

The `code` matters more than the status: the session service has to tell a permanent verdict
(`CELL_OCCUPIED` — do not retry) from a transient failure (`ENGINE_UNAVAILABLE` — retry), and
409 alone does not say which.

Interactive API docs: `/swagger-ui.html` on each service.

---

## Design decisions

### MongoDB, where the brief suggested H2

The brief allows "an in-memory data structure or an H2 in-memory database". This uses MongoDB
instead, and the deviation is deliberate rather than incidental.

Neither entity has relational structure — a game and a session are each one self-contained
document — so the relational half of H2 buys nothing. What a document store does buy is the
thing that makes the *distributed* part of this exercise real: MongoDB guarantees
single-document atomicity, so a move can be a compare-and-swap that holds **across engine
replicas**, not merely within one JVM.

The requirement is still honoured: every service runs on an `in-memory` profile by default,
with no database, no Docker, and no configuration. Both adapters sit behind one interface and
pass the same contract test, so the choice of store cannot change how the engine behaves.

### Optimistic concurrency, not locks

Applying a move is read → modify → write, and the gap between the read and the write is where
a lost update lives. Rather than lock, every write carries the version it read at:

```
query:  { "_id": "…", "version": 0 }
update: { …, "version": 1 }
```

The version is part of the `WHERE` clause, so a writer whose snapshot is stale matches zero
documents and is told so (`409 VERSION_CONFLICT`) instead of silently erasing someone's move.

`synchronized` would have been simpler and wrong: it coordinates threads inside one JVM, and
two engine replicas each have their own lock. The check has to live in the one thing both
replicas share. The session service uses the same idea for a different question — the
transition from `CREATED` to `RUNNING` is itself a conditional update, so two callers racing to
start one session cannot both win.

Nothing is locked and nobody waits. Conflicts are rare here (one runner drives one game), so
detecting them beats preventing them.

### The two services share no Java types

`Player`/`Mark` and `GameStatus`/`GameOutcome` are deliberately duplicated, and line detection
appears in both.

The contract between these services is JSON, not Java. Sharing classes would create the
illusion of a shared type system that does not exist on the wire: renaming `Player` to `Mark`
changes no payload yet would break a shared-class build, while renaming `X_WON` breaks the wire
format and the compiler is silent either way. A compile-time dependency would also end
independent deployability — the property the split exists to buy.

What is duplicated is small (two enums and a nine-line loop) and the two uses differ: the
engine's line detection is *authoritative*, the session's is a lookahead for choosing a move.
The agreement between them is checked where it actually lives — in the client's mapper, and in
integration tests that play real games.

### Retries cover 5xx, never 4xx

The engine client classifies every failure once, into one of two types:

```yaml
retry-exceptions:  [ EngineUnavailableException ]   # 5xx, timeouts, refused connections
ignore-exceptions: [ EngineRejectedException ]      # 4xx — the engine considered it and said no
```

Replaying a `CELL_OCCUPIED` produces the same refusal three times and calls the result
resilience. Because the classification happens in one place and the configuration names a
single exception class, it is not a rule anyone has to remember at a call site.

Refusals are also excluded from the circuit breaker: an engine healthy enough to have an
opinion is not an engine that is down, and a session full of legitimate rejections must not
take it offline for everyone.

### Server-sent events, not WebSocket

The UI only ever *receives* on this channel — starting a game is a normal `POST`. WebSocket's
bidirectionality would be capability paid for and never used, and reconnection would be ours to
write. SSE is plain HTTP, and `EventSource` reconnects on its own.

Every stream opens with a `snapshot` event, so a client that attaches mid-game — or reconnects
after a drop — sees the current board immediately instead of waiting for a move that may never
come.

One constraint this depends on: nothing between the browser and the service may buffer the
response. A compression or buffering filter turns a live game into one update at the end, which
looks like "SSE is broken" rather than "a filter is buffering". The gateway is configured with
no response timeout for exactly this reason.

### The UI is its own deployable

The browser needs one origin; it does not need the files to live inside the gateway jar. The
gateway routes `/` to an nginx container and `/api/**` to the services, so a change to the
board's stylesheet rebuilds a small static image rather than redeploying the component that
carries all of the system's traffic. nginx also serves static content properly — etags, gzip,
cache headers — which Spring Cloud Gateway would not have.

### Sync mode exists for tests, not for the UI

`?mode=sync` plays the whole game before responding and ignores the move delay. The async path
with a delay is what makes a game watchable; a test that waits out nine real pauses and polls
for completion is slow and flaky. Both paths run the same loop.

---

## What I would change at production scale

- **Move coordination onto a log.** Synchronous REST between the services is easy to follow and
  easy to test, but it couples the session's progress to the engine's availability. Publishing
  moves to Kafka and having the engine consume them would let a game survive an engine restart
  mid-play rather than ending as `FAILED`.
- **Idempotency keys on every move**, so a retried request the engine already applied is
  recognised as a duplicate instead of colliding with its own earlier write.
- **Contract tests (Pact) between the services.** Today the agreement on `X_WON` and `"X"` is
  checked by integration tests that happen to exercise it; a consumer-driven contract would
  fail the *engine's* build when it breaks its consumer.
- **Separate database instances per service.** One instance with scoped users keeps the access
  boundary real, but the failure domain is still shared.

## Not in scope

Authentication, human players, and multi-region concerns. Eureka was considered and left out:
with two services the gateway's static routes are sufficient, and a discovery server would be
configuration without a question to answer.
