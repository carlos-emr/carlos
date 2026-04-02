# CARLOS EMR — Devcontainer Security Constraints
# ==============================================

This directory contains OS-level security configurations that constrain the
AI coding assistant (Claude Code) inside the CARLOS EMR devcontainer.

## Quick Start

### Layer 1 — iptables network policy (enabled by default)

No action required.  When the devcontainer starts, `setup-network-policy.sh`
runs automatically and blocks outbound MySQL/MariaDB connections to any host
other than the devcontainer's own `db` service.

Prerequisite: the `carlos` service in `docker-compose.yml` must have
`cap_add: [NET_ADMIN]` (already set).

### Layer 2 — AppArmor kernel profile (opt-in)

Requires AppArmor on the host machine (Linux; not available on macOS/Windows).

```bash
# 1. Load the profile on the host (one-time setup)
sudo bash .devcontainer/security/load-apparmor-host.sh

# 2. Enable in docker-compose.yml (uncomment the security_opt block)
# 3. Rebuild the container: Dev Containers: Rebuild Container
```

---

## Files

| File | Purpose |
|------|---------|
| `setup-network-policy.sh` | Runs inside container; creates iptables rules |
| `apparmor-carlos-dev.profile` | AppArmor MAC profile (opt-in, host-loaded) |
| `load-apparmor-host.sh` | Helper to load AppArmor profile on host |

---

## See Also

`docs/devcontainer-ai-security-constraints.md` — full security model
documentation including threat analysis, design decisions, and Dan Walsh
references.
