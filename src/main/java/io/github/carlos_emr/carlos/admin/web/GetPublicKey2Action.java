/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.admin.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import io.github.carlos_emr.carlos.commn.dao.PublicKeyDao;
import io.github.carlos_emr.carlos.commn.model.PublicKey;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * JSON endpoint that returns the {@link PublicKey} record for a named service.
 *
 * <p>Replaces the legacy {@code /admin/keygen/getPublicKey.json.jsp} which had
 * no application-level authorization check. Requires {@code _admin w}
 * privilege; the underlying record exposes private key material so access is
 * restricted to administrators with write privileges.</p>
 *
 * @since 2026-05-19
 */
@Component(GetPublicKey2Action.SPRING_BEAN_NAME)
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class GetPublicKey2Action extends ActionSupport {

    public static final String SPRING_BEAN_NAME = "getPublicKey2Action";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final transient SecurityInfoManager securityInfoManager;
    private final transient PublicKeyDao publicKeyDao;

    /**
     * Creates the action for Struts-managed instantiation paths.
     *
     * <p>Delegates to the injected constructor after retrieving collaborators
     * from the Spring context.</p>
     *
     * @throws org.springframework.beans.BeansException if a required Spring bean is unavailable
     * @since 2026-05-19
     */
    public GetPublicKey2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class),
                SpringUtils.getBean(PublicKeyDao.class));
    }

    /**
     * Creates the action with explicit collaborators for Spring injection and tests.
     *
     * @param securityInfoManager authorization service used for admin privilege checks
     * @param publicKeyDao DAO used to load public key records
     * @since 2026-05-19
     */
    @Autowired
    public GetPublicKey2Action(SecurityInfoManager securityInfoManager, PublicKeyDao publicKeyDao) {
        this.securityInfoManager = securityInfoManager;
        this.publicKeyDao = publicKeyDao;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        String keyId = request.getParameter("id");
        if (keyId == null || keyId.trim().isEmpty()) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Public key id is required.");
            return NONE;
        }

        PublicKey publicKey = publicKeyDao.find(keyId);
        if (publicKey == null) {
            writeError(response, HttpServletResponse.SC_NOT_FOUND, "Public key was not found.");
            return NONE;
        }

        LogAction.addLog(loggedInInfo, LogConst.READ, "PublicKey", keyId, "", "private key accessed via API");
        writeJson(response, PublicKeyResponse.from(publicKey));
        return NONE;
    }

    private void writeError(HttpServletResponse response, int status, String error) throws IOException {
        response.setStatus(status);
        ObjectNode json = MAPPER.createObjectNode();
        json.put("success", false);
        json.put("error", error);
        writeJson(response, json);
    }

    private void writeJson(HttpServletResponse response, Object payload) throws IOException {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setContentType("application/json;charset=UTF-8");
        MAPPER.writeValue(response.getOutputStream(), payload);
    }

    private record PublicKeyResponse(boolean success, String service, String type, String base64EncodedPrivateKey,
                                     Integer matchingProfessionalSpecialistId) {
        private static PublicKeyResponse from(PublicKey publicKey) {
            return new PublicKeyResponse(
                    true,
                    publicKey.getService(),
                    publicKey.getType(),
                    publicKey.getBase64EncodedPrivateKey(),
                    publicKey.getMatchingProfessionalSpecialistId());
        }
    }
}
