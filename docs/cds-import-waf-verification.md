# CDS upload WAF verification

The packaged policy in `debian/assets/modsecurity/crs-overrides.conf` allows the
browser's bounded binary upload protocol through an otherwise unchanged CRS
content-type policy. The exception requires all of these selectors:

- Exact canonical path `/carlos/form/cdsUpload`, without encoded or matrix paths,
  aliases, or percent-encoded query components.
- `POST`, exactly one query `operation=chunk`, and exactly one
  `Content-Type: application/octet-stream` header, without MIME parameters.
- The ordinary CRS checks on headers, query fields and other inspected targets.

The client additionally sends `apiVersion=2`, its upload ID, offset, byte count,
SHA-256, session and native CSRF token. Application authorization, protocol-version
checks and exact upload receipts remain authoritative. The policy does not remove
CRS rules or make the binary MIME type available to other transactions. The Java
service defaults to four-MiB chunks and allows configuration only up to sixteen
MiB. Both known-length and chunked binary requests have an explicit sixteen-MiB
WAF ceiling because an unparsed opaque body does not receive the parser-specific
`SecRequestBodyNoFilesLimit` protection in libModSecurity. The global fifty-MiB
request limit and sixteen-MiB parsed-body limit remain unchanged.

`scripts/ci/run-cds-waf-boundary.py` is an opt-in actual HTTP gate. Run it against
a disposable Nginx with the packaged ModSecurity configuration and CRS in blocking
mode. Its upstream must be an **inert test receiver**, never a CARLOS clinical
endpoint. The receiver streams the request, computes its byte count and SHA-256,
and returns only `{"bytes":N,"sha256":"..."}` plus
`X-Carlos-Test-Inert-Upstream: true`. It must support both known-length and chunked
requests. Configure the isolated proxy to add
`X-Carlos-Cds-Test-Proxy: <random-32-hex-nonce>` on every response; this header is a
test fixture, not a production setting. Keep its actual ModSecurity JSON audit
log private. The gate accepts no patient files, credentials, sessions or imports.

```sh
python3 scripts/ci/run-cds-waf-boundary.py \
  --disposable --base-url http://127.0.0.1:18084 \
  --proxy-token "$CDS_WAF_TEST_PROXY_TOKEN" \
  --audit-log /private/cds-waf/modsec_audit.log \
  --evidence /private/cds-waf/new-boundary-evidence
```

The gate streams synthetic binary bytes, including NUL and non-UTF-8 bytes. It
checks exact byte preservation for the canonical request, four-MiB, chunked
four-MiB and sixteen-MiB bodies. It rejects unrelated paths, route aliases,
encoded/matrix/dot-segment paths, repeated or conflicting operation parameters,
other methods and disallowed content types. It separately proves ordinary JSON
still works, SQL injection detection remains active on the permitted transaction,
the global request ceiling remains active, and both binary length mechanisms
reject sixteen MiB plus one byte. Private JSON evidence records response status,
proxy/backend proof and numeric rule IDs; it does not include request bodies or
audit headers.

The blocking-engine matrix in `scripts/ci/run-cds-waf-boundary.py` checks, against the packaged
Nginx, ModSecurity connector, libModSecurity and CRS versions, that the canonical binary request
is allowed, that unrelated SQL injection still receives `403` from `942100`/`949110`, and that
explicit binary over-limit requests receive `413` from the scoped rules. This inert matrix proves
the proxy boundary, not authentication or a completed clinical import.

Verify the native application separately through the same real proxy: a canonical
request without a valid session/CSRF must be denied, an authorized small chunk
must receive its exact application receipt, and an over-configured-limit chunk
must fail without advancing its receipt offset. Use the normal browser login and
API version; retain protected session material outside arguments and published
logs. A loopback HTTP or HTTPS browser origin is required for browser WebCrypto.
For an isolated proxy on a nonstandard local port, preserve `$http_host` or use
an equivalent redirect policy so native login redirects keep that public port.
The large-ingress browser gate also requires the test proxy nonce header and must
not silently use a direct Tomcat TCP relay. Do not change production proxy limits
or application authorization to obtain a passing result.
