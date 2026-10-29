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
package io.github.carlos_emr.carlos.utility;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.jasper.servlet.JasperInitializer;
import org.apache.logging.log4j.Level;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.dispatcher.filter.StrutsExecuteFilter;
import org.apache.struts2.dispatcher.filter.StrutsPrepareFilter;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.model.AbstractModel;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.log.LogActionTestHooks;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.utility.WebXmlFilterMappings.Mapping;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * Runs the production web.xml filter order in a real Tomcat with the real Struts prepare/execute
 * filters, real compiled JSPs, the real {@link DbConnectionFilter} and the real
 * {@link UserActivityFilter} (issue #5305). Every other filter is replaced by a pass-through
 * probe with the same name, URL patterns and dispatchers, which records on its way out whether
 * the request's deliberately unclosed JDBC resources had been released yet.
 *
 * <p>A second context runs the pre-#5305 order (both filters mapped after
 * {@code struts2-execute}) as a control: Struts actions there must still leak and bypass the
 * inactivity policy, which shows these tests detect the defect rather than pass vacuously.</p>
 */
@Tag("integration")
@Tag("filter")
@DisplayName("Request-boundary filters around real Struts actions, JSP forwards and servlets")
public class RequestBoundaryFilterOrderIntegrationTest {

    private static final String PRODUCTION = "/carlos";
    private static final String LEGACY = "/legacy";
    private static final String STRUTS_CONFIG = "struts-default.xml,struts-request-boundary-test.xml";
    private static final Duration WAIT = Duration.ofSeconds(10);

    private static final List<Mapping> PRODUCTION_ORDER = WebXmlFilterMappings.production();
    private static final List<Mapping> LEGACY_ORDER = legacyOrder(PRODUCTION_ORDER);

    private static Path baseDir;
    private static Tomcat tomcat;
    private static int port;
    private static OscarTrackingBasicDataSource dataSource;
    private static BeanFactory previousBeanFactory;
    private static OscarLogDao oscarLogDao;

    @BeforeAll
    static void startTomcat() throws Exception {
        baseDir = Files.createTempDirectory("carlos-request-boundary");
        Path docBase = Files.createDirectories(baseDir.resolve("webapp"));
        copyResource("request-boundary-webapp/WEB-INF/jsp/boundary/leak.jsp", docBase);
        copyResource("request-boundary-webapp/WEB-INF/jsp/boundary/leak-then-fail.jsp", docBase);

        dataSource = new OscarTrackingBasicDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        // OscarTrackingBasicDataSource appends serverTimezone unless the URL names it; H2 ignores it.
        dataSource.setUrl("jdbc:h2:mem:request-boundary-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;IGNORE_UNKNOWN_SETTINGS=TRUE;serverTimezone=UTC");
        dataSource.setMaxTotal(32);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE boundary_note (id INT AUTO_INCREMENT PRIMARY KEY, note VARCHAR(40))");
        }
        OscarTrackingBasicDataSource.releaseThreadConnections();
        Probe.dataSource = dataSource;
        Probe.transactionManager = new DataSourceTransactionManager(dataSource);

        oscarLogDao = Mockito.mock(OscarLogDao.class);
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("dataSource", dataSource);
        beans.registerSingleton("oscarLogDao", oscarLogDao);
        previousBeanFactory = swapSpringBeanFactory(beans);
        LogActionTestHooks.useOscarLogDao(oscarLogDao);

        tomcat = new Tomcat();
        tomcat.setBaseDir(Files.createDirectories(baseDir.resolve("tomcat")).toString());
        tomcat.setPort(0);
        tomcat.getConnector();
        tomcat.getHost().setAutoDeploy(false);
        addContext(PRODUCTION, docBase, PRODUCTION_ORDER);
        addContext(LEGACY, docBase, LEGACY_ORDER);
        tomcat.start();
        port = tomcat.getConnector().getLocalPort();
    }

    @AfterAll
    static void stopTomcat() throws Exception {
        try {
            if (tomcat != null) {
                tomcat.stop();
                tomcat.destroy();
            }
        } finally {
            LogActionTestHooks.useOscarLogDao(null);
            LogActionTestHooks.resetExecutorService();
            swapSpringBeanFactory(previousBeanFactory);
            if (dataSource != null) {
                dataSource.close();
            }
            deleteRecursively(baseDir);
        }
    }

    @BeforeEach
    void resetProbe() {
        Probe.reset();
    }

    @Nested
    @DisplayName("legacy JDBC cleanup")
    class Cleanup {

        @Test
        @DisplayName("should release what a Struts action and its forwarded JSP leave open, once, outside every application filter")
        void shouldReleaseLeaksOnce_whenStrutsActionForwardsToJsp() throws Exception {
            HttpResponse<String> response = get(newClient(), PRODUCTION + "/boundary/forwardToJsp");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("rendered");
            assertReleasedOnceAtOuterBoundary("/boundary/forwardToJsp", 4);
            assertThat(Probe.exitsFor("FORWARD")).as("the JSP forward runs inside the boundary").isNotEmpty()
                    .allSatisfy(exit -> assertThat(exit.released()).isFalse());
        }

        @Test
        @DisplayName("should release what a failing Struts action leaves open")
        void shouldReleaseLeaks_whenStrutsActionThrows() throws Exception {
            HttpResponse<String> response = get(newClient(), PRODUCTION + "/boundary/actionFails");

            assertThat(response.statusCode()).isEqualTo(500);
            assertReleasedOnceAtOuterBoundary("/boundary/actionFails", 3);
        }

        @Test
        @DisplayName("should release what a forwarded JSP leaves open when the JSP throws")
        void shouldReleaseLeaks_whenForwardedJspThrows() throws Exception {
            HttpResponse<String> response = get(newClient(), PRODUCTION + "/boundary/jspFails");

            assertThat(response.statusCode()).isEqualTo(500);
            assertReleasedOnceAtOuterBoundary("/boundary/jspFails", 4);
        }

        @Test
        @DisplayName("should release what a servlet leaves open")
        void shouldReleaseLeaks_whenServletSucceeds() throws Exception {
            HttpResponse<String> response = get(newClient(), PRODUCTION + "/servlet/boundary-leak");

            assertThat(response.statusCode()).isEqualTo(200);
            assertReleasedOnceAtOuterBoundary("/servlet/boundary-leak", 3);
            assertThat(Probe.exitsFor("REQUEST")).as("servlet requests pass through Struts to the later filters")
                    .anyMatch(exit -> indexOf(exit.filterName()) > indexOf("struts2-execute"));
        }

        @Test
        @DisplayName("should release what a failing servlet leaves open")
        void shouldReleaseLeaks_whenServletThrows() throws Exception {
            HttpResponse<String> response = get(newClient(), PRODUCTION + "/servlet/boundary-fail");

            assertThat(response.statusCode()).isEqualTo(500);
            assertReleasedOnceAtOuterBoundary("/servlet/boundary-fail", 3);
        }

        @Test
        @DisplayName("should release what a servlet and the JSP it forwards to leave open")
        void shouldReleaseLeaks_whenServletForwardsToJsp() throws Exception {
            HttpResponse<String> response = get(newClient(), PRODUCTION + "/servlet/boundary-forward");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("rendered");
            assertReleasedOnceAtOuterBoundary("/servlet/boundary-forward", 4);
        }

        @Test
        @DisplayName("should leave a transaction's connection to the transaction and release the rest after it commits")
        void shouldPreserveTransactionConnection_whenLegacyResourcesAreReleasedMidTransaction() throws Exception {
            try (LogCapture legacyLog = LogCapture.forLogger(LegacyJdbcQuery.class);
                 LogCapture trackingLog = LogCapture.forLogger(OscarTrackingBasicDataSource.class)) {
                HttpResponse<String> response = get(newClient(), PRODUCTION + "/boundary/transaction");

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).isEqualTo("committed");
                assertThat(Probe.transactionConnectionOpenAfterInnerRelease.get())
                        .as("an inner legacy release must not close the transaction's connection").isEqualTo(1);
                assertReleasedOnceAtOuterBoundary("/boundary/transaction", 5);
                assertThat(committedNotes()).containsExactly("before-inner-release", "after-inner-release");
                assertThat(legacyLog.events()).noneMatch(e -> e.getLevel().isMoreSpecificThan(Level.ERROR));
                assertThat(trackingLog.events()).noneMatch(e -> e.getLevel().isMoreSpecificThan(Level.ERROR));
            }
        }

        @Test
        @DisplayName("control: the pre-#5305 order leaves a Struts action's leaks open")
        void shouldLeaveLeaksOpen_whenCleanupIsMappedAfterStrutsExecute() throws Exception {
            try {
                HttpResponse<String> response = get(newClient(), LEGACY + "/boundary/forwardToJsp");

                assertThat(response.statusCode()).isEqualTo(200);
                awaitOutermostExit();
                assertThat(Probe.cleanupRuns).as("Struts never reaches a filter mapped after it").isEmpty();
                assertThat(Probe.leaked).hasSize(4).allSatisfy(c -> assertThat(isClosed(c)).isFalse());
                assertThat(dataSource.getNumActive()).isGreaterThan(0);
            } finally {
                Probe.closeLeaked();
            }
        }

        private void assertReleasedOnceAtOuterBoundary(String path, int expectedLeaks) {
            awaitOutermostExit();
            assertThat(Probe.cleanupRuns)
                    .as("DbConnectionFilter must run exactly once, on the REQUEST dispatch")
                    .containsExactly("REQUEST " + PRODUCTION + path);
            assertThat(Probe.leaked).hasSize(expectedLeaks).allSatisfy(c -> assertThat(isClosed(c)).isTrue());
            await(() -> dataSource.getNumActive() == 0, "every pooled connection returned");

            int cleanup = indexOf("DbConnectionFilter");
            List<ProbeExit> requestExits = Probe.exitsFor("REQUEST");
            assertThat(requestExits).anyMatch(exit -> indexOf(exit.filterName()) < cleanup);
            assertThat(requestExits).anyMatch(exit -> indexOf(exit.filterName()) > cleanup);
            assertThat(requestExits).allSatisfy(exit -> assertThat(exit.released())
                    .as("%s (chain position %d, cleanup at %d)", exit.filterName(), indexOf(exit.filterName()), cleanup)
                    .isEqualTo(indexOf(exit.filterName()) < cleanup));
        }
    }

    @Nested
    @DisplayName("user activity policy")
    class Activity {

        private ExecutorService auditExecutor;

        @BeforeEach
        void useAuditExecutor() {
            Mockito.clearInvocations(oscarLogDao);
            auditExecutor = Executors.newSingleThreadExecutor();
            LogActionTestHooks.useExecutorService(auditExecutor);
        }

        @AfterEach
        void restoreAuditExecutor() {
            auditExecutor.shutdownNow();
            LogActionTestHooks.resetExecutorService();
        }

        @Test
        @DisplayName("should not create a session for an anonymous Struts action or servlet request")
        void shouldNotCreateSession_whenAnonymousRequestsPassThrough() throws Exception {
            HttpClient client = newClient();

            HttpResponse<String> action = get(client, PRODUCTION + "/boundary/activity");
            HttpResponse<String> servlet = get(client, PRODUCTION + "/servlet/boundary-session?read=true");

            assertThat(action.statusCode()).isEqualTo(200);
            assertThat(action.headers().allValues("Set-Cookie")).isEmpty();
            assertThat(servlet.headers().allValues("Set-Cookie")).isEmpty();
            assertThat(servlet.body()).isEqualTo("no-session");
        }

        @Test
        @DisplayName("should start the timer on the first request, then count only the user's own Struts requests")
        void shouldRecordActivity_onlyForUserStrutsRequests() throws Exception {
            HttpClient client = newClient();
            assertThat(get(client, PRODUCTION + "/servlet/boundary-session?user=999998").body()).isEqualTo("created");
            long beforeFirst = System.currentTimeMillis();
            long started = Long.parseLong(readLastActivity(client));
            assertThat(started).as("the first authenticated request, a poll here, starts the timer")
                    .isGreaterThanOrEqualTo(beforeFirst);

            Thread.sleep(20);
            long before = System.currentTimeMillis();
            assertThat(get(client, PRODUCTION + "/boundary/activity").statusCode()).isEqualTo(200);
            long recorded = Long.parseLong(readLastActivity(client));
            assertThat(recorded).isGreaterThanOrEqualTo(before).isGreaterThan(started);

            assertThat(get(client, PRODUCTION + "/boundary/activity?autoRefresh=true").statusCode()).isEqualTo(200);
            assertThat(get(client, PRODUCTION + "/status/SessionHeartbeat?autoRefresh=true").statusCode()).isEqualTo(200);
            assertThat(get(client, PRODUCTION + "/status/SessionHeartbeat").statusCode()).isEqualTo(200);
            assertThat(get(client, PRODUCTION + "/provider/ViewTabAlertsRefresh").statusCode())
                    .as("a poll LoginFilter exempts from its timer").isEqualTo(200);
            assertThat(Long.parseLong(readLastActivity(client))).isEqualTo(recorded);
            assertThat(Probe.actionRuns.get()).isEqualTo(5);
        }

        @Test
        @DisplayName("should stop an inactive session's Struts action, audit once and let the logout flow through")
        void shouldRedirectInactiveSession_beforeStrutsActionRuns() throws Exception {
            HttpClient client = newClient();
            assertThat(get(client, PRODUCTION + "/servlet/boundary-session?user=999998&stale=true").body())
                    .isEqualTo("created");

            HttpResponse<String> action = get(client, PRODUCTION + "/boundary/activity");
            HttpResponse<String> heartbeat = get(client, PRODUCTION + "/status/SessionHeartbeat?autoRefresh=true");

            String logoutTarget = PRODUCTION + "/logoutPage?autoLogout=true&errorMessage=logged%20out%20due%20to%20inactivity";
            assertThat(action.statusCode()).isEqualTo(302);
            assertThat(action.headers().firstValue("Location")).hasValueSatisfying(l -> assertThat(l).endsWith(logoutTarget));
            assertThat(heartbeat.statusCode()).isEqualTo(302);
            assertThat(Probe.actionRuns.get()).as("neither request may reach its action").isZero();

            HttpResponse<String> logoutPage = get(client, PRODUCTION + "/logoutPage?autoLogout=true");
            HttpResponse<String> logout = post(client, PRODUCTION + "/logout");
            assertThat(logoutPage.statusCode()).as("the logout page must not loop").isEqualTo(200);
            assertThat(logout.statusCode()).as("the logout action must not loop").isEqualTo(200);
            assertThat(logout.body()).isEqualTo("logged-out");

            auditExecutor.shutdown();
            assertThat(auditExecutor.awaitTermination(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            @SuppressWarnings("rawtypes")
            ArgumentCaptor<AbstractModel> audit = ArgumentCaptor.forClass(AbstractModel.class);
            verify(oscarLogDao, atLeastOnce()).persist(audit.capture());
            List<OscarLog> inactivity = audit.getAllValues().stream()
                    .filter(OscarLog.class::isInstance).map(OscarLog.class::cast)
                    .filter(log -> UserActivityFilter.INACTIVITY_AUDIT_TEXT.equals(log.getContentId()))
                    .toList();
            assertThat(inactivity).as("one audit row per inactive session").hasSize(1);
            assertThat(inactivity.get(0).getProviderNo()).isEqualTo("999998");
            assertThat(inactivity.get(0).getAction()).isEqualTo(LogConst.LOGOUT);
        }

        @Test
        @DisplayName("control: the pre-#5305 order lets an inactive session's Struts action run")
        void shouldRunAction_whenActivityFilterIsMappedAfterStrutsExecute() throws Exception {
            HttpClient client = newClient();
            get(client, LEGACY + "/servlet/boundary-session?user=999998&stale=true");

            HttpResponse<String> action = get(client, LEGACY + "/boundary/activity");

            assertThat(action.statusCode()).isEqualTo(200);
            assertThat(Probe.actionRuns.get()).isEqualTo(1);
        }

        private String readLastActivity(HttpClient client) throws Exception {
            return get(client, PRODUCTION + "/servlet/boundary-session?read=true&autoRefresh=true").body();
        }
    }

    // ---------------------------------------------------------------- container wiring

    private static void addContext(String path, Path docBase, List<Mapping> order) {
        Context context = tomcat.addContext(path, docBase.toString());
        context.setParentClassLoader(RequestBoundaryFilterOrderIntegrationTest.class.getClassLoader());
        Tomcat.initWebappDefaults(context);
        // The test JSPs use no tag libraries; skip scanning the whole test class path for TLDs.
        StandardJarScanner jarScanner = new StandardJarScanner();
        jarScanner.setScanClassPath(false);
        context.setJarScanner(jarScanner);
        context.addServletContainerInitializer(new JasperInitializer(), null);

        addServlet(context, "boundaryLeak", new LeakingServlet(), "/servlet/boundary-leak");
        addServlet(context, "boundaryFail", new FailingServlet(), "/servlet/boundary-fail");
        addServlet(context, "boundaryForward", new ForwardingServlet(), "/servlet/boundary-forward");
        addServlet(context, "boundarySession", new SessionServlet(), "/servlet/boundary-session");

        List<String> defined = new ArrayList<>();
        for (Mapping mapping : order) {
            if (!defined.contains(mapping.filterName())) {
                FilterDef def = new FilterDef();
                def.setFilterName(mapping.filterName());
                def.setFilter(filterFor(mapping.filterName()));
                if ("struts2-prepare".equals(mapping.filterName())) {
                    def.addInitParameter("config", STRUTS_CONFIG);
                }
                context.addFilterDef(def);
                defined.add(mapping.filterName());
            }
            FilterMap map = new FilterMap();
            map.setFilterName(mapping.filterName());
            mapping.urlPatterns().forEach(map::addURLPatternDecoded);
            mapping.dispatchers().forEach(map::setDispatcher);
            context.addFilterMap(map);
        }
    }

    private static void addServlet(Context context, String name, HttpServlet servlet, String pattern) {
        Tomcat.addServlet(context, name, servlet);
        context.addServletMappingDecoded(pattern, name);
    }

    /** The filters under test run for real; every other mapping becomes a recording pass-through. */
    private static Filter filterFor(String name) {
        return switch (name) {
            case "DbConnectionFilter" -> new CountingDbConnectionFilter();
            case "UserActivityFilter" -> new UserActivityFilter();
            case "struts2-prepare" -> new StrutsPrepareFilter();
            case "struts2-execute" -> new StrutsExecuteFilter();
            default -> new ProbeFilter(name);
        };
    }

    /** The order before #5305: both filters mapped after struts2-execute, everything else unchanged. */
    private static List<Mapping> legacyOrder(List<Mapping> production) {
        List<Mapping> legacy = new ArrayList<>(production);
        Mapping db = WebXmlFilterMappings.mappingsFor(production, "DbConnectionFilter").get(0);
        Mapping activity = WebXmlFilterMappings.mappingsFor(production, "UserActivityFilter").get(0);
        legacy.remove(db);
        legacy.remove(activity);
        int execute = WebXmlFilterMappings.indexOf(legacy, "struts2-execute");
        legacy.add(execute + 1, activity);
        legacy.add(execute + 2, db);
        return List.copyOf(legacy);
    }

    private static int indexOf(String filterName) {
        return WebXmlFilterMappings.indexOf(PRODUCTION_ORDER, filterName);
    }

    // ---------------------------------------------------------------- probes, actions and servlets

    /** A recorded probe exit: which filter, on which dispatch, and whether the leaks were already released. */
    record ProbeExit(String filterName, String dispatcher, boolean released) {
    }

    /**
     * Shared state between the test thread and the request threads. Public because the test JSPs
     * and the Struts actions, which the container loads by name, call it.
     */
    public static final class Probe {
        static volatile OscarTrackingBasicDataSource dataSource;
        static volatile DataSourceTransactionManager transactionManager;
        static final List<Connection> leaked = new CopyOnWriteArrayList<>();
        static final List<ProbeExit> exits = new CopyOnWriteArrayList<>();
        static final List<String> cleanupRuns = new CopyOnWriteArrayList<>();
        static final AtomicInteger actionRuns = new AtomicInteger();
        static final AtomicInteger transactionConnectionOpenAfterInnerRelease = new AtomicInteger();

        private Probe() {
        }

        static void reset() {
            leaked.clear();
            exits.clear();
            cleanupRuns.clear();
            actionRuns.set(0);
            transactionConnectionOpenAfterInnerRelease.set(0);
        }

        /** Leaks one connection of each legacy kind the request-end cleanup must release. */
        @SuppressWarnings("removal")
        static void leakAll() {
            try {
                leaked.add(dataSource.getConnection());
                leaked.add(LegacyJdbcQuery.getConnection());
                leaked.add(DbConnectionFilter.getThreadLocalDbConnection());
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }

        /** Called from the test JSPs: leaks a tracked connection after the Struts forward. */
        public static void leakFromJsp() {
            try {
                leaked.add(dataSource.getConnection());
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }

        static boolean allReleased() {
            return leaked.stream().allMatch(RequestBoundaryFilterOrderIntegrationTest::isClosed);
        }

        static List<ProbeExit> exitsFor(String dispatcher) {
            return exits.stream().filter(e -> e.dispatcher().equals(dispatcher)).toList();
        }

        static void closeLeaked() {
            for (Connection c : leaked) {
                try {
                    c.close();
                } catch (SQLException e) {
                    // The control test only needs the pool back.
                }
            }
        }
    }

    /** Pass-through stand-in for a production filter that records the leak state on its way out. */
    static final class ProbeFilter implements Filter {
        private final String name;

        ProbeFilter(String name) {
            this.name = name;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            try {
                chain.doFilter(request, response);
            } finally {
                Probe.exits.add(new ProbeExit(name, request.getDispatcherType().name(), Probe.allReleased()));
            }
        }
    }

    /** The real DbConnectionFilter, counting how often its try/finally runs and on which dispatch. */
    static final class CountingDbConnectionFilter extends DbConnectionFilter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            Probe.cleanupRuns.add(request.getDispatcherType().name() + " "
                    + ((HttpServletRequest) request).getRequestURI());
            super.doFilter(request, response, chain);
        }
    }

    /** Leaks every legacy resource kind and forwards to a JSP that leaks one more. */
    public static class LeakingAction extends ActionSupport {
        @Override
        public String execute() {
            Probe.actionRuns.incrementAndGet();
            Probe.leakAll();
            return SUCCESS;
        }
    }

    /** Leaks every legacy resource kind, then fails. */
    public static class FailingAction extends ActionSupport {
        @Override
        public String execute() {
            Probe.leakAll();
            throw new IllegalStateException("synthetic action failure after a leak");
        }
    }

    /**
     * Works inside a real Spring transaction through {@link LegacyJdbcQuery}, runs the legacy
     * release while the transaction is still open, keeps using the transaction's connection, and
     * leaves a second legacy connection registered for the request-end cleanup. After the commit
     * it leaks one connection of each kind, which the request-end cleanup must release.
     */
    public static class TransactionalAction extends ActionSupport {
        @Override
        public String execute() throws IOException {
            Probe.actionRuns.incrementAndGet();
            new TransactionTemplate(Probe.transactionManager).executeWithoutResult(status -> {
                try {
                    Connection transactional = LegacyJdbcQuery.getConnection();
                    Probe.leaked.add(transactional);
                    insert(transactional, "before-inner-release");
                    LegacyJdbcQuery.releaseThreadResources();
                    if (!transactional.isClosed()) {
                        Probe.transactionConnectionOpenAfterInnerRelease.incrementAndGet();
                    }
                    insert(transactional, "after-inner-release");
                    Probe.leaked.add(LegacyJdbcQuery.getConnection());
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            });
            // The transaction has closed its own connection; leave non-transactional ones open too.
            Probe.leakAll();
            HttpServletResponse response = ServletActionContext.getResponse();
            response.setContentType("text/plain");
            response.getWriter().write("committed");
            return NONE;
        }

        private static void insert(Connection connection, String note) throws SQLException {
            try (var statement = connection.prepareStatement("INSERT INTO boundary_note (note) VALUES (?)")) {
                statement.setString(1, note);
                statement.executeUpdate();
            }
        }
    }

    /** Stand-in for an ordinary action, the heartbeat and the logout page: counts runs and answers 200. */
    public static class ActivityAction extends ActionSupport {
        @Override
        public String execute() throws IOException {
            Probe.actionRuns.incrementAndGet();
            HttpServletResponse response = ServletActionContext.getResponse();
            response.setContentType("text/plain");
            response.getWriter().write("action-ran");
            return NONE;
        }
    }

    /** Stand-in for /logout: invalidates the session, as Logout2Action does. */
    public static class LogoutAction extends ActionSupport {
        @Override
        public String execute() throws IOException {
            HttpSession session = ServletActionContext.getRequest().getSession(false);
            if (session != null) {
                session.invalidate();
            }
            HttpServletResponse response = ServletActionContext.getResponse();
            response.setContentType("text/plain");
            response.getWriter().write("logged-out");
            return NONE;
        }
    }

    static final class LeakingServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            Probe.leakAll();
            response.setContentType("text/plain");
            response.getWriter().write("servlet-ok");
        }
    }

    static final class FailingServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException {
            Probe.leakAll();
            throw new ServletException("synthetic servlet failure after a leak");
        }
    }

    static final class ForwardingServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            Probe.leakAll();
            request.getRequestDispatcher("/WEB-INF/jsp/boundary/leak.jsp").forward(request, response);
        }
    }

    /**
     * Test session control: {@code user=} creates an authenticated session (optionally with a
     * stale last-activity time), {@code read=true} reports the recorded last activity.
     */
    static final class SessionServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setContentType("text/plain");
            if (request.getParameter("user") != null) {
                HttpSession session = request.getSession(true);
                session.setMaxInactiveInterval(7200);
                session.setAttribute("user", request.getParameter("user"));
                if (Boolean.parseBoolean(request.getParameter("stale"))) {
                    session.setAttribute(UserActivityFilter.LAST_USER_ACTIVITY,
                            System.currentTimeMillis() - TimeUnit.HOURS.toMillis(3));
                }
                response.getWriter().write("created");
                return;
            }
            HttpSession session = request.getSession(false);
            if (session == null) {
                response.getWriter().write("no-session");
                return;
            }
            Object last = session.getAttribute(UserActivityFilter.LAST_USER_ACTIVITY);
            response.getWriter().write(last == null ? "none" : last.toString());
        }
    }

    // ---------------------------------------------------------------- helpers

    private static HttpClient newClient() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(WAIT)
                .proxy(HttpClient.Builder.NO_PROXY)
                .build();
    }

    private static HttpResponse<String> get(HttpClient client, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(WAIT).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(HttpClient client, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(WAIT).POST(HttpRequest.BodyPublishers.noBody()).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** The response can reach the client a moment before the request thread finishes unwinding. */
    private static void awaitOutermostExit() {
        await(() -> Probe.exits.stream().anyMatch(e -> e.dispatcher().equals("REQUEST")
                && e.filterName().equals(PRODUCTION_ORDER.get(0).filterName())), "the outermost filter to finish");
    }

    private static void await(BooleanSupplier condition, String description) {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out waiting for " + description);
            }
            Thread.onSpinWait();
        }
    }

    private static boolean isClosed(Connection connection) {
        try {
            return connection.isClosed();
        } catch (SQLException e) {
            return true;
        }
    }

    private static List<String> committedNotes() throws SQLException {
        List<String> notes = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT note FROM boundary_note ORDER BY id")) {
            while (rs.next()) {
                notes.add(rs.getString(1));
            }
        } finally {
            OscarTrackingBasicDataSource.releaseThreadConnections();
        }
        return notes;
    }

    private static BeanFactory swapSpringBeanFactory(BeanFactory replacement) throws ReflectiveOperationException {
        Field field = SpringUtils.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        BeanFactory previous = (BeanFactory) field.get(null);
        field.set(null, replacement);
        return previous;
    }

    private static void copyResource(String resource, Path docBase) throws IOException {
        String relative = resource.substring("request-boundary-webapp/".length());
        Path target = docBase.resolve(relative);
        Files.createDirectories(target.getParent());
        try (InputStream in = RequestBoundaryFilterOrderIntegrationTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource " + resource);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
