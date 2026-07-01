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
package io.github.carlos_emr.carlos.login;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the migrated root login/error pages onto Struts-backed routes and
 * internal WEB-INF views.
 *
 * <p>These tests are configuration guardrails, not a substitute for action-level unit tests or
 * Playwright coverage. Keep assertions focused on routing, CSRF wiring, and JSP migration
 * invariants that are easy to break during extensionless-route cleanup.</p>
 *
 * @since 2026-04-15
 */
@DisplayName("Login JSP migration regressions")
@Tag("unit")
@Tag("security")
class LoginJspMigrationRegressionTest {

    private static final Path STRUTS_LOGIN_XML =
            Path.of("src/main/webapp/WEB-INF/classes/struts-login.xml");
    private static final Path STRUTS_XML =
            Path.of("src/main/webapp/WEB-INF/classes/struts.xml");
    private static final Path STRUTS_INTEGRATION_XML =
            Path.of("src/main/webapp/WEB-INF/classes/struts-integration.xml");
    private static final Path WEB_XML =
            Path.of("src/main/webapp/WEB-INF/web.xml");
    private static final Path CSRF_GUARD =
            Path.of("src/main/webapp/WEB-INF/Owasp.CsrfGuard.properties");
    private static final Path MENU_CONFIG =
            Path.of("src/main/webapp/WEB-INF/menu-config.xml");
    private static final Path PROVIDER_MAIN_MENU =
            Path.of("src/main/webapp/WEB-INF/jsp/provider/mainMenu.jsp");
    private static final Path PROVIDER_CONTROL =
            Path.of("src/main/webapp/WEB-INF/jsp/provider/providercontrol.jsp");
    private static final Path APPOINTMENT_PROVIDER_ADMIN_DAY =
            Path.of("src/main/webapp/WEB-INF/jsp/provider/appointmentprovideradminday.jsp");
    private static final Path PROVIDER_SCHEDULE_PAGE_JS =
            Path.of("src/main/webapp/WEB-INF/jsp/provider/schedulePage.js.jsp");
    private static final Path ADMINISTRATION_LEFT_NAV =
            Path.of("src/main/webapp/WEB-INF/jsp/administration/leftNav.jspf");
    private static final Path LOGIN_ACTION =
            Path.of("src/main/java/io/github/carlos_emr/carlos/login/Login2Action.java");
    private static final Path FORCE_PASSWORD_RESET_GATE =
            Path.of("src/main/java/io/github/carlos_emr/carlos/login/gate/ViewForcePasswordReset2Action.java");
    private static final Path FORCE_PASSWORD_RESET_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/login/forcepasswordreset.jsp");
    private static final Path LOGIN_FAILED_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/login/loginfailed.jsp");
    private static final Path SELECT_FACILITY_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/login/select_facility.jsp");
    private static final Path THIRD_PARTY_LOGIN_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/login/3rdpartyLogin.jsp");
    private static final Path MFA_HANDLER_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/mfa/mfa_handler.jsp");
    private static final Path MFA_OTP_HANDLER_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/mfa/mfa_otp_handler.jsp");
    private static final Path LOGIN_FILTER =
            Path.of("src/main/java/io/github/carlos_emr/carlos/sec/LoginFilter.java");

    @Test
    @DisplayName("struts login config should expose the migrated page actions and internal view targets")
    void strutsLoginConfigShouldExposeMigratedPageActions() throws IOException {
        String struts = Files.readString(STRUTS_LOGIN_XML, StandardCharsets.UTF_8);

        assertThat(struts).contains("<action name=\"index\"");
        assertThat(struts).contains("<action name=\"logoutPage\"");
        assertThat(struts).contains("<action name=\"loginfailed\"");
        assertThat(struts).contains("<action name=\"forcepasswordreset\"");
        assertThat(struts).contains("<action name=\"forcepasswordresetSubmit\"");
        assertThat(struts).contains("<action name=\"mfa/loginMfa\"");
        assertThat(struts).contains("<action name=\"location\"");
        assertThat(struts).contains("<action name=\"select_facility\"");
        assertThat(struts).contains("<action name=\"securityError\"");
        assertThat(struts).contains("<action name=\"errorpage\"");
        assertThat(struts).contains("<action name=\"failure\"");
        assertThat(struts).contains("<action name=\"closenreload\"");
        assertThat(struts).contains("/WEB-INF/jsp/login/index.jsp");
        assertThat(struts).contains("/WEB-INF/jsp/error/errorpage.jsp");
        assertThat(struts).contains("/WEB-INF/jsp/common/closenreload.jsp");
        assertThat(actionBlock(struts, "select_facility"))
                .contains("io.github.carlos_emr.carlos.login.gate.SelectFacility2Action")
                .contains("<result name=\"provider\" type=\"redirect\">/provider/providercontrol</result>");
        assertThat(actionBlock(struts, "login"))
                .contains("<result name=\"error\">/WEB-INF/jsp/login/loginfailed.jsp</result>");
        assertThat(actionBlock(struts, "forcepasswordresetSubmit"))
                .contains("<result name=\"error\">/WEB-INF/jsp/login/loginfailed.jsp</result>");
        assertThat(struts).doesNotContain("/WEB-INF/jsp/error/WEB-INF/jsp/error/");
        assertThat(struts).doesNotContain("/WEB-INF/jsp/common/WEB-INF/jsp/common/");
    }

    @Test
    @DisplayName("MFA submit route should be mapped and CSRF protected")
    void shouldMapAndProtectMfaSubmitRoute_whenOtpFormPosts() throws IOException {
        String struts = Files.readString(STRUTS_LOGIN_XML, StandardCharsets.UTF_8);
        String mfaOtpJsp = Files.readString(MFA_OTP_HANDLER_JSP, StandardCharsets.UTF_8);

        assertThat(struts)
                .contains("<action name=\"mfa/loginMfa\" class=\"io.github.carlos_emr.carlos.login.Login2Action\">")
                .contains("<result name=\"mfaHandler\">/WEB-INF/jsp/mfa/mfa_handler.jsp</result>");
        assertThat(mfaOtpJsp)
                .contains("action=\"<%= request.getContextPath() %>/mfa/loginMfa\"")
                .contains("<%@ taglib prefix=\"csrf\" uri=\"https://owasp.org/www-project-csrfguard/Owasp.CsrfGuard.tld\" %>")
                .contains("name=\"<csrf:tokenname/>\"")
                .contains("value=\"<csrf:tokenvalue/>\"")
                .doesNotContain("http://www.owasp.org/index.php/Category:OWASP_CSRFGuard_Project/Owasp.CsrfGuard.tld")
                .doesNotContain("owasp.encoder.jakarta.advanced");
    }

    @Test
    @DisplayName("facility selector should submit selection with CSRF token")
    void facilitySelectorShouldSubmitSelection_withCsrfToken() throws IOException {
        String selectFacilityJsp = Files.readString(SELECT_FACILITY_JSP, StandardCharsets.UTF_8);

        assertThat(selectFacilityJsp)
                .contains("method=\"post\"")
                .contains("action=\"${pageContext.request.contextPath}/select_facility\"")
                .contains("csrf:tokenname")
                .contains("csrf:tokenvalue")
                .doesNotContain("href='?nextPage=");
    }

    @Test
    @DisplayName("welcome page and error handling should point at the new action and WEB-INF error view")
    void webXmlShouldUseMigratedLoginAndErrorTargets() throws IOException {
        String webXml = Files.readString(WEB_XML, StandardCharsets.UTF_8);
        String strutsXml = Files.readString(STRUTS_XML, StandardCharsets.UTF_8);
        int loginFilterIndex = filterMappingIndex(webXml, "LoginFilter");
        int sectionRootCompatibilityIndex = filterMappingIndex(webXml, "SectionRootCompatibilityFilter");
        int prepareIndex = filterMappingIndex(webXml, "struts2-prepare");
        int loggedInUserIndex = filterMappingIndex(webXml, "LoggedInUserFilter");
        int executeIndex = filterMappingIndex(webXml, "struts2-execute");

        assertThat(webXml).contains("<welcome-file>index</welcome-file>");
        assertThat(webXml).contains("<location>/WEB-INF/jsp/error/errorpage.jsp</location>");
        assertThat(webXml).doesNotContain("<welcome-file>index.jsp</welcome-file>");
        assertThat(webXml).doesNotContain("<welcome-file>index.html</welcome-file>");
        assertThat(webXml).doesNotContain("<welcome-file>index.htm</welcome-file>");
        assertThat(webXml).doesNotContain("<url-pattern>*.do</url-pattern>");
        assertThat(webXml).contains("<filter-name>RootEntryRedirectFilter</filter-name>");
        assertThat(webXml).contains("<filter-name>SectionRootCompatibilityFilter</filter-name>");
        assertThat(webXml).contains("org.apache.struts2.dispatcher.filter.StrutsPrepareFilter");
        assertThat(webXml).contains("org.apache.struts2.dispatcher.filter.StrutsExecuteFilter");
        assertThat(filterMappingBlock(webXml, "CsrfGuardScriptInjectionFilter"))
                .contains("<dispatcher>FORWARD</dispatcher>")
                .doesNotContain("<dispatcher>REQUEST</dispatcher>");
        assertThat(webXml).doesNotContain("StrutsPrepareAndExecuteFilter");
        assertThat(loginFilterIndex).isGreaterThan(-1);
        assertThat(sectionRootCompatibilityIndex).isGreaterThan(loginFilterIndex);
        assertThat(prepareIndex).isGreaterThan(-1);
        assertThat(prepareIndex).isGreaterThan(loginFilterIndex);
        assertThat(loggedInUserIndex).isGreaterThan(prepareIndex);
        assertThat(executeIndex).isGreaterThan(loggedInUserIndex);
        assertThat(strutsXml).contains("woff|woff2|ttf");
        assertThat(strutsXml).contains("html|htm");
        assertThat(strutsXml).contains("^(/[^/]+)?/WEB-INF/.*");
    }

    @Test
    @DisplayName("administration section home should keep the clean public route")
    void shouldKeepCleanPublicRoute_forAdministrationSectionHome() throws IOException {
        String integrationStruts = Files.readString(STRUTS_INTEGRATION_XML, StandardCharsets.UTF_8);
        String menuConfig = Files.readString(MENU_CONFIG, StandardCharsets.UTF_8);
        String personaService = Files.readString(
                Path.of("src/main/java/io/github/carlos_emr/carlos/webserv/rest/PersonaService.java"),
                StandardCharsets.UTF_8);
        String mainMenu = Files.readString(PROVIDER_MAIN_MENU, StandardCharsets.UTF_8);
        String appointmentProviderAdminDay =
                Files.readString(APPOINTMENT_PROVIDER_ADMIN_DAY, StandardCharsets.UTF_8);
        String administrationLeftNav = Files.readString(ADMINISTRATION_LEFT_NAV, StandardCharsets.UTF_8);

        assertThat(actionBlock(integrationStruts, "administration/index"))
                .contains("class=\"io.github.carlos_emr.carlos.administration.gate.ViewAdministrationIndex2Action\"")
                .contains("<result name=\"success\">/WEB-INF/jsp/administration/index.jsp</result>");
        assertThat(actionBlock(integrationStruts, "administration"))
                .contains("class=\"io.github.carlos_emr.carlos.administration.gate.ViewAdministrationIndex2Action\"")
                .contains("<result name=\"success\">/WEB-INF/jsp/administration/index.jsp</result>");
        assertThat(menuConfig).doesNotContain("/administration/index");
        assertThat(personaService)
                .contains("../administration")
                .doesNotContain("../administration/")
                .doesNotContain("../administration/index");
        assertThat(mainMenu)
                .contains("String administrationUrl = request.getContextPath() + \"/administration\"")
                .contains("openScheduleMenuSection")
                .contains("SafeEncode.forJavaScriptAttribute(administrationUrl)")
                .doesNotContain("/administration/")
                .doesNotContain("/administration/index");
        assertThat(appointmentProviderAdminDay)
                .contains("String scheduleAdministrationUrl = request.getContextPath() + \"/administration\"")
                .contains("openScheduleSection")
                .contains("scheduleAdministrationUrlForJsAttribute")
                .contains("newWindow(\"<%= request.getContextPath() %>/administration\", \"admin\")")
                .doesNotContain("/administration/")
                .doesNotContain("/administration/index");
        assertThat(administrationLeftNav)
                .contains("${ctx}/administration")
                .doesNotContain("${ctx}/administration/")
                .doesNotContain("/administration/index");
    }

    @Test
    @DisplayName("appointment day should run password expiry warning on non CAISI schedule load")
    void shouldRunPasswordExpiryWarning_onNonCaisiScheduleLoad() throws IOException {
        String appointmentProviderAdminDay =
                Files.readString(APPOINTMENT_PROVIDER_ADMIN_DAY, StandardCharsets.UTF_8);
        String providerSchedulePageJs = Files.readString(PROVIDER_SCHEDULE_PAGE_JS, StandardCharsets.UTF_8);
        String passwordExpiryWarningFunction =
                functionBlock(providerSchedulePageJs, "showPasswordExpiryWarning");

        assertThat(appointmentProviderAdminDay)
                .contains("<body")
                .contains("showPasswordExpiryWarning();");
        assertThat(providerSchedulePageJs)
                .contains("function showPasswordExpiryWarning()")
                .contains("function popupPageOfChangePassword()")
                .contains("password-expiry-warning")
                .contains("document.createElement(\"div\")")
                .contains("provider.changePassword.msgAccountExpiringWithDays")
                .contains("changePasswordLink.href")
                .contains("/provider/ViewChangePassword")
                .contains("showPasswordExpiryWarning();")
                .doesNotContain("\" day\"");
        assertThat(passwordExpiryWarningFunction)
                .contains("changePasswordLink.href")
                .contains("/provider/ViewChangePassword")
                .doesNotContain("window.location.href")
                .doesNotContain("window.open(");
    }

    @Test
    @DisplayName("provider control should include schedule JSPs directly")
    void providerControlShouldIncludeScheduleJspsDirectly() throws IOException {
        String providerControl = Files.readString(PROVIDER_CONTROL, StandardCharsets.UTF_8);

        assertThat(providerControl)
                .contains("{\"day\", \"/WEB-INF/jsp/provider/appointmentprovideradminday.jsp\"}")
                .contains("{\"month\", \"/WEB-INF/jsp/provider/appointmentprovideradminmonth.jsp\"}")
                .contains("ProviderAppointmentReadGate.hasAccess")
                .contains("keep the shared appointment gate above")
                .doesNotContain("out.clearBuffer()")
                .doesNotContain("{\"day\", \"/provider/ViewAppointmentAdminDay\"}")
                .doesNotContain("{\"month\", \"/provider/ViewAppointmentAdminMonth\"}");
    }

    @Test
    @DisplayName("login failure JSP should prefer action attributes before legacy query parameters")
    void shouldPreferActionAttribute_beforeLegacyLoginFailureQueryParameter() throws IOException {
        String loginFailedJsp = Files.readString(LOGIN_FAILED_JSP, StandardCharsets.UTF_8);

        assertThat(loginFailedJsp)
                .contains("request.getAttribute(\"errormsg\")")
                .contains("request.getParameter(\"errormsg\")");
        assertThat(loginFailedJsp.indexOf("request.getAttribute(\"errormsg\")"))
                .isLessThan(loginFailedJsp.indexOf("request.getParameter(\"errormsg\")"));
        assertThat(loginFailedJsp)
                .contains("request.setAttribute(\"errormsg\", errormsg)")
                .contains("${pageContext.request.contextPath}/images/favicon.ico")
                .contains("request.getContextPath() %>/js/global.js")
                .contains("value=\"${errormsg}\"")
                .doesNotContain("<base href")
                .doesNotContain("request.getServerName()")
                .doesNotContain("<%= errormsg %>")
                .doesNotContain("owasp.encoder.jakarta.advanced");
    }

    @Test
    @DisplayName("MFA handler should encode request error message with CARLOS encoder tag")
    void shouldEncodeRequestErrorMessage_withCarlosEncoderTag() throws IOException {
        String mfaHandlerJsp = Files.readString(MFA_HANDLER_JSP, StandardCharsets.UTF_8);

        assertThat(mfaHandlerJsp)
                .contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>")
                .contains("<carlos:encode value=\"${requestScope.errMsg}\" context=\"html\"/>")
                .doesNotContain("${requestScope.errMsg}</strong>")
                .doesNotContain("<c:out");
    }

    @Test
    @DisplayName("third party login should rely on rotated session cookie")
    void shouldUseRotatedSessionCookie_forThirdPartyLogin() throws IOException {
        String thirdPartyLoginJsp = Files.readString(THIRD_PARTY_LOGIN_JSP, StandardCharsets.UTF_8);

        assertThat(thirdPartyLoginJsp)
                .contains("const loginUrl = \"${pageContext.request.contextPath}/login\"")
                .contains("action=\"${carlos:forHtmlAttribute(oauthData.replyTo)}\"")
                .contains("Authentication always rotates the session")
                .doesNotContain("invalidate_session")
                .doesNotContain("/login;jsessionid=")
                .doesNotContain("oauthData.replyTo)};jsessionid=")
                .doesNotContain("pageContext.session.id");
    }

    @Test
    @DisplayName("representative public callers should use migrated login routes")
    void shouldUseMigratedLoginRoutes_forRepresentativePublicCallers() throws IOException {
        String csrfGuard = Files.readString(CSRF_GUARD, StandardCharsets.UTF_8);
        String menuConfig = Files.readString(MENU_CONFIG, StandardCharsets.UTF_8);

        assertThat(csrfGuard).contains("CarlosCsrfGuardFilter owns invalid-token responses");
        assertThat(csrfGuard).doesNotContain("org.owasp.csrfguard.action.Redirect.Page=%servletContext%/errorpage");

        assertThat(csrfGuard).contains("%servletContext%/index");
        assertThat(csrfGuard).contains("%servletContext%/logoutPage");
        assertThat(csrfGuard).contains("%servletContext%/login");
        assertThat(csrfGuard).doesNotContain("%servletContext%/forcepasswordresetSubmit");
        assertThat(menuConfig).doesNotContain("location=\"index.jsp\"");
    }

    @Test
    @DisplayName("forced password reset should use the credential cache token and no userName session dependency")
    void shouldUseCredentialCacheToken_forForcedPasswordReset() throws IOException {
        String loginAction = Files.readString(LOGIN_ACTION, StandardCharsets.UTF_8);
        String forcePasswordResetGate = Files.readString(FORCE_PASSWORD_RESET_GATE, StandardCharsets.UTF_8);
        String forcePasswordResetJsp = Files.readString(FORCE_PASSWORD_RESET_JSP, StandardCharsets.UTF_8);
        String loginFilter = Files.readString(LOGIN_FILTER, StandardCharsets.UTF_8);

        assertThat(forcePasswordResetGate)
                .contains("GET, HEAD")
                .contains("if (session == null)")
                .contains("Login2Action.hasValidLoginCredentialsToken(request)")
                .contains("Login2Action.loginFailedRedirectUrl(request,")
                .contains("Login2Action.message(request, \"provider.providerchangepassword.errorSessionExpired\")")
                .doesNotContain("getAttribute(\"userName\")")
                .doesNotContain("setAttribute(\"userName\"")
                .doesNotContain("Session expired. Please log in again.")
                .doesNotContain("String result = super.execute()");
        assertThat(actionBlock(Files.readString(STRUTS_LOGIN_XML, StandardCharsets.UTF_8), "forcepasswordreset"))
                .contains("class=\"io.github.carlos_emr.carlos.login.gate.ViewForcePasswordReset2Action\"")
                .contains("<result name=\"success\">/WEB-INF/jsp/login/forcepasswordreset.jsp</result>");
        assertThat(actionBlock(Files.readString(STRUTS_LOGIN_XML, StandardCharsets.UTF_8), "forcepasswordresetSubmit"))
                .contains("class=\"io.github.carlos_emr.carlos.login.Login2Action\"")
                .contains("<result name=\"forcepasswordreset\">/WEB-INF/jsp/login/forcepasswordreset.jsp</result>");
        assertThat(loginFilter).contains("\"/forcepasswordresetSubmit\"");
        assertThat(loginAction)
                .contains("LOGIN_CREDENTIALS_TOKEN_ATTR")
                .contains("LoginCredentialCache.getInstance().peek")
                .contains("LoginCredentialCache.getInstance().consume")
                .contains("LoginCredentialCache.getInstance().store")
                .contains("session.setAttribute(LOGIN_CREDENTIALS_TOKEN_ATTR, token)")
                .contains("response.sendRedirect(request.getContextPath() + \"/forcepasswordreset\")")
                .contains("hasValidLoginCredentialsToken")
                .contains("loginFailedRedirectUrl")
                .contains("URLEncoder.encode(errorMessage, StandardCharsets.UTF_8)")
                .contains("removeAttributesFromSession(request);")
                .doesNotContain("FORCE_PASSWORD_RESET_PENDING_ATTR")
                .doesNotContain("session.setAttribute(\"userName\"")
                .doesNotContain("Your old password, does NOT match")
                .doesNotContain("Your new password, does NOT match")
                .doesNotContain("Your new password, is the same");
        assertThat(countOccurrences(loginAction, "removeAttributesFromSession(request);"))
                .isGreaterThanOrEqualTo(3);
        assertThat(loginAction).doesNotContain("session.removeAttribute(\"userName\")");
        assertThat(forcePasswordResetJsp)
                .contains("action=\"${pageContext.request.contextPath}/forcepasswordresetSubmit\"")
                .contains("csrf:tokenname")
                .contains("csrf:tokenvalue")
                .contains("request.getAttribute(\"errormsg\")")
                .doesNotContain("request.getParameter(\"errormsg\")")
                .doesNotContain("session.getAttribute(\"userName\")");
    }

    @Test
    @DisplayName("migrated public root JSP pages should no longer exist")
    void migratedPublicRootJspPagesShouldNoLongerExist() {
        assertThat(Path.of("src/main/webapp/index.jsp")).doesNotExist();
        assertThat(Path.of("src/main/webapp/index.html")).doesNotExist();
        assertThat(Path.of("src/main/webapp/index.htm")).doesNotExist();
        assertThat(Path.of("src/main/webapp/logout.jsp")).doesNotExist();
        assertThat(Path.of("src/main/webapp/loginfailed.jsp")).doesNotExist();
        assertThat(Path.of("src/main/webapp/forcepasswordreset.jsp")).doesNotExist();
        assertThat(Path.of("src/main/webapp/location.jsp")).doesNotExist();
        assertThat(Path.of("src/main/webapp/select_facility.jsp")).doesNotExist();
        assertThat(Path.of("src/main/webapp/securityError.jsp")).doesNotExist();
        assertThat(Path.of("src/main/webapp/errorpage.jsp")).doesNotExist();
        assertThat(Path.of("src/main/webapp/failure.jsp")).doesNotExist();
        assertThat(Path.of("src/main/webapp/500.jsp")).doesNotExist();
        assertThat(Path.of("src/main/webapp/closenreload.jsp")).doesNotExist();
    }

    private static int filterMappingIndex(String webXml, String filterName) {
        String mappingStart = "<filter-mapping>";
        String mappingEnd = "</filter-mapping>";
        String filterNameElement = "<filter-name>" + filterName + "</filter-name>";
        int searchFrom = 0;
        while (true) {
            int start = webXml.indexOf(mappingStart, searchFrom);
            if (start < 0) {
                return -1;
            }
            int end = webXml.indexOf(mappingEnd, start);
            if (end < 0) {
                return -1;
            }
            String mapping = webXml.substring(start, end + mappingEnd.length());
            if (mapping.contains(filterNameElement)) {
                return start;
            }
            searchFrom = end + mappingEnd.length();
        }
    }

    private static String filterMappingBlock(String webXml, String filterName) {
        String mappingStart = "<filter-mapping>";
        String mappingEnd = "</filter-mapping>";
        String filterNameElement = "<filter-name>" + filterName + "</filter-name>";
        int searchFrom = 0;
        while (true) {
            int start = webXml.indexOf(mappingStart, searchFrom);
            if (start < 0) {
                return "";
            }
            int end = webXml.indexOf(mappingEnd, start);
            if (end < 0) {
                return "";
            }
            String mapping = webXml.substring(start, end + mappingEnd.length());
            if (mapping.contains(filterNameElement)) {
                return mapping;
            }
            searchFrom = end + mappingEnd.length();
        }
    }

    private static String actionBlock(String strutsXml, String actionName) {
        String actionStart = "<action name=\"" + actionName + "\"";
        String actionEnd = "</action>";
        int start = strutsXml.indexOf(actionStart);
        if (start < 0) {
            return "";
        }
        int end = strutsXml.indexOf(actionEnd, start);
        if (end < 0) {
            return "";
        }
        return strutsXml.substring(start, end + actionEnd.length());
    }

    private static String functionBlock(String source, String functionName) {
        String functionStart = "function " + functionName + "()";
        int start = source.indexOf(functionStart);
        if (start < 0) {
            return "";
        }
        int nextFunction = source.indexOf("\nfunction ", start + functionStart.length());
        if (nextFunction < 0) {
            return source.substring(start);
        }
        return source.substring(start, nextFunction);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int searchFrom = 0;
        while (true) {
            int found = text.indexOf(needle, searchFrom);
            if (found < 0) {
                return count;
            }
            count++;
            searchFrom = found + needle.length();
        }
    }
}
