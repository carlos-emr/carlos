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
package io.github.carlos_emr.carlos.clinical.summary.web;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.app.CarlosCsrfGuardFilter;
import io.github.carlos_emr.carlos.casemgmt.model.*;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.clinical.summary.*;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.*;
import java.nio.file.*;
import java.util.*;
import org.apache.catalina.startup.Tomcat;
import org.apache.struts2.ActionContext;
import org.owasp.csrfguard.CsrfGuard;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;

/** Real JSP/action/service/CSRF code, synthetic login/model/in-memory persistence. Never an EMR deployment. */
public final class ChartUpdatesBrowserHarness {
    private static final String HISTORY = "History: suspected asthma; diagnosis not yet confirmed.";
    private static final String FOLLOWUP = "Plan: review in two weeks if symptoms persist.";
    private static final String SOURCE = HISTORY + "\n" + FOLLOWUP
            + "\nUntrusted document text: <script>window.sourceExecuted=true</script>";

    static final class Fixture {
        String source = SOURCE;
        int revision;
        boolean unavailable;
        boolean originalMissing;
        boolean broad;
        boolean emptyCoverage;
        int reminders;
        int histories;
        final Map<String, ChartUpdateReceipt> receipts = new HashMap<>();
        final List<ChartUpdateContext.Entry> entries = new ArrayList<>(List.of(
                historyEntry("note-1", "history", "Previous clinician entry: seasonal symptoms.")));
        ChartUpdateContext.Snapshot snapshot() {
            return new ChartUpdateContext.Snapshot(42, 3001, "Patient, Synthetic", "Synthetic referral", "2026-09-28",
                    source, ChartUpdateProposals.hash(source), "revision-" + revision, "10016", "1", List.copyOf(entries));
        }
    }

    private static ChartUpdateContext.Entry historyEntry(String id, String kind, String text) {
        return new ChartUpdateContext.Entry(id, kind, text, text, "", "", Set.of("Concerns"));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Expected isolated base, isolated webroot and CSRF properties");
        var csrf = new Properties();
        try (var input = Files.newInputStream(Path.of(args[2]))) { csrf.load(input); }
        // Use the checked-in settings directly, without a running installation's property overlays.
        csrf.setProperty("org.owasp.csrfguard.configuration.provider.factory",
                "org.owasp.csrfguard.config.PropertiesConfigurationProviderFactory");
        CsrfGuard.load(csrf);
        var tomcat = new Tomcat();
        tomcat.setBaseDir(args[0]);
        tomcat.setPort(0);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        var context = tomcat.addWebapp("/carlos", args[1]);
        context.setParentClassLoader(ChartUpdatesBrowserHarness.class.getClassLoader());
        context.setContainerSciFilter("^(?!org\\.apache\\.jasper\\.servlet\\.JasperInitializer$).*");
        context.setIgnoreAnnotations(true);
        ((org.apache.tomcat.util.scan.StandardJarScanner) context.getJarScanner()).setScanClassPath(false);
        Tomcat.addServlet(context, "synthetic-chart-updates", new HttpServlet() {
            @Override protected void service(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException, jakarta.servlet.ServletException {
                request.setCharacterEncoding("UTF-8");
                var session = request.getSession();
                synchronized (session) {
                    if (session.getAttribute("fixture") == null) session.setAttribute("fixture", new Fixture());
                }
                new CarlosCsrfGuardFilter().doFilter(request, response, (req, res) -> {
                    try { dispatch((HttpServletRequest) req, (HttpServletResponse) res); }
                    catch (Exception failure) { throw new jakarta.servlet.ServletException(failure); }
                });
            }
        });
        context.addServletMappingDecoded("/documentManager/*", "synthetic-chart-updates");
        context.addServletMappingDecoded("/fixture/*", "synthetic-chart-updates");
        tomcat.start();
        System.out.println("CHART_UPDATE_BROWSER_URL=http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + "/carlos");
        System.out.flush();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { tomcat.stop(); tomcat.destroy(); } catch (Exception ignored) { }
        }));
        tomcat.getServer().await();
    }

    private static void dispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
        var session = request.getSession();
        var fixture = (Fixture) session.getAttribute("fixture");
        if (request.getServletPath().equals("/documentManager") && request.getPathInfo().equals("/ViewDocumentReport")) {
            request.getRequestDispatcher("/fixture-picker.jsp").forward(request, response);
            return;
        }
        if (request.getServletPath().equals("/fixture")) {
            if (request.getPathInfo().equals("/echart") && request.getMethod().equals("GET")) {
                request.getRequestDispatcher("/fixture-echart.jsp").forward(request, response);
                return;
            }
            if (request.getPathInfo().equals("/picker") && request.getMethod().equals("GET")) {
                request.getRequestDispatcher("/fixture-picker.jsp").forward(request, response);
                return;
            }
            if (request.getPathInfo().equals("/original") && request.getMethod().equals("GET")) {
                if (fixture.originalMissing) { response.sendError(404); return; }
                response.setContentType("text/plain");
                response.getWriter().write("Readable synthetic original");
                return;
            }
            if (request.getPathInfo().equals("/native-form") && request.getMethod().equals("GET")) {
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().write("<!doctype html><title>Native form fixture</title><p>Patient #3001</p><input name='demographic_no' value='3001' readonly><input aria-label='Native draft'>");
                return;
            }
            if (request.getPathInfo().equals("/stats") && request.getMethod().equals("GET")) {
                response.setContentType("application/json");
                response.getWriter().write("{\"reminders\":" + fixture.reminders + ",\"histories\":"
                        + fixture.histories + ",\"receipts\":" + fixture.receipts.size() + "}");
                return;
            }
            if (!request.getMethod().equals("POST")) { response.sendError(405); return; }
            switch (request.getPathInfo()) {
                case "/chart-change" -> {
                    fixture.revision++;
                    fixture.entries.add(historyEntry("note-external", "history", "Synthetic concurrent chart edit."));
                }
                case "/empty-coverage" -> fixture.emptyCoverage = true;
                case "/broad" -> {
                    fixture.broad = true;
                    fixture.source = "Social History\nLives with daughter.\nAllergies: none.\nMedications: Drug A 5 mg daily.\nImmunization: influenza given.\nHypertension\n\nUnselected finding <img src=x onerror=window.auditExecuted=true>.";
                    fixture.entries.add(new ChartUpdateContext.Entry("note-family-only", "history", "Hypertension", "Hypertension", "", "", Set.of("FamHistory")));
                    fixture.revision++;
                }
                case "/repeat-source" -> { fixture.source += "\n" + FOLLOWUP; fixture.revision++; }
                case "/paraphrased-chart" -> {
                    fixture.entries.add(historyEntry("note-paraphrase", "history",
                            "No asthma.\nLeft knee osteoarthritis.\nHTN"));
                    fixture.entries.add(historyEntry("note-markup", "history",
                            "Synthetic <img src=x onerror=window.matchExecuted=true> entry"));
                    fixture.entries.add(historyEntry("note-family", "history", "Family history:\nHTN"));
                    fixture.entries.add(historyEntry("note-negated", "history", "No hypertension"));
                    fixture.revision++;
                }
                case "/matching-chart" -> {
                    fixture.entries.add(historyEntry("note-duplicate", "history",
                            HISTORY.toUpperCase(java.util.Locale.ROOT).replace(" ", "  \n")));
                    fixture.revision++;
                }
                case "/source-change" -> { fixture.source += "\nSynthetic document amendment."; fixture.revision++; }
                case "/unavailable" -> fixture.unavailable = true;
                case "/missing-original" -> fixture.originalMissing = true;
                case "/available" -> { fixture.unavailable = false; fixture.originalMissing = false; }
                case "/clear-timing" -> { fixture.source = fixture.source.replace(FOLLOWUP, "Plan: review in four weeks."); fixture.revision++; }
                case "/expire" -> ReflectionTestUtils.setField(session.getAttribute(ChartUpdateReview.SESSION_KEY), "expiresAt", 0L);
                default -> { response.sendError(404); return; }
            }
            response.setStatus(204);
            return;
        }
        var properties = mock(CarlosProperties.class);
        when(properties.getProperty(anyString(), eq("false"))).thenReturn("true");
        // Initializing LogAction requires its DAO, even though audit effects are suppressed here.
        try (var spring = mockStatic(SpringUtils.class); var settings = mockStatic(CarlosProperties.class)) {
            spring.when(() -> SpringUtils.getBean(OscarLogDao.class)).thenReturn(mock(OscarLogDao.class));
            settings.when(CarlosProperties::getInstance).thenReturn(properties);
            try (var logs = mockStatic(LogAction.class)) {
                var provider = new Provider();
                provider.setProviderNo("101");
                provider.setFirstName("Synthetic");
                provider.setLastName("Clinician");
                var user = mock(LoggedInInfo.class);
                when(user.getLoggedInProviderNo()).thenReturn("101");
                when(user.getLoggedInProvider()).thenReturn(provider);
                when(user.getSession()).thenReturn(session);
                session.setAttribute(new LoggedInInfo().getLoggedInInfoKey(), user);
                var providers = mock(ProviderDao.class);
                when(providers.getActiveProviders()).thenReturn(List.of(provider));
                var chart = mock(ChartUpdateContext.class);
                when(chart.writableSections(user, 3001)).thenReturn(ChartUpdateSections.CODES);
                when(chart.load(user, 42)).thenAnswer(call -> {
                    if (fixture.originalMissing) throw new ChartUpdateContext.OriginalDocumentMissingException();
                    if (fixture.unavailable) throw new IllegalStateException("Document text is unavailable. Reopen the original.");
                    return fixture.snapshot();
                });
                var receipts = new ChartUpdateReceiptStore() {
                    @Override public void requireTransactionalTables(boolean history, boolean legacy) { }
                    @Override public void lockPatient(int patient) { }
                    @Override public void requireNoteLock(LoggedInInfo actor, int patient) { }
                    @Override public ChartUpdateReceipt find(String key) { return fixture.receipts.get(key); }
                    @Override public void save(ChartUpdateReceipt receipt) {
                        fixture.receipts.put((String) ReflectionTestUtils.getField(receipt, "key"), receipt);
                    }
                };
                var ticklers = mock(TicklerManager.class);
                when(ticklers.addTickler(eq(user), any())).thenAnswer(call -> {
                    Tickler tickler = call.getArgument(1);
                    tickler.setId(++fixture.reminders + 100);
                    fixture.entries.add(new ChartUpdateContext.Entry("tickler-" + tickler.getId(), "tickler", tickler.getMessage()));
                    fixture.revision++;
                    return true;
                });
                when(ticklers.addTicklerLink(eq(user), any())).thenReturn(true);
                var notes = mock(CaseManagementManager.class);
                var issue = new Issue();
                issue.setId(7L);
                issue.setRole("doctor");
                when(notes.getIssueByCode(anyString())).thenAnswer(call -> {
                    var selected = new Issue(); selected.setId(7L); selected.setRole("doctor"); selected.setCode(call.getArgument(0));
                    return selected;
                });
                when(notes.getRoleName("101", "10016")).thenReturn("doctor");
                when(notes.saveNote(any(), any(), any(), any(), isNull(), any())).thenAnswer(call -> {
                    CaseManagementNote note = call.getArgument(1);
                    note.setId((long) ++fixture.histories + 200);
                    fixture.entries.add(new ChartUpdateContext.Entry("note-" + note.getId(), "history", note.getNote(), note.getNote(), "", "",
                            Set.of(note.getIssues().iterator().next().getIssue().getCode())));
                    fixture.revision++;
                    return "";
                });
                when(chart.nativeReviewUrl(user, 3001, "Preventions")).thenReturn("/fixture/native-form?demographic_no=3001");
                var generator = mock(ChartUpdateProposals.class);
                List<ChartUpdateProposals.Proposal> generated = List.of(
                        new ChartUpdateProposals.Proposal("tickler", fixture.source.contains(FOLLOWUP) ? FOLLOWUP : "Plan: review in four weeks."), new ChartUpdateProposals.Proposal("history", HISTORY));
                if (fixture.broad) generated = List.of(
                        new ChartUpdateProposals.Proposal("history", "Social History\nLives with daughter.", "SocHistory"),
                        new ChartUpdateProposals.Proposal("review", "Allergies: none.", "Allergies"),
                        new ChartUpdateProposals.Proposal("review", "Medications: Drug A 5 mg daily.", "Medications"),
                        new ChartUpdateProposals.Proposal("review", "Immunization: influenza given.", "Preventions"),
                        new ChartUpdateProposals.Proposal("history", "Hypertension", "MedHistory"));
                ChartUpdateCoverage coverage = null;
                if (fixture.broad) {
                    var audit = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("version", 1);
                    audit.putArray("sections").addObject().put("start", 0).put("end", fixture.source.length());
                    audit.putArray("rejected").addObject().put("evidence", "Unselected finding <img src=x onerror=window.auditExecuted=true>.")
                            .put("reason", "Untrusted explanation <script>window.auditExecuted=true</script>");
                    coverage = ChartUpdateCoverage.parse(audit, fixture.source);
                }
                if (fixture.emptyCoverage) generated = List.of();
                when(generator.generateReport(anyString())).thenReturn(new ChartUpdateProposals.Report(generated, coverage));
                var action = new AiChartUpdates2Action(chart,
                        new ReviewedChartUpdateService(chart, receipts, ticklers, notes, providers), providers, generator);
                ActionContext.of().withServletRequest(request).withServletResponse(response).bind();
                try {
                    String result = switch (request.getPathInfo()) {
                        case "/AiChartUpdates" -> action.execute();
                        case "/GenerateAiChartUpdates" -> action.generate();
                        case "/ApplyAiChartUpdate" -> action.apply();
                        case "/DismissAiChartUpdate" -> action.dismiss();
                        default -> { response.sendError(404); yield "none"; }
                    };
                    if ("success".equals(result)) request.getRequestDispatcher("/WEB-INF/jsp/documentManager/aiChartUpdates.jsp").forward(request, response);
                } finally { ActionContext.clear(); }
            }
        }
    }
}
