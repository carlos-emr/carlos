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
import io.github.carlos_emr.carlos.sms.model.SmsSecretEncryptionException;
import io.github.carlos_emr.carlos.sms.service.SmsConfigConflictException;
import io.github.carlos_emr.carlos.sms.service.SmsConfigService;
import io.github.carlos_emr.carlos.sms.service.SmsCredentialField;
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
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;
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
 *
 * @since 2026-09-24
 */
public class ConfigureSms2Action extends ActionSupport {
    static final String METHOD_CONFIGURE = "configure";
    static final String METHOD_SEND_SYSTEM_TEST = "sendSystemTest";
    private static final String SECURITY_OBJECT = "_admin.sms";
    private static final String CREDENTIAL_PARAMETER_PREFIX = "credential.";
    static final String CREDENTIAL_PROVIDER_MISMATCH_ERROR = "sms.config.error.credentialProviderMismatch";
    static final String CONCURRENT_SAVE_ERROR = "sms.config.error.concurrentSave";
    static final String ENCRYPTION_UNAVAILABLE_ERROR = "sms.config.error.encryptionUnavailable";
    /** Stands in for a version field that is not a number: it matches no stored version, so the save is refused. */
    private static final int UNREADABLE_VERSION = -1;

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
        Map<String, String> credentials = new HashMap<>();
        List<SmsCredentialField> fields = configService.credentialFields(providerType);
        String selectedPrefix = CREDENTIAL_PARAMETER_PREFIX + providerType + ".";
        for (SmsCredentialField field : fields) {
            String value = request.getParameter(selectedPrefix + field.name());
            if (value != null) {
                credentials.put(field.name(), value);
            }
        }
        SmsConfigUpdateDto update = new SmsConfigUpdateDto(
                providerType,
                isChecked(request, "enabled"),
                isChecked(request, "schedulerEnabled"),
                trimToEmpty(request.getParameter("senderNumber")),
                request.getParameter("webhookSecret"),
                isChecked(request, "clearWebhookSecret"),
                credentials,
                parseVersion(request.getParameter("version"))
        );
        List<String> errors = new ArrayList<>(validator.validate(update, configService.installedProviders(),
                configService.providerNeeds(update)));
        Set<String> allowedParameters = fields.stream().map(field -> selectedPrefix + field.name())
                .collect(Collectors.toSet());
        // With JavaScript disabled, all groups may be submitted. Refuse typed values for any other group
        // (including the old unscoped names) rather than silently dropping or assigning them to a provider.
        boolean mismatchedCredentials = request.getParameterMap().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(CREDENTIAL_PARAMETER_PREFIX))
                .filter(entry -> !allowedParameters.contains(entry.getKey()))
                .flatMap(entry -> Arrays.stream(entry.getValue()))
                .anyMatch(value -> value != null && !value.isBlank());
        if (mismatchedCredentials) {
            errors.add(CREDENTIAL_PROVIDER_MISMATCH_ERROR);
        }
        if (!errors.isEmpty()) {
            // Re-displayed with 200: CARLOS's ResponseSanitizationFilter mishandles a JSP body rendered
            // under a 4xx status ("committed mid-chain"), which left the admin a blank page.
            request.setAttribute("smsConfig", assembler.assembleRejected(update, errors));
            return SUCCESS;
        }
        try {
            configService.save(update, loggedInInfo.getLoggedInProviderNo());
        } catch (SmsConfigConflictException e) {
            if (configService.alreadySaved(update, loggedInInfo.getLoggedInProviderNo())) {
                // A second click on Save: the first click already stored exactly these settings.
                return redirect(request, response, "saved");
            }
            // Re-displayed with 200 for the same reason as the validation errors above.
            request.setAttribute("smsConfig", assembler.assemble(null, List.of(CONCURRENT_SAVE_ERROR)));
            return SUCCESS;
        } catch (SmsSecretEncryptionException e) {
            // Nothing was stored. Shown on the form, with what was submitted, instead of an error page.
            request.setAttribute("smsConfig",
                    assembler.assembleRejected(update, List.of(ENCRYPTION_UNAVAILABLE_ERROR)));
            return SUCCESS;
        }
        return redirect(request, response, "saved");
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

    /**
     * @return the settings version the page showed; null when it showed nothing saved (an empty field)
     */
    private static Integer parseVersion(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return UNREADABLE_VERSION;
        }
    }

    private static boolean isChecked(HttpServletRequest request, String name) {
        return "true".equals(request.getParameter(name));
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
