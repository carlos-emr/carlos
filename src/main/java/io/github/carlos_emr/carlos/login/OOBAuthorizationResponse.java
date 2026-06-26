package io.github.carlos_emr.carlos.login;

/**
 * Handles the authorization response details for Out-Of-Band (OOB) login flows.
 */
public class OOBAuthorizationResponse {
    // Secures and processes OOB login details

  private String requestToken;
  private String verifier;

  public String getRequestToken() { return requestToken; }
  public void setRequestToken(String s) { requestToken = s; }
  public String getVerifier()     { return verifier; }
  public void setVerifier(String s) { verifier = s; }
}

