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
package io.github.carlos_emr.carlos.sms.admin;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.assembler.SmsConfigViewModelAssembler;
import io.github.carlos_emr.carlos.sms.dto.SmsConfigUpdateDto;
import io.github.carlos_emr.carlos.sms.dto.SmsSendResultDto;
import io.github.carlos_emr.carlos.sms.service.SmsConfigConflictException;
import io.github.carlos_emr.carlos.sms.service.SmsConfigService;
import io.github.carlos_emr.carlos.sms.service.SmsSendService;
import io.github.carlos_emr.carlos.sms.support.SmsPhoneNumbers;
import io.github.carlos_emr.carlos.sms.validator.SmsConfigValidator;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Administration &gt; SMS ({@code admin/ConfigureSms}).
 * <p>
 * A plain request shows the settings page and needs {@code _admin.sms} read. {@code method=configure}
 * saves the settings and {@code method=sendSystemTest} sends the fixed system-test text to a number the
 * administrator types; both are POST-only (405 before any check or write, so a GET cannot slip past CSRF
 * protection) and need {@code _admin.sms} write. Both redirect back to the page with a fixed result
 * code, so a refresh does not repeat them. Secrets are write-only: they are never read back into the page.
 * <p>
 * Typed credentials are saved only under the provider whose credential fields the page showed, which the
 * form names in a hidden field. If another provider was chosen in the list before saving, or the field is
 * missing, the typed credentials and the "remove the stored credentials" checkbox are ignored, the other
 * settings are saved, and the page says the credentials were not saved.
 *
 * @since 2026-09-24
 */
public class ConfigureSms2Action extends ActionSupport {
    static final String METHOD_CONFIGURE = "configure";
    static final String METHOD_SEND_SYSTEM_TEST = "sendSystemTest";
    private static final String SECURITY_OBJECT = "_admin.sms";
    private static final String CREDENTIAL_PARAMETER_PREFIX = "credential.";
    private static final String CLEAR_CREDENTIALS_PARAMETER = "clearCredentials";
    /** Hidden form field naming the provider whose credential fields the page showed. */
    private static final String CREDENTIALS_PROVIDER_PARAMETER = "credentialsProvider";
    static final String RESULT_SAVED = "saved";
    static final String RESULT_SAVED_WITHOUT_CREDENTIALS = "savedWithoutCredentials";
    static final String CONCURRENT_SAVE_ERROR = "sms.config.error.concurrentSave";

    private final SecurityInfoManager securityInfoManager;
    private final SmsConfigService configService;
    private final SmsConfigValidator validator;
    private final SmsConfigViewModelAssembler assembler;
    private final SmsSendService sendService;

    public ConfigureSms2Action(SecurityInfoManager securityInfoManager, SmsConfigService configService,
                               SmsConfigValidator validator, SmsConfigViewModelAssembler assembler,
                               SmsSendService sendService) {
        this.securityInfoManager = securityInfoManager;
        this.configService = configService;
        this.validator = validator;
        this.assembler = assembler;
        this.sendService = sendService;
    }

    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        String method = request.getParameter("method");
        if (METHOD_CONFIGURE.equals(method) || METHOD_SEND_SYSTEM_TEST.equals(method)) {
            if (!isPost(request)) {
                response.setHeader("Allow", "POST");
                response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return NONE;
            }
            LoggedInInfo loggedInInfo = requirePrivilege(request, "w");
            return METHOD_CONFIGURE.equals(method)
                    ? configure(request, response, loggedInInfo)
                    : sendSystemTest(request, response, loggedInInfo);
        }
        requirePrivilege(request, "r");
        request.setAttribute("smsConfig", assembler.assemble(request.getParameter("result"), List.of()));
        return SUCCESS;
    }

    private String configure(HttpServletRequest request, HttpServletResponse response, LoggedInInfo loggedInInfo)
            throws IOException {
        SmsProviderType providerType = parseProvider(request.getParameter("providerType"));
        // The credential inputs belong to the provider the page showed. Under any other provider (or when the
        // form does not say which it showed) they are dropped, never guessed at.
        boolean credentialsForChosenProvider = providerType != null
                && providerType.name().equals(request.getParameter(CREDENTIALS_PROVIDER_PARAMETER));
        Map<String, String> credentials = new HashMap<>();
        if (credentialsForChosenProvider) {
            for (String field : configService.credentialFields(providerType)) {
                String value = request.getParameter(CREDENTIAL_PARAMETER_PREFIX + field);
                if (value != null) {
                    credentials.put(field, value);
                }
            }
        }
        boolean clearCredentialsChecked = isChecked(request, CLEAR_CREDENTIALS_PARAMETER);
        boolean credentialsIgnored = !credentialsForChosenProvider
                && (clearCredentialsChecked || anyCredentialTyped(request));
        SmsConfigUpdateDto update = new SmsConfigUpdateDto(
                providerType,
                isChecked(request, "enabled"),
                isChecked(request, "schedulerEnabled"),
                trimToEmpty(request.getParameter("senderNumber")),
                request.getParameter("webhookSecret"),
                isChecked(request, "clearWebhookSecret"),
                credentials,
                credentialsForChosenProvider && clearCredentialsChecked
        );
        List<String> errors = validator.validate(update, configService.installedProviders());
        if (!errors.isEmpty()) {
            // Re-displayed with 200: CARLOS's ResponseSanitizationFilter mishandles a JSP body rendered
            // under a 4xx status ("committed mid-chain"), which left the admin a blank page.
            request.setAttribute("smsConfig", assembler.assembleRejected(update, errors));
            return SUCCESS;
        }
        try {
            configService.save(update, loggedInInfo.getLoggedInProviderNo());
        } catch (SmsConfigConflictException e) {
            // Re-displayed with 200 for the same reason as the validation errors above.
            request.setAttribute("smsConfig", assembler.assemble(null, List.of(CONCURRENT_SAVE_ERROR)));
            return SUCCESS;
        }
        return redirect(request, response, credentialsIgnored ? RESULT_SAVED_WITHOUT_CREDENTIALS : RESULT_SAVED);
    }

    private String sendSystemTest(HttpServletRequest request, HttpServletResponse response,
                                  LoggedInInfo loggedInInfo) throws IOException {
        String testNumber = trimToEmpty(request.getParameter("testNumber"));
        if (SmsPhoneNumbers.normalizeToE164(testNumber).isEmpty()) {
            return redirect(request, response, "testInvalid");
        }
        Security security = loggedInInfo.getLoggedInSecurity();
        SmsSendResultDto result = sendService.sendSystemTest(
                testNumber,
                loggedInInfo.getLoggedInProviderNo(),
                security == null ? null : security.getSecurityNo()
        );
        if (result.accepted()) {
            // Accepted but QUEUED: the rate limit was reached, so it has not gone out yet.
            return redirect(request, response, result.status() == SmsStatus.QUEUED ? "testQueued" : "testSent");
        }
        return redirect(request, response, result.status() == SmsStatus.CONSENT_BLOCKED ? "testBlocked" : "testFailed");
    }

    private LoggedInInfo requirePrivilege(HttpServletRequest request, String privilege) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null
                || !securityInfoManager.hasPrivilege(loggedInInfo, SECURITY_OBJECT, privilege, null)) {
            throw new SecurityException("missing required sec object (" + SECURITY_OBJECT + ")");
        }
        return loggedInInfo;
    }

    private static String redirect(HttpServletRequest request, HttpServletResponse response, String resultCode)
            throws IOException {
        response.sendRedirect(request.getContextPath() + "/admin/ConfigureSms?result=" + resultCode);
        return NONE;
    }

    private static SmsProviderType parseProvider(String value) {
        return Arrays.stream(SmsProviderType.values())
                .filter(type -> type.name().equals(value))
                .findFirst()
                .orElse(null);
    }

    private static boolean isChecked(HttpServletRequest request, String name) {
        return "true".equals(request.getParameter(name));
    }

    /** @return whether any credential input, for whichever provider, holds a value; the values are not kept */
    private static boolean anyCredentialTyped(HttpServletRequest request) {
        return request.getParameterMap().entrySet().stream()
                .filter(parameter -> parameter.getKey().startsWith(CREDENTIAL_PARAMETER_PREFIX))
                .flatMap(parameter -> Arrays.stream(parameter.getValue()))
                .anyMatch(value -> value != null && !value.isBlank());
    }

    // IMPROPER_UNICODE: case-insensitive comparison of the literal HTTP method name, not user-identity folding.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "case-insensitive comparison of the literal HTTP method name, not user-identity folding")
    private static boolean isPost(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod());
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
