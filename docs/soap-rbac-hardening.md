# SOAP RBAC Hardening — privilege map and upgrade checklist

CARLOS EMR's JAX-WS endpoints authenticate callers through WS-Security
(`AuthenticationInWSS4JInterceptor`), which establishes *who* is calling. Until this change most
individual service methods did not then check *what* that caller may reach: an authenticated
account could call any method on any service. This document records the privilege each endpoint now
requires, and the operator steps needed before deploying it.

Related: issue #2814.

## How the check works

`AbstractWs.requirePrivilege(...)` wraps `SecurityInfoManager.hasPrivilege(...)` and throws
`SecurityException("missing required sec object (<object>)")` on denial. Call it before any manager
or DAO invocation, so a denial can never be preceded by a PHI-bearing load.

Two behaviours are worth knowing:

- **Privilege tiers.** A grant of `x` satisfies any requested privilege; `w` satisfies `r`/`u`/`w`;
  `u` satisfies `r`/`u`. Most seeded grants are `x`, so a role granted an object at all usually
  passes an `r` check.
- **Patient scope.** Passing a `demographicNo` makes `hasPrivilege` consult
  `<object>$<demographicNo>` first, falling back to the unscoped object when no per-patient override
  row matches the caller's roles. Endpoints that know the patient pass it; the rest pass null.

Denied calls are recorded in the audit log as `ws.accessDenied` with the object, privilege and
patient scope — identifiers only, never clinical content.

## Privilege map

| Service | Methods | Requires |
|---|---|---|
| `DemographicWs` | `getDemographic`, `getDemographic2` | `_demographic r`, scoped to the requested patient |
| `DemographicWs` | `getDemographics` | `_demographic r` for **each** requested patient |
| `DemographicWs` | `searchDemographicByName`, `searchDemographicsByAttributes` | `_demographic r`; results additionally filtered per patient |
| `DemographicWs` | `getAdmittedDemographicIdsByProgramProvider`, `getActiveDemographicsAfter`, `getActiveDemographicsAfter2`, `getConsentedDemographicIdsAfter` | `_demographic r` |
| `DocumentWs` | all | `_edoc r` |
| `PrescriptionWs` | `getPrescription` | `_rx r` at entry, then `_rx r` scoped to the prescription's patient |
| `PrescriptionWs` | remaining reads | `_rx r`, scoped to the patient where the method takes one |
| `MeasurementWs` | reads | `_measurement r` |
| `MeasurementWs` | `addMeasurement` | `_measurement w`, scoped to the measurement's patient |
| `ScheduleWs` / `BookingWs` | reads | `_appointment r` |
| `ScheduleWs` | `addAppointment`, `updateAppointment` | `_appointment w` |
| `LabUploadWs` | all nine upload methods | `_lab w` on the calling account |
| `ProgramWs` | `getAllPrograms` | `_pmm.programList r` |
| `ProgramWs` | `getAllProgramProviders` | `_pmm.staffList r` |
| `ProviderWs` | `getLoggedInProviderTransfer` | `_pref r` |
| `ProviderWs` | `getProviderProperties` | `_pref r` for self-reads; `_admin r` for cross-provider reads |
| `FacilityWs` | `getAllFacilities` | `_admin r` |

### Security objects deliberately not used

`_appointment.UpdatedAfterDate` looks like an appointment-sync gate and is not one. In
`ScheduleManagerImpl.getAppointmentUpdatedAfterDate` its *absence* means "filter these results by
patient consent" and its *presence* means "you may see them unfiltered" — a consent-bypass marker,
not an access gate. It is also granted to no role in either province's migration set, so requiring
it would deny every caller including admin. `getAppointmentArchivesUpdatedAfterDate` therefore uses
`_appointment r`, matching its non-archive sibling.

`_pmm_management` is referenced by several PMmodule actions but likewise appears in no migration
as a grant, so `ProgramWs` uses `_pmm.programList` / `_pmm.staffList` instead — the objects the
PMmodule UI uses for the same two screens, both granted to `admin` and `doctor` in Ontario and BC.

`SecurityObjectSeedContractUnitTest` pins this class of mistake: it fails the build when code gates
on a security object that no migration grants to any role.

## Upgrade checklist

These endpoints were previously reachable by any authenticated SOAP caller. Accounts that relied on
that will now fault. Before deploying:

1. **Lab intake (`_lab w`).** Both province migration sets grant `_lab` to the `doctor` role only.
   Dedicated lab-uploader service accounts on minimal custom roles will start faulting, and from
   the EMR side lab intake simply stops — the failure surfaces only on the sender. Inventory the
   accounts used by lab feeds and grant `_lab w` to their roles.
2. **Facility directory (`_admin r`).** `_admin` is granted to the `admin` role only. Integrator /
   inter-EMR sync accounts provisioned with a provider-type role will fault on
   `getAllFacilities`. Grant them the object, or move those integrations to an admin-role account.
3. **Cross-provider property reads (`_admin r`).** `ProviderWs.getProviderProperties` for a
   provider other than the caller now needs `_admin r`; self-reads need only `_pref r`.
4. **Program reads.** `_pmm.programList` and `_pmm.staffList` are granted to `admin` and `doctor`
   out of the box, so no action is expected for stock installs with custom roles derived from
   those.

After deploying, watch the audit log for `ws.accessDenied` entries — each one is an integration
that needs a grant, and the entry names the object and privilege it was refused.

## Deferred

Policy for these was not clear enough to fix in this pass, and they remain unguarded:

- `ProviderWs.getProviders2(...)` — whether the provider directory should be admin-only or readable
  by any authenticated provider.
- `FacilityWs.getDefaultFacility()` and the deprecated `getDefaultFacilities()` alias — whether
  default-facility reads should be admin-only or tied to authenticated facility membership.
