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
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the migrated root login/error pages onto Struts-backed routes and
 * internal WEB-INF views.
 *
 * @since 2026-04-15
 */
@DisplayName("Login JSP migration regressions")
@Tag("unit")
@Tag("security")
class LoginJspMigrationRegressionTest {

    /**
     * Matches legacy public error JSP references such as "/errorpage.jsp" while
     * intentionally excluding migrated internal paths like
     * "/WEB-INF/jsp/error/errorpage.jsp".
     */
    private static final Pattern LEGACY_PUBLIC_ERROR_JSP_REFERENCE =
            Pattern.compile("(?<![A-Za-z0-9])/(errorpage|failure|securityError|500)\\.jsp(?=[\"'<\\s]|$)");
    private static final Path STRUTS_LOGIN_XML =
            Path.of("src/main/webapp/WEB-INF/classes/struts-login.xml");
    private static final Path STRUTS_XML =
            Path.of("src/main/webapp/WEB-INF/classes/struts.xml");
    private static final Path STRUTS_INTEGRATION_XML =
            Path.of("src/main/webapp/WEB-INF/classes/struts-integration.xml");
    private static final Path STRUTS_CONFIG_DIRECTORY =
            Path.of("src/main/webapp/WEB-INF/classes");
    private static final Path WEB_XML =
            Path.of("src/main/webapp/WEB-INF/web.xml");
    private static final Path CSRF_GUARD =
            Path.of("src/main/webapp/WEB-INF/Owasp.CsrfGuard.properties");
    private static final Path MENU_CONFIG =
            Path.of("src/main/webapp/WEB-INF/menu-config.xml");
    private static final Path JAVA_SOURCE_DIRECTORY =
            Path.of("src/main/java");
    private static final Path ERROR_PAGE_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/error/errorpage.jsp");
    private static final Path FAILURE_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/error/failure.jsp");
    private static final Path SECURITY_ERROR_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/error/securityError.jsp");

    @Test
    @DisplayName("struts login config should expose the migrated page actions and internal view targets")
    void strutsLoginConfigShouldExposeMigratedPageActions() throws IOException {
        String struts = Files.readString(STRUTS_LOGIN_XML, StandardCharsets.UTF_8);

        assertThat(struts).contains("<action name=\"index\"");
        assertThat(struts).contains("<action name=\"logoutPage\"");
        assertThat(struts).contains("<action name=\"loginfailed\"");
        assertThat(struts).contains("<action name=\"forcepasswordreset\"");
        assertThat(struts).contains("<action name=\"location\"");
        assertThat(struts).contains("<action name=\"select_facility\"");
        assertThat(struts).contains("<action name=\"securityError\"");
        assertThat(struts).contains("<action name=\"errorpage\"");
        assertThat(struts).contains("<action name=\"failure\"");
        assertThat(struts).contains("<action name=\"closenreload\"");
        assertThat(struts).contains("/WEB-INF/jsp/login/index.jsp");
        assertThat(struts).contains("/WEB-INF/jsp/error/errorpage.jsp");
        assertThat(struts).contains("/WEB-INF/jsp/common/closenreload.jsp");
        assertThat(struts).doesNotContain("/WEB-INF/jsp/error/WEB-INF/jsp/error/");
        assertThat(struts).doesNotContain("/WEB-INF/jsp/common/WEB-INF/jsp/common/");
    }

    @Test
    @DisplayName("welcome page and error handling should point at the new action and WEB-INF error view")
    void webXmlShouldUseMigratedLoginAndErrorTargets() throws IOException {
        String webXml = Files.readString(WEB_XML, StandardCharsets.UTF_8);
        String strutsXml = Files.readString(STRUTS_XML, StandardCharsets.UTF_8);
        int loginFilterIndex = webXml.indexOf("<filter-name>LoginFilter</filter-name>");
        int sectionRootCompatibilityIndex =
                webXml.indexOf("<filter-name>SectionRootCompatibilityFilter</filter-name>", loginFilterIndex);
        int prepareIndex = webXml.indexOf("<filter-name>struts2-prepare</filter-name>");
        int loggedInUserIndex = webXml.indexOf("<filter-name>LoggedInUserFilter</filter-name>", prepareIndex);
        int executeIndex = webXml.indexOf("<filter-name>struts2-execute</filter-name>");

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
        assertThat(webXml).doesNotContain("StrutsPrepareAndExecuteFilter");
        assertThat(loginFilterIndex).isGreaterThan(-1);
        assertThat(sectionRootCompatibilityIndex).isGreaterThan(loginFilterIndex);
        assertThat(prepareIndex).isGreaterThan(-1);
        assertThat(loggedInUserIndex).isGreaterThan(prepareIndex);
        assertThat(executeIndex).isGreaterThan(loggedInUserIndex);
        assertThat(strutsXml).contains("woff|woff2|ttf");
        assertThat(strutsXml).contains("html|htm");
        assertThat(strutsXml).contains("^(/[^/]+)?/WEB-INF/.*");
    }

    @Test
    @DisplayName("administration section home should keep the clean public route")
    void administrationSectionHomeShouldKeepTheCleanPublicRoute() throws IOException {
        String integrationStruts = Files.readString(STRUTS_INTEGRATION_XML, StandardCharsets.UTF_8);
        String menuConfig = Files.readString(MENU_CONFIG, StandardCharsets.UTF_8);
        String personaService = Files.readString(
                Path.of("src/main/java/io/github/carlos_emr/carlos/webserv/rest/PersonaService.java"),
                StandardCharsets.UTF_8);
        String mainMenu = Files.readString(
                Path.of("src/main/webapp/WEB-INF/jsp/provider/mainMenu.jsp"),
                StandardCharsets.UTF_8);

        assertThat(integrationStruts).contains("<action name=\"administration/index\"");
        assertThat(integrationStruts).contains("/WEB-INF/jsp/administration/index.jsp");
        assertThat(menuConfig).doesNotContain("/administration/index");
        assertThat(personaService).contains("../administration/");
        assertThat(personaService).doesNotContain("../administration/index");
        assertThat(mainMenu).contains("/administration/");
        assertThat(mainMenu).doesNotContain("/administration/index");
    }

    @Test
    @DisplayName("representative public callers should use migrated login routes")
    void representativePublicCallersShouldUseMigratedLoginRoutes() throws IOException {
        String csrfGuard = Files.readString(CSRF_GUARD, StandardCharsets.UTF_8);
        String menuConfig = Files.readString(MENU_CONFIG, StandardCharsets.UTF_8);

        assertThat(csrfGuard).contains("%servletContext%/index");
        assertThat(csrfGuard).contains("%servletContext%/logoutPage");
        assertThat(csrfGuard).contains("%servletContext%/errorpage");
        assertThat(menuConfig).doesNotContain("location=\"index.jsp\"");
    }

    @Test
    @DisplayName("struts configs and Java callers should not keep public root error JSP references")
    void migratedErrorCallersShouldNotKeepPublicRootJspReferences() throws IOException {
        List<String> strutsOffenders = findContainingLines(
                STRUTS_CONFIG_DIRECTORY,
                path -> path.getFileName().toString().startsWith("struts-")
                        && path.getFileName().toString().endsWith(".xml"));
        List<String> javaOffenders = findContainingLines(
                JAVA_SOURCE_DIRECTORY,
                path -> path.getFileName().toString().endsWith(".java"));

        assertThat(strutsOffenders)
                .as("all Struts configs should point at /WEB-INF/jsp/error/* and never the old public JSPs")
                .isEmpty();
        assertThat(javaOffenders)
                .as("Java redirect and dispatcher callers should not reference deleted public error JSPs")
                .isEmpty();
    }

    @Test
    @DisplayName("error JSPs should keep safe navigation links and OWASP HTML encoding")
    void errorJspViewsShouldKeepSafeNavigationLinksAndOwaspHtmlEncoding() throws IOException {
        String errorPage = Files.readString(ERROR_PAGE_JSP, StandardCharsets.UTF_8);
        String failurePage = Files.readString(FAILURE_JSP, StandardCharsets.UTF_8);
        String securityErrorPage = Files.readString(SECURITY_ERROR_JSP, StandardCharsets.UTF_8);

        assertThat(errorPage).contains("href=\"#\" onclick=\"window.history.back();\"");
        assertThat(errorPage).contains("href=\"${e:forUri(pageContext.request.contextPath)}/provider/providercontrol\"");
        assertThat(errorPage).contains("href=\"${e:forHtmlAttribute(LoginResourceBean.supportLink)}\"");
        assertThat(errorPage).contains("src=\"${e:forUri(pageContext.request.contextPath)}/loginResource/supportLogo.png\"");
        assertThat(failurePage).contains("import=\"org.owasp.encoder.Encode\"");
        assertThat(failurePage).contains("Encode.forHtml(error)");
        assertThat(securityErrorPage).contains("org.owasp.encoder.Encode");
        assertThat(securityErrorPage).contains("Encode.forHtml(val)");
        assertThat(securityErrorPage).contains("Encode.forHtml(error)");
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

    private static List<String> findContainingLines(Path root, Predicate<Path> fileFilter) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(fileFilter)
                    .flatMap(LoginJspMigrationRegressionTest::readLines)
                    .filter(LoginJspMigrationRegressionTest::containsLegacyPublicErrorJspReference)
                    .toList();
        }
    }

    private static boolean containsLegacyPublicErrorJspReference(String line) {
        return LEGACY_PUBLIC_ERROR_JSP_REFERENCE.matcher(line).find();
    }

    private static Stream<String> readLines(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).lines()
                    .map(line -> path + ": " + line.trim());
        }
        catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }
}
