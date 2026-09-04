#!/usr/bin/env bash
# Try to pull the pre-built CARLOS dev image and classify the result so the
# calling workflow can distinguish "not in the registry, build it locally" from
# "the registry is unreachable / access denied, fail the job".
#
# Inputs:  IMAGE (env)  -- fully qualified image reference to pull
# Outputs: pulled=true|false and reason=success|not-found|error, written to
#          $GITHUB_OUTPUT. Never exits non-zero: every outcome is reported
#          through the outputs and acted on by the next step.
#
# Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
#
# This software is published under the GPL GNU General Public License.
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
#
# CARLOS EMR Project
# https://github.com/carlos-emr/carlos
set -uo pipefail

: "${IMAGE:?IMAGE must be set to the image reference to pull}"
: "${GITHUB_OUTPUT:?GITHUB_OUTPUT must point at the step output file}"

error_log=$(mktemp)
trap 'rm -f "$error_log"' EXIT

echo "Attempting to pull pre-built image from $IMAGE..."
# Capture stderr so we can distinguish "not found" from other errors
if ! docker pull "$IMAGE" 2>"$error_log"; then
  echo "pulled=false" >> "$GITHUB_OUTPUT"
  if grep -qiE 'manifest unknown|not found' "$error_log"; then
    echo "reason=not-found" >> "$GITHUB_OUTPUT"
    echo "Pre-built image not available (not found in registry), will build locally"
  else
    echo "reason=error" >> "$GITHUB_OUTPUT"
    echo "Image pull failed due to an unexpected error:"
    cat "$error_log"
  fi
elif ! docker tag "$IMAGE" carlos-tomcat-dev 2>"$error_log"; then
  # The pull succeeded but the local alias the callers run could not be
  # created: report an error rather than pulled=true, otherwise the action
  # would skip the fallback build and start a container from a missing image.
  echo "pulled=false" >> "$GITHUB_OUTPUT"
  echo "reason=error" >> "$GITHUB_OUTPUT"
  echo "Image was pulled but could not be tagged as carlos-tomcat-dev:"
  cat "$error_log"
else
  echo "pulled=true" >> "$GITHUB_OUTPUT"
  echo "reason=success" >> "$GITHUB_OUTPUT"
  echo "Successfully pulled pre-built image!"
fi
