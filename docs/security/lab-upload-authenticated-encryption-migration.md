# Lab-upload sender response changes and version 2 coordination

## Status

DRAFT local proposal only. This document describes planned behavior relative to the reviewed
compatible release candidate ce7b32cbcb2f50e9727d665a1e3db9f6224cdcf8, based on
release/2026.08 at 2d4588c931e83531945fbc863116ce793ed3ab97. It is not vendor approval,
executed runtime evidence, a version 2 implementation, or a supported deployment.

The compatible candidate has separate actual local107-test and full-source review evidence.
Those results do not cover this draft. This draft has not been compiled or tested.

## Scope

Keep legacy request fields, algorithms, successful200 result view, duplicate409 behavior,
bad-signature406 classification, private staging, both signature boundaries and current
transaction-aware storage. Separate sender-visible changes into this draft:

- Unknown/blank/missing service and undecryptable envelopes use400/rejected in HTTP-code mode.
- Body mode uses the XML result and a rejected token for those cases.
- Sender-key lookup or parsing faults move from uncaught/global error routing to handled
  exception responses. In body mode this changes HTML500 to XML200/exception.
- Signature-reading/algorithm/key faults and changed or unreadable saved copies use
  exception500 in HTTP-code mode. In body mode they use XML200/exception.
- Filename-validation, no-file and action-level source-validation errors honor their400/403
  codes when the flag is present, instead of the compatible early-return200.
- Receiver private-key unavailability remains a receiver fault. A stale sender wrapping to
  a replaced receiver key is indistinguishable from another failed unwrap and enters the
  proposed rejected path. No vendor retry policy is assumed.

## Source-derived compatibility matrix

These are intended mappings, not observed network responses. With the flag absent, the
ordinary result view produces XML at default200. The actual error-page content type and
multipart/interceptor behavior require separate container checks.

| Case | Compatible HTTP-code mode | Draft HTTP-code mode | Compatible body mode | Draft body mode |
| --- | --- | --- | --- | --- |
| New valid message | 200 normal result | held | 200 XML uploaded | held |
| Duplicate | 409 error dispatch | held | 200 XML uploaded previously | held |
| Missing/malformed/wrong signature | 406 validation failed | held | 200 XML validation failed | held |
| Unknown/blank/missing service | global500 HTML | 400 rejected | global500 HTML | 200 XML rejected |
| Sender-key lookup or stored-key parsing fault | global500 HTML | handled500 exception | global500 HTML | 200 XML exception |
| Failed unwrap, including stale receiver-key wrap | 500 exception | 400 rejected | 200 XML exception | 200 XML rejected |
| Lazy AES block/padding failure | 500 exception | 400 rejected | 200 XML exception | 200 XML rejected |
| Receiver private key unavailable | 500 exception | held | 200 XML exception | held |
| Signature-read/verification-provider fault | 406 validation failed | 500 exception | 200 XML validation failed | 200 XML exception |
| Saved copy changed after staging | 406 validation failed | 500 exception | 200 XML validation failed | 200 XML exception |
| Save returns no path | 500 exception | held | 200 XML exception | held |
| Parser rejects | 500 upload failed, empty audit | held | 200 XML upload failed, empty audit | held |
| Filename-validation or no-file with valid service | early200 XML exception | 400 exception | 200 XML exception | held |
| Action-level rejected source with valid service | early200 XML exception | 403 exception | 200 XML exception | held |
| HHS filter acknowledgement | 200 uploaded, absent audit | held | XML audit=failure default; saved copy retained | held |
| Unknown store commit outcome | 500 exception; saved copy retained | held | 200 XML exception; saved copy retained | held |

A file rejected before durable saving leaves no document copy. The release already removes
unreferenced saved rejects through its current storage helper; historical claims that every
bad signature leaves an orphan do not describe this release.

## Vendor and rollout gates

Before ready/pilot/release, obtain a named owner for every active sending system and record:

1. Which response mode it actually uses and whether it reads status, content type, outcome,
   audit or some combination. Handler names do not prove that a sender is active.
2. Actual retry/backoff/quarantine decisions for200/400/403/406/409/500 and every changed
   outcome. Test a message that has not been delivered; never assume500 means it will retry.
3. A synthetic sender fixture for every before/compatible/draft case in both modes. Include
   stale receiver-key wraps, key-store outage, signature-read failure and lost commit acknowledgement.
4. Owner-approved migration timing, restore/key-rotation recovery, staged cutover and rollback.
   Preserve the original receiver and keys until the agreed rollback window closes.
5. Actual deployed container/Struts/multipart/JSP evidence and fresh whole draft code/test review.
   No actual sender or clinical/production database is contacted by the local preparation.

Changing500/406 to400 could make a sender stop retrying an undelivered message. Changing
global HTML500 into body-mode XML200 could make a sender that checks only status believe
delivery succeeded. These are unresolved compatibility questions, not verified vendor behavior.

## Coordinated authenticated version 2 transport

Issue3413 remains the owner/contract discussion for an authenticated versioned envelope.
No version 2 decoder, cipher substitution, legacy cutoff, telemetry, new endpoint, database
migration, or workflow change is implemented by this draft. The original published RFC and
discussion remain available in PR3417 history; this release draft does not turn that RFC into
a verified production contract.

A future version 2 proposal requires explicit format/canonicalization and key ownership,
recipient and issuer binding, freshness/replay policy, interoperable sender fixtures, dual-read
and downgrade rules, rollout/rollback and legacy cutoff approvals. Each remains external work.
