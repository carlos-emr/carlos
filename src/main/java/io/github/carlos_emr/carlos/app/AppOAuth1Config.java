/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.app;


import java.util.HashMap;
import java.util.Map;


import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;


/**
 * OAuth 1.0a authentication configuration for third-party service integration.
 *
 * <p>Holds the consumer credentials, token service endpoints, and authorization URI
 * needed to perform the OAuth 1.0a three-legged authorization flow:
 * <ol>
 *   <li>System requests a RequestToken from the third party using the consumer key and callback URL</li>
 *   <li>User is redirected to the third party's authorization page</li>
 *   <li>Upon approval, the user is redirected back with a usage key</li>
 *   <li>System exchanges the usage key for a final access token</li>
 * </ol>
 *
 * <p>Configurations can be parsed from XML documents via {@link #fromDocument(Document)}.
 *
 * @see AppAuthConfig
 * @since 2026-03-17
 */
public class AppOAuth1Config implements AppAuthConfig {
    private static final Logger logger = MiscUtils.getLogger();

    private String type = null;
    private String name = null;
    private String consumerKey = null;
    private String consumerSecret = null;
    private String requestTokenService = null;
    private String accessTokenService = null;
    private String authorizationServiceURI = null;
    private String baseURL = null;


    /**
     * {@inheritDoc}
     *
     * @return String the authentication type identifier
     */
    public String getType() {
        return type;
    }

    /**
     * {@inheritDoc}
     *
     * @return String the authentication provider name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the URL of the OAuth request token service endpoint.
     *
     * @return String the request token service URL
     */
    public String getRequestTokenService() {
        return requestTokenService;
    }

    /**
     * Returns the URL of the OAuth access token service endpoint.
     *
     * @return String the access token service URL
     */
    public String getAccessTokenService() {
        return accessTokenService;
    }

    /**
     * Returns the URI of the third-party authorization page where users grant access.
     *
     * @return String the authorization service URI
     */
    public String getAuthorizationServiceURI() {
        return authorizationServiceURI;
    }

    /**
     * Returns the OAuth consumer key used to identify this application.
     *
     * @return String the consumer key
     */
    public String getConsumerKey() {
        return consumerKey;
    }

    /**
     * Returns the base URL of the third-party service.
     *
     * @return String the base URL
     */
    public String getBaseURL() {
        return baseURL;
    }

    /**
     * Sets the base URL of the third-party service.
     *
     * @param baseURL String the base URL to set
     */
    public void setBaseURL(String baseURL) {
        this.baseURL = baseURL;
    }

    /**
     * Sets the OAuth consumer key.
     *
     * @param consumerKey String the consumer key to set
     */
    public void setConsumerKey(String consumerKey) {
        this.consumerKey = consumerKey;
    }

    /**
     * Returns the OAuth consumer secret used for signing requests.
     *
     * @return String the consumer secret
     */
    public String getConsumerSecret() {
        return consumerSecret;
    }

    /**
     * Sets the OAuth consumer secret.
     *
     * @param consumerSecret String the consumer secret to set
     */
    public void setConsumerSecret(String consumerSecret) {
        this.consumerSecret = consumerSecret;
    }

    /**
     * Parses an {@link AppOAuth1Config} from an XML string.
     *
     * @param s String the XML configuration string
     * @return AppOAuth1Config the parsed configuration
     * @throws Exception if the XML cannot be parsed
     */
    public static AppOAuth1Config fromDocument(String s) throws Exception {
        return fromDocument(XmlUtils.toDocument(s));
    }

    /**
     * Parses an {@link AppOAuth1Config} from an XML {@link Document}.
     *
     * <p>Expects the document root to contain child elements: {@code type}, {@code name},
     * {@code consumerKey}, {@code consumerSecret}, {@code requestTokenService},
     * {@code accessTokenService}, {@code authorizationServiceURI}, and {@code baseURL}.
     *
     * @param doc Document the XML document containing the OAuth configuration
     * @return AppOAuth1Config the parsed configuration
     */
    public static AppOAuth1Config fromDocument(Document doc) {
        AppOAuth1Config config = new AppOAuth1Config();

        Node rootNode = doc.getFirstChild();
        config.type = XmlUtils.getChildNodeTextContents(rootNode, "type");
        config.name = XmlUtils.getChildNodeTextContents(rootNode, "name");
        config.consumerKey = XmlUtils.getChildNodeTextContents(rootNode, "consumerKey");
        config.consumerSecret = XmlUtils.getChildNodeTextContents(rootNode, "consumerSecret");

        config.requestTokenService = XmlUtils.getChildNodeTextContents(rootNode, "requestTokenService");
        config.accessTokenService = XmlUtils.getChildNodeTextContents(rootNode, "accessTokenService");
        config.authorizationServiceURI = XmlUtils.getChildNodeTextContents(rootNode, "authorizationServiceURI");
        config.baseURL = XmlUtils.getChildNodeTextContents(rootNode, "baseURL");

        return config;
    }

    /**
     * Serializes an OAuth token and secret into an XML string.
     *
     * @param token  String the OAuth token key
     * @param secret String the OAuth token secret
     * @return String the XML representation containing the token key and secret
     * @throws Exception if XML serialization fails
     */
    public static String getTokenXML(String token, String secret) throws Exception {
        Document doc = XmlUtils.newDocument("token");

        XmlUtils.appendChildToRootIgnoreNull(doc, "key", token);
        XmlUtils.appendChildToRootIgnoreNull(doc, "secret", secret);
        String docAsString = XmlUtils.toString(doc, false);

        return docAsString;
    }


    /**
     * Deserializes an OAuth token key and secret from an XML string.
     *
     * @param str String the XML string containing {@code key} and {@code secret} elements
     * @return Map&lt;String, String&gt; a map with "key" and "secret" entries
     * @throws Exception if XML parsing fails
     */
    public static Map<String, String> getKeySecret(String str) throws Exception {
        logger.debug("token === " + str);
        Document doc = XmlUtils.toDocument(str);
        Node node = doc.getFirstChild();
        logger.error(node.getNodeName());
        String key = XmlUtils.getChildNodeTextContents(node, "key");
        String secret = XmlUtils.getChildNodeTextContents(node, "secret");
        Map<String, String> map = new HashMap<String, String>();
        map.put("key", key);
        map.put("secret", secret);
        return map;
    }


}
