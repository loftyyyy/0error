-- The dormant-guest sweep looks for old guests that never started a session.
-- A partial index keeps that daily scan proportional to guest rows that are old
-- enough to matter rather than the whole users table.

create index users_guest_activity_idx
    on users (updated_at)
    where kind = 'GUEST';
