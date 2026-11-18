/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.messenger.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.EChartDao;
import io.github.carlos_emr.carlos.login.LoginResourceBean;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.ResponseSanitizationFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.catalina.startup.Tomcat;
import org.apache.jasper.servlet.JasperInitializer;
import org.apache.struts2.ActionContext;
import org.apache.tomcat.util.descriptor.web.ErrorPage;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

/** Runs the Messenger action through a real Tomcat error dispatch and the shared JSP. */
@Tag("integration")
class MsgAttachmentRefusalIntegrationTest {
    private static final String ERROR_PAGE = "/WEB-INF/jsp/error/errorpage.jsp";
    private static final String ATTACH_NOTICE = "One selected attachment is unavailable.";
    private static final String PRIVATE_ERROR = "FAKE technical error detail";
    private static final String EARLIER_ATTACHMENT = "FAKE earlier attachment";
    private static final int PATIENT = 42;
    private static final AtomicBoolean ATTACHMENT_KEPT = new AtomicBoolean();

    @TempDir
    static Path directory;
    private static Tomcat tomcat;

    @BeforeAll
    static void startTomcat() throws Exception {
        Path source = Path.of("src/main/webapp");
        Path web = Files.createDirectory(directory.resolve("web"));
        for (String file : List.of("WEB-INF/carlos-tag.tld", ERROR_PAGE.substring(1))) {
            copy(source.resolve(file), web.resolve(file));
        }
        for (Class<?> type : List.of(jakarta.servlet.jsp.jstl.core.Config.class,
                org.apache.taglibs.standard.tag.rt.fmt.SetBundleTag.class)) {
            Path jar = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
            copy(jar, web.resolve("WEB-INF/lib").resolve(jar.getFileName()));
        }
        tomcat = new Tomcat();
        tomcat.setBaseDir(directory.resolve("tomcat").toString());
        tomcat.setPort(0);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        var context = tomcat.addContext("/carlos", web.toString());
        context.setParentClassLoader(MsgAttachmentRefusalIntegrationTest.class.getClassLoader());
        var scanner = new StandardJarScanner();
        scanner.setScanClassPath(false);
        context.setJarScanner(scanner);
        context.addServletContainerInitializer(new JasperInitializer(), null);
        Tomcat.initWebappDefaults(context);
        var error = new ErrorPage();
        error.setErrorCode(HttpServletResponse.SC_BAD_REQUEST);
        error.setLocation(ERROR_PAGE);
        context.addErrorPage(error);
        var sanitization = new FilterDef();
        sanitization.setFilterName("ResponseSanitizationFilter");
        sanitization.setFilter(new ResponseSanitizationFilter());
        context.addFilterDef(sanitization);
        var sanitizationMapping = new FilterMap();
        sanitizationMapping.setFilterName(sanitization.getFilterName());
        sanitizationMapping.setDispatcher(DispatcherType.REQUEST.name());
        sanitizationMapping.setDispatcher(DispatcherType.ERROR.name());
        sanitizationMapping.addURLPattern("/*");
        context.addFilterMap(sanitizationMapping);

        var branding = new FilterDef();
        branding.setFilterName("branding-fixture");
        branding.setFilter((request, response, chain) -> {
            try (var ignored = mockConstruction(LoginResourceBean.class)) {
                chain.doFilter(request, response);
            }
        });
        context.addFilterDef(branding);
        var brandingMapping = new FilterMap();
        brandingMapping.setFilterName(branding.getFilterName());
        brandingMapping.setDispatcher(DispatcherType.ERROR.name());
        brandingMapping.addURLPattern(ERROR_PAGE);
        context.addFilterMap(brandingMapping);
        Tomcat.addServlet(context, "attachment", new AttachmentServlet());
        context.addServletMapping("/messenger/Doc2PDF", "attachment");
        context.addServletMapping("/ordinary-bad-request", "attachment");
        tomcat.start();
    }

    @AfterAll
    static void stopTomcat() throws Exception {
        if (tomcat != null) {
            tomcat.stop();
            tomcat.destroy();
        }
    }

    @Test
    void shouldShowActionableNotice_forAStaleAttachmentSelection() throws Exception {
        var response = post("/messenger/Doc2PDF", "en");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("CARLOS Error: 400");
        var notice = Jsoup.parse(response.body()).select("#messenger-attachment-unavailable[role=alert]");
        assertThat(notice).singleElement().satisfies(element ->
                assertThat(element.text()).contains(ATTACH_NOTICE, "Reopen the attachment chooser"));
        assertThat(ATTACHMENT_KEPT.get()).isTrue();

        var localized = post("/messenger/Doc2PDF", "fr");
        assertThat(localized.statusCode()).isEqualTo(400);
        assertThat(Jsoup.parse(localized.body()).select("#messenger-attachment-unavailable"))
                .singleElement().satisfies(element -> assertThat(element.text()).contains("indisponible", "Rouvrez"));
    }

    @Test
    void shouldKeepTechnicalDetailsPrivate_forOtherBadRequests() throws Exception {
        var response = post("/ordinary-bad-request?carlosMessengerAttachmentUnavailable=true", "en");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("CARLOS Error: 400").doesNotContain(PRIVATE_ERROR, ATTACH_NOTICE);
    }

    private static HttpResponse<String> post(String route, String language) throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + "/carlos" + route);
        try (HttpClient client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).header("Accept-Language", language)
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private static void copy(Path source, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        Files.copy(source, destination);
    }

    private static final class AttachmentServlet extends HttpServlet {
        private final LoggedInInfo actor = new LoggedInInfo();
        private final SecurityInfoManager security = mock(SecurityInfoManager.class);
        private final DemographicManager demographicManager = mock(DemographicManager.class);
        private final MsgPdfAttachmentResolver resolver = new MsgPdfAttachmentResolver(mock(EChartDao.class));
        private final MsgAttachPDF2Action.PdfConverter converter = mock(MsgAttachPDF2Action.PdfConverter.class);

        private AttachmentServlet() {
            when(security.hasPrivilege(actor, "_msg", "w", null)).thenReturn(true);
            when(security.isAllowedAccessToPatientRecord(actor, PATIENT)).thenReturn(true);
            when(security.hasPrivilege(actor, "_eChart", "r", null)).thenReturn(true);
            when(security.hasPrivilege(actor, "_eChart", "r", PATIENT)).thenReturn(true);
        }

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
            if ("/ordinary-bad-request".equals(request.getServletPath())) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, PRIVATE_ERROR);
                return;
            }
            String key = MsgAttachmentStaging.newKey();
            var session = request.getSession();
            LoggedInInfo.setLoggedInInfoIntoSession(session, actor);
            MsgAttachmentStaging.stagePdfs(session, key, PATIENT, EARLIER_ATTACHMENT);
            ActionContext.of().withServletRequest(request).withServletResponse(response).bind();
            try {
                var action = new MsgAttachPDF2Action(security, demographicManager, resolver,
                        (req, res, route) -> { throw new AssertionError("A stale attachment reached rendering"); }, converter);
                action.setDemographic_no(Integer.toString(PATIENT));
                action.setAttachmentKey(key);
                action.setItem(new String[]{"encounter"});
                action.execute();
            } finally {
                ActionContext.clear();
            }
            ATTACHMENT_KEPT.set(EARLIER_ATTACHMENT.equals(MsgAttachmentStaging.peek(session, key).pdfAttachment()));
        }
    }
}
