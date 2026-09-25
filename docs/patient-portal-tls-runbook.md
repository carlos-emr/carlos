# Patient portal TLS pin runbook

How a clinic chooses where its patient portal's HTTPS ends, sets the TLS public-key pin CARLOS
checks, and keeps that pin valid through renewals, key rotations and failures. Each step leaves a
line to fill in the clinic's own
[deployment record](patient-portal-deployment-record-template.md).

**Who this is for:** the person who owns the portal's certificate and the CARLOS portal settings.
That should be one person (see [Ownership](#3-ownership)).

**Related:**
- [`patient-portal-client-security.md`](patient-portal-client-security.md) explains what the pin
  protects against and what it does not.
- [`patient-portal-staging-checklist.md`](patient-portal-staging-checklist.md) is the end-to-end
  staging run, which uses this runbook for its pin steps.
- The recipes quoted here also appear in `src/main/resources/carlos.properties` under *Patient
  Portal integration*.

## What CARLOS enforces

These rules are fixed in code, and no choice below loosens them.

- **A pin is required.** `patient_portal.certificate.pins` must hold at least one
  `sha256/<base64>` pin of the public key of the certificate CARLOS is served. There is no fallback
  to plain certificate-authority trust. A missing or malformed pin stops the portal client; the rest
  of CARLOS keeps working.
- **Normal TLS still applies.** The certificate must also be trusted by the CARLOS JVM and match the
  hostname. Redirects are refused.
- **Only the leaf key is pinned.** Pinning an issuing CA to survive key changes is not supported.
- **Settings are read at startup.** Every pin change needs a CARLOS restart.
- **A wrong pin fails before any request.** CARLOS sends nothing, not even the service token, to an
  endpoint whose key does not match.
- **The pin is not the staff-assertion key.** The TLS pin identifies the portal to CARLOS. The
  Ed25519 staff-assertion key identifies CARLOS to the portal. They are managed separately.
- **The portal can be switched off.** With `patient_portal.enabled=false` (added in #3934), CARLOS
  makes no portal call at all, so the portal can be taken offline while a pin problem is fixed.

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

Alternatives:
- **A separate internal hostname on a private network.** The internal API is then unreachable
  from the internet. It needs a second certificate, usually from a private CA added to the CARLOS
  truststore, and a VPN or private link between the hosts.
- **Avoid a path under the clinic's existing website.** Whoever hosts the website then ends the
  portal's HTTPS, on their renewal schedule, usually with a new key each time.

> Record: patient address, CARLOS address, whether they exist yet, staging addresses.

## 2. Decide where HTTPS ends

The pin must match the key of whatever server answers CARLOS's connection. That choice decides
who can change the key, and so who can break the portal without meaning to.

| Where HTTPS ends | Verdict |
|---|---|
| nginx on the portal host, with a key the clinic controls | **Recommended.** It is the portal's documented deployment, and the key changes only when the clinic changes it. |
| Caddy on the portal host | Workable only if key reuse is configured. By default Caddy may make a new key at renewal, which breaks the pin. The reference nginx rules (IP restriction, header stripping) must be translated. |
| A CDN such as Cloudflare in front | **Not for CARLOS's route.** The CDN rotates its edge key on its own schedule and sees decrypted patient data. Patients may use a CDN only if CARLOS reaches the portal directly or privately (section 1, internal hostname). |
| A cloud load balancer | Only with a certificate whose key the clinic generated and uploaded. Provider-managed certificates rotate their keys automatically. |

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
which could be an attacker. **Never take a pin from a live connection, a browser, an online checker
or a pin-mismatch error.**

### Recommended: generate the key yourself

The pin then comes from a key you created, with no network step, and can be in CARLOS before the
portal goes live. On the portal host, or on an offline machine whose output is copied there:

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

Have the certificate issued for that request: with certbot, `certbot certonly --csr portal-tls.csr`
plus your usual validation options, or through your certificate authority's CSR upload.

### Acceptable: compute it from the certificate file on the server

When the key already exists, for example one certbot generated, copy the certificate **file nginx
serves** over SSH or another authenticated administration channel, then:

```sh
printf 'sha256/'; openssl x509 -in verified-portal-leaf.pem -pubkey -noout \
  | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
```

For certbot, that file is `/etc/letsencrypt/live/<name>/cert.pem`.

### Either way

1. A second person computes the pin independently, from the same key or file, and gets the same
   value.
2. The pin goes into `patient_portal.certificate.pins` in the deployment's override properties,
   not in the committed `carlos.properties`. The `sha256/` prefix is part of the value.
3. The standby pin from section 5 goes in the same setting, comma-separated.

> Record: the trusted channel, who computed the pin, who checked it, and where the approved pins
> are kept. Pins are public-key hashes and are not secret; private keys never go in the record.

## 5. Renewal and rotation

Renewal and rotation are different things:
- **Renewing** with the same key keeps the pin valid, and CARLOS needs no change.
- **Rotating** to a new key needs new pins.

Public certificates now last 90 days or less, so renewals must never rotate the key by accident.

### Renew with the same key

- **With certbot and a CSR** (section 4), renew by reissuing from the same `portal-tls.csr`. CSR
  certificates are not renewed by `certbot renew`, so schedule the reissue.
- **With certbot managing the key**, set `--reuse-key` once, for example
  `certbot reconfigure --cert-name <name> --reuse-key`. Check that
  `/etc/letsencrypt/renewal/<name>.conf` has `reuse_key = True`. Never pass `--new-key` or change
  `--key-type`: both create a new key.
- **After every renewal**, recompute the pin from the served certificate (section 4). It must equal
  a configured pin. Automate this check if you can; a mismatch found here costs nothing, while one
  found by CARLOS stops the portal.

### Keep a standby key pinned

Generate a second key now, exactly as in section 4, and add its pin to CARLOS alongside the live
one from day one. Store its private key offline, protected like any private key.

If the live key is compromised or lost, move the portal to the standby key. CARLOS already trusts
it, so nothing breaks:
1. Issue a certificate for the standby key's request.
2. Install the key and certificate on nginx and reload it.

Afterwards, generate a new standby key and pin it with the rotation steps below.

### Rotate on a schedule

Rotate the live key on a fixed interval, for example yearly, and whenever the standby is used.
Never leave CARLOS without a valid pin:

1. Generate and verify the new key's pin (section 4).
2. Set `patient_portal.certificate.pins` to the old and new pins together, keeping the standby.
   Restart CARLOS.
3. Switch nginx to the new key and certificate, and reload it.
4. Verify from CARLOS: open a patient's **Patient portal** page, which must load without a portal
   error.
5. Remove the old pin, restart CARLOS, and verify again.

> Record: the renewal method and key-reuse setting, the rotation interval and next date, and where
> the standby key is kept.

## 6. Roll out, verify and recover

### Rollout

1. **Staging first.** Run [`patient-portal-staging-checklist.md`](patient-portal-staging-checklist.md)
   with staging keys and test patients. Never reuse staging keys, tokens or pins in production.
2. **Production.** Generate fresh keys and pins with sections 4 and 5, configure CARLOS, restart,
   and run the production verification below.

### Production verification

Do these on the production installation and record the date and names. Green repository CI and
a passed staging run are not evidence that a live installation is protected.

- [ ] **The right portal connects.** Open a test patient's **Patient portal** page in CARLOS; it
      loads without a portal error.
- [ ] **A wrong pin fails before any request.** Replace the pins with a deliberately wrong one
      (a pin of any unrelated key) and restart CARLOS. The page reports a portal failure, and the
      portal's nginx access log shows **no** request from CARLOS for that attempt. Restore the pins
      and restart.
- [ ] **The standby pin is configured.** The setting holds two pins, and the second matches the
      offline standby key.
- [ ] **A renewal keeps the pin.** After the first renewal, the recomputed pin matches (section 5).
- [ ] **Rotation leaves no gap.** On staging, or at the first scheduled rotation, the five steps in
      section 5 complete with the page working after steps 2, 4 and 5.

Until each item is done, the deployment record says *not yet verified in production*.

### When the pin does not match

CARLOS reports a portal failure and logs that the presented key did not match. Portal features
stop; the rest of CARLOS works.

- **Never** switch pinning off, and **never** copy the presented pin from the error or the log.
- If the key changed by accident, for example a renewal without key reuse, put the previous key and
  certificate back on nginx. CARLOS works again at once.
- If the change was intended, verify the new key through the section 4 channel, add its pin and
  restart CARLOS.
- If neither can happen quickly, set `patient_portal.enabled=false` and restart CARLOS. The portal
  controls go away cleanly until the pin is fixed.
- If nobody on the clinic side changed the key, treat it as a possible impersonation: keep the
  portal off and investigate before trusting any new key.

> Record: who provisions pins, restarts CARLOS, verifies, and monitors; the target date; whether
> staging came first.
