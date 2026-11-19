-- Sessions keep their gate decision so a history list never has to re-judge an
-- attempt against thresholds that may have moved since. Rows completed before
-- this column existed stay NULL, which reads as "not recorded", not as a fail.

alter table sessions
    add column gate_passed boolean;

comment on column sessions.gate_passed is 'Gate decision at completion. Null for sessions completed before this column existed.';

-- Recent-drills reads completed rows for one guest, newest first.
create index sessions_history_idx
    on sessions (user_id, ended_at desc)
    where status = 'COMPLETED';
