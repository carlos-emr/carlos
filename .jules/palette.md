## 2024-04-15 - Missing hrefs break keyboard focus
**Learning:** Found multiple action links `<a>` in admin jobs page using `onclick` but missing `href` attributes, which prevents keyboard navigation (tabbing) from reaching them. Also found an icon-only schedule button missing an ARIA label, and a decorative icon read awkwardly by screen readers.
**Action:** Always add `href="javascript:void(0);"` or a similar valid target to `<a>` tags acting as buttons to ensure keyboard focusability. Add `aria-label` to icon-only action links, and hide decorative inner icons with `aria-hidden="true"`.
