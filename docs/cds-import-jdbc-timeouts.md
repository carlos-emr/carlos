# CDS JDBC connection deadlines

Controlled CDS connections use the shipped Connector/J transport settings before opening a socket.
Missing `connectTimeout` receives **5000 ms** and missing `socketTimeout` receives **60000 ms**.
Explicit positive operator values are retained up to **30000 ms** for connect and **300000 ms**
for socket reads. An explicit zero, negative, nonnumeric or over-ceiling value is refused with
`CDS_JDBC_TIMEOUT_CONFIGURATION_INVALID` / SQLState `08001`; it is never silently accepted as
an unlimited operation. The policy uses the driver's actual query/property/per-host precedence: explicit connection
properties override URL query options, while single-host descriptors retain the driver's more
specific host settings. Driver, URL, credentials, SSL, charset and other properties
are copied without modifying the original factory or shared configuration.

The scope is deliberate:

- Ordinary WAR **pooled** connections retain their previous settings when
  `cds.import.maintenanceGuardEnabled=false` (the existing default).
- Opting into `cds.import.maintenanceGuardEnabled=true` also opts the **whole primary WAR pool**
  into finite network settings before its first Flyway/JPA connection. This includes ordinary
  requests using that pool; operators with long database queries must choose suitable finite
  values within the limits. This is not a per-request timeout or a late pool reconfiguration.
- The dedicated one-shot CLI datasource always uses the finite policy, including startup,
  schema validation and authorization before run execution.
- Dedicated nonpooled coordinator, maintenance and ordinary filesystem-admission connections
  always use a separately copied bounded factory. Ordinary pool flags are not changed by these calls.

Controlled connections require one primary-writer endpoint: Connector/J `SINGLE_CONNECTION`.
Multi-host failover, load-balance, replication and DNS-SRV URLs are refused before connecting or
resolving SRV targets with `CDS_JDBC_SINGLE_HOST_REQUIRED`. A secondary host cannot retain an
unbounded per-host override. The protocol does not claim cross-server advisory-lock or HA failover
support. Default-off ordinary pools are not subjected to this controlled-connection topology rule.

These are driver TCP-connect and individual socket-read limits, including the initial server
handshake. They complement the existing 3-second admission checks, bounded drain/query timers,
owned transaction limits and cooperative source/report deadlines; they do not replace them or
claim a single wall-clock deadline for the entire import. Endpoint name resolution remains a
responsibility of the deployment's resolver. No global `DriverManager` login timeout, generic
future executor, automatic retry or pool abandonment setting is introduced. Transport failure
must retain the existing unknown-commit/hold/reconciliation rules.

`CdsJdbcConnectionTimeoutsUnitTest` checks the actual Connector/J property merge, opt-out and
opt-in factories, explicit invalid values, both orders of the former secondary-host bypass, and
four real loopback silent-handshake paths (maintenance, ordinary lease, opted-in web pool and
focused CLI). Each test peer retires within three seconds even if a timeout regresses. The HRM
MariaDB tier additionally reads back the actual 60000 ms network timeout on pooled/nonpooled
connections while proving abandoned-pool reclamation cannot release a filesystem admission lease.
