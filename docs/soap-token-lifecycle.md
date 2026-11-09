# SOAP token lifecycle

SOAP login and `login2` keep their published response types and string token
field. They now issue independent 256-bit random bearer tokens (`ct1.` followed
by URL-safe Base64). Only a namespaced SHA-256 digest is stored in the existing,
indexed `SecurityToken.token` column. The token, password hash and username are
not written to token metadata or issue/revocation audit data. The `soap1:` stored-digest namespace is reserved and excluded from generic token
lookup, including case aliases, so a stored hash cannot itself be replayed as a
credential through another token consumer. Issuance fails if a custom DAO does
not enforce that purpose boundary. Custom token consumers and housekeeping must
reserve this namespace. Other token purposes retain their representation and
lookup API.

The default lifetime is 60 minutes. Set `soap.token.ttl.minutes` to an integer
from 1 through 1440; an invalid setting fails explicitly during service setup.
New settings affect newly issued credentials. Tokens are reusable until expiry
or revocation. Absolute epoch timestamps in metadata determine validity across
JVM timezone differences; the legacy table's display date is not the authority.
Cluster clocks must be synchronized; validation permits up to one minute of
forward issue-time skew and never extends the absolute expiry.

Validation reads shared storage on every request, binds the credential to the
security account, username/provider and current password hash, and checks the
account expiry. A changed binding rejects the old credential. Explicit
revocation removes the account's SOAP token rows, independently of credential
changes, and records the actual removed count. Already authenticated in-flight
requests are not undone; subsequent validation sees committed revocation. No
per-JVM token cache or JVM-start seed is used. Restarting a node retains valid
persisted credentials, and another upgraded node can validate them.

## Revocation

An authenticated browser session can send:

```http
DELETE /carlos/ws/rs/soapTokens
X-Carlos-Token-Action: revoke
```

This revokes the session account's SOAP tokens and returns 204, including when
there were none. A `securityId` query parameter targets another account only
with the existing `_admin w` or `_admin.userAdmin w` permission. The manager
checks this policy before deletion. This resource keeps server registration parity, but the OAuth
policy explicitly blocks this route in every access mode; no grantable OAuth
scope opens it. It requires explicit non-form intent and rejects a
browser `Origin` values other than the trusted `soap.token.revoke.origin` setting, because the existing `/ws/*` CSRF exemption also
covers session REST. No token or secret belongs in the URL or request body.

Issue and revocation use a transaction with strict audit persistence. If storage
or audit fails, the operation raises an error; it must not return a successful
credential or revocation response. Duplicate digest rows and malformed stored
metadata are explicit storage errors, rather than arbitrary successful matches.
Expired rows contain no usable bearer secret. A qualified bounded maintenance script is described below; the deployed schedule and
installed multi-node qualification remain acceptance items in the PR ledger.

## Cutover and recovery

All nodes handling a SOAP client must be upgraded together, or traffic must
switch to an upgraded pool after every node in that pool is ready. Old nodes do
not understand these tokens. Do not mix old and new validators behind a random
load balancer. There is no deterministic-token fallback: clients must discard
previous tokens, authenticate again with their supported username/password
flow, and handle the existing authorization fault by refreshing credentials.
The published WSDLs do not change. Password authentication remains supported.

A token-only client must have a tested credential-refresh path before cutover.
Qualify its polling and failure handling before release. Keep the security
fix when recovering application/database state: restoring older authentication
data can restore old credential bindings and previously revoked rows. Revoke
SOAP tokens after a security-state restore, isolate old application nodes, and
re-authenticate clients. Rolling back to the deterministic validator restores
the vulnerability and is not a supported recovery for this change.

Set `soap.token.revoke.origin` to the browser deployment origin, for example `https://clinic.example`, without a path, user info, query or fragment. This trusted configuration is independent of request Host and forwarding headers. An invalid nonempty value fails resource setup; when unset, any request carrying Origin is refused. Non-browser session requests without Origin still require explicit revocation intent and manager authorization. Configure the public origin when terminating TLS at a proxy; no forwarded header grants trust.

## Expired-row housekeeping

The release includes `scripts/sql/purge-expired-soap-tokens.sql`, qualified on
MariaDB 11.8.8 with generated and Ontario/BC Flyway schemas. Run it on a
dedicated connection to the selected application database, with a maintenance
account allowed to SELECT and DELETE `SecurityToken`. Keep client credentials
in an operator-owned protected option file, rather than command-line secrets:

```sh
mariadb --defaults-extra-file=/etc/carlos/token-maintenance.cnf \
  --database=carlos --batch --skip-column-names \
  < scripts/sql/purge-expired-soap-tokens.sql
```

Use the script from the deployed release and verify its package/source identity.
Schedule it through the deployment's database-maintenance mechanism. Each
invocation deletes at most 500 rows and reports the actual removed count;
repeat successful full batches until the count is below 500. Bound the number
of batches in a maintenance window and monitor both exit status and remaining
backlog. A failed invocation must stop the loop and alert operations.

Only the reserved SOAP namespace is considered. The cutoff uses database epoch
seconds, independent of session time zones, and retains rows at least 24 hours
after their authoritative metadata expiry. The display `expiry` column never
authorizes deletion. Unexpired/recent rows, other token purposes and audit logs
are preserved. Synchronize database/application clocks as required for validation.

Corrupt SOAP metadata, invalid lifetime/owner values, a non-InnoDB table or an
already active caller transaction cause an explicit refusal. Deletion failures
roll back that batch; earlier successful batches remain committed. No token,
metadata or patient record is printed by the script. Investigate corrupt storage
rather than weakening validation or deleting unrelated token purposes. The
application does not install an operator's maintenance schedule automatically;
record that schedule and monitoring in the deployment qualification.
