/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.admin.gate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.lab.ca.all.util.KeyPairGen;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Key Pair Creation ({@code admin/ViewKeygenCreateKey}, reached from the Key Pair Generator's
 * Create New Key button). Requires {@code _admin} {@code w}.
 *
 * <p>Opening the page (no {@code name} parameter) is a read and may be a GET. A request that
 * names a service is a write: it stores a key pair in {@code publicKeys} (or, for the reserved
 * name {@code oscar}, the server's own pair in {@code oscarKey}). Such a request must be a POST,
 * which CSRFGuard checks for a token; a GET or HEAD carrying {@code name}, with any value, is
 * answered 405 before anything is read or stored (#6055, #5216). The route is also listed in
 * {@code HttpMethodGuardFilter.MUTATION_INTENT_PARAMS_BY_ROUTE} as a backstop.</p>
 *
 * <p>Creating the pair used to happen in {@code createKey.jsp} itself, which streamed the key
 * file through {@code getOutputStream()} and then kept rendering the page, so every successful
 * creation ended in an {@code IllegalStateException}. The action now owns the response:</p>
 * <ul>
 *   <li>a created service pair is written as the {@code keyPair.key} attachment and the action
 *       returns {@link #NONE}, so no page is rendered after the bytes;</li>
 *   <li>every other outcome (refusal, failure, the server pair created) writes nothing and
 *       returns {@link #SUCCESS} with {@link #MESSAGE_ATTRIBUTE} and {@link #ERROR_ATTRIBUTE}
 *       set for the page to show.</li>
 * </ul>
 *
 * <p>The attachment holds a private key, so it is sent {@code no-store}. Neither the key nor the
 * service name is logged.</p>
 *
 * @since 2026-04-13
 */
public final class ViewKeygenCreateKey2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();

    /** Request attribute holding the status message the page shows, or absent for none. */
    static final String MESSAGE_ATTRIBUTE = "keygenMessage";

    /** Request attribute, {@link Boolean}, true when {@link #MESSAGE_ATTRIBUTE} reports a failure. */
    static final String ERROR_ATTRIBUTE = "keygenError";

    /** The reserved service name of the server's own key pair ({@code oscarKey}). */
    static final String SERVER_KEY_NAME = "oscar";

    /** The other-type marker of the page's Lab type list; the type is then read from {@code otherType}. */
    private static final String OTHER_TYPE = "OTHER";

    /** Service names the page has always accepted; also safe in the downloaded file's header line. */
    private static final Pattern SERVICE_NAME = Pattern.compile("^[a-zA-Z0-9._-]+$");

    /** {@code publicKeys.service} and {@code publicKeys.type} are both {@code varchar(100)}. */
    static final int MAX_COLUMN_LENGTH = 100;

    private final transient SecurityInfoManager securityInfoManager;

    public ViewKeygenCreateKey2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        String name = request.getParameter("name");
        if (name == null) {
            // Opening the empty form, by GET (Create New Key) or by a POST without a name.
            return SUCCESS;
        }

        // The presence of `name`, whatever its value, is the write. Refuse it before any lookup or
        // key generation, so a cross-site GET (which CSRFGuard does not check) changes nothing.
        if (!"POST".equals(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            response.setHeader("Allow", "POST");
            return NONE;
        }

        if (name.isEmpty()) {
            return showError(request, "Failed: Please enter a service name.");
        }
        if (name.length() > MAX_COLUMN_LENGTH || !SERVICE_NAME.matcher(name).matches()) {
            return showError(request, "Failed: Service name must be at most " + MAX_COLUMN_LENGTH
                    + " characters and contain only letters, numbers, periods, hyphens and underscores.");
        }

        if (SERVER_KEY_NAME.equals(name)) {
            return createServerKeyPair(request);
        }
        return createServiceKeyPair(request, response, name);
    }

    /** Creates the server's own pair, once; it is never downloaded, so the page reports the outcome. */
    private String createServerKeyPair(HttpServletRequest request) {
        if (KeyPairGen.checkName(SERVER_KEY_NAME)) {
            return showError(request, "Failed: The oscar key pair has already been created");
        }
        if (!"success".equals(KeyPairGen.createKeys(SERVER_KEY_NAME, null))) {
            return showError(request, "Failed: Could not create the oscar key pair");
        }
        request.setAttribute(MESSAGE_ATTRIBUTE, "CARLOS key pair created successfully");
        request.setAttribute(ERROR_ATTRIBUTE, Boolean.FALSE);
        return SUCCESS;
    }

    /**
     * Creates a service's pair and sends it, with the server's public key, as the
     * {@code keyPair.key} attachment.
     */
    private String createServiceKeyPair(HttpServletRequest request, HttpServletResponse response, String name)
            throws IOException {
        String type = request.getParameter("type");
        if (OTHER_TYPE.equals(type)) {
            type = request.getParameter("otherType");
        }
        if (type == null || type.isBlank()) {
            return showError(request, "Failed: Please specify the message type");
        }
        type = type.strip();
        if (type.length() > MAX_COLUMN_LENGTH) {
            return showError(request, "Failed: The message type must be at most " + MAX_COLUMN_LENGTH + " characters");
        }

        if (KeyPairGen.checkName(name)) {
            return showError(request, "Failed: Key pair has already been created for the service '" + name + "'");
        }

        // The file is useless without the server's public key. Look it up before storing the
        // service pair: storing first left a pair nobody received, and its name could then not
        // be used again ("already created").
        String serverPublicKey = KeyPairGen.getPublic();
        if (serverPublicKey == null) {
            return showError(request, "Failed: The CARLOS key pair has not been created yet. "
                    + "Create it first by entering the service name 'oscar'.");
        }

        String clientPrivateKey = KeyPairGen.createKeys(name, type);
        if (clientPrivateKey == null) {
            return showError(request, "Failed: Could not create key pair");
        }

        byte[] keyFile = ("-------- Service Name --------\n" + name + "\n------------------------------\n"
                + "----- Client Private Key -----\n" + clientPrivateKey + "\n------------------------------\n"
                + "------ Oscar Public Key ------\n" + serverPublicKey + "\n------------------------------")
                .getBytes(StandardCharsets.UTF_8);

        response.setContentType("text/plain;charset=UTF-8");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Content-Disposition", "attachment; filename=keyPair.key");
        response.setContentLength(keyFile.length);
        try {
            ServletOutputStream output = response.getOutputStream();
            output.write(keyFile);
            output.flush();
        } catch (IOException e) {
            // The pair is stored; the administrator can still read its private key in the Key Pair
            // Generator. Nothing else can be sent once the body has started, so only log it.
            logger.warn("The created key pair could not be sent to the browser: {}", e.getClass().getSimpleName());
        }
        return NONE;
    }

    private static String showError(HttpServletRequest request, String message) {
        request.setAttribute(MESSAGE_ATTRIBUTE, message);
        request.setAttribute(ERROR_ATTRIBUTE, Boolean.TRUE);
        return SUCCESS;
    }
}
