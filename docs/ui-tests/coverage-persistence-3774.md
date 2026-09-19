# Release persistence and screening report coverage (#3774)

This group tests the release's existing emailConfig TEXT mapping, not develop-only V1.0.26 credential columns. It adds reload-based checks for active-only selection, conjunctive sender/type/provider matching, deactivation, absent results, quoted sender input, enum conversion, and a 15KB synthetic JSON value. Security DAO tests cover provider ordering and rejection of non-allowlisted sort expressions.

Mammogram/Pap report scenarios exercise the production report code with fixed dates and controlled history/measurement inputs: no information, future records, due-date boundary, due, overdue, refusal, ineligibility, pending MAM results, report totals, follow-up action and billing metadata. They characterize the release's rules; they do not assert that historical screening intervals are current clinical guidance. Spring initializes the legacy static DAO dependencies, so these are integration-tagged rather than misleadingly classified as isolated unit tests.

Local validation: 48 JUnit cases passed, including the existing Security DAO scenarios. Transactions roll back owned rows. A disposable mutant removing active filters from EmailConfigDaoImpl was rejected by these tests. This is H2/JPA coverage, not evidence of a fresh MariaDB migration or live email transport.

Existing ConsultRequestDao and ProviderDataDao integration tests already exercise the implementations flagged by filename matching in the issue. Their measured baseline and remaining branch limits are recorded in the audit PR; this group does not duplicate those tests just to add implementation filenames.
