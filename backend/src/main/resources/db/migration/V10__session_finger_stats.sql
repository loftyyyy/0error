-- The finger counterpart of session_weak_pairs: one row per finger per
-- completed session, so the reach note can say whether a finger is improving
-- rather than only its lifetime total. Counts and timings, never typed text;
-- kept after the 30-day keystroke purge.
--
-- Sessions completed before this table existed have no rows, so a trend waits
-- for two later completions instead of inventing a split.

create table session_finger_stats (
    session_id       uuid not null references sessions (id) on delete cascade,
    user_id          uuid not null references users (id) on delete cascade,
    tier             smallint not null check (tier between 0 and 4),
    finger           varchar(20) not null check (finger in (
                         'LEFT_PINKY', 'LEFT_RING', 'LEFT_MIDDLE', 'LEFT_INDEX',
                         'THUMBS', 'RIGHT_INDEX', 'RIGHT_MIDDLE', 'RIGHT_RING',
                         'RIGHT_PINKY')),
    attempts         integer not null check (attempts > 0),
    errors           integer not null check (errors >= 0),
    pauses           integer not null check (pauses >= 0),
    total_display_ms bigint not null check (total_display_ms >= 0),
    recorded_at      timestamptz not null default now(),
    primary key (session_id, finger)
);

create index session_finger_stats_trend_idx
    on session_finger_stats (user_id, tier, finger, recorded_at desc);

comment on table session_finger_stats is 'Finger counts for one completed session, kept for per-finger trends after raw keystrokes expire.';
comment on column session_finger_stats.recorded_at is 'When completion recorded the session, used to order attempts oldest to newest.';
