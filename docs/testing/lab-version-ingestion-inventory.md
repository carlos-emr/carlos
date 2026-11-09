# Native CML version ingestion (#5726)

`lab-version-ingestion` uses Inbox → HL7 Lab Upload to ingest different CML
ORU^R01 messages. SQL creates owned patients and independently reads state; it
does not seed a version chain. The shared upload fixture still checks invalid
input retryability, first ingestion and exact duplicate refusal. Its optional
continuation reuses the same accession/checksum/raw-message/measurement/archive
cleanup without invoking the separate ON-only flat-CML path.

| Version | Observation time | Result | Flag | ORC/OBR/OBX status |
| --- | --- | --- | --- | --- |
| Original | 2026-03-10 08:00:00 | 5.1 mmol/L, range 3–8 | N | F |
| Correction | 2026-03-10 12:00:00 | 12.7 mmol/L, range 3–8 | H | C |
| Older, delivered last | 2026-03-10 07:00:00 | 4.3 mmol/L, range 3–8 | N | F |

The correction deliberately amends both value and observation time. Its explicit
C status distinguishes a correction from a byte-different resend or an additional
unrelated test. The accession and patient are the same across all three messages.
A second independently named patient/accession is uploaded as a control. The
configured test clinician is the synthetic ordering provider `999998`, matching
the existing CML upload fixture. `EXCLUSIVE=1`, a disposable VM and the actual
archived-upload store are required. Missing prerequisites cannot qualify.

The original is acknowledged through its own native report before the
correction arrives. Independent snapshots verify exact raw values, flags,
observation times, stored status/final-count metadata, one patient link and
unduplicated provider routes. Original review state must remain unchanged and
the correction must be independently New. Every selected report and native
historical link is checked for the exact source ID and result-row cells.

Inbox ALL intentionally presents separate versions, with independent review
states; it is not a single-current selector. Its own-patient rows must contain
exactly the ingested source set, with no duplicate or control-patient row. The
explicit correction row must open that correction. The patient chart presents a
current accession entry; its title/date anchors are two links in one list item,
so the check counts list items without suppressing duplicate clinical entries.
The current chart source must be the correction after both later deliveries.
Normal first-open chart initialization precedes read-isolation snapshots. All
subsequent report/chart reads must preserve complete owned clinical and source/
review state, and the independent control patient/accession must never change.

The supported CML ordering counts both F and C observations as complete, then
orders by clinical observation date. At equal completeness and observation time,
a correction outranks a final original; source ID is only the final stable tie
break. C remains C in the source/report status. Completeness is derived from the
stored source when resolving a CML chain, so old zero-count correction metadata
does not require rewriting clinical records. Historical deliveries retain their
raw messages and review/routing state but do not mutate active measurements.
The other vendor ordering paths retain their existing contracts.

Earlier native ON/BC retakes reproduced two separate findings, whose fixes now
require fresh installed qualification:

- [#5819](https://github.com/carlos-emr/carlos/issues/5819): the chart selects the
  original F as current instead of the later C correction. Explicit Inbox and
  historical source selection remain correct.
- [#5820](https://github.com/carlos-emr/carlos/issues/5820): original and corrected
  Measurements remain active after correction; delivering the older version
  removes the correction and leaves original/older measurements active.

The check continues read-only observations after those mismatches, then fails;
it never prints those comparisons as passes. Exact corrected-message replay
must answer Already uploaded and preserve all three raw versions, clinical/
measurement state, routes, three checksums and three archives. Replay does not
repair a lost measurement and is not presented as doing so. Active measurement
links must name the intended patient and a known source; the intended active
result is independently specified as the correction. Every raw historical
message must remain unchanged.

Cleanup discovers all owned source IDs before deleting their lookup links,
retains IDs across failed/lost delete acknowledgments, removes owned measurement
extensions/checksums/archive files, restores pre-existing routing-lock presence
and preserves unrelated locks. Control parent ownership is claimed only after
its namespace is proved empty, before INSERT, so a lost acknowledgment is
recoverable and a collision cannot adopt another patient. Setup now registers
scratch recovery before payload writes and refuses pre-existing accession/raw/
legacy namespaces. A committed source requires its archive; setup failure before
any accepted upload legitimately has none. Those paths have fault-injection
controls, including BC's missing ON-only legacy table.

Snapshot30 application source is `52b5b9c80752ea3d3732ee9e92bb98c6a3cd69ed`,
Debian package SHA-256
`bdc240d0e9581c57290e76634a5ab3e62322e3a295236863d9851009fd2b8e32`.
Both provinces reached original acknowledgment, correction/older delivery,
native report histories and chart selection, exact correction replay and clean
owned-source/patient teardown, failing on the four comparisons above. The stronger Inbox row-set and read-isolation assertions also reach completion
on both provinces. One BC repetition failed during local schedule asset loading
and remains a failed attempt; the handoff now waits for native load/AJAX completion
before returning to the captured day sheet. Its retake reaches all stages with
only the four tracked clinical comparisons failing. Full release qualification
is separate.
