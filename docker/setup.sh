#!/usr/bin/env bash
#
# =============================================================================
# CARLOS EMR - Production Container Installer
# =============================================================================
#
# Interactive installer that sets up CARLOS EMR as a set of containers using
# podman (preferred) or docker. Transparent by design - every action is
# announced before execution, and --dry-run shows commands without running
# anything.
#
# Usage:
#   ./setup.sh                       # Interactive install
#   ./setup.sh --dry-run             # Show what would happen
#   ./setup.sh --non-interactive     # Use env vars, no prompts (for automation)
#   ./setup.sh --demo                # Load demo patient data (evaluation only)
#   ./setup.sh --province bc         # Set province non-interactively
#   ./setup.sh --help
#
# Environment variables (respected in --non-interactive mode):
#   DB_ROOT_PASSWORD    Required if non-interactive
#   CARLOS_PROVINCE     on|bc (default: on)
#   LOAD_DEMO_DATA      true|false (default: false)
#   CARLOS_PORT         default 8080
#   DRUGREF_PORT        default 8180
#   WAR_SOURCE          prebuilt|source (default: prebuilt)
#   WAR_PATH            path/URL to prebuilt WAR
#   CARLOS_GIT_REF      branch/tag for source build (default: develop)
#
# License: GPL-2.0+

set -eo pipefail

# =============================================================================
# Globals
# =============================================================================

# Resolve script directory (works when invoked via symlink too).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_DIR="$SCRIPT_DIR/production"
CONFIG_DIR="$COMPOSE_DIR/config"
ENV_FILE="$COMPOSE_DIR/.env"

DRY_RUN=0
NON_INTERACTIVE=0
RUNTIME=""           # Populated by detect_runtime: "podman" or "docker"
COMPOSE_CMD=""       # Populated by detect_runtime: e.g. "podman-compose"

# ANSI colors (disabled if stdout is not a TTY)
if [ -t 1 ]; then
    C_RED=$'\033[31m'
    C_GREEN=$'\033[32m'
    C_YELLOW=$'\033[33m'
    C_BLUE=$'\033[34m'
    C_BOLD=$'\033[1m'
    C_RESET=$'\033[0m'
else
    C_RED=""; C_GREEN=""; C_YELLOW=""; C_BLUE=""; C_BOLD=""; C_RESET=""
fi

# =============================================================================
# Output helpers
# =============================================================================

info()    { printf '%s[INFO]%s %s\n'    "$C_BLUE"   "$C_RESET" "$*"; }
success() { printf '%s[OK]%s %s\n'      "$C_GREEN"  "$C_RESET" "$*"; }
warn()    { printf '%s[WARN]%s %s\n'    "$C_YELLOW" "$C_RESET" "$*" >&2; }
fail()    { printf '%s[FAIL]%s %s\n'    "$C_RED"    "$C_RESET" "$*" >&2; exit 1; }

# Log a command that is about to run (for transparency) and either run it or
# simulate it in --dry-run mode.
run() {
    printf '%s[RUN]%s %s\n' "$C_BOLD" "$C_RESET" "$*"
    if [ "$DRY_RUN" -eq 1 ]; then
        return 0
    fi
    "$@"
}

banner() {
    cat <<'EOF'

 ██████╗ █████╗ ██████╗ ██╗      ██████╗ ███████╗    ███████╗███╗   ███╗██████╗
██╔════╝██╔══██╗██╔══██╗██║     ██╔═══██╗██╔════╝    ██╔════╝████╗ ████║██╔══██╗
██║     ███████║██████╔╝██║     ██║   ██║███████╗    █████╗  ██╔████╔██║██████╔╝
██║     ██╔══██║██╔══██╗██║     ██║   ██║╚════██║    ██╔══╝  ██║╚██╔╝██║██╔══██╗
╚██████╗██║  ██║██║  ██║███████╗╚██████╔╝███████║    ███████╗██║ ╚═╝ ██║██║  ██║
 ╚═════╝╚═╝  ╚═╝╚═╝  ╚═╝╚══════╝ ╚═════╝ ╚══════╝    ╚══════╝╚═╝     ╚═╝╚═╝  ╚═╝

            Production Container Installer

EOF
}

usage() {
    sed -n '3,35p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    exit 0
}

# =============================================================================
# Phase 1: Runtime detection (podman preferred, docker fallback)
# =============================================================================

detect_runtime() {
    info "Detecting container runtime..."

    # Podman first (preferred per user requirement). Check both the native
    # `podman compose` subcommand (podman 4.0+) and the Python podman-compose.
    if command -v podman >/dev/null 2>&1; then
        RUNTIME="podman"
        if podman compose version >/dev/null 2>&1; then
            COMPOSE_CMD="podman compose"
        elif command -v podman-compose >/dev/null 2>&1; then
            COMPOSE_CMD="podman-compose"
            local pc_version
            pc_version=$(podman-compose --version 2>/dev/null | grep -oE '[0-9]+\.[0-9]+' | head -1 || echo "0.0")
            # depends_on.condition: service_healthy requires podman-compose 1.0+.
            # Warn but don't fail - user may have manually edited the compose file.
            if [ "$(printf '%s\n1.0\n' "$pc_version" | sort -V | head -1)" != "1.0" ]; then
                warn "podman-compose $pc_version is older than 1.0."
                warn "depends_on health conditions may not be honored. Consider upgrading:"
                warn "  pip install --upgrade podman-compose"
            fi
        else
            fail "Found podman but neither 'podman compose' nor 'podman-compose' is available.
       Install one:
         - Native:  (included with podman 4.0+, may need plugin on older hosts)
         - Python:  pip install podman-compose"
        fi

        # On macOS, podman requires a Linux VM (podman machine). Docker Desktop
        # handles this transparently; podman does not.
        if [ "$(uname -s)" = "Darwin" ]; then
            if ! podman machine list --format '{{.Running}}' 2>/dev/null | grep -q true; then
                warn "podman machine is not running."
                warn "On macOS, podman needs a Linux VM. Start one with:"
                warn "  podman machine init       (first time only)"
                warn "  podman machine start"
                if [ "$NON_INTERACTIVE" -eq 0 ]; then
                    read -r -p "Continue anyway? [y/N] " reply
                    [ "$reply" = "y" ] || [ "$reply" = "Y" ] || exit 1
                fi
            fi
        fi

        success "Using $RUNTIME with '$COMPOSE_CMD'"
        return
    fi

    # Docker fallback. Prefer v2 plugin (`docker compose`) over legacy (`docker-compose`).
    if command -v docker >/dev/null 2>&1; then
        RUNTIME="docker"
        if docker compose version >/dev/null 2>&1; then
            COMPOSE_CMD="docker compose"
        elif command -v docker-compose >/dev/null 2>&1; then
            COMPOSE_CMD="docker-compose"
            warn "Using legacy docker-compose v1. Consider upgrading to the Compose v2 plugin."
        else
            fail "Found docker but neither 'docker compose' nor 'docker-compose' is available.
       Install the Compose v2 plugin:
         https://docs.docker.com/compose/install/"
        fi
        success "Using $RUNTIME with '$COMPOSE_CMD'"
        return
    fi

    fail "Neither podman nor docker is installed.
       Install one of:
         - Podman (recommended): https://podman.io/getting-started/installation
         - Docker:               https://docs.docker.com/get-docker/"
}

# =============================================================================
# Phase 2: Prerequisites
# =============================================================================

check_prereqs() {
    info "Checking prerequisites..."

    # Disk space: need ~10GB for images, WAR, database volume, and growth headroom.
    local avail_kb
    avail_kb=$(df -Pk "$SCRIPT_DIR" | awk 'NR==2 {print $4}')
    local avail_gb=$((avail_kb / 1024 / 1024))
    if [ "$avail_gb" -lt 10 ]; then
        warn "Only ${avail_gb}GB free on $(df -P "$SCRIPT_DIR" | awk 'NR==2 {print $6}'). Recommend 10GB+."
    else
        success "Disk space: ${avail_gb}GB available"
    fi

    # Memory: CARLOS JVM heap is 3G + MariaDB buffer pool 2G + overhead. 4GB
    # total is tight but workable for small clinics.
    local total_mem_kb=0
    if [ -r /proc/meminfo ]; then
        total_mem_kb=$(awk '/^MemTotal:/ {print $2}' /proc/meminfo)
    elif [ "$(uname -s)" = "Darwin" ]; then
        total_mem_kb=$(($(sysctl -n hw.memsize) / 1024))
    fi
    local total_mem_gb=$((total_mem_kb / 1024 / 1024))
    if [ "$total_mem_gb" -gt 0 ] && [ "$total_mem_gb" -lt 4 ]; then
        warn "Only ${total_mem_gb}GB RAM detected. CARLOS recommends 6GB+ for production."
    elif [ "$total_mem_gb" -gt 0 ]; then
        success "RAM: ${total_mem_gb}GB"
    fi

    # SELinux: inform the user. Our compose file uses :Z labels so this should
    # just work, but it's worth flagging.
    if command -v getenforce >/dev/null 2>&1; then
        local se
        se=$(getenforce 2>/dev/null || echo "")
        if [ "$se" = "Enforcing" ]; then
            info "SELinux is Enforcing. docker-compose.yml uses :Z labels for bind mounts."
        fi
    fi

    # Cgroup v2 check - recommended for rootless podman memory limits.
    if [ "$RUNTIME" = "podman" ] && [ "$(id -u)" -ne 0 ]; then
        if [ -f /sys/fs/cgroup/cgroup.controllers ]; then
            if ! grep -q memory /sys/fs/cgroup/cgroup.controllers 2>/dev/null; then
                warn "cgroup v2 memory controller not enabled for your user."
                warn "Memory limits (mem_limit) may not be enforced in rootless mode."
            fi
        else
            warn "System appears to use cgroup v1. Rootless podman with mem_limit works"
            warn "best with cgroup v2. Memory limits may be ignored."
        fi
    fi
}

check_port_free() {
    local port="$1"
    if command -v ss >/dev/null 2>&1; then
        ! ss -ltn "sport = :$port" 2>/dev/null | grep -q LISTEN
    elif command -v netstat >/dev/null 2>&1; then
        ! netstat -ln 2>/dev/null | grep -q ":$port "
    elif command -v lsof >/dev/null 2>&1; then
        ! lsof -i ":$port" >/dev/null 2>&1
    else
        # Can't check - assume free and let the container engine report any conflict.
        return 0
    fi
}

check_ports() {
    local port
    for port in "$CARLOS_PORT" "$DRUGREF_PORT"; do
        if ! check_port_free "$port"; then
            warn "Port $port appears to be in use. Stopping the conflicting service"
            warn "or changing the port in .env is required before 'up' will succeed."
        fi
        # Rootless podman can't bind ports < 1024.
        if [ "$RUNTIME" = "podman" ] && [ "$(id -u)" -ne 0 ] && [ "$port" -lt 1024 ]; then
            fail "Port $port < 1024 cannot be bound by rootless podman. Choose a higher port."
        fi
    done
}

# =============================================================================
# Phase 3: Interactive configuration
# =============================================================================

# Generate a strong random password. Uses openssl if available, falls back to
# /dev/urandom. 32 characters of base64 = ~192 bits of entropy.
gen_password() {
    if command -v openssl >/dev/null 2>&1; then
        openssl rand -base64 24 | tr -d '=+/' | head -c 32
    else
        LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 32
    fi
}

prompt_default() {
    local prompt="$1" default="$2" var
    if [ "$NON_INTERACTIVE" -eq 1 ]; then
        printf '%s' "$default"
        return
    fi
    read -r -p "$prompt [$default]: " var
    printf '%s' "${var:-$default}"
}

configure() {
    info "Gathering configuration..."

    # Province
    if [ -z "${CARLOS_PROVINCE:-}" ]; then
        while : ; do
            CARLOS_PROVINCE=$(prompt_default "Province (on/bc)" "on")
            case "$CARLOS_PROVINCE" in on|bc) break;; *) warn "Enter 'on' or 'bc'";; esac
        done
    fi

    # Database password - generate a strong default if not provided
    if [ -z "${DB_ROOT_PASSWORD:-}" ]; then
        local default_pw
        default_pw=$(gen_password)
        if [ "$NON_INTERACTIVE" -eq 1 ]; then
            fail "DB_ROOT_PASSWORD must be set when running non-interactively."
        fi
        echo ""
        echo "A random password has been generated for the database:"
        echo "  $default_pw"
        echo ""
        echo "Press Enter to use this password, or type your own:"
        read -r user_pw
        DB_ROOT_PASSWORD="${user_pw:-$default_pw}"
    fi

    # Demo data toggle
    if [ -z "${LOAD_DEMO_DATA:-}" ]; then
        if [ "$NON_INTERACTIVE" -eq 1 ]; then
            LOAD_DEMO_DATA="false"
        else
            read -r -p "Load demo patient data for evaluation? (y/N): " reply
            case "$reply" in y|Y|yes|YES) LOAD_DEMO_DATA="true";; *) LOAD_DEMO_DATA="false";; esac
        fi
    fi

    # Ports
    CARLOS_PORT=$(prompt_default "CARLOS HTTP port" "${CARLOS_PORT:-8080}")
    DRUGREF_PORT=$(prompt_default "DrugRef HTTP port" "${DRUGREF_PORT:-8180}")

    # WAR source
    if [ -z "${WAR_SOURCE:-}" ]; then
        if [ "$NON_INTERACTIVE" -eq 1 ]; then
            WAR_SOURCE="prebuilt"
        else
            echo ""
            echo "WAR source options:"
            echo "  1) prebuilt - Use a pre-built WAR file (faster, recommended)"
            echo "  2) source   - Compile from source in a container (slower, ~10 min)"
            read -r -p "Choice [1]: " reply
            case "$reply" in 2) WAR_SOURCE="source";; *) WAR_SOURCE="prebuilt";; esac
        fi
    fi

    # Timezone (best effort detection)
    if [ -z "${TZ:-}" ]; then
        if [ -L /etc/localtime ]; then
            TZ=$(readlink /etc/localtime | sed 's|.*/zoneinfo/||')
        fi
        TZ="${TZ:-America/Toronto}"
    fi

    check_ports
}

# =============================================================================
# Phase 4: File generation
# =============================================================================

# envsubst-lite: replaces ${VAR} with the value of $VAR. We avoid depending on
# gettext (which provides envsubst) since it's not installed everywhere.
subst_template() {
    local template="$1" output="$2"
    # Only substitute a specific whitelist of variables. Using full envsubst
    # would break any legitimate ${...} reference in the template (e.g. the
    # echart signature patterns use Java-style ${DATE}).
    sed \
        -e "s|\${DB_ROOT_PASSWORD}|${DB_ROOT_PASSWORD}|g" \
        "$template" > "$output"
}

generate_files() {
    info "Generating configuration files..."

    if [ "$DRY_RUN" -eq 1 ]; then
        info "(dry-run) would write: $ENV_FILE"
        info "(dry-run) would write: $CONFIG_DIR/carlos.properties"
        info "(dry-run) would write: $CONFIG_DIR/drugref2.properties"
        return 0
    fi

    # .env file
    cat > "$ENV_FILE" <<EOF
# Generated by setup.sh on $(date -u '+%Y-%m-%dT%H:%M:%SZ')
# Edit and re-run '$COMPOSE_CMD up -d' to apply changes.
DB_ROOT_PASSWORD=$DB_ROOT_PASSWORD
CARLOS_PROVINCE=$CARLOS_PROVINCE
LOAD_DEMO_DATA=$LOAD_DEMO_DATA
CARLOS_PORT=$CARLOS_PORT
DRUGREF_PORT=$DRUGREF_PORT
WAR_PATH=${WAR_PATH:-docker/carlos.war}
TZ=$TZ
EOF
    chmod 600 "$ENV_FILE"  # Contains password - restrict to owner
    success "Wrote $ENV_FILE"

    # carlos.properties (substitute DB password into template)
    subst_template \
        "$CONFIG_DIR/carlos.properties.template" \
        "$CONFIG_DIR/carlos.properties"
    chmod 644 "$CONFIG_DIR/carlos.properties"
    success "Wrote $CONFIG_DIR/carlos.properties"

    # drugref2.properties
    subst_template \
        "$CONFIG_DIR/drugref2.properties.template" \
        "$CONFIG_DIR/drugref2.properties"
    chmod 644 "$CONFIG_DIR/drugref2.properties"
    success "Wrote $CONFIG_DIR/drugref2.properties"
}

# =============================================================================
# Phase 5: WAR acquisition (prebuilt mode)
# =============================================================================

acquire_war() {
    [ "$WAR_SOURCE" = "source" ] && return 0

    local war_dest="$REPO_ROOT/docker/carlos.war"
    if [ -n "${WAR_PATH:-}" ] && [ -e "$REPO_ROOT/$WAR_PATH" ]; then
        info "Using existing WAR at $WAR_PATH"
        return 0
    fi

    # Check for a locally built WAR from `make install` first
    if [ -d "$REPO_ROOT/target/carlos-0-SNAPSHOT" ]; then
        info "Found locally-built exploded WAR at target/carlos-0-SNAPSHOT/"
        WAR_PATH="target/carlos-0-SNAPSHOT"
        # Update .env with the actual path
        sed -i.bak "s|^WAR_PATH=.*|WAR_PATH=$WAR_PATH|" "$ENV_FILE" && rm -f "$ENV_FILE.bak"
        return 0
    fi

    warn "No prebuilt WAR found at $war_dest or target/carlos-0-SNAPSHOT/."
    warn "Build options:"
    warn "  1. Run 'make install' in a devcontainer to produce target/carlos-0-SNAPSHOT/"
    warn "  2. Download a release WAR:"
    warn "       curl -L -o $war_dest https://github.com/carlos-emr/carlos/releases/latest/download/carlos.war"
    warn "  3. Re-run setup.sh with --source to build from source in a container"

    if [ "$NON_INTERACTIVE" -eq 0 ]; then
        read -r -p "Continue anyway? (y/N): " reply
        case "$reply" in y|Y|yes|YES) ;; *) exit 1;; esac
    fi
}

# =============================================================================
# Phase 6: Deployment
# =============================================================================

deploy() {
    info "Deploying CARLOS EMR stack..."
    echo ""
    echo "About to run the following commands:"
    echo "  cd $COMPOSE_DIR"
    if [ "$WAR_SOURCE" = "source" ]; then
        echo "  $COMPOSE_CMD -f docker-compose.yml build --no-cache  # may take ~10 min"
    else
        echo "  $COMPOSE_CMD -f docker-compose.yml build"
    fi
    echo "  $COMPOSE_CMD -f docker-compose.yml up -d"
    echo ""

    if [ "$NON_INTERACTIVE" -eq 0 ]; then
        read -r -p "Proceed? (Y/n): " reply
        case "$reply" in n|N|no|NO) info "Aborted. Generated files remain in $COMPOSE_DIR."; exit 0;; esac
    fi

    # For source builds we need to point the compose build context at the repo root.
    # The compose file already handles this via context: ../..
    cd "$COMPOSE_DIR"

    # shellcheck disable=SC2086  # COMPOSE_CMD may contain a subcommand (e.g. 'docker compose')
    run $COMPOSE_CMD -f docker-compose.yml build
    # shellcheck disable=SC2086
    run $COMPOSE_CMD -f docker-compose.yml up -d

    if [ "$DRY_RUN" -eq 1 ]; then
        info "Dry-run complete. No containers were started."
        return 0
    fi

    # Wait for health
    info "Waiting for services to become healthy (up to 5 minutes)..."
    local waited=0 timeout=300
    while [ "$waited" -lt "$timeout" ]; do
        local healthy
        healthy=$($RUNTIME ps --filter "name=carlos-app" --format '{{.Status}}' 2>/dev/null || echo "")
        if echo "$healthy" | grep -q "healthy"; then
            success "CARLOS EMR is healthy!"
            break
        fi
        sleep 10
        waited=$((waited + 10))
        printf '.'
    done
    echo ""

    if [ "$waited" -ge "$timeout" ]; then
        warn "Timeout waiting for health. Check logs with:"
        warn "  $COMPOSE_CMD -f $COMPOSE_DIR/docker-compose.yml logs carlos"
    fi
}

# =============================================================================
# Phase 7: Post-install info
# =============================================================================

print_access_info() {
    cat <<EOF

${C_GREEN}${C_BOLD}============================================================${C_RESET}
${C_GREEN}${C_BOLD}  CARLOS EMR is ready!${C_RESET}
${C_GREEN}${C_BOLD}============================================================${C_RESET}

  Access URL:  ${C_BOLD}http://localhost:${CARLOS_PORT}/carlos/${C_RESET}

  Default login (change immediately):
    Username:  carlosdoc
    Password:  carlos2026
    PIN:       1117

  ${C_YELLOW}These credentials expire 1 month from first login.${C_RESET}

  Database password saved to: ${ENV_FILE}
  ${C_YELLOW}Back up this file - you need it to access the database!${C_RESET}

  Useful commands:
    Stop:      $COMPOSE_CMD -f $COMPOSE_DIR/docker-compose.yml stop
    Start:     $COMPOSE_CMD -f $COMPOSE_DIR/docker-compose.yml start
    Logs:      $COMPOSE_CMD -f $COMPOSE_DIR/docker-compose.yml logs -f carlos
    Teardown:  $COMPOSE_CMD -f $COMPOSE_DIR/docker-compose.yml down
               (add --volumes to also destroy the database)

  Documentation: docs/production-deployment.md

EOF
}

# =============================================================================
# Main
# =============================================================================

main() {
    # Parse flags
    while [ $# -gt 0 ]; do
        case "$1" in
            --dry-run)         DRY_RUN=1 ;;
            --non-interactive) NON_INTERACTIVE=1 ;;
            --demo)            LOAD_DEMO_DATA="true" ;;
            --province)        CARLOS_PROVINCE="$2"; shift ;;
            --port)            CARLOS_PORT="$2"; shift ;;
            --source)          WAR_SOURCE="source" ;;
            --prebuilt)        WAR_SOURCE="prebuilt" ;;
            --help|-h)         usage ;;
            *) fail "Unknown option: $1 (use --help)" ;;
        esac
        shift
    done

    banner
    detect_runtime
    check_prereqs
    configure
    generate_files
    acquire_war
    deploy
    print_access_info
}

main "$@"
