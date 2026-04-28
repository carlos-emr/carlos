## 2024-04-28 - Add ARIA Labels and keyboard focus to admin jobs icons
**Learning:** Found several anchor tags in the job scheduling table that lack `href="javascript:void(0);"` which makes them unfocusable by keyboard. The schedule job icon also lacks an `aria-label` and has a purely decorative `<i>` tag that needs `aria-hidden="true"`.
**Action:** Adding `href="javascript:void(0);"` to interactive `<a>` elements and using `aria-label` for icon-only buttons while hiding the inner icon with `aria-hidden="true"` to improve accessibility.
