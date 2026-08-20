# Abena Bonsu citation-backed patient overview mock

Interactive, standalone mock of a split-screen general patient-overview reading experience. It uses the fabricated Abena Bonsu record from the [NHS England Synthetic Clinical Notes dataset](https://huggingface.co/datasets/NHSEDataScience/synthetic_clinical_notes).

This is synthetic test data and a product-design concept—not a clinical system or a clinically validated summary.

## Open the mock

Use the local preview server at `http://localhost:4173`. No build or package installation is required. A web server is recommended because the full-note viewer retrieves the primary synthetic CSV files at runtime.

The mock demonstrates:

- A compact CARLOS-style navigation, typography, and panel treatment
- A clinical-briefing format with an orientation rail followed by a problem-oriented general overview synthesized from all 45 notes in one admission
- An explicit warning that the available record covers only one acute respiratory admission and is not a complete longitudinal chart
- DOB-derived age calculated at the viewing date, with ages from clinical documents interpreted at each document's date
- A persistent Allergies & safety view that keeps expected clinical categories visible and uses explicit record-limited empty states
- Clickable summary sentences
- Multiple sources attached to one sentence
- Complete source-note viewing with automatic scrolling to highlighted evidence
- A source selector for moving between multiple complete documents linked to one claim
- Direct, corroborating, contextual, and differing source accounts
- Quiet narrative documentation notes that preserve each cited account without assigning reconciliation to the clinician
- Exact source identifiers and links to the primary dataset files
- Fact-ledger and retrieval-coverage views
- Responsive evidence-panel behavior for narrow screens
- A draggable, keyboard-accessible desktop divider for enlarging either the summary or source evidence pane

## Current screenshots

- `abena-overview.png` — problem-oriented overview and orientation rail
- `abena-allergies-safety.png` — allergies and safety categories, including explicit record-limited empty states
- `abena-medication-evidence.png` — medication documentation with the complete-note evidence pane enlarged
- `abena-coverage.png` — retrieval manifest and note-coverage accounting

## Important data note

The mock uses Abena Bonsu's admission `7b266c9e-fb25-4209-b856-95bda6915f12`. The dataset also contains a duplicate synthetic admission (`8ab5fb09-1638-4054-a031-03291ddf8d07`) with the same 45 notes; that duplicate is deliberately excluded from retrieval and counts.

The evidence pane retrieves and displays the complete `clean_note_text` value for each selected synthetic note, or the complete structured CSV row for patient/admission records. Relevant phrases are highlighted in context and the pane scrolls to the first match. A production implementation should store immutable source-span offsets during extraction rather than rediscovering highlights through phrase matching in the browser.
