# DrugRef devcontainer driver configuration (#5862)

The devcontainer selects DrugRef `master`, currently
`57c8642821fbf38653913280db278a137f1fe261`. It ships HikariCP 6.2.1 and MySQL
Connector/J 9.1.0. The release's mounted properties requested the absent MariaDB
driver, so datasource creation failed before connecting to the database.

Restore the MySQL driver and URL in the devcontainer properties. The packaged
DrugRef revision accepts these legacy settings through its MariaDB adapter, as
well as its existing MariaDB settings. Packaged defaults are unchanged.

CI now opens the built image's actual WAR, instantiates its configured datasource,
resolves its driver and URL settings, loads that driver, and requires it to accept
the effective URL. This check runs without network access and fails before the
existing Tomcat startup smoke if driver configuration is incompatible. Include
the mounted properties in the DrugRef image freshness hash so a configuration
change reruns the check.

Eight container controls on Ubuntu 26.04 / OpenJDK 25 pass their expected outcomes:

- Original properties fail with the missing MariaDB driver using the immutable
  master's actual datasource XML and declared Hikari/MySQL dependencies.
- Repaired properties succeed with that dependency set.
- A missing driver and an unsupported JDBC URL are both rejected.
- The actual packaged DrugRef WAR accepts both repaired legacy settings and its
  modern MariaDB settings.
- The complete CI shell entry point succeeds against that WAR with repaired
  settings and fails with a deliberately missing driver.

These are driver-wiring checks. They do not establish database connectivity or a
complete devcontainer deployment. Current-head CI image-build results are pending.
