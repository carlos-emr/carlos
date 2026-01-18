# UI Test Slash Commands

Custom Claude Code commands for automated execution of OpenO EMR UI tests.

## Overview

These slash commands automate UI test execution using Playwright MCP for browser automation. All commands reference the authoritative test documentation in `docs/ui-tests/` to avoid duplication.

**Documentation Structure**:
- `docs/ui-tests/UI-TEST-PROCESS.md` - Standard test process and directory structure
- `docs/ui-tests/test-N/test-N-EXECUTION.md` - Step-by-step execution guide for each test
- `docs/ui-tests/test-N/test-N-README.md` - Test overview and details
- `docs/ui-tests/test-N/screenshots/` - Gold standard screenshots for visual comparison

## Available Commands

### `/test1` - UI Test 1: Smoke Test

Automated execution of Test 1 (login + patient demographics).

**Quick Reference**:
- **Documentation**: `docs/ui-tests/test-1/`
- **Duration**: ~2 minutes
- **Steps**: 7 (login, dashboard, search, 2 patient records)
- **Screenshots**: 7 captured automatically
- **Test Data**: openodoc user, patients 1 and 182
- **Note**: Search requires pressing Enter after typing - the form does not auto-submit

**Usage**:
```
/test1
```

**Output Location**:
```
ui-test-runs/YYYYMMDD-HHMMSS-mmm/test-1/
├── screenshots/        # 7 PNG files
├── reports/           # test-1-results.md
└── comparison/        # (reserved for visual diffs)
```

**Post-Test**:
1. Review report at `ui-test-runs/TIMESTAMP/test-1/reports/test-1-results.md`
2. Compare screenshots with gold standards in `docs/ui-tests/test-1/screenshots/`
3. See full verification steps in `docs/ui-tests/test-1/test-1-EXECUTION.md`

### Future Commands

- `/test2` - (To be defined)
- `/test3` - (To be defined)
- `/test-all` - Run all tests sequentially

## Prerequisites

Before running any test command:

1. **Application Running**: `server start && server log`
2. **Database Ready**: Test users and patients exist
3. **Playwright MCP**: Configured in `.mcp.json`
4. **Permissions**: Already configured in `.claude/settings.json`

See `docs/ui-tests/UI-TEST-PROCESS.md` for complete setup requirements.

## Quick Troubleshooting

**Application not responding**:
```bash
server restart && server log
curl -I http://localhost:8080/oscar/index.jsp  # Should return 200
```

**Test user missing**:
```bash
db-connect
SELECT user_name, pin FROM security WHERE user_name='openodoc';
# Reset password if needed (see docs/ui-tests/test-1/test-1-EXECUTION.md)
```

**Playwright MCP issues**:
- Check `.mcp.json` exists
- Verify playwright enabled in `.claude/settings.json`
- Restart Claude Code

**Complete troubleshooting**: See `docs/ui-tests/UI-TEST-PROCESS.md`

## Gold Standards and Visual Comparison

Gold standard screenshots (the "correct" reference images) are stored in:
```
docs/ui-tests/test-1/screenshots/test-1-*.png
```

After each test run, compare new screenshots against gold standards to detect:
- Layout regressions
- Missing elements
- Styling changes
- Functional breakage

**IMPORTANT**: Never update gold standards as part of normal testing. See `docs/ui-tests/UI-TEST-PROCESS.md` for when and how to update gold standards (separate task requiring approval).

## Adding New Test Commands

To add a new UI test command:

1. **Create test documentation** in `docs/ui-tests/test-N/`:
   - `test-N-README.md` - Test overview
   - `test-N-EXECUTION.md` - Execution steps
   - `screenshots/` - Gold standards

2. **Create slash command** at `.claude/commands/ui-tests/testN.md`:
   - Copy structure from `test1.md`
   - Update frontmatter (description, allowed-tools)
   - Reference new test docs (avoid duplicating steps)
   - Include pre-flight checks and post-test verification

3. **Update this README** with new command details

4. **Test the command** before committing

## Permissions

All required permissions are pre-configured in `.claude/settings.json`:
- Playwright MCP browser automation tools
- Directory creation/file operations in `ui-test-runs/`
- Report generation

No additional permissions needed.
