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

import org.junit.Test;
import org.springframework.util.Assert;
import io.github.carlos_emr.carlos.utility.SSOUtility;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;

public class SSOUtilityTest {

    @Test
    public void getLoginRedirectUrl() {
        System.out.println("Test: getLoginRedirectUrl");
        String context = "http://localhost/oscar/ssoLogin.do";
        try {
            System.out.println(SSOUtility.getLoginRedirectUrl(context));
            URL url = new URL(SSOUtility.getLoginRedirectUrl(context));
            Assert.isTrue(url.getQuery().contains("ssoLogin"));
            Assert.isTrue(url.getPath().contains("ssoLogin.do"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void getLogoutRedirectUrl() {
        System.out.println("Test: getLogoutRedirectUrl");
        String context = "http://localhost/oscar/ssoLogin.do";
        try {
            URL url = new URL(SSOUtility.getLogoutRedirectUrl(context));
            Assert.isTrue(url.getQuery().contains("ssoLogout"));
            Assert.isTrue(url.getPath().contains("ssoLogin.do"));
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void getSSOPresetsFromOscarProperties() {
        System.out.println("Test: getSSOPresetsFromOscarProperties");
        Map<SSOUtility.SSO_SETTING, String> settings = SSOUtility.getSSOPresetsFromOscarProperties();
        Assert.isTrue(settings.containsKey(SSOUtility.SSO_SETTING.sso_url_login));
        Assert.isTrue(settings.containsKey(SSOUtility.SSO_SETTING.sso_url_logout));
        Assert.isTrue(settings.containsKey(SSOUtility.SSO_SETTING.sso_entity_id));
        Assert.isTrue(settings.containsKey(SSOUtility.SSO_SETTING.sso_entity_metadata));
        Assert.isTrue(settings.containsKey(SSOUtility.SSO_SETTING.sso_idp_x509cert));
    }

    @Test
    public void isSSOEnabled() {
        System.out.println("Test: isSSOEnabled");
        Assert.isInstanceOf(Boolean.class, SSOUtility.isSSOEnabled());
    }
}