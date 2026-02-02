// src/main/java/oscar/login/OAuthData.java
package io.github.carlos_emr.carlos.login;

import java.util.Collections;
import java.util.List;

public class OAuthData {
  private String applicationName;
  private String applicationURI;
  private String replyTo;
  private String authenticityToken;
  private String oauthToken;
  private List<String> permissions = Collections.emptyList();

  // getters & setters
  public String getApplicationName()    { return applicationName; }
  public void setApplicationName(String s) { applicationName = s; }
  public String getApplicationURI()     { return applicationURI; }
  public void setApplicationURI(String s) { applicationURI = s; }
  public String getReplyTo()            { return replyTo; }
  public void setReplyTo(String s) { replyTo = s; }
  public String getAuthenticityToken()  { return authenticityToken; }
  public void setAuthenticityToken(String s) { authenticityToken = s; }
  public String getOauthToken()         { return oauthToken; }
  public void setOauthToken(String s) { oauthToken = s; }
  public List<String> getPermissions()  { return permissions; }
  public void setPermissions(List<String> l) { permissions = l; }
}
