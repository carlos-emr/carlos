-- V1.0.45 — consultationRequests list/filter indexes (common schema, issue #3976).
--
-- The Consultations list (encounter/ViewConsultation, ConsultationRequestDaoImpl.getConsults) and its
-- new Consultant filter/type-ahead run against a table whose baseline carries only the primary key;
-- V1.0.3 added (demographicNo) and (sendTo, status). The parallel fork measured the list's first load
-- going from ~3.1 s to ~30 ms on a clinic with ~8.4k consultants once equivalent indexes existed.
-- Each index below backs a real predicate:
--   * (status, referalDate): the default list shape (status != '4', referral-date range, ORDER BY
--     referalDate), getCountReferralsAfterCutOffDateAndNotCompleted (referalDate < ? AND status != 4)
--     and getReferrals (referalDate <= ? AND status = '1').
--   * (status, appointmentDate): the same list when "search on appointment date" is selected.
--   * (specId): the Consultant filter (specialist.id = ?) and the type-ahead's EXISTS probe that limits
--     suggestions to specialists referenced by a consult (searchDistinctConsultants).
--   * (serviceId): the list's LEFT JOIN to consultationServices and findByDemographicAndService(s).
-- The fork's (providerNo, status, referalDate) is not added: the list filters on the patient's MRP
-- (demographic.provider_no, indexed by V1.0.3), not on consultationRequests.providerNo.
--
-- Version: V1.0.44 is claimed by open PR #3694 (provider signature identity); see ../README.md.
--
-- Same safety rules as V1.0.3/V1.0.18: a forward migration so fresh installs and OSCAR 19 conversions
-- (V1 stamped, not executed) both receive it; `CREATE INDEX IF NOT EXISTS` (MariaDB DDL) keeps it
-- idempotent on heterogeneous converted datadirs; each statement is an online InnoDB secondary-index
-- add with no table rebuild. CAVEAT (shared with V1.0.3): IF NOT EXISTS is a no-op when an index of
-- the SAME NAME already exists, even with different columns; the idx_* names below are new.

CREATE INDEX IF NOT EXISTS `idx_consultationRequests_status_referalDate` ON `consultationRequests` (`status`,`referalDate`);
CREATE INDEX IF NOT EXISTS `idx_consultationRequests_status_appointmentDate` ON `consultationRequests` (`status`,`appointmentDate`);
CREATE INDEX IF NOT EXISTS `idx_consultationRequests_specId` ON `consultationRequests` (`specId`);
CREATE INDEX IF NOT EXISTS `idx_consultationRequests_serviceId` ON `consultationRequests` (`serviceId`);
