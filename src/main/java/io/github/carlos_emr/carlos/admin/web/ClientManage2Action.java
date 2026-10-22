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
import java.security.SecureRandom;
import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.ServiceAccessTokenDao;
import io.github.carlos_emr.carlos.commn.dao.ServiceClientDao;
import io.github.carlos_emr.carlos.commn.model.ServiceAccessToken;
import io.github.carlos_emr.carlos.commn.model.ServiceClient;
import io.github.carlos_emr.carlos.security.CarlosMethodSecurity;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * JSON endpoint that manages OAuth service clients and access tokens for the
 * admin API console.
 *
 * <p>Replaces the legacy {@code /admin/api/clientManage.json.jsp} which performed
 * DAO writes with no application-level authorization check (any authenticated
 * session could mutate the OAuth trust store). Read operations require
 * {@code _admin r}; write operations require {@code _admin w} (or
 * {@code _admin.userAdmin w}) privilege.</p>
 *
 * <p>Mutating dispatches ({@code add}, {@code delete}) require POST; read
 * dispatches ({@code list}, {@code listTokens}) permit GET.</p>
 *
 * @since 2026-05-19
 */
@Component(ClientManage2Action.SPRING_BEAN_NAME)
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ClientManage2Action extends ActionSupport {

    public static final String SPRING_BEAN_NAME = "clientManage2Action";

    private static final Logger logger = MiscUtils.getLogger();
    private static final String RANDOM_KEY_CHARS = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final int RANDOM_KEY_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final transient ServiceClientDao serviceClientDao;
    private final transient ServiceAccessTokenDao serviceAccessTokenDao;
    private final transient CarlosMethodSecurity methodSecurity;

    /**
     * Creates the action for Struts-managed instantiation paths.
     *
     * <p>Delegates to the injected constructor after retrieving collaborators
     * from the Spring context.</p>
     *
     * @throws org.springframework.beans.BeansException if a required Spring bean is unavailable
     * @since 2026-05-19
     */
    public ClientManage2Action() {
        this(SpringUtils.getBean(ServiceClientDao.class),
                SpringUtils.getBean(ServiceAccessTokenDao.class),
                SpringUtils.getBean(CarlosMethodSecurity.class));
    }

    /**
     * Creates the action with explicit collaborators for Spring injection and tests.
     *
     * @param serviceClientDao DAO used to read and mutate service client records
     * @param serviceAccessTokenDao DAO used to read and delete service access tokens
     * @param methodSecurity method-level authorization facade for admin privileges
     * @since 2026-05-19
     */
    @Autowired
    public ClientManage2Action(ServiceClientDao serviceClientDao,
            ServiceAccessTokenDao serviceAccessTokenDao,
            CarlosMethodSecurity methodSecurity) {
        this.serviceClientDao = serviceClientDao;
        this.serviceAccessTokenDao = serviceAccessTokenDao;
        this.methodSecurity = methodSecurity;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        String method = request.getParameter("method");
        if (isReadMethod(method)) {
            if (!methodSecurity.hasPrivilege("_admin", "r")) {
                throw new SecurityException("missing required sec object (_admin)");
            }
            return handleRead(method, response);
        }

        if (!methodSecurity.hasAdminWrite()) {
            throw new SecurityException("missing required sec object (_admin or _admin.userAdmin)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            logger.warn("Rejected client-manage mutation with method {} from {}",
                    LogSafe.sanitize(String.valueOf(request.getMethod())),
                    LogSafe.sanitize(String.valueOf(request.getRemoteAddr())));
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        return handleMutation(method, request, response);
    }

    private static boolean isReadMethod(String method) {
        return "list".equals(method) || "listTokens".equals(method);
    }

    private String handleRead(String method, HttpServletResponse response) throws IOException {
        if ("list".equals(method)) {
            List<ServiceClientListEntry> clients = serviceClientDao.findAll().stream()
                    .map(ServiceClientListEntry::from)
                    .toList();
            writeJson(response, clients);
        } else {
            List<ServiceAccessTokenListEntry> tokens = serviceAccessTokenDao.findAll().stream()
                    .map(ServiceAccessTokenListEntry::from)
                    .toList();
            writeJson(response, tokens);
        }
        return NONE;
    }

    private String handleMutation(String method, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        ObjectNode json = MAPPER.createObjectNode();
        json.put("method", method);
        boolean success = false;
        String error = "";

        if ("add".equals(method)) {
            String name = request.getParameter("name");
            String uri = request.getParameter("uri");
            String lifetime = request.getParameter("lifetime");

            json.put("name", name);

            if (name == null || name.isEmpty()) {
                error = "Add Failure: Cannot add Client with empty value.";
            } else if (serviceClientDao.findByName(name) != null) {
                error = "Add Failure: Could not add entry to database.Name already being used.";
            } else {
                ServiceClient sc = new ServiceClient();
                sc.setName(name);
                sc.setKey(randomString(RANDOM_KEY_LENGTH, RANDOM_KEY_CHARS));
                sc.setSecret(randomString(RANDOM_KEY_LENGTH, RANDOM_KEY_CHARS));
                sc.setUri(uri);
                try {
                    sc.setLifetime(Integer.parseInt(lifetime));
                } catch (NumberFormatException e) {
                    sc.setLifetime(0);
                }
                serviceClientDao.persist(sc);
                if (sc.getId() == 0) {
                    error = "Add Failure: Could not add entry to database.";
                } else {
                    success = true;
                }
            }
        } else if ("delete".equals(method)) {
            String id = request.getParameter("id");
            json.put("id", id);
            if (id == null || id.isEmpty()) {
                error = "Delete Failure: Cannot remove Client with empty id.";
            } else {
                try {
                    serviceClientDao.remove(Integer.parseInt(id));
                    success = true;
                } catch (NumberFormatException e) {
                    error = "Delete Failure: Invalid client identifier.";
                }
            }
        } else {
            error = "Invalid method supplied.";
        }

        json.put("success", success);
        json.put("error", error);

        writeJson(response, json);
        return NONE;
    }

    private void writeJson(HttpServletResponse response, Object payload) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        MAPPER.writeValue(response.getOutputStream(), payload);
    }

    private static String randomString(int length, String chars) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private record ServiceClientListEntry(Integer id, String name, String key, String uri, Integer lifetime) {
        private static ServiceClientListEntry from(ServiceClient client) {
            return new ServiceClientListEntry(
                    client.getId(),
                    client.getName(),
                    client.getKey(),
                    client.getUri(),
                    client.getLifetime());
        }
    }

    private record ServiceAccessTokenListEntry(Integer id, Integer clientId, String scopes, long lifetime, long issued,
                                               Date dateCreated, String providerNo) {
        private static ServiceAccessTokenListEntry from(ServiceAccessToken token) {
            return new ServiceAccessTokenListEntry(
                    token.getId(),
                    token.getClientId(),
                    token.getScopes(),
                    token.getLifetime(),
                    token.getIssued(),
                    token.getDateCreated(),
                    token.getProviderNo());
        }
    }
}
