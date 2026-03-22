package io.github.carlos_emr.carlos.login;

/**
 * Data transfer object for OAuth 1.0a out-of-band (OOB) authorization responses.
 *
 * <p>Carries the request token and verifier code returned to the user after
 * they authorize an OAuth consumer application via an out-of-band flow
 * (i.e., the verifier is displayed to the user rather than sent via callback URL).
 *
 * @see OscarOAuthDataProvider#finalizeAuthorization(io.github.carlos_emr.carlos.webserv.oauth.RequestToken)
 * @since 2026-03-17
 */
public class OOBAuthorizationResponse {
  private String requestToken;
  private String verifier;

  /** @return String the OAuth request token */
  public String getRequestToken() { return requestToken; }

  /** @param s String the OAuth request token */
  public void setRequestToken(String s) { requestToken = s; }

  /** @return String the OAuth verifier code */
  public String getVerifier()     { return verifier; }

  /** @param s String the OAuth verifier code */
  public void setVerifier(String s) { verifier = s; }
}

