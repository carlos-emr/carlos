# NHS chart-update integration test — September 28, 2026

## Environment and scope

The existing development database contained all 50 NHS synthetic patients.
The run used a separate CARLOS Tomcat on loopback port 8082 and a new MariaDB
schema copied from that development database. Original patient charts were not
changed by the walkthrough. The receipt-table migration was applied to the copy.

Three documents were prepared from complete, unchanged corpus notes already
present in those copied CARLOS charts. Their zero-based note indices were 16,
16 and 14 for NHSSYN001, NHSSYN002 and NHSSYN003 respectively. File hashes:

| Fixture | SHA-256 |
| --- | --- |
| NHSSYN001 | `0cac494dc22d152317479c21ae5414cdfc800b3b0c7dbe7e54b716a94307cd92` |
| NHSSYN002 | `b074b2a00e06d9dcf3694c2ea57610cf47195e4476c6480d972a3ef0e2f118c4` |
| NHSSYN003 | `48bb604a1eff0edebcd031f0b7f81bdd73c1a98d8ff1ad8e523c9f7e36e9f846` |

**No live model was used.** The previous model gateway configuration was absent
after the container rebuild. The application called `nhs_fixture_gateway.py`,
which returns three fixed source excerpts for each selected note. Its contract
checks accepted all three complete notes and rejected a modified version of each.
This tests application integration, not model extraction or clinical accuracy.

## Defect found

The initial real review request failed: `ChartUpdateContext.load()` was marked
`@Transactional(readOnly = true)` but called the synchronous access audit writer.
The MySQL JDBC connection rejected the audit insert and Spring raised
`UnexpectedRollbackException`, producing the CARLOS error page.

The loader now uses a writable transaction because access auditing is a database
write. The browser regression asserts both a successful review page and a new
`ChartUpdates.read` audit row. The existing mocked/H2 checks had not caught the
MySQL-specific read-only connection failure.

## Verification

All three patient walkthroughs passed. The [machine-readable result](chart-update-integration.json) records
the agent type and checks per patient.

| Fixture | Proposals | Reminders saved | Signed history entries saved | Dismissed |
| --- | ---: | ---: | ---: | ---: |
| NHSSYN001 | 3 | 1 | 1 | 1 |
| NHSSYN002 | 3 | 1 | 1 | 1 |
| NHSSYN003 | 3 | 1 | 1 | 1 |

Verified through the real application and MariaDB:

- Login, patient search, eChart and the document-summary review link.
- Access audit persistence and exact source excerpts.
- Generation and dismissal do not create chart-update records.
- Edited reminders retain the chosen date, assignee, source passage and native
  document link.
- History entries are signed under the development clinician and have native
  document links.
- Desktop and 390-pixel mobile rendering, with no horizontal overflow on mobile.
- Durable retry protection: NHSSYN001's two entries were created during the initial
  corrected run, then replayed during the final complete run. The database still
  contains exactly one reminder and one history entry for that document.

The final database check found six approval receipts, three reminders and three
signed history entries across the three test documents. NHSSYN002 and NHSSYN003
had zero receipts before the final run. The shared browser runner was adjusted to
handle CARLOS's explicit same-user editing-lock prompt and close named search
windows between patients.

All **73 focused Java tests passed**, including native persistence and rollback
coverage. The fixture gateway separately passed complete-source acceptance and
changed-source refusal for all three selected notes.

The full Maven package build succeeded before the transaction fix. The corrected
class was rebuilt by the focused Maven test run and deployed to the isolated
instance for verification. Production deployment was outside this run. The isolated Tomcat and fixed
proposal gateway were stopped after verification; the database copy and local
artifacts were retained for inspection.

See [reproduction instructions](../../browser/NHS_LIVE_CHECK.md).
