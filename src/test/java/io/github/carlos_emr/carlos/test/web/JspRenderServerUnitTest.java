/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.test.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServlet;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pins how {@link JspRenderServer}, the shared fixture of the #6336 JSP render tests, builds its
 * temporary web application and guards {@link SpringUtils}' global bean factory, without starting
 * Tomcat. Render tests trust it to copy every page a JSP includes (a missed fragment fails Jasper
 * far from its cause) and to leave the factory as it found it (a leaked factory changes later
 * tests in the same Surefire fork). {@code JspRenderServerIntegrationTest} covers a running server.
 */
@Tag("unit")
@Tag("web")
class JspRenderServerUnitTest {

    @TempDir
    Path directory;

    @Test
    void shouldResolveEveryLiteralIncludeForm_againstTheIncludingFile() {
        String text = String.join("\n",
                "<%@ include file=\"part.jspf\" %>",
                "<%@include file='/WEB-INF/jspf/shared.jspf'%>",
                "<%@ include file=\"../../../../outside.jspf\" %>",
                "<jsp:include page=\"../other/child.jsp\" />",
                "<jsp:include flush=\"true\" page=\"/WEB-INF/jsp/area/fragment.jspf?mode=1\">",
                "<jsp:include page=\"<%= chosenPage %>\"/>",
                "<jsp:include page=\"${chosenPage}\"/>",
                "<c:import url=\"/WEB-INF/jsp/area/imported.jsp\"/>",
                "<c:import url=\"https://example.invalid/page.jsp\"/>");

        assertThat(JspRenderServer.literalIncludes("WEB-INF/jsp/area/page.jsp", text)).containsExactly(
                "WEB-INF/jsp/area/part.jspf",
                "WEB-INF/jspf/shared.jspf",
                "WEB-INF/jsp/other/child.jsp",
                "WEB-INF/jsp/area/fragment.jspf",
                "WEB-INF/jsp/area/imported.jsp");
    }

    @Test
    void shouldNormalizePaths_onlyInsideTheWebApplication() {
        assertThat(JspRenderServer.normalize("/WEB-INF/jsp/./area/../page.jsp")).isEqualTo("WEB-INF/jsp/page.jsp");
        assertThat(JspRenderServer.normalize("taglibs.jsp")).isEqualTo("taglibs.jsp");
        assertThat(JspRenderServer.normalize("../page.jsp")).isNull();
        assertThat(JspRenderServer.normalize("/WEB-INF/../../page.jsp")).isNull();
        assertThat(JspRenderServer.normalize("/")).isNull();
        assertThat(JspRenderServer.normalize("WEB-INF\\page.jsp")).isNull();
    }

    @Test
    void shouldCopyIncludesTransitively_andSkipIncludedRoutesWithoutAFile() throws Exception {
        Path source = directory.resolve("source");
        write(source, "WEB-INF/jsp/area/page.jsp",
                "<%@ include file=\"part.jspf\" %><jsp:include page=\"/area/StrutsRoute\"/>page");
        write(source, "WEB-INF/jsp/area/part.jspf", "<%@ include file=\"/WEB-INF/jspf/nested.jspf\" %>part");
        // Includes its includer again: the copy must not loop.
        write(source, "WEB-INF/jspf/nested.jspf", "<%@ include file=\"/WEB-INF/jsp/area/part.jspf\" %>nested");
        write(source, "WEB-INF/jsp/area/unrelated.jsp", "unrelated");
        Path webapp = Files.createDirectories(directory.resolve("webapp"));
        Set<String> present = new HashSet<>();

        List<String> written = JspRenderServer.assemble(source, webapp, Map.of(), present, "/WEB-INF/jsp/area/page.jsp");

        assertThat(written).containsExactly(
                "WEB-INF/jsp/area/page.jsp", "WEB-INF/jsp/area/part.jspf", "WEB-INF/jspf/nested.jspf");
        assertThat(Files.readString(webapp.resolve("WEB-INF/jspf/nested.jspf"), StandardCharsets.UTF_8))
                .isEqualTo("<%@ include file=\"/WEB-INF/jsp/area/part.jspf\" %>nested");
        assertThat(Files.exists(webapp.resolve("WEB-INF/jsp/area/unrelated.jsp"))).isFalse();
        assertThat(Files.exists(webapp.resolve("area/StrutsRoute"))).isFalse();
        assertThat(present).hasSize(3);

        // A second page sharing the fragments copies only what is new.
        write(source, "WEB-INF/jsp/area/second.jsp", "<%@ include file=\"part.jspf\" %>second");
        assertThat(JspRenderServer.assemble(source, webapp, Map.of(), present, "WEB-INF/jsp/area/second.jsp"))
                .containsExactly("WEB-INF/jsp/area/second.jsp");
    }

    @Test
    void shouldWriteSuppliedFiles_inPlaceOfTheProductionCopy() throws Exception {
        Path source = directory.resolve("source");
        write(source, "WEB-INF/jsp/area/page.jsp", "<%@ include file=\"part.jspf\" %>page");
        write(source, "WEB-INF/jsp/area/part.jspf", "production fragment");
        write(source, "WEB-INF/jsp/area/extra.jspf", "extra");
        Path webapp = Files.createDirectories(directory.resolve("webapp"));
        String standIn = "<%@ include file=\"extra.jspf\" %>stand-in Équipe";

        List<String> written = JspRenderServer.assemble(source, webapp,
                Map.of("WEB-INF/jsp/area/part.jspf", standIn), new HashSet<>(), "WEB-INF/jsp/area/page.jsp");

        assertThat(written).containsExactly(
                "WEB-INF/jsp/area/page.jsp", "WEB-INF/jsp/area/part.jspf", "WEB-INF/jsp/area/extra.jspf");
        assertThat(Files.readString(webapp.resolve("WEB-INF/jsp/area/part.jspf"), StandardCharsets.UTF_8))
                .isEqualTo(standIn);
    }

    @Test
    void shouldRefuseAPage_whenNeitherSourceHasIt() throws Exception {
        Path source = Files.createDirectories(directory.resolve("source"));
        Path webapp = Files.createDirectories(directory.resolve("webapp"));

        assertThatThrownBy(() -> JspRenderServer.assemble(source, webapp, Map.of(), new HashSet<>(),
                "/WEB-INF/jsp/area/missing.jsp"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WEB-INF/jsp/area/missing.jsp");
        assertThatThrownBy(() -> JspRenderServer.assemble(source, webapp, Map.of(), new HashSet<>(), "../page.jsp"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRestoreTheBeanFactory_whenStartFailsAfterInstallingIt() throws Exception {
        BeanFactory original = SpringUtils.getBeanFactory();
        DefaultListableBeanFactory sentinel = new DefaultListableBeanFactory();
        ReflectionTestUtils.setField(SpringUtils.class, "beanFactory", sentinel);
        try {
            Path source = directory.resolve("source");
            write(source, "WEB-INF/jsp/area/page.jsp", "page");
            // Tomcat refuses this servlet mapping after the fixture has installed its beans.
            JspRenderServer.Builder builder = JspRenderServer.builder(directory)
                    .source(source)
                    .jsp("/WEB-INF/jsp/area/page.jsp")
                    .bean("jspRenderFixtureGreeting", "FAKE greeting")
                    .servlet("no-leading-slash", new HttpServlet() { });

            assertThatThrownBy(builder::start).isInstanceOf(IllegalArgumentException.class);
            assertThat(SpringUtils.getBeanFactory()).isSameAs(sentinel);
        } finally {
            ReflectionTestUtils.setField(SpringUtils.class, "beanFactory", original);
        }
    }

    @Test
    void shouldLeaveTheBeanFactoryAlone_whenANamedPageIsMissing() throws Exception {
        BeanFactory original = SpringUtils.getBeanFactory();
        DefaultListableBeanFactory sentinel = new DefaultListableBeanFactory();
        ReflectionTestUtils.setField(SpringUtils.class, "beanFactory", sentinel);
        try {
            JspRenderServer.Builder builder = JspRenderServer.builder(directory)
                    .source(Files.createDirectories(directory.resolve("source")))
                    .jsp("/WEB-INF/jsp/area/missing.jsp")
                    .bean("jspRenderFixtureGreeting", "FAKE greeting");

            assertThatThrownBy(builder::start)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("WEB-INF/jsp/area/missing.jsp");
            assertThat(SpringUtils.getBeanFactory()).isSameAs(sentinel);
        } finally {
            ReflectionTestUtils.setField(SpringUtils.class, "beanFactory", original);
        }
    }

    private static void write(Path root, String path, String text) throws Exception {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
