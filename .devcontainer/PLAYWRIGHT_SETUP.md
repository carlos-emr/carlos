# Playwright MCP Server Configuration

## Chromium Executable Path Verification

The `.mcp.json` file in the repository root configures the Playwright MCP server with a specific Chromium executable path. This path must match the Chromium version installed by Playwright.

### Current Configuration

- **Playwright Version**: 1.60.0 (specified in `.devcontainer/development/Dockerfile`)
- **Chromium Revision**: 1223
- **Executable Path**: `/root/.cache/ms-playwright/chromium-1223/chrome-linux64/chrome`

### How to Verify the Path

If you need to verify or update the Chromium executable path, follow these steps:

1. **Check Playwright version** in the Dockerfile:
   ```bash
   grep "PLAYWRIGHT_VERSION" .devcontainer/development/Dockerfile
   ```

2. **Find the Chromium revision** for that Playwright version:
   ```bash
   # For Playwright 1.60.0
   curl -s https://raw.githubusercontent.com/microsoft/playwright/v1.60.0/packages/playwright-core/browsers.json | grep -A 3 '"name": "chromium"'
   ```

3. **Verify the installed path** inside the devcontainer:
   ```bash
   ls -la /root/.cache/ms-playwright/
   ```

4. **Dry-run installation** to see what would be installed:
   ```bash
   npx playwright install chromium --dry-run
   ```

### Directory Structure

Playwright's Chromium installation follows this structure on Linux:
```
/root/.cache/ms-playwright/
└── chromium-{revision}/
    └── chrome-linux64/
        └── chrome          # The executable
```

**Note**: Playwright 1.60.0 installs Chromium from Chrome for Testing, which uses the `chrome-linux64` directory on Linux. If the Playwright version changes, verify the directory with `npx playwright install chromium --dry-run` or by inspecting `/root/.cache/ms-playwright/`.

### Updating the Configuration

If you update the Playwright version in the Dockerfile, you must also update the Chromium path in `.mcp.json`:

1. Check the new Chromium revision from browsers.json (see step 2 above)
2. Update the `--executable-path` argument in `.mcp.json`:
   ```json
   {
     "mcpServers": {
       "playwright": {
         "args": [
           ...
           "--executable-path",
           "/root/.cache/ms-playwright/chromium-{NEW_REVISION}/chrome-linux64/chrome"
         ]
       }
     }
   }
   ```

### Playwright Version to Chromium Revision Mapping

| Playwright Version | Chromium Revision | Chromium Browser Version |
|-------------------|-------------------|--------------------------|
| 1.56.0            | 1194              | 141.0.7390.37            |
| 1.57.0            | 1200              | 143.0.7499.4             |
| 1.60.0            | 1223              | 148.0.7778.96            |

To find the mapping for other versions, check the official browsers.json file:
```bash
curl -s https://raw.githubusercontent.com/microsoft/playwright/v{VERSION}/packages/playwright-core/browsers.json
```
