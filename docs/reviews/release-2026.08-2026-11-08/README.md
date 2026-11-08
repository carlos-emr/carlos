# release/2026.08 vs main review (2026-11-08)

Full per-reviewer reports behind issue #5830
("Review: release/2026.08 vs main — intensive review findings and corrections needed").

- Release head reviewed: `9004536d` (2026.08.0-beta7-SNAPSHOT). Main: `d40d2939` (tag 2026.08.0-beta7).
  Merge base: `e53e6cb5` (tag 2026.08.0-beta6). Scope: 134 commits, 2,550 files.
- Each report has Coverage, Findings, Pre-existing issues (where requested), Contract changes,
  Known issues re-confirmed, Checked and found OK, and Declined to judge sections.
- `REVIEW-BRIEF.md` is the shared instruction set every reviewer followed.
- `R-release-level.md` has the coordinator's release-process findings.
- `VERIFICATION-LOG.md` records which findings the coordinator re-checked in code.
- Static review only. No JDK 25/Maven build, installed package, live browser run or native MariaDB was available.

| Report | Scope |
| --- | --- |
| [S01](S01.md) | Library upgrades/removals: JavaMelody, OWASP Encoder 1.6.0, OTel, Drools, Flyway, HAPI FHIR 8.12.1, #5494/#5511/#5513 |
| [S02](S02.md) | EBS/MCEDT WS-Security, Jasypt removal, CXF modules and transport |
| [S03](S03.md) | CDS to JDK XML, Xerces removal, JDOM2 safeguards |
| [S04](S04.md) | Metro removal, JAXB/SOAP XML contracts (#5526, #5710, #5722) |
| [S05](S05.md) | Report outputs: OpenPDF 3, CSV formula guard, BC CSV, Dx XLSX, half-letter Rx PDF |
| [S06](S06.md) | Report SQL and budgets (#5560, #5553, #5688) |
| [S07](S07.md) | Library replacement #5518 (charts, tables, browser), #5646 |
| [S08](S08.md) | Forced modern chart, legacy editor rejection (#5602) |
| [S09](S09.md) | Response-rewriting filters, pre-login facility assets |
| [S10](S10.md) | Typed DAOs part 1: BC MSP/WCB, initial queries, raw JDBC accessor |
| [S11](S11.md) | Typed DAOs part 2: lab, inbox, workflow (#5695) |
| [S12](S12.md) | Typed DAOs part 3: encounter and eForm (#5694) |
| [S13](S13.md) | MariaDB Connector/J migration (#5460) |
| [S14](S14.md) | Debian packaging and startup encryption keys |
| [S15](S15.md) | Prescriptions (Rx) |
| [S16](S16.md) | eChart, encounter, case management, notes |
| [S17](S17.md) | Billing, scheduling, demographics |
| [S18](S18.md) | Consultations, documents, inbox, labs, HRM, measurements |
| [S19](S19.md) | Fax, email, eForms |
| [S20](S20.md) | BCAR 2020 and Growth 0–36 form persistence |
| [S21](S21.md) | Reflective credential/patient diagnostics removal (#5512) |
| [S22](S22.md) | REST API and assorted endpoint fixes |
| [S23](S23.md) | Behaviour-equivalent refactors (#5497, #5489) |
| [S24](S24.md) | JSP dead-directive cleanup (#5681, 713 files) |
| [S25](S25.md) | Release merges, conflict resolutions, version metadata |
| [S26a](S26a.md) | CI and release-verification tooling, DCO |
| [S26b](S26b.md) | Browser/Playwright and Node harness |
| [S26c](S26c.md) | Java tests, production code in test commits, Claude hooks |
| [C1](C1.md) | Cross-cutting: Java API and return-contract changes |
| [C2](C2.md) | Cross-cutting: wire and stored data formats |
| [C3](C3.md) | Cross-cutting: HTTP status and error-response contracts |
