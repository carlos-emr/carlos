# CARLOS EMR - Production Deployment Guide

This guide covers deploying CARLOS EMR as containers for production clinical
use. For a quick start, see [docker/README.md](../docker/README.md). For the
in-container development workflow, see [.devcontainer/README.md](../.devcontainer/README.md).

> **⚠️ Healthcare data warning**
>
> CARLOS stores Personal Health Information (PHI) subject to HIPAA, PIPEDA,
> and provincial privacy legislation. The container defaults described in
> this document cover basic hardening, but **clinics are responsible** for:
>
> - Physical and network security of the host
> - TLS termination (reverse proxy required for production use)
> - Backup encryption and off-site retention
> - Access control, audit log retention, and breach response policies
> - Jurisdiction-specific compliance (BC PIPA, ON PHIPA, etc.)

## Architecture

A production deployment consists of three containers on a shared bridge network:

```
┌─────────────────────────────────────────────────┐
│                  Host (linux/macOS)             │
│                                                 │
│  ┌────────────┐  ┌────────────┐  ┌───────────┐  │
│  │  carlos    │  │     db     │  │ drugref   │  │
│  │  Tomcat 11 │←→│ MariaDB    │←→│ Tomcat 9  │  │
│  │  JDK 21    │  │ 10.5       │  │ JDK 11    │  │
│  │  :8080     │  │ :3306      │  │ :8080     │  │
│  └─────┬──────┘  └─────┬──────┘  └─────┬─────┘  │
│        │               │               │        │
│        ▼               ▼               ▼        │
│  ┌──────────────────────────────────────────┐   │
│  │      carlos-net (bridge network)          │   │
│  └──────────────────────────────────────────┘   │
│                                                 │
│  Named volumes (persistent):                    │
│    carlos-db-data      (PHI - BACK UP)          │
│    carlos-documents    (PHI - BACK UP)          │
└─────────────────────────────────────────────────┘
     :8080 (carlos)         :8180 (drugref)
         │                       │
         ▼                       ▼
       clinic LAN or reverse proxy
```

- **carlos**: The main EMR application. Tomcat 11 + JDK 21 + CARLOS WAR.
  Runs as non-root user `carlos` (uid 1000).
- **db**: MariaDB 10.5 with the full CARLOS schema, ICD codes, SNOMED CT,
  province-specific reference data, and all migration scripts baked in at
  image build time.
- **drugref**: Legacy drug reference service (Tomcat 9 + JDK 11). Pulled from
  `ghcr.io/carlos-emr/carlos-drugref:latest`.

The CARLOS app talks to both the database (`db:3306`) and DrugRef
(`drugref:8080`) using Docker's internal DNS. Nothing is exposed outside the
`carlos-net` bridge network unless you explicitly publish a port.

## System Requirements

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| CPU      | 2 cores | 4 cores     |
| RAM      | 4 GB    | 8 GB        |
| Disk     | 10 GB   | 100 GB (grows with document storage) |
| OS       | Linux with cgroup v2, macOS with podman machine | Any recent x86_64 or arm64 Linux |

The app container's JVM is tuned for `-Xmx3G`, the database for a 2GB InnoDB
buffer pool. Adjust in `docker-compose.yml` (`mem_limit`) and `Dockerfile`
(`CATALINA_OPTS`) if your host has less RAM.

## Installation

### Automated (recommended)

```sh
git clone https://github.com/carlos-emr/carlos.git
cd carlos
./docker/setup.sh
```

See [docker/README.md](../docker/README.md) for installer options.

### Manual

If you prefer to inspect every step:

```sh
cd docker/production

# Create .env from template
cp .env.example .env
"${EDITOR:-nano}" .env           # set DB_ROOT_PASSWORD, etc.

# Generate carlos.properties from template (substitutes ${DB_ROOT_PASSWORD})
DB_ROOT_PASSWORD="$(grep ^DB_ROOT_PASSWORD .env | cut -d= -f2)" \
    envsubst '${DB_ROOT_PASSWORD}' < config/carlos.properties.template > config/carlos.properties

DB_ROOT_PASSWORD="$(grep ^DB_ROOT_PASSWORD .env | cut -d= -f2)" \
    envsubst '${DB_ROOT_PASSWORD}' < config/drugref2.properties.template > config/drugref2.properties

# Build + start
docker compose build
docker compose up -d
docker compose logs -f carlos
```

## Configuration Reference

### Environment variables (`.env`)

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_ROOT_PASSWORD` | (required) | Database root password. Required. No sane default - set to a strong random value. |
| `CARLOS_PROVINCE` | `on` | `on` (Ontario) or `bc` (British Columbia). Determines schema extensions, billing codes, reference data. |
| `LOAD_DEMO_DATA` | `false` | `true` loads demo patients (evaluation only). Cannot be changed after init. |
| `CARLOS_PORT` | `8080` | Host port for the app. Must be >1024 for rootless podman. |
| `DRUGREF_PORT` | `8180` | Host port for drug reference service. |
| `WAR_PATH` | `docker/carlos.war` | Path to WAR file or exploded directory (prebuilt mode). |
| `TZ` | `America/Toronto` | Timezone for audit log timestamps. |

### Application properties (`config/carlos.properties`)

The full production properties file is derived from the dev template and has
1169 lines covering:

- Database connection (filled in by setup.sh)
- Module activation (Fax enabled; CAISI, OLIS, HRM, etc. disabled by default)
- UI customization (logos, clinic name, confidentiality statements)
- Date/time formats
- Integration URLs (help, drug reference)

Edit `config/carlos.properties` and restart the app container to apply
changes:

```sh
cd docker/production
"${EDITOR:-nano}" config/carlos.properties
docker compose restart carlos
```

### Tomcat configuration (`config/server.xml`)

Hardened versus the dev container:

- `server="CARLOS"` hides the Tomcat version string
- `xpoweredBy="false"` suppresses `X-Powered-By` header
- `maxThreads="200"` for production capacity
- `RemoteIpValve` honors `X-Forwarded-For` / `X-Forwarded-Proto` from a reverse proxy
- Access log format includes response time and user-agent for audit trail
- No AJP connector, no Manager/Host-Manager webapps

### Database configuration (`config/my.cnf`)

Production tuning versus dev:

- `innodb_doublewrite = 1` (data safety, was off in dev for speed)
- `innodb_flush_log_at_trx_commit = 1` (ACID durability on crash)
- Slow query log enabled at 2-second threshold
- `innodb_buffer_pool_size = 2G` (tune down for smaller hosts)

## Data Persistence & Backup

Two named Docker volumes hold data that must survive container recreation:

| Volume | Contents | PHI? |
|--------|----------|------|
| `carlos-db-data` | MariaDB data directory (the full clinical database) | **Yes** |
| `carlos-documents` | Patient documents, eForm image uploads, billing files | **Yes** |

### Manual backup

```sh
# Database dump (preferred - portable across DB versions)
docker compose exec db mysqldump \
    --single-transaction --quick --lock-tables=false \
    -u root -p"$DB_ROOT_PASSWORD" oscar > oscar-$(date +%F).sql

# Documents backup (tar the volume)
docker run --rm \
    -v carlos_carlos-documents:/data:ro \
    -v "$PWD":/backup \
    alpine tar czf /backup/documents-$(date +%F).tar.gz /data
```

Store backups **encrypted** and **off-site**. The database dump contains full
PHI in plaintext.

### Restore

```sh
# Stop the app (but leave db running)
docker compose stop carlos

# Restore database
docker compose exec -T db mysql -u root -p"$DB_ROOT_PASSWORD" oscar < oscar-YYYY-MM-DD.sql

# Restore documents
docker run --rm \
    -v carlos_carlos-documents:/data \
    -v "$PWD":/backup \
    alpine sh -c 'cd / && tar xzf /backup/documents-YYYY-MM-DD.tar.gz'

# Bring the app back up
docker compose start carlos
```

### Scheduled backups

A simple cron example (host-side):

```cron
# Nightly backup at 02:00 - adjust paths and retention policy
0 2 * * * cd /opt/carlos/docker/production && \
    /opt/carlos/scripts/backup.sh >> /var/log/carlos-backup.log 2>&1
```

Ship backup scripts separately - they should encrypt with gpg and upload to
S3/B2/whatever your compliance framework accepts.

## Reverse Proxy & TLS

CARLOS must not be exposed to the internet over plain HTTP in production.
Put it behind nginx/caddy/traefik with TLS termination.

### nginx example

```nginx
upstream carlos {
    server 127.0.0.1:8080;
}

server {
    listen 443 ssl http2;
    server_name emr.myclinic.ca;

    ssl_certificate     /etc/letsencrypt/live/emr.myclinic.ca/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/emr.myclinic.ca/privkey.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;

    # CARLOS can upload large lab files and scanned documents
    client_max_body_size 100M;

    # Audit trail: real client IP forwarded to Tomcat via RemoteIpValve
    location / {
        proxy_pass http://carlos;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 300s;
    }
}

server {
    listen 80;
    server_name emr.myclinic.ca;
    return 301 https://$host$request_uri;
}
```

Narrow the `RemoteIpValve` `internalProxies` regex in `config/server.xml` to
match your proxy's IP before going live.

## Upgrades

### Upgrading CARLOS (new WAR)

```sh
cd docker/production

# Option A: prebuilt WAR
curl -L -o ../carlos.war \
    https://github.com/carlos-emr/carlos/releases/download/vX.Y.Z/carlos.war
docker compose build --no-cache carlos
docker compose up -d carlos

# Option B: build from source
docker compose build --no-cache --build-arg GIT_REF=vX.Y.Z carlos
docker compose up -d carlos
```

Database migrations baked into the new image run automatically via the
`/docker-entrypoint-initdb.d/init-db.sh` entrypoint on a fresh volume.
**For existing volumes, migrations must be applied manually** - the init
script only runs on an empty data directory. See the `database/mysql/updates/`
directory for the migration script corresponding to your upgrade path.

### Upgrading MariaDB

MariaDB major version upgrades can require data migration. Do a full dump,
upgrade the image, and restore.

## Troubleshooting

### App container won't start

```sh
docker compose logs carlos
```

Common causes:

- **Database not ready** - wait for `docker compose ps` to show db as
  `healthy`. First startup takes 2-3 minutes while schema loads.
- **carlos.properties not found** - the file must exist in `config/`.
  setup.sh generates it; if you ran compose manually, use the envsubst
  command from the "Manual" install section.
- **WAR path wrong** - confirm `WAR_PATH` in `.env` points at an existing
  file or directory.

### Database initialization hung

First startup applies dozens of SQL files. It can take 3-5 minutes on slow
disks. Check progress:

```sh
docker compose logs -f db | grep -E "(loading|applying|complete)"
```

If truly stuck (no log output for 5+ minutes), restart:

```sh
docker compose down --volumes   # destroys incomplete data!
docker compose up -d
```

### Podman-specific: SELinux permission denied

If containers can't read `carlos.properties` or `drugref2.properties`:

```sh
# Verify :Z labels are in compose file (they are by default)
grep ':Z' docker/production/docker-compose.yml

# Manually relabel if needed
chcon -Rt container_file_t docker/production/config/
```

### Podman-specific: rootless port binding fails

```
error listen tcp4 0.0.0.0:80: listen: permission denied
```

Rootless podman can't bind ports <1024. Use 8080/8180 (default) or raise the
unprivileged port threshold:

```sh
sudo sysctl net.ipv4.ip_unprivileged_port_start=80
```

## Runtime Directories

The app container writes to several filesystem paths inside the container,
all of which are persisted via the `carlos-documents` named volume. See
[runtime-directories.md](./runtime-directories.md) for the complete list and
their `carlos.properties` keys.

The most important paths:

- `/var/lib/OscarDocument/oscar/document` - uploaded patient documents (PDFs, scans)
- `/var/lib/OscarDocument/oscar/eform/images` - eForm image assets
- `/var/lib/OscarDocument/oscar/billing/*` - billing file downloads & invoices

## Security Hardening Checklist

- [ ] Change default `carlosdoc` password and PIN at first login
- [ ] Set strong random `DB_ROOT_PASSWORD` in `.env` (setup.sh does this)
- [ ] Deploy behind TLS-terminating reverse proxy (see nginx example above)
- [ ] Restrict `RemoteIpValve.internalProxies` to your proxy's actual IP
- [ ] Firewall port 3306 from external access (default: not exposed)
- [ ] Rotate backups to off-site encrypted storage
- [ ] Enable multi-factor authentication for provider accounts
- [ ] Review and restrict `.claude/settings.json` if running CARLOS with
      Claude Code integration (see the warning at the top of CLAUDE.md)
- [ ] Schedule OS-level patching for the container host
- [ ] Set up log aggregation / SIEM for the Tomcat access log
- [ ] Document your incident response and breach notification procedures

## Related Documentation

- [docker/README.md](../docker/README.md) - Quick start and installer options
- [CLAUDE.md](../CLAUDE.md) - Architecture overview and development context
- [.devcontainer/README.md](../.devcontainer/README.md) - Development environment
- [docs/runtime-directories.md](./runtime-directories.md) - Filesystem layout reference
- [docs/csrf-protection-architecture.md](./csrf-protection-architecture.md) - CSRF details
- [docs/fax-provider-configuration-and-ux.md](./fax-provider-configuration-and-ux.md) - Fax module
