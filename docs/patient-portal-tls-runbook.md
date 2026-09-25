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

### Recommended: generate the key yourself

The pin then comes from a key you created, with no network step, and can be in CARLOS before the
portal goes live. This is also the only method that supports the standby key and scheduled
rotation in section 5. On the portal host, in a directory only root can read, for example
`/etc/portal-tls`:

```sh
# 1. The TLS key (ECDSA P-256; RSA 2048 or larger also works)
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out portal-tls.key
chmod 600 portal-tls.key

# 2. A certificate request for that key
openssl req -new -key portal-tls.key \
  -subj "/CN=portal.example.ca" -addext "subjectAltName=DNS:portal.example.ca" \
  -out portal-tls.csr

# 3. The pin
printf 'sha256/'; openssl pkey -in portal-tls.key -pubout -outform der \
  | openssl dgst -sha256 -binary | base64
```

Have a certificate issued for that request, through your certificate authority's CSR upload or
with certbot. certbot writes exactly the paths you give it and installs nothing:

```sh
certbot certonly --csr portal-tls.csr <your validation options, e.g. --webroot -w /var/www/certbot> \
  --cert-path cert.pem --chain-path chain.pem --fullchain-path fullchain.pem
```

Point nginx at the key and the **full chain**: `ssl_certificate /etc/portal-tls/fullchain.pem;` and
`ssl_certificate_key /etc/portal-tls/portal-tls.key;`. Serving only `cert.pem` fails Java's
validation.

### Acceptable for a first setup: compute it from the certificate file on the server

When the key already exists, for example one certbot generated, copy the certificate file nginx
serves over SSH or another authenticated administration channel, then:

```sh
printf 'sha256/'; openssl x509 -in verified-portal-leaf.pem -pubkey -noout \
  | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
```

For a certbot-managed certificate, use `/etc/letsencrypt/live/<name>/cert.pem`. The
`fullchain.pem` nginx serves gives the same result, because `openssl x509` reads its first
certificate, the leaf.

A certbot-managed key cannot be rotated safely: certbot only changes it at renewal, and then serves
it before any pin exists. Plan to move to a self-generated key at the first rotation (section 5).

### Either way

1. A second person computes the pin independently, from the same key or file, and gets the same
   value.
2. The pin goes into `patient_portal.certificate.pins` in the deployment's override properties
   (`over_ride_config.properties`), not in the committed `carlos.properties`. The `sha256/` prefix
   is part of the value.
3. The standby pin from section 5 goes in the same setting, comma-separated.
4. Restart CARLOS and open a patient's **Patient portal** page; it loads without a portal error.

> Record: the trusted channel, who computed the pin, who checked it, and where the approved pins
> are kept. Pins are public-key hashes and are not secret; private keys never go in the record.

## 5. Renewal, rotation and compromise

Renewal and rotation are different things:
- **Renewing** with the same key keeps the pin valid, and CARLOS needs no change.
- **Rotating** to a new key needs new pins.

Certificate lifetimes keep shrinking (Let's Encrypt issues 90-day certificates, and the maximum for
every public CA falls in steps to 47 days by 2029), so renewal must be automatic and must never
change the key by accident.

### Renew with the same key

**Self-generated key (recommended).** `certbot renew` does not renew certificates issued from a
CSR, so schedule a reissue from the same request, for example every 60 days from a systemd timer
or cron. The script checks the new certificate's pin against the approved pins before nginx
serves it; `approved-pins.txt` holds the pins configured in CARLOS, one per line:

```sh
#!/bin/sh
set -eu
cd /etc/portal-tls
rm -f new-cert.pem new-chain.pem new-fullchain.pem   # certbot will not overwrite them
certbot certonly --non-interactive --csr portal-tls.csr <your validation options> \
  --cert-path new-cert.pem --chain-path new-chain.pem --fullchain-path new-fullchain.pem
pin="sha256/$(openssl x509 -in new-cert.pem -pubkey -noout \
  | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64)"
grep -qxF "$pin" approved-pins.txt || { echo "renewed certificate is not pinned: $pin" >&2; exit 1; }
mv new-fullchain.pem fullchain.pem && mv new-cert.pem cert.pem && mv new-chain.pem chain.pem
nginx -t && systemctl reload nginx
```

A failing run leaves the current certificate in place and exits with an error; have the timer
report failures to the owner.

**certbot-managed key (first setup only).** Make every renewal keep the key:
- certbot 2.3 or later: `certbot reconfigure --cert-name <name> --reuse-key`.
- Older certbot (such as Ubuntu 22.04's or Debian 12's packages): add `reuse_key = True` under
  `[renewalparams]` in `/etc/letsencrypt/renewal/<name>.conf`.

Then run `certbot renew --dry-run`. Never pass `--new-key` or change `--key-type`: both create a new
key. After each renewal, compare the pin of `/etc/letsencrypt/live/<name>/cert.pem` with the
configured pins.

### Keep a standby key pinned

With a self-generated live key, generate a second key and request exactly as in section 4, as
`standby.key` and `standby.csr`, and add its pin to CARLOS alongside the live one from day one.
Keep the standby key offline and protected like any private key. With both pins configured, the
portal can move to the standby key at any moment without breaking CARLOS.

### Rotate on a schedule

Rotate the live key on a fixed interval, for example yearly. CARLOS must always hold a pin for the
key nginx is serving. **The old pin in these steps must never be a compromised key**; for a
compromise, follow the next section instead.

1. Generate the new key and request, and verify the pin (section 4).
2. Set `patient_portal.certificate.pins` to the old, new and standby pins, and update
   `approved-pins.txt`. Restart CARLOS, then open a patient's **Patient portal** page; it must load
   without a portal error. Stop here if it does not.
3. Issue a certificate for the new request and switch nginx to the new key and full chain; reload
   nginx.
4. Open the **Patient portal** page again; it must load.
5. Remove the old pin from both places, restart CARLOS, and open the page once more.

A clinic that started with a certbot-managed key does its first rotation this way, moving to a
self-generated key and CSR renewals.

### When a key is compromised or lost

A stolen key is still trusted by the JVM through its unexpired certificate; Java does not check
revocation by default. Removing its pin is the only thing that stops CARLOS trusting it, so do it
at once:

1. Issue a certificate for `standby.csr`. Switch nginx to `standby.key` and its full chain; reload
   nginx. CARLOS already trusts this key, so nothing breaks.
2. Set `patient_portal.certificate.pins` to the standby pin **only**, and update
   `approved-pins.txt`. Restart CARLOS and open the **Patient portal** page; it must load.
3. If the key was compromised, revoke its certificate: `certbot revoke --cert-path <old cert.pem>
   --reason keycompromise`, or through your certificate authority. Destroy the old key.
4. The standby is now the live key. Generate a new standby and add its pin (rotation steps 1 and 2).
5. Record what happened in the deployment record's change log.

If there is no standby key, switch the portal off first (`patient_portal.enabled=false`, restart),
then generate a new key, pin it and switch back on.

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
- [ ] **A wrong pin fails before any request.** Replace the pins with a deliberately wrong one
      (the pin of any unrelated key) and restart CARLOS. The page reports a portal failure, and the
      portal's nginx access log shows **no** request from CARLOS for that attempt (the aborted
      handshake appears, if at all, only in nginx's error log). Restore the pins, restart, and check
      the page loads.
- [ ] **The standby pin is configured.** The setting holds two pins, and the second matches the
      offline standby key.
- [ ] **A renewal keeps the pin.** The first scheduled renewal completes and its pin check passes
      (section 5).
- [ ] **Rotation leaves no gap.** A rotation, rehearsed on staging or done at the first scheduled
      date, completes with the page loading after steps 2, 4 and 5.

Until each item is done, the deployment record says *not yet verified in production*.

### When the portal fails over TLS

The CARLOS log shows `patient portal call failed: kind=TRANSPORT_FAILURE` with the cause
`portal transport failed: TLS handshake`. That same line covers a pin mismatch, an expired
certificate, an untrusted issuer, a missing intermediate and a hostname mismatch. Portal features
stop; the rest of CARLOS works.

**Never** switch pinning off, and never adopt a pin from a live connection or an error. If the
portal must stay down while you investigate, set `patient_portal.enabled=false` and restart.

To find the cause, compute the pin of the certificate nginx serves, from the file on the portal
host (section 4), and compare it with the configured pins:

- **It is not a configured pin: the key changed on the server.**
  - If it was changed deliberately, verify the new key through the section 4 channel, add its pin,
    restart CARLOS and check the page.
  - If it changed by accident, typically a certbot renewal without key reuse, the quick fix is to
    serve the previous key and certificate again (a backup, or certbot's
    `/etc/letsencrypt/archive/<name>/`, the files numbered one lower than the current ones). That is
    temporary: the restored certificate expires soon, and certbot's next renewal uses its newer
    key. Then either pin the current key, verified from the server file with a second-person check,
    and turn on key reuse; or move to a self-generated key (section 4).
- **It is a configured pin: the server is fine, so check what CARLOS is shown.**
  - Check the certificate on the host is in date (`openssl x509 -in <file> -noout -enddate`) and
    that nginx serves the full chain for the hostname in `patient_portal.base_url`.
  - From the CARLOS host, compare what it is actually served with the configured pins:

    ```sh
    openssl s_client -connect portal.example.ca:443 -servername portal.example.ca </dev/null 2>/dev/null \
      | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der \
      | openssl dgst -sha256 -binary | base64
    ```

    If this differs from the server's pin, something between CARLOS and the portal is presenting
    another key: a TLS-inspecting proxy, a CDN, a changed DNS record or a load balancer. Treat it as
    a possible impersonation. Keep the portal switched off and investigate; never pin what it
    presents.

> Record: who provisions pins, restarts CARLOS, verifies, and monitors; the target date; whether
> staging came first.
