## 2026-04-14 - [Add ARIA labels to icon-only links in DashboardDisplay]
**Learning:** Icon-only anchor tags using FontAwesome icons need explicit `aria-label` attributes on the anchor, and `aria-hidden="true"` on the icon element itself. Screen readers may not read tooltips (`title` attributes) reliably or may read the raw icon character poorly.
**Action:** Always add `aria-label` to link or button elements containing only decorative or semantic icons, and hide the actual icon element from screen readers using `aria-hidden="true"`.
