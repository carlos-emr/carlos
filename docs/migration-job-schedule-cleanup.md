# Migration Note: Job/Schedule Cleanup for Removed Integration Classes

## Why this check is needed
The following integration classes were removed from the codebase as dead/unused code:

- `io.github.carlos_emr.carlos.integration.dashboard.OutcomesDashboardMetricSenderJob`
- `io.github.carlos_emr.carlos.integration.dhir.DHIRUtils`
- `io.github.carlos_emr.carlos.integration.ebs.client.ng.RawXmlLoggingInInterceptor`
- `io.github.carlos_emr.carlos.integration.ebs.client.ng.WSS4JInNonValidatingActionInterceptor`
- `io.github.carlos_emr.carlos.integration.fhir.model.PatientContact`
- `io.github.carlos_emr.carlos.integration.fhir.model.RelatedPerson`
- `io.github.carlos_emr.carlos.integration.mchcv.OBECRunner`

Although these classes are not present in repo-managed scheduler config/seed SQL anymore, some deployed environments may still contain custom rows in `OscarJobType` / `OscarJob` from manual setup or legacy data.

---

## Step 1: Find stale job type entries
Run this SQL in each environment (dev/stage/prod) before or during deployment:

```sql
SELECT id, name, description, class_name, enabled, created_at
FROM OscarJobType
WHERE class_name IN (
  'io.github.carlos_emr.carlos.integration.dashboard.OutcomesDashboardMetricSenderJob',
  'io.github.carlos_emr.carlos.integration.dhir.DHIRUtils',
  'io.github.carlos_emr.carlos.integration.ebs.client.ng.RawXmlLoggingInInterceptor',
  'io.github.carlos_emr.carlos.integration.ebs.client.ng.WSS4JInNonValidatingActionInterceptor',
  'io.github.carlos_emr.carlos.integration.fhir.model.PatientContact',
  'io.github.carlos_emr.carlos.integration.fhir.model.RelatedPerson',
  'io.github.carlos_emr.carlos.integration.mchcv.OBECRunner'
);
```

If this returns no rows, no DB cleanup is needed for this migration.

---

## Step 2: Find linked scheduled jobs
If Step 1 returns rows, run:

```sql
SELECT j.id, j.name, j.description, j.oscarJobTypeId, j.schedule, j.enabled
FROM OscarJob j
JOIN OscarJobType t ON t.id = j.oscarJobTypeId
WHERE t.class_name IN (
  'io.github.carlos_emr.carlos.integration.dashboard.OutcomesDashboardMetricSenderJob',
  'io.github.carlos_emr.carlos.integration.dhir.DHIRUtils',
  'io.github.carlos_emr.carlos.integration.ebs.client.ng.RawXmlLoggingInInterceptor',
  'io.github.carlos_emr.carlos.integration.ebs.client.ng.WSS4JInNonValidatingActionInterceptor',
  'io.github.carlos_emr.carlos.integration.fhir.model.PatientContact',
  'io.github.carlos_emr.carlos.integration.fhir.model.RelatedPerson',
  'io.github.carlos_emr.carlos.integration.mchcv.OBECRunner'
);
```

---

## Step 3: Remove stale jobs and job types
> Take a backup/snapshot first and run in a maintenance window.

```sql
START TRANSACTION;

DELETE j
FROM OscarJob j
JOIN OscarJobType t ON t.id = j.oscarJobTypeId
WHERE t.class_name IN (
  'io.github.carlos_emr.carlos.integration.dashboard.OutcomesDashboardMetricSenderJob',
  'io.github.carlos_emr.carlos.integration.dhir.DHIRUtils',
  'io.github.carlos_emr.carlos.integration.ebs.client.ng.RawXmlLoggingInInterceptor',
  'io.github.carlos_emr.carlos.integration.ebs.client.ng.WSS4JInNonValidatingActionInterceptor',
  'io.github.carlos_emr.carlos.integration.fhir.model.PatientContact',
  'io.github.carlos_emr.carlos.integration.fhir.model.RelatedPerson',
  'io.github.carlos_emr.carlos.integration.mchcv.OBECRunner'
);

DELETE FROM OscarJobType
WHERE class_name IN (
  'io.github.carlos_emr.carlos.integration.dashboard.OutcomesDashboardMetricSenderJob',
  'io.github.carlos_emr.carlos.integration.dhir.DHIRUtils',
  'io.github.carlos_emr.carlos.integration.ebs.client.ng.RawXmlLoggingInInterceptor',
  'io.github.carlos_emr.carlos.integration.ebs.client.ng.WSS4JInNonValidatingActionInterceptor',
  'io.github.carlos_emr.carlos.integration.fhir.model.PatientContact',
  'io.github.carlos_emr.carlos.integration.fhir.model.RelatedPerson',
  'io.github.carlos_emr.carlos.integration.mchcv.OBECRunner'
);

COMMIT;
```

---

## Step 4: Post-deploy verification
Run the same SELECTs from Step 1 and Step 2; both should return zero rows.

If your deployment has scheduler diagnostics endpoints/logs, verify that there are no class-loading attempts for any of the removed class names.

---

## Notes
- No corresponding Spring scheduler bean wiring exists for these classes in repo scheduler config.
- This is strictly a defensive runtime DB cleanup check for environments with historical/custom data.
