#!/bin/bash
# Setup GitHub CLI for Claude Code web sessions
# This hook installs gh CLI if not present and configures authentication
# via the GH_TOKEN environment variable.

set -e

# Skip if not a remote/web environment
if [ "$CLAUDE_CODE_REMOTE" != "true" ]; then
  exit 0
fi

# Install gh CLI if not already available
if ! command -v gh &>/dev/null; then
  (
    curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg \
      | dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg 2>/dev/null
    chmod go+r /usr/share/keyrings/githubcli-archive-keyring.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" \
      | tee /etc/apt/sources.list.d/github-cli.list >/dev/null
    apt-get update -qq
    apt-get install -y -qq gh >/dev/null 2>&1
  ) >&2

  if command -v gh &>/dev/null; then
    echo "gh CLI installed successfully" >&2
  else
    echo "WARNING: Failed to install gh CLI" >&2
    exit 0
  fi
fi

# Verify GH_TOKEN is available for authentication
if [ -z "$GH_TOKEN" ]; then
  echo "WARNING: GH_TOKEN not set. Add GH_TOKEN to your Claude Code web environment variables for GitHub CLI access." >&2
  exit 0
fi

exit 0
