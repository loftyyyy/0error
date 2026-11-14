-- Pair-level aggregates survive the raw keystroke purge: they are the
-- long-lived memory the drill picker uses to target a typist's weak spots.
-- Raw keystrokes still expire after 30 days; these counters never do.

create table weak_pairs (
    user_id          uuid not null references users (id) on delete cascade,
    tier             smallint not null check (tier between 0 and 4),
    first_char       varchar(1) not null check (ascii(first_char) between 32 and 126),
    second_char      varchar(1) not null check (ascii(second_char) between 32 and 126),
    attempts         integer not null default 0 check (attempts >= 0),
    errors           integer not null default 0 check (errors >= 0),
    pauses           integer not null default 0 check (pauses >= 0),
    total_display_ms bigint not null default 0 check (total_display_ms >= 0),
    updated_at       timestamptz not null default now(),
    primary key (user_id, tier, first_char, second_char)
);

comment on table weak_pairs is 'Rolling bigram aggregates per user and tier, merged at session completion.';
comment on column weak_pairs.first_char is 'First expected character of the pair.';
comment on column weak_pairs.second_char is 'Second expected character; its timing and accuracy define the pair.';
comment on column weak_pairs.pauses is 'Second keys whose display gap exceeded the friction threshold.';

create trigger weak_pairs_set_updated_at
    before update on weak_pairs
    for each row
    execute function set_updated_at();
