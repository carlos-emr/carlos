// src/main/java/oscar/login/OAuthData.java
package io.github.carlos_emr.carlos.login;

import java.util.Collections;
import java.util.List;

/**
 * Data transfer object that holds OAuth authorization page display data.
 *
 * <p>Used to present the OAuth authorization form to the provider, showing
 * the requesting application's name, URI, requested permissions, and the
 * token and authenticity token needed to complete the authorization.
 *
 * @see OscarOAuthDataProvider
 * @since 2026-03-17
 */
public class OAuthData {
  private String applicationName;
  private String applicationURI;
  private String replyTo;
  private String authenticityToken;
  private String oauthToken;
  private List<String> permissions = Collections.emptyList();

  /** @return String the name of the requesting OAuth application */
  public String getApplicationName()    { return applicationName; }

  /** @param s String the name of the requesting OAuth application */
  public void setApplicationName(String s) { applicationName = s; }

  /** @return String the URI of the requesting OAuth application */
  public String getApplicationURI()     { return applicationURI; }

  /** @param s String the URI of the requesting OAuth application */
  public void setApplicationURI(String s) { applicationURI = s; }

  /** @return String the reply-to URL for the authorization response */
  public String getReplyTo()            { return replyTo; }

  /** @param s String the reply-to URL for the authorization response */
  public void setReplyTo(String s) { replyTo = s; }

  /** @return String the CSRF authenticity token for the authorization form */
  public String getAuthenticityToken()  { return authenticityToken; }

  /** @param s String the CSRF authenticity token for the authorization form */
  public void setAuthenticityToken(String s) { authenticityToken = s; }

  /** @return String the OAuth request token being authorized */
  public String getOauthToken()         { return oauthToken; }

  /** @param s String the OAuth request token being authorized */
  public void setOauthToken(String s) { oauthToken = s; }

  /** @return List of String permission/scope identifiers requested by the application */
  public List<String> getPermissions()  { return permissions; }

  /** @param l List of String permission/scope identifiers requested by the application */
  public void setPermissions(List<String> l) { permissions = l; }
}
