# Application Encryption Key

CARLOS encrypts these stored values at rest with the application key
`encryption.util.secret.key`:

- fax account passwords (`fax_config.passwd`, `fax_config.faxPasswd`);
- the Teleplan password (BC);
- MFA secrets (`security.mfaSecret`);
- digital signature images (`DigitalSignature.signatureImage`).

Later releases also encrypt email sender credentials (`emailConfig.configDetails`)
and SMS settings (`sms_config`) with the same key. This release stores email
credentials in plaintext and has no SMS settings, but the startup check below
looks at both too, because a database may already have run on a later release.

Everything encrypted with the key can only be decrypted with that exact key, so
back the key up with the rest of the server's configuration and never replace
it on a server that has been running.

## Where the key comes from

A packaged (Debian) install gets its key from `carlos-ctl init-config`, which
writes it to `/etc/carlos-emr/carlos.properties`. That command and the package
scripts can still generate a missing key without checking existing data
([#4371](https://github.com/carlos-emr/carlos/issues/4371)). For a restored or
existing database, restore its original key before running `init-config`,
`finish-install`, or installing, upgrading, reconfiguring or removing the
packages. The startup check below cannot detect a replacement key already
written by those paths.

Otherwise, when no key is set at startup, CARLOS first checks the database for
data encrypted with a previous key (#3939):

| What the check finds | What CARLOS does |
|---|---|
| Nothing encrypted (a fresh install) | Generates a key and appends it to `<context>.properties` in the Tomcat user's home directory (for example `~/carlos.properties`), as before. |
| Possibly encrypted data | Refuses to start, rather than risk losing access. One ERROR names the kinds of data, conservative counts, and the fix. |
| It cannot read the database | Refuses to start, because it cannot show that nothing would be lost. The ERROR names what could not be read. |

CARLOS also refuses to start with an invalid key. The refusal reads, for
example:

```text
encryption.util.secret.key is missing or blank, but 3 items in the database may be
encrypted with the original key (fax accounts: 1, users with an MFA secret: 2).
Refusing to start: a new key cannot decrypt data encrypted with the original key.
Fix: restore the original
encryption.util.secret.key from backup into the properties file, then restart.
Only if the original key is lost for good: set
encryption.util.secret.key.acknowledge_loss=true and restart. ...
```

## If the key is lost

1. Restore the original `encryption.util.secret.key` from backup into the
   properties file it was configured in, and restart. On a packaged install,
   see README.Debian for restoring `/etc/carlos-emr`.
2. Only if the original key cannot be recovered, add
   `encryption.util.secret.key.acknowledge_loss=true` (`yes` and `on` also work)
   and restart. CARLOS generates a new key and logs one ERROR giving the number
   of possibly encrypted items. Values encrypted with the lost key are unreadable;
   legacy plaintext signatures are unaffected. Then:
   - re-enter the password of every fax account in
     Administration > Faxes > Configure Fax;
   - re-enter the Teleplan password (BC);
   - reset MFA on each affected user's security record; those users cannot log
     in until it is reset (if every administrator is affected, one
     administrator's `security.mfaSecret` has to be cleared in the database
     first, which sends them through MFA registration at their next login);
   - stored signature images encrypted with the old key cannot be recovered;
   - if the ERROR also names email sender accounts or SMS settings (encrypted by
     a later release), re-enter those credentials once that release is running.
3. Remove `encryption.util.secret.key.acknowledge_loss`. Left in place it would
   let a future loss of the key through without the check, so CARLOS logs a
   WARN at every start while it is set.

Logs never contain the key, a credential or stored ciphertext. The messages
carry kinds of data and counts only.

## Limits of the check

It recognises an encrypted value by the `{ENC}` marker followed by well-formed
Base64 long enough to hold the IV and tag. Digital signatures have no encryption
marker: random ciphertext can start with a valid image header. Every signature
of at least 28 bytes is therefore treated as possibly encrypted, including
legacy plaintext images. This can refuse startup on an older installation
holding only plaintext signatures when its key is absent; the refusal then says
that only signature images were found. What matters is the database's history,
not the server's.

- **Look for the key first.** Plain OSCAR never had an `encryption.util.secret.key`
  line, so if the old server's properties file, or a backup of it, has one,
  restore it. CARLOS and OpenO EMR (since September 2024) create the key by
  themselves, so any database they have run on had a key, even if nobody set
  one, and even if this server was later rebuilt from a backup without it. On
  the old server (or this one) or in its backup, look in
  `/etc/carlos-emr/carlos.properties` (packaged install), in `<context>.properties`
  in the home directory of the user Tomcat runs as (for example
  `carlos.properties` or `oscar.properties`), and in the file named by
  `-Dcarlos_override_properties` or, on an OpenO EMR server,
  `-Doscar_override_properties`. On a packaged install, until the key is
  restored, do not run `carlos-ctl init-config` or `finish-install`, and do not
  install, upgrade, reconfigure or remove the `carlos-emr` packages: each of
  these can write a new key, and CARLOS then starts without this check.
- **Only** if the database comes straight from OSCAR, or from an OpenO EMR build
  from before December 2024, and no OpenO EMR build from December 2024 or later
  and no CARLOS ran on it, other than starts refused by this check, are the
  signatures plaintext. Then the deliberate override above loses nothing, and
  the plaintext signatures stay as they are.
- If you are not sure, treat the signatures as encrypted and keep looking for
  the key.

Plaintext strings shaped like ciphertext are also counted (see #3132). The check
runs only when the key is missing: a valid but wrong key, such as a new one
pasted in by hand, is not detected at startup.
