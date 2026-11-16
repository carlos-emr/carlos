/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.report.ClinicalReports;

import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilege;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilegePrimaryKey;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.catalina.startup.Tomcat;
import org.apache.jasper.servlet.JasperInitializer;
import org.apache.jasper.servlet.JspServlet;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real Jasper compilation and HTTP downloads of the production export JSP. */
@Tag("integration")
@Tag("report")
class ClinicalReportExportJspIntegrationTest {
    private static final String NAME = "FAKE report/été; 漢字, \"totals\"";
    private static final String FILENAME = NAME.replace("/", "---").replace(" ", "_");
    private static final String CSV = "'Montréal','12','20','60'";
    @TempDir
    Path directory;

    @Test
    void shouldDownloadOnlyTheReportBytes_withUtf8AndSafeCsvFilenames() throws Exception {
        var previous = SpringUtils.getBeanFactory();
        var beans = new DefaultListableBeanFactory();
        var privileges = mock(SecObjPrivilegeDao.class);
        var grant = new SecObjPrivilege();
        grant.setId(new SecObjPrivilegePrimaryKey("doctor", "_report"));
        grant.setPrivilege("r");
        grant.setPriority(1);
        when(privileges.findByObjectNames(any())).thenReturn(List.of(grant));
        beans.registerSingleton("secObjPrivilegeDao", privileges);
        ReflectionTestUtils.setField(SpringUtils.class, "beanFactory", beans);
        var report = mock(ReportEvaluator.class);
        when(report.getName()).thenReturn(NAME);
        when(report.getCSV()).thenReturn(CSV);
        var tomcat = new Tomcat();
        try {
            Path web = Files.createDirectory(directory.resolve("web"));
            copy(Path.of("src/main/webapp/WEB-INF/jsp/report/reportExport.jsp"), web.resolve("WEB-INF/jsp/report/reportExport.jsp"));
            copy(Path.of("src/main/webapp/WEB-INF/security.tld"), web.resolve("WEB-INF/security.tld"));
            tomcat.setBaseDir(directory.resolve("tomcat").toString());
            tomcat.setPort(0);
            tomcat.getConnector().setProperty("address", "127.0.0.1");
            var context = tomcat.addContext("", web.toString());
            context.setParentClassLoader(getClass().getClassLoader());
            var scanner = new StandardJarScanner();
            scanner.setScanClassPath(false);
            context.setJarScanner(scanner);
            context.addServletContainerInitializer(new JasperInitializer(), null);
            Tomcat.addServlet(context, "jsp", new JspServlet());
            context.addServletMappingDecoded("*.jsp", "jsp");
            Tomcat.addServlet(context, "export", new HttpServlet() {
                @Override
                protected void doGet(HttpServletRequest request, HttpServletResponse response)
                        throws java.io.IOException, jakarta.servlet.ServletException {
                    var session = request.getSession();
                    session.setAttribute("userrole", "doctor");
                    session.setAttribute("user", "FAKE-6056");
                    session.setAttribute("ClinicalReports", new ArrayList<>(List.of(report)));
                    request.getRequestDispatcher("/WEB-INF/jsp/report/reportExport.jsp").forward(request, response);
                }
            });
            context.addServletMappingDecoded("/export", "export");
            tomcat.start();
            URI base = URI.create("http://127.0.0.1:" + tomcat.getConnector().getLocalPort());
            try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
                for (boolean single : List.of(true, false)) {
                    var response = client.send(HttpRequest.newBuilder(base.resolve(single ? "/export?id=0" : "/export"))
                            .timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                    assertThat(response.statusCode()).isEqualTo(200);
                    String body = new String(response.body(), StandardCharsets.UTF_8);
                    if (single) {
                        assertThat(body).isEqualTo(CSV);
                    } else {
                        try (var parser = CSVFormat.DEFAULT.parse(new StringReader(body))) {
                            var records = parser.getRecords();
                            assertThat(records).hasSize(1);
                            assertThat(records.getFirst().size()).isEqualTo(5);
                            assertThat(records.getFirst().get(0)).isEqualTo(FILENAME);
                            assertThat(records.getFirst().get(1)).isEqualTo("'Montréal'");
                        }
                    }
                    assertThat(response.headers().firstValue("Content-Type")).hasValue("text/csv;charset=UTF-8");
                    String disposition = response.headers().firstValue("Content-Disposition").orElseThrow();
                    assertThat(ContentDisposition.parse(disposition).getFilename())
                            .isEqualTo(single ? FILENAME + ".csv" : "report.csv");
                    assertThat(ContentDisposition.parse(disposition).getType()).isEqualTo("attachment");
                }
            }
        } finally {
            try {
                tomcat.stop();
                tomcat.destroy();
            } finally {
                ReflectionTestUtils.setField(SpringUtils.class, "beanFactory", previous);
            }
        }
    }

    private static void copy(Path source, Path destination) throws Exception {
        Files.createDirectories(destination.getParent());
        Files.copy(source, destination);
    }
}
