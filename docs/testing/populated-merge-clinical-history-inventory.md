# Populated patient merge and unmerge

Target: `release/2026.08`; request [#5715](https://github.com/carlos-emr/carlos/issues/5715).
Run `npm run test:demographic-merge-clinical-history-playwright` on a disposable
installed deployment with `CARLOS_DISPOSABLE_HOST=true`, `EXCLUSIVE=1` and
`DOCUMENT_DIR`. This check must not run concurrently with other checks
(live wrapper: EXCLUSIVE=1).

The original demographic merge workflow remains the action driver. Its optional
acceptance observers add fixtures and clinical inspections without changing the
default check. Missing source/target, self-merge, cycle, forged head and GET
refusal controls all remain active. Setup uses isolated SQL and is not claimed
as coverage of clinical entry or signing.

Each source chart and an unrelated populated control owns two signed revisions
of one UUID, distinct medications/directions and prescription records, allergy
and reaction, a dated WT observation and a stored PDF. An independent SHA-256
oracle verifies each signed revision's text/hash/algorithm/provider/UUID.
Identity reservation includes dangling clinical references before allocating
notes, drugs, prescriptions and document records. No existing row or allocator
is reset.

Before merge, every chart is independently found through Schedule Search and
its native Master Record and E-Chart controls. The test opens native note
revision history, medication/allergy lists and full pages, Browse Notes/document previews and
Measurements/WT history. Exact identities, values, signing records and absence
of foreign or duplicate content are required.

Administration search, record checkboxes, Main Record, confirmation and native
Merge perform the action. The merged head exposes the tail's actual Master
Record link, which is used to open that source chart. Current release contracts
are asserted per module: notes and WT histories retain the selected source owner;
the head's medication, allergy and Browse Notes document lists include the
head and its merged tail, once each. The tail's own document list remains its
own. Each native preview forwards the actual upstream PDF bytes losslessly and
compares them with the independently generated original, with exact HTTP/content
type and document identity checks. This expectation follows `DocumentMergeDemographicDAOImpl`, `DrugMergedDemographicDaoImpl` and
`AllergyMergedDemographicDaoImpl` through `MergedDemographicTemplate.findMerged`; it does not require physical clinical reassignment.

Native Search Merged Records/UnMerge restores independent search and clinical
views for both sources. Complete captured clinical rows, demographics,
signatures, histories, drafts, relationships, document metadata/links and file
hashes must remain exact throughout. The legacy eChart infrastructure row
created by opening a chart is excluded explicitly; clinical rows are not.
The populated control's clinical snapshot is also exact. Merge relationships,
privilege recycling and audit rows retain the original driver's assertions.

Module failures are retained in a final aggregate; later successful modules
cannot erase them. A failed baseline stops before merge. Browser problems remain
fatal and are attributed to the inspection that caused them. Real native merge
POSTs are forwarded unchanged once under durable request supervision; an
unknown write outcome retains every fixture and its private recovery journal.

Cleanup runs after browser teardown and request drain. It removes clinical
children before parents, checks parent ownership and refuses to delete changed
PDF inodes/bytes. A child failure retains every source parent and the control
parent. The file helper retires verified bytes atomically. Successful and failed
installed attempts must independently show zero owned residue; unreached
assertions or missing prerequisites never pass.
