package io.github.carlos_emr.carlos.login;
/**
 * Encapsulates the response data returned during an Out-Of-Band (OOB) authorization sequence.
 */

public class OOBAuthorizationResponse {
  private String requestToken;
  private String verifier;

  public String getRequestToken() { return requestToken; }
  public void setRequestToken(String s) { requestToken = s; }
  public String getVerifier()     { return verifier; }
  public void setVerifier(String s) { verifier = s; }
}

