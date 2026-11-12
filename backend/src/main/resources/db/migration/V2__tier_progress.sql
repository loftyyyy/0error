-- Tier progression is sticky: an unlocked tier is never revoked.
-- A tier unlocks after two consecutive passing sessions under its gate.

create table tier_progress (
    user_id             uuid not null references users (id) on delete cascade,
    tier                smallint not null check (tier between 0 and 4),
    consecutive_passes  smallint not null default 0 check (consecutive_passes >= 0),
    failed_attempts     smallint not null default 0 check (failed_attempts >= 0),
    unlocked_at         timestamptz,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    primary key (user_id, tier)
);

comment on table tier_progress is 'Per-user tier gate state. Rows exist only after a completed session; tier 0 is unlocked implicitly.';
comment on column tier_progress.unlocked_at is 'Non-null means unlocked. Never cleared, so progress cannot regress.';
comment on column tier_progress.failed_attempts is 'Every third failure relaxes the cadence gate by 15%. Reset on a pass.';

create trigger tier_progress_set_updated_at
    before update on tier_progress
    for each row
    execute function set_updated_at();
