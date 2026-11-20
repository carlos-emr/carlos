# eChart navigation readiness

The eChart requests 22 navigation fragments independently. A column keeps its
loading indicator and `aria-busy="true"` until all its requested fragments have
settled. A failed fragment displays its error without leaving the column busy.
Other fragments remain usable. An empty successful fragment, or a module removed
by the provider's preferences, still counts as a completed request.

`window.carlosNavbarLoadState` records the current load generation, whether the
initial requests have all been scheduled, each module's status, pending count,
and failed module names. Both initial loads and individual `reloadNav` requests
use this state. Replacing a module request or starting a new generation prevents
older callbacks from overwriting current content or changing current readiness.

The shared Playwright `waitForNavbars` helper waits for the current generation's
actual completion, rejects any failed module, and then verifies links on both
sides. Stable link counts and global network idle cannot prove completion: one
slow or failed fragment can leave counts unchanged while other modules render.
Background note polling does not delay navigation readiness.

`echart-navbar-modules-playwright-checks.js` deliberately holds the initial
Preventions request while the other modules complete. It verifies the loading
indicator and busy state remain, that the shared waiter does not finish early,
and that releasing the real response allows completion before auditing links.
The route is always released and removed during cleanup. This browser check
requires deployment of the matching navigation JSP changes.

Focused Node regressions:

```
node --test scripts/echart-navbar-readiness.test.js scripts/playwright-surfaces.test.js
```

They cover a slow last module, failed/empty/removed fragments, HTTP and synchronous
send errors, aborted requests, old generations, same-module retry, populated
columns with a missing completion signal, and a single failure concealed by many
successful links. The test executes the production loader functions extracted
from the JSP; it does not duplicate the loader implementation.
