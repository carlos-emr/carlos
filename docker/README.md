# CARLOS EMR - Production Container Deployment

Quick-start for deploying CARLOS EMR as a set of containers (podman or docker).
For detailed architecture, backup, upgrade, and troubleshooting guidance see
[docs/production-deployment.md](../docs/production-deployment.md).

## Quick Start

```sh
git clone https://github.com/carlos-emr/carlos.git
cd carlos
./docker/setup.sh
```

The installer will:

1. Detect your container runtime (podman preferred, docker supported)
2. Check prerequisites (disk, RAM, ports, SELinux)
3. Prompt for configuration (province, password, ports, demo data)
4. Generate `.env` and config files (shown before writing)
5. Pull/build container images
6. Start all three services (app, database, drugref)
7. Print the access URL and default credentials

Every shell command is announced with `[RUN]` before execution. Run with
`--dry-run` to see everything without touching your system.

## Prerequisites

- **Container runtime**: [podman](https://podman.io/) 4.0+ (recommended) or
  [docker](https://docs.docker.com/get-docker/) 20.10+ with the Compose v2 plugin
- **Disk space**: 10GB free minimum
- **RAM**: 6GB recommended (4GB minimum)
- **Ports**: 8080 and 8180 available on the host (configurable)

On **RHEL/Fedora/CentOS** with SELinux enforcing, the compose file uses `:Z`
labels automatically - no manual SELinux configuration needed.

On **macOS**, podman requires a Linux VM:

```sh
podman machine init
podman machine start
```

## Installer options

```sh
./docker/setup.sh                       # Interactive (default)
./docker/setup.sh --dry-run             # Show what would happen
./docker/setup.sh --non-interactive     # Use env vars, no prompts
./docker/setup.sh --demo                # Load demo patient data (evaluation)
./docker/setup.sh --province bc         # Set province
./docker/setup.sh --source              # Build WAR from source (slower)
./docker/setup.sh --prebuilt            # Use prebuilt WAR (default)
./docker/setup.sh --help
```

## What gets deployed

| Service | Port | Description |
|---------|------|-------------|
| carlos | 8080 | Tomcat + CARLOS EMR WAR |
| db | (internal) | MariaDB 10.5 with schema + migrations baked in |
| drugref | 8180 | Legacy drug reference service |

All containers run with a restart policy of `unless-stopped` and proper
health checks. Data is persisted in two named volumes:
- `carlos-db-data` - database files
- `carlos-documents` - patient documents, eForm images, billing files

**Both volumes contain PHI and must be backed up regularly.**

## Default login

After setup completes:

- **URL**: http://localhost:8080/carlos/
- **Username**: `carlosdoc`
- **Password**: `carlos2026`
- **PIN**: `1117`

These credentials expire one month after first login. Change them immediately.

## Useful commands

```sh
# Navigate to compose directory first
cd docker/production

# Start/stop (data preserved)
docker compose stop
docker compose start

# View logs
docker compose logs -f carlos      # app logs
docker compose logs -f db          # database logs

# Restart after changing carlos.properties
docker compose restart carlos

# Full teardown (data preserved)
docker compose down

# Full teardown INCLUDING database - destroys all data!
docker compose down --volumes
```

Replace `docker compose` with `podman compose` or `podman-compose` if using
podman.

## WAR Source

The setup supports two paths for getting the CARLOS WAR into the container:

### Prebuilt (default, faster)

Expects a WAR file or exploded WAR directory at one of:
- `docker/carlos.war` (file)
- `target/carlos-0-SNAPSHOT/` (exploded WAR from `make install`)

Download from [GitHub Releases](https://github.com/carlos-emr/carlos/releases):

```sh
curl -L -o docker/carlos.war \
    https://github.com/carlos-emr/carlos/releases/latest/download/carlos.war
```

### Build from source (slower, ~10 minutes)

Multi-stage Dockerfile that clones the repo and compiles the WAR with Maven:

```sh
./docker/setup.sh --source
```

Override branch/tag with the `CARLOS_GIT_REF` environment variable.

## Demo data vs production data

The database can be initialized in two modes:

- **Production** (default, `LOAD_DEMO_DATA=false`): Schema + reference data
  only. No demo patients. Only the default admin account exists.
- **Demo** (`LOAD_DEMO_DATA=true` or `--demo` flag): Loads synthetic patients,
  appointments, and providers from `development.sql` plus name sanitization.
  For evaluation and testing only - not for production use.

This is a one-time decision per database. To change modes, destroy the
`carlos-db-data` volume and re-run setup.

## Security notes

- Containers run as non-root user `carlos` (uid 1000)
- Tomcat version is hidden from HTTP response headers (`server="CARLOS"`)
- Database is not exposed to the host by default (uncomment in
  `docker-compose.yml` if external DBA tools need access)
- All bind mounts are read-only from the container's perspective
- The database password in `.env` is chmod 600 (owner read-only)
- No debug ports, no manager webapps, no dev tools in production images

See [docs/production-deployment.md](../docs/production-deployment.md) for
reverse proxy (HTTPS), backup procedures, upgrade process, and advanced
configuration.
