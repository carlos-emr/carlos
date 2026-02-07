# Contributing to CARLOS EMR

We heartily welcome any and all contributions that help make CARLOS a better EMR for the
healthcare community. Whether you're fixing a typo, reporting a bug, improving documentation,
or building a new feature, your effort matters and we appreciate you taking the time.

Remember: there are real people on the other side of the screen. Keep discussions positive,
productive, and respectful.

## Table of Contents

- [Ground Rules](#ground-rules)
- [Ways to Contribute](#ways-to-contribute)
- [Development Environment Setup](#development-environment-setup)
- [Contributing Code](#contributing-code)
- [Code Standards](#code-standards)
- [Project Heritage](#project-heritage)
- [Questions?](#questions)
- [License](#license)

## Ground Rules

### Community Guidelines

All participants in the CARLOS community are expected to follow our
[Code of Conduct](CODE_OF_CONDUCT.md). In short: be respectful, be constructive, and
remember that we're all working toward better healthcare software.

### Developer Certificate of Origin

By contributing to CARLOS, you agree to the
[Developer Certificate of Origin (DCO)](https://developercertificate.org/). This is a
lightweight agreement that certifies you have the right to submit your contribution under
the project's open source license.

You sign off by adding a `Signed-off-by` line to your commit messages. Git can do this
automatically with the `-s` flag:

```bash
git commit -s -m "fix: your commit message"
```

This adds a line like:

```
Signed-off-by: Your Name <your.email@example.com>
```

The DCO certifies that:

1. You wrote the contribution, or have the right to submit it
2. You understand it will be distributed under the project's open source license
3. Your contribution is provided under the project's license terms

If you are contributing on behalf of your employer, the sign-off certifies that you have
authorization to submit the work.

## Ways to Contribute

Not all contributions require writing code. Here are some ways to get involved at any
experience level:

**5-10 minutes:**
- Report a bug or request a feature via [GitHub Issues](https://github.com/carlos-emr/carlos/issues/new/choose)
- Star the [CARLOS repository](https://github.com/carlos-emr/carlos) to help others discover it
- Improve an error message or fix a typo

**A few hours:**
- Improve documentation or write a how-to guide
- Triage and reproduce reported bugs
- Review a pull request

**Ongoing:**
- Tackle a [good first issue](https://github.com/carlos-emr/carlos/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22)
- Implement a new feature or fix a complex bug
- Help with testing across different environments

### Reporting Bugs

Good bug reports help us fix issues faster. When filing an issue, please include:

- A clear, descriptive title
- Steps to reproduce the problem
- Expected vs. actual behavior
- Your environment (browser, OS, CARLOS version/branch)
- Screenshots or logs if applicable

**Never include real patient data in bug reports.** Use synthetic/test data only.

Use our [bug report template](https://github.com/carlos-emr/carlos/issues/new?template=bug_report.yml)
to get started.

### Improving Documentation

Our documentation lives in the `docs/` directory and is written in Markdown. Improvements
to documentation are always welcome, whether it's fixing a typo, clarifying an explanation,
or adding a new guide.

For small changes, you can edit files directly on GitHub without cloning the repository.

## Development Environment Setup

### Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop) installed and running
- [VS Code](https://code.visualstudio.com/) with the [Dev Containers](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers) extension
- [Git](https://git-scm.com/)
- **Ports 8080 and 3306 must be available.** The devcontainer uses these for the web
  application and database respectively. If another service (Tomcat, MySQL, etc.) is already
  using these ports, you'll need to stop it first or customize the port mappings in the
  Docker Compose configuration. If you run into conflicts, please let us know what you're
  seeing — PRs to improve our setup flexibility are welcome.

### Platform Notes

#### Windows: Use WSL (Strongly Recommended)

Docker accessing the Windows filesystem is significantly slow. We **strongly recommend**
cloning the repository inside
[WSL (Windows Subsystem for Linux)](https://learn.microsoft.com/en-us/windows/wsl/install)
and launching VS Code from within the WSL terminal. Both steps are important — the clone
must live on the WSL filesystem, and VS Code must be started from inside WSL so the
devcontainer runs against that filesystem.

It may work without WSL, but we do not test or QA that workflow on Windows and you should
expect poor build and runtime performance.

```bash
# From a Windows terminal (PowerShell or CMD), install WSL with Ubuntu if needed
wsl --install -d Ubuntu

# Enter your WSL environment
wsl

# Clone inside WSL (not on /mnt/c/)
cd $HOME
mkdir -p dev && cd dev

# External contributors: fork on GitHub first, then clone your fork
git clone https://github.com/YOUR-USERNAME/carlos.git

# Internal contributors: clone the repo directly
git clone https://github.com/carlos-emr/carlos.git

cd carlos

# Launch VS Code from inside WSL
code .
```

VS Code will detect the WSL environment automatically. When it prompts "Reopen in
Container", the devcontainer will run with native Linux filesystem performance.

#### Linux

Straightforward. Make sure Docker Desktop is running and your user has Docker permissions
(typically membership in the `docker` group). Clone, open in VS Code, and reopen in
container.

```bash
# External contributors: fork on GitHub first, then clone your fork
git clone https://github.com/YOUR-USERNAME/carlos.git

# Internal contributors: clone the repo directly
git clone https://github.com/carlos-emr/carlos.git

cd carlos
code .
```

#### macOS

Works similarly to Linux. There can be some performance impact due to Docker's filesystem
layer on macOS, though it is less severe than on Windows. We may explore mounting the
clone inside the container in the future to improve this — PRs welcome.

```bash
# External contributors: fork on GitHub first, then clone your fork
git clone https://github.com/YOUR-USERNAME/carlos.git

# Internal contributors: clone the repo directly
git clone https://github.com/carlos-emr/carlos.git

cd carlos
code .
```

### Devcontainer Setup

CARLOS uses a Docker-based devcontainer that provides a complete, isolated development
environment with all dependencies pre-configured.

When VS Code prompts "Reopen in Container", click it. If you don't see the prompt, click
the green remote connection icon in the bottom-left corner and select "Reopen in Container".

The first build takes several minutes as it initializes the database and downloads
dependencies. Subsequent builds are much faster.

For detailed setup instructions, see [.devcontainer/README.md](.devcontainer/README.md).

### Build and Run

Once inside the devcontainer:

```bash
make install        # Build and deploy (without tests)
```

On a fresh clone, `make install` is all you need. When rebuilding after making changes,
run `make clean` first to remove previous build artifacts:

```bash
make clean          # Clean previous build artifacts
make install        # Rebuild and deploy
```

Access the application at `http://localhost:8080`. See the devcontainer README for login
credentials.

**A note on `make`:** Yes, wrapping Maven in a shell script is a bit of an anti-pattern
for Java/Maven purists. CARLOS has some build peculiarities — dual dependency lock files
for legacy and modern builds, WAR deployment with symlinks, Tomcat lifecycle management —
that make the raw `mvn` invocation non-trivial. The `make` script handles all of this so
developers can focus on developing, and it significantly smooths onboarding for new
contributors. If you come from a Maven-native background and have ideas for improving the
build workflow, those discussions are very welcome.

### Run Tests

```bash
make install --run-tests              # All tests (modern + legacy)
make install --run-unit-tests         # Fast unit tests only (< 4 seconds)
make install --run-integration-tests  # Integration tests (requires database)
```

## Contributing Code

### Branch Strategy

CARLOS uses `develop` as the default branch and the focus for all active development.
Pull requests and merges target `develop`. Releases are promoted from staging branches
to `main`. **Do not work directly on `develop`** — always create a feature branch for
your changes.

### Internal vs. External Contributors

**Internal contributors** have push access to the CARLOS repository and can create
`feature/`, `fix/`, and other branches directly on the repo. If you've been granted
repository access, you can clone the CARLOS repo directly and push branches to it.

**External contributors** — and many valued contributors work this way — need to first
**fork** the CARLOS repository to your own GitHub account (consider giving us a star while
you're there!), then clone your fork to your local machine. You'll make changes and push
branches to *your* fork, then open a pull request back to the CARLOS repository when your
work is ready for review.

Both workflows end the same way: **all changes go through a pull request** targeting the
`develop` branch, reviewed and approved before merging. No one pushes directly to
protected branches.

### External Contributor Workflow (Fork-Based)

1. **Fork** the CARLOS repository on GitHub and **clone your fork** locally
2. **Add the upstream remote** so you can keep your fork up to date:
   ```bash
   git remote add upstream https://github.com/carlos-emr/carlos.git
   ```
3. **Sync your fork** before starting new work:
   ```bash
   git checkout develop
   git pull upstream develop
   git push origin develop
   ```
4. **Create a feature branch** from `develop` (never work directly on `develop`):
   ```bash
   git checkout -b your-feature-name
   ```
5. **Make your changes** following the [code standards](#code-standards) below
6. **Test your changes** - include tests for new functionality
7. **Commit with DCO sign-off** (required — PRs without signed commits will not be accepted):
   ```bash
   git commit -s -m "fix: description of your change"
   ```
8. **Push to your fork**:
   ```bash
   git push origin your-feature-name
   ```
9. **Open a pull request** on GitHub from your fork's branch to `carlos-emr/carlos:develop`

### Internal Contributor Workflow

1. **Clone** the CARLOS repository directly
2. **Create a feature branch** from `develop` (never work directly on `develop`):
   ```bash
   git checkout develop
   git pull origin develop
   git checkout -b feature/your-feature-name
   ```
3. **Make and test your changes** following the [code standards](#code-standards) below
4. **Commit with DCO sign-off** (required — PRs without signed commits will not be accepted):
   ```bash
   git commit -s -m "fix: description of your change"
   ```
5. **Push your branch** and **open a pull request** targeting `develop`

### Pull Request Guidelines

All changes — from internal and external contributors alike — must go through a pull
request. Direct pushes to `develop`, `main`, and other protected branches are not allowed.

- **Target `develop`**, never `main`
- **Reference related issues** (e.g., `fixes #123`)
- **Include a clear description** of what changed and why
- **Add tests** for new functionality
- **Keep PRs focused** - one logical change per PR

Your PR will be reviewed by a maintainer. We may ask for changes or suggest improvements.
This is a normal part of the process and not a reflection on the quality of your work —
we're all learning and improving together.

For bug fixes, feel free to open a PR directly. For new features, please
[open an issue](https://github.com/carlos-emr/carlos/issues/new/choose) first so we can
discuss the approach and make sure it fits the project roadmap.

### Commit Message Format

We use [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add patient allergy search filter
fix: correct date formatting in lab results
refactor: simplify caseload infinite scroll detection
chore: update Spring dependency to 5.3.39
docs: add deployment guide for BC clinics
test: add TicklerDao integration tests
```

## Code Standards

CARLOS is a healthcare EMR built with **Java 21**, Spring 5.3, Struts 6.8, Hibernate 5.x,
and MariaDB/MySQL. It handles sensitive patient data — security and code quality are not
optional.

We **strongly recommend** developing inside the devcontainer, which provides the correct
Java version, all dependencies, and a pre-configured database. Building outside the
devcontainer is possible but unsupported — PRs to improve that experience are welcome.

**Note for AI-assisted development:** The `.claude/` directory contains Claude Code
configuration with pre-approved permissions. These defaults assume an isolated devcontainer
environment with no real patient data. If you are using Claude Code outside a devcontainer,
review `.claude/settings.json` and restrict permissions as appropriate.

### Security Requirements (Mandatory)

Every code change must follow these security practices:

- **Output encoding**: Use `Encode.forHtml()`, `Encode.forJavaScript()`, and other
  context-appropriate [OWASP Encoder](https://owasp.org/www-project-java-encoder/) methods
  for all user-provided data
- **Parameterized queries only**: Never use string concatenation for SQL. Always use
  parameterized queries or Hibernate criteria
- **Authorization checks**: All actions must include `SecurityInfoManager.hasPrivilege()`
  checks
- **File path validation**: Use `PathValidationUtils` for all file operations involving
  user input
- **No PHI in logs**: Patient Health Information must never appear in log output or error
  messages

### Code Patterns

- **Struts2 Actions**: New actions follow the `*2Action.java` naming convention
  (e.g., `AddTickler2Action.java`). See existing actions for examples.
- **Spring Integration**: Use `SpringUtils.getBean()` for dependency injection
- **Package namespace**: All new code uses `io.github.carlos_emr.carlos.*`
- **Copyright headers**: New files use the CARLOS project header
  (see `docs/copyright-header-carlos.md`). Never remove existing copyright notices from
  modified files - this is required by the GPL.

### Testing

- Modern tests use **JUnit 5** in `src/test-modern/`
- Follow **BDD naming**: `shouldReturnTickler_whenValidIdProvided()`
- Extend `OpenOTestBase` for integration tests, `OpenOUnitTestBase` for unit tests
- See `docs/test/test-writing-guide.md` for detailed patterns

## Project Heritage

CARLOS has evolved through multiple open-source projects over 20+ years. You may encounter
references to "OSCAR", "OpenO", or "OpenOSP" in code comments, git history, and legacy
documentation. These reflect the project's heritage, not current affiliations. See
[NOTICE.md](NOTICE.md) for full attribution details.

## Questions?

If you're stuck, confused, or just want to say hello, open a
[GitHub Issue](https://github.com/carlos-emr/carlos/issues). We'd rather you ask than
struggle in silence. We plan to enable GitHub Discussions in the future for broader
conversation.

## License

CARLOS as a project is licensed under GPL-2.0. Refer to individual file headers for
further license and copyright information. See [COPYING.md](COPYING.md) for the full
license text and [NOTICE.md](NOTICE.md) for project attribution.
