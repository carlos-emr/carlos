#!/usr/bin/env python3
"""
CARLOS EMR API Smoke Test Script

Simple smoke tests to catch systemic problems with REST/SOAP endpoints.
Tests the most critical paths to ensure the application is functioning.

Usage:
    # Basic smoke test
    ./api-smoke-test.py

    # Custom base URL
    BASE_URL=http://localhost:9090/oscar ./api-smoke-test.py

Environment Variables:
    BASE_URL  - Base URL for CARLOS EMR (default: http://localhost:8080/oscar)
    TIMEOUT   - Request timeout in seconds (default: 5)

Exit Codes:
    0 - All tests passed
    1 - One or more tests failed
    2 - Configuration error
"""

import os
import sys
import time

try:
    import requests
except ImportError:
    print("ERROR: 'requests' library not found.")
    print("Install with: pip3 install requests")
    sys.exit(2)


# Simple color codes
GREEN = '\033[92m'
RED = '\033[91m'
YELLOW = '\033[93m'
RESET = '\033[0m'
BOLD = '\033[1m'


def test_endpoint(base_url, name, path, expected_status=200, timeout=5):
    """Test a single endpoint and return (passed, message)"""
    url = f"{base_url}{path}"
    try:
        start = time.time()
        resp = requests.get(url, timeout=timeout, verify=False, allow_redirects=True)
        elapsed = time.time() - start

        if resp.status_code == expected_status:
            return True, f"HTTP {resp.status_code} ({elapsed:.2f}s)"
        else:
            return False, f"Expected HTTP {expected_status}, got {resp.status_code}"
    except requests.Timeout:
        return False, f"Timeout after {timeout}s"
    except requests.ConnectionError:
        return False, "Connection refused - server not running?"
    except Exception as e:
        return False, f"Error: {str(e)}"


def run_smoke_tests():
    """Run essential smoke tests to catch systemic problems"""
    base_url = os.getenv('BASE_URL', 'http://localhost:8080/oscar')
    timeout = int(os.getenv('TIMEOUT', '5'))

    # Disable SSL warnings for dev environments
    requests.packages.urllib3.disable_warnings()

    print(f"\n{BOLD}CARLOS EMR API Smoke Tests{RESET}")
    print(f"Base URL: {base_url}")
    print(f"{'='*60}\n")

    passed = 0
    failed = 0

    # Define tests: (category, name, path, expected_status)
    tests = [
        # Basic health - is the server responding?
        ("Web App", "Login Page", "/index.jsp", 200),
        ("Web App", "Logout Page", "/logout.jsp", 200),

        # SOAP services - is CXF servlet configured?
        ("SOAP", "DemographicWs WSDL", "/ws/DemographicWs?wsdl", 200),
        ("SOAP", "ProviderWs WSDL", "/ws/ProviderWs?wsdl", 200),

        # REST API - is OAuth interceptor working?
        # (401 = properly rejecting unauthenticated requests)
        ("REST", "Provider Service", "/ws/services/providerService/providers_json", 401),
        ("REST", "Schedule Service", "/ws/services/schedule/statuses", 401),
    ]

    current_category = None
    for category, name, path, expected in tests:
        # Print category header
        if category != current_category:
            print(f"\n{BOLD}{category}{RESET}")
            current_category = category

        # Run test
        success, message = test_endpoint(base_url, name, path, expected, timeout)

        # Print result
        if success:
            print(f"  {GREEN}✓{RESET} {name}: {message}")
            passed += 1
        else:
            print(f"  {RED}✗{RESET} {name}: {message}")
            failed += 1

    # Summary
    total = passed + failed
    print(f"\n{'='*60}")
    print(f"Results: {passed}/{total} passed", end="")
    if failed > 0:
        print(f" ({RED}{failed} failed{RESET})")
    else:
        print(f" ({GREEN}all passed{RESET})")
    print(f"{'='*60}\n")

    return failed == 0


def main():
    """Main entry point"""
    if '--help' in sys.argv or '-h' in sys.argv:
        print(__doc__)
        sys.exit(0)

    success = run_smoke_tests()
    sys.exit(0 if success else 1)


if __name__ == '__main__':
    main()
