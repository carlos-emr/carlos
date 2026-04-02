# =============================================================================
# CARLOS EMR — Custom AppArmor Profile for the AI Devcontainer
# =============================================================================
#
# Profile name : carlos-dev-container
# Applies to   : The Tomcat/Maven/Claude Code devcontainer (carlos service).
#
# How to use
# ----------
# 1. Load the profile on the HOST machine that runs Docker:
#
#       sudo apparmor_parser -r \
#           .devcontainer/security/apparmor-carlos-dev.profile
#
#    Or run the helper script:
#       sudo bash .devcontainer/security/load-apparmor-host.sh
#
# 2. Enable in docker-compose.yml by uncommenting the security_opt block:
#
#       security_opt:
#         - apparmor:carlos-dev-container
#
# 3. Restart the devcontainer.
#
# Design principles
# -----------------
# • Allow everything needed for Java/Tomcat/Maven development.
# • Deny writes to version-control metadata and CI/CD definitions (defence-in-
#   depth; .claude/settings.json also denies these at the command level).
# • Deny writes to seed/init SQL files to protect schema integrity.
# • Deny dangerous kernel capabilities (sys_admin, sys_module, sys_rawio).
# • Allow NET_ADMIN so the setup-network-policy.sh iptables script can run.
# • Network destination filtering is handled by iptables, not AppArmor; this
#   profile therefore permits all socket operations.
#
# References
# ----------
# • Docker AppArmor documentation:
#     https://docs.docker.com/engine/security/apparmor/
# • AppArmor profile language:
#     https://gitlab.com/apparmor/apparmor/-/wikis/QuickProfileLanguage
# • docker-default AppArmor profile (base reference):
#     https://github.com/moby/moby/blob/master/profiles/apparmor/template.go
# =============================================================================

#include <tunables/global>

profile carlos-dev-container flags=(attach_disconnected,mediate_deleted) {

  #include <abstractions/base>
  #include <abstractions/nameservice>

  # ==========================================================================
  # CAPABILITIES
  # ==========================================================================

  # Standard container capabilities
  capability chown,
  capability dac_override,
  # dac_read_search — allows the JVM and Maven to read files owned by other UIDs
  # (e.g., reading /proc/<pid>/maps for arbitrary processes during profiling,
  # or traversing directories not owned by the container user).
  capability dac_read_search,
  capability fowner,
  capability fsetid,
  capability kill,
  capability setgid,
  capability setuid,
  # setpcap — allows dropping capabilities from the bounding set.  Needed by
  # the container runtime itself during process setup (e.g., when Tomcat drops
  # capabilities from child processes).  This does not grant the ability to
  # acquire new capabilities beyond those already in the permitted set.
  capability setpcap,
  capability net_bind_service,
  capability net_raw,
  capability sys_chroot,
  capability audit_write,
  capability mknod,

  # NET_ADMIN — required by setup-network-policy.sh to manipulate iptables.
  # This capability is scoped to the container's own network namespace and
  # cannot affect the host's routing or firewall tables.
  capability net_admin,

  # Explicitly deny dangerous capabilities.
  deny capability sys_ptrace,
  deny capability sys_admin,
  deny capability sys_module,
  deny capability sys_rawio,
  deny capability sys_boot,
  deny capability sys_time,
  deny capability wake_alarm,
  deny capability mac_admin,
  deny capability mac_override,

  # ==========================================================================
  # NETWORK ACCESS
  # ==========================================================================
  # Network destination filtering is enforced by iptables (setup-network-policy.sh).
  # AppArmor permits socket creation; iptables enforces which hosts are reachable.
  network inet  tcp,
  network inet  udp,
  network inet6 tcp,
  network inet6 udp,
  network unix  stream,
  network unix  dgram,
  network netlink raw,

  # ==========================================================================
  # FILESYSTEM
  # ==========================================================================

  # --- Read everywhere (needed by JVM, Maven, shell tools) ------------------
  /** r,

  # --- Full read/write/lock access to the project workspace -----------------
  /workspace/** rwlk,

  # --- Defence-in-depth: deny writes to git metadata and CI/CD definitions --
  # .claude/settings.json enforces the same rules at the command level;
  # these AppArmor rules add a kernel-level backstop.
  deny /workspace/.git/**               wl,
  deny /workspace/.github/workflows/**  wl,

  # --- Deny writes to seed/init SQL files (protect schema integrity) ---------
  deny /workspace/database/mysql/oscarinit*.sql  wl,
  deny /workspace/database/mysql/oscardata*.sql  wl,
  deny /workspace/database/mysql/icd*.sql        wl,
  deny /workspace/database/mysql/SnomedCore/**   wl,
  deny /workspace/database/mysql/olis/**         wl,
  deny /workspace/database/mysql/caisi/**        wl,

  # --- Temporary / runtime directories --------------------------------------
  /tmp/**         rwlk,
  /run/**         rwlk,
  /var/run/**     rwlk,
  /var/tmp/**     rwlk,

  # --- Home directory (Maven ~/.m2 cache, Node ~/.npm, etc.) ----------------
  /root/**  rwlk,

  # --- Tomcat installation --------------------------------------------------
  /usr/local/tomcat/**  rwlk,

  # --- /proc and /sys reads needed by the JVM and container runtime ---------
  /proc/              r,
  /proc/**            r,
  /proc/*/fd/         r,
  /proc/*/fd/**       r,
  /sys/fs/cgroup/**   r,
  /sys/kernel/mm/transparent_hugepage/enabled  r,

  # --- Devices --------------------------------------------------------------
  /dev/null     rw,
  /dev/zero     rw,
  /dev/random   r,
  /dev/urandom  r,
  /dev/pts/**   rw,
  /dev/tty      rw,

  # xtables lock needed by iptables
  /run/xtables.lock  rwl,

  # ==========================================================================
  # PROCESS EXECUTION
  # ==========================================================================
  # Allow executing any binary in standard paths.
  # Specific command restrictions are enforced at the Claude Code level via
  # .claude/settings.json allow/deny/ask rules.
  /usr/bin/**        Pixr,
  /usr/sbin/**       Pixr,
  /bin/**            Pixr,
  /sbin/**           Pixr,
  /usr/local/bin/**  Pixr,
  /usr/local/sbin/** Pixr,
  /usr/lib/**        Pixr,

  # ==========================================================================
  # DENIED OPERATIONS
  # ==========================================================================

  # Deny mount/umount to prevent namespace escapes.
  deny mount,
  deny umount,

  # Deny ptrace to prevent process injection attacks.
  deny ptrace,
}
