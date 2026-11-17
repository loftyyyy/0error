# Worklog

A living record of what this project has become, why it looks that way, and
what is still open. Update it in the same commit as the work it describes.

Head at the time of writing: the newest commit on `main`. The table marks that
commit `HEAD`; older rows keep their real hashes.

## Current state

- Backend: Spring Boot 4.1 / WebFlux / R2DBC / Flyway / Spring AI, Postgres 18.
  114 tests pass, including the Postgres-backed acceptance test.
- Frontend: React 19 / Vite 8 / Tailwind 4. 69 tests in 15 files, oxlint clean,
  production build green.
- Postgres is the only service needed. No Redis, no external cache.
- AI is optional: without `OPENAI_API_KEY` drills come from the static pool and
  coaching from the deterministic fallback.

## Delivered, newest first

| Commit    | What it does |
| --------- | ------------ |
| `HEAD`    | Server-owned drill text. Every served drill gets a short-lived issuance id; session creation cites three of them and the server derives tier, charset, length, source, and the composed text. Telemetry `expected` characters and correctness are rewritten from the persisted text, so a client cannot choose what it is scored against. Issuances are single-use, same-tier, owned, and expire in 10 minutes; expired rows are purged daily. |
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
- **AI is off the critical path.** Drills are prefetched and cached; the static
  pool always answers. Coaching always has a deterministic fallback.
- **Identity is a guest token.** No accounts; `X-Guest-Id` + `X-Guest-Token`
  rotate the same guest's token.
- **Unlocks are sticky.** Accuracy ≥ 96%, median ≤ 450 ms, p95 ≤ 900 ms, ≥ 120
  scored keys, two consecutive passes. Every third failure relaxes cadence by
  15%. An unlocked tier never closes.
- **Warmup after any pause.** Three keystrokes after resume are stored but not
  scored; pauses also stop the clock.
- **Pair focus beats finger focus.** A pair is two consecutive expected
  characters keyed by the second one. Score is
  `0.55·error + 0.30·pause + 0.15·latency`; focus needs 2 attempts and 0.1.
- **The guide is quiet.** It appears only after a real pause, names the key, the
  Shift, and the finger, and hides while paused or scoring.
- **Rhythm, not red ink.** Up to 400 ms reads smooth, up to 900 ms steady, beyond
  that hesitant.
- **Theme is explicit.** System preference decides until the switch is pressed;
  then the stored class wins and is applied before the first paint.
- **Controls own their keys.** The window listener skips focused inputs, buttons,
  links, and `contenteditable`, so Space activates a button instead of typing.
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

## Open work

None of these blocks the app; they are the next things worth doing, in order.

1. **Accessibility polish.** The hesitation hint and gate outcome are visual
   only. Announce the hint through a polite live region, and honour
   `prefers-reduced-motion` for the guide's fade and the coach cursor.
2. **Tier ladder.** `/api/v1/progress` returns every tier with effective
   thresholds, but the client only uses the highest unlocked tier. A small
   ladder would show what is next and what the relaxed thresholds are.
3. **Completion error path.** `App.test.tsx` covers the happy loop. A second
   case should fail the telemetry POST and prove the result panel stays hidden,
   the retry button rescores, and nothing is lost from the outbox.
4. **Issuance edge cases in tests.** Unit and HTTP paths cover missing, reused,
   and mixed-tier ids. Expiry and a foreign guest are exercised only by code,
   not by a test, because they need clock control or a second guest.
5. **Blocked by environment.** No `OPENAI_API_KEY`, so live drill generation and
   model coaching are unverified; the fallbacks are. No Docker, so
   `docker compose up -d postgres` was never executed here.

## Housekeeping

Untracked by intent: `.idea/`, `backend/.openchamber/`, and
`backend/0error_blueprint.md` (the product blueprint is kept locally).
Everything else is committed and pushed to `origin/main`.
