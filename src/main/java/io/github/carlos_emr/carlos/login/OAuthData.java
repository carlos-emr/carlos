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
  /** Whether the listed permissions limit the app; true only when an operator turned scope enforcement on (#4419). */
  private boolean scopesEnforced = true;
  /** With enforcement off: true when the app is held to the legacy integration endpoints, false when it gets full access. */
  private boolean legacyRestricted = true;

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
  public boolean isScopesEnforced()     { return scopesEnforced; }
  public void setScopesEnforced(boolean b) { scopesEnforced = b; }
  public boolean isLegacyRestricted()   { return legacyRestricted; }
  public void setLegacyRestricted(boolean b) { legacyRestricted = b; }
}
