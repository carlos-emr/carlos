#!/usr/bin/env bash
#
# Thin wrapper for the billing BDD test-name lint so local and CI invocations
# use the same implementation.

set -euo pipefail
exec python3 "$(dirname "$0")/check-bdd-test-naming.py" "$@"
