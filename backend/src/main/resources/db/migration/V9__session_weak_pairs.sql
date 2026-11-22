-- A per-session copy of the pair counts, so a typist can see whether one
-- transition is actually improving rather than only its lifetime total. Like
-- the rolling aggregate, this stores counts and timings, never typed text, and
-- survives the 30-day keystroke purge.
--
-- Sessions completed before this table existed have no rows. A trend simply
-- waits for two later completions; the missing split cannot be invented.

create table session_weak_pairs (
    session_id       uuid not null references sessions (id) on delete cascade,
    user_id          uuid not null references users (id) on delete cascade,
    tier             smallint not null check (tier between 0 and 4),
    first_char       varchar(1) not null check (ascii(first_char) between 32 and 126),
    second_char      varchar(1) not null check (ascii(second_char) between 32 and 126),
    attempts         integer not null check (attempts > 0),
    errors           integer not null check (errors >= 0),
    pauses           integer not null check (pauses >= 0),
    total_display_ms bigint not null check (total_display_ms >= 0),
    recorded_at      timestamptz not null default now(),
    primary key (session_id, first_char, second_char)
);

create index session_weak_pairs_trend_idx
    on session_weak_pairs (user_id, tier, first_char, second_char, recorded_at desc);

comment on table session_weak_pairs is 'Pair counts for one completed session, kept for per-pair trends after raw keystrokes expire.';
comment on column session_weak_pairs.recorded_at is 'When completion recorded the session, used to order attempts oldest to newest.';
