/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.casemgmt.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Renders the modern chart layout ({@code newEncounterLayout.jsp}, the {@code setUpMainEncounterPage}
 * result of {@code CaseManagementEntry}) and reads the message it gives the note save handler.
 * 0ba9d149d105 (#6143) made the layout publish the localized {@code encounter.sessionEndedNotSaved.msg}
 * as the JavaScript variable {@code sessionEndedNotSavedError}: when a save is answered by the
 * sign-in page, {@code newCaseManagementView.js.jsp} keeps the typed text on screen and alerts that
 * message ({@code alert(sessionEndedNotSavedError || savingNoteError)}). The Node tests
 * ({@code echart-note-save-signed-out.test.js}, {@code echart-cpp-issue-refresh.test.js}) drive the
 * handler with the variable set on a fake window; before #6336 nothing rendered the line that sets
 * it. Each bundle that has the key is checked: the page must assign it once, as a well-formed,
 * JavaScript-escaped string, after the external script that declares the variable.
 *
 * <p>The four chart panels the layout includes ({@code newEncounterHeader.jsp},
 * {@code rightColumn.jsp}, {@code newNavigation.jsp}, {@code newCaseManagementView.jsp}) each need
 * the whole chart's data and are not what this change touched, so they are replaced by empty
 * stand-ins; everything the layout itself prints runs as in production. The layout loads no class
 * that keeps a {@code SpringUtils} bean in a static field ({@code CppPreferencesUIBean} looks its
 * {@code UserPropertyDAO} up per instance).</p>
 */
@Tag("integration")
@Tag("casemgmt")
class EncounterLayoutSessionEndedMessageJspIntegrationTest {
    private static final String PAGE = "/WEB-INF/jsp/casemgmt/newEncounterLayout.jsp";
    private static final String KEY = "encounter.sessionEndedNotSaved.msg";
    private static final String PATIENT = "1001";
    private static final String PROVIDER = "999998";
    /** The assignment the layout prints: {@code sessionEndedNotSavedError = "<JavaScript string>";}. */
    private static final Pattern ASSIGNMENT =
            Pattern.compile("\\bsessionEndedNotSavedError = \"((?:[^\"\\\\\\r\\n]|\\\\.)*)\";");

    @TempDir
    static Path directory;
    private static JspRenderServer server;

    @BeforeAll
    static void startServer() throws Exception {
        UserPropertyDAO userProperties = mock(UserPropertyDAO.class);
        when(userProperties.getProviderPropertiesAsMap(PROVIDER)).thenReturn(Map.of());
        server = JspRenderServer.builder(directory)
                .file("/WEB-INF/jsp/casemgmt/newEncounterHeader.jsp", "<div id=\"stand-in-header\"></div>\n")
                .file("/WEB-INF/jsp/casemgmt/rightColumn.jsp", "<div id=\"stand-in-right-column\"></div>\n")
                .file("/WEB-INF/jsp/casemgmt/newNavigation.jsp", "<div id=\"stand-in-navigation\"></div>\n")
                .file("/WEB-INF/jsp/casemgmt/newCaseManagementView.jsp", "<div id=\"stand-in-notes\"></div>\n")
                .jsp(PAGE)
                .bean(SecObjPrivilegeDao.class, JspRenderServer.privilegeDaoGranting("doctor", "x"))
                .bean(UserPropertyDAO.class, userProperties)
                .start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr-CA", "es", "pt-BR", "pl"})
    void shouldPublishTheLocalizedSessionEndedMessage_forTheSignedOutSave(String language) throws Exception {
        String expected = ResourceBundle.getBundle("oscarResources", Locale.forLanguageTag(language)).getString(KEY);

        Document html = openChart(language);

        List<String> assigned = new ArrayList<>();
        Element assigning = null;
        for (Element script : html.select("script")) {
            Matcher assignment = ASSIGNMENT.matcher(script.data());
            while (assignment.find()) {
                assigned.add(unescapeJavaScript(assignment.group(1)));
                assigning = script;
            }
        }
        assertThat(expected).isNotBlank();
        assertThat(assigned).containsExactly(expected);
        // newCaseManagementView.js.jsp declares "var sessionEndedNotSavedError;" and must load first.
        Elements scripts = html.select("script");
        Element handler = html.selectFirst("script[src$=/js/newCaseManagementView.js.jsp]");
        assertThat(handler).isNotNull();
        assertThat(scripts.indexOf(handler) < scripts.indexOf(assigning)).isTrue();
    }

    private static Document openChart(String language) throws Exception {
        return server.request(PAGE)
                .header("Accept-Language", language)
                .parameter("demographicNo", PATIENT)
                .parameter("providerNo", PROVIDER)
                .parameter("appointmentNo", "0")
                .render(EncounterLayoutSessionEndedMessageJspIntegrationTest::signIn)
                .assertOk()
                .document();
    }

    /** The chart session: role, provider and the login's LoggedInInfo (a doctor, not a resident). */
    private static void signIn(HttpServletRequest request) {
        request.getSession().setAttribute("userrole", "doctor");
        request.getSession().setAttribute("user", PROVIDER);
        Provider provider = new Provider(PROVIDER);
        provider.setProviderType("doctor");
        LoggedInInfo login = new LoggedInInfo();
        login.setLoggedInProvider(provider);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
    }

    /** Decodes the escapes the OWASP JavaScript block encoder writes into a double-quoted literal. */
    private static String unescapeJavaScript(String literal) {
        StringBuilder text = new StringBuilder(literal.length());
        for (int i = 0; i < literal.length(); i++) {
            char c = literal.charAt(i);
            if (c != '\\') {
                text.append(c);
                continue;
            }
            char escaped = literal.charAt(++i);
            switch (escaped) {
                case 'b' -> text.append('\b');
                case 't' -> text.append('\t');
                case 'n' -> text.append('\n');
                case 'f' -> text.append('\f');
                case 'r' -> text.append('\r');
                case 'x' -> {
                    text.append((char) Integer.parseInt(literal.substring(i + 1, i + 3), 16));
                    i += 2;
                }
                case 'u' -> {
                    text.append((char) Integer.parseInt(literal.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> text.append(escaped);
            }
        }
        return text.toString();
    }
}
