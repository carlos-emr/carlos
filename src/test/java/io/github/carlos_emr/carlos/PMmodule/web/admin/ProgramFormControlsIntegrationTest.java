/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.PMmodule.web.admin;

import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.catalina.startup.Tomcat;
import org.apache.jasper.servlet.JasperInitializer;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Compiles the actual Struts result JSPs and their tab includes in Jasper. */
@Tag("integration")
class ProgramFormControlsIntegrationTest {
    private static final String ADMIN = "WEB-INF/jsp/PMmodule/Admin/";
    private static final String BASE_PATH = "/carlos/PMmodule/";
    private static final String FIXTURE_NAME = "Équipe & \"Care\"";

    @TempDir
    static Path directory;
    private static Tomcat tomcat;
    private static BeanFactory previousBeanFactory;

    @BeforeAll
    static void startJasper() throws Exception {
        previousBeanFactory = SpringUtils.getBeanFactory();
        Path source = Path.of("src/main/webapp");
        Path web = Files.createDirectory(directory.resolve("web"));
        for (String file : List.of("taglibs.jsp", "WEB-INF/security.tld", "WEB-INF/caisi-tag.tld",
                "WEB-INF/caisirole-tag.tld", "WEB-INF/oscar-tag.tld", "WEB-INF/carlos-tag.tld",
                "WEB-INF/jsp/common/messages.jsp", "WEB-INF/jsp/common/carlosTableAssets.jspf",
                ADMIN + "ProgramManagerForm.jsp", ADMIN + "ProgramManagerView.jsp", ADMIN + "ProgramEdit/access.jsp",
                ADMIN + "ProgramEdit/client_status.jsp", ADMIN + "ProgramEdit/queue.jsp",
                ADMIN + "ProgramEdit/service_restrictions.jsp", ADMIN + "ProgramEdit/staff.jsp",
                ADMIN + "ProgramEdit/teams.jsp", ADMIN + "ProgramView/queue.jsp")) {
            copy(source.resolve(file), web.resolve(file));
        }
        for (Class<?> type : List.of(jakarta.servlet.jsp.jstl.core.Config.class,
                org.apache.taglibs.standard.tag.rt.fmt.SetBundleTag.class)) {
            Path jar = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
            copy(jar, web.resolve("WEB-INF/lib").resolve(jar.getFileName()));
        }

        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("programProviderDAO", mock(ProgramProviderDAO.class));
        beans.registerSingleton("secObjPrivilegeDao", mock(SecObjPrivilegeDao.class));
        ReflectionTestUtils.setField(SpringUtils.class, "beanFactory", beans);

        tomcat = new Tomcat();
        tomcat.setBaseDir(directory.resolve("tomcat").toString());
        tomcat.setPort(0);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        var context = tomcat.addContext("/carlos", web.toString());
        context.setParentClassLoader(ProgramFormControlsIntegrationTest.class.getClassLoader());
        var scanner = new StandardJarScanner();
        scanner.setScanClassPath(false);
        context.setJarScanner(scanner);
        context.addServletContainerInitializer(new JasperInitializer(), null);
        Tomcat.initWebappDefaults(context);
        Tomcat.addServlet(context, "program", new FixtureServlet());
        context.addServletMapping("/PMmodule/ProgramManager", "program");
        context.addServletMapping("/PMmodule/ProgramManagerView", "program");
        tomcat.start();
    }

    @AfterAll
    static void stopJasper() throws Exception {
        try {
            if (tomcat != null) {
                tomcat.stop();
                tomcat.destroy();
            }
        } finally {
            ReflectionTestUtils.setField(SpringUtils.class, "beanFactory", previousBeanFactory);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "Access, access.id|access.programId|access.accessTypeId|access.allRoles|checked_role",
            "Client Status, client_status.id|client_status.name",
            "Queue, queue.id",
            "Service Restrictions, restriction.id|program.maximumServiceRestrictionDays|program.defaultServiceRestrictionDays",
            "Staff, provider.id|provider.providerNo|provider.roleId",
            "Teams, team.id|team.name"
    })
    void shouldRenderSuccessfulFormControls_forEachProgramEditTab(String tab, String fields) throws Exception {
        Element form = render("ProgramManager?view.tab=" + URLEncoder.encode(tab, StandardCharsets.UTF_8), "programManagerForm");
        for (String name : fields.split("\\|")) {
            assertThat(form.select("input[name='" + name + "'], select[name='" + name + "']"))
                    .as("native form control %s", name).hasSize(1);
        }
        assertThat(form.selectFirst("input[name='id']").val()).isEqualTo("42");
    }

    @Test
    void shouldRetainSelectedValues_inTheRenderedProgramForms() throws Exception {
        Element access = render("ProgramManager?view.tab=Access", "programManagerForm");
        assertThat(access.selectFirst("select[name='access.accessTypeId']").val()).isEqualTo("18");
        assertThat(access.selectFirst("select[name='access.accessTypeId'] option").text()).isEqualTo(FIXTURE_NAME);
        assertThat(access.select("input[name='access.allRoles'][checked]")).hasSize(1);

        Element staff = render("ProgramManager?view.tab=Staff", "programManagerForm");
        assertThat(staff.selectFirst("select[name='provider.providerNo'] option[value='123']").text()).isEqualTo(FIXTURE_NAME);
        assertThat(staff.selectFirst("select[name='provider.roleId']").val()).isEqualTo("33");

        Element settings = render("ProgramManager?view.tab=Service%20Restrictions", "programManagerForm");
        assertThat(settings.selectFirst("input[name='program.maximumServiceRestrictionDays']").val()).isEqualTo("60");
        assertThat(settings.selectFirst("input[name='program.defaultServiceRestrictionDays']").val()).isEqualTo("30");
    }

    @Test
    void shouldRenderQueueIdentifiersAndRejectionReasons_afterSelectingAQueueEntry() throws Exception {
        Element form = render("ProgramManagerView?tab=Queue&clientId=12345&queueId=707", "programManagerViewForm");
        assertThat(form.selectFirst("input[type=hidden][name=clientId]").val()).isEqualTo("12345");
        assertThat(form.selectFirst("input[type=hidden][name=queueId]").val()).isEqualTo("707");
        assertThat(form.select("input[type=radio][name=radioRejectionReason]"))
                .extracting(Element::val).containsExactly("1", "2", "3", "4", "5");
    }

    private static Element render(String route, String formName) throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + BASE_PATH + route);
        try (HttpClient client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).header("Accept-Language", "fr-CA").build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertThat(response.statusCode()).as(route).isEqualTo(200);
            assertThat(response.body()).contains("</head>", "</body>").doesNotContain("<html:", "</html:");
            var document = Jsoup.parse(response.body());
            assertThat(document.selectFirst("html").attr("lang")).isEqualTo("fr");
            var form = document.selectFirst("form[name=" + formName + "]");
            assertThat(form).as(formName).isNotNull();
            assertThat(form.attr("method")).isEqualTo("post");
            assertThat(form.attr("action")).isEqualTo(BASE_PATH + route.substring(0, route.indexOf('?')));
            return form;
        }
    }

    private static void copy(Path source, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        Files.copy(source, destination);
    }

    private static final class FixtureServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            request.setAttribute("id", "42");
            request.setAttribute("programName", "FAKE Program");
            request.setAttribute("program", Map.of("name", "FAKE Program", "maximumServiceRestrictionDays", 60,
                    "defaultServiceRestrictionDays", 30));
            request.setAttribute("access", Map.of("id", 7, "accessTypeId", 18, "allRoles", true));
            request.setAttribute("accessTypes", List.of(Map.of("id", 18, "name", FIXTURE_NAME)));
            request.setAttribute("roles", List.of(Map.of("id", 33, "name", "FAKE Role")));
            request.setAttribute("availableProviders", List.of(Map.of("providerNo", "123", "formattedName", FIXTURE_NAME)));
            request.setAttribute("do_reject", true);
            String page = request.getServletPath().endsWith("View") ? "ProgramManagerView" : "ProgramManagerForm";
            request.getRequestDispatcher('/' + ADMIN + page + ".jsp").forward(request, response);
        }
    }
}
