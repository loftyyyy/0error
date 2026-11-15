-- Finger-level aggregates expose which reach costs the most, the second half
-- of the blueprint's "pairs or finger reaches" analysis. Like weak_pairs they
-- survive the raw keystroke purge and are merged at session completion.

create table finger_stats (
    user_id          uuid not null references users (id) on delete cascade,
    tier             smallint not null check (tier between 0 and 4),
    finger           varchar(20) not null check (finger in (
                         'LEFT_PINKY', 'LEFT_RING', 'LEFT_MIDDLE', 'LEFT_INDEX',
                         'THUMBS', 'RIGHT_INDEX', 'RIGHT_MIDDLE', 'RIGHT_RING',
                         'RIGHT_PINKY')),
    attempts         integer not null default 0 check (attempts >= 0),
    errors           integer not null default 0 check (errors >= 0),
    pauses           integer not null default 0 check (pauses >= 0),
    total_display_ms bigint not null default 0 check (total_display_ms >= 0),
    updated_at       timestamptz not null default now(),
    primary key (user_id, tier, finger)
);

comment on table finger_stats is 'Rolling per-finger aggregates per user and tier, merged at session completion.';
comment on column finger_stats.finger is 'The finger that types the expected key at a position.';
comment on column finger_stats.pauses is 'Keys whose display gap exceeded the friction threshold.';

create trigger finger_stats_set_updated_at
    before update on finger_stats
    for each row
    execute function set_updated_at();
