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
| `carlos-tomcat-dev` | Tomcat 11 + JDK 21 (CARLOS app) | 8080 | 8080 |
| `carlos-drugref-dev` | Tomcat 11 + JDK 21 (drugref2.war) | 8080 | 8180 |
| `carlos-mariadb-dev` | MariaDB | 3306 | 3306 |

Both Tomcat instances listen on container port 8080 — there is no conflict because they run in separate containers on the `carlos-network` bridge. CARLOS resolves `drugref` via Docker's internal DNS.

The `drugref2` database (separate from CARLOS's `oscar_15` database) is created on the same MariaDB instance by `.devcontainer/db/scripts/populate_db.sh`.

## Remote Address Restriction & Override

### The problem

The upstream `drugref2.war` ships with a `META-INF/context.xml` that hardcodes a `RemoteAddrValve` allowing only `127.0.0.1`:

```xml
<Valve className="org.apache.catalina.valves.RemoteAddrValve"
       allow="127\.0\.0\.1|::1|0:0:0:0:0:0:0:1" />
```

This is safe for single-host bare-metal installs (CARLOS and DrugRef on the same machine, both reaching each other over loopback) but **breaks any container or multi-host deployment**: requests from the CARLOS container's bridge IP receive `HTTP 403 Forbidden`.

The container's healthcheck (`curl -fs http://localhost:8080/drugref2/`) still passes because it originates from `127.0.0.1` inside the drugref container itself, so the container reports `healthy` while being unreachable from CARLOS — a confusing failure mode.

### The fix (no upstream change required)

CARLOS ships an external Tomcat context descriptor that overrides the WAR's embedded one. Tomcat resolves context descriptors in this order, **first match wins**:

1. `$CATALINA_BASE/conf/[engine]/[host]/[contextpath].xml` ← our override
2. `META-INF/context.xml` inside the WAR ← upstream's restrictive valve

Files involved:

- **`.devcontainer/drugref/drugref2.xml`** — replacement descriptor using `RemoteCIDRValve` with a permissive but parameterizable default.
- **`.devcontainer/docker-compose.yml`** — bind-mounts the descriptor into `/usr/local/tomcat/conf/Catalina/localhost/drugref2.xml` and exposes a `DRUGREF_CATALINA_OPTS` pass-through.

Default allow list (covers loopback + all RFC 1918 ranges + IPv6 ULA):

```
127.0.0.0/8, 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, ::1, fc00::/7
```

### Customizing the allow list

The override descriptor uses Tomcat's `${name:default}` substitution syntax:

```xml
allow="${drugref.allowedRemoteAddresses:127.0.0.0/8,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,::1,fc00::/7}"
```

Set `drugref.allowedRemoteAddresses` via `CATALINA_OPTS` to override per-deployment. The `docker-compose.yml` exposes this as `DRUGREF_CATALINA_OPTS`:

```bash
# Lock down to a single internal subnet:
DRUGREF_CATALINA_OPTS='-Ddrugref.allowedRemoteAddresses=10.20.30.0/24,::1' \
  docker compose -f .devcontainer/docker-compose.yml up -d --force-recreate drugref

# Add an extra subnet on top of the defaults (full list, comma-separated):
DRUGREF_CATALINA_OPTS='-Ddrugref.allowedRemoteAddresses=127.0.0.0/8,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,::1,fc00::/7,203.0.113.0/24' \
  docker compose -f .devcontainer/docker-compose.yml up -d --force-recreate drugref
```

`RemoteCIDRValve` takes CIDR notation (not regex), comma-separated. Both IPv4 and IPv6 entries are supported in the same list.

### Verifying

From the CARLOS container:

```bash
curl -i http://drugref:8080/drugref2/DrugrefService?wsdl
```

A healthy service returns `HTTP/1.1 200` with the WSDL document. `HTTP/1.1 403` means the requesting IP is outside the allow list — widen `DRUGREF_CATALINA_OPTS`.

## Bare-Metal Deployment

For a single-host install where CARLOS and DrugRef run on the same machine, the upstream localhost-only default already works — no override needed. For split deployments, drop an equivalent `drugref2.xml` under your Tomcat's `conf/Catalina/localhost/` and set `CATALINA_OPTS` per the examples above.

## Related Files

- `.devcontainer/drugref/Dockerfile` — builds the drugref2 image from `carlos-emr/drugref2026`
- `.devcontainer/drugref/drugref2.xml` — context override
- `.devcontainer/docker-compose.yml` — drugref service definition
- `.devcontainer/development/config/shared/volumes/drugref2.properties` — DrugRef's DB connection config
- `.devcontainer/development/config/shared/volumes/carlos.properties` — `drugref_url` setting consumed by CARLOS
- `.devcontainer/db/scripts/populate_db.sh` — creates the `drugref2` database and applies schema patches
