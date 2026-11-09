# Provider billing identifiers in API responses

The existing `ohipNo`, `billingNo`, `rmaNo` and `hsoNo` properties remain nullable
strings in the REST DTOs and published SOAP contracts. A response includes their
values only when the authenticated caller has `_billing r`. Provider self or
administrative read access alone does not add this billing permission.

The existing provider visibility policy also applies. Billing permission does
not turn another provider's summary into a complete record. Other fields retain
their established self/admin/summary behavior. Login responses, SOAP provider
lists, REST lists and native provider JSON use the same billing disclosure rule.
Clients needing these identifiers must use an account with the existing billing
read permission and retain their normal provider access.

Converters leave the persisted provider unchanged. Java callers using the
original ProviderTransfer conversion without an authorization context receive
null billing identifiers; service code can use the authorized overload after the
shared policy check. Inbound ProviderTo1 conversion retains its existing fields;
this change does not create a new billing write permission. Permission-service
failures propagate instead of being treated as a successful authorization.
