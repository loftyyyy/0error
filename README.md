# 0Error

A minimalist touch-typing tutor that starts you on the home row within seconds and
never asks you to sign up. The browser captures every keystroke locally, the
server turns those raw events into the metrics, and the next drill leans on
whatever pair or finger still makes you hesitate.

## How a session works

1. The browser asks for the guest identity once and keeps the bearer token.
2. `GET /api/v1/drills/next?tier=` returns a short drill for the current tier.
3. Typing is scored locally for the live display only; every keystroke is also
   queued in IndexedDB and sent to the server in batches.
4. The server recomputes accuracy and cadence from the raw rows, never from
   client aggregates.
5. Completion applies the unlock gate, merges the session into pair and finger
   aggregates, and marks the session `COMPLETED`.
6. The client streams coaching over SSE and shows the weakest transitional pair
   or the least steady reach.
7. The next drill prefers a text that exercises that weakness.

## In the browser

- The keyboard drawing reveals the next key only after 1.2 s without input, and
  names the finger that reaches it. Shifted symbols point at the base key and
  add a Shift hint.
- The text track colors by rhythm, not by mistakes: up to 400 ms is smooth,
  up to 900 ms steady, and beyond that hesitant.
- Losing focus or pausing stops the clock. The first three keys after a pause are
  warmup and never scored.
- After a result, two quiet notes name the weakest transition and the least
  steady reach. Raw charts are deliberately absent.
- Light and dark follow the operating system until the switch beside the tier
  name is pressed. That choice is remembered and applied before the first paint,
  so a dark session never flashes white.

## Stack

| Part     | Choice                                                        |
| -------- | ------------------------------------------------------------- |
| Backend  | Spring Boot 4.1, WebFlux, R2DBC, Flyway, Spring AI             |
| Frontend | React 19, Vite 8, Tailwind 4, Lucide                           |
| Database | PostgreSQL 18                                                  |
| Optional | OpenAI-compatible chat model for drills and coaching           |

Java 17+ and Node 20.19+ are required.

## Quick start

```bash
# 1. Postgres
docker compose up -d postgres

# 2. Backend on http://localhost:8080
cd backend
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run

# 3. Frontend on http://localhost:5173
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173>. Vite proxies `/api` to port 8080, and the backend
allows that exact origin, so use `localhost:5173` rather than `127.0.0.1:5173`.

The defaults in `docker-compose.yml` match the backend's defaults
(`0error` / `0error_dev` on port 5432). Flyway creates the schema on first
start.

## Configuration

Copy `.env.example` values into the backend process environment as needed.

| Variable                     | Default                 | Purpose                                      |
| ---------------------------- | ----------------------- | -------------------------------------------- |
| `DB_HOST` / `DB_PORT`        | `localhost` / `5432`    | Postgres location                            |
| `DB_NAME` / `DB_USER`        | `0error` / `0error`     | Database and role                            |
| `DB_PASSWORD`                | `0error_dev`            | Database password                            |
| `SERVER_PORT`                | `8080`                  | Backend port                                 |
| `CORS_ALLOWED_ORIGINS`       | `http://localhost:5173` | Browser origin allowed to call the API       |
| `OPENAI_API_KEY`             | unset                   | Enables generated drills and coaching        |
| `OPENAI_MODEL`               | `gpt-4.1-mini`          | Chat model                                   |
| `DRILL_AI_ENABLED`           | `true`                  | Background drill prefetch                    |
| `TELEMETRY_RAW_RETENTION_DAYS` | `30`                  | Raw keystroke retention                      |
| `TELEMETRY_MAX_BATCH_SIZE`   | `500`                   | Events accepted per telemetry request        |

The frontend takes one optional variable, `VITE_API_BASE_URL`. Leave it empty in
development so the Vite proxy is used; set it when the API lives on another
origin.

## Rules the server enforces

**Scoring.** Only `KEY` events count, and warmup keys never score. Accuracy is
first-attempt per position, so a correction cannot erase an error. Cadence is the
gap between consecutive scored keys; gaps over 1500 ms are excluded and counted
as hesitations.

**Unlock gate.** Accuracy at least 96%, median inter-key gap at most 450 ms, p95
at most 900 ms, at least 120 scored keystrokes, and two consecutive passes. Every
third failed attempt relaxes both cadence thresholds by 15%. An unlocked tier is
never revoked.

**Tiers.** Home row, Top row, Bottom row, Punctuation, Full sentences. Tier 0 is
always open; the rest need an unlock row.

**Drills.** 40–60 characters drawn only from the tier's charset. A static pool
always answers the request. Generated drills are prefetched in the background
and cached by tier plus focus pairs and weak reach, so the model is never on the
request path.

**Adaptive targeting.** Consecutive expected characters form a pair keyed by the
second key. A pair or finger is weak when it has at least two attempts and a
score of 0.1 or more, where score is `0.55 × error rate + 0.30 × pause rate +
0.15 × normalized latency`. Pair focus wins over finger focus; a missing
curated drill is replaced by a deterministic drill composed from the target.

**Coaching.** The SSE stream always emits text: generated tokens when a model is
configured, deterministic sentences otherwise. The prompt sees the session
metrics, the focus pairs, and the weak reach.

**Retention.** Raw keystrokes are purged after 30 days. Session aggregates, pair
aggregates, and finger aggregates are kept indefinitely.

## API

All `/api/v1` endpoints except guest token creation require
`Authorization: Bearer <guest token>`. Sessions and reports are scoped to the
guest that owns them. The actuator probes are public.

| Method | Path                                   | Purpose                                             |
| ------ | -------------------------------------- | --------------------------------------------------- |
| POST   | `/api/v1/auth/guest`                   | Create or rotate a guest token                      |
| GET    | `/api/v1/drills/next?tier=&exclude=`   | Next drill; `403` when the tier is locked           |
| POST   | `/api/v1/sessions`                     | Start a session; idempotent per `clientSessionId`   |
| POST   | `/api/v1/sessions/{id}/telemetry`      | Append raw keystrokes; `200`, or `409` if all known |
| POST   | `/api/v1/sessions/{id}/complete`       | Score, merge aggregates, apply the gate             |
| GET    | `/api/v1/sessions/{id}/feedback`       | SSE coaching: `token` events, then `done` `[DONE]`  |
| GET    | `/api/v1/progress`                     | Tiers, effective thresholds, unlock timestamps      |
| GET    | `/api/v1/progress/weak-pairs?tier=`    | Pair aggregates, weakest first                      |
| GET    | `/api/v1/progress/fingers?tier=`       | Per-finger aggregates, weakest first                |
| GET    | `/actuator/health`                     | Liveness and readiness probes                       |

A telemetry batch carries `{ batchId, sessionId, sentAt, events[] }`; each event
is `{ seq, position, kind, expected, actual, correct, t, displayMs, warmup }`.
The unique `(session_id, seq)` constraint makes retried batches safe.

## Tests

Backend unit suite:

```bash
cd backend
./mvnw test
```

Database-backed suites, including the end-to-end
`TypingLoopAcceptanceTest` (guest → drill → telemetry → completion → analysis →
coaching → next targeted drill → unlock):

```bash
cd backend
RUN_INTEGRATION_TESTS=true ./mvnw test
```

PowerShell:

```powershell
$env:RUN_INTEGRATION_TESTS = 'true'
.\mvnw.cmd test
```

The integration run expects Postgres at `DB_HOST`/`DB_PORT` (default
`localhost:5432`) with the configured credentials.

Frontend:

```bash
cd frontend
npm run lint
npm test
npm run build
```

Most frontend tests are pure functions rendered to static markup. The keystroke
capture and hesitation tests run in jsdom and dispatch real `keydown`, `blur`,
and `paste` events, so the window wiring is covered without a browser.

## Project layout

```text
backend/
  src/main/java/com/rho/backend/
    identity/    guest tokens and bearer authentication
    sessions/    session lifecycle, completion transaction, tier gating
    telemetry/   raw keystroke ingest, scoring, retention
    drills/      tier catalogue, static pool, focused builder, AI cache
    progress/    tier state and unlock rules
    analysis/    weak-pair and per-finger aggregates
    feedback/    SSE coaching and its fallback
  src/main/resources/db/migration/   Flyway schema
frontend/
  src/features/   typing loop, drills, progress, feedback, identity
```

## Notes

- There are no accounts, emails, or passwords. Identity is a random guest token;
  supplying `X-Guest-Id` and `X-Guest-Token` to the guest endpoint rotates that
  same guest's token.
- The server stores single expected/actual characters per keystroke, never the
  typed text. It re-derives each keystroke's correctness from those characters
  and never trusts client-calculated aggregates.
- Without `OPENAI_API_KEY` the app is fully functional: drills come from the
  static pool and coaching from the deterministic fallback.
- Postgres is the only service required to run. Nothing is cached out of process.
