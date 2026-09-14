# Security Policy

CARLOS is healthcare software trusted with sensitive patient data. We take security
seriously and appreciate the community's help in keeping the project safe for the
clinics and patients who depend on it.

## Reporting a Vulnerability

**Please do not open a public issue for security vulnerabilities.**

Report vulnerabilities privately through
[GitHub Security Advisories](https://github.com/carlos-emr/carlos/security/advisories/new).
This ensures the issue is handled confidentially before any public disclosure.

### What to Include

To help us understand and address the issue quickly, please include:

- A description of the vulnerability and its potential impact
- Steps to reproduce the issue, including any proof-of-concept code
- The component or file affected, if known
- Your assessment of severity (critical, high, medium, low)

### What to Expect

- **Acknowledgment** within 5 business days of your report
- **Initial assessment** within 10 business days
- We will keep you informed of progress toward a fix
- We will coordinate public disclosure timing with you

CARLOS is maintained by volunteers. We may not always meet these targets, but we
will always prioritize security issues and communicate openly about timelines.

## Scope

This policy covers the CARLOS EMR codebase hosted at
[github.com/carlos-emr/carlos](https://github.com/carlos-emr/carlos).

We are especially interested in vulnerabilities that could:

- Expose patient health information (PHI)
- Bypass authentication or authorization controls
- Allow SQL injection, XSS, or other OWASP Top 10 attacks
- Enable path traversal or unauthorized file access
- Compromise the integrity of medical records

### Out of Scope

- Vulnerabilities in dependencies that are already publicly disclosed (please open
  a [dependency update issue](https://github.com/carlos-emr/carlos/issues/new?template=dependency_update.yml) instead)
- Issues in third-party software, hosting environments, or individual deployments
- Findings from automated scanners without a demonstrated exploit
- Denial of service attacks
- Social engineering or phishing

## Supported Versions

Security fixes for unreleased code are applied to `develop`. High-priority fixes for a
supported release are applied to its `release/YYYY.MM` maintenance branch, published as
a prerelease or patch as appropriate, and forward-merged into newer supported lines and
`develop`. The active 2026.08 line is maintained on `release/2026.08` while development
of 2026.09 continues on `develop`.

Prepare embargoed remediation through the private security-advisory workflow. At the
coordinated disclosure point, land it on the oldest affected supported maintenance line,
then forward-merge it into newer supported lines and `develop` while preserving the
version and SCM metadata on each target. Existing release tags are never moved or reused.

See the [release process](docs/release-process.md) for the maintenance, tagging, and
forward-merge policy. Supported maintenance lines may change as releases reach end of
support; published tags and immutable releases remain available.

## Safe Harbor

We consider security research conducted under this policy to be authorized. We will
not pursue legal action against researchers who report vulnerabilities in good faith,
follow this disclosure process, and do not compromise the privacy or safety of
patients or the availability of clinical systems.

## Security Practices

CARLOS enforces several security controls across the codebase:

- **Output encoding**: OWASP Encoder for all user-provided data — `encoder-jsp` EL functions (`${e:forHtml()}`) in JSPs, `Encode.*` static methods in Java
- **Parameterized queries**: No SQL string concatenation
- **Authorization checks**: `SecurityInfoManager.hasPrivilege()` on all actions
- **Path validation**: `PathValidationUtils` for all file operations
- **PHI protection**: Patient data must never appear in logs or error messages
- **CSRF protection**: OWASP CSRFGuard 4.5 with auto-injected tokens (see [`docs/csrf-protection-architecture.md`](docs/csrf-protection-architecture.md))
- **Dependency scanning**: GitHub Dependabot and secret scanning enabled

Contributors are expected to follow these practices. See
[CONTRIBUTING.md](CONTRIBUTING.md) for details.
