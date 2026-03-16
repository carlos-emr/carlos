# Archived Legacy Tests

These tests were removed from `src/test/` during the JUnit 5 migration because they
cannot run in a standard CI/CD pipeline. They are preserved here for reference.

## Categories

### `mcedt/` — Ontario MCEDT Conformance Tests (JUnit 4)
Ontario Ministry of Health Electronic Data Transfer conformance tests. Require live
MCEDT service connectivity (https://204.41.14.200:1443/EDTService). All tests were
`@Ignore`d and annotated "Not intended to be run as build tests."

- 10 test classes, ~100 test methods
- Test data files in `mcedt/resources/`

### `webserv/` — REST Web Service Tests (JUnit 4)
Functional REST API tests requiring a running CARLOS application instance.
Disabled by default (`test.webserv.rest.enabled=true` required in properties).

- 3 test classes + 1 base class
- `DemographicConverterTest` could potentially be rewritten as a modern unit test

### `selenium-ui/` — Browser Automation Tests (TestNG)
End-to-end UI tests using Selenium WebDriver with headless Chrome.
Require a running CARLOS instance and browser environment.

- 10 test classes, ~51 test methods
- Uses TestNG framework (not JUnit)

### `selenium-ide/` — Selenium IDE Test Suites
Legacy Selenium IDE recorded test suites for MyOSCAR integration.
These are HTML-based Selenium IDE scripts, not Java tests.

## Restoration

If these tests need to be restored for integration testing environments,
move the Java files back to `src/test/java/` under their original package paths
and the resources to `src/test/resources/`.
