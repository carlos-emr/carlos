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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Renders the modern chart layout ({@code newEncounterLayout.jsp}, the {@code setUpMainEncounterPage}
 * result of {@code CaseManagementEntry}) and follows its reference to the note save script.
 * The script now publishes the localized {@code encounter.sessionEndedNotSaved.msg} introduced
 * in #6143. Executing the rendered script and invoking its alert helper verifies the message
 * shown in each supported language. The Node tests ({@code echart-note-save-signed-out.test.js},
 * {@code echart-cpp-issue-refresh.test.js}) exercise when a reply from the sign-in page triggers
 * the warning and leaves typed text on screen.
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
    private static final String NOTE_SCRIPT = "/js/newCaseManagementView.js.jsp";
    private static final String KEY = "encounter.sessionEndedNotSaved.msg";
    private static final String PATIENT = "1001";
    private static final String PROVIDER = "999998";
    private static final String EVALUATE_WARNING = """
            const fs = require('node:fs');
            const vm = require('node:vm');
            const alerts = [];
            const chart = {
              Image: class {},
              addEventListener() {},
              jQuery() {},
              alert(message) { alerts.push(message); }
            };
            chart.window = chart;
            const script = fs.readFileSync(process.argv[1], 'utf8');
            vm.runInNewContext(script + '\\nalertSessionEndedNotSaved();', chart, { timeout: 1000 });
            if (alerts.length !== 1) throw new Error('Expected one signed-out alert');
            process.stdout.write(alerts[0]);
            """;

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
                .jsp(PAGE, NOTE_SCRIPT)
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

        assertThat(openChart(language).select("script[src=\"%s%s\"]".formatted(JspRenderServer.CONTEXT_PATH, NOTE_SCRIPT)))
                .singleElement()
                .satisfies(handler -> assertThat(handler.attr("type")).isIn("", "text/javascript"));

        String script = server.request(NOTE_SCRIPT)
                .header("Accept-Language", language)
                .render(EncounterLayoutSessionEndedMessageJspIntegrationTest::signIn)
                .assertOk()
                .body();
        assertThat(expected).isNotBlank();
        assertThat(evaluateWarning(script)).isEqualTo(expected);
    }

    private static String evaluateWarning(String script) throws Exception {
        Path source = Files.createTempFile(directory, "chart-layout-", ".js");
        Files.writeString(source, script, StandardCharsets.UTF_8);
        Process node = new ProcessBuilder("node", "--input-type=commonjs", "-e", EVALUATE_WARNING, source.toString())
                .redirectErrorStream(true)
                .start();
        if (!node.waitFor(10, TimeUnit.SECONDS)) {
            node.destroyForcibly();
            throw new AssertionError("The rendered chart script did not finish");
        }
        String output = new String(node.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(node.exitValue()).as("rendered chart script output: %s", output).isZero();
        return output;
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

}
