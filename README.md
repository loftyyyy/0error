# 0Error

A minimalist touch-typing tutor that starts you on the home row within seconds and
never asks you to sign up. The browser captures every keystroke locally, the
server turns those raw events into the metrics, and the next drill leans on
whatever pair or finger still makes you hesitate.

## How a session works

1. The browser asks for the guest identity once and keeps the bearer token.
2. `GET /api/v1/drills/next?tier=` returns a short drill for the current tier,
   with a server-issued `drillId`. Three drills form one attempt.
3. Typing is scored locally for the live display only; every keystroke is also
   queued in IndexedDB and sent to the server in batches.
4. `POST /api/v1/sessions` cites the issued drill ids. The server persists the
   exact text, derives the tier, charset, and length from it, and rewrites every
   telemetry `expected` character from that text. Aggregates are recomputed from
   the raw rows, never from client aggregates.
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
  steady reach. Each is also followed across recent completed drills: "dk: mean
  610 ms → 480 ms over your last 3 drills" for the pair, and "Left ring: mean
  720 ms → 560 ms over your last 2 drills" for the reach. They are extra context,
  so a failed read leaves the note standing. Raw charts are deliberately absent.
  The gate outcome is also announced through a polite live region.
- A short tier ladder follows the result: every tier, the one just practised
  marked, and the next locked tier's effective median, p95, and any easing after
  three failed attempts.
- A missed gate is stated as advice, one line per criterion: "Median 512 ms:
  needs 450 ms or less." The list is not raw diagnostics in monospace.
- A collapsed "Recent drills" list under the result shows the guest's five most
  recent finished drills with the date, accuracy, median, and whether each
  cleared its gate. Up to twenty are one click away. It is optional context: if
  the history request fails, the panel is unchanged.
- Above that list, a single line reads the practised tier's direction across the
  most recent five attempts at it: "Home row over your last 4 drills: accuracy
  91.2% → 97.5% · median 610 ms → 380 ms." It compares the server's own numbers
  and draws no chart.
- If the keystrokes cannot all reach the server, no result is invented. The
  batches stay in the outbox and "Try again" rescores once the API answers.
- A drill the server has already closed — swept as abandoned while a tab slept,
  completed before answers were stored, or owned by a guest identity this
  browser no longer holds — is not retried forever. The panel says the attempt
  cannot be scored and offers a fresh drill, with the tier untouched.
- A cold load needs the API, because drills are issued rather than bundled. When
  the server is unreachable the panel says so in plain words instead of echoing
  a browser's "Failed to fetch".
- Coaching is never on the critical path: a failed stream leaves the scored
  result in place and says so in one line.
- Each drill takes focus when it appears and the target text is also exposed as
  a hidden plain paragraph, so a screen reader reads what to type while the
  colored, per-character drawing stays out of the announcement.
- A hesitation hint is announced for screen readers, and the guide's fade plus
  the coach cursor stop moving under `prefers-reduced-motion`.
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
| `AI_API_KEY`                 | unset                   | Enables generated drills and coaching        |
| `AI_BASE_URL`                | `https://api.openai.com/v1` | Primary OpenAI-compatible endpoint        |
| `AI_MODEL`                   | `gpt-4.1-mini`          | Primary chat model                           |
| `AI_FALLBACK_API_KEY`        | unset                   | Enables the fallback provider                |
| `AI_FALLBACK_BASE_URL`       | `https://integrate.api.nvidia.com/v1` | Fallback endpoint             |
| `AI_FALLBACK_MODEL`          | `google/gemma-4-31b-it` | Fallback chat model                          |
| `DRILL_AI_ENABLED`           | `true`                  | Background drill prefetch                    |
| `TELEMETRY_RAW_RETENTION_DAYS` | `30`                  | Raw keystroke retention                      |
| `TELEMETRY_MAX_BATCH_SIZE`   | `500`                   | Events accepted per telemetry request        |

The frontend takes one optional variable, `VITE_API_BASE_URL`. Leave it empty in
development so the Vite proxy is used; set it when the API lives on another
origin.

The AI variables name the model, not the vendor. They work with any
OpenAI-compatible endpoint, and a second provider can sit behind the first:

```bash
AI_BASE_URL=https://integrate.api.nvidia.com/v1
AI_MODEL=nvidia/nemotron-3-ultra-550b-a55b
AI_FALLBACK_BASE_URL=https://integrate.api.nvidia.com/v1
AI_FALLBACK_MODEL=google/gemma-4-31b-it
```

The fallback is tried when the primary errors, times out, or returns nothing;
the deterministic text remains the last resort. For Gemini, use
`https://generativelanguage.googleapis.com/v1beta/openai` and a model the key can
access, such as `gemini-3.8-flash`.

The keys come from `AI_API_KEY` and `AI_FALLBACK_API_KEY`
(`OPENAI_API_KEY` is still accepted for the primary) and are never written into
the repository. Without them, everything still runs: drills fall back to the
static pool and coaching to `FallbackFeedback`.

## Rules the server enforces

**Scoring.** Only `KEY` events count, and warmup keys never score. Accuracy is
first-attempt per position, so a correction cannot erase an error. Cadence is the
gap between consecutive scored keys; gaps over 1500 ms are excluded and counted
as hesitations.

**Closed sessions are final.** Telemetry is accepted only while a session is
`ACTIVE`. A batch that arrives after completion gets `409`, and the client drops
it rather than retrying forever, so the stored aggregates can never drift away
from the gate decision that was made from them. Completion also stores that
decision (`gate_passed`), so the history list reports the gate as it was judged
rather than re-judging an old attempt against thresholds that later eased. Rows
completed before the column existed read as unknown, not as a failure.

**Completion is idempotent.** The scored response is stored with the session in
the same transaction that marks it `COMPLETED`. A retried `POST /complete` —
what a lost response produces — returns that stored answer instead of scoring
again, and pair or finger aggregates are still merged only once. A session
completed before the stored answer existed has nothing to replay and still
answers `409`.

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

**Issued text.** Every served drill is stored with a short-lived issuance id.
A session can only cite owned, unexpired, unconsumed ids from one tier, and each
id works once. The session keeps the composed text, so a client cannot change
the target, the charset, the length, or the expected characters it is scored
against.

**Adaptive targeting.** Consecutive expected characters form a pair keyed by the
second key. A pair or finger is weak when it has at least two attempts and a
score of 0.1 or more, where score is `0.55 × error rate + 0.30 × pause rate +
0.15 × normalized latency`. Pair focus wins over finger focus; a missing
curated drill is replaced by a deterministic drill composed from the target.

**Coaching.** The SSE stream always emits text: generated tokens when a model is
configured, deterministic sentences otherwise. The prompt sees the session
metrics, the focus pairs, and the weak reach.

**Retention.** Raw keystrokes are purged after 30 days. Session aggregates, pair
aggregates, per-session pair and finger counts, and finger aggregates are kept
indefinitely. A daily sweep marks
sessions untouched for a day as `ABANDONED`, with `ended_at` set to the last
activity, so `ACTIVE` keeps meaning "still open". A second sweep removes guests
that have no session at all and no activity for 30 days: sign-in writes a row,
and a browser opened once should not stay in the table forever. Guests that ever
started a drill keep their row, because their session carries aggregates.

## API

All `/api/v1` endpoints except guest token creation require
`Authorization: Bearer <guest token>`. Sessions and reports are scoped to the
guest that owns them. The actuator probes are public.

**Guest sign-in is paced.** A new guest writes a row, so one address may create
30 in a burst and then one every two seconds. Over that, the endpoint answers
`429` with `Retry-After` and the browser asks the visitor to wait. Rotating an
existing guest's token is never limited. The counter lives in the process: it
resets on restart and does not coordinate across instances, which is enough to
stop a trivial loop without adding a dependency. Behind a proxy every client
shares the proxy's address, so the burst is sized for a room rather than one
person.

| Method | Path                                   | Purpose                                             |
| ------ | -------------------------------------- | --------------------------------------------------- |
| POST   | `/api/v1/auth/guest`                   | Create or rotate a guest token; `429` when paced    |
| GET    | `/api/v1/drills/next?tier=&exclude=`   | Next drill; `403` when the tier is locked           |
| POST   | `/api/v1/sessions`                     | Start from drill ids; idempotent per client id      |
| POST   | `/api/v1/sessions/{id}/telemetry`      | Append raw keystrokes; `200`, or `409` if all known |
| POST   | `/api/v1/sessions/{id}/complete`       | Score once; a retry replays the stored answer       |
| GET    | `/api/v1/sessions/recent?limit=`       | Own completed drills, newest first; 1–20, default 5 |
| GET    | `/api/v1/sessions/{id}/feedback`       | SSE coaching: `token` events, then `done` `[DONE]`  |
| GET    | `/api/v1/progress`                     | Tiers, effective thresholds, unlock timestamps      |
| GET    | `/api/v1/progress/weak-pairs?tier=`    | Pair aggregates, weakest first                      |
| GET    | `/api/v1/progress/weak-pairs/trend?tier=&pair=&limit=` | One pair across recent sessions, oldest first; 1–10, default 5 |
| GET    | `/api/v1/progress/fingers?tier=`       | Per-finger aggregates, weakest first                |
| GET    | `/api/v1/progress/fingers/trend?tier=&finger=&limit=` | One reach across recent sessions, oldest first; 1–10, default 5 |
| GET    | `/actuator/health`                     | Liveness and readiness probes                       |

A telemetry batch carries `{ batchId, sessionId, sentAt, events[] }`; each event
is `{ seq, position, kind, expected, actual, correct, t, displayMs, warmup }`.
The server ignores `expected` and `correct` and reads both from the session's
issued text. The unique `(session_id, seq)` constraint makes retried batches
safe.

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

Most frontend tests are pure functions rendered to static markup. The `App` flow
tests, the keystroke capture test, and the hesitation test run in jsdom and
dispatch real `keydown`, `blur`, and `paste` events. `App.test.tsx` covers the
happy loop, the raw events that reach the server, and a telemetry outage where
the result is held back until the queued batch is retried.

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
- Without `AI_API_KEY` the app is fully functional: drills come from the static
  pool and coaching from the deterministic fallback.
- Postgres is the only service required to run. Nothing is cached out of process.

The build history, the reasoning behind each decision, and the remaining work
live in [WORKLOG.md](WORKLOG.md).
