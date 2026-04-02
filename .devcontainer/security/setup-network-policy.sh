#!/usr/bin/env bash
# =============================================================================
# CARLOS EMR - AI Devcontainer Network Security Policy
# =============================================================================
#
# Purpose:
#   Applies iptables rules inside the devcontainer to restrict outbound database
#   connections.  The biggest risk from an unrestricted AI coding assistant
#   (Claude Code) is accidentally connecting to a production or external
#   database server on port 3306/3307.  This script prevents that by:
#
#     1. Allowing MySQL/MariaDB connections ONLY to the devcontainer's own
#        `db` service (carlos-mariadb-dev).
#     2. Blocking all other outbound connections to MySQL/MariaDB ports.
#     3. Blocking outbound connections to PostgreSQL (5432) as extra defence.
#
# Requirements:
#   - The `carlos` service in docker-compose.yml must have:
#       cap_add: [NET_ADMIN]
#
# Usage:
#   This script is called automatically via the devcontainer postStartCommand.
#   It can also be run manually inside the container:
#       bash /workspace/.devcontainer/security/setup-network-policy.sh
#
# Safety:
#   - All failures are non-fatal; the script exits 0 with a warning so the
#     devcontainer continues to start normally even if iptables is unavailable.
#   - Rules are idempotent: re-running flushes and recreates the CARLOS_AI chain.
#
# =============================================================================

set -uo pipefail

CHAIN="CARLOS_AI_POLICY"
DB_HOST="${CARLOS_DB_HOST:-db}"

log()  { echo "[carlos-security] $*" >&2; }
warn() { echo "[carlos-security] WARNING: $*" >&2; }

# ─── Pre-flight checks ────────────────────────────────────────────────────────

if ! command -v iptables &>/dev/null; then
    warn "iptables not found – network policy NOT applied."
    exit 0
fi

# Test whether we actually have permission to list rules (requires NET_ADMIN).
if ! iptables -L OUTPUT -n &>/dev/null; then
    warn "Cannot access iptables (NET_ADMIN capability missing?)."
    warn "Add 'cap_add: [NET_ADMIN]' to the carlos service in docker-compose.yml."
    warn "Network policy NOT applied."
    exit 0
fi

# ─── Resolve the db container's IP ───────────────────────────────────────────

resolve_db_ip() {
    getent hosts "$DB_HOST" 2>/dev/null | awk '{ print $1; exit }'
}

DB_IP=$(resolve_db_ip)

if [ -z "$DB_IP" ]; then
    log "Could not resolve '$DB_HOST' on first try – waiting 5 s and retrying…"
    sleep 5
    DB_IP=$(resolve_db_ip)
fi

if [ -z "$DB_IP" ]; then
    warn "Could not resolve '$DB_HOST' hostname."
    warn "Network policy NOT applied – the db container may not be running."
    exit 0
fi

log "Resolved '$DB_HOST' → $DB_IP"

# ─── Build iptables rules ─────────────────────────────────────────────────────

# Create (or flush) the custom chain.
if iptables -L "$CHAIN" -n &>/dev/null; then
    iptables -F "$CHAIN"
    log "Flushed existing $CHAIN chain."
else
    iptables -N "$CHAIN"
    log "Created $CHAIN chain."
fi

# Allow connections to the devcontainer's own db service.
iptables -A "$CHAIN" \
    -d "$DB_IP" -p tcp --dport 3306 \
    -j ACCEPT \
    -m comment --comment "carlos-ai: allow devcontainer MariaDB"

iptables -A "$CHAIN" \
    -d "$DB_IP" -p tcp --dport 3307 \
    -j ACCEPT \
    -m comment --comment "carlos-ai: allow devcontainer MariaDB proxy"

# Block MySQL/MariaDB to everything else.
iptables -A "$CHAIN" \
    -p tcp --dport 3306 \
    -j REJECT --reject-with tcp-reset \
    -m comment --comment "carlos-ai: block external MySQL"

iptables -A "$CHAIN" \
    -p tcp --dport 3307 \
    -j REJECT --reject-with tcp-reset \
    -m comment --comment "carlos-ai: block external MySQL proxy"

# Block PostgreSQL as additional defence.
iptables -A "$CHAIN" \
    -p tcp --dport 5432 \
    -j REJECT --reject-with tcp-reset \
    -m comment --comment "carlos-ai: block all PostgreSQL"

# ─── Jump into the chain from OUTPUT ──────────────────────────────────────────

# Use -C to check before inserting to stay idempotent.
if ! iptables -C OUTPUT -j "$CHAIN" &>/dev/null; then
    iptables -I OUTPUT 1 -j "$CHAIN"
    log "Inserted $CHAIN jump at top of OUTPUT chain."
else
    log "$CHAIN jump already present in OUTPUT chain."
fi

# ─── Summary ──────────────────────────────────────────────────────────────────

log "Network security policy applied successfully:"
log "  MySQL/MariaDB (3306/3307) → ALLOWED only to $DB_IP ($DB_HOST)"
log "  MySQL/MariaDB (3306/3307) → REJECTED to all other hosts"
log "  PostgreSQL    (5432)      → REJECTED to all hosts (no PostgreSQL in devcontainer)"
log ""
log "Active $CHAIN rules:"
iptables -L "$CHAIN" -n -v --line-numbers 2>/dev/null || true
