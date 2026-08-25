# Distributed Tic Tac Toe — Implementation Plan

> Home assignment: two Spring Boot microservices play tic-tac-toe against each other,
> a browser UI watches the game unfold live.

---

## 1. Requirement traceability

| # | Assignment requirement | Where it is satisfied |
|---|---|---|
| 1 | Game Engine: board management, move validation, outcome detection | `game-engine-service`, pure `Game` domain aggregate |
| 2 | `POST /games/{gameId}/move`, `GET /games/{gameId}` | `GameController` |
| 3 | Session service: session mgmt, automated move generation, coordination | `game-session-service`, `MoveStrategy` + `SimulationRunner` |
| 4 | `POST /sessions`, `POST /sessions/{id}/simulate`, `GET /sessions/{id}` | `SessionController` |
| 5 | UI: start button, live 3x3 board, status, move log, errors | `ui/` static assets on nginx, routed at `/` by the gateway |
| 6 | In-memory state | `in-memory` Spring profile (default for tests / no-Docker run) |
| 7 | Robust error handling | RFC-7807 `ProblemDetail` + `@RestControllerAdvice` in both services |
| 8 | Integration tests of the full flow | `integration-tests` module |
| 9 | *Optional* — concurrency handling | Atomic single-document CAS in both services (see §7) |
| 10 | *Optional* — API gateway | `api-gateway` (Spring Cloud Gateway) |
| 11 | *Optional* — persistence & recovery | MongoDB: games and sessions both survive restart and stay queryable |
| 12 | *Optional* — real-time updates | SSE stream of moves as they are played |

**Deliberate deviation to justify in the README:** the brief says "in-memory data structure or
H2". We use MongoDB instead, behind repository ports, and keep an `in-memory` profile so the
project still runs and `mvn verify` still passes with no Docker. Rationale: neither entity has
relational structure — a game and a session are each one self-contained document — and a
document store is what makes the *distributed* concurrency guarantee and the durable,
queryable result history real rather than simulated.

---

## 2. Technology decisions

| Concern | Choice | Why |
|---|---|---|
| Language / runtime | Java 17 | Already installed; Boot 3.x baseline |
| Framework | Spring Boot 3.3.x | Named in the brief; `ProblemDetail`, `RestClient`, `@HttpExchange` built in |
| Build | Maven multi-module | Maven 3.9 installed; one `mvn verify` builds and tests everything |
| Persistence | MongoDB 7 (Spring Data MongoDB) | Documents with no cross-entity relations; single-document atomicity gives distributed compare-and-swap for free |
| Store topology | One MongoDB instance per service | Database-per-service; neither service ever reads the other's data |
| Service-to-service | `RestClient` + `@HttpExchange` interface | Declarative, no Feign dependency, easy to stub in tests |
| Resilience | Resilience4j | Retry w/ backoff + circuit breaker + timeouts on engine calls |
| Real-time | SSE (`SseEmitter`) | One-way push is all the UI needs; simpler than WebSocket |
| Gateway | Spring Cloud Gateway | Routing only: `/` → ui, `/api/**` → services. The browser sees one origin, so there is no CORS config anywhere |
| API docs | springdoc-openapi | Browsable contract for the reviewer |
| UI | Static HTML + vanilla JS + CSS grid | No build step, no toolchain risk; this is a backend assignment |
| UI hosting | `nginx:alpine` over a plain `ui/` folder | The UI has no build step, so it is not a Java artifact. Keeps application assets out of the routing layer and lets the UI ship on its own cadence |
| Tests | JUnit 5, MockMvc, WireMock, Testcontainers | Real MongoDB in tests, no in-memory fakes lying to us |
| Packaging | Dockerfile per service + docker-compose | `docker compose up` is the single run command |

Using one storage technology for both services is a deliberate simplification. Adding a second
one would buy nothing here and would cost an extra dependency, an extra container, and an extra
failure mode to test.

---

## 3. Architecture

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
          └────────────────┘  │ • MoveStrategy X/O │ │
                              │ • SimulationRunner │ │
                              │ • SSE registry     │ │
                              └───┬────────────┬───┘ │
                                  │            │REST │
                    ┌─────────────▼──────┐  ┌──▼─────▼───────────────┐
                    │ mongo-session:27018│  │ game-engine-svc :8081  │
                    │ sessions + history │  │ • Game aggregate       │
                    └────────────────────┘  │ • move validation      │
                                            │ • win / draw detection │
                                            │ • optimistic CAS       │
                                            └──────────┬─────────────┘
                                                       │
                                            ┌──────────▼─────────────┐
                                            │ mongo-engine   :27017  │
                                            │ games                  │
                                            └────────────────────────┘
```

The gateway routes; it does not host. Keeping the UI out of the gateway artifact means a
change to the board's stylesheet never triggers a redeploy of the component that carries all
of the system's traffic.

Database-per-service: each service owns its store exclusively, in its own container, and no
other service ever reads it. All cross-service reads go through the REST API.

---

## 4. Repository layout

```
distributed-tic-tac-toe/
├── pom.xml                          # parent: dependencyManagement, plugin config
├── game-engine-service/
│   └── src/main/java/.../engine/
│       ├── domain/                  # Game, Board, Player, GameStatus, Move (+ exception/)
│       │                            #   zero framework imports — the rules stand alone
│       ├── controller/              # GameController, ApiExceptionHandler
│       ├── dto/                     # CreateGameRequest, MoveRequest, GameStateResponse
│       ├── service/                 # GameService, GameCreationResult (+ exception/)
│       ├── mapper/                  # GameDocumentMapper — domain <-> stored shape
│       ├── config/                  # ClockConfiguration
│       └── repository/              # GameRepository (the port), StoredGame
│           ├── inmemory/            #   InMemoryGameRepository        @Profile("in-memory")
│           └── mongo/               #   MongoGameRepository, GameDocument,
│                                    #   SpringDataGameRepository      @Profile("mongo")
├── game-session-service/
│   └── src/main/java/.../session/
│       ├── domain/                  # Session, SessionStatus, MoveRecord
│       ├── controller/              # SessionController, SseController, ExceptionHandler
│       ├── dto/                     # request/response records
│       ├── service/                 # SessionService, SimulationRunner, MoveStrategy(+impls)
│       ├── client/                  # GameEngineClient (@HttpExchange), resilience config
│       └── repository/              # SessionRepository (interface) + Mongo/InMemory impls
├── api-gateway/                     # routing only — holds no application assets
├── ui/                              # plain files, no build step, served by nginx
│   ├── index.html
│   ├── app.js
│   ├── styles.css
│   └── Dockerfile                   # FROM nginx:alpine
├── integration-tests/               # cross-service end-to-end tests
├── docker-compose.yml
├── PLAN.md
└── README.md
```

Conventional layered packaging, so the structure reads the way a Spring reviewer expects. The two
properties the design leans on survive the flattening because they come from the code rather than
the folder names: `domain` has no framework imports, so the rules are unit-testable with no Spring
context; and `repository` exposes an interface with profile-selected implementations, so swapping
the store stays a one-line configuration change. Exceptions live beside the layer that raises them
rather than in a catch-all package.

---

## 5. API contracts

### Game Engine Service (`:8081`)

```
POST /games
  body   { "gameId": "<optional, [A-Za-z0-9_-]{0,64}>", "startingPlayer": "X" }
  201    GameState        (idempotent: existing gameId returns 200 + current state)
  400    VALIDATION_FAILED

GET  /games/{gameId}
  200    GameState
  404    GAME_NOT_FOUND

POST /games/{gameId}/move
  body   { "player": "X", "position": 4, "expectedVersion": 3 }
  200    GameState  (incl. lastMove, winningLine when won)
  400    VALIDATION_FAILED | INVALID_PLAYER | MALFORMED_REQUEST
  404    GAME_NOT_FOUND
  409    CELL_OCCUPIED | NOT_PLAYERS_TURN | GAME_ALREADY_FINISHED | VERSION_CONFLICT

Bean validation bounds `position` to 0..8 and `expectedVersion` to >= 0 before the request
reaches the domain, so a bad field is VALIDATION_FAILED carrying per-field `errors`. An unknown
player symbol cannot be caught that way — Jackson fails while building the record, before any
validator runs — so it keeps its own INVALID_PLAYER code.

GET  /games?status=IN_PROGRESS
```

```jsonc
// GameState
{
  "gameId": "…", "board": ["X",null,"O",null,"X",null,null,null,"O"],
  "nextPlayer": "X", "status": "IN_PROGRESS",   // IN_PROGRESS | X_WON | O_WON | DRAW
  "moveCount": 4, "version": 4, "winningLine": null
}
```

### Game Session Service (`:8082`)

```
POST /sessions
  body   { "xStrategy": "RULE_BASED", "oStrategy": "RANDOM", "moveDelayMs": 500 }
  201    Session   (creates the game in the engine eagerly)

POST /sessions/{sessionId}/simulate?mode=async     # default
  202    Session (status RUNNING) — moves stream over SSE
POST /sessions/{sessionId}/simulate?mode=sync      # used by tests
  200    Session (status FINISHED, full history)
  409    SIMULATION_ALREADY_STARTED

GET  /sessions/{sessionId}
  200    Session + current board + full move history
  404    SESSION_NOT_FOUND

GET  /sessions?status=FINISHED&outcome=X_WON&xStrategy=RULE_BASED&page=0&size=20
  200    paged Session summaries

GET  /sessions/{sessionId}/events                  # text/event-stream
  events: move | status | error | finished
```

```jsonc
// Session
{
  "sessionId": "…", "gameId": "…",
  "status": "RUNNING",                    // CREATED | RUNNING | FINISHED | FAILED
  "xStrategy": "RULE_BASED", "oStrategy": "RANDOM",
  "board": [...], "gameStatus": "IN_PROGRESS", "outcome": null,
  "moves": [ { "seq": 1, "player": "X", "position": 4, "at": "…" } ],
  "createdAt": "…", "finishedAt": null, "failureReason": null
}
```

### Error format — RFC 7807 (both services)

```jsonc
{ "type": "https://…/errors/cell-occupied", "title": "Cell occupied",
  "status": 409, "detail": "Position 4 is already taken by X",
  "code": "CELL_OCCUPIED", "instance": "/games/abc/move" }

// validation failures add per-field detail
{ "type": "https://…/errors/validation-failed", "title": "Validation failed",
  "status": 400, "detail": "position must be a cell from 0 to 8",
  "code": "VALIDATION_FAILED", "instance": "/games/abc/move",
  "errors": [ { "field": "position", "message": "must be a cell from 0 to 8",
                "rejectedValue": 99 } ] }
```

---

## 6. Data models

### `games` collection — engine service

```jsonc
{ "_id": "gameId", "board": ["X",null,…], "nextPlayer": "O",
  "status": "IN_PROGRESS", "moveCount": 1, "winningLine": null,
  "version": 1,                     // @Version → optimistic locking
  "createdAt": "…", "updatedAt": "…" }
```

### `sessions` collection — session service

```jsonc
{ "_id": "sessionId", "gameId": "…", "status": "FINISHED",
  "xStrategy": "RULE_BASED", "oStrategy": "RANDOM",
  "outcome": "X_WON", "board": [...], "moveCount": 7,
  "moves": [ { "seq": 1, "player": "X", "position": 4, "at": "…" } ],
  "simulationOwner": "instance-a1b2",   // set atomically when simulation starts
  "version": 7,
  "createdAt": "…", "startedAt": "…", "finishedAt": "…", "failureReason": null }
```

The move history is an **embedded array** — bounded at 9 entries, always read as a whole, never
queried element-by-element. That is the document model doing exactly what it is good at, and it
means a session and its full history are read or written in a single round trip.

**Indexes:** `{status: 1, finishedAt: -1}` and `{outcome: 1}` on `sessions` — these make the
interesting question cheap to answer: *does `RULE_BASED` actually beat `RANDOM`, and by how much?*

**Retention:** results are durable by default. Both collections may carry an optional TTL index
on `updatedAt`/`finishedAt` (e.g. 30 days) so demo data eventually self-cleans, but nothing is
evicted while it is still the only copy of a result.

---

## 7. Concurrency model

Both services get their safety from the same primitive — **atomic single-document updates** —
which means the guarantees hold *across replicas*, not merely inside one JVM. That is the point
of a distributed exercise; an in-JVM lock would only look correct until the service scaled.

**Engine — per-game serialization.** Every move is a compare-and-swap on `version`. Spring
Data's `@Version` turns a concurrent write into `OptimisticLockingFailureException`, which maps
to `409 VERSION_CONFLICT`.
*Test:* 9 threads each claim cell 4 simultaneously → exactly 1 × `200`, 8 × `409`, and the final
board contains exactly one mark.

**Session — single-writer simulation.** Starting a simulation is itself a CAS:

```
findOneAndUpdate(
    { _id: sessionId, status: "CREATED" },
    { $set: { status: "RUNNING", simulationOwner: <instanceId>, startedAt: now } })
```

No match means another caller — a double-clicked button, a retrying client, a second replica —
already owns the run, and the request gets `409 SIMULATION_ALREADY_STARTED` instead of
corrupting the session with a double run. No lock service required.

**Ordering.** Moves are strictly alternating and driven by one runner, so the engine never sees
genuine parallel play on the happy path. The guards exist for retries, duplicate clicks, and
replica races — the realistic failure modes.

---

## 8. Simulation + real-time flow

```
UI  ── POST /api/sessions ─────────────► session-svc ── POST /games ──► engine
UI  ── GET  /sessions/{id}/events ─────► session-svc   (SSE, emitter registered)
UI  ── POST /sessions/{id}/simulate ──► session-svc   → 202, runs on task executor
                                            │
                                     loop:  ├─ strategy picks a position from current board
                                            ├─ POST /games/{id}/move ──► engine
                                            ├─ $push move + $set board/status on the session doc
                                            ├─ emit event to that session's SSE subscribers
                                            └─ sleep(moveDelayMs)   ← makes the game watchable
                                     until status != IN_PROGRESS
                                            └─ emit finished, persist terminal state
```

SSE emitters live in an in-process registry keyed by sessionId. The simulation runs in the same
JVM that holds the emitters, so delivery is a direct call — no message broker in the path.

*Scale-out note for the README:* with more than one session-service replica, a browser could
attach to replica B while replica A runs the simulation. MongoDB **change streams** on the
`sessions` collection solve that without adding a new technology — each replica tails the
collection and forwards changes to its local subscribers. Change streams need a replica set
rather than a standalone `mongod`, which is why the single-replica in-process registry is the
default here and the change-stream variant is documented as the scaling path.

**Two gateway constraints this design depends on:**
- *Route scope.* Gateway routes are matched before the static-resource path, so routes stay
  scoped to `/api/**`; a catch-all `/**` route would swallow the UI.
- *No response buffering on the SSE route.* Gateway streams by default, but a compression or
  buffering filter turns a live game into one update at the end. Asserted in the SSE
  integration test rather than eyeballed.

**Move strategies** (`MoveStrategy` interface, selected per player at session creation):
- `RandomMoveStrategy` — uniform choice over free cells, seeded `Random` for reproducible tests.
- `RuleBasedMoveStrategy` — win → block → center → corner → side. Deterministic, so its unit
  tests can assert exact positions.

Two strategies means X and O can behave differently, which makes the "two players" framing real
instead of decorative — and makes the `GET /sessions` outcome query worth running.

---

## 9. Resilience & error handling

- **Timeouts:** connect 1s, read 2s on the engine client. Never inherit an infinite default.
- **Retry (Resilience4j):** 3 attempts, exponential backoff, **only** on 5xx / IO / timeout.
  4xx are deterministic domain answers — retrying `CELL_OCCUPIED` is a bug, not resilience.
- **Circuit breaker:** opens on sustained engine failure; simulation ends as `FAILED` with a
  `failureReason` rather than hanging.
- **Propagation to the UI:** `FAILED` → SSE `error` event → banner + reason in the UI, which is
  exactly the brief's "present appropriate error messages" bullet.
- **Crash recovery:** a session left `RUNNING` by a killed instance is detectable (`status =
  RUNNING` with a stale `startedAt`), and because the session document is durable the move
  history up to the crash is intact and inspectable.
- **Correlation:** `X-Request-Id` generated at the gateway, propagated to both services, put in
  the MDC, printed in every log line — makes a failed simulation traceable end to end.
- **Health:** Actuator `/actuator/health` per service with a Mongo indicator; compose uses these
  as healthchecks so services start in order.

---

## 10. Testing strategy

| Level | Scope | Tooling |
|---|---|---|
| Unit — domain | All 8 winning lines (parameterized), draw, illegal move, turn order, play-after-finish | Plain JUnit 5, no Spring context |
| Unit — strategies | RuleBased *must* take a winning cell; *must* block an opponent's win; Random only picks free cells | JUnit + seeded `Random` |
| Web slice | Status codes and `ProblemDetail` bodies for every error case | `@WebMvcTest` + MockMvc |
| Persistence | Round-trip, `@Version` conflict (engine), simulation-start CAS returning no match (session), index-backed queries | `@DataMongoTest` + Testcontainers |
| Client / resilience | Engine 500 → retried; persistent 500 → breaker opens → session `FAILED`; 409 → **not** retried | WireMock |
| Concurrency | 9 threads, same cell → exactly one winner | Real MongoDB via Testcontainers |
| **Integration (required)** | Both services live on random ports + real MongoDB: create session → simulate → assert terminal status, 5–9 moves, history consistent with final board | `integration-tests` module |
| Integration — SSE | Subscribe, run async simulation, assert event sequence ends with `finished` and move events are strictly ordered | `WebTestClient` SSE consumer |
| Integration — durability | Restart the session service mid-suite; a finished session is still readable with its full history | Testcontainers |
| Smoke (optional) | `docker compose up` → gateway reachable → full game via public API | Testcontainers compose module |

Invariant worth asserting explicitly in the E2E test: a finished game has between 5 and 9 moves,
and replaying the move history reproduces the final board exactly. That single assertion catches
most desynchronization bugs between the two services.

---

## 11. Build phases

| Phase | Deliverable | Rough effort |
|---|---|---|
| 0 | Parent POM, 4 modules, actuator, `mvn verify` green on empty modules | 0.5h |
| 1 | Engine domain (TDD: rules first, no framework) + full unit suite | 1.5h |
| 2 | Engine REST + `ProblemDetail` error model + OpenAPI | 1h |
| 3 | Engine persistence adapter, `@Version` CAS, Testcontainers tests, in-memory adapter | 1.5h |
| 4 | Session domain, persistence adapter, simulation-start CAS, strategies + tests | 1.5h |
| 5 | Engine client (`@HttpExchange`) + Resilience4j + WireMock tests | 1h |
| 6 | `SimulationRunner`, SSE emitter registry + endpoint | 1h |
| 7 | Gateway routes + `ui/` assets (board, status, move log, error banner) | 1.5h |
| 8 | Dockerfiles + docker-compose + healthchecks | 1h |
| 9 | Integration tests, README, design-discussion section | 1.5h |

Order matters: phases 1–3 give a fully working, fully tested engine before any distribution
concerns appear. If time runs short, phases 8–9 shrink and the project still demos end to end.

---

## 12. README outline (graded artifact — not an afterthought)

1. What it does + a screenshot/GIF of a game playing itself
2. Quick start: `docker compose up` → open `http://localhost:8080`
3. Run without Docker: `mvn spring-boot:run -Dspring-profiles.active=in-memory` per service, plus
   `python3 -m http.server 3000 --directory ui` and `UI_URL=http://localhost:3000` on the gateway
4. `mvn verify` — what the test suite covers
5. Architecture diagram + why two services, why database-per-service
6. **Design decisions & trade-offs** — the section that actually gets read:
   - Why a document store for both services, and why one storage technology rather than two
   - Why the brief's H2 suggestion was deviated from, and how the `in-memory` profile keeps faith with it
   - Why optimistic CAS instead of a distributed lock, in both services
   - Why SSE over WebSocket, and the change-stream path to multiple replicas
   - Why the UI is a separate deployable rather than a folder inside the gateway jar
   - Why retries are restricted to 5xx
7. **What I'd do differently at production scale** — event-driven moves over Kafka instead of
   synchronous REST, engine as an actor/partition per game, idempotency keys on every move,
   OpenTelemetry tracing, contract tests (Pact) between the services

---

## 13. Non-goals

Authentication, human players, multi-game concurrency benchmarks, Kubernetes manifests, and
Eureka (the gateway's static routes are sufficient for two services — adding a discovery server
would be config noise, and the README says so explicitly rather than ignoring the bullet).
