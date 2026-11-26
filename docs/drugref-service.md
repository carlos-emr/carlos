# DrugRef Service (drugref2)

## Overview

CARLOS EMR uses an external **DrugRef** SOAP service for drug lookup, interaction checking, and ATC classification. The service is a separate web application (`drugref2.war`) maintained at [`carlos-emr/drugref2026`](https://github.com/carlos-emr/drugref2026) and runs in its own Tomcat 11 container in dev/prod.

CARLOS connects via the URL configured in `carlos.properties`:

```properties
drugref_url=http://drugref:8080/drugref2/DrugrefService
```

The endpoint is consumed by the prescription module for drug search, dosing, ATC code resolution, and interaction warnings.

## DevContainer Architecture

| Container | Image | Internal Port | Host Port |
|-----------|-------|---------------|-----------|
| `carlos-tomcat-dev` | Tomcat 11 + JDK 25 (CARLOS app) | 8080 | 8080 |
| `carlos-drugref-dev` | Tomcat 11 + JDK 25 (drugref2.war) | 8080 | 8180 |
| `carlos-mariadb-dev` | MariaDB | 3306 | 3306 |

Both Tomcat instances listen on container port 8080 — there is no conflict because they run in separate containers on the `carlos-network` bridge. CARLOS resolves `drugref` via Docker's internal DNS.

The `drugref2` database (separate from CARLOS's `carlos` database) is created on the same MariaDB instance by `.devcontainer/db/scripts/populate_db.sh`.

## Remote Address Restriction & Override

### The problem

The upstream `drugref2.war` ships with a `META-INF/context.xml` that hardcodes a `RemoteAddrValve` allowing only `127.0.0.1`:

```xml
<Valve className="org.apache.catalina.valves.RemoteAddrValve"
       allow="127\.0\.0\.1|::1|0:0:0:0:0:0:0:1" />
```

This is safe for single-host bare-metal installs (CARLOS and DrugRef on the same machine, both reaching each other over loopback) but **breaks any container or multi-host deployment**: requests from the CARLOS container's bridge IP receive `HTTP 403 Forbidden`.

The container checks the DrugRef servlet over loopback. Check access from the CARLOS container as well because the address filter evaluates each client separately.

### The fix (no upstream change required)

CARLOS ships an external Tomcat context descriptor that overrides the WAR's embedded one. Tomcat resolves context descriptors in this order, **first match wins**:

1. `$CATALINA_BASE/conf/[engine]/[host]/[contextpath].xml` ← our override
2. `META-INF/context.xml` inside the WAR ← upstream's restrictive valve

Files involved:

- **`.devcontainer/drugref/drugref2.xml`** — replacement descriptor using `RemoteCIDRValve` with a hardcoded allow list covering the ranges typical container networks draw from.
- **`.devcontainer/docker-compose.yml`** — bind-mounts the descriptor into `/usr/local/tomcat/conf/Catalina/localhost/drugref2.xml`.

Default allow list (covers loopback, RFC 1918, CGNAT, link-local, and IPv6 ULA/link-local ranges):

```text
127.0.0.0/8, 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 100.64.0.0/10, 169.254.0.0/16, ::1, fc00::/7, fe80::/10
```

### Customizing the allow list

The allow list is **hardcoded** in the descriptor. Tomcat's `RemoteCIDRValve` splits the `allow` attribute on commas before system-property substitution runs, so `${name:a,b,c}` style placeholders with comma-separated defaults fail to parse — there is no `CATALINA_OPTS` / `-D...` override for this list.

To change the allow list, edit `.devcontainer/drugref/drugref2.xml` directly (the file is bind-mounted, so changes take effect on the next container restart — no image rebuild needed):

```xml
<Valve className="org.apache.catalina.valves.RemoteCIDRValve"
       allow="127.0.0.0/8,10.0.0.0/8,..." />
```

`RemoteCIDRValve` takes CIDR notation (not regex), comma-separated. Both IPv4 and IPv6 entries are supported in the same list.

`DRUGREF_CATALINA_OPTS` is still exposed on the container for unrelated Tomcat tuning (heap sizing, GC flags, etc.) but does **not** influence the allow list.

### Verifying

From the CARLOS container:

```bash
curl -i http://drugref:8080/drugref2/DrugrefService
```

The XML-RPC servlet accepts POST and responds to this GET with HTTP 405. The health check also accepts HTTP 200 from older builds. HTTP 403 indicates the address filter refused the client. HTTP 404 indicates the servlet is unavailable at this path.

## Drug-Drug Interaction Warnings on the Rx Page

The staged prescription cards on the Rx page (`SearchDrug3.jsp`) show interaction warnings from
DrugRef's Holbrook data (`fetch("interactions_byATC", atcCodes)`). Issue #4506 restored them; before
that the page never asked.

- **Flow.** Whenever a card is staged or removed (and on load), `share/javascript/rx-interaction-warnings.js`
  requests `GET rx/interactionWarnings?demographicNo=N` (`RxInteractionWarnings2Action`). The server checks
  every staged drug's ATC code against the other staged drugs and the patient's current medications
  (`RxInteractionWarningService`) and answers JSON; the page fills the card's `major_/moderate_/minor_/unknown_<randomId>`
  marker, with the effect, evidence and DrugRef's comment in the tooltip. Interactions among current
  medications alone have no card and are not shown.
- **Warning level.** `RxInteractionWarningLevelResolver` applies `rxInteractionWarningLevel`: the facility
  value, replaced by the provider's preference when set, replaced by the patient's setting when set.
  Low (or not specified) shows everything, Medium moderate and major, High major only, None nothing.
- **No-effect records are not warnings.** DrugRef records some pairs with effect `N` ("has no effect on");
  in the shipped data those are exactly the records with no significance. They are skipped. Any other
  ungraded record is shown at every level except None.
- **Failures are visible.** A DrugRef error, an error struct (`{"Error": ...}`) or an empty answer makes the
  check `unavailable`, and the page says so above the cards; it never renders an unanswered check as a
  card with no interactions.
- **Unclassified drugs are flagged.** A staged drug with no ATC code (a custom drug) cannot be checked; the
  answer lists it under `unchecked` and its card says "Interactions not checked". Free-text custom notes
  are not drugs and are left out.

Browser regression: `scripts/rx-interaction-warnings-playwright-checks.js`.

## Bare-Metal Deployment

For a single-host install where CARLOS and DrugRef run on the same machine, the upstream localhost-only default already works — no override needed. For split deployments, drop an equivalent `drugref2.xml` under your Tomcat's `conf/Catalina/localhost/` with the desired CIDR ranges hardcoded in the `allow` attribute.

## Running the Standalone Image

The image built from `.devcontainer/drugref/Dockerfile` (published as `carlos-drugref-dev`) holds Tomcat and `drugref2.war` and nothing else. Started on its own, Tomcat comes up and answers on port 8080, but the `drugref2` context fails to deploy. Current WARs declare a `RemoteCIDRValve` whose `allow` value is a placeholder with a comma-separated default, which the valve cannot parse, and the image carries no database settings. An answer from Tomcat therefore says nothing about DrugRef.

The supported configuration is the one the Compose `drugref` service applies:

| Requirement | Supplied by |
|-------------|-------------|
| External context descriptor | `.devcontainer/drugref/drugref2.xml` mounted at `/usr/local/tomcat/conf/Catalina/localhost/drugref2.xml` |
| Database settings | `drugref2.properties` mounted at `/root/drugref2.properties` (DrugRef reads `${user.home}/drugref2.properties`) |
| Hibernate dialect | `CATALINA_OPTS=-Dhibernate.dialect=org.hibernate.dialect.MariaDBDialect` |
| Database | MariaDB at the `db_url` host with the `drugref2` schema loaded from `database/mysql/development-drugref.sql` and then `database/mysql/drugref/*.sql`, as `populate_db.sh` does |

Start the database first. Hibernate validates the schema while the context starts, so a database that is missing or still loading takes the whole context down, and it does not retry. DrugRef is ready when `GET /drugref2/DrugrefService` answers HTTP 405, because the XML-RPC servlet accepts only POST. An HTTP 404 there means the context did not deploy; the cause is in `/usr/local/tomcat/logs/localhost.*.log` inside the container, not in `docker logs`.

CI holds the image to this configuration. `scripts/ci/check-drugref-deployment.sh`, run by the `build-drugref` job of the Development Container Images workflow, starts the image with the Compose service's mounts and environment against a database holding the `drugref2` schema and requires that answer. It also requires the same check to reject the bare image and the deployment without its database.

## Related Files

- `.devcontainer/drugref/Dockerfile` — builds the drugref2 image from `carlos-emr/drugref2026`
- `.devcontainer/drugref/drugref2.xml` — context override
- `.devcontainer/docker-compose.yml` — drugref service definition
- `.devcontainer/development/config/shared/volumes/drugref2.properties` — DrugRef's DB connection config
- `.devcontainer/development/config/shared/volumes/carlos.properties` — `drugref_url` setting consumed by CARLOS
- `.devcontainer/db/scripts/populate_db.sh` — creates the `drugref2` database and applies schema patches
- `scripts/ci/check-drugref-deployment.sh` — CI check that the image deploys DrugRef with this configuration
