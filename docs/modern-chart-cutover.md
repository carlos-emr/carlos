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
