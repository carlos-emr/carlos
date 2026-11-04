# Startup encryption key

CARLOS 2026.08 requires a stable encryption key before the web application
starts. The Java property is **`encryption.util.secret.key`** in
`carlos.properties`; an environment variable named `ENCRYPTION_KEY` does not
configure it.

If the effective configuration has a nonblank, valid Base64 AES key, startup
uses it, including when that configuration comes from a classpath resource.
The decoded key must contain 16, 24 or 32 bytes.

If the key is missing or blank, CARLOS generates and persists one only when
the loaded configuration is an external, writable properties file. It refuses
startup for classpath-only configuration: it cannot safely persist a generated
key in the WAR, and does not create a fallback file in `user.home`. A temporary
key would make encrypted data unreadable after restart.

## Configure a new demo, CI or standalone WAR deployment

1. Supply an external `carlos.properties` containing the deployment's database
   and document settings. Keep the file outside the WAR and on persistent storage.
   Migrate any legacy `<webapp-context>.properties` in the Tomcat account's
   `user.home` (usually `carlos.properties`) into that file, preserving its original
   key, then move the legacy file out of the startup search path. The startup
   listener loads home-directory properties after the explicit override, so a
   stale home file can replace the override's settings and key.
2. Set `-Dcarlos_override_properties=/absolute/path/to/carlos.properties` in
   Tomcat's `CATALINA_OPTS`. The Tomcat service account must be able to read it;
   first startup with a blank key also needs permission to update the file.
3. Either allow CARLOS to generate and append a key at first startup, or
   provision `encryption.util.secret.key` before startup when configuration is
   read-only. For a **new, empty deployment only**, `openssl rand -base64 32`
   generates a suitable value. Store it securely in the properties file, without
   placing it in build logs, source control or a shared demo image.
4. Back up the properties file and key securely alongside the database and
   document store. Reuse the same key on every restart and application replica.

## Recover an existing deployment

If encrypted data already exists, restore its **original key** from the previous
configuration or backup. Check the previous Tomcat account's home directory for
the legacy `carlos.properties` file as well as external overrides. Copy that key
into the configuration actually loaded by the application before restarting.
Generating a replacement key does not recover existing encrypted data.

The startup exception names the loaded source and the configuration setting.
Correct that source or select the external file explicitly after migrating any
home-directory configuration, then restart and
verify that existing encrypted credentials can still be read. Do not print keys
or decrypted credentials while troubleshooting.

## Repository deployment paths

- The devcontainer selects `/root/carlos.properties` through `CATALINA_OPTS` in
  `.devcontainer/development/Dockerfile`; its setup copies the development
  properties into that external file. The file survives a restart of the same
  container, but **does not survive container recreation**. The database can
  survive through its volume, so preserve the key before rebuilding:

  1. Stop Tomcat in the existing container with
     `docker exec carlos-tomcat-dev server stop` to stop application writes.
  2. Export the complete configuration to a private directory outside the
     repository and keep it with the database backup. For example, after
     creating `/absolute/private/carlos-config` with mode `0700`, run:

     ```bash
     umask 077
     docker cp carlos-tomcat-dev:/root/carlos.properties /absolute/private/carlos-config/carlos.properties
     chmod 0600 /absolute/private/carlos-config/carlos.properties
     ```

  3. Before recreating the container, add the following bind mount to the
     `carlos` service's `volumes` in `.devcontainer/docker-compose.yml` (or a
     local Compose override). The host file must already exist and contain
     the original key:

     ```yaml
     - /absolute/private/carlos-config/carlos.properties:/root/carlos.properties
     ```

  4. Rebuild/recreate only after the mount is configured. The first startup
     must see the preserved key; verify that existing encrypted data can still
     be read. Do not copy the key into the tracked development configuration
     or bake it into an image.
- The Debian installation provisions `/etc/carlos-emr/carlos.properties`;
  `debian/assets/bin/carlos-emr-tomcat` selects it with
  `-Dcarlos_override_properties`. The installer and CLI manage this external
  configuration. Loading demonstration data does not change that path.
- Ad-hoc WAR and CI smoke deployments must supply an external file or a valid,
  preconfigured key; bundling properties with a blank key is insufficient.

See also [2026.08 release notes](release-notes-2026.08.md) and
[Debian installation](install-deb.md).
