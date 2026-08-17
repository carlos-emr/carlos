-- Apply after migrations with psql variables naming pre-created NOLOGIN/LOGIN roles:
--   psql "$DATABASE_ADMIN_URL" \
--     -v owner_role=portal_schema_owner \
--     -v runtime_role=portal_runtime \
--     -v maintenance_role=portal_audit_maintenance \
--     -f postgresql-audit-roles.sql
-- The deployment platform owns role creation/password delivery. The web and outbox processes use
-- runtime_role; only the offline prune job receives maintenance_role credentials.

\set ON_ERROR_STOP on

REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO :"runtime_role", :"maintenance_role";

-- The runtime role needs ordinary application DML but must not own the evidence table. Run this
-- after every migration, or mirror these grants through deployment-managed default privileges.
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO :"runtime_role";
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO :"runtime_role";
ALTER TABLE public.patient_portal_audit_events OWNER TO :"owner_role";
REVOKE UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER
  ON public.patient_portal_audit_events FROM :"runtime_role";
GRANT SELECT, INSERT ON public.patient_portal_audit_events TO :"runtime_role";

-- Pruning is deliberately isolated. This role cannot modify application state or insert evidence.
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM :"maintenance_role";
GRANT SELECT, DELETE ON public.patient_portal_audit_events TO :"maintenance_role";

