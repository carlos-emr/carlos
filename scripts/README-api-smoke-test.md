# API Smoke Test Script

## Purpose

Simple smoke test script to catch systemic problems with CARLOS EMR's REST and SOAP endpoints. Tests the most critical paths to verify the application is functioning correctly before and after changes.

## What It Tests

The script verifies:

1. **Web Application Health**
   - Login page accessible (server responding)
   - Logout page accessible

2. **SOAP Web Services**
   - CXF servlet configured correctly
   - WSDL generation working for DemographicWs and ProviderWs

3. **REST API**
   - OAuth interceptor working (properly rejecting unauthenticated requests)
   - Core services accessible (Provider, Schedule)

## Installation

```bash
# Install required Python package
pip3 install requests
```

## Usage

```bash
# Basic usage (tests localhost:8080)
cd scripts
./api-smoke-test.py

# Or with python3
python3 api-smoke-test.py

# Test a different server
BASE_URL=http://localhost:9090/oscar ./api-smoke-test.py

# Adjust timeout for slow servers
TIMEOUT=10 ./api-smoke-test.py
```

## Typical Workflow

```bash
# Before making changes
./api-smoke-test.py

# Make your code changes
# ...

# Build and deploy
make install
server restart

# After changes - verify nothing broke
./api-smoke-test.py
```

## Expected Output

When the server is running correctly:

```
CARLOS EMR API Smoke Tests
Base URL: http://localhost:8080/oscar
============================================================

Web App
  ✓ Login Page: HTTP 200 (0.05s)
  ✓ Logout Page: HTTP 200 (0.03s)

SOAP
  ✓ DemographicWs WSDL: HTTP 200 (0.12s)
  ✓ ProviderWs WSDL: HTTP 200 (0.08s)

REST
  ✓ Provider Service: HTTP 401 (0.04s)
  ✓ Schedule Service: HTTP 401 (0.03s)

============================================================
Results: 6/6 passed (all passed)
============================================================
```

## Exit Codes

- `0` - All tests passed
- `1` - One or more tests failed
- `2` - Configuration error (e.g., missing dependencies)

## What Each Test Detects

| Test | Detects These Problems |
|------|------------------------|
| Login Page | Server not running, webapp not deployed |
| Logout Page | Basic routing issues |
| SOAP WSDL | CXF servlet misconfigured, Spring context errors |
| REST endpoints returning 401 | OAuth interceptor working correctly |

## Troubleshooting

**Connection refused errors:**
- Server not running → Start with `server start`
- Wrong port → Check BASE_URL matches your server

**Unexpected HTTP status codes:**
- 404 errors → Webapp not deployed correctly
- 500 errors → Application errors (check logs with `server log`)
- 200 instead of 401 on REST → OAuth interceptor misconfigured

**Timeouts:**
- Server slow to start → Increase TIMEOUT
- Application hung → Check server logs

## CI/CD Integration

The script is designed for CI/CD pipelines:

```bash
#!/bin/bash
# Build and test
make install
server restart
python3 scripts/api-smoke-test.py || exit 1
```

Exit code 0 indicates all tests passed, non-zero indicates failures.
