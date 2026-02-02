# UI Test Slash Commands

Custom Claude Code commands for automated execution of OpenO EMR UI tests.

## Overview

These slash commands automate UI test execution using Playwright MCP for browser automation. All commands reference the authoritative test documentation in `docs/ui-tests/` to avoid duplication.

**Documentation Structure**:
- `docs/ui-tests/COMPREHENSIVE-TEST-GUIDE.md` - Master guide for all tests
- `docs/ui-tests/UI-TEST-PROCESS.md` - Standard test process and directory structure
- `docs/ui-tests/test-N/test-N-EXECUTION.md` - Step-by-step execution guide for each test
- `docs/ui-tests/test-N/test-N-README.md` - Test overview and details
- `docs/ui-tests/test-N/screenshots/` - Gold standard screenshots for visual comparison

## Complete Test Suite

| Test | Name | Steps | Screenshots | Duration |
|------|------|-------|-------------|----------|
| 1 | Smoke Test | 7 | 7 | ~3 min |
| 2 | Demographics | 30 | 30 | ~25 min |
| 3 | Appointments | 15 | 15 | ~12 min |
| 4 | Prescriptions | 18 | 18 | ~15 min |
| 5 | Ticklers | 12 | 12 | ~8 min |
| 6 | Encounters | 22 | 22 | ~20 min |
| 7 | Billing (ON) | 15 | 15 | ~12 min |
| 8 | Lab Results | 12 | 12 | ~10 min |
| 9 | Prevention | 12 | 12 | ~10 min |

**Total**: 143 steps, 143 screenshots, ~115 minutes

## Available Commands

### `/test1` - Smoke Test
Validates basic login, search, and patient demographic viewing.
- **Documentation**: `docs/ui-tests/test-1/`
- **Steps**: 7 (login, dashboard, search, 2 patient records)
- **Test Data**: openodoc user, patients 1 and 182

### `/test2` - Comprehensive Demographics
Tests patient creation, editing, search methods, and filters.
- **Documentation**: `docs/ui-tests/test-2/`
- **Steps**: 30 (create, edit, search, filters, cleanup)
- **Creates**: TEST-UITEST2 patient (marked Inactive at end)

### `/test3` - Appointments & Scheduling
Tests schedule viewing, appointment creation, and management.
- **Documentation**: `docs/ui-tests/test-3/`
- **Steps**: 15 (views, create, edit, delete)
- **Creates**: Test appointment (deleted at end)

### `/test4` - Prescriptions (Rx)
Tests allergy management, drug search, and prescription creation.
- **Documentation**: `docs/ui-tests/test-4/`
- **Steps**: 18 (allergy, drug search, create Rx, history)
- **Creates**: Penicillin allergy, Acetaminophen prescription

### `/test5` - Ticklers & Messaging
Tests tickler creation, status management, and messaging module.
- **Documentation**: `docs/ui-tests/test-5/`
- **Steps**: 12 (create, status, inbox)
- **Creates**: Test tickler

### `/test6` - Encounter & E-Chart
Tests clinical documentation workflow in E-Chart.
- **Documentation**: `docs/ui-tests/test-6/`
- **Steps**: 22 (E-Chart, vitals, diagnosis, notes, panels)
- **Creates**: Clinical encounter with measurements

### `/test7` - Billing (Ontario MOH)
Tests Ontario MOH billing workflow.
- **Documentation**: `docs/ui-tests/test-7/`
- **Steps**: 15 (service codes, diagnostics, history)
- **Requires**: Ontario billing configuration
- **Creates**: Billing entry

### `/test8` - Lab Results
Tests lab inbox viewing and management.
- **Documentation**: `docs/ui-tests/test-8/`
- **Steps**: 12 (inbox, detail, history, review)
- **Note**: Lab data may be empty in test environment

### `/test9` - Prevention & Immunization
Tests preventive care and immunization tracking.
- **Documentation**: `docs/ui-tests/test-9/`
- **Steps**: 12 (prevention, add immunization, reports)
- **Creates**: Immunization record

### `/test-fullsuite` - Complete Test Suite
Runs all 9 tests sequentially for comprehensive validation.
- **Documentation**: `docs/ui-tests/COMPREHENSIVE-TEST-GUIDE.md`
- **Total Steps**: 143
- **Duration**: ~115 minutes

## Prerequisites

Before running any test command:

1. **Application Running**: `server start && server log`
2. **Database Ready**: Test users and patients exist
3. **Playwright MCP**: Configured in `.mcp.json`
4. **Permissions**: Already configured in `.claude/settings.json`

See `docs/ui-tests/UI-TEST-PROCESS.md` for complete setup requirements.

## Quick Pre-Flight Check

```bash
# Application responding
curl -sI http://localhost:8080/oscar/index.jsp | head -1

# Database accessible
mysql -h db -uroot -ppassword oscar -e "SELECT 1;"

# Test user exists
mysql -h db -uroot -ppassword oscar -e "SELECT user_name FROM security WHERE user_name='openodoc';"
```

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

## Test Output Structure

```
ui-test-runs/YYYYMMDD-HHMMSS-mmm/
├── test-1/
│   ├── screenshots/        # Captured screenshots
│   └── reports/           # Test results
├── test-2/
│   └── ...
├── ...
└── summary/               # Full suite summary (if running all)
    └── full-suite-results.md
```

## Gold Standards and Visual Comparison

Gold standard screenshots (the "correct" reference images) are stored in:
```
docs/ui-tests/test-N/screenshots/test-N-*.png
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
   - Copy structure from existing test command
   - Update frontmatter (description, allowed-tools)
   - Reference new test docs (avoid duplicating steps)
   - Include pre-flight checks and post-test verification

3. **Update this README** with new command details

4. **Update main README** at `docs/ui-tests/README.md`

5. **Test the command** before committing

## Permissions

All required permissions are pre-configured in `.claude/settings.json`:
- Playwright MCP browser automation tools
- Directory creation/file operations in `ui-test-runs/`
- Report generation
- Database queries for verification

No additional permissions needed.

---

*UI Test Commands for OpenO EMR*
*Last Updated: 2026-01-19*
