# Administration shell: how framed section pages are hosted

`WEB-INF/jsp/administration/index.jsp` is the Administration shell. Most section
pages do not replace it — they are loaded into an `<iframe id="myFrame">` that the
`.xlink` click handler in `leftNav.jspf` creates inside `#dynamic-content`. A few
entries instead load through jQuery `.load()` into the same container (`a.contentLink`,
`registerFormSubmit`); those are "AJAX mode" and have no frame.

## The contract

The shell owns two things on behalf of every framed page:

1. **Height.** `.dynamic-iframe-content` sizes the frame with `padding-top: 80%` — an
   aspect-ratio box with no relation to the content. Anything taller is clipped and
   gets a nested scrollbar. `growFrameTo()` replaces that box with the real content
   height. It only ever grows, so a page that reports a nonsense height cannot
   collapse the frame.
2. **Scroll position.** After the frame loads a new document the shell must return to
   the top. Wizard buttons ("Next", "Save") sit at the bottom of the framed page, so
   the reader is scrolled *down* when they press one. If the shell stays at that
   offset while the frame swaps in the next step, the reader is looking at the middle
   of a page they have never seen and reasonably concludes the button did nothing.

`scrollFramedContentIntoView(frame)` does both, and the `.xlink` handler binds it to
the frame's `load` event so it runs for the initial document **and** every in-frame
navigation after it — a link, a form post, a wizard step. That coverage matters:
only a handful of legacy pages ask for it themselves.

## `parent.parent.resizeIframe(...)`

Some framed pages call `parent.parent.resizeIframe($('html').height())` from their own
jQuery `ready` callback to request the same thing. `resizeIframe()` is the shell-side
hook for that. Two rules:

- **Do not remove `resizeIframe()` from the shell.** It was commented out during the
  Bootstrap 5 rework. That produced two alpha10 reports at once: Administration >
  Schedule Management > Schedule Setting "saves the schedule but clicking Next does
  nothing" (the shell never scrolled back, so the next wizard step loaded off-screen),
  and a console `Uncaught TypeError: parent.parent.resizeIframe is not a function`
  surfacing at `jquery-3.7.1.min.js:2` — jQuery re-throwing a failed ready callback
  through `jQuery.readyException`.
- **Guard the call site.** A framed page can also be opened standalone or by a
  different shell, where no such function exists:

  ```js
  if (parent && parent.parent && typeof parent.parent.resizeIframe === 'function') {
      parent.parent.resizeIframe($('html').height());
  }
  ```

  An unguarded call throws out of the ready callback and abandons whatever else that
  callback was going to do.

## Regression check

`scripts/schedule-setting-playwright-checks.js` (`npm run test:schedule-setting-playwright`)
drives the three-step Schedule Setting wizard through the shell and asserts that each
step advances, that the newly loaded step's top is in the viewport, that the framed
document is not clipped, and that the sibling Schedule Management pages that call
`resizeIframe()` load without a browser exception. It scrolls the shell to the bottom
before pressing each "Next" — the defect is invisible from a shell that was already at
the top. It writes schedule rows, so run it against a disposable local/dev database.

Point `BASE_URL` at a packaged install to run it through the real front door, which is
what an operator actually uses:

```
BASE_URL=https://<host>/carlos TEST_PASSWORD=<password> \
  node scripts/schedule-setting-playwright-checks.js
```

The wizard's `avail_hour` parameter carries markup-shaped values
(`<MON>Standard</MON>…`). That reads like an XSS payload, but it does not score against
CRS 3.3.8 at paranoia level 1 — libinjection's tag blacklist ignores three-letter tags
other than XML and SVG, and the generic HTML-tag-handler rules are paranoia level 2. A
full run against a packaged install with `SecRuleEngine On` logged zero denials, so the
schedule routes need no WAF exclusion of their own. Re-check that if the paranoia level
is ever raised.
