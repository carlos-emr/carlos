#!/usr/bin/env bash
# Validate every action in the modular Struts configuration without fetching DTDs.
set -euo pipefail
exec python3 "$(dirname "$0")/validate_struts_actions.py" "$@"
