# Reviewed nonclinical source rulings

The catalogue pins every physical table and column. Presence is not itself evidence
of a patient clinical occurrence. These rulings were reviewed against the ON/BC
physical contracts and owning models/DAOs. Patient payloads and relationships still
require capture, independent inventory and explicit review when unsupported.

| Tables | Owning evidence and disposition |
| --- | --- |
| `quickListUser`, `encounterWindow`, `view` | `QuickListUser`, `EncounterWindow`, `View`: provider UI names, sizes, view filters and last-use metadata. `REFERENCE`. |
| `custom_filter_assignees`, `custom_filter_providers` | `CustomFilter`, `CustomFilterDaoImpl`: provider/assignee sets for a saved tickler search. Actual patient ticklers retain their separate scope. `REFERENCE`. |
| `provider_default_program` | `ProviderDefaultProgram`: provider preferred program and sign-note default; no patient admission or note. `REFERENCE`. |
| `groupMembers_tbl`, `MyGroupAccessRestriction` | `GroupMembers`, `GroupMembersDaoImpl`, `MyGroupAccessRestriction`: provider group routing/access configuration. Current patient/program/provider rights remain independently checked. `REFERENCE`. |
| `waitingListName` | `WaitingListName`: list name, provider/group, creation and archive flag. Actual patient membership remains a separately scoped source. `REFERENCE`. |
| `dashboard`, `indicatorTemplate` | `Dashboard`, `IndicatorTemplate` and their DAOs: dashboard definitions and metric/query templates, not patient measurement values or generated reports. `REFERENCE`. |
| `reportByExamples`, `reportByExamplesFavorite`, `reportConfig`, `reportFilter`, `reportItem`, `reportTableFieldCaption`, `reportTemplates` | Corresponding models/DAOs and `ReportConfigurationService`: saved queries, inputs, captions and templates. Export never executes definition SQL or exempts generated report output. Document reports retain independent attachment capture. `REFERENCE`. |
| `professionalSpecialists`, `serviceSpecialists` | `ProfessionalSpecialist`, `ProfessionalSpecialistDaoImpl`, `ServiceSpecialists`: referral directories. Patient referrals, consultations and clinical authors retain capture or explicit review. `REFERENCE`. |
| `default_issue` | `DefaultIssue`, `DefaultIssueDaoImpl`: provider issue-selection defaults. Patient issue assignments remain independently inventoried. `REFERENCE`. |
| `messengerMembershipLock` | `GroupMembersDaoImpl.lockMembership`: singleton for serializing group membership updates, without message content. `OPERATIONAL`. |
| `user_ds_message_prefs` | `UserDSMessagePrefs`: provider/resource read/display preferences and lifecycle clocks. Message bodies, patient associations and attachments remain separately gated. `OPERATIONAL`. |

These rows are not serialized into patient packages. Their schema remains pinned,
and unknown columns/tables require review. Saved queries/preferences may contain
sensitive text; the ruling does not authorize reading, publishing or executing it.

The native database regression populates provider defaults, quick-list preferences,
report definitions and a messenger lock, then executes the actual source gate.
Separate clinical-source cases still refuse selected unsupported rows, dangling
parents and unresolved ownership.
