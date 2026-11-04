# CARLOS 2026.08 release notes

## Startup configuration compatibility

CARLOS refuses to start when the configuration is loaded only from the classpath
and `encryption.util.secret.key` is missing or blank. Earlier versions could
write a generated key to a `carlos.properties` file in the Tomcat account's home
directory. CARLOS now persists generated keys to the external configuration
that was actually loaded, so startup cannot silently depend on a different file.

Demo, CI and standalone WAR deployments must configure a stable key or select a
writable external `carlos.properties` using `-Dcarlos_override_properties`.
Packaged Debian installations and the devcontainer already select external
configuration files. A valid key supplied through classpath configuration
continues to work without writing to that resource.

For existing encrypted data, preserve and restore the original key; generating
a replacement prevents that data from being decrypted. See the
[startup encryption key guide](startup-encryption-key.md) for setup, deployment
paths and recovery instructions. This behavior is tracked in
[#5664](https://github.com/carlos-emr/carlos/issues/5664).
