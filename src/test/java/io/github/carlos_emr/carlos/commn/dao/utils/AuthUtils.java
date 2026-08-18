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
package io.github.carlos_emr.carlos.commn.dao.utils;

import java.util.Locale;

import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import io.github.carlos_emr.carlos.util.ConversionUtils;

public class AuthUtils {

    public static LoggedInInfo initLoginContext() {
        HttpSession session = null;
        Facility currentFacility = new Facility("Test Facility", "Test Facility Desription");
        Provider loggedInProvider = new Provider("-1", "Test Provider Last Name", "String Provider Type", "M", "Speciality", "T");
        String initiatingCode = "INIT_CODE";
        Security security = new Security();
        security.setId(-1);
        security.setUserName("Test User Name");
        security.setPassword("Test Password");
        security.setProviderNo("-1");
        security.setPin("Test Pin");
        security.setBExpireset(0);
        security.setDateExpiredate(ConversionUtils.fromDateString("2099-01-01"));
        security.setBLocallockset(0);
        security.setBRemotelockset(0);
        Locale locale = Locale.getDefault();

        LoggedInInfo info = new LoggedInInfo();
        info.setCurrentFacility(currentFacility);
        info.setLoggedInProvider(loggedInProvider);
        info.setInitiatingCode(initiatingCode);
        info.setLoggedInSecurity(security);
        info.setLocale(locale);
        info.setSession(session);

        return (info);
    }

}
