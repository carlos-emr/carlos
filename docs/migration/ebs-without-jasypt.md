# EBS WS-Security without Jasypt

## Finding and decision

The upstream [Jasypt repository](https://github.com/jasypt/jasypt) is archived and
its README states that it is no longer maintained. The November 2025 maintenance
notice applies to `org.jasypt:jasypt:1.9.3`. The older Melloware fork is not a
supported upgrade: its latest published release, 1.9.4, dates from 2018.

The dashboard migration removed the application's direct use, but the release
2026.08 dependency tree still included:

```text
apache-cxf:4.1.9
  cxf-rt-ws-security:4.1.9
    wss4j-ws-security-common:4.0.2
      org.jasypt:jasypt:1.9.3
```

Jasypt encrypts **local Crypto-property passwords**, not EBS SOAP messages.
WSS4J's `WSHandler.getPasswordEncryptor` constructs `JasyptPasswordEncryptor`
whenever a callback is configured and no explicit encryptor is supplied, even
when all keystore passwords are ordinary values. Excluding the JAR alone would
therefore break keystore loading with `NoClassDefFoundError`.

EBS now supplies `PlaintextCryptoPasswordEncryptor` in both interceptor maps and
the CXF request context. Merlin still loads ordinary passwords itself; the
explicit SPI rejects `ENC(...)` values without logging their contents. Jasypt
is excluded from CXF and banned by Maven Enforcer to prevent reintroduction.
Outbound signature properties keep CXF's existing resource resolution and lazy
loading: the file is read when first needed, not while constructing the client.

## Existing MCEDT and HCV deployments

Keep the existing external `clientKeystore.properties`, keystore, aliases, and
EBS credentials. No password re-encryption, new salt, or wire-protocol migration
is required for ordinary Crypto-property passwords. SOAP UsernameToken,
timestamps, signatures, response decryption, attachments, and the Ministry's
legacy algorithms remain implemented by CXF, WSS4J, XML Security, and JCA.

This is separate from the dashboard's Spring Security Crypto migration.
Replacing EBS SOAP encryption with Spring Crypto would change the protocol and
is not an interoperable replacement.

## Encrypted Crypto properties

The EBS `ClientPasswordCallback` has never supplied WSS4J's
`PASSWORD_ENCRYPTOR_PASSWORD` callback, so `ENC(...)` properties were not a
supported EBS configuration. They continue to fail. Inbound loading reports an
explicit migration message; CXF's existing outbound fallback can instead report
a keystore-loading error.
They are never passed through as plaintext or decrypted using a guessed key.

Before upgrading any locally customized deployment that does use `ENC(...)`:

1. Recover the original keystore/truststore password using the existing secret
   provisioning process. Do not put it in source control, logs, or tickets.
2. Have the deployment's secret manager provision the password into the external
   Crypto properties file selected by `mcedt.service.clientKeystore.properties`
   or `hcv.service.clientKeystore.properties`.
3. Restrict the file and parent directory to the application service account
   (for example, file mode `0600`), retain encrypted storage/backups, and test
   keystore loading before switching the application.

Customized integrations that require encrypted property values need a separately
configured, maintained `PasswordEncryptor` and a defined secret-management
contract. Do not add Jasypt back or substitute the dashboard ciphertext format.

## Verification

`EdtWsSecurityWithoutJasyptUnitTest` exercises the production inbound and outbound
property maps with synthetic PKCS12 and JKS keystores, real WSS4J signing/decryption, and
checks that Jasypt classes are absent. The MCEDT encrypted-response and attachment
regressions also run without Jasypt. No live Ministry credentials are used.
Regression tests also preserve signature-file provisioning and updates between
client construction and first use. These lifecycle checks and the signing/decryption
checks were compared with the release implementation using its original Jasypt JAR.

```bash
mvn test -Dtest=EdtWsSecurityWithoutJasyptUnitTest,DynamicWSS4JInInterceptorUnitTest,DynamicWSS4JEncryptionResultsUnitTest
mvn dependency:tree -Dincludes=org.jasypt:*,com.melloware:jasypt
```

The dependency query must return no matching artifacts. Maven's normal
`validate` phase checks both the dependency lock and the Jasypt ban.
