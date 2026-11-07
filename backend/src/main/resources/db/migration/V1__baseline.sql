-- 0Error baseline schema.
-- Raw keystroke rows are purged after 30 days by the retention job; aggregates stay forever.

create or replace function set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create table users (
    id                 uuid primary key default gen_random_uuid(),
    kind               varchar(16) not null check (kind in ('GUEST', 'REGISTERED')),
    email              varchar(320),
    display_name       varchar(80),
    guest_token_hash   varchar(128),
    claimed_by_user_id uuid references users (id) on delete set null,
    claimed_at         timestamptz,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    constraint users_identity_chk check (
        (kind = 'GUEST' and email is null and guest_token_hash is not null)
        or (kind = 'REGISTERED' and email is not null)
    )
);

comment on table users is 'Guests are first-class: a guest row exists before any signup.';
comment on column users.guest_token_hash is 'SHA-256 of the anonymous token. The raw token never reaches the database.';

create unique index users_email_unique_idx
    on users (lower(email))
    where email is not null;

create unique index users_guest_token_unique_idx
    on users (guest_token_hash)
    where guest_token_hash is not null;

create trigger users_set_updated_at
    before update on users
    for each row
    execute function set_updated_at();

create table sessions (
    id                    uuid primary key default gen_random_uuid(),
    user_id               uuid not null references users (id) on delete cascade,
    client_session_id     uuid not null,
    tier                  smallint not null check (tier between 0 and 4),
    drill_source          varchar(16) not null check (drill_source in ('STATIC', 'AI')),
    target_length         smallint not null check (target_length between 1 and 500),
    allowed_charset       varchar(128) not null,
    status                varchar(16) not null default 'ACTIVE'
        check (status in ('ACTIVE', 'COMPLETED', 'ABANDONED')),
    started_at            timestamptz not null,
    ended_at              timestamptz,
    keystroke_count       integer not null default 0 check (keystroke_count >= 0),
    correct_count         integer not null default 0 check (correct_count >= 0),
    accuracy              numeric(5, 2) check (accuracy between 0 and 100),
    median_inter_key_ms   integer check (median_inter_key_ms >= 0),
    p95_inter_key_ms      integer check (p95_inter_key_ms >= 0),
    hesitation_count      integer not null default 0 check (hesitation_count >= 0),
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    constraint sessions_client_unique unique (user_id, client_session_id),
    constraint sessions_end_after_start check (ended_at is null or ended_at >= started_at)
);

comment on table sessions is 'One practice drill. Aggregate columns are recomputed from keystroke_events, never trusted from the client.';
comment on column sessions.client_session_id is 'Client-generated idempotency key for session creation.';
comment on column sessions.allowed_charset is 'Character set the drill was validated against, e.g. asdfjkl;';

create index sessions_user_started_idx
    on sessions (user_id, started_at desc);

create index sessions_active_idx
    on sessions (updated_at)
    where status = 'ACTIVE';

create trigger sessions_set_updated_at
    before update on sessions
    for each row
    execute function set_updated_at();

create table keystroke_events (
    id            bigint generated always as identity primary key,
    session_id    uuid not null references sessions (id) on delete cascade,
    seq           integer not null check (seq >= 0),
    position      smallint not null check (position >= 0),
    kind          varchar(12) not null default 'KEY' check (kind in ('KEY', 'BACKSPACE')),
    expected_char char(1),
    actual_char   char(1),
    is_correct    boolean not null,
    t_ms          integer not null check (t_ms >= 0),
    display_ms    integer check (display_ms >= 0),
    is_warmup     boolean not null default false,
    created_at    timestamptz not null default now(),
    constraint keystroke_events_seq_unique unique (session_id, seq)
);

comment on table keystroke_events is 'Raw per-keystroke telemetry. Typed strings are never stored; each row holds one character. Rows older than 30 days are purged.';
comment on column keystroke_events.t_ms is 'Active session time in milliseconds. Pauses freeze this clock, so cadence gaps never include pause time.';
comment on column keystroke_events.display_ms is 'Milliseconds from the target character becoming visible to the keypress.';
comment on column keystroke_events.is_warmup is 'First 3 keystrokes after a resume are stored but excluded from scoring.';

create index keystroke_events_session_seq_idx
    on keystroke_events (session_id, seq);

-- BRIN is cheap and matches the append-only retention purge by created_at.
create index keystroke_events_retention_idx
    on keystroke_events using brin (created_at);
