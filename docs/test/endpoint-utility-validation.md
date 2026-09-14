# Endpoint, manager and utility test validation

This work supersedes PRs #765 and #767 on `release/2026.08`. It combines their
executable test coverage with the release branch's existing PDF permission,
XML external-entity and other security regressions. It does not restore removed
SHA-1 APIs or claim coverage from deleted/disabled source examples.

## Running the tests

Use Java 21 and the repository Maven configuration. Run the complete suite with:

```bash
mvn -B -ntp test -Dtest.forkCount=1
```

A single fork limits memory use; the repository default remains four forks.
Do not compile while running a validation VM on a memory-constrained host.

For endpoint-only work and harness self-tests:

```bash
mvn -B -ntp test -Dtest.forkCount=1 -Dgroups=endpoint
mvn -B -ntp test -Dtest.forkCount=1 \
  -Dtest=CarlosRestTestBaseTest,CarlosSoapTestBaseTest
```

See [the endpoint guide](endpoint-testing-guide.md) for fixture setup, tags,
JSON/XML requests and the boundaries this local transport does not validate.

## Checking that the tests detect defects

The corrected age and appointment tests first produced ten failures against the
old implementation. The program-list and denied-prescription-menu assertions
also failed against the old application code. The fixes must satisfy the
payload and persistence assertions, not just return a successful status.

The harness self-tests were also checked with four temporary mutations. Each
mutation was applied independently, compiled successfully, and caused a test
failure or error. The original files were restored and all ten self-tests passed.

| Temporary mutation | Expected detector |
|---|---|
| Set `LocalConduit.DIRECT_DISPATCH` to false in both bases | Static dependency call must run on the test's caller thread |
| Remove `SmartDateModule` registration | A `java.util.Date` at local midnight must serialize as a calendar date |
| Replace UUID addresses with a fixed address | Two instances of the same test class must have distinct addresses |
| Remove request/session identity injection | REST and SOAP authenticated-request calls must return the supplied provider identity |

These are deliberate failing runs, not skipped tests or accepted flakes. Do not
commit the mutations. Tests that change legacy static state restore it and run
in isolation. Manager tests use strict Mockito checks, with narrowly scoped
lenient shared permission defaults where a test does not use that permission.

The restored harness also passed all ten self-tests with two concurrent JUnit
workers. The Surefire discovery guard includes the new `EndpointTest` suffix,
matching the POM; the guard and four seed-security checks pass together.

Four compatibility tests also reproduced HTTP 406 responses for XML clients of
pharmacy lookup, demographic listing, demographic merges and message counts.
The affected classes must advertise both JSON and XML; methods with their own
explicit media types retain those contracts. The message-count XML representation wraps the number in a `count` element.
The generic list response declares its concrete JAXB item types so nonempty
XML lists can be serialized.

## Application findings

The replacement PR description lists the fixed defects and remaining findings.
The latter include inconsistent plain-text document errors under a JSON media
type, empty successful note responses for access denial, and the legacy empty
SQL IN-clause builder. Local endpoint tests verify the observed contracts and
protected dependency calls; they do not establish deployed authentication,
servlet-filter, transaction, browser or database correctness.
