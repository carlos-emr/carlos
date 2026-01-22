# Playwright MCP Server Configuration

## Chromium Executable Path Verification

The `.mcp.json` file in the repository root configures the Playwright MCP server with a specific Chromium executable path. This path must match the Chromium version installed by Playwright.

### Current Configuration

- **Playwright Version**: 1.56.0 (specified in `.devcontainer/development/Dockerfile`)
- **Chromium Revision**: 1194
- **Executable Path**: `/root/.cache/ms-playwright/chromium-1194/chrome-linux/chrome`

### How to Verify the Path

If you need to verify or update the Chromium executable path, follow these steps:

1. **Check Playwright version** in the Dockerfile:
   ```bash
   grep "playwright@" .devcontainer/development/Dockerfile
   ```

2. **Find the Chromium revision** for that Playwright version:
   ```bash
   # For Playwright 1.56.0
   curl -s https://raw.githubusercontent.com/microsoft/playwright/v1.56.0/packages/playwright-core/browsers.json | grep -A 3 '"name": "chromium"'
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
    └── chrome-linux/
        └── chrome          # The executable
```

**Note**: The directory is `chrome-linux` (not `chrome-linux64`), regardless of whether the system is 32-bit or 64-bit.

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
           "/root/.cache/ms-playwright/chromium-{NEW_REVISION}/chrome-linux/chrome"
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

To find the mapping for other versions, check the official browsers.json file:
```bash
curl -s https://raw.githubusercontent.com/microsoft/playwright/v{VERSION}/packages/playwright-core/browsers.json
```
