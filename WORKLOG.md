# Worklog

A living record of what this project has become, why it looks that way, and
what is still open. Update it in the same commit as the work it describes.

Head at the time of writing: the newest commit on `main`. The table marks that
commit `HEAD`; older rows keep their real hashes.

## Current state

- Backend: Spring Boot 4.1 / WebFlux / R2DBC / Flyway / Spring AI, Postgres 18.
  203 tests, of which the 4 live-AI tests are gated and skipped without keys; the
  other 199 pass, including the Postgres-backed acceptance tests.
- Frontend: React 19 / Vite 8 / Tailwind 4. 132 tests in 21 files, oxlint clean,
  production build green.
- Postgres is the only service needed. No Redis, no external cache.
- AI is optional and provider-agnostic: without `AI_API_KEY` drills come from the
  static pool and coaching from the deterministic fallback. Gemini works through
  its OpenAI-compatible endpoint (see the environment table).

## Delivered, newest first

| Commit    | What it does |
| --------- | ------------ |
| `HEAD`    | Client errors now have one hook. `installClientErrorReporting` listens for `error` and `unhandledrejection`, `ErrorBoundary` reports render crashes, and all three emit `0error:client-error` with kind and message for a host or vendor to consume. The footer shows the injected build version; Vite reads it from the npm package version. Tests cover direct reports, both global hooks with cleanup, the boundary report, and a non-empty version. |
| `b8b501f` | A scored result moves focus to its heading; inactive guide labels gain contrast; reduced-motion users get no animation. |
| `c401cd2` | The coach panel says "Coach is reading the run" until the first token. |
| `1338c82` | Recent drills says "Loading recent drills..." while fetching and "No finished drills yet." when empty; a failed request still hides the section. |
| `bab3085` | Touch-only devices get a physical-keyboard notice instead of a drill that cannot accept input. |
| `6245a0d` | An uncaught render crash shows a calm fallback and one reload action; queued keystrokes stay in storage. |
| `9a5d29d` | Playwright drives Chromium through a full drill and through reload resume plus Start over. The config starts Vite and Spring Boot with AI disabled. |
| `b9def9d` | An open drill can be abandoned. `POST /sessions/{id}/abandon` closes only the owner's ACTIVE row with an honest end time; resume stops offering it. A quiet "Start over" closes it and loads a fresh attempt. |
| `db67008` | Resume keeps the stored key count and accuracy; the engine seeds scored positions and never double counts restored text. |
| `a641775` | `GET /sessions/active` returns typed, scored, and correct counts derived from stored attempts. |
| `8f01e0b` | The drill shows one quiet line while keys wait locally: offline when the network fails, pending when the server asks for a retry. It clears when acknowledged. |
| `bd56a3e` | A reload resumes the open drill. `GET /sessions/active` returns cursor, sequence, and active clock; the engine restores all three, marks three warmup keys, and the status says "Picked up where you left off." |
| `4161611` | `GET /sessions/active` returns the cursor position and active time beside `nextSeq`, derived from the last stored keystroke. |
| `3b2967c` | The focus and reach notes offer "Practise dk" and "Practise Left ring". Each starts a three-drill attempt with `focus=` or `finger=` on every request. |
| `752a93c` | Compose now works with Postgres 18: the volume mounts `/var/lib/postgresql`, host port is `${POSTGRES_PORT:-5432}`, and the duplicate `docker-compose.yml` is gone. Healthy container on 55432, PostgreSQL 18.7, V1 to V10 from empty, full backend suite green. |
| `98e75d9` | Backend gaps closed without schema: `finger` focus on drills, `GET /sessions/active` with `nextSeq`, and a daily `TrendBackfillJob` that repairs missing per-session trend rows from retained keystrokes without re-merging rolling totals. |
| `11c5c99` | `GET /drills/next` accepts an explicit `focus` pair, validated against the tier charset and the lock. Malformed focus, an outside pair, and a locked tier answer `400`, `400`, and `403`. Controller tests now cover drills, progress, fingers, weak pairs, and sessions; an HTTP test proves the focus path. |
| `a918c56` | AI got a fallback provider. `AiChatClient` keeps the Spring AI primary and builds a second OpenAI-compatible model from the `AI_FALLBACK_*` variables; a failed or blank primary moves to the fallback, and a stream that already emitted keeps its partial answer. Offline failover tests plus gated live tests for Nemotron and Gemma passed. |
| `23a535a` | AI became a provider choice: `AI_API_KEY`, `AI_BASE_URL`, and `AI_MODEL` replaced the OpenAI-only names, with the old names still accepted. Gemini works through its OpenAI-compatible endpoint. A gated live test boots the real context and checks a drill and a streamed note. |
| `28bd12a` | The reach note follows the weakest repeated finger across completed drills. `session_finger_stats` (V10) keeps one row per finger per session, and `GET /progress/fingers/trend` returns recent sessions oldest first. The note adds "Left ring: mean 720 ms → 560 ms over your last 2 drills", fetched only when the reach clears the note's own evidence floor and dropped silently if it fails. |
| `8359f2d` | The result panel follows the weakest focus pair across completed drills. `session_weak_pairs` (V9) keeps one row per pair per session, and `GET /progress/weak-pairs/trend` returns the recent sessions oldest first. The note adds "dk: mean 610 ms → 480 ms over your last 3 drills", fetched only for the weakest pair and dropped silently if it fails. |
| `1f0836e` | The result panel reads the practised tier's direction from the same history window: "Home row over your last 4 drills: accuracy 91.2% → 97.5% · median 610 ms → 380 ms." It filters by tier so another tier never blends in, shows nothing below two attempts, and draws no chart. |
| `e299df0` | A completion answered `404` or `409` is terminal: the panel says the attempt cannot be scored, leaves the tier untouched, and offers "Start a fresh drill" instead of retrying forever. A network or server failure still keeps the queued keystrokes and the retry path. |
| `d28943b` | A daily sweep removes guests that never started a drill and have been idle for 30 days. Sign-in writes a users row, so one-time browsers would otherwise accumulate forever; a guest with any session keeps its row because the session carries aggregates. Issuances and other child rows cascade, and a partial `users (updated_at) where kind = 'GUEST'` index keeps the scan small. A Postgres test proves an old empty guest and its fetched drill go, while a fresh guest, an old guest with a session, and a registered user stay. |
| `af974fb` | Guest sign-in is paced per address: 30 new guests in a burst, then one every two seconds, answered `429` with `Retry-After`. Rotating an existing token is never limited. The bucket is in-process and idle ones are pruned, so no dependency was added; the browser turns the status into "Too many new visitors from this network. Wait a moment and try again." Tests cover the burst, the refill, a refused attempt not borrowing from the future, per-address budgets, the controller's `429`, and the wording. |
| `cbaf2cc` | The unlock line names the tier instead of its number: "Top row unlocked. Your next drill moves on." The name comes from the ladder already in memory, with "Tier N" kept as the fallback when progress has not landed. An App test scores a two-pass completion and asserts the sentence. |
| `56073c2` | A dropped connection now says "The server is not reachable. Check your connection and try again." instead of passing a browser's "Failed to fetch" into the error panel. The wrapped fetch is used for guest sign-in and every authenticated request, including the retry after a `401`, and a unit test pins the wording. |
| `2746ba9` | Telemetry is refused once a session is closed. Late batches now get `409` instead of moving aggregates away from the gate decision that already read them; the client treats that status as done and drops the batch so the queue never blocks. Unit tests cover the refusal on both sides. |
| `9be18d1` | A daily sweep marks sessions untouched for 24 hours as `ABANDONED` and sets `ended_at` to the last activity, so `ACTIVE` finally means "still open" and the partial active index stops growing with every closed tab. A Postgres integration test proves stale active rows change, fresh ones and completed ones do not, and the end time is the stop time rather than the sweep time. |
| `40484eb` | Each drill now takes focus when it mounts, so a new drill after "Next drill" is announced instead of leaving focus on the body, and the drawn text gains a hidden plain paragraph the typing surface points at with `aria-describedby`. The App flow test also proves a failed coach stream leaves the scored result intact. |
| `0b5e2c5` | Tier ladder after a score: every tier, the one just practised marked, and the next locked tier's effective median and p95 plus any easing after three failures. The completion error path is now tested — a failing telemetry POST holds the result back, keeps the batch in the outbox, and the retry rescores from it. Issuance expiry, a foreign guest, duplicate ids, and mixed tiers have unit tests. |
| `12635fd` | Accessibility polish. The hesitation hint is also a polite live region, the scored gate outcome is announced from an always-mounted status region, and the guide's fade plus the coach cursor stop moving under `prefers-reduced-motion`. |
| `6e3a630` | Server-owned drill text. Every served drill gets a short-lived issuance id; session creation cites three of them and the server derives tier, charset, length, source, and the composed text. Telemetry `expected` characters and correctness are rewritten from the persisted text, so a client cannot choose what it is scored against. Issuances are single-use, same-tier, owned, and expire in 10 minutes; expired rows are purged daily. |
| `9100031` | App-level jsdom flow test: a guest goes from an empty page through progress, three drills, session creation, eight keydowns, the queued telemetry POST, completion, the result panel, and the streamed coach note. It also asserts the raw `expected`/`actual` pairs reach the server. |
| `34575e5` | Focused controls own their keys, and a pointer click on the theme switch returns focus so the next keystroke is typed instead of swallowed. |
| `f99ebc4` | Remembered light/dark switch. Dark styles key on an `<html>` class, the choice is stored, an inline script applies it before first paint, and the OS setting is followed until the user chooses. |
| `60e67ff` | The client input path is tested in jsdom: real `keydown`, `blur`, and `paste` events through the mounted hook, plus fake-timer tests for the hesitation delay. |
| `b58ecbf` | Removed the unused Redis starter, pool, config, compose service, and health override. Startup no longer scans for Redis repositories. |
| `e776afa` | The keyboard guide names the finger as well as the key after a hesitation, mirroring the server's finger groups. Shifted symbols still point at the base key. |
| `15cf52a` | Telemetry correctness is re-derived on the server from `expected` and `actual`; the client flag is ignored. |
| `40fcc1c` | Top-level README: loop, browser behavior, stack, quick start, configuration, rules, API, tests, layout. |
| `e572911` | Redis health indicator disabled so `/actuator/health` did not go down without the unused service. Superseded by `b58ecbf`. |
| `500408d` | HTTP acceptance test: guest → drill → telemetry → completion → pair/finger reports → SSE coach → replay `409` → targeted drill → second pass unlocks tier 1. |
| `7f83f67` | Coach prompt includes the weak pairs and weak reach. |
| `5629e78` | Finger-targeted drills, AI cache keyed by reach, prompt support. |
| `5df8f01` | Result panel names the least steady reach. |
| `13b06fa` | Per-finger aggregates scored from raw keystrokes. |
| `99a992d` | Deterministic offline drills for uncovered weak pairs. |
| `f453182` | Result panel names the focus pairs behind the next drill. |
| `3dbe624` | Weak-pair aggregation and pair-targeted drills. |
| `d9e53f2` | Text track shaded by typing rhythm, not by mistakes. |
| `6806f00` | Keyboard guide reveals the next key only after 1.2 s without input. |
| `de6ce4e` | Coach notes stream into the result panel. |
| `47808dc` | SSE coaching with a deterministic fallback so the stream always emits text. |
| `8baa43e` | AI drills prefetched and cached; static pool always answers the request. |
| `d66f88a` | Retention purge proven against a real Postgres. |
| `88bc089` | Frontend practices server drills and shows gate outcomes. |
| `c01452c` | Tier unlocks, static drills, completion transaction. |
| `c82edd3` | Raw keystrokes purged after the retention window. |
| `4a77b30` | Guest identity bootstrap and server session creation. |
| `4f48051` | Guest identity, session creation, idempotent telemetry ingest. |
| `2d29e78` | Initial reactive backend and Vite frontend scaffold. |

## Decisions worth remembering

- **Server owns the numbers.** Telemetry is stored raw; accuracy, cadence, pairs,
  and reaches are recomputed from rows. Correctness is derived from `expected`
  against `actual`, never from the client flag.
- **Drills are issued, not echoed.** `GET /drills/next` stores the text with an
  issuance id; `POST /sessions` accepts only ids. The persisted session text is
  the source of every telemetry `expected` character. Ids are single-use,
  same-tier, owned, and expire after 10 minutes.
- **An explicit focus overrides analysis, never validation.** `focus` is one
  two-character pair; both keys must belong to the tier, and the tier must still
  be unlocked. It skips the pair and finger reads, so a browser action costs one
  query instead of three. `finger` works the same way and cannot be combined with
  `focus`. The issue, validation, and lock rules stay identical to an
  automatically focused drill.
- **Resume returns a sequence and a cursor, not a replay.** `GET /sessions/active`
  answers `204` or the newest ACTIVE session with `nextSeq` one past the highest
  stored keystroke, `position` derived from the last KEY or BACKSPACE, the active
  clock, and the stored typed/scored/correct counts. A reloaded tab appends
  instead of colliding, starts at the stored index, and keeps the accuracy the
  server will score. Earlier rhythm shading is not rebuilt; the progress bar
  carries the position. Only the owner sees it; completed attempts and other
  guests are invisible.
- **Closing is explicit, not deletion.** "Start over" marks the owner's ACTIVE
  session `ABANDONED` with the real end time; the row and its aggregates stay.
  Telemetry for it is refused afterwards, and the resume check stops returning it.
  A foreign or missing drill is a `404`, never a silent success.
- **Backfill is repair, not recomputation.** The daily job rebuilds per-session
  trend rows only where they are missing and raw keystrokes still exist. It never
  merges the rolling aggregates again, because those already counted the session;
  replaying them would inflate every weakness score.
- **AI is off the critical path.** Drills are prefetched and cached; the static
  pool always answers. Coaching always has a deterministic fallback.
- **Identity is a guest token.** No accounts; `X-Guest-Id` + `X-Guest-Token`
  rotate the same guest's token.
- **Unlocks are sticky.** Accuracy ≥ 96%, median ≤ 450 ms, p95 ≤ 900 ms, ≥ 120
  scored keys, two consecutive passes. Every third failure relaxes cadence by
  15%. An unlocked tier never closes.
- **Warmup after any pause.** Three keystrokes after resume are stored but not
  scored; pauses also stop the clock.
- **Abandoned is a real state.** A session untouched for a day becomes
  `ABANDONED` with `ended_at` at its last update. Completion already refuses
  anything that is not `ACTIVE`, so a swept session can never be scored late.
- **Closed sessions take no more telemetry.** Ingest answers `409` once a
  session leaves `ACTIVE`, which keeps stored aggregates identical to the ones
  the gate judged. A `409` on a telemetry batch means "done", not "retry", so
  the outbox drops it.
- **A waiting queue is visible, not silent.** The drill shows one quiet line when
  keys are held locally: offline when the network fails, pending when the server
  asks for a retry. It disappears on the next acknowledged batch. The state is
  presentation only; it never changes scoring or the outbox rules.
- **A first load needs the server.** Drills are issued and sessions are created
  server-side, so there is nothing honest to practise offline. The client says
  the server is unreachable in plain words; the outbox still protects a drill
  that was already under way.
- **Pair focus beats finger focus.** A pair is two consecutive expected
  characters keyed by the second one. Score is
  `0.55·error + 0.30·pause + 0.15·latency`; focus needs 2 attempts and 0.1.
- **The guide is quiet.** It appears only after a real pause, names the key, the
  Shift, and the finger, and hides while paused or scoring. A polite live region
  carries the same hint for screen readers, and the visual fade and coach cursor
  stop moving under `prefers-reduced-motion`.
- **Each drill takes focus.** The result button that starts the next drill
  unmounts, so the drill surface focuses itself on mount and points at a hidden
  plain copy of the text. A screen reader then hears the target text; the drawn
  per-character spans stay `aria-hidden` so color and rhythm are not read out.
- **Rhythm, not red ink.** Up to 400 ms reads smooth, up to 900 ms steady, beyond
  that hesitant.
- **Theme is explicit.** System preference decides until the switch is pressed;
  then the stored class wins and is applied before the first paint.
- **Controls own their keys.** The window listener skips focused inputs, buttons,
  links, and `contenteditable`, so Space activates a button instead of typing.
- **The ladder speaks after the score.** Progress is fetched on bootstrap and
  refreshed once completion lands, because that is the only moment tiers change.
  A failed refresh keeps the last known ladder; the result never waits on it.
- **Names, not numbers, in the result.** The unlock sentence reads the tier name
  from the ladder; "Tier N" stays only as the fallback for a missing summary, so
  the copy never contradicts the ladder beside it.
- **No promise of a tier that cannot open.** The result panel only says a clean
  pass unlocks what is left when something above the practised tier is still
  locked. With the whole ladder open, the sentence changes rather than lying.
- **History is recorded, not recomputed.** Completion stores `gate_passed` with
  the session, so a later history read reports the gate as it was judged. A row
  completed before the column existed stays null, which the UI shows as "gate
  unknown"; it is never treated as a failure. Recent-drills is optional context:
  a failed request leaves the result panel exactly as it was. It opens with five
  rows and reveals up to twenty on request from the same single fetch, so there
  is no paging state to keep.
- **Completion is idempotent by replay, never by re-scoring.** The response is
  stored as jsonb beside the session, so a retry returns the same numbers and
  gate decision without touching aggregates or tier progress. Recomputing it
  later would be dishonest: thresholds ease and counters move.
- **A miss is phrased as advice.** Failures name the measured value and what the
  gate wanted ("Median 512 ms: needs 450 ms or less."), never a bare comparison.
  The result list drops mono so it reads like a sentence, and the history dates
  each attempt instead of leaving five identical-looking rows.
- **A closed attempt ends.** `404` and `409` from completion are terminal, not a
  flaky network: the client stops offering "Try again" and starts a fresh drill,
  leaving the tier untouched. Only a network or server failure keeps the queued
  keystrokes and the retry path.
- **A trend is a sentence, not a chart.** The result panel names the direction of
  the practised tier from the same history window, filtering to that tier and
  comparing the oldest attempt with the newest above it. It uses only the
  server's stored numbers, shows nothing below two attempts, and keeps raw
  charts out of the loop as before.
- **A pair trend is per completed drill.** `session_weak_pairs` keeps one row per
  pair per session at completion, next to the rolling merge. A session is one
  observation even if the pair appeared ten times; the mean inside it is what the
  typist felt. The copy happens in the completion transaction and conflicts do
  nothing, so replay cannot add a point. Old sessions have no split and the trend
  simply waits for two new attempts rather than inventing history.
- **The reach trend borrows the note's own floor.** A finger trend is fetched only
  when the weakest reach already clears the two-attempt and score floors that
  show the reach note. One stumble therefore costs neither a sentence nor a
  request, and the two notes stay honest about the same evidence.
- **The model is configuration, not code.** One OpenAI-compatible client shape
  serves OpenAI, Gemini, NVIDIA, and any compatible gateway through `AI_BASE_URL`
  and `AI_MODEL`; the fallback repeats that shape with its own key, URL, and model.
  The fallback is tried only when the primary errors, times out, or returns
  nothing. A stream that already emitted tokens keeps its partial answer rather
  than splicing two providers together. The deterministic drills and coaching
  remain the last resort when both providers are absent or busy. Keys live only in
  the environment.
- **Sign-in is paced in the process.** New guests are the only unauthenticated
  write in the API, so one address gets 30 in a burst and one per two seconds
  after that. Rotating a token is untouched. The bucket resets on restart and is
  per instance: a guard that needs no new dependency, not a distributed quota.
  The burst is sized for a class behind one proxy address.
- **Empty guests expire, used ones do not.** A guest row with no session and a
  month of silence is swept; one that started any drill keeps its row forever,
  because its aggregates are the record. The cutoff reads `updated_at`, so token
  rotation counts as activity. This pairs with pacing: creation is capped, and
  the rows that slip through do not accumulate.
- **No Redis.** It was provisioned but never read; removing it removed a
  dependency, a container, and a repository scan.

## Verification recipes

Backend unit suite:

```bash
cd backend
./mvnw test
```

Database-backed suites (writeable Postgres required):

```bash
cd backend
RUN_INTEGRATION_TESTS=true DB_PORT=5432 DB_PASSWORD=... ./mvnw test
```

Frontend:

```bash
cd frontend
npm run lint
npm test
npm run build
```

The jsdom suites (`App`, `useKeystrokeCapture`, `useHesitation`, `useTheme`)
dispatch real browser events, so the full guest loop, key wiring, and the theme
switch are covered without a browser. `App.test.tsx` mocks only `fetch` and the
SSE body; everything else is the real component tree. A local throwaway Postgres
can be started with
`pg_ctl -D <data> -o "-p 55434 -c listen_addresses=localhost" -l server.log start`
and stopped with `pg_ctl -D <data> stop -m fast`.

Live smoke (backend + Postgres, no browser): with the API on `:8080`, a `POST`
to `/api/v1/auth/guest` returned a token, `/api/v1/progress` returned five tiers,
`/api/v1/drills/next?tier=0` returned a `STATIC` home-row drill, and a burst of
new sign-ins ended in `429` with `Retry-After: 2`.

Fresh-database check — the only proof that the migrations work from nothing:

```bash
createdb -h localhost -p 55434 -U 0error error_fresh
RUN_INTEGRATION_TESTS=true DB_NAME=error_fresh DB_PORT=55434 DB_PASSWORD='' ./mvnw test
dropdb -h localhost -p 55434 -U 0error error_fresh
```

Use a database name that does not begin with a digit: Postgres then needs it
quoted, which this shell drops. On 2026-11-25 V1–V10 applied to an empty database;
196 tests ran, with the 4 gated live-AI tests skipped, so 192 actually executed.

Compose check, same day:

```bash
POSTGRES_PORT=55432 docker compose up -d postgres
docker compose ps                     # healthy, 0.0.0.0:55432->5432
RUN_INTEGRATION_TESTS=true DB_PORT=55432 DB_PASSWORD=0error_dev DB_NAME=0error \
  ./mvnw test                         # 196 tests, 4 live ones skipped
```

The container answered as PostgreSQL 18.7 and Flyway applied V1–V10 from empty.
Port 5432 was already owned by Laragon on this machine, hence the override; the
compose default stays 5432.

Live AI check — off by default because it spends real requests. Primary provider:

```bash
RUN_AI_LIVE_TESTS=true AI_API_KEY=<key> \
  AI_BASE_URL=https://integrate.api.nvidia.com/v1 \
  AI_MODEL=nvidia/nemotron-3-ultra-550b-a55b \
  RUN_INTEGRATION_TESTS=true DB_NAME=error_nv DB_PORT=55434 DB_PASSWORD='' \
  ./mvnw test -Dtest=LiveAiSmokeTest
```

Fallback provider — the primary is pointed at a model that does not exist, so any
output must come from the fallback:

```bash
RUN_AI_FAILOVER_TESTS=true \
  AI_API_KEY=<primary key> AI_BASE_URL=https://integrate.api.nvidia.com/v1 \
  AI_MODEL=nvidia/nemotron-3-ultra-550b-a55b \
  AI_FALLBACK_API_KEY=<fallback key> \
  AI_FALLBACK_BASE_URL=https://integrate.api.nvidia.com/v1 \
  AI_FALLBACK_MODEL=google/gemma-4-31b-it \
  RUN_INTEGRATION_TESTS=true DB_NAME=error_nv DB_PORT=55434 DB_PASSWORD='' \
  ./mvnw test -Dtest=LiveAiFailoverSmokeTest
```

On 2026-11-24 both passed: Nemotron wrote a valid drill and streamed coaching
(2/2, 28 s), and Gemma served both when the primary returned `404` (2/2, 108 s).
Gemini still works through the same configuration, but its earlier live attempt
was refused by the provider with `503` and a free-tier `429`.

Browser check (backend + Vite at `http://localhost:5173/`): the first drill
rendered, the idle hint lit the `a` key and named the left pinky, and the theme
switch flipped the page to dark with a light active key. Screenshots from that
pass are under `backend/.openchamber/screenshots/`.

## Open work

None of these blocks the app; they are the next things worth doing, in order.

1. **Live AI and Compose are proven.** NVIDIA Nemotron and the Gemma fallback
   both passed the gated live tests on 2026-11-24. Gemini remains a supported
   configuration but its earlier attempt was refused by the provider (`503` and a
   free-tier `429`), so re-check it only if that provider is chosen. Compose was
   fixed and exercised on 2026-11-25: `postgres:18-alpine` needs the volume at
   `/var/lib/postgresql`, and the host port is now `${POSTGRES_PORT:-5432}` so a
   local Postgres can coexist.
2. **Shared pacing, only if hosted on more than one instance.** The sign-in
   bucket is per process, so two instances each allow the full burst. A shared
   counter in Postgres would fix it; a single instance does not need one.
3. **Production packaging.** No `Dockerfile` or deploy pipeline yet. Add a
   multi-stage backend image, Azure Container Apps or App Service, managed
   Postgres with SSL, and Vercel for the frontend. Keep one backend replica until
   shared sign-in pacing and scheduled-job leadership exist.

## Housekeeping

Untracked by intent: `.idea/`, `backend/.openchamber/`, and
`backend/0error_blueprint.md` (the product blueprint is kept locally).
Everything else is committed and pushed to `origin/main`.
