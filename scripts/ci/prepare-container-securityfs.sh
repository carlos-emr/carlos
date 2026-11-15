#!/usr/bin/env bash
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
# Run inside the privileged disposable validation container before package installation.
set -euo pipefail
if [ "${CARLOS_DISPOSABLE_HOST:-}" != true ]; then
  echo 'Securityfs preparation requires CARLOS_DISPOSABLE_HOST=true' >&2
  exit 1
fi
enabled_path=/sys/module/apparmor/parameters/enabled
restriction_path=/proc/sys/kernel/apparmor_restrict_unprivileged_userns
security_root=/sys/kernel/security
if [ ! -r "$enabled_path" ] || [ "$(cat "$enabled_path")" != Y ]; then exit 0; fi
if [ ! -r "$restriction_path" ] || [ "$(cat "$restriction_path")" != 1 ]; then exit 0; fi
# A privileged container may inherit the restriction while securityfs is not mounted.
# Expose the policy interface so the package can load its own dedicated userns grant.
# The kernel restriction and the renderer's sandbox configuration stay enabled.
if ! mountpoint -q "$security_root"; then
  if ! mount -t securityfs securityfs "$security_root"; then
    echo 'Cannot mount securityfs; sandboxed renderer qualification cannot proceed' >&2
    exit 1
  fi
fi
if [ ! -r "$security_root/apparmor/profiles" ]; then
  echo 'AppArmor policy interface is unavailable; sandboxed renderer qualification cannot proceed' >&2
  exit 1
fi
