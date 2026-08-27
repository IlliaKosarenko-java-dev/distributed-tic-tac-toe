/*
 * Runs once, when the data directory is first created.
 *
 * One MongoDB instance, one database per service, and a user scoped to that database alone.
 * The scoping is what keeps "database-per-service" a constraint rather than a naming
 * convention: neither service holds credentials that can read the other's collections.
 */

db = db.getSiblingDB('tictactoe-games');
db.createUser({
    user: 'engine',
    pwd: process.env.MONGO_ENGINE_PASSWORD || 'engine-dev-password',
    roles: [{ role: 'readWrite', db: 'tictactoe-games' }]
});

db = db.getSiblingDB('tictactoe-sessions');
db.createUser({
    user: 'session',
    pwd: process.env.MONGO_SESSION_PASSWORD || 'session-dev-password',
    roles: [{ role: 'readWrite', db: 'tictactoe-sessions' }]
});

print('Created per-database users: engine -> tictactoe-games, session -> tictactoe-sessions');
