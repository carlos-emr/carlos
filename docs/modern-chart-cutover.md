# Modern chart cutover and rollback

CARLOS now requires the modern chart. Modern notes and CPP changes are no longer
mirrored into historical `eChart` rows. Historical records remain read-only; they
must not be treated as a current chart after this cutover.

Before startup completes, CARLOS verifies that the **loaded external properties
file** contains an effective `AbandonOldChart=true`. If the setting is absent or
false, startup appends a final true entry under the configuration file lock and
flushes it to disk. Existing keys, comments and historical values are preserved;
Java properties readers use the final value for duplicate keys. An already true
setting requires read permission only. The encryption key uses the same locking
protocol and is neither replaced nor rewritten by the cutover.
The guard examines the final loaded external configuration, including any
Tomcat-user home override loaded after singleton construction. If that loaded
value disables the modern chart, it logs one sanitized warning before persisting
the true setting. The warning instructs operators to retain that external true
setting on rollback; already true configuration produces no cutover warning.

If the external file cannot be updated, startup fails with the source location
and remediation. An administrator can set `AbandonOldChart=true` in that file
before restarting; the application account need not receive general configuration
write access. A bundled WAR resource or an in-memory property is insufficient,
because replacing the WAR during rollback would discard the protection. Manual
WAR deployments must supply an external file, for example with
`-Dcarlos_override_properties=/path/to/carlos.properties`, or the existing
Tomcat-user home configuration lookup. The Debian installation's external file
is normally `/etc/carlos-emr/carlos.properties`.
Package configure persists the flag as root after `init-config` and before
starting the application, preserving the normal `root:carlos` ownership and
`0640` mode. A persistence failure fails configure; it does not proceed to the
application start. The startup guard independently verifies the result.

Packaged starts also run a JDK-only preflight before Tomcat: a scoped root step
reconciles the managed `/etc` file, then a step running as `carlos` checks the
effective managed/home configuration and existing encryption key. This covers
deferred configure and restored files on the next start, using the currently
pinned carlos-ctl without changing its commands or provisioning lock. The
preflight uses the same POSIX record locks as Java writers; it never waits on
the CLI's process-lifetime provisioning `flock`. Root touches only a root-owned
managed file under root-controlled directories. It never writes a legacy home
file, creates configuration, or replaces a key. A legacy home file that cannot
retain the flag is refused before Tomcat starts, with the source and recovery
instructions. Move its settings and original key into the managed file before
removing it from the home-directory search path.

A rollback must load **the same persisted external configuration**, with
`AbandonOldChart=true` still effective. Do not restore an older false/absent
configuration snapshot while keeping the database's later modern-only writes,
and do not point an older binary at another configuration. Such a rollback is
unsupported: historical rows omit changes saved after cutover. Configuration
restoration and database restoration must be planned together, preserving the
original encryption key and accounting for all clinical writes. The cutover
flag must also remain true on every node sharing that database. Stop or restart
older nodes with that persisted setting before allowing modern-only writes;
an already-running old process may still hold a false in-memory snapshot.
