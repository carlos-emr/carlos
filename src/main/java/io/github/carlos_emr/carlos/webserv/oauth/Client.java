/**
 * File: Client.java
 *
 * Purpose:
 *   Domain model representing an OAuth 1.0a client (consumer application).
 *   Encapsulates credentials, identifying information, and callback URIs.
 *
 * Responsibilities:
 *   • Store the client’s consumer key/secret pair used for OAuth signing.
 *   • Provide metadata such as application name, URI, and optional loginName.
 *   • Track callback URI used during the authorization flow.
 *   • Expose simple getters/setters for use in token and verification services.
 *
 * Context / Why Added:
 *   Used during signature verification and token issuance to validate
 *   client credentials and associate issued tokens with the correct
 *   consumer application.
 *
 * Notes:
 *   • consumerSecret must be stored securely and never logged.
 *   • Optional loginName field can be used to map to a providerNo in OSCAR.
 *   • Supports callback URI overrides, allowing per-client authorization
 *     redirect handling.
 */

package io.github.carlos_emr.carlos.webserv.oauth;

/**
 * Domain model representing an OAuth 1.0a client (consumer application).
 *
 * <p>Contains the consumer key, secret, application name, and optional callback URI
 * for per-client authorization redirect handling.
 *
 * @since 2026-01-18
 */
public class Client {
    private String consumerKey;
    private String secret;
    private String name;
    private String uri;
    private String loginName; // optional, used for storing providerNo
    private String callbackUri;

    public Client(String consumerKey, String secret, String name, String uri) {
        this.consumerKey = consumerKey;
        this.secret = secret;
        this.name = name;
        this.uri = uri;
    }

    public String getConsumerKey() {
        return consumerKey;
    }

    public String getSecret() {
        return secret;
    }

    public String getName() {
        return name;
    }

    public String getUri() {
        return uri;
    }

    public String getLoginName() {
        return loginName;
    }

    public void setLoginName(String loginName) {
        this.loginName = loginName;
    }

    public String getCallbackUri() { 
        return callbackUri; 
    }

    public void setCallbackUri(String v) { 
        this.callbackUri = v; 
    }

}
