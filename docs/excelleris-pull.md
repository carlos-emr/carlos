# Excelleris Lab Pull (`release/excelleris_pull.py`)

## Purpose

`excelleris_pull.py` pulls pending lab results from the Excelleris HL7 pull endpoint and
uploads them straight into CARLOS EMR, or into OSCAR 19, with no middleware in between.

It is one of two Excelleris options shipped under `release/`:

| Option | Pull | Hand-off to the EMR | Choose it when |
|---|---|---|---|
| `ExcellerisDownload.sh` | `curl` with the clinic certificate | Drops the file for a Mule 1.3.3 / `hl7_file_management` bridge to upload | The site already runs the Mule bridge and wants to keep that pipeline. |
| `excelleris_pull.py` | Python, same protocol | Uploads directly over the EMR's lab-upload route | The site has no bridge, or wants to retire it. |

Both speak the same Excelleris protocol and both end at the same EMR upload action; the
difference is whether Mule sits in between. This guide covers the Python option.

No change to CARLOS or OSCAR is needed. The upload uses the lab-upload route both EMRs
already expose to external lab senders (`lab/newLabUpload`), which is exactly what the
Mule bridge spoke. CARLOS checksums every upload and answers `409` for a file it already
imported, which is what makes the tool's retry logic safe.

Files:

| File | Role |
|---|---|
| `release/excelleris_pull.py` | The tool. Python 3.10+, standard library plus `python3-cryptography`. |
| `release/excelleris_pull.conf.example` | Annotated configuration template. Copy and edit. |
| `release/test_excelleris_pull.py` | Unit and live-TLS tests. Run manually, see below. |
| `release/ExcellerisDownload.sh` | The shell-and-Mule option, unchanged. |

## How one run works

A run is normally started by cron or a systemd timer every few minutes.

1. **Retry.** Any file still waiting in `<state_dir>/inbox` from an earlier run is uploaded
   first, so a CARLOS outage never loses a pull that Excelleris has already been told we
   received.
2. **Pull.** Log in to Excelleris with the clinic's client certificate, pull pending results,
   write them to the inbox (fsync'd, mode 0600), and only then send Excelleris a positive
   acknowledgment. An empty pull, an error document, a failed pull or a failed write gets a
   negative acknowledgment, so Excelleris keeps the results pending.
3. **Upload.** Log in to the EMR, obtain a CSRF token (CARLOS only), and post each inbox file
   as the signed and encrypted envelope the EMR's lab upload action expects.
4. **Housekeeping.** Uploaded files are compressed into `<state_dir>/done`; files the EMR
   definitively rejected move to `<state_dir>/failed` for a person to look at; `done` files
   older than the retention window are purged.

Every failure ends in an alert email (if configured), an `ERROR` line in the log and a
non-zero exit. The only quiet early exit is when a previous run still holds the lock.

```
Excelleris --(mutual TLS GET)--> inbox/ --(AES + RSA + MD5withRSA multipart POST)--> EMR
                                   |                                                  |
                                   +------------- 200 or 409 -------------------------+
                                   v
                                 done/*.xml.xz   (failed/ on 4xx or 5xx)
```

## One-time setup in the EMR

No code change. All of this is done through the existing administration pages.

### CARLOS

1. **Service account.** Create a dedicated provider login for the tool. Give it only the
   `_lab` security object with write access. Do not enrol it in MFA, do not attach it to more
   than one facility, and do not leave a forced password reset pending. Each of those stops a
   scripted login, and the tool's alert says which one it hit.
2. **Key pair.** Administration > Key Manager > Create Key. The name is the value you will
   put in `[carlos] service`. Choose type `OTHER` and type `ExcellerisON` in the box. The
   type selects the upload handler; a BC site uses the handler its Excelleris feed is parsed
   with, normally `PATHL7`.
3. **Client private key.** As an administrator open
   `<base_url>/admin/keygen/getPublicKey?id=<service>` and copy the
   `base64EncodedPrivateKey` value. This endpoint writes an audit log entry each time it is
   read.
4. **Server public key.** Copy the long block at the top of the Key Manager page.

### OSCAR 19

Set `[carlos] flavour = oscar19`. The OSCAR 19 line (Bitbucket `oscaremr/oscar`, branch
`stable` and tag `OSCAR_19_RC1`; the `oscar-emr` Debian package is built from `stable` by
`release/make_deb.sh`, and its `source.txt` names that branch) has the same upload action, parameters, `ExcellerisON` and
`PATHL7` handlers and cryptography; it differs only in Struts 1 `*.do` routes, a
`GET logout.jsp`, and having no CSRF layer. The flavour switch covers exactly those three
things. Two parts of the setup are simpler than on CARLOS:

- **No login is needed.** OSCAR 19 exempts the upload route from its login filter and its
  upload action makes no privilege check. That is how the Mule bridge uploaded, with no OSCAR
  credentials at all. Leave `username`, `password` and `pin` empty to do the same. Set all
  three to log in first instead; either way works.
- **The Mule key file is reusable.** A site that ran the Mule bridge has a `keyPair.key`, the
  Create Key download that holds the service name, the client private key and the server
  public key. Point `[carlos] key_pair_file` at it and nothing else in `[carlos]` beyond
  `flavour` and `base_url` is required. The key's type stays whatever it was registered with.
- The Key Manager is at `admin/keygen/`. The private key is also available from
  `admin/keygen/getPublicKey.json?id=<service>`, the server public key from
  `admin/keygen/keyManager.jsp`.
- `base_url` is the OSCAR context path, for example `https://emr.example.ca/oscar`.

## One-time setup on the host

1. Install `python3-cryptography` (`apt install python3-cryptography`). Nothing else is
   needed beyond the Python standard library.
2. Create a service user, for example `carlos-excelleris`. The tool refuses to run as root.
3. Create the directories and give them to that user:

   ```sh
   install -d -m 0750 -o carlos-excelleris -g carlos-excelleris /etc/carlos-excelleris
   install -d -m 0700 -o carlos-excelleris -g carlos-excelleris /var/lib/carlos-excelleris
   install -d -m 0750 -o carlos-excelleris -g carlos-excelleris /var/log/carlos-excelleris
   ```

4. Copy `release/excelleris_pull.conf.example` to `/etc/carlos-excelleris/pull.conf`, fill
   it in, and make it private. The tool refuses to start if group or other can read it:

   ```sh
   chown carlos-excelleris:carlos-excelleris /etc/carlos-excelleris/pull.conf
   chmod 600 /etc/carlos-excelleris/pull.conf
   ```

5. Put the Excelleris PFX beside it, also mode 0600. If you keep the two keys in files
   rather than inline (`client_private_key_file`, `server_public_key_file`), those files
   must be 0600 too.
6. Validate offline, then prove the credentials and network path without pulling anything:

   ```sh
   sudo -u carlos-excelleris python3 excelleris_pull.py --config /etc/carlos-excelleris/pull.conf --check-config
   sudo -u carlos-excelleris python3 excelleris_pull.py --config /etc/carlos-excelleris/pull.conf --dry-run
   ```

   `--check-config` loads the keys, the PFX and any `ca_file` bundles and prints the
   configuration with secrets masked. `--dry-run` logs in and out of both Excelleris and the
   EMR and touches no data.

7. Point the configuration at the Excelleris **test** endpoint for the first real pull, then
   switch `url` to production.

## Configuration reference

INI format. Values are taken literally (`%` and `;` inside a value are fine). Do not quote.

### `[excelleris]`

| Key | Required | Meaning |
|---|---|---|
| `context` | no | Clinic name. Informational; appears in alert emails. |
| `url` | yes | Pull endpoint. Ontario test `https://api.ontest.excelleris.com/hl7pull.aspx`, Ontario production `https://api.on.excelleris.com/hl7pull.aspx`, BC replaces `on` with `bc`. |
| `user_id` | yes | Clinic Excelleris user id. |
| `password` | yes | Clinic Excelleris password. Any characters; it is URL-encoded correctly. |
| `pfx_file` | yes | Path to the PFX Excelleris issued, mode 0600. |
| `pfx_password` | no | PFX passphrase. |
| `timeout_seconds` | no | Per-request timeout, default 60, minimum 5. |
| `ca_file` | no | Extra PEM bundle to trust, for a TLS-intercepting proxy or a test endpoint. Server verification is never disabled. |
| `product` | no | Product name Excelleris sees in the User-Agent comment. Defaults to `OSCAR` when `flavour = oscar19`, else `CARLOS`. Set it explicitly to whatever name is on file with Excelleris. |

### `[carlos]`

| Key | Required | Meaning |
|---|---|---|
| `flavour` | no | `carlos` (default) or `oscar19`. |
| `base_url` | yes | EMR base URL including the context path, no trailing slash. |
| `username` | carlos: yes; oscar19: optional | Service account login, 1 to 30 letters or digits. For `oscar19`, leaving all three credentials empty uploads without a session, as Mule did. Set all three or none. |
| `password` | as above | Service account password. |
| `pin` | as above | Four digits. |
| `service` | yes, unless `key_pair_file` names it | Key name registered in Key Manager. The EMR picks the upload handler from the key's type. |
| `key_pair_file` | alternative to the two keys | The `keyPair.key` file the Create Key page downloads (service name, client private key, server public key). Mode 0600. Cannot be combined with the keys below. |
| `client_private_key` or `client_private_key_file` | one of, unless `key_pair_file` | Base64 PKCS#8 private key from the Key Manager JSON endpoint. PEM armour and line breaks are tolerated. |
| `server_public_key` or `server_public_key_file` | one of, unless `key_pair_file` | Base64 X.509 public key from the Key Manager page. |
| `timeout_seconds` | no | Per-request timeout, default 120, minimum 5. |
| `ca_file` | no | PEM bundle for an EMR served under a private CA. |

### `[paths]`

| Key | Required | Meaning |
|---|---|---|
| `state_dir` | yes | Absolute path. The tool creates `inbox/`, `done/`, `failed/` and `run.lock` under it, all mode 0700. Lab results live here: keep it on local, encrypted storage and out of any backup that is not itself PHI-grade. |
| `log_file` | yes | Absolute path. Never contains result content or credentials. |
| `retention_days` | no | Days to keep compressed, already-imported pulls in `done/`. Default 90. `0` keeps forever and logs a warning every run. |

### `[alerts]`

| Key | Required | Meaning |
|---|---|---|
| `email` | no | Failure alerts go here through sendmail. Empty disables email; failures are still logged and still exit non-zero. |
| `from` | no | Sender address. Defaults to `carlos-excelleris@<hostname>`. |
| `sendmail` | no | Path to sendmail, default `/usr/sbin/sendmail`. |

## Running

```
excelleris_pull.py --config PATH [--check-config] [--dry-run] [--no-upload | --upload-only] [-v]
```

| Flag | Effect | Old script equivalent |
|---|---|---|
| `--check-config` | Validate config, keys, PFX and CA bundles. No network. | none |
| `--dry-run` | Log in and out of both systems. No pull, no upload. | none |
| `--no-upload` | Pull and acknowledge only; leave files in the inbox. | `-s` |
| `--upload-only` | Upload whatever is in the inbox; do not pull. | `-a` |
| `-v` | Debug logging. Adds request metadata only, never payloads. | `-v` |

Exit codes:

| Code | Meaning |
|---|---|
| 0 | Success, including "nothing to do". |
| 1 | A step failed. An alert was sent if email is configured. |
| 2 | Configuration or usage error, or run as root. |
| 3 | A previous run still holds the lock. Nothing was done and no alert is sent. |

### Scheduling

cron, as the service user:

```
*/15 * * * * carlos-excelleris /usr/bin/python3 /opt/carlos-excelleris/excelleris_pull.py --config /etc/carlos-excelleris/pull.conf
```

Or a systemd timer. The service unit should run as the service user and may use
`ProtectSystem=strict` with `ReadWritePaths=` for the state and log directories, following
the pattern of the `carlos-emr-backup` units shipped by the Debian package.

## Operations

- **`inbox/`** holds pulls that Excelleris has acknowledged but the EMR has not yet
  accepted. The next run retries them before pulling anything new. A non-empty inbox after
  a run always comes with an alert.
- **`done/`** holds `.xml.xz` copies of imported pulls until `retention_days` expires.
- **`failed/`** holds files the EMR answered with a definitive rejection (a signature failure,
  a parse failure). They are not retried. Fix the cause, then move the file back into
  `inbox/` or run it through the EMR's own upload page.
- **Duplicates** are harmless. If a positive acknowledgment was lost and Excelleris re-sends,
  the EMR answers `409` and the tool treats that as success.
- **Lock contention** (exit 3) means the previous run is still working, usually because the
  EMR is slow. It is not a failure and does not email.
- **Logs** carry one `>>>>>` line per run start and one `<<<<<` line per finish. Nothing
  from a result file is ever logged, with or without `-v`.

### Alert messages and what they mean

| Alert detail contains | Cause |
|---|---|
| `access denied` | Wrong Excelleris user id or password. |
| `unexpected reply` on Excelleris login | Endpoint URL wrong, certificate rejected, or Excelleris maintenance page. |
| `forced password reset pending` | Clear the flag on the service account in the EMR. |
| `enrolled in MFA` | The service account must not use MFA. |
| `more than one facility` | Attach the service account to exactly one facility. |
| `redirected the login to its logout page; check [carlos] flavour` | CARLOS routes were sent to an OSCAR 19, or the base URL is wrong. |
| `unexpected reply HTTP 404` on login | OSCAR 19 routes were sent to a CARLOS. |
| `could not obtain a CSRF token` | The CARLOS session was not established, or the base URL is wrong. |
| `signature validation failed` | `service` does not match the key name, or the client private key is not the one CARLOS generated for it. |
| `could not import the file` | The handler type on the key is wrong for the feed, or the EMR log has the parse error. |
| `uses a cipher this OpenSSL does not enable` | The PFX uses a legacy cipher. Re-export it with the command in the message. |

## What the Mule bridge did, and what this tool does instead

Read from `hl7_file_management` (Bitbucket `oscaremr/hl7_file_management`, `Uploader.java`
and `mule-config*.xml`), so the comparison is against the actual bridge, not a guess.

| Mule bridge | This tool |
|---|---|
| Polled an incoming directory every second for `*.hl7` and `*.xml` dropped by any producer. | Pulls from Excelleris itself and drops the result in `inbox/`; anything else placed in `inbox/` as `*.xml` is uploaded on the next run too. One feed (one service key) per configuration file; run several configurations for several feeds. |
| Read the service name and both keys from `keyPair.key`. | Same file via `key_pair_file`, or the two keys separately. |
| Signed with MD5withRSA, encrypted with AES then RSA PKCS#1 v1.5, posted `importFile`, `key`, `signature`, `service` as multipart to `lab/newLabUpload.do`. | Identical envelope and fields, plus `use_http_response_code`. Verified by tests that decrypt and verify exactly as the EMR does. |
| Never logged in. OSCAR 19 exempts the route from its login filter. | Same on `oscar19` when no credentials are configured. CARLOS requires a logged-in `_lab` session, so the tool logs in there. |
| Parsed `<outcome>` from the `uploadComplete.jsp` XML reply. | Reads the HTTP status, and still parses `<outcome>` if a build returns the XML, so a rejection is never archived as a success. |
| Moved files to `completed` or `error` directories, renamed with a timestamp; emailed on error. | `done/` (compressed, with retention) and `failed/`; alert email with the failing step. |
| Accepted any server certificate (`EasySSLProtocolSocketFactory`). | Verifies the server certificate; `ca_file` adds a private CA. |
| 8 second connection timeout, no read timeout. | Configurable timeout on connect and read. |
| For MDS results, appended the audit text to a `CURHST.0` file. | Not implemented. MDS is a different lab feed; an Excelleris upload's audit is always `success`. |

## Switching from `ExcellerisDownload.sh`

Only if a site chooses to move from the shell-and-Mule option to this one.

| `config_inc.txt` | `pull.conf` |
|---|---|
| `CONTEXT` | `[excelleris] context` |
| `USERNAME`, `PASSWORD` | `[excelleris] user_id`, `password` |
| `PFX`, `CERT_PASS` | `[excelleris] pfx_file` (full path now), `pfx_password` |
| `HL7URLPATH` | `[excelleris] url` |
| `MULE_HOME`, `MULE_HL7PATH`, `MULE_ERRORS`, `MULE_DONE`, `MULE_LOG`, `SLEEP` | Gone. Results go directly to the EMR. |
| `LOG_FILE` | `[paths] log_file` |
| `EMAIL` | `[alerts] email` |
| (none) | `[carlos]` section: the EMR login and keys the Mule bridge used to hold in its own configuration. |

Deliberate changes from the shell script:

- Runs as a service user and refuses root.
- Config file must be mode 0600; a world-readable config is rejected instead of sourced.
- Credentials never appear on a command line or in a URL that is logged.
- Result files are private (0700 directories, 0600 files) and are purged on a schedule.
- Every network call has a timeout.
- Every error path alerts. The shell script's `set -e` silently killed most of its own error
  handling, including the email.

## Security notes

- Excelleris mandates credentials in the query string of a GET. That is their protocol. The
  URL is built in memory, sent over mutual TLS, and never written to a log or an error.
- The PFX is unpacked to a PEM in a private temporary directory for the lifetime of one run,
  because Python's `ssl` module can only load a client certificate from a file. The file is
  0600 and deleted in a `finally`.
- The upload envelope (AES-128-ECB payload, RSA PKCS#1 v1.5 wrapped key, MD5withRSA
  signature) is the legacy format the EMR's `LabUpload2Action` decrypts. It is kept in one
  class, `LabUploadEnvelope`, so it can be swapped when CARLOS issue #3413 lands a modern
  format.
- Server certificates are always verified. `ca_file` adds a trust anchor; nothing disables
  verification.

## Testing

The tests need `python3-cryptography` and no network. They start two local HTTPS servers that
impersonate Excelleris (client certificate required) and the EMR (both flavours), and drive
the real transport, TLS, certificate handling and cryptography through them.

```sh
python3 -m unittest release/test_excelleris_pull.py -v
```

They are not part of the repository's CI, which does not discover tests under `release/`.

## Known limits

- The EMR stand-ins in the tests encode the CARLOS and OSCAR 19 contracts as read from their
  source. A `--dry-run` against a real dev instance is the gate before production.
- CARLOS refuses multipart uploads above 50 MB (`struts.multipart.maxSize`); OSCAR 19 above
  100 MB. The tool warns before sending a file that large.
- `release/` is gitignored in this repository. New files there must be added with
  `git add -f`.
