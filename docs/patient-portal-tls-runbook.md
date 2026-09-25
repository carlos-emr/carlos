# Patient portal TLS pin runbook

How a clinic chooses where its patient portal's HTTPS ends, sets the TLS public-key pin CARLOS
checks, and keeps that pin valid through renewals, key rotations, compromise and failures. Each
section ends with what to write in the clinic's own
[deployment record](patient-portal-deployment-record-template.md).

**Who this is for:** the person who owns the portal's certificate and the CARLOS portal settings.
That should be one person (see [Ownership](#3-ownership)).

**Related:**
- [`patient-portal-client-security.md`](patient-portal-client-security.md) explains what the pin
  protects against and what it does not.
- [`patient-portal-staging-checklist.md`](patient-portal-staging-checklist.md) is the end-to-end
  staging run, which uses this runbook for its pin steps.
- `src/main/resources/carlos.properties`, under *Patient Portal integration*, documents each setting
  and the recipe for computing a pin from a certificate file.

## What CARLOS enforces

These rules are fixed in code, and no choice below loosens them.

- **A pin is required.** `patient_portal.certificate.pins` must hold at least one
  `sha256/<base64>` pin of the public key of the certificate CARLOS is served. Several pins are
  separated by commas. There is no fallback to plain certificate-authority trust. A missing or
  malformed pin stops the portal features; the rest of CARLOS keeps working.
- **Normal TLS still applies.** The certificate must also be trusted by the CARLOS JVM, be in date
  and match the hostname. Java does not fetch missing intermediate certificates, so the portal must
  serve its full chain. Redirects are refused.
- **Only the leaf key is pinned.** Pinning an issuing CA to survive key changes is not supported.
- **Settings are read once.** CARLOS reads the portal settings the first time the portal is used
  after a start, and keeps them. Every pin change needs a restart, and a restart alone proves
  nothing: after each one, open a patient's **Patient portal** page to make CARLOS load the new
  settings and show any error.
- **A wrong pin fails before any request.** The pin is checked during the TLS handshake, so CARLOS
  sends nothing, not even the service token, to an endpoint whose key does not match.
- **The pin is not the staff-assertion key.** The TLS pin identifies the portal to CARLOS. The
  Ed25519 staff-assertion key identifies CARLOS to the portal. They are managed separately.
- **The portal can be switched off.** Switching it off stops every portal call, so it is the safe
  state while a pin problem is fixed. To switch it off: set `patient_portal.enabled=false` (added in
  #3934; on a build without it, remove every `patient_portal.*` setting instead), restart CARLOS,
  and confirm the **Patient portal** entry is gone from a patient's record. To switch it on again:
  `patient_portal.enabled=true` (or restore the removed settings, except
  `patient_portal.certificate.pins`, which keeps the value you set since), restart, and open the
  page.

## 1. Choose the addresses

CARLOS uses two addresses:
- `patient_portal.public_base_url`: what patients open. Invitation emails link to it.
- `patient_portal.base_url`: where CARLOS calls `/internal/carlos/`.

**Recommended: a dedicated subdomain, such as `portal.<clinic-domain>`, used for both.**
- Patients see the clinic's own name.
- There is one certificate, one key and one pin.
- It is the layout the portal's reference nginx configuration and the staging checklist assume.

On that layout, nginx's IP allow-list for `/internal/carlos/` is what keeps the internal API
private, so it must list exactly the CARLOS server addresses.

**Alternative: a separate internal hostname on a private network.** The internal API is then
unreachable from the internet. It needs a second certificate, usually from a private CA added to
the CARLOS truststore, and a VPN or private link between the hosts.

**Not recommended: a path under the clinic's existing website.** Whoever hosts the website then
ends the portal's HTTPS, on their renewal schedule, usually with a new key each time.

> Record: patient address, CARLOS address, whether they exist yet, staging addresses.

## 2. Decide where HTTPS ends

The pin must match the key of whatever server answers CARLOS's connection. That choice decides
who can change the key, and so who can break the portal without meaning to.

| Where HTTPS ends | Verdict |
|---|---|
| nginx on the portal host, with a key the clinic controls | **Recommended.** It is the portal's documented deployment, and the key changes only when the clinic changes it. |
| Caddy on the portal host | Not with its automatic certificates, which get a new key at every renewal. Workable only if Caddy serves a key and certificate the clinic supplies (its `tls <cert> <key>` form). The reference nginx rules (IP restriction, header stripping) must also be translated. |
| A CDN such as Cloudflare in front | **Not for CARLOS's route.** The CDN rotates its edge key on its own schedule and sees decrypted patient data. Patients may use a CDN only if CARLOS reaches the portal directly or privately (section 1, internal hostname). |
| A cloud load balancer | Only with a certificate whose key the clinic generated and uploaded; provider-managed certificates rotate their keys automatically. The balancer also sees decrypted patient data, and nginx then sees the balancer's address rather than CARLOS's, so the `/internal/carlos/` allow-list must be rethought rather than opened to the balancer. |

> Record: where the portal runs, and what ends HTTPS for CARLOS's connection.

## 3. Ownership

A pin couples two systems. Whoever changes the portal's key must change CARLOS's pins in the same
window, or portal calls stop. The usual cause of an outage is two people each assuming the other
will do it.

**Recommended: one owner for both the certificate and the CARLOS pins, with a named backup.**

If a hosting vendor owns the certificate, the contract must require notice before any key change,
and delivery of the new certificate or public key through an authenticated channel.

> Record: an owner and a backup for DNS, the TLS key, the CARLOS portal settings, the
> staff-assertion keys, and portal incidents.

## 4. Set up the key and the first pin

The first pin is where trust begins. A pin read off the network pins whoever happened to answer,
which could be an attacker. **Never adopt a pin from a live connection, a browser, an online
checker or an error message.** (Comparing a live connection against pins you already trust is
safe, and section 6 uses it; adopting one is not.)

This runbook supports one setup: a key the clinic generates itself, in a fixed file layout, with
certificates issued from a certificate request (CSR). The pin then comes from a key you created,
with no network step, and renewal, rotation and compromise all work by renaming files. A portal
already serving a key some other tool generated, such as certbot's own, moves to this setup with
[Moving an existing portal to this setup](#moving-an-existing-portal-to-this-setup).

### File layout

One directory on the portal host, readable only by root, with fixed names:

| File | What it is |
|---|---|
| `/etc/portal-tls/live.key` | The key nginx serves (`ssl_certificate_key`) |
| `/etc/portal-tls/live.csr` | The certificate request for `live.key`; renewal reissues from it |
| `/etc/portal-tls/fullchain.pem` | The certificate and intermediates nginx serves (`ssl_certificate`) |
| `/etc/portal-tls/cert.pem`, `chain.pem` | The certificate alone, and the intermediates alone |
| `/etc/portal-tls/approved-pins.txt` | The pins configured in CARLOS, one per line |

nginx must serve the **full chain**: Java does not fetch missing intermediates, so serving only
`cert.pem` fails CARLOS's validation.

### Where certificates come from

- **An ACME certificate authority through certbot** (such as Let's Encrypt): register an account
  once (`certbot register`), and use your usual validation options (for example
  `--webroot -w /var/www/certbot`) wherever the commands below say `<validation>`. Renewal is then
  automatic (section 5).
- **A certificate authority that takes an uploaded CSR**: wherever the commands below run certbot,
  upload the named `.csr` file instead and save what the CA returns under the file names that
  certbot command gives (certificate, intermediates, and both together). There is no automatic
  renewal: the renewal job fails daily from 30 days before expiry, which is the reminder. Renew by
  uploading `live.csr`, saving the result as `new-cert.pem`, `new-chain.pem` and
  `new-fullchain.pem` in `/etc/portal-tls`, and running the renewal job, which checks and installs
  them (see its `new-` handling below).

### First setup

```sh
install -d -m 700 /etc/portal-tls && cd /etc/portal-tls
if [ -e live.key ]; then echo "live.key exists; not overwriting it" >&2; else
  # The key (ECDSA P-256; RSA 2048 or larger also works)
  (umask 077; openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out live.key)
  # A certificate request for it
  openssl req -new -key live.key \
    -subj "/CN=portal.example.ca" -addext "subjectAltName=DNS:portal.example.ca" -out live.csr
  # Its pin
  printf 'sha256/'; openssl pkey -in live.key -pubout -outform der \
    | openssl dgst -sha256 -binary | base64
fi
```

Then:

1. A second person computes the pin independently from `live.key` with the last command, and gets
   the same value.
2. Put the pin in `approved-pins.txt`, and in `patient_portal.certificate.pins` in the deployment's
   override properties (`over_ride_config.properties`), not in the committed `carlos.properties`.
   The `sha256/` prefix is part of the value. Add the standby pin (section 5) to both as well; pins
   in the setting are comma-separated.
3. Issue the certificate. certbot writes exactly the paths it is given, refuses to overwrite a file,
   and installs nothing:

   ```sh
   certbot certonly --csr live.csr <validation> \
     --cert-path cert.pem --chain-path chain.pem --fullchain-path fullchain.pem
   ```

4. Point nginx at the layout, `ssl_certificate /etc/portal-tls/fullchain.pem;` and
   `ssl_certificate_key /etc/portal-tls/live.key;`, then `nginx -t && systemctl reload nginx`.
5. Install the renewal job (section 5).
6. Set `patient_portal.enabled=true`, restart CARLOS and open a patient's **Patient portal** page;
   it loads without a portal error. The CARLOS log shows
   `patient portal transport: certificate pinning active (2 pin(s))`.

> Record: who computed the pin, who checked it, and where the approved pins are kept. Pins are
> public-key hashes and are not secret; private keys never go in the record.

### Moving an existing portal to this setup

For a portal already serving a key from another tool. The pin for the current key must come from
the certificate file on the server, copied over SSH or another authenticated channel (never from a
connection):

```sh
printf 'sha256/'; openssl x509 -in verified-portal-leaf.pem -pubkey -noout \
  | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
```

For certbot that file is `/etc/letsencrypt/live/<name>/cert.pem`; `nginx -T | grep ssl_certificate`
shows which file nginx serves. If that pin is not already in CARLOS, the portal is not yet in use:
simply do the first setup above.

Otherwise:

1. Do the first setup above, steps up to 3, keeping the current pin in CARLOS and in
   `approved-pins.txt` alongside the new and standby pins. Restart CARLOS and open the page; it
   must load.
2. Step 4 of the first setup: nginx now serves the new key. Open the page; it must load. If it
   does not, point `ssl_certificate` and `ssl_certificate_key` back at the old files, reload nginx,
   and stop.
3. Install the renewal job (section 5).
4. Remove the old key's pin from the setting and from `approved-pins.txt`, restart CARLOS and open
   the page.
5. Retire the old tool's certificate so nothing keeps renewing it: for certbot, first check with
   `nginx -T | grep letsencrypt` that nothing else still uses it, then
   `certbot delete --cert-name <name>`, which also deletes its key. If that key was compromised,
   revoke first: `certbot revoke --cert-path /etc/letsencrypt/live/<name>/cert.pem --key-path
   /etc/letsencrypt/live/<name>/privkey.pem --reason keycompromise`.

## 5. Renewal, rotation and compromise

Renewal and rotation are different things:
- **Renewing** with the same key keeps the pin valid, and CARLOS needs no change.
- **Rotating** to a new key needs new pins.

Certificate lifetimes keep shrinking (every public CA's maximum falls in steps to 47 days by 2029),
so renewal must be automatic, frequent enough to survive a failed run, and must never change the
key.

### The renewal job

`certbot renew` does not renew certificates issued from a CSR. Save this as
`/usr/local/sbin/portal-tls-renew`, set `HOST`, `CONNECT` (an address nginx listens on for the
portal) and `<validation>`, and run it daily from a `portal-tls-renew.timer` systemd timer (or
cron), with failures reported to the owner.

What it does:
- It takes a lock, so it never runs alongside itself or a manual change (see below).
- It refuses to go on unless `live.key` is an approved key and `live.csr` is for that key.
- It reissues when fewer than 30 days remain, and installs a new certificate (from certbot, or
  `new-*.pem` saved by hand) only if it is for `live.key`.
- It reloads nginx whenever nginx is not serving `fullchain.pem`, then checks again and fails if
  it still is not, so a failed reload is reported every day rather than forgotten.

```sh
#!/bin/sh
set -eu
HOST=portal.example.ca
CONNECT=127.0.0.1:443
cd /etc/portal-tls
exec 9>.lock; flock -w 600 9 || { echo "portal-tls-renew: another run holds the lock" >&2; exit 1; }
pin_of() { openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64; }
fail() { echo "portal-tls-renew: $*" >&2; exit 1; }
served() { openssl s_client -connect "$CONNECT" -servername "$HOST" </dev/null 2>/dev/null \
  | openssl x509 -noout -fingerprint -sha256 2>/dev/null || true; }

key_pin=$(openssl pkey -in live.key -pubout | pin_of)
grep -qxF "sha256/$key_pin" approved-pins.txt || fail "live.key is not an approved key"
[ "$(openssl req -in live.csr -pubkey -noout | pin_of)" = "$key_pin" ] || fail "live.csr is not for live.key"

if [ ! -e new-fullchain.pem ] && ! openssl x509 -in fullchain.pem -noout -checkend $((30 * 86400)) >/dev/null 2>&1; then
  rm -f new-cert.pem new-chain.pem
  certbot certonly --non-interactive --csr live.csr <validation> \
    --cert-path new-cert.pem --chain-path new-chain.pem --fullchain-path new-fullchain.pem
fi
if [ -e new-fullchain.pem ]; then
  [ "$(openssl x509 -in new-fullchain.pem -pubkey -noout | pin_of)" = "$key_pin" ] \
    || fail "new-fullchain.pem is not for live.key"
  mv new-fullchain.pem fullchain.pem
  mv new-cert.pem cert.pem 2>/dev/null || true; mv new-chain.pem chain.pem 2>/dev/null || true
fi

want=$(openssl x509 -in fullchain.pem -noout -fingerprint -sha256)
[ "$(served)" = "$want" ] && exit 0
nginx -t 9>&- && systemctl reload nginx 9>&-   # the reload must not inherit the lock
sleep 5
[ "$(served)" = "$want" ] || fail "nginx does not serve fullchain.pem after a reload (check CONNECT and HOST)"
```

**Stopping the job** before any manual change in `/etc/portal-tls`: run
`systemctl stop portal-tls-renew.timer` (or comment out the cron line), then
`flock /etc/portal-tls/.lock true`, which returns once any run in progress has finished.
**Starting it again**: `systemctl start portal-tls-renew.timer` (or restore the cron line).

### Keep a standby key pinned

Generate it on an offline machine, never on the portal host:

```sh
if [ -e standby.key ]; then echo "standby.key exists; not overwriting it" >&2; else
  (umask 077; openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out standby.key)
  openssl req -new -key standby.key \
    -subj "/CN=portal.example.ca" -addext "subjectAltName=DNS:portal.example.ca" -out standby.csr
  printf 'sha256/'; openssl pkey -in standby.key -pubout -outform der \
    | openssl dgst -sha256 -binary | base64
fi
```

Add its pin to CARLOS and `approved-pins.txt`, with a second-person check. Keep `standby.key`
offline, protected like any private key; `standby.csr` is public and may be kept with it.

If the standby key is lost or may be compromised, remove its pin from the setting and from
`approved-pins.txt`, restart CARLOS, open the page, and generate a new standby.

### Rotate on a schedule

Rotate the live key on a fixed interval, for example yearly. CARLOS must always hold a pin for the
key nginx is serving. **The old pin here must never be a compromised key**; for a compromise,
follow the next section instead.

1. Stop the renewal job. In `/etc/portal-tls`, generate `next.key` and `next.csr` with the first
   setup commands, replacing `live` with `next`, and have the pin checked by a second person.
2. Add the new pin to `patient_portal.certificate.pins` (now live, next and standby) and to
   `approved-pins.txt`. Restart CARLOS and open a patient's **Patient portal** page; it must load
   without a portal error. If it does not, remove the new pin from the setting and from
   `approved-pins.txt`, restart, start the renewal job, and stop here.
3. Issue a certificate for `next.csr` and make it the live set, keeping the old set until the end.
   The block stops at the first error, and it moves nothing until the new certificate is issued
   and checked:

   ```sh
   ( set -eu; cd /etc/portal-tls
     [ ! -e previous-live.key ] || { echo "previous-* exist: finish or roll back the last change" >&2; exit 1; }
     pin_of() { openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64; }
     rm -f next-cert.pem next-chain.pem next-fullchain.pem
     certbot certonly --csr next.csr <validation> \
       --cert-path next-cert.pem --chain-path next-chain.pem --fullchain-path next-fullchain.pem
     [ "$(openssl x509 -in next-fullchain.pem -pubkey -noout | pin_of)" = \
       "$(openssl pkey -in next.key -pubout | pin_of)" ] || { echo "certificate is not for next.key" >&2; exit 1; }
     for f in live.key live.csr cert.pem chain.pem fullchain.pem; do mv "$f" "previous-$f"; done
     mv next.key live.key; mv next.csr live.csr
     mv next-cert.pem cert.pem; mv next-chain.pem chain.pem; mv next-fullchain.pem fullchain.pem
     nginx -t && systemctl reload nginx )
   ```

   If it stops before the swap (`previous-live.key` does not exist), nothing has moved: remove the
   new pin from the setting and from `approved-pins.txt`, restart CARLOS, start the renewal job,
   and stop. If it stops after the swap, roll back as in step 4.
4. Open the **Patient portal** page again; it must load. If it does not, or step 3 stopped after the
   swap, roll back and stop:

   ```sh
   ( set -eu; cd /etc/portal-tls
     [ -e previous-live.key ] || { echo "no previous-* files: nothing to roll back" >&2; exit 1; }
     for f in live.key live.csr; do [ ! -e "$f" ] || mv "$f" "next.${f#live.}"; done
     for f in cert.pem chain.pem fullchain.pem; do [ ! -e "$f" ] || mv "$f" "next-$f"; done
     for f in live.key live.csr cert.pem chain.pem fullchain.pem; do mv "previous-$f" "$f"; done
     nginx -t && systemctl reload nginx )
   ```

   Then open the page; it must load. Remove the new pin from the setting and from
   `approved-pins.txt` (or leave it to retry later), restart CARLOS, and start the renewal job.
5. Remove the old pin from the setting and from `approved-pins.txt`. Restart CARLOS and open the
   page once more. Delete the `previous-` files and start the renewal job.

An aborted rotation can leave `next.key` behind; the first setup commands then refuse to overwrite
it and print no pin. Compute its pin with the last line of those commands, or delete it and start
again.

### When the live key is compromised or lost

A stolen key is still trusted by the JVM through its unexpired certificate; Java does not check
revocation by default. Removing its pin is the only thing that stops CARLOS trusting it, so do it at
once. If the steps below cannot be finished quickly, switch the portal off (see
[What CARLOS enforces](#what-carlos-enforces)) rather than leave the stolen key pinned.

**If the portal host itself may be compromised**, switch the portal off and rebuild the host.
Never bring the standby key onto a host that may be in an attacker's hands. On the rebuilt host,
do the first setup with `standby.key` and `standby.csr` copied in as `live.key` and `live.csr` (the
guard then skips key generation), set the pins to that key's pin **only**, and switch the portal on.
Then revoke the old certificate and create a new standby as below.

**If the key is lost but not compromised** (the host still serves it from its files), there is no
need to switch off: do a scheduled rotation.

**With a standby key:**

1. Stop the renewal job. Copy `standby.key` and `standby.csr` to `/etc/portal-tls` as `next.key`
   and `next.csr`, then issue and install them with the rotation step 3 commands. CARLOS already
   trusts this key, so nothing breaks.
2. Set `patient_portal.certificate.pins` and `approved-pins.txt` to the new live pin **only**.
   Restart CARLOS and open the **Patient portal** page; it must load. If step 1 or this check
   fails, switch the portal off and leave the renewal job stopped until it is fixed.
3. If the key was compromised, revoke its certificate, proving it with the key itself so the CA can
   block the key:
   `certbot revoke --cert-path previous-cert.pem --key-path previous-live.key --reason keycompromise`,
   or through your certificate authority.
4. Delete the `previous-` files and start the renewal job.
5. Generate a new standby key and add its pin, as above.
6. Record what happened in the deployment record's change log.

**Without a standby key:**

1. Switch the portal off.
2. Stop the renewal job. Generate `next.key` and `next.csr` (first setup commands with `next`),
   have a second person check the pin, and issue and install them with the rotation step 3
   commands.
3. Set `patient_portal.certificate.pins` and `approved-pins.txt` to the new pin **only**. Never keep
   the compromised pin alongside it.
4. Switch the portal on, restart CARLOS and open the page; it must load.
5. Revoke, delete and restart the timer as in steps 3 and 4 above, then create a standby key.
6. Record what happened in the deployment record's change log.

> Record: the certificate authority and renewal method, the renewal job, the rotation interval
> and next date, and where the standby key is kept.

## 6. Roll out, verify and recover

### Rollout

1. **Staging first.** Run [`patient-portal-staging-checklist.md`](patient-portal-staging-checklist.md)
   with staging keys and test patients, including a rehearsed rotation. Never reuse staging keys,
   tokens or pins in production.
2. **Production.** Generate fresh keys and pins with sections 4 and 5, configure CARLOS, restart,
   and run the production verification below.

### Production verification

Do these on the production installation, in a maintenance window (the wrong-pin check makes the
portal unavailable for two restarts), and record the date and names. Green repository CI and a
passed staging run are not evidence that a live installation is protected.

- [ ] **The right portal connects.** Open a test patient's **Patient portal** page in CARLOS; it
      loads without a portal error.
- [ ] **A wrong pin fails before any request.** Generate a throwaway key, and replace the pins with
      its correctly formed pin (a malformed pin tests only the settings check). Restart CARLOS and
      open the page. It reports the portal as unavailable; the CARLOS log shows
      `portal transport failed: TLS handshake`, not `patient portal configuration is invalid`; and
      the portal's nginx access log shows **no** request from CARLOS (the aborted handshake appears,
      if at all, only in nginx's error log). Restore the pins, restart, and check the page loads.
- [ ] **The standby pin is configured.** The log shows `certificate pinning active (2 pin(s))`, and
      the second pin matches the offline standby key.
- [ ] **Renewal works.** The renewal job has run, and an external expiry monitor watches the
      certificate nginx serves. After the first real renewal, the page still loads.
- [ ] **Rotation leaves no gap.** A rotation, rehearsed on staging or done at the first scheduled
      date, completes with the page loading after steps 2, 4 and 5.

Until each item is done, the deployment record says *not yet verified in production*.

### When the portal fails over TLS

Search the CARLOS log for `portal transport failed: TLS handshake`. It appears as the cause under
`patient portal panel section invites could not be read: kind=TRANSPORT_FAILURE` (or section
`account`, from the **Patient portal** page) or `patient portal call failed: kind=TRANSPORT_FAILURE`
(from an invite or account action). The same line covers a pin mismatch, an expired certificate, an
untrusted issuer, a missing intermediate and a hostname mismatch. (A malformed pin logs
`patient portal configuration is invalid; check deployment settings` instead.) Portal features stop;
the rest of CARLOS works.

**Never** switch pinning off, and never adopt a pin from a live connection or an error. If the
portal must stay down while you investigate, switch it off.

To find the cause, compute the pin of the certificate nginx serves, from the file on the portal host
(`nginx -T | grep ssl_certificate` shows which), and compare it with the configured pins:

- **It is not a configured pin: the key on the server changed.** If someone changed it on purpose,
  verify the new key through the section 4 channel, add its pin, restart CARLOS and check the page.
  Otherwise, with the renewal job stopped, restore the files: the rotation step 4 rollback if
  `previous-live.key` exists, or a backup; then start the job again and find out who changed it; if nobody on the clinic side did, treat the host as possibly compromised (section 5).
- **It is a configured pin: the server's key is right, so check the rest.**
  - The certificate is in date (`openssl x509 -in <file> -noout -enddate`), and nginx serves the full
    chain for the hostname and port in `patient_portal.base_url`.
  - The CARLOS host's clock is correct.
  - The CARLOS JVM trusts the issuer. A private CA, as on an internal hostname, must be in the JVM
    truststore (`keytool -list -cacerts`).
  - nginx offers TLS 1.2 or 1.3.
  - What CARLOS is actually served. From the CARLOS host:

    ```sh
    printf 'sha256/'; openssl s_client -connect portal.example.ca:443 -servername portal.example.ca \
      </dev/null 2>/dev/null | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der \
      | openssl dgst -sha256 -binary | base64
    ```

    This only compares; `s_client` does not validate anything. If the connection failed, the output
    is `sha256/47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=`, the hash of nothing; fix connectivity
    first. On the portal host, the same command with `-connect 127.0.0.1:443` (keeping
    `-servername`) shows whether nginx itself serves the expected key.

    If CARLOS is shown a key other than the server's, something between them is presenting it: a
    TLS-inspecting proxy, a CDN, a changed DNS record or a load balancer. Treat it as a possible
    impersonation. Switch the portal off and investigate; never pin what it presents.

> Record: who provisions pins, restarts CARLOS, verifies, and monitors; the target date; whether
> staging came first.
