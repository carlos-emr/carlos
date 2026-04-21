## 2026-04-21 - [Accessible buttons for Admin Jobs]
**Learning:** Icon-only and inline action buttons (like those generated via JS in data tables) often lack accessibility features like proper keyboard focusability and screen reader descriptions.
**Action:** Always ensure that dynamically generated action links or icon-only buttons include valid 'href's (or 'href="javascript:void(0);"'), correct 'aria-label' attributes for context, and use 'aria-hidden="true"' on purely decorative icons to provide an inclusive micro-UX improvement.
