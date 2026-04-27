## 2024-04-27 - Enhance Accessibility for Job Management Table

**Learning:** When generating interactive table rows dynamically via JavaScript strings, anchor links that act as buttons (e.g., executing an `onclick` function) are often created without `href` attributes, making them inaccessible to keyboard navigation via the `tab` key. Furthermore, the `href="javascript:void();"` syntax is invalid JS, and should be `href="javascript:void(0);"`. Icon-only links (like a calendar icon to schedule a job) lack accessible names if they don't have an `aria-label`, and the inner purely decorative icons should be hidden with `aria-hidden="true"`.

**Action:** Add valid `href="javascript:void(0);"` to all interactive anchor tags created dynamically in JS templates so they become keyboard focusable. Ensure icon-only actionable links have an explicit `aria-label`, and add `aria-hidden="true"` to their internal purely decorative font-awesome `<i class="...">` elements.
