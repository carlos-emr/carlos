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
package io.github.carlos_emr.carlos.managers;

import com.onelogin.saml2.Auth;
import com.onelogin.saml2.settings.Saml2Settings;
import com.onelogin.saml2.settings.SettingsBuilder;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.FacilityDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderPreferenceDao;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderPreference;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SSOUtility;
import io.github.carlos_emr.carlos.utility.SessionConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import io.github.carlos_emr.carlos.login.LoginCheckLogin;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;


@Service
public class SsoAuthenticationManager implements Serializable {

    private static final Logger logger = MiscUtils.getLogger();

    private LoginCheckLogin loginCheck;

    @Autowired
    private ProviderDao providerDao;

    @Autowired
    private ProviderPreferenceDao providerPreferenceDao;

    @Autowired
    private FacilityDao facilityDao;

    private static final String samlPropertiesFile = "/onelogin.saml.properties";

    /**
     * Manager method that builds a settings object for an IDP authentication request.
     * This method is specific for use with OneLogin Tools
     *
     * @return Saml2Settings object for use with OneLogin tools.
     * @throws IOException
     */
    public Saml2Settings buildAuthenticationRequestSettings(String user_email, String context) throws IOException, URISyntaxException {

        Properties samlData = new Properties();
        try (InputStream inputStream = getClass().getResourceAsStream(samlPropertiesFile)) {
            samlData.load(inputStream);

            /*
             * Set service provider data:
             * Data about OSCAR's identifiers and links for SSO
             * See onelogin.saml.properties file in resources for details.
             */
            samlData.put("onelogin.saml2.sp.assertion_consumer_service.url",
                    new URL(SSOUtility.getLoginRedirectUrl(context)));
            samlData.put("onelogin.saml2.sp.single_logout_service.url",
                    new URL(SSOUtility.getLogoutRedirectUrl(context)));
        }

        // do something with user_email here.

        /*
         * Set identity provider data:
         * Data about the IDP OSCAR will use for SSO authentication
         */
        Map<SSOUtility.SSO_SETTING, String> sso_presets = SSOUtility.getSSOPresetsFromOscarProperties();
        samlData.put("onelogin.saml2.sp.entityid", sso_presets.get(SSOUtility.SSO_SETTING.sso_sp_entity_id));
        samlData.put("onelogin.saml2.idp.entityid", sso_presets.get(SSOUtility.SSO_SETTING.sso_entity_id));
        samlData.put("onelogin.saml2.idp.single_sign_on_service.url", sso_presets.get(SSOUtility.SSO_SETTING.sso_url_login));
        samlData.put("onelogin.saml2.idp.single_logout_service.url", sso_presets.get(SSOUtility.SSO_SETTING.sso_url_logout));
        samlData.put("onelogin.saml2.idp.x509cert", sso_presets.get(SSOUtility.SSO_SETTING.sso_idp_x509cert));

        SettingsBuilder builder = new SettingsBuilder();
        return builder.fromValues(typeCast(samlData)).build();
    }

    /**
     * @return Map of attributes for use in new login session
     */
    public Map<String, Object> checkSSOLogin(Auth auth) {
        Map<String, List<String>> attributes = auth.getAttributes();
        String nameId = auth.getNameId();
        String nameIdFormat = auth.getNameIdFormat();
        String sessionIndex = auth.getSessionIndex();
        String nameidNameQualifier = auth.getNameIdNameQualifier();
        String nameidSPNameQualifier = auth.getNameIdSPNameQualifier();

        /* add session attributes;
         * NOTE: these are the ONLY attributes we add to a session.
         * NO provider preferences here.
         */
        logger.debug("Setting session SSO attributes: ");
        Collection<String> keys = attributes.keySet();
        for (String name : keys) {
            logger.debug(name);
            List<String> values = attributes.get(name);
            for (String value : values) {
                logger.debug(" - " + value);
            }
        }

        Map<String, Object> sessionData = checkLogin(new HashMap<>(), nameId);
        if (sessionData != null && !sessionData.isEmpty()) {
            sessionData.put("attributes", attributes);
            sessionData.put("nameId", nameId);
            sessionData.put("nameIdFormat", nameIdFormat);
            sessionData.put("sessionIndex", sessionIndex);
            sessionData.put("nameidNameQualifier", nameidNameQualifier);
            sessionData.put("nameidSPNameQualifier", nameidSPNameQualifier);

            // if the nameId is in an email format
            logger.debug("SSO email: " + nameId);
            sessionData.put("oneIdEmail", nameId);

            // if in token format
            logger.debug("SSO token: " + nameId);
            sessionData.put("oneid_token", nameId);
        }
        return sessionData;
    }

    public Map<String, Object> checkLogin(Map<String, Object> sessionData, String[] authenticationParams) {
        String[] providerInformation = checkPlainTextLogin(authenticationParams);
        return createSession(sessionData, providerInformation);
    }

    private Map<String, Object> checkLogin(Map<String, Object> sessionData, String nameId) {
        String[] providerInformation = checkSSOLogin(nameId);
        return createSession(sessionData, providerInformation);
    }

    /**
     * Validate the user and then return valid session data.
     * Null data if user does not authenticate.
     *
     * @param sessionData         new or existing hashmap
     * @param providerInformation data of the provider profile
     * @return session data or NULL
     */
    private Map<String, Object> createSession(Map<String, Object> sessionData, String[] providerInformation) {

        if (providerInformation != null && providerInformation.length > 0) {

            logger.debug("SSO login confirmed with provider info: " + Arrays.toString(providerInformation));
            String providerNo = providerInformation[0];
            sessionData.put("user", providerNo);
            sessionData.put("userfirstname", providerInformation[1]);
            sessionData.put("userlastname", providerInformation[2]);
            sessionData.put("userrole", providerInformation[4]);
            sessionData.put("expired_days", providerInformation[5]);
            sessionData.put("fullSite", "true");

            // only the provider class info here.  Nothing more.
            sessionData.put(SessionConstants.LOGGED_IN_PROVIDER, getProvider(providerInformation[0]));

            // this will set ONLY if the user login checks out from ssoAuthenticationManager.checkLogin(nameId)
            sessionData.put(SessionConstants.LOGGED_IN_SECURITY, getSecurity());

            // provider preferences.  Let's stop putting this into session
            setUserPreferences(sessionData, providerNo);

            // not sure if this is needed yet
            setFacilityInformation(sessionData, providerNo);

            // determines if this user should be locked out or not.
            // updateLogin("", providerInformation[0]);
        }

        return sessionData;
    }


    /**
     * Check authentication by plain text credentials
     *
     * @param authenticationParams [username, password, pin, ip]
     * @return
     */
    private String[] checkPlainTextLogin(String[] authenticationParams) {

        /* short circuit any null or empty values saves processing power
         * at least the username and password is required: length < 2
         */
        if (authenticationParams == null || authenticationParams.length < 2) {
            loginCheck = null;
            return null;
        }

        /* otherwise validate that the SSO authentication request is
         * for a valid user.
         */
        loginCheck = new LoginCheckLogin();
        return loginCheck.auth(authenticationParams[0], authenticationParams[1], authenticationParams[2], authenticationParams[3]);
    }

    /**
     * Check login credentials specific to SSO by the SSO NameId parameter.
     *
     * @param nameId
     * @return
     */
    private String[] checkSSOLogin(String nameId) {

        /* short circuit any null or empty values saves processing power
         */
        if (nameId == null || nameId.isEmpty()) {
            loginCheck = null;
            return null;
        }

        /* otherwise validate that the SSO authentication request is
         * for a valid user.
         */
        loginCheck = new LoginCheckLogin();
        return loginCheck.ssoAuth(nameId);
    }

    /**
     * @Deprecated trying to avoid putting this data into the session.
     */
    private Map<String, Object> setUserPreferences(Map<String, Object> sessionData, String providerNo) {
        ProviderPreference providerPreferences = providerPreferenceDao.find(providerNo);

        if (providerPreferences == null) {
            providerPreferences = new ProviderPreference();
        }

        sessionData.put(SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE, providerPreferences);
        sessionData.put("starthour", providerPreferences.getStartHour().toString());
        sessionData.put("endhour", providerPreferences.getEndHour().toString());
        sessionData.put("everymin", providerPreferences.getEveryMin().toString());
        sessionData.put("groupno", providerPreferences.getMyGroupNo());

        return sessionData;
    }

    private Map<String, Object> setFacilityInformation(Map<String, Object> sessionData, String providerNo) {
        List<Integer> facilityIds = providerDao.getFacilityIds(providerNo);
        if (facilityIds != null && facilityIds.size() == 1) {
            // only one facility can be used with SSO auth.
            Facility facility = facilityDao.find(facilityIds.get(0));
            sessionData.put("currentFacility", facility);
        }
        return sessionData;
    }

    public void updateLogin(String username, String ipAddress) {
        logger.debug("Updating login list: username " + username + " ipAddress: " + ipAddress);
        loginCheck.updateLoginList(username, ipAddress);
    }

    public Provider getProvider(String providerNumber) {
        return providerDao.getProvider(providerNumber);
    }

    public Security getSecurity() {
        return loginCheck.getSecurity();
    }

    private static HashMap<String, Object> typeCast(Properties properties) {
        Map propertiesMap = properties;
        Map<String, Object> propertiesMapCast = (Map<String, Object>) propertiesMap;
        return new HashMap<>(propertiesMapCast);
    }

}
