#!/bin/bash
# Script to verify Playwright Chromium path configuration
# This helps ensure .mcp.json has the correct executable path for the installed Playwright version

set -e

echo "🔍 Playwright Chromium Path Verification"
echo "=========================================="
echo

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if we're in the devcontainer
if [ ! -d "/root/.cache/ms-playwright" ]; then
    echo -e "${YELLOW}⚠️  Warning: Playwright cache directory not found.${NC}"
    echo "This script should be run inside the devcontainer after Playwright is installed."
    exit 1
fi

# Get Playwright version from Dockerfile
DOCKERFILE="/workspace/.devcontainer/development/Dockerfile"
PLAYWRIGHT_VERSION="unknown"
if [ -f "$DOCKERFILE" ]; then
    PLAYWRIGHT_VERSION=$(awk -F= '/^ARG[[:space:]]+PLAYWRIGHT_VERSION=/ {print $2; exit}' "$DOCKERFILE")
    if [ -z "$PLAYWRIGHT_VERSION" ]; then
        PLAYWRIGHT_VERSION=$(grep -oP 'playwright@\K[0-9.]+' "$DOCKERFILE" | head -1 || true)
    fi
    if [ -z "$PLAYWRIGHT_VERSION" ]; then
        PLAYWRIGHT_VERSION="unknown"
    fi
fi
echo "📦 Playwright version in Dockerfile: ${PLAYWRIGHT_VERSION}"

# Check installed Chromium directories
echo
echo "📁 Installed Chromium directories:"
ls -d /root/.cache/ms-playwright/chromium-* 2>/dev/null || echo "  None found"

# Get the latest installed Chromium revision
LATEST_CHROMIUM=$(ls -d /root/.cache/ms-playwright/chromium-* 2>/dev/null | sort -t- -k2 -n | tail -1 | grep -oP 'chromium-\K[0-9]+' || echo "")

if [ -z "$LATEST_CHROMIUM" ]; then
    echo -e "${RED}❌ No Chromium installation found${NC}"
    exit 1
fi

echo
echo "🌐 Latest installed Chromium revision: ${LATEST_CHROMIUM}"

# Check if the executable exists. Playwright browser layouts can change
# between releases, so discover the path instead of assuming chrome-linux.
EXPECTED_PATH=$(find "/root/.cache/ms-playwright/chromium-${LATEST_CHROMIUM}" -path "*/chrome" -type f 2>/dev/null | sort | head -1 || true)
if [ -n "$EXPECTED_PATH" ] && [ -f "$EXPECTED_PATH" ]; then
    echo -e "${GREEN}✅ Chromium executable found at: ${EXPECTED_PATH}${NC}"
else
    echo -e "${RED}❌ Chromium executable not found${NC}"
    echo
    echo "🔎 Searching for chrome executable..."
    find "/root/.cache/ms-playwright/chromium-${LATEST_CHROMIUM}" -name "chrome" -type f 2>/dev/null || echo "  Not found"
    exit 1
fi

# Check .mcp.json configuration
echo
echo "⚙️  Checking .mcp.json configuration..."
MCP_JSON="/workspace/.mcp.json"

if [ ! -f "$MCP_JSON" ]; then
    echo -e "${YELLOW}⚠️  .mcp.json not found${NC}"
    exit 1
fi

# Extract the current executable path from .mcp.json
CURRENT_PATH=$(grep -oP '"--executable-path",\s*"\K[^"]+' "$MCP_JSON" || echo "")

if [ -z "$CURRENT_PATH" ]; then
    echo -e "${YELLOW}⚠️  No executable-path found in .mcp.json${NC}"
    exit 1
fi

echo "   Current path in .mcp.json: ${CURRENT_PATH}"
echo "   Expected path: ${EXPECTED_PATH}"

if [ "$CURRENT_PATH" = "$EXPECTED_PATH" ]; then
    echo -e "${GREEN}✅ Configuration is correct!${NC}"
else
    echo -e "${RED}❌ Configuration mismatch!${NC}"
    echo
    echo "To fix this, update the executable-path in .mcp.json to:"
    echo "   ${EXPECTED_PATH}"
    echo
    echo "Or use jq to update it programmatically:"
    echo "   jq '.mcpServers.playwright.args |= map(if . == \"--executable-path\" then . else if . == \"${CURRENT_PATH}\" then \"${EXPECTED_PATH}\" else . end end)' ${MCP_JSON} > ${MCP_JSON}.tmp && mv ${MCP_JSON}.tmp ${MCP_JSON}"
    exit 1
fi

echo
echo -e "${GREEN}🎉 All checks passed!${NC}"
