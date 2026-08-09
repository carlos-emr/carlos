package io.github.carlos_emr.carlos.login;

/**
 * Model representing the response for Out-Of-Band (OOB) authorization flows, such as MFA challenges during login.
 */
public class OOBAuthorizationResponse {
  private String requestToken;
  private String verifier;

  public String getRequestToken() { return requestToken; }
  public void setRequestToken(String s) { requestToken = s; }
  public String getVerifier()     { return verifier; }
  public void setVerifier(String s) { verifier = s; }
}

