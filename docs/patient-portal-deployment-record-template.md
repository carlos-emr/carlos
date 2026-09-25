# Patient portal deployment record (template)

One record per clinic installation, filled in by the clinic following
[`patient-portal-tls-runbook.md`](patient-portal-tls-runbook.md).

**Keep the completed record with the clinic's own operational documents, not in the CARLOS
repository or its issues.** It names people, hosts and procedures specific to one installation.

**Never write into it:** private keys, the service token, staff-assertion private keys, passwords,
or patient data. Pins are public-key hashes and may be recorded.

---

**Clinic:**
**Record owner:**
**Last updated:**

## 1. Addresses

| Item | Production | Staging |
|---|---|---|
| Patient address (`patient_portal.public_base_url`) | | |
| CARLOS API address (`patient_portal.base_url`) | | |
| Exists yet, or planned? | | |

## 2. Hosting

| Item | Answer |
|---|---|
| Where the portal runs (provider, machine) | |
| What ends HTTPS for CARLOS's connection | |
| CARLOS server addresses allowed to reach `/internal/carlos/` | |

## 3. Owners

| Area | Owner | Backup |
|---|---|---|
| DNS for the portal names | | |
| TLS certificate and private key | | |
| CARLOS portal settings and pins | | |
| Staff-assertion keys (Ed25519) | | |
| Incident response for portal outages | | |

## 4. First pin

| Item | Answer |
|---|---|
| Method (self-generated key, or certificate file) | |
| Trusted channel the key or certificate came through | |
| Pin computed by | |
| Independently checked by | |
| Where the approved pins are kept | |
| Live pin | `sha256/` |
| Standby pin | `sha256/` |

## 5. Renewal and rotation

| Item | Answer |
|---|---|
| Renewal method and key-reuse setting | |
| Post-renewal pin check (automated or manual, by whom) | |
| Where the standby private key is stored | |
| Rotation interval | |
| Next scheduled rotation | |

## 6. Rollout and verification

| Item | Answer |
|---|---|
| Staging checklist completed (date, by whom) | |
| Who provisions pins and restarts CARLOS | |
| Who monitors portal failures | |
| Target production date | |

| Production check | Date | By |
|---|---|---|
| The right portal connects | | |
| A wrong pin fails before any request (nginx log checked) | | |
| The standby pin is configured | | |
| A renewal keeps the pin | | |
| Rotation leaves no gap | | |

**Production verified:** not yet / yes, on ____ by ____

## Change log

| Date | Change (renewal, rotation, standby used, owner change) | By |
|---|---|---|
| | | |
