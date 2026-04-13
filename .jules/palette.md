## 2026-04-13 - Adding explicit ARIA labels to single-character action links
**Learning:** Icon-like text links or buttons (e.g., links that just use 'E' for Encounter or 'T' for Tickler) may read ambiguously or out of context to screen readers, even if they have a 'title' attribute.
**Action:** Always add descriptive `aria-label` attributes to single-character or icon-like action links so screen readers can clearly announce the element's purpose.
