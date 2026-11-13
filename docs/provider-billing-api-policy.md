# Provider billing identifiers in API responses

The `ohipNo`, `billingNo`, `rmaNo` and `hsoNo` properties are nullable strings in
the REST DTOs and the published SOAP contracts. A response includes their stored
values **whenever it includes the provider's record**; no billing permission is
consulted. Existing clients read these identifiers (for claims, referrals and
provider matching) with accounts that do not hold `_billing r`, and an unmodified
client does not need a new permission to keep receiving them.

This is the published behaviour (as at 2026.08.0-beta6), restored after the
release-hardening change #6098, which no tagged build contains, required
`_billing r`. The authentication and authorization needed to reach each endpoint
are exactly what they were:

| Response | Billing identifiers |
| --- | --- |
| SOAP `getLoggedInProviderTransfer`, `getProviders`, `getProviders2` | Present |
| SOAP `login2` (the logged-in provider) | Present |
| REST `providerService/provider/{id}`, `provider/me`, `providerjson/{id}`, `providers`, `providers_json`, `providers/search` | Present in a whole record; absent from a summary (below) |
| `ProviderTo1` nested in other REST responses (for example a patient's provider) | Present |
| Provider embedded in appointment and consultation responses | Present |

The existing REST provider visibility policy still applies and is the only
boundary: a provider's whole record goes to that provider and to holders of
`_admin r`; every other caller receives the summary (number, names, specialty,
status, team and type), which has never contained billing, contact, licence or
personal details. Holding `_billing r` does not turn a summary into a whole
record, and lacking it does not remove anything from a whole record.

Converters leave the persisted provider unchanged. `ProviderTransfer.toTransfer`
and `ProviderConverter` copy the four identifiers for any caller; deciding which
providers are converted is the calling service's job. Inbound `ProviderTo1`
conversion retains its existing fields; nothing here creates a billing write
permission.

The separate `billingDetail` gating on appointment history is unaffected: patient
billing lines there still require global and patient-specific billing read. See
[rest-embedded-record-snapshots.md](rest-embedded-record-snapshots.md).

`ProviderBillingDisclosureUnitTest`, real-SOAP `ProviderWsEndpointTest`,
`LoginWsTokenUnitTest`, real-CXF `ProviderServiceRecordVisibilityEndpointTest`
and `EmbeddedRecordSnapshotsUnitTest` run with `_billing r` denied and require
all four identifiers; the visibility test also requires that `_billing r` adds
nothing to a summary.
