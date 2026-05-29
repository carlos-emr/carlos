package io.github.carlos_emr.carlos.login;

/**
 * Provides core structure for OOBAuthorizationResponse.
 */
public class OOBAuthorizationResponse {
  private String requestToken;
  private String verifier;


    // Getrequesttoken is exposed here to satisfy the external component interface contract without exposing internal state.
    public String getRequestToken() { return requestToken; }
  public void setRequestToken(String s) { requestToken = s; }
  public String getVerifier()     { return verifier; }
  public void setVerifier(String s) { verifier = s; }
}

