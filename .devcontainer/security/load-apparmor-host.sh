#!/usr/bin/env bash
# =============================================================================
# CARLOS EMR — Load AppArmor Profile on Host Machine
# =============================================================================
#
# Run this script on the HOST machine (not inside the container) to load the
# custom AppArmor profile before starting the devcontainer.
#
# Usage:
#   sudo bash .devcontainer/security/load-apparmor-host.sh
#
# After loading, enable in docker-compose.yml:
#   security_opt:
#     - apparmor:carlos-dev-container
#
# =============================================================================

set -euo pipefail

PROFILE_FILE="$(dirname "$0")/apparmor-carlos-dev.profile"
PROFILE_NAME="carlos-dev-container"

log()  { echo "[carlos-apparmor] $*"; }
error(){ echo "[carlos-apparmor] ERROR: $*" >&2; exit 1; }

# ─── Checks ───────────────────────────────────────────────────────────────────

if [ "$(id -u)" -ne 0 ]; then
    error "This script must be run as root (use sudo)."
fi

if ! command -v apparmor_parser &>/dev/null; then
    error "apparmor_parser not found. Install AppArmor: sudo apt-get install apparmor apparmor-utils"
fi

if [ ! -f "$PROFILE_FILE" ]; then
    error "Profile file not found: $PROFILE_FILE"
fi

# ─── Check AppArmor is enabled ────────────────────────────────────────────────

if ! aa-status &>/dev/null; then
    error "AppArmor does not appear to be enabled on this system. Check: sudo aa-status"
fi

# ─── Load the profile ─────────────────────────────────────────────────────────

log "Loading AppArmor profile '$PROFILE_NAME' from $PROFILE_FILE …"
apparmor_parser -r "$PROFILE_FILE"

log "Profile loaded successfully."
log ""
log "Verify with:  sudo aa-status | grep $PROFILE_NAME"
log ""
log "Next steps:"
log "  1. Uncomment the security_opt block in .devcontainer/docker-compose.yml"
log "  2. Restart the devcontainer: Dev Containers: Rebuild Container"
log ""
log "To remove the profile later:"
log "  sudo apparmor_parser -R $PROFILE_FILE"
