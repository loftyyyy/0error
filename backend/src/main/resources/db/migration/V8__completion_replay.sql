-- A completion retried after a lost response must not score the session twice.
-- The response is stored as jsonb and replayed verbatim, so the guest sees the
-- same numbers and gate decision the first call produced.

alter table sessions
    add column completion jsonb;

comment on column sessions.completion is 'The completion response as it was returned, replayed on a retried completion. Null for sessions completed before this column existed.';
