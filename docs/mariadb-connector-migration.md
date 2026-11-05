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

CARLOS and packaged DrugRef use MariaDB Connector/J 3.5.11. New configuration
uses `org.mariadb.jdbc.Driver` and `jdbc:mariadb://host:port/database`. Existing
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

Connection settings are decoded once. Explicit connection properties retain
precedence over URL query options; supported single-host address options retain
their host-specific precedence. Legacy multi-host failover URLs use MariaDB's
sequential host selection. CDS maintenance connections continue to require one
host and finite, validated network timeouts.

`serverTimezone` becomes `connectionTimeZone`. Both the modern `sslMode` values
and the legacy `useSSL`, `requireSSL` and `verifyServerCertificate` flags retain
their verification requirements. LOCAL INFILE remains disabled unless explicitly
enabled. MariaDB always uses Unicode and INFORMATION_SCHEMA metadata; the former
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
