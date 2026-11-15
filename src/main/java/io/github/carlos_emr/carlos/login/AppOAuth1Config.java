/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.login;

import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Inbound OAuth 1.0a verifier configuration.
 * The app-package AppAuthConfig describes outbound third-party endpoints and has a separate public API.
 */
public class AppOAuth1Config {
    private static final Logger logger = MiscUtils.getLogger();
    private String consumerKey;
    private String consumerSecret;
    private String baseUrl;
    private String callbackURI;
    private String applicationURI;
    private List<String> scopes;

    public String getConsumerKey() { return consumerKey; }
    public void setConsumerKey(String consumerKey) { this.consumerKey = consumerKey; }
    
    public String getConsumerSecret() { return consumerSecret; }
    public void setConsumerSecret(String consumerSecret) { this.consumerSecret = consumerSecret; }
    
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    
    public String getCallbackURI() { return callbackURI; }
    public void setCallbackURI(String callbackURI) { this.callbackURI = callbackURI; }
    
    public String getApplicationURI() { return applicationURI; }
    public void setApplicationURI(String applicationURI) { this.applicationURI = applicationURI; }
    
    public List<String> getScopes() { return scopes; }
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
        
        if (!(doc instanceof org.w3c.dom.Document) && doc.toString().trim().isEmpty()) {
            return null;
        }
        
        try {
            var root = io.github.carlos_emr.carlos.utility.OAuthConfigXml.root(doc);
            AppOAuth1Config config = new AppOAuth1Config();
            config.setConsumerKey(io.github.carlos_emr.carlos.utility.OAuthConfigXml.trimmedText(root, "consumerKey"));
            config.setConsumerSecret(io.github.carlos_emr.carlos.utility.OAuthConfigXml.trimmedText(root, "consumerSecret"));
            config.setBaseUrl(io.github.carlos_emr.carlos.utility.OAuthConfigXml.trimmedText(root, "baseUrl"));
            config.setCallbackURI(io.github.carlos_emr.carlos.utility.OAuthConfigXml.trimmedText(root, "callbackURI"));
            config.setApplicationURI(io.github.carlos_emr.carlos.utility.OAuthConfigXml.trimmedText(root, "applicationURI"));
            String scopesStr = io.github.carlos_emr.carlos.utility.OAuthConfigXml.trimmedText(root, "scopes");
            if (scopesStr != null && !scopesStr.isEmpty()) config.setScopes(Arrays.asList(scopesStr.split(",")));
            return config;
        } catch (IllegalArgumentException invalid) {
            logger.warn("OAuth configuration XML could not be parsed");
            return null;
        }
    }
}
