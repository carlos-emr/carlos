# OAuth configuration contracts

The two `AppOAuth1Config` classes serve different public interfaces and retain
their package names and methods. Substituting imports would change behavior.

| Class | Role and fields |
| --- | --- |
| `app.AppOAuth1Config` (`AppAuthConfig`) | Outbound application configuration: type/name, credentials, request/access token services, authorization URI and `baseURL`. |
| `login.AppOAuth1Config` | Inbound OAuth verifier configuration: credentials, `baseUrl`, callback/application URIs and scopes. |

`OAuthInterceptor`, `AccessTokenResource` and `OscarRequestTokenService` construct
the inbound class with setters from the registered consumer; their signed
request path does not parse this XML. The public XML factories remain available
for existing integrations. Both schemas now share only secure XML mechanics.
Well-formed XML, CDATA and escaped Unicode credentials are parsed as XML values,
not extracted by string search. Comments before the root are accepted; direct
leaf fields are used. Duplicate or nested credential fields, malformed XML and
DTDs are refused without credential-bearing exception diagnostics. Already
parsed DOM documents are accepted, with DTDs rejected there too.

Missing optional fields remain null and present-empty fields remain empty.
Outbound whitespace is preserved; the inbound factory retains its historical
trimming and comma-splitting behavior. The inbound factory's established
null-on-failure contract is preserved with a fixed warning. The outbound factory
continues to throw on parse errors. Callers must validate required settings for
the operation they intend to perform; this does not make absent credentials
valid. Token XML creation and key/secret reading retain their API, escaping and
round-trip behavior without logging token XML details.
