// src/main/java/oscar/login/AppOAuth1Config.java
package io.github.carlos_emr.carlos.login;

import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Configuration class for OAuth 1.0a consumer applications in CARLOS EMR.
 *
 * <p>Holds the consumer key, secret, base URL, callback URI, application URI,
 * and scopes for an OAuth 1.0a client registration. Instances can be created
 * programmatically or parsed from an XML configuration document via {@link #fromDocument(Object)}.
 *
 * @see OscarOAuthDataProvider
 * @see OscarRequestTokenService
 * @since 2026-03-17
 */
public class AppOAuth1Config {
    private static final Logger logger = MiscUtils.getLogger();
    private String consumerKey;
    private String consumerSecret;
    private String baseUrl;
    private String callbackURI;
    private String applicationURI;
    private List<String> scopes;

    /** @return String the OAuth consumer key */
    public String getConsumerKey() { return consumerKey; }

    /** @param consumerKey String the OAuth consumer key */
    public void setConsumerKey(String consumerKey) { this.consumerKey = consumerKey; }

    /** @return String the OAuth consumer secret */
    public String getConsumerSecret() { return consumerSecret; }

    /** @param consumerSecret String the OAuth consumer secret */
    public void setConsumerSecret(String consumerSecret) { this.consumerSecret = consumerSecret; }

    /** @return String the base URL of the OAuth provider */
    public String getBaseUrl() { return baseUrl; }

    /** @param baseUrl String the base URL of the OAuth provider */
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    /** @return String the OAuth callback URI */
    public String getCallbackURI() { return callbackURI; }

    /** @param callbackURI String the OAuth callback URI */
    public void setCallbackURI(String callbackURI) { this.callbackURI = callbackURI; }

    /** @return String the application URI for the OAuth client */
    public String getApplicationURI() { return applicationURI; }

    /** @param applicationURI String the application URI for the OAuth client */
    public void setApplicationURI(String applicationURI) { this.applicationURI = applicationURI; }

    /** @return List of String scope identifiers */
    public List<String> getScopes() { return scopes; }

    /** @param scopes List of String scope identifiers */
    public void setScopes(List<String> scopes) { this.scopes = scopes; }

    /**
     * Creates an AppOAuth1Config from a configuration document.
     * 
     * @param doc The configuration document (XML string format)
     * @return AppOAuth1Config instance or null if parsing fails
     */
    public static AppOAuth1Config fromDocument(Object doc) {
        if (doc == null) {
            return null;
        }
        
        String configXml = doc.toString();
        if (configXml.trim().isEmpty()) {
            return null;
        }
        
        AppOAuth1Config config = new AppOAuth1Config();
        
        try {
            // Parse XML-like configuration format
            config.setConsumerKey(extractXmlValue(configXml, "consumerKey"));
            config.setConsumerSecret(extractXmlValue(configXml, "consumerSecret"));
            config.setBaseUrl(extractXmlValue(configXml, "baseUrl"));
            config.setCallbackURI(extractXmlValue(configXml, "callbackURI"));
            config.setApplicationURI(extractXmlValue(configXml, "applicationURI"));
            
            // Parse scopes if present
            String scopesStr = extractXmlValue(configXml, "scopes");
            if (scopesStr != null && !scopesStr.trim().isEmpty()) {
                config.setScopes(Arrays.asList(scopesStr.split(",")));
            }
            
            return config;
        } catch (Exception e) {
            logger.error("Error parsing OAuth config document: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Extracts value from simple XML-like format: <tag>value</tag>
     */
    private static String extractXmlValue(String xml, String tagName) {
        String startTag = "<" + tagName + ">";
        String endTag = "</" + tagName + ">";
        
        int startIndex = xml.indexOf(startTag);
        if (startIndex == -1) {
            return null;
        }
        
        int valueStart = startIndex + startTag.length();
        int endIndex = xml.indexOf(endTag, valueStart);
        if (endIndex == -1) {
            return null;
        }
        
        return xml.substring(valueStart, endIndex).trim();
    }
}
