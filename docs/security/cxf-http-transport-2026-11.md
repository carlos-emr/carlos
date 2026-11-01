# CXF HTTP 4 removal: draft transport decision

Tracking: [PR #5467](https://github.com/carlos-emr/carlos/pull/5467),
[dependency audit #5470](https://github.com/carlos-emr/carlos/issues/5470), and
[broader CXF cleanup #3053](https://github.com/carlos-emr/carlos/issues/3053).
The [adversarial report](https://github.com/carlos-emr/carlos/pull/5467#issuecomment-6549372557)
and [alternative assessment](https://github.com/carlos-emr/carlos/pull/5467#issuecomment-6551180655)
include the source references and executable research probes.

## Support status and scope

[Apache's status page](https://hc.apache.org/status.html) marks HttpAsyncClient
4.1 and HttpCore NIO 4.4 as end of life. HttpClient 4.5 and HttpCore Classic
4.4 remain in maintenance for major defects and security fixes. Calling the
entire HTTP 4 family unsupported would overstate the finding.

The optional `cxf-rt-transports-http-hc` adapter is the resolved path to these
four HTTP 4 libraries. Its removal, lockfile update and transitive Enforcer ban
remove five artifacts, without replacing CXF or changing the existing HTTP 5
dependencies. The wider `apache-cxf` aggregate cleanup remains separate.

Classpath discovery matters: the adapter registers a conduit factory even
when application code never imports it. Under CXF 4.1.9's default `ASYNC_ONLY`
policy, ordinary synchronous calls already use its JDK superclass. Removing
the adapter changes asynchronous and explicitly forced Apache calls. The
adapter subclasses `HttpClientHTTPConduit`, so an `instanceof` check alone
cannot identify the selected implementation.

## Client policy

`EdtClientBuilder` selects URLConnection through two per-proxy request-context
properties: `use.async.http.conduit=false` and
`force.urlconnection.http.conduit=true`. The first also overrides an old
Apache adapter configured with `usePolicy=ALWAYS`. No JVM-wide transport
property or new operator setting is introduced.

This preserves the existing SOAP interfaces, WS-Security configuration,
certificate trust, hostname verification, MTOM settings and timeout values.
The known integrations use synchronous HTTP/1.1 SOAP. URLConnection supplies
socket idle-read timeouts while reading a response body; the existing
240-second receive setting is **not a total operation deadline**. A response
that continues making progress can take longer. Native asynchronous transport,
HTTP/2, Apache pool behavior and Apache-specific authentication are not promised
by this choice.

The application retains ownership of its disk-backed response replay until
completion or fault, closing it explicitly even if MIME/security processing
replaces the message stream. Downstream faults also clear the attachment
ThreadLocal. This does not claim ownership of every cache inside CXF.

## Consumer inventory

Search of `src/main`, deployment templates, `.devcontainer` and `debian` found:

| Consumer | Evidence and disposition |
| --- | --- |
| MCEDT | `DelegateFactory` constructs `EDTDelegate` through `EdtClientBuilder`, with MTOM enabled. |
| HCV | `OnlineHCValidator` constructs `HCValidation` through the same builder. |
| Other application client factories | No other main-source `JaxWsProxyFactoryBean`, `JAXRSClientFactory`, `WebClient`, or `STSClient` construction was found. Other CXF imports implement server endpoints/interceptors. |
| Generated service classes | `EDTService` and `HCValidationService` expose generated `getPort` methods; no application call sites were found. |
| External deployments/extensions | Not inventoried by source search. External bus/JVM transport settings, dynamic clients and custom modules remain a deployment gate. |

The per-client settings do not affect an unrelated client on the same Bus.
Removing the adapter still affects the whole WAR, so that isolation test does
not establish compatibility for external asynchronous consumers.

## Regression contract and remaining gates

`EdtClientBuilderTransportUnitTest` invokes the real builder and generated EDT/HCV
interfaces against an independent loopback HTTPS peer. The peer verifies the
UsernameToken and signatures covering EBS, IDP, Timestamp and SOAP Body. It
returns signed/encrypted SOAP and signed/encrypted MTOM with ephemeral keys and
synthetic data. Tests cover upload/download bytes, invalid security, TLS
1.2/1.3, certificate/hostname rejection, mutual TLS, stalled headers/body,
truncation, progressing bodies, shared-Bus isolation, concurrent clients and
application replay cleanup. The separate default-transport smoke tests use an
exact conduit-class assertion after adapter removal.

Ubuntu 26.04.1 with Temurin 25.0.3 passed 132 focused tests through the normal
Maven lifecycle. A supplemental isolated classpath restored the original HTTP 4
adapter and all four legacy jars without adding them to the project or WAR:
six builder scenarios (HCV, MTOM, stalled body, tampered signature, truncation,
concurrency) passed under both `ASYNC_ONLY` and `ALWAYS`. The probe asserted that
the legacy conduit factory was active, while the real client's outbound
interceptor asserted URLConnection was selected.

The fixture also exposed **CXF-owned attachment cache files surviving a
completed signed/encrypted download** at a deliberately low spill threshold.
Allocation traces identify `AttachmentDataSource.cache()` during WSS4J
attachment signature processing, separately from the application's replay.
Closing the final attachment stream did not release that cache in the probe.
The replay tests intentionally verify the application's streams; they do not
claim that all CXF attachment caches are released. This finding remains open
for investigation and must be assessed before release promotion.

Keep the PR draft until approved staging MCEDT/HCV checks and a deployed Tomcat
smoke test establish the actual endpoint, TLS, proxy/authentication, chunking,
retry and resource requirements. Include vendor-specific ciphertext transforms
and multiple-resource downloads; the standard SwA fixture is not a vendor
certification. No staging credentials or endpoint configuration were available
in this task's runtime. If a total deadline is required, cancellation must close
the exchange and release its resources; merely timing out the caller is insufficient.

## Alternatives

| Approach | Reason for deferral |
| --- | --- |
| Leave the JDK transport and unset the generic `TLS` field | Fixes the reproduced TLS error but not the mid-body idle timeout finding. |
| Replace the adapter with HTTP 5 | The tested CXF/HTTP 5 combinations returned a partial HTTP 200 string response after an idle timeout. Needs an identified fix and regression before selection. |
| Upgrade to CXF 4.2.4 | Standalone probes reproduced both relevant transport findings. A larger platform upgrade does not by itself establish compatibility. |
| Global URLConnection switch | Changes all CXF clients; the existing builder provides a narrower policy boundary. |
| Temporarily retain HTTP 4 | A possible time-bounded exception if another deployed consumer requires Apache behavior. Does not remove the EOL async/NIO dependencies. |

If native async/HTTP/2 or Apache-specific requirements are discovered, validate
an upstream-fixed HTTP 5 or JDK response-stream implementation against the same
contract rather than assuming the scoped EBS policy satisfies them.
