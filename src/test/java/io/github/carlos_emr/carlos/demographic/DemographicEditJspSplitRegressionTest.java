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
package io.github.carlos_emr.carlos.demographic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source-level regressions for the master demographic edit JSP split.
 *
 * <p>The legacy {@code demographiceditdemographic.jsp} previously generated a
 * {@code _jspService} method larger than the JVM's 65,535-byte method-code
 * limit during JSPC. These tests pin the compatibility wrapper and dynamic
 * fragment includes so future edits do not silently reintroduce an oversized
 * JSP target.</p>
 *
 * @since 2026-05-21
 */
@DisplayName("Demographic edit JSP split regression tests")
@Tag("unit")
@Tag("demographic")
class DemographicEditJspSplitRegressionTest {

    private static final Path WEBAPP_ROOT = projectRoot().resolve("src/main/webapp");
    private static final Path LEGACY_JSP = WEBAPP_ROOT.resolve(
            "WEB-INF/jsp/demographic/demographiceditdemographic.jsp");
    private static final Path MASTER_JSP = WEBAPP_ROOT.resolve("WEB-INF/jsp/demographic/edit.jsp");
    private static final Pattern JSP_FORWARD = Pattern.compile(
            "(?is)<jsp:forward\\b([^>]*)>(.*?)</jsp:forward>|<jsp:forward\\b([^>]*)/\\s*>");
    private static final Pattern JSP_INCLUDE = Pattern.compile("(?is)<jsp:include\\b([^>]*)/\\s*>");
    private static final Pattern JSP_PAGE_ATTRIBUTE = Pattern.compile("\\bpage\\s*=\\s*(['\"])(.*?)\\1");
    private static final Pattern JSP_PARAM = Pattern.compile("(?is)<jsp:param\\b[^>]*/\\s*>");
    private static final Set<String> REQUIRED_EDIT_FRAGMENTS = Set.of(
            "edit-view.jsp", "edit-form-personal.jsp", "edit-form-clinical.jsp");

    @Test
    @DisplayName("should keep legacy JSP target as tiny forward wrapper")
    void shouldRemainSmall_asForwardWrapper() throws IOException {
        String legacyJsp = Files.readString(LEGACY_JSP, StandardCharsets.UTF_8);
        String executableJsp = removeJspCommentsAndDirectives(legacyJsp);
        Matcher forwardMatcher = JSP_FORWARD.matcher(executableJsp);

        assertThat(forwardMatcher.find())
                .as("legacy JSP must contain a standard JSP forward")
                .isTrue();
        assertThat(pageAttribute(forwardMatcher))
                .as("legacy JSP must forward to the demographic edit action")
                .isEqualTo("/demographic/DemographicEdit");
        assertThat(containsOnlyJspParams(forwardBody(forwardMatcher)))
                .as("forward body may only contain explicit JSP params")
                .isTrue();
        assertThat(forwardMatcher.find())
                .as("legacy JSP must contain exactly one forward")
                .isFalse();

        assertThat(JSP_FORWARD.matcher(executableJsp).replaceFirst("").trim())
                .as("legacy JSP must not grow scriptlet, template, or tag bodies outside the forward")
                .isEmpty();
    }

    @Test
    @DisplayName("should dispatch legacy wrapper to edit action with request parameters")
    void shouldDispatchLegacyWrapper_toEditActionWithRequestParameters() throws Exception {
        String legacyJsp = Files.readString(LEGACY_JSP, StandardCharsets.UTF_8);
        RecordingServletContext servletContext = new RecordingServletContext();
        // The legacy ajax result is returned from demographic/Contact and then forwards through this JSP wrapper.
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext, "POST", "/demographic/Contact");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setParameter("postMethod", "ajax");
        request.setParameter("demographic_no", "12345");

        servletContext.getRequestDispatcher(forwardTarget(legacyJsp)).forward(request, response);

        assertThat(servletContext.forwardedPath()).isEqualTo("/demographic/DemographicEdit");
        assertThat(((MockHttpServletRequest) servletContext.forwardedRequest()).getParameter("postMethod"))
                .isEqualTo("ajax");
        assertThat(((MockHttpServletRequest) servletContext.forwardedRequest()).getParameter("demographic_no"))
                .isEqualTo("12345");
    }

    @Test
    @DisplayName("should render demographic edit through dynamic JSP fragments")
    void shouldUseDynamicFragments_forMasterEditPage() throws IOException {
        String masterJsp = Files.readString(MASTER_JSP, StandardCharsets.UTF_8);

        Set<String> editFragmentIncludes = jspIncludePages(masterJsp);
        editFragmentIncludes.removeIf(page -> !page.startsWith("edit-") || !page.endsWith(".jsp"));

        // These fragments are the split contract that keeps the generated _jspService methods below the JVM limit.
        assertThat(editFragmentIncludes)
                .containsExactlyInAnyOrderElementsOf(REQUIRED_EDIT_FRAGMENTS);
    }

    private static String removeJspCommentsAndDirectives(String jsp) {
        return jsp
                .replaceAll("(?s)<%--.*?--%>", "")
                .replaceAll("(?s)<%@.*?%>", "")
                .trim();
    }

    private static String forwardTarget(String jsp) {
        String executableJsp = removeJspCommentsAndDirectives(jsp);
        Matcher forwardMatcher = JSP_FORWARD.matcher(executableJsp);
        assertThat(forwardMatcher.find())
                .as("legacy JSP must contain a standard JSP forward")
                .isTrue();
        return pageAttribute(forwardMatcher);
    }

    private static String forwardBody(Matcher forwardMatcher) {
        return forwardMatcher.group(2) == null ? "" : forwardMatcher.group(2);
    }

    private static boolean containsOnlyJspParams(String forwardBody) {
        return JSP_PARAM.matcher(forwardBody).replaceAll("").trim().isEmpty();
    }

    private static String pageAttribute(Matcher jspTagMatcher) {
        String attributes = jspTagMatcher.group(1) != null ? jspTagMatcher.group(1) : jspTagMatcher.group(3);
        Matcher pageMatcher = JSP_PAGE_ATTRIBUTE.matcher(attributes);
        assertThat(pageMatcher.find())
                .as("JSP tag must declare a page attribute")
                .isTrue();
        return pageMatcher.group(2);
    }

    private static Set<String> jspIncludePages(String jsp) {
        Set<String> pages = new HashSet<>();
        Matcher includeMatcher = JSP_INCLUDE.matcher(jsp);
        while (includeMatcher.find()) {
            Matcher pageMatcher = JSP_PAGE_ATTRIBUTE.matcher(includeMatcher.group(1));
            if (pageMatcher.find()) {
                pages.add(pageMatcher.group(2));
            }
        }
        return pages;
    }

    private static Path projectRoot() {
        try {
            Path location = Path.of(DemographicEditJspSplitRegressionTest.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            Path current = Files.isRegularFile(location) ? location.getParent() : location;
            while (current != null) {
                if (Files.isDirectory(current.resolve("src/main/webapp"))) {
                    return current;
                }
                current = current.getParent();
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Unable to resolve test class location", e);
        }
        throw new IllegalStateException("Unable to locate CARLOS EMR project root from test classpath");
    }

    private static final class RecordingServletContext extends MockServletContext {
        private String forwardedPath;
        private ServletRequest forwardedRequest;

        private String forwardedPath() {
            return forwardedPath;
        }

        private ServletRequest forwardedRequest() {
            return forwardedRequest;
        }

        @Override
        public RequestDispatcher getRequestDispatcher(String path) {
            return new RequestDispatcher() {
                @Override
                public void forward(ServletRequest request, ServletResponse response)
                        throws ServletException, IOException {
                    forwardedPath = path;
                    forwardedRequest = request;
                }

                @Override
                public void include(ServletRequest request, ServletResponse response) {
                    throw new UnsupportedOperationException("include is not used by this regression test");
                }
            };
        }
    }
}
