## 2026-04-19 - Invalid href and Keyboard Focusability
**Learning:** In dynamically constructed HTML tables (like the jobs list in this app), anchor tags acting as buttons must use `href="javascript:void(0);"` to ensure keyboard focusability, and icon-only buttons require `aria-label` for screen readers while their inner icons should have `aria-hidden="true"`.
**Action:** Always add `href="javascript:void(0);"` and appropriate ARIA attributes when constructing dynamic action links.
