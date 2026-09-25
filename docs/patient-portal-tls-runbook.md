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
- **The portal can be switched off.** With `patient_portal.enabled=false` (added in #3934), CARLOS
  makes no portal call at all, so the portal can be taken offline while a pin problem is fixed.
  Before #3934, remove every `patient_portal.*` setting instead, and restart.

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

## 4. Set the first pin

The first pin is where trust begins. A pin read off the network pins whoever happened to answer,
which could be an attacker. **Never adopt a pin from a live connection, a browser, an online
checker or an error message.** (Comparing a live connection against pins you already trust is
safe, and section 6 uses it; adopting one is not.)

### File layout

Everything below uses one directory on the portal host, readable only by root, with fixed names.
The renewal job always renews whatever `live.csr` is, so moving to a new key is a matter of
renaming files, never of editing the job.

| File | What it is |
|---|---|
| `/etc/portal-tls/live.key` | The key nginx serves (`ssl_certificate_key`) |
| `/etc/portal-tls/live.csr` | The certificate request for `live.key`; the renewal job reissues from it |
| `/etc/portal-tls/fullchain.pem` | The certificate and intermediates nginx serves (`ssl_certificate`) |
| `/etc/portal-tls/cert.pem`, `chain.pem` | The certificate alone, and the intermediates alone |
| `/etc/portal-tls/approved-pins.txt` | The pins configured in CARLOS, one per line |

Serve the **full chain**: Java does not fetch missing intermediates, so an nginx serving only
`cert.pem` fails CARLOS's validation.

### Recommended: generate the key yourself

The pin then comes from a key you created, with no network step, and can be in CARLOS before the
portal goes live. Rotation (section 5) needs this method.

```sh
cd /etc/portal-tls
if [ -e live.key ]; then echo "live.key exists; not overwriting it" >&2; else
  # 1. The key (ECDSA P-256; RSA 2048 or larger also works)
  (umask 077; openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out live.key)
  # 2. A certificate request for it
  openssl req -new -key live.key \
    -subj "/CN=portal.example.ca" -addext "subjectAltName=DNS:portal.example.ca" -out live.csr
  # 3. Its pin
  printf 'sha256/'; openssl pkey -in live.key -pubout -outform der \
    | openssl dgst -sha256 -binary | base64
fi
```

Have a certificate issued for `live.csr`, through your certificate authority's CSR upload or with
certbot. certbot writes exactly the paths it is given, refuses to overwrite a file, and installs
nothing:

```sh
certbot certonly --csr live.csr <your validation options, e.g. --webroot -w /var/www/certbot> \
  --cert-path cert.pem --chain-path chain.pem --fullchain-path fullchain.pem
```

Then set `ssl_certificate /etc/portal-tls/fullchain.pem;` and
`ssl_certificate_key /etc/portal-tls/live.key;` in nginx, run `nginx -t` and reload.

### Acceptable for a first setup: compute it from the certificate file on the server

When the key already exists, for example one certbot generated, copy the certificate file nginx
serves (`nginx -T | grep ssl_certificate` shows which) over SSH or another authenticated
administration channel, then:

```sh
printf 'sha256/'; openssl x509 -in verified-portal-leaf.pem -pubkey -noout \
  | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
```

For a certbot-managed certificate, use `/etc/letsencrypt/live/<name>/cert.pem`. Its `fullchain.pem`
gives the same result, because `openssl x509` reads the first certificate, the leaf.

certbot cannot rotate its own key safely: it changes the key only at renewal, and serves the new
one before any pin exists. Such a clinic moves to the file layout above at its first rotation or
compromise (section 5). A standby key works either way.

### Either way

1. A second person computes the pin independently, from the same key or file, and gets the same
   value.
2. The pin goes into `patient_portal.certificate.pins` in the deployment's override properties
   (`over_ride_config.properties`), not in the committed `carlos.properties`, and into
   `approved-pins.txt`. The `sha256/` prefix is part of the value.
3. The standby pin from section 5 goes in both places too; pins in the setting are comma-separated.
4. Restart CARLOS and open a patient's **Patient portal** page; it loads without a portal error.
   The CARLOS log shows `patient portal transport: certificate pinning active (2 pin(s))`.

> Record: the trusted channel, who computed the pin, who checked it, and where the approved pins
> are kept. Pins are public-key hashes and are not secret; private keys never go in the record.

## 5. Renewal, rotation and compromise

Renewal and rotation are different things:
- **Renewing** with the same key keeps the pin valid, and CARLOS needs no change.
- **Rotating** to a new key needs new pins.

Certificate lifetimes keep shrinking (every public CA's maximum falls in steps to 47 days by 2029),
so renewal must be automatic, frequent enough to survive a failed run, and must never change the
key by accident.

### Renew with the same key

**Self-generated key (recommended).** `certbot renew` does not renew certificates issued from a
CSR, so run this script daily from a systemd timer or cron, and have failures reported to the
owner. It reissues only when fewer than 30 days remain, refuses to ask the CA for a certificate
unless `live.csr` carries an approved pin, and refuses to install a certificate whose pin is not
approved. A failed run leaves the current certificate in place.

```sh
#!/bin/sh
set -eu
cd /etc/portal-tls
pin_of() { openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64; }
approved() { grep -qxF "sha256/$1" approved-pins.txt; }

openssl x509 -in cert.pem -noout -checkend $((30 * 86400)) >/dev/null && exit 0
csr_pin=$(openssl req -in live.csr -pubkey -noout | pin_of)
approved "$csr_pin" || { echo "live.csr is not an approved key: sha256/$csr_pin" >&2; exit 1; }

rm -f new-cert.pem new-chain.pem new-fullchain.pem
certbot certonly --non-interactive --csr live.csr <your validation options> \
  --cert-path new-cert.pem --chain-path new-chain.pem --fullchain-path new-fullchain.pem
cert_pin=$(openssl x509 -in new-cert.pem -pubkey -noout | pin_of)
approved "$cert_pin" || { echo "new certificate is not pinned: sha256/$cert_pin" >&2; exit 1; }

mv new-cert.pem cert.pem && mv new-chain.pem chain.pem && mv new-fullchain.pem fullchain.pem
nginx -t && systemctl reload nginx
```

**certbot-managed key (first setup only).** Make every renewal keep the key:
- certbot 2.3 or later: `certbot reconfigure --cert-name <name> --reuse-key`.
- Older certbot (such as Ubuntu 22.04's or Debian 12's packages): add `reuse_key = True` under
  `[renewalparams]` in `/etc/letsencrypt/renewal/<name>.conf`.

Then run `certbot renew --dry-run`. Never pass `--new-key` or change `--key-type`: both create a new
key. After each renewal, compare the pin of `/etc/letsencrypt/live/<name>/cert.pem` with the
configured pins.

### Keep a standby key pinned

Every clinic should have one, however its live key is managed. On an offline machine, never on the
portal host:

```sh
if [ -e standby.key ]; then echo "standby.key exists; not overwriting it" >&2; else
  (umask 077; openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out standby.key)
  openssl req -new -key standby.key \
    -subj "/CN=portal.example.ca" -addext "subjectAltName=DNS:portal.example.ca" -out standby.csr
  printf 'sha256/'; openssl pkey -in standby.key -pubout -outform der \
    | openssl dgst -sha256 -binary | base64
fi
```

Add the pin to CARLOS and `approved-pins.txt` (section 4, "Either way"). Keep `standby.key` offline,
protected like any private key; `standby.csr` is public and may be kept with it.

### Rotate on a schedule

Rotate the live key on a fixed interval, for example yearly. CARLOS must always hold a pin for the
key nginx is serving. **The old pin here must never be a compromised key**; for a compromise, follow
the next section instead.

1. Stop the renewal timer. In `/etc/portal-tls`, generate `next.key` and `next.csr` with the
   section 4 commands, replacing `live` with `next`, and verify the pin as in section 4.
2. Add the new pin to `patient_portal.certificate.pins` (now live, next and standby) and to
   `approved-pins.txt`. Restart CARLOS and open a patient's **Patient portal** page; it must load
   without a portal error. Stop here if it does not.
3. Issue a certificate for `next.csr`:
   `certbot certonly --csr next.csr <your validation options> --cert-path next-cert.pem --chain-path next-chain.pem --fullchain-path next-fullchain.pem`.
   Then make it the live set, keeping the old one until the end:

   ```sh
   for f in live.key live.csr cert.pem chain.pem fullchain.pem; do mv "$f" "previous-$f"; done
   mv next.key live.key && mv next.csr live.csr
   mv next-cert.pem cert.pem && mv next-chain.pem chain.pem && mv next-fullchain.pem fullchain.pem
   nginx -t && systemctl reload nginx
   ```

   If `nginx -t` fails, move the `previous-` files back and reload.
4. Open the **Patient portal** page again; it must load.
5. Remove the old pin from the setting and from `approved-pins.txt`. Restart CARLOS and open the
   page once more. Delete the `previous-` files and start the renewal timer again.

A clinic that started with a certbot-managed key does its first rotation this way: step 1 creates
the `/etc/portal-tls` layout, and nginx moves to it in step 3.

### When a key is compromised or lost

A stolen key is still trusted by the JVM through its unexpired certificate; Java does not check
revocation by default. Removing its pin is the only thing that stops CARLOS trusting it, so do it at
once. If step 1 cannot be finished quickly, switch the portal off
(`patient_portal.enabled=false`, restart) rather than leave the stolen key pinned.

**If the portal host itself may be compromised**, switch the portal off and rebuild the host first.
Never bring the standby key onto a host that may be in an attacker's hands.

**With a standby key:**

1. Stop the renewal timer. Copy `standby.key` and `standby.csr` to `/etc/portal-tls` as `next.key`
   and `next.csr`, then issue and install them exactly as in rotation step 3. CARLOS already trusts
   this key, so nothing breaks.
2. Set `patient_portal.certificate.pins` and `approved-pins.txt` to the new live pin **only**.
   Restart CARLOS and open the **Patient portal** page; it must load.
3. If the key was compromised, revoke its certificate, proving it with the key itself so the CA can
   block the key:
   `certbot revoke --cert-path previous-cert.pem --key-path previous-live.key --reason keycompromise`,
   or through your certificate authority.
4. Destroy the `previous-` files and start the renewal timer again.
5. Generate a new standby key and add its pin, as above.
6. Record what happened in the deployment record's change log.

**Without a standby key:**

1. Switch the portal off: `patient_portal.enabled=false`, restart CARLOS.
2. Stop the renewal timer. Generate `next.key` and `next.csr` (section 4 commands with those names),
   have a second person verify the pin, and issue and install them as in rotation step 3.
3. Set `patient_portal.certificate.pins` and `approved-pins.txt` to the new pin **only**. Never keep
   the compromised pin alongside it.
4. Switch the portal on (`patient_portal.enabled=true`), restart CARLOS and open the page; it must
   load.
5. Revoke, destroy and restart the timer as in steps 3 and 4 above, then create a standby key.
6. Record what happened in the deployment record's change log.

> Record: the renewal method and schedule, the rotation interval and next date, and where the
> standby key is kept.

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
- [ ] **A renewal keeps the pin.** The first scheduled renewal completes and its pin checks pass.
- [ ] **Rotation leaves no gap.** A rotation, rehearsed on staging or done at the first scheduled
      date, completes with the page loading after steps 2, 4 and 5.

Until each item is done, the deployment record says *not yet verified in production*.

### When the portal fails over TLS

Search the CARLOS log for `portal transport failed: TLS handshake`. It appears as the cause under
`patient portal panel section invites could not be read: kind=TRANSPORT_FAILURE` (from the
**Patient portal** page) or `patient portal call failed: kind=TRANSPORT_FAILURE` (from an invite or
account action). The same line covers a pin mismatch, an expired certificate, an untrusted issuer, a
missing intermediate and a hostname mismatch. (A malformed pin logs
`patient portal configuration is invalid; check deployment settings` instead.) Portal features stop;
the rest of CARLOS works.

**Never** switch pinning off, and never adopt a pin from a live connection or an error. If the
portal must stay down while you investigate, switch it off (`patient_portal.enabled=false`,
restart).

To find the cause, compute the pin of the certificate nginx serves, from the file on the portal host
(`nginx -T | grep ssl_certificate` shows which; section 4 has the command), and compare it with the
configured pins:

- **It is not a configured pin: the key changed on the server.**
  - If it was changed deliberately, verify the new key through the section 4 channel, add its pin,
    restart CARLOS and check the page.
  - If it changed by accident, typically a certbot renewal without key reuse, the quick fix is to
    serve the previous key and certificate again (a backup, or certbot's
    `/etc/letsencrypt/archive/<name>/`, the files numbered one lower than the current ones). That is
    temporary: the restored certificate expires soon, and the next renewal will not use the
    restored key. Then either pin the current key, verified from the server file with a
    second-person check, and turn on key reuse; or move to the section 4 file layout.
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
    first. Running the same command on the portal host against `localhost` shows whether nginx
    itself serves the expected key (it may not have been reloaded).

    If CARLOS is shown a key other than the server's, something between them is presenting it: a
    TLS-inspecting proxy, a CDN, a changed DNS record or a load balancer. Treat it as a possible
    impersonation. Switch the portal off and investigate; never pin what it presents.

> Record: who provisions pins, restarts CARLOS, verifies, and monitors; the target date; whether
> staging came first.
