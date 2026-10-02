# Excelleris Lab Pull (`release/excelleris_pull.py`)

## Purpose

`excelleris_pull.py` pulls pending lab results from the Excelleris HL7 pull endpoint and
uploads them straight into CARLOS EMR, or into OSCAR 19, with no middleware in between.

It is one of two Excelleris options shipped under `release/`:

| Option | Pull | Hand-off to the EMR | Choose it when |
|---|---|---|---|
| `ExcellerisDownload.sh` | `curl` with the clinic certificate | Drops the file for a Mule 1.3.3 / `hl7_file_management` bridge to upload | The site already runs the Mule bridge and wants to keep that pipeline. |
| `excelleris_pull.py` | Python, same protocol | Uploads directly over the EMR's lab-upload route | The site has no bridge, or prefers not to run one. |

Both speak the same Excelleris protocol and both end at the same EMR upload action; the
difference is whether Mule sits in between. This guide covers the Python option.

No change to CARLOS or OSCAR is needed. The upload uses the lab-upload route both EMRs
already expose to external lab senders (`lab/newLabUpload`), which is exactly what the
Mule bridge spoke. CARLOS checksums every upload and answers `409` for a file it already
imported, which is what makes the tool's retry logic safe. OSCAR 19 records the checksum
before it imports, so there a `409` that follows a failed attempt is not proof of import; the
tool moves such a file to `failed/` for a person to verify (see Operations).

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
3. **Upload.** Log in to the EMR (CARLOS always; OSCAR 19 only when credentials are
   configured), obtain a CSRF token (CARLOS only), and post each inbox file as the signed and
   encrypted envelope the EMR's lab upload action expects.
4. **Housekeeping.** Uploaded files are compressed into `<state_dir>/done`; files the EMR
   definitively rejected move to `<state_dir>/failed` for a person to look at; `done` files
   older than the retention window are purged.

Every failure after a run has started ends in an alert email (if configured), an `ERROR` line
in the log and a non-zero exit. Configuration errors and a root refusal exit 2 before logging
starts and report on stderr only. The only quiet early exit is when a previous run still holds
the lock.

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
  three to log in first instead; either way works. If you do log in, note that OSCAR 19
  blanks a username that is not plain alphanumeric (at most 30 characters) and a PIN that
  is not all digits (at least four) before checking them, so such an account can never log
  in from a script. OSCAR 19 answers the scripted login with a JSON body rather than the
  redirect CARLOS sends; the tool accepts both.
- **The Mule key file is reusable.** A site that ran the Mule bridge has a `keyPair.key`, the
  Create Key download that holds the service name, the client private key and the server
  public key. Point `[carlos] key_pair_file` at it and nothing else in `[carlos]` beyond
  `flavour` and `base_url` is required. The key's type stays whatever it was registered with.
- The Key Manager is at `admin/keygen/`. The private key is also available from
  `admin/keygen/getPublicKey.json?id=<service>`, the server public key from
  `admin/keygen/keyManager.jsp`.
- `base_url` is the OSCAR context path, for example `https://emr.example.ca/oscar`.
- `[excelleris]` is the same section whatever the flavour. `product` keeps its default of
  `CARLOS` on OSCAR 19 unless set; `product = OSCAR` sends the OSCAR 19 package script's
  header instead.

The OSCAR 19 contract above was verified against the shipped artifact as well as the branch:
the `oscar-emr` 19-99~5040 package carries the Bitbucket build 5040 WAR byte for byte
(manifest `git-SHA-1` b425be0, the `stable` head), and the compiled `LoginFilter`,
`LabUploadAction`, `LabUploadForm`, `LoginAction` and `ExcellerisOntarioHandler` classes of
build 5036 disassemble to exactly the behaviour described.

## One-time setup on the host

1. Install `python3-cryptography` (`apt install python3-cryptography`); any version from 3.4
   (Ubuntu 22.04) upward works. Nothing else is needed beyond the Python standard library.
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

5. Put the Excelleris PFX beside it, also mode 0600. If only the PEM pair extracted from the
   PFX is available (a GoFetchRover installation keeps one), use `client_cert_file` and
   `client_key_file` instead; the key file must be 0600. If you keep the two EMR keys in
   files rather than inline (`client_private_key_file`, `server_public_key_file`), those
   files must be 0600 too.
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
| `pfx_file` | yes, unless the PEM pair is given | Path to the PFX Excelleris issued, mode 0600. |
| `pfx_password` | no | PFX passphrase. |
| `client_cert_file` and `client_key_file` | alternative to `pfx_file` | The certificate (with chain) and the unencrypted private key extracted from the PFX, as PEM. The key file must be mode 0600. Cannot be combined with `pfx_file`. |
| `timeout_seconds` | no | Per-request timeout, default 60, minimum 5. |
| `ca_file` | no | Extra PEM bundle to trust, for a TLS-intercepting proxy or a test endpoint. Server verification is never disabled. Must be a regular file owned by the service user or root and not writable by group or other. |
| `product` | no | `CARLOS` (default) or `OSCAR`: which shell script's User-Agent to send, byte for byte (the CARLOS `ExcellerisDownload.sh` or the OSCAR 19 package's). Independent of `[carlos] flavour`: the EMR behind the tool never changes the header Excelleris sees. |
| `user_agent` | no | The whole User-Agent header, sent verbatim; overrides `product`. For a site that must keep a string it was conformance-tested under. |

### `[carlos]`

| Key | Required | Meaning |
|---|---|---|
| `flavour` | no | `carlos` (default) or `oscar19`. Selects the EMR routes and the CSRF step only; it has no effect on the `[excelleris]` section. |
| `base_url` | yes | EMR base URL including the context path, no trailing slash. |
| `username` | carlos: yes; oscar19: optional | Service account login, 1 to 30 letters or digits. For `oscar19`, leaving all three credentials empty uploads without a session, as Mule did. Set all three or none. |
| `password` | as above | Service account password. |
| `pin` | as above | Four digits. |
| `service` | yes, unless `key_pair_file` names it | Key name registered in Key Manager. The EMR picks the upload handler from the key's type. |
| `key_pair_file` | alternative to the two keys | The `keyPair.key` file the Create Key page downloads (service name, client private key, server public key). Mode 0600. Cannot be combined with the keys below. |
| `client_private_key` or `client_private_key_file` | one of, unless `key_pair_file` | Base64 PKCS#8 private key from the Key Manager JSON endpoint. PEM armour and line breaks are tolerated. |
| `server_public_key` or `server_public_key_file` | one of, unless `key_pair_file` | Base64 X.509 public key from the Key Manager page. |
| `timeout_seconds` | no | Per-request timeout, default 120, minimum 5. |
| `ca_file` | no | PEM bundle for an EMR served under a private CA. Must be a regular file owned by the service user or root and not writable by group or other. |
| `max_upload_attempts` | no | Runs with a transient upload failure (5xx, 429, a redirect to the login page, an unreadable reply) tolerated for one file before it moves to `failed/`. Default 24, minimum 1. Permanent rejections (400, 403, 406) go to `failed/` at once. |

### `[paths]`

| Key | Required | Meaning |
|---|---|---|
| `state_dir` | yes | Absolute path. The tool creates `inbox/`, `done/` and `failed/` under it (mode 0700) and `run.lock` (mode 0600). Lab results live here: keep it on local, encrypted storage and out of any backup that is not itself PHI-grade. |
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
| `--dry-run` | Log in and out of Excelleris, and of the EMR where credentials are configured. No pull, no upload. Cannot be combined with the two modes below. | none |
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

A system crontab entry in `/etc/cron.d/carlos-excelleris` (the user field is only valid there):

```
*/15 * * * * carlos-excelleris /usr/bin/python3 /opt/carlos-excelleris/excelleris_pull.py --config /etc/carlos-excelleris/pull.conf
```

Or, in the service user's own crontab (`crontab -u carlos-excelleris -e`), the same line without
the user field.

Or a systemd timer. The service unit should run as the service user and may use
`ProtectSystem=strict` with `ReadWritePaths=` for the state and log directories, following
the pattern of the `carlos-emr-backup` units shipped by the Debian package.

## Operations

- **`inbox/`** holds pulls that Excelleris has acknowledged but the EMR has not yet
  accepted. The next run retries them before pulling anything new. A non-empty inbox after
  a run always comes with an alert. A transient EMR failure (5xx, a proxy error, a session
  bounce) leaves the file here with a `.attempts` sidecar that counts one attempt per run (a
  run retries the backlog before and after the pull, but counts it once); after
  `max_upload_attempts` such runs it moves to `failed/` so a file that fails every time still surfaces.
- **`done/`** holds `.xml.xz` copies of imported pulls until `retention_days` expires.
- **`failed/`** holds files the EMR rejected for a reason in the request itself (400, 403, a
  406 signature failure) or that exhausted their transient-failure attempts. They are not retried. Fix the cause, then move the file back into
  `inbox/` or run it through the EMR's own upload page.
- **Duplicates** are harmless. If a positive acknowledgment was lost and Excelleris re-sends,
  the EMR answers `409` and the tool treats that as success. One exception, on OSCAR 19 only:
  its upload action records the file's checksum before it parses, so a `409` that follows an
  earlier failed attempt (a 5xx, or a connection lost mid-upload) does not prove the results
  were imported. The tool moves that file to `failed/` and alerts. Check the EMR inbox for
  the results; if they are missing, an administrator must delete the file's row from the
  `fileUploadCheck` table before the same bytes can be uploaded again.
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
| `duplicate (409) after an earlier failed attempt` | OSCAR 19 only. The checksum was recorded by an attempt that then failed; the results may not be in the EMR. Verify in the EMR inbox (see Operations). |
| `signature validation failed` (406) | `service` does not match the key name, or the client private key is not the one the EMR generated for it. |
| `upload-source validation` (403, CARLOS) | CARLOS refused the upload before checking the signature; see the CARLOS log. |
| `instead of an upload result` | The EMR answered HTTP 200 with a page, not a result: usually the multipart layer refused the request (size limit). The file stays in `inbox/`. |
| `could not import the file` | The handler type on the key is wrong for the feed, or the EMR log has the parse error. |
| `uses a cipher this OpenSSL does not enable` | The PFX uses a legacy cipher. Re-export it with the command in the message. |

## Converting a GoFetchRover (LifeLabs/Rover) installation

GoFetchRover (GetWellClinic, AGPL-3) is a Docker bundle in which a `rover` container pulls
from Excelleris and drops the XML into a folder that a `muled` container (the same Mule 1.3.3
bridge) uploads to OSCAR. Nothing from it is reused here, but everything it already holds
maps onto this tool's configuration, so a switch is a copy of existing material, not a new
enrolment with LifeLabs or a new key in OSCAR. Default install root is `/opt/gofetchrover`.

### What to copy, and where it goes

| GoFetchRover location | Holds | `pull.conf` |
|---|---|---|
| `volumes/rover/rover_config.json` → `base_url` | `https://api.on.excelleris.com/` or the `ontest` host, trailing slash | `[excelleris] url` = that value with `hl7pull.aspx` appended, no trailing slash before it |
| `rover_config.json` → `user_id`, `password` | LifeLabs credentials | `[excelleris] user_id`, `password` |
| `volumes/secrets/*.pfx` | The PFX LifeLabs issued (uploaded there before the extract step) | `[excelleris] pfx_file`, `pfx_password`, if the passphrase is still known |
| `volumes/secrets/client_certificate*.pem` and `client_key*.pem` | The PEM pair the extract step produced (`openssl pkcs12 -clcerts` / `-nocerts -nodes`) | `[excelleris] client_cert_file`, `client_key_file`, when the PFX or its passphrase is gone. The key file must be mode 0600. |
| `volumes/secrets/root_certificate*.pem` | LifeLabs root for the **test** host; production uses public CAs | `[excelleris] ca_file` only for the test host; leave unset for production |
| `rover_config.json` → `app_name`, `app_version` | The User-Agent LifeLabs conformance-tested the site under (`GoFetchRover`, `1.0.0-alpha`) | `[excelleris] user_agent` with that exact string if the site wants to keep it; otherwise `product` selects one of the two shell scripts' exact headers |
| `volumes/keys/<Service>.key` | The OSCAR Key Manager download, one per lab; `LifelabsHL7` (type `ExcellerisON`) for Ontario, `LifelabsRover` (type `EXCELLERIS`) for BC in their naming | `[carlos] key_pair_file` pointing at the LifeLabs one. The service name inside the file is used; `service` may stay empty. |
| `volumes/LabProperties.properties` → `oscarURL` | `https://host:8443/oscar/lab/newLabUpload.do` | `[carlos] base_url` = that URL without `/lab/newLabUpload.do`; `flavour = oscar19` when the URL ends in `.do` |
| `LabProperties.properties` → `smtpServer`, `recipientEmailAddress` | Mule's error mail | `[alerts] email`; delivery is through the host's sendmail rather than an SMTP setting |
| `volumes/incoming/<KeyName>/`, `volumes/completedHL7dir/`, `volumes/errorHL7dir/` | Mule's inbox, done and error folders | `[paths] state_dir` with `inbox/`, `done/`, `failed/` created by the tool |
| `volumes/rover/gfr.log`, `rover_error.log` | Rover's logs | `[paths] log_file` |
| `Docker/rover/Dockerfile` cron line (`1 8,20 * * *` by default) | Pull schedule | The cron entry or timer for this tool |

### Steps

1. Stop only the Rover container (`docker stop gofetchrover-rover-1`). Leave `muled` running
   if Dynacare, Alpha Labs or Med-Health still feed it; this tool replaces the LifeLabs leg
   only, and Mule keeps serving the other labs untouched.
2. Create the service user and directories as in the host setup above, and copy the files in
   the table into `/etc/carlos-excelleris/` with mode 0600 (the certificate PEM may stay
   world-readable; the key, PFX, key pair and config must not be).
3. Fill in `pull.conf` from the table. Three details that differ from GoFetchRover:
   - `url` is the full endpoint. GoFetchRover appends `hl7pull.aspx` itself.
   - A self-signed Excelleris endpoint is not accepted. GoFetchRover allows
     `"root_cert_path": false`; here, put the endpoint's certificate in `ca_file` instead.
   - The OSCAR login (`username`, `password`, `pin`) is not needed for `flavour = oscar19`.
     Leave all three empty and the upload is session-less, exactly as Mule's was.
4. Run `--check-config`, then `--dry-run` against the same `base_url` GoFetchRover used,
   then one real pull against the LifeLabs test host if the site still has test
   credentials. A result file that GoFetchRover had already handed to Mule is answered
   with 409 by OSCAR and treated as success (no failed attempt of this tool precedes it).
5. Schedule the tool and remove the Rover cron, or the whole `rover` service from
   `docker-compose.yml`.

### Behaviour differences worth knowing

| GoFetchRover | This tool |
|---|---|
| Sends the Excelleris parameters as a POST body. | Sends them as a GET query, as the OSCAR shell script always did. Excelleris accepts both. |
| Queries with `Pending=Yes`. | Same. |
| Refuses the pull and sends a negative ack when `MessageCount` disagrees with the number of `Message` elements. | Logs the disagreement and trusts the actual elements; results are not left pending over a header count. |
| Writes the XML to `volumes/rover/xml/` and copies it to the Mule inbox; Mule uploads and moves it to `completedHL7dir`. | Writes once to `inbox/`, uploads directly, compresses into `done/`. |
| Positive ack after the copy to the Mule folder, before the upload. | Positive ack after the file is fsync'd locally, before the upload. Same ordering, same recovery: an unsent file is retried next run and a duplicate is a 409. |
| Error mail from Mule via SMTP. | Alert mail from the tool via sendmail, naming the failing step. |
| Runs as a container; secrets in `volumes/secrets`. | Runs as a service user; refuses root; refuses group- or world-readable secrets. |

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

## Replacing `ExcellerisDownload.sh` with `excelleris_pull.py`

Only if a site chooses to move from the shell-and-Mule option to this one. The tool takes
the shell script's cron slot as is. On the Excelleris side the requests are the script's
requests, whatever `[carlos] flavour` is set to (the Excelleris session code never reads it):

- The same four `GET`s in the same order, with the same query strings byte for byte
  (parameter names and order included): `Page=Login&Mode=Silent&UserID=…&Password=…`,
  `Page=HL7&Query=NewRequests&Pending=Yes`, `Page=HL7&ACK=Positive|Negative`, `Logout=Yes`.
- The same request headers: `Host`, the User-Agent (`product = CARLOS`, the default, is the
  script's string byte for byte, escaped slashes included), `Accept: */*` (curl's default),
  and the session cookie Excelleris set at login. The tool's HTTP library adds two transport
  headers curl does not send, `Accept-Encoding: identity` and `Connection: close`; they
  govern compression and connection reuse only. curl negotiates HTTP/2 where a server offers
  it; the tool always speaks HTTP/1.1.
- The same client certificate from the same PFX, with server verification on.
- The same acknowledgment rule: positive only when the pull holds at least one `<Message>`
  (the script tested the first line for `<Message `; the tool parses the document), negative
  otherwise, including after a failed download; `ReturnCode="0"` accepted.
- One difference by design: a password is URL-encoded. The script sent it raw, which broke
  on `&`, `+`, `%`, `#` and spaces. For any other password the bytes are identical.

`ShellScriptWireParityTest` in the test file runs curl with the script's exact flags and the
tool's real transport against one recording TLS server and compares the request heads.

On the host side: the same `YYYYMMDD-HHMMSS.xml` file names, the same `xz` compression of
the kept copy (the script ran `xz` on its own copy and on Mule's done directory; the tool
compresses into `done/`), one run at a time under a lock, an email on failure. What the script handed to Mule, the tool uploads
itself.

| `ExcellerisDownload.sh` flag | `excelleris_pull.py` |
|---|---|
| `-s` (suppress the Mule hand-off) | `--no-upload` |
| `-a` (go on to the Mule hand-off even when nothing was pulled) | Every run already uploads whatever is in `inbox/` before and after the pull; `--upload-only` does only that, without a pull. |
| `-v` | `-v` / `--verbose` |
| `-h` | `--help` |

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

## Moving a site from OSCAR 19 to CARLOS

A site that runs the tool against OSCAR 19 keeps the same installation when the EMR
changes. What carries over and what changes in `pull.conf`:

| Item | On the move |
|---|---|
| `[excelleris]` (credentials, certificate, `url`, `product` / `user_agent`, `timeout_seconds`) | Unchanged. The header Excelleris sees is the same before and after. |
| `[carlos] flavour` | `oscar19` → `carlos`. |
| `[carlos] base_url` | The CARLOS context path. |
| `[carlos] username`, `password`, `pin` | Required on CARLOS: a provider login with `_lab` write access, no MFA, one facility, no pending password reset. |
| `[carlos] service`, `key_pair_file` or the two keys | Unchanged when the database was imported with `carlos-ctl`: the OSCAR 19 import copies the `publicKeys` and `oscarKeys` tables (both are listed as copied in `debian/assets/o19-manifest/o19_preflight.json`), so the service name, the client private key and the server public key stay valid. A CARLOS set up with a fresh database needs a new key from its Key Manager instead. |
| `[paths] state_dir`, `log_file`, `[alerts]` | Unchanged. Files still in `inbox/` at the switch are uploaded to CARLOS by the next run; `done/` keeps its retention. |

Run `--check-config` after editing, then `--dry-run` to prove the CARLOS login and CSRF
token before the first scheduled run.

## Security notes

- Excelleris mandates credentials in the query string of a GET. That is their protocol. The
  URL is built in memory, sent over mutual TLS, and never written to a log or an error.
- The PFX, or the PEM pair, is combined into one PEM in a private temporary directory for the
  lifetime of one run, because Python's `ssl` module can only load a client certificate from
  a file. The file is 0600 and deleted in a `finally`.
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
`ShellScriptWireParityTest` also needs `curl` on the machine running the tests and is skipped
without it.

## Known limits

- The EMR stand-ins in the tests encode the CARLOS and OSCAR 19 contracts as read from their
  source. A `--dry-run` against a real dev instance is the gate before production.
- CARLOS refuses multipart uploads above 50 MB (`struts.multipart.maxSize`); OSCAR 19 above
  100 MB. The tool warns before sending a file that large.
- `release/` is gitignored in this repository. New files there must be added with
  `git add -f`.
