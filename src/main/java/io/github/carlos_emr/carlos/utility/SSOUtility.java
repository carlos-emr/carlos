//CHECKSTYLE:OFF
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
 */
package io.github.carlos_emr.carlos.utility;

import org.apache.http.client.utils.URIBuilder;
import io.github.carlos_emr.OscarProperties;

import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;


public final class SSOUtility {

    private static final OscarProperties oscarProperties = OscarProperties.getInstance();

    public enum SSO_SETTING {
        sso_entity_id,
        sso_url_login,
        sso_url_logout,
        sso_entity_metadata,
        sso_idp_x509cert,
        sso_sp_entity_id,
        sp_encryption_key
    }

    public static String getLoginRedirectUrl(String context) throws URISyntaxException {
        URIBuilder ssoUrl = getRedirectUrl(context);
        ssoUrl.addParameter("method", "ssoLogin");
        return ssoUrl.toString();
    }

    public static String getLogoutRedirectUrl(String context) throws URISyntaxException {
        URIBuilder ssoUrl = getRedirectUrl(context);
        ssoUrl.addParameter("method", "ssoLogout");
        return ssoUrl.toString();
    }

    /**
     * Build the base redirect url from the given context path.
     * Do not add parameters to this path. It is used only for validation
     *
     * @return URIBuilder
     */
    public static URIBuilder getRedirectUrl(String context) throws URISyntaxException {
        /*
         * Callback URL for OSCAR is #!/ssoLogin.do?loginStart={timestamp}&method={method}
         */
        URIBuilder ssoUrl = new URIBuilder(context);

        /* all call back links MUST BE secured. This accounts for secure connections by
         * proxy.  ie: NGINX, HaProxy etc...
         */
        ssoUrl.setScheme("https");
        return ssoUrl;
    }

    /**
     * Get SSO presets from Oscar.properties
     * Values are mapped directly to standard OneLogin SAML properties settings
     *
     * @return Map of preset values
     */
    public static Map<SSO_SETTING, String> getSSOPresetsFromOscarProperties() {

        //TODO this can be overriden with values accepted from the user interface via the Properties table.
        Map<SSO_SETTING, String> presets = new HashMap<>();
        String idp_login = oscarProperties.getProperty("sso.url.login");
        String idp_logout = oscarProperties.getProperty("sso.url.logout");
        String idp_entity_id = oscarProperties.getProperty("sso.entity.id");
        String idp_metadata = oscarProperties.getProperty("sso.entity.metadata");
        String idp_x509cert = oscarProperties.getProperty("sso.idp.x509cert");
        String sp_entity_id = oscarProperties.getProperty("sso.sp.entity.id");
        String sp_encryption_key = oscarProperties.getProperty("sso.encryptionKey");

        // use the global variable SSO_SETTING only.
        presets.put(SSO_SETTING.sso_entity_id, idp_entity_id);
        presets.put(SSO_SETTING.sso_url_login, idp_login);
        presets.put(SSO_SETTING.sso_url_logout, idp_logout);
        presets.put(SSO_SETTING.sso_entity_metadata, idp_metadata);
        presets.put(SSO_SETTING.sso_idp_x509cert, idp_x509cert);
        presets.put(SSO_SETTING.sso_sp_entity_id, sp_entity_id);
        presets.put(SSO_SETTING.sp_encryption_key, sp_encryption_key);

        return presets;
    }

    public static boolean isSSOEnabled() {
        return oscarProperties.getBooleanProperty("sso.enabled", "true");
    }

}
