# Patient Portal Runtime — why the portal is a separate Python service

**Status:** accepted for the MVP / pilot, open to revisit before general availability
**Applies to:** `patient_portal/**`
**Written:** 2026-08-13, from the implementation in PR #3220

## Context

CARLOS needs a patient-facing surface: patients receive encrypted email from the clinic and need
somewhere to retrieve the generated passphrase that opens it, plus the account, contact, and
help screens that go with having a login at all.

Every other line of CARLOS is Java on Struts/Spring/Hibernate against MariaDB, deployed as one WAR
in Tomcat. The portal is Python on FastAPI/SQLAlchemy/Alembic against PostgreSQL, deployed as a
separate process with its own database. That is the single largest structural decision in the
codebase, and this document records the reasoning so it can be argued with rather than inferred.

The forcing constraint: **the patient-facing surface is reachable from the public internet, and
the CARLOS application is not.** Everything below follows from wanting that boundary to be a
process and network boundary rather than a servlet filter.

## Decision

Build the portal as a standalone service with its own datastore, and connect it to CARLOS through
a narrow authenticated HTTP contract (`/internal/carlos/*`) rather than a shared database or a
shared deployment.

### Why a separate process rather than a module in the WAR

- **Blast radius.** A defect in the patient surface cannot reach the EMR's session handling, its
  Struts action mappings, its filter chain, or its database credentials. An RCE in the portal
  yields the portal's database — portal accounts, hashed credentials, and encrypted passphrases —
  not the chart.
- **Different threat model, different controls.** The portal needs internet-grade rate limiting,
  MFA, lockout, CSRF on every mutation, and a strict CSP; CARLOS needs none of those at the same
  settings, and retrofitting them onto the shared filter chain would change behaviour for staff
  users. The response-rewriting-filter incidents recorded in `CLAUDE.md` are the precedent: shared
  filters are where changes intended for one route family break another.
- **Independent deployment.** Patient-facing downtime and EMR downtime are different incidents with
  different urgency. Separating them lets either be restarted without the other.
- **Data separation is the point.** The portal deliberately holds no chart data. It stores portal
  accounts, invites, audit events, and encrypted passphrases — nothing clinical. Sharing the
  CARLOS schema would have made that boundary a convention instead of a fact.

### Why Python/FastAPI rather than a second Java service

This is the weakest link in the argument and should be read as a genuine trade, not a slam dunk.

In favour: the portal is a small, self-contained web service whose whole job is HTTP, validation,
and crypto. FastAPI/Pydantic gives request validation, the OpenAPI contract CARLOS integrates
against, and typed settings validation with far less ceremony than the equivalent Java stack; the
whole service is ~9k lines including its own migrations.

Against, and unresolved: **it is a second language in a Java shop.** Every contributor who can
review the EMR cannot necessarily review the portal, and vice versa. Security patching now has two
cadences and two advisory feeds. This is a real, ongoing tax — see *Consequences* for what has to
be true for it to stay acceptable.

### Why PostgreSQL rather than the MariaDB already deployed

The portal leans on two PostgreSQL features that MariaDB does not offer equivalently:

- **Partial unique indexes** (`WHERE status = 'pending'`), used to enforce one pending invite per
  patient, one pending reset token per account, one pending contact review per account, and one
  pending email-change request per account. These are correctness invariants held by the database
  rather than by application code, and MariaDB has no partial-index equivalent — they would become
  application-level checks with a race window.
- **Transactional advisory locks** (`pg_advisory_xact_lock`), used to serialise activation-attempt
  throttling so a burst of concurrent activation attempts cannot each read a stale failure count.

Concurrency behaviour that depends on these is exercised in CI against PostgreSQL 16
(`tests/test_postgresql_integration.py`), not only against the SQLite used for unit tests.

The cost is real: a clinic now runs two database engines, with two backup, restore, and upgrade
procedures. If the portal ever needs to run on the clinic's existing MariaDB, both invariants would
have to be re-expressed, and the migration would not be mechanical.

### Why a narrow HTTP contract rather than a shared database

Sharing tables would have coupled the portal's schema to Hibernate's mapping and made every portal
migration an EMR migration. The internal API keeps the coupling to a handful of documented
operations (invite lifecycle, account unlock and enable/disable, unlock-secret create/publish/
revoke, contact-review listing and decision) with explicit request and response models.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Java module inside the CARLOS WAR | Shares the filter chain, session handling, and database credentials with the EMR; a public-internet defect reaches the chart. |
| Separate Java/Spring Boot service | Keeps one language and one skill set. Would have avoided the two-language tax entirely; rejected on development speed for the MVP, which is a weaker reason than the others here and is the alternative most worth revisiting. |
| Portal on the existing MariaDB | Loses partial unique indexes and advisory locks; four uniqueness invariants become racy application checks. |
| Shared database between EMR and portal | Couples schemas and migration cadence; removes the data-separation property that motivates the split. |

## Consequences

Accepted costs, stated plainly:

- Two languages, two dependency ecosystems, two lockfile/audit pipelines, two migration tools, two
  database engines, two backup/restore procedures, two sets of security advisories.
- Portal dependency advisories are release-blocking, not routine — the service handles credentials
  and the AES-256-GCM keys that protect patient passphrases. `cryptography` in particular is called
  out in `patient_portal/README.md`.
- The clinic/deployment operator owns SMTP/SMS delivery, database and backup alerting, restore
  drills, and incident response; CARLOS maintainers own application regressions and migration
  compatibility. Runbooks must name both before pilot traffic.

Conditions under which this decision should be revisited:

- If the portal grows to need chart data directly, the "holds nothing clinical" property is gone
  and the calculus changes.
- If Python ownership cannot be staffed — that is, if there is no named maintainer who patches this
  service on a defined SLA — the two-language tax is not being paid and a Java rewrite is cheaper
  than a stale service handling credentials.
- If a multi-clinic deployment is ever wanted. The MVP is deliberately one deployment, one
  database, one origin, one clinic identity per clinic, and several of the schema decisions assume
  it.

## Open items owned by the author

These belong in this document but cannot be reconstructed from the code:

- Whether a Java service was evaluated and on what grounds it was set aside.
- Who the named Python maintainer is, and the patch SLA for portal dependency advisories.
- Whether any clinic in the pilot cohort has an operational constraint against running PostgreSQL.

## See also

- `patient_portal/README.md` — configuration, operations, and the current pilot-blocker list
- `docs/architecture/layer-names.md` — the naming policy the portal's `*ViewModel`,
  `*ViewModelAssembler`, and service modules follow
