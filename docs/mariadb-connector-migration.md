<!-- SPDX-License-Identifier: GPL-2.0-or-later -->
<!--
Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.

This software is published under the GPL GNU General Public License.
This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

CARLOS EMR Project
https://github.com/carlos-emr/carlos
-->

# MariaDB JDBC connector

CARLOS and packaged DrugRef use MariaDB Connector/J 3.5.11. The WAR and
devcontainer defaults use `org.mariadb.jdbc.Driver` and
`jdbc:mariadb://host:port/database`. Packaged CLI installs differ: the pinned
`carlos-ctl` 1.3.1 `init-config` still writes `db_driver=com.mysql.cj.jdbc.Driver`
and a `jdbc:mysql:` `db_uri`, and its schema launcher supplies a MySQL-style
`FLYWAY_URL`. This applies to newly provisioned packaged installs as well as
upgrades; their legacy-looking configuration does not mean MySQL Connector/J
is bundled. They require the compatibility adapter shipped in the WAR and
extracted by the Debian Flyway launcher.

`com.mysql.cj.jdbc.Driver`, `com.mysql.jdbc.Driver`, and `jdbc:mysql:` configuration
is adapted when the applications open their pools or dedicated connections.
An empty legacy hostname continues to mean `localhost`.

The compatibility driver delegates transport, authentication, prepared statements,
transactions, and connection management to MariaDB Connector/J. It preserves the
application's existing JDBC conversion contract, including `zeroDateTimeBehavior`,
DATETIME object types, timezone handling, checkbox metadata and
`useOldAliasMetadataBehavior`. Invalid partial-zero dates remain errors, and
`ROUND` applies only to genuinely zero values. Report streaming requests are
translated to MariaDB's positive fetch-size convention.

FLOAT, REAL and DOUBLE columns read as text keep Connector/J's form, the
printed Java double: `100.0`, `1.0E7`, and through server-prepared statements
`1.100000023841858` for a FLOAT holding 1.1. MariaDB would return the server's
text (`100`, `10000000`, `1.1`). Their `getBigDecimal` keeps the same scale
(`100.0`). BIT columns read as text keep their signed number (`1`, `5`) rather
than MariaDB's `b'101'` and `b''`; BIT(1) reads as a `Boolean` object, BIT
metadata names `java.lang.Boolean`, and BIT values convert to `double`, `float`
and `BigDecimal` as unsigned numbers, as Connector/J did (#5968). Report by
Template, Query by Example, fixed and clinical reports and the legacy form
loaders therefore print these columns as before. TINYINT(1), which both drivers
report as BIT(1), keeps its sign. Its untyped `getObject` reads use the same
numeric truth rule as `getBoolean`: positive values and `-1` are true, other
non-positive values are false, and SQL NULL remains null.
Typed `getObject` reads also keep SQL NULL as null when the requested numeric
or Boolean class is primitive (for example `double.class`). Primitive getters
such as `getDouble` still return zero with `wasNull()` set.

These Connector/J behaviours are not reproduced, and nothing in CARLOS depends
on them: `getBytes` on a numeric column (MariaDB refuses it), the deprecated
`getBigDecimal(column, scale)` rounding, `getNString` on a non-national column
(MariaDB returns the text), out-of-range `getLong` and `getFloat` reads (MariaDB
returns the type's limit, or infinity for `getFloat`, where Connector/J refused
them), `getByte` on BIT columns wider than 8 bits, and some metadata details:
FLOAT and DOUBLE scale, the display size of DECIMAL and INT columns, and the
`INT` type name, which MariaDB reports as `INTEGER`.

URL query settings are decoded once. MySQL connection properties retain
precedence over URL query options, including after URL normalization. Native
MariaDB URLs retain their opposite precedence: URL options override connection
properties. Supported single-host address options retain their host-specific
precedence. Legacy multi-host failover URLs use MariaDB's
sequential host selection. CDS maintenance connections continue to require one
host and finite, validated network timeouts.
For native URLs, conflicting case variants or aliases canonicalized by the adapter
fail with a configuration error: MariaDB otherwise chooses between them by property-map
iteration order. Equivalent normalized values remain accepted. Native URL
normalization preserves option spelling so this check still applies when
connection properties are supplied later.

`serverTimezone` becomes `connectionTimeZone`. Both the modern `sslMode` values
and the legacy `useSSL`, `requireSSL` and `verifyServerCertificate` flags retain
their verification requirements. Legacy `tlsVersions`/`enabledTLSProtocols` and
`tlsCiphersuites`/`enabledSSLCipherSuites` restrictions map to MariaDB's protocol
and cipher settings. Certificate-store URL/type/password aliases and system-store
fallback flags retain their precedence; local `file:` values support encoded or
unescaped spaces without changing literal plus signs. Explicit FIPS mode or JSSE
provider selection cannot be represented and fails with a configuration error.
A selected certificate store or private key that cannot be loaded causes a
connection failure. The connection does not silently omit the requested client
identity or substitute public trust roots for a configured trust store.
Normalized MySQL URLs retain their original TLS flag semantics. For native
`jdbc:mariadb:` URLs, explicit `useSsl`/`useSSL` flags retain MariaDB's exact
case-sensitive names and values: `true`, `1`, or an empty value enable verified
TLS, with `trustServerCertificate` or `disableSslHostnameVerification` selecting
the corresponding native policy. These two modifiers do not enable TLS without
an enabled `useSsl`/`useSSL` flag. An enabled native flag overrides the global
`sslMode`; an explicit host `sslMode` overrides the global policy. Native address
descriptors retain MariaDB's supported host, port, type, SSL mode, pipe and local
socket fields, including their literal path spelling. Other native descriptor
fields remain ignored, as in MariaDB itself; they do not change global TLS,
certificate-store or LOCAL INFILE policy.
Connections without TLS options retain the application's existing PREFERRED
default, which negotiates TLS when available without certificate verification.
Native system-store fallback and LOCAL INFILE flags retain MariaDB's Boolean
tokens, including an empty value for true; unsupported `yes`/`no` values fail.
MySQL-style configuration (`jdbc:mysql:` URLs, with their connection properties
and address descriptors) keeps MySQL Connector/J's `yes` and `no` spellings, in
any letter case, for every MariaDB connection option backed by a Boolean
setting: they become `true` and `false` (#5969). This includes options the
adapter does not otherwise translate, such as `allowPublicKeyRetrieval`,
`useServerPrepStmts`, `cachePrepStmts`, `useCompression`, `allowMultiQueries`,
`useAffectedRows`, `tcpKeepAlive`, `useLocalSessionState`, `yearIsDateType`,
`createDatabaseIfNotExist` and `nullCatalogMeansCurrent`. The option list is
read from the shipped MariaDB driver, so a connector upgrade cannot leave a new
Boolean option out. As in Connector/J, these option names match without regard
to case and the later source wins: the adapter keeps one spelling per option,
so a connection property overrides a differently spelled URL option rather than
leaving MariaDB to choose between them. For options the adapter does not check
itself, other values reach MariaDB unchanged: it accepts `true`, `false`, `1`,
`0` or an empty value and refuses anything else with an error naming the
option. Native `jdbc:mariadb:` URLs do not get this translation.
LOCAL INFILE remains disabled unless explicitly enabled. MariaDB always uses
Unicode and INFORMATION_SCHEMA metadata; the former
`useInformationSchema` setting is accepted but no longer selects an implementation.
The obsolete `useUnicode` flag remains accepted, as in MySQL Connector/J 9.6,
where it no longer changes the encoding. Non-UTF-8 `characterEncoding` values,
non-utf8mb4 `connectionCollation` values, DNS SRV discovery, a path-restricted LOCAL INFILE policy, and differing
per-host credentials in a multi-host URL cannot be represented by this adapter
and fail with a configuration error rather than being silently ignored.

The Debian Flyway launcher extracts the MariaDB jar and these same compatibility
classes from the CARLOS WAR. Existing `carlos-ctl` versions may continue to pass
MySQL-style URLs. DrugRef is built from the immutable revision in
`debian/drugref.pin`, with its separate dependency lock. Package staging rejects
a DrugRef WAR containing MySQL Connector/J, a MariaDB connector other than
3.5.11, or lacking the compatibility classes and property-based datasource
credentials; supplying a prebuilt WAR does not bypass these checks.

The compatibility layer reads two pinned MariaDB result metadata structures to
preserve table aliases and binary partial-zero DATE strings. These reads never
modify the driver state and fail explicitly if a future driver changes the
layout. Connector upgrades must run `JdbcConnectorMariaDbIntegrationTest` against
a physical MariaDB server, including buffered, streaming, and server-prepared
results. The generated-schema CI lane requires those results; H2 alone cannot
establish driver compatibility. Shutdown closes each webapp's own MariaDB timeout
scheduler after its connections close, preserving a container-shared driver.
