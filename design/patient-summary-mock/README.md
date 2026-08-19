# Patient 9 citation-backed patient overview mock

Interactive, standalone mock of a split-screen general patient-overview review experience. It uses the fabricated Patient 9 record (Brian William Morris) from the [NHS England Synthetic Clinical Notes dataset](https://huggingface.co/datasets/NHSEDataScience/synthetic_clinical_notes).

This is synthetic test data and a product-design concept—not a clinical system or a clinically validated summary.

## Open the mock

Use the local preview server at `http://localhost:4173`. No build or package installation is required. A web server is recommended because the full-note viewer retrieves the primary synthetic CSV files at runtime.

The mock demonstrates:

- A compact CARLOS-style navigation, typography, panels, and Bootstrap-blue interaction treatment
- A clinical-briefing format with a sticky orientation/review rail followed by a problem-oriented general overview synthesized from all 26 retrieved notes
- An explicit warning that the available record covers only one orthopaedic episode and is not a complete longitudinal chart
- Clickable summary sentences
- Multiple sources attached to one sentence
- Complete source-note viewing with automatic scrolling to highlighted evidence
- A source selector for moving between multiple complete documents linked to one claim
- Direct, corroborating, contextual, and conflicting evidence
- Explicit side-by-side conflict narratives: one source position followed by a separately linked “Conversely” position
- Exact source identifiers and links to the primary dataset files
- Fact-ledger and retrieval-coverage views
- Claim confirmation and issue-flagging interactions
- Hard-blocked finalization while high-risk conflicts remain
- Responsive evidence-panel behavior for narrow screens
- A draggable, keyboard-accessible desktop divider for enlarging either the summary or source evidence pane

## Important data note

The evidence pane retrieves and displays the complete `clean_note_text` value for each selected synthetic note, or the complete structured CSV row for patient/admission records. Relevant phrases are highlighted in context and the pane scrolls to the first match. A production implementation should store immutable source-span offsets during extraction rather than rediscovering highlights through phrase matching in the browser.
