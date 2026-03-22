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

/*
 * Written by Brandon Aubie
 * brandon@aubie.ca
 */


package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.carlos.commn.dao.ServiceRequestTokenDao;
import io.github.carlos_emr.carlos.commn.model.ServiceRequestToken;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.utility.SpringUtils;


/**
 * Utility that associates an authenticated provider's session with an OAuth request token.
 *
 * <p>After a provider logs in and authorizes an OAuth consumer, this class updates the
 * request token record with the provider's number so the token can be exchanged
 * for an access token that carries the provider's identity.
 *
 * @see OscarOAuthDataProvider
 * @since 2026-03-17
 */
public class OAuthSessionMerger {

    /**
     * Merges the current provider session with the OAuth request token identified
     * by the "oauth_token" request parameter.
     *
     * @param request HttpServletRequest the request containing the oauth_token parameter and the provider session
     * @return boolean true if the request token was found and updated with the provider number, false otherwise
     */
    public static boolean mergeSession(HttpServletRequest request) {

        String proNo = (String) request.getSession().getAttribute("user");
        ServiceRequestTokenDao serviceRequestTokenDao = SpringUtils.getBean(ServiceRequestTokenDao.class);
        ServiceRequestToken srt = serviceRequestTokenDao.findByTokenId(request.getParameter("oauth_token"));
        if (srt != null) {
            srt.setProviderNo(proNo);
            serviceRequestTokenDao.merge(srt);
            return true;
        }
        return false;
    }

}
