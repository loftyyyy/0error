-- Drill issuances bind a served drill text to the guest it was served to.
-- Session creation cites these ids, so the server, not the client, decides the
-- tier, charset, length, and expected characters a session is scored against.

create table drill_issuances (
    id            uuid primary key,
    user_id       uuid not null references users (id) on delete cascade,
    tier          smallint not null check (tier between 0 and 4),
    source        varchar(16) not null check (source in ('STATIC', 'AI')),
    charset       varchar(128) not null,
    drill_text    varchar(500) not null,
    created_at    timestamptz not null default now(),
    expires_at    timestamptz not null,
    consumed_at   timestamptz
);

comment on table drill_issuances is 'A drill text the server served. Only an unexpired, unconsumed, owned issuance can open a session.';
comment on column drill_issuances.drill_text is 'The exact text the session is scored against; telemetry expected characters are read from here.';
comment on column drill_issuances.consumed_at is 'Set once a session claims the issuance. A drill id cannot be used twice.';

create index drill_issuances_user_open_idx
    on drill_issuances (user_id, expires_at)
    where consumed_at is null;

create index drill_issuances_expiry_idx
    on drill_issuances (expires_at);

-- The issued text is persisted with the session so telemetry can be normalized
-- against it. Null only for sessions created before this migration.
alter table sessions
    add column drill_text varchar(500);

comment on column sessions.drill_text is 'Server-issued drill text. Telemetry expected_char is rewritten from this column, never trusted from the client.';
