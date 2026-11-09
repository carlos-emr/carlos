# Required production integration configuration

Before enabling an integration, reconcile the deployed external `carlos.properties`
with the settings below. The shipped file is a development example. Load the
external file using `-Dcarlos_override_properties=/absolute/path/carlos.properties`
and keep its secrets outside version control. Follow [startup configuration](startup-encryption-key.md)
for the durable file and encryption key requirements. Disabled integrations do
not require invented production credentials.

| Integration | Settings to review | Production acceptance |
| --- | --- | --- |
| Legacy mail | `email.host`, `email.port` | Confirm the intended relay and a synthetic delivery through the actual mail path. A loopback relay is valid only when a managed local MTA is deliberately installed and monitored. |
| Modern email | Persisted `EmailConfig` provider and transport JSON (`host`, `port`, authentication/TLS settings) | Configure the provider through the email administration workflow. `LOCAL` accepts only loopback hosts and disables authentication/TLS; changing `email.host` cannot convert it into a remote authenticated SMTP provider. Select a supported remote provider/transport for the relay and test delivery. |
| FHIR sender identity | `ws_endpoint_url_base` | Replace `https://127.0.0.1:8081/oscar` with the externally reachable, correctly named deployment URL/context. `SenderFactory` puts this value in sender metadata; it does not select an outbound FHIR destination. Validate recipient/destination configuration independently. |
| DrugRef | `drugref_url` | The current default is `http://localhost:8080/drugref2/DrugrefService`, rather than the historical public IP reported in #2943. Point it at the intended managed DrugRef service and verify lookup with a synthetic drug. Use protected transport/network placement appropriate to that service. |
| Ontario HCV | `hcv.service.url` | The shipped `ws.conf.ebs.health.gov.on.ca` URL is conformance. Obtain the correct environment endpoint and enrollment from the service operator; verify credentials and a sanctioned synthetic validation before enabling production calls. Never validate production patient data against conformance. |
| HCV key material | `hcv.service.clientKeystore.properties`, `hcv.keystore.user`, `hcv.keystore.pass`, `TOMCAT_KEYSTORE_FILE` and other deployed TLS/keystore configuration | Inspect the actual `OnlineHCValidator`/application-server key path in this deployment. Provision the registered private key/certificate chain, correct alias and secret, readable only by the runtime account. Replace sample paths/aliases/passwords; do not log key material or secrets. Check expiry and the renewal procedure. |

Record the environment, deployed file/configuration identity, synthetic check
outcome and operator sign-off in the deployment record, without copying secrets
or clinical payloads. A successful application startup does not qualify these
external services. Refuse production traffic for an unqualified enabled
integration; retain its previous known-good configuration for recovery.

This release takes the documentation path permitted by #2943. It preserves
existing settings and deliberate local-relay/DrugRef installations; it does not
silently replace endpoints, enable integrations, or claim that repository
checks verified an operator's external configuration.
