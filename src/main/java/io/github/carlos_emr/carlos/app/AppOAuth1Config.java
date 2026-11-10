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


import io.github.carlos_emr.carlos.utility.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import io.github.carlos_emr.carlos.utility.OAuthConfigXml;


/*
 * Summary of how oauth1 works (assuming consumer key has been configured as pre requisite to this
 *
 * 1. User visits the app config page and wants to set up authentication with 3rd party site.
 * 2. User clicks on the "authenticate" button
 * 3. The System requests a "RequestToken" from the 3rd party, passing the 3rd party it's consumer key and it's callback URL(ie what oscar url the 3rd party should redirect too).
 * 4. The System then receives the token and redirects the 3rd party's authorization page.
 * 5. The Third party then asks the user to authorize the system for use
 * 6. If the user agrees to give access to the System they are redirected back to the system's callback page with a usage key.
 * 7. The system then uses this token to call the 3rd party system on more time to get the final accessToken.
 *
 *
 *
 *
 */
public class AppOAuth1Config implements AppAuthConfig {

    private String type = null;
    private String name = null;
    private String consumerKey = null;
    private String consumerSecret = null;
    private String requestTokenService = null;
    private String accessTokenService = null;
    private String authorizationServiceURI = null;
    private String baseURL = null;


    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getRequestTokenService() {
        return requestTokenService;
    }

    public String getAccessTokenService() {
        return accessTokenService;
    }

    public String getAuthorizationServiceURI() {
        return authorizationServiceURI;
    }

    public String getConsumerKey() {
        return consumerKey;
    }

    public String getBaseURL() {
        return baseURL;
    }

    public void setBaseURL(String baseURL) {
        this.baseURL = baseURL;
    }

    public void setConsumerKey(String consumerKey) {
        this.consumerKey = consumerKey;
    }

    public String getConsumerSecret() {
        return consumerSecret;
    }

    public void setConsumerSecret(String consumerSecret) {
        this.consumerSecret = consumerSecret;
    }

    public static AppOAuth1Config fromDocument(String s) throws Exception {
        return fromRoot(OAuthConfigXml.root(s));
    }

    public static AppOAuth1Config fromDocument(Document doc) {
        return fromRoot(OAuthConfigXml.root(doc));
    }

    private static AppOAuth1Config fromRoot(Element rootNode) {
        AppOAuth1Config config = new AppOAuth1Config();
        config.type = OAuthConfigXml.text(rootNode, "type");
        config.name = OAuthConfigXml.text(rootNode, "name");
        config.consumerKey = OAuthConfigXml.text(rootNode, "consumerKey");
        config.consumerSecret = OAuthConfigXml.text(rootNode, "consumerSecret");

        config.requestTokenService = OAuthConfigXml.text(rootNode, "requestTokenService");
        config.accessTokenService = OAuthConfigXml.text(rootNode, "accessTokenService");
        config.authorizationServiceURI = OAuthConfigXml.text(rootNode, "authorizationServiceURI");
        config.baseURL = OAuthConfigXml.text(rootNode, "baseURL");

        return config;
    }

    public static String getTokenXML(String token, String secret) throws Exception {
        Document doc = XmlUtils.newDocument("token");

        XmlUtils.appendChildToRootIgnoreNull(doc, "key", token);
        XmlUtils.appendChildToRootIgnoreNull(doc, "secret", secret);
        String docAsString = XmlUtils.toString(doc, false);

        return docAsString;
    }


    public static Map<String, String> getKeySecret(String str) throws Exception {
        Element node = OAuthConfigXml.root(str);
        String key = OAuthConfigXml.text(node, "key");
        String secret = OAuthConfigXml.text(node, "secret");
        Map<String, String> map = new HashMap<String, String>();
        map.put("key", key);
        map.put("secret", secret);
        return map;
    }


}
