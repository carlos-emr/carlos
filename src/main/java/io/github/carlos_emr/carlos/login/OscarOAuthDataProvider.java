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
package io.github.carlos_emr.carlos.login;

import java.io.IOException;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.ServiceAccessTokenDao;
import io.github.carlos_emr.carlos.commn.dao.ServiceClientDao;
import io.github.carlos_emr.carlos.commn.dao.ServiceRequestTokenDao;
import io.github.carlos_emr.carlos.commn.model.ServiceAccessToken;
import io.github.carlos_emr.carlos.commn.model.ServiceClient;
import io.github.carlos_emr.carlos.commn.model.ServiceRequestToken;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.webserv.oauth.AccessToken;
import io.github.carlos_emr.carlos.webserv.oauth.Client;
// OAuth 1.0a - replaced cxf OAuthServiceException. 
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1Exception;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1Permission;
import io.github.carlos_emr.carlos.webserv.oauth.RequestToken;
import io.github.carlos_emr.carlos.webserv.oauth.RequestTokenRegistration;
import io.github.carlos_emr.carlos.webserv.oauth.UserSubject;

import java.util.*;

@Component
@Transactional
public class OscarOAuthDataProvider {

    private final org.apache.logging.log4j.Logger logger = MiscUtils.getLogger();

    @Autowired private ServiceRequestTokenDao serviceRequestTokenDao;
    @Autowired private ServiceAccessTokenDao serviceAccessTokenDao;
    @Autowired private ServiceClientDao serviceClientDao;

    public Client getClient(String consumerKey) {
        logger.debug("getClient({})", consumerKey);
        ServiceClient sc = serviceClientDao.findByKey(consumerKey);
        if (sc != null) {
            return new Client(sc.getKey(), sc.getSecret(), sc.getName(), sc.getUri());
        }
        return null;
    }

    public RequestToken createRequestToken(RequestTokenRegistration reg) {
        logger.debug("createRequestToken() called");
        String tokenId = UUID.randomUUID().toString();
        String tokenSecret = UUID.randomUUID().toString();

        RequestToken rt = new RequestToken(reg.getClient(), tokenId, tokenSecret);
        List<OAuth1Permission> perms = new ArrayList<>();
        StringBuilder scopeStr = new StringBuilder();

        if (reg.getScopes() != null) {
            for (String scope : reg.getScopes()) {
                perms.add(new OAuth1Permission(scope, scope));
                scopeStr.append(scope).append(" ");
            }
        }
        rt.setScopes(perms);
        rt.setCallback(reg.getCallback());

        ServiceRequestToken srt = new ServiceRequestToken();
        srt.setCallback(rt.getCallback());
        srt.setClientId(serviceClientDao.findByKey(rt.getClient().getConsumerKey()).getId());
        srt.setDateCreated(new Date());
        srt.setTokenId(rt.getTokenKey());
        srt.setTokenSecret(rt.getTokenSecret());
        srt.setScopes(scopeStr.toString().trim());
        serviceRequestTokenDao.persist(srt);

        return rt;
    }

    public RequestToken getRequestToken(String tokenId) {
        ServiceRequestToken srt = serviceRequestTokenDao.findByTokenId(tokenId);
        if (srt == null) return null;

        // expire after 1 hour
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR, -1);
        if (srt.getDateCreated().before(cal.getTime())) {
            serviceRequestTokenDao.remove(srt);
            return null;
        }

        ServiceClient sc = serviceClientDao.find(srt.getClientId());
        Client client = new Client(sc.getKey(), sc.getSecret(), sc.getName(), sc.getUri());

        RequestToken rt = new RequestToken(client, srt.getTokenId(), srt.getTokenSecret());
        List<OAuth1Permission> perms = new ArrayList<>();
        for (String scope : srt.getScopes().split(" ")) {
            perms.add(new OAuth1Permission(scope, scope));
        }
        rt.setScopes(perms);
        rt.setCallback(srt.getCallback());
        rt.setVerifier(srt.getVerifier());

        return rt;
    }

    // public String finalizeAuthorization(Token data) throws OAuthServiceException {
    //     logger.debug("finalizeAuthorization() called");
    //     RequestToken requestToken = data.getToken();
    //     requestToken.setVerifier(UUID.randomUUID().toString());
    //     ServiceRequestToken srt = serviceRequestTokenDao.findByTokenId(requestToken.getTokenKey());
    //     if (srt != null) {
    //         srt.setVerifier(requestToken.getVerifier());
    //         serviceRequestTokenDao.merge(srt);
    //     }
    //     return requestToken.getVerifier();
    // }


    public AccessToken createAccessToken(RequestToken requestToken) {
        ServiceRequestToken srt = serviceRequestTokenDao.findByTokenId(requestToken.getTokenKey());
        if (srt == null) throw new OAuth1Exception(401, "Invalid request token");

        String accessTokenId = UUID.randomUUID().toString();
        String tokenSecret = UUID.randomUUID().toString();
        long issuedAt = System.currentTimeMillis() / 1000;

        AccessToken at = new AccessToken(requestToken.getClient(), accessTokenId, tokenSecret, 3600, issuedAt);
        at.setSubject(new UserSubject(srt.getProviderNo(), new ArrayList<>()));
        at.setScopes(requestToken.getScopes());

        ServiceAccessToken sat = new ServiceAccessToken();
        ServiceClient sc = serviceClientDao.findByKey(requestToken.getClient().getConsumerKey());
        sat.setClientId(sc.getId());
        sat.setDateCreated(new Date());
        sat.setIssued(issuedAt);
        sat.setLifetime(3600);
        sat.setTokenId(accessTokenId);
        sat.setTokenSecret(tokenSecret);
        sat.setProviderNo(srt.getProviderNo());
        sat.setScopes(String.join(" ", requestToken.getScopes().stream()
            .map(OAuth1Permission::getPermission).toArray(String[]::new)));

        serviceAccessTokenDao.persist(sat);
        serviceRequestTokenDao.remove(srt);

        return at;
    }

    public AccessToken getAccessToken(String tokenId) {
        ServiceAccessToken sat = serviceAccessTokenDao.findByTokenId(tokenId);
        if (sat == null) return null;

        ServiceClient sc = serviceClientDao.find(sat.getClientId());
        Client client = getClient(sc.getKey());

        AccessToken at = new AccessToken(client, sat.getTokenId(), sat.getTokenSecret(),
            sat.getLifetime(), sat.getIssued());
        at.setSubject(new UserSubject(sat.getProviderNo(), new ArrayList<>()));

        List<OAuth1Permission> perms = new ArrayList<>();
        for (String scope : sat.getScopes().split(" ")) {
            perms.add(new OAuth1Permission(scope, scope));
        }
        at.setScopes(perms);

        return at;
    }

    public void removeToken(String tokenId) {
        ServiceRequestToken srt = serviceRequestTokenDao.findByTokenId(tokenId);
        if (srt != null) serviceRequestTokenDao.remove(srt);

        ServiceAccessToken sat = serviceAccessTokenDao.findByTokenId(tokenId);
        if (sat != null) serviceAccessTokenDao.remove(sat);
    }

    public String finalizeAuthorization(RequestToken requestToken) throws OAuth1Exception {
        logger.debug("finalizeAuthorization() called");
        // RequestToken requestToken = data.getToken(); - now passing the token directly. 
        requestToken.setVerifier(UUID.randomUUID().toString());
        ServiceRequestToken srt = serviceRequestTokenDao.findByTokenId(requestToken.getTokenKey());
        if (srt != null) {
            srt.setVerifier(requestToken.getVerifier());
            serviceRequestTokenDao.merge(srt);
        }
        return requestToken.getVerifier();
    }
 

    public String getAccessTokenSecret(String accessTokenId) {
        ServiceAccessToken sat = serviceAccessTokenDao.findByTokenId(accessTokenId);
        return sat != null ? sat.getTokenSecret() : null;
    }

    public String getProviderNoByAccessToken(String accessTokenId) {
        ServiceAccessToken sat = serviceAccessTokenDao.findByTokenId(accessTokenId);
        return sat != null ? sat.getProviderNo() : null;
    }

    public String getRequestTokenSecret(String requestTokenId) {
        ServiceRequestToken srt = serviceRequestTokenDao.findByTokenId(requestTokenId);
        return srt != null ? srt.getTokenSecret() : null;
    }

}
