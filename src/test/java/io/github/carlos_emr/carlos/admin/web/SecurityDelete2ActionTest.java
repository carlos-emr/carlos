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
package io.github.carlos_emr.carlos.admin.web;

import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.config.MethodSecurityConfig;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.security.CarlosMethodSecurity;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.spring.StrutsSpringObjectFactory;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SecurityDelete2Action} covering privilege checks,
 * POST enforcement, successful delete, not-found, invalid ID, and DAO exception handling.
 *
 * @since 2026-04-11
 */
@DisplayName("SecurityDelete2Action")
@Tag("unit")
@Tag("admin")
@Tag("security")
class SecurityDelete2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private SecurityDao mockSecurityDao;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(SecurityDao.class, mockSecurityDao);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
            .thenReturn(mockLoggedInInfo);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("doc1");
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    private SecurityDelete2Action createActionWithPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), eq("w"), isNull()))
            .thenReturn(true);
        mockRequest.setMethod("POST");
        return createAction();
    }

    private SecurityDelete2Action createAction() {
        return new SecurityDelete2Action(mockSecurityDao, new CarlosMethodSecurity(mockSecurityInfoManager));
    }

    @Nested
    @DisplayName("Privilege checks")
    class PrivilegeChecks {

        @Test
        @DisplayName("should throw SecurityException when both privileges are denied")
        void shouldThrowSecurityException_whenBothPrivilegesDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), eq("w"), isNull()))
                .thenReturn(false);
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin.userAdmin"), eq("w"), isNull()))
                .thenReturn(false);

            SecurityDelete2Action action = createAction();
            assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("should pass when _admin w privilege is granted")
        void shouldPass_whenAdminPrivilegeGranted() throws Exception {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), eq("w"), isNull()))
                .thenReturn(true);
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin.userAdmin"), eq("w"), isNull()))
                .thenReturn(false);
            mockRequest.setMethod("POST");

            SecurityDelete2Action action = createAction();
            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        }

        @Test
        @DisplayName("should pass when _admin.userAdmin w privilege is granted")
        void shouldPass_whenUserAdminPrivilegeGranted() throws Exception {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), eq("w"), isNull()))
                .thenReturn(false);
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin.userAdmin"), eq("w"), isNull()))
                .thenReturn(true);
            mockRequest.setMethod("POST");

            SecurityDelete2Action action = createAction();
            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        }
    }

    @Nested
    @DisplayName("Declarative method security proof of concept")
    class DeclarativeMethodSecurityPoc {

        @Test
        @DisplayName("should block execute before action body when PreAuthorize denies")
        void shouldBlockExecute_whenPreAuthorizeDenies() {
            CarlosMethodSecurity methodSecurity = mock(CarlosMethodSecurity.class);
            when(methodSecurity.hasAdminWrite()).thenReturn(false);

            try (AnnotationConfigApplicationContext context = methodSecurityContext(methodSecurity)) {
                SecurityDelete2Action action = context.getBean(
                    SecurityDelete2Action.SPRING_BEAN_NAME, SecurityDelete2Action.class);

                assertThatThrownBy(action::execute).isInstanceOf(AccessDeniedException.class);
                verify(mockSecurityInfoManager, never()).hasPrivilege(any(), anyString(), anyString(), any());
                verifyNoInteractions(mockSecurityDao);
            }
        }

        @Test
        @DisplayName("should allow execute when PreAuthorize grants admin privilege")
        void shouldAllowExecute_whenPreAuthorizeGrantsAdminPrivilege() throws Exception {
            CarlosMethodSecurity methodSecurity = mock(CarlosMethodSecurity.class);
            when(methodSecurity.hasAdminWrite()).thenReturn(true);
            mockRequest.setMethod("POST");

            try (AnnotationConfigApplicationContext context = methodSecurityContext(methodSecurity)) {
                SecurityDelete2Action action = context.getBean(
                    SecurityDelete2Action.SPRING_BEAN_NAME, SecurityDelete2Action.class);

                String result = action.execute();

                assertThat(result).isEqualTo(ActionSupport.SUCCESS);
                assertThat((String) mockRequest.getAttribute("msg"))
                    .isEqualTo("No security identifier was provided.");
            }
        }
    }

    @Nested
    @DisplayName("Struts/Spring wiring guardrail")
    class StrutsSpringWiringGuardrail {

        private static final Path STRUTS_CONFIG = Path.of(
            "src", "main", "webapp", "WEB-INF", "classes", "struts.xml");
        private static final int MAX_PARENT_SEARCH_DEPTH = 8;

        @Test
        @DisplayName("should configure Struts to use Spring object factory")
        void shouldConfigureStruts_toUseSpringObjectFactory() throws Exception {
            assertThat(hasStrutsConstant("struts.objectFactory", "spring"))
                .as("Method security depends on Struts obtaining actions from Spring")
                .isTrue();
        }

        @Test
        @DisplayName("should build Struts action class through Spring method-security proxy")
        void shouldBuildStrutsActionClass_throughSpringMethodSecurityProxy() throws Exception {
            mockRequest.setMethod("POST");
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), eq("w"), isNull()))
                .thenReturn(false);
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin.userAdmin"), eq("w"), isNull()))
                .thenReturn(false);

            // StrutsAdminConfigTest pins that struts-admin.xml class="SPRING_BEAN_NAME"; here
            // we prove Struts' Spring object factory resolves that class value to the AOP proxy.
            try (AnnotationConfigApplicationContext context = methodSecurityComponentScanContext()) {
                // init() configures the Struts container and type-mapper; neither is
                // needed for a buildBean(beanName, ...) lookup against a test ApplicationContext.
                StrutsSpringObjectFactory objectFactory = new StrutsSpringObjectFactory();
                objectFactory.setApplicationContext(context);
                Object action = objectFactory.buildBean(
                    SecurityDelete2Action.SPRING_BEAN_NAME, Map.of(), false);

                assertThat(AopUtils.isAopProxy(action)).isTrue();
                assertThat(action).isInstanceOf(SecurityDelete2Action.class);
                assertThatThrownBy(((SecurityDelete2Action) action)::execute)
                    .isInstanceOf(AccessDeniedException.class);
                verify(mockSecurityInfoManager, times(1))
                    .hasPrivilege(mockLoggedInInfo, "_admin", "w", null);
                verify(mockSecurityInfoManager, times(1))
                    .hasPrivilege(mockLoggedInInfo, "_admin.userAdmin", "w", null);
                verifyNoInteractions(mockSecurityDao);
            }
        }

        private boolean hasStrutsConstant(String name, String value) throws Exception {
            NodeList constants = parseStrutsConfig().getElementsByTagName("constant");
            for (int i = 0; i < constants.getLength(); i++) {
                Element constant = (Element) constants.item(i);
                if (name.equals(constant.getAttribute("name"))
                        && value.equals(constant.getAttribute("value"))) {
                    return true;
                }
            }
            return false;
        }

        private Document parseStrutsConfig() throws Exception {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setValidating(false);
            dbf.setNamespaceAware(false);
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            // Struts config files declare a DOCTYPE; external DTD loading and all
            // external entities stay disabled above, and the resolver below returns
            // an empty local source so the parser never performs network access.
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            db.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));

            try (InputStream in = Files.newInputStream(resolveProjectPath(STRUTS_CONFIG))) {
                return db.parse(in);
            }
        }

        private static Path resolveProjectPath(Path relativePath) {
            Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
            int checked = 0;
            while (current != null && checked < MAX_PARENT_SEARCH_DEPTH) {
                Path candidate = current.resolve(relativePath);
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
                current = current.getParent();
                checked++;
            }
            throw new IllegalStateException(
                "Unable to locate " + relativePath + " in " + MAX_PARENT_SEARCH_DEPTH
                + " ancestor directories of " + System.getProperty("user.dir"));
        }
    }

    @Nested
    @DisplayName("Targeted method-security context")
    class TargetedMethodSecurityContext {

        @Test
        @DisplayName("should expose method-security beans and proxied prototype action")
        void shouldExposeMethodSecurityBeans_andProxiedPrototypeAction() {
            try (AnnotationConfigApplicationContext context = methodSecurityComponentScanContext()) {
                assertThat(context.getBeansOfType(MethodSecurityConfig.class)).isNotEmpty();
                assertThat(context.containsBean("carlosMethodSecurity")).isTrue();
                assertThat(context.containsBean(SecurityDelete2Action.SPRING_BEAN_NAME)).isTrue();

                Object firstAction = context.getBean(SecurityDelete2Action.SPRING_BEAN_NAME);
                Object secondAction = context.getBean(SecurityDelete2Action.SPRING_BEAN_NAME);

                assertThat(AopUtils.isAopProxy(firstAction)).isTrue();
                assertThat(firstAction).isInstanceOf(SecurityDelete2Action.class);
                assertThat(secondAction).isInstanceOf(SecurityDelete2Action.class);
                assertThat(firstAction).isNotSameAs(secondAction);

                assertThatThrownBy(((SecurityDelete2Action) firstAction)::execute)
                    .as("method-security proxy must intercept execute() and deny unauthenticated calls")
                    .isInstanceOf(AccessDeniedException.class);
            }
        }
    }

    private AnnotationConfigApplicationContext methodSecurityComponentScanContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().registerSingleton("securityInfoManager", mockSecurityInfoManager);
        context.getBeanFactory().registerSingleton("securityDao", mockSecurityDao);
        context.register(MethodSecurityConfig.class, CarlosMethodSecurity.class, SecurityDelete2Action.class);
        context.refresh();
        return context;
    }

    private AnnotationConfigApplicationContext methodSecurityContext(CarlosMethodSecurity methodSecurity) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().registerSingleton("carlosMethodSecurity", methodSecurity);
        context.getBeanFactory().registerSingleton("securityDao", mockSecurityDao);
        context.registerBean(
            SecurityDelete2Action.SPRING_BEAN_NAME,
            SecurityDelete2Action.class,
            definition -> definition.setScope(ConfigurableBeanFactory.SCOPE_PROTOTYPE));
        context.register(MethodSecurityProxyTestConfig.class);
        context.refresh();
        return context;
    }

    @Configuration
    @EnableMethodSecurity(prePostEnabled = true, proxyTargetClass = true)
    static class MethodSecurityProxyTestConfig {
    }

    @Nested
    @DisplayName("POST enforcement")
    class PostEnforcement {

        @Test
        @DisplayName("should return NONE with 405 error on GET request")
        void shouldReturnNone_whenGetRequest() throws Exception {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), eq("w"), isNull()))
                .thenReturn(true);
            mockRequest.setMethod("GET");

            SecurityDelete2Action action = createAction();
            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(405);
        }
    }

    @Nested
    @DisplayName("Delete operations")
    class DeleteOperations {

        @Test
        @DisplayName("should delete entity and set success message when valid ID provided")
        void shouldDeleteEntity_whenValidIdProvided() throws Exception {
            SecurityDelete2Action action = createActionWithPrivilege();
            mockRequest.setParameter("keyword", "42");

            Security entity = mock(Security.class);
            when(entity.getUserName()).thenReturn("testuser");
            when(mockSecurityDao.find(42)).thenReturn(entity);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            verify(mockSecurityDao).remove(entity);
            assertThat((String) mockRequest.getAttribute("msg"))
                .contains("Security entry deleted for user: testuser");
        }

        @Test
        @DisplayName("should set not-found message when entity does not exist")
        void shouldSetNotFoundMessage_whenEntityDoesNotExist() throws Exception {
            SecurityDelete2Action action = createActionWithPrivilege();
            mockRequest.setParameter("keyword", "999");
            when(mockSecurityDao.find(999)).thenReturn(null);

            action.execute();

            assertThat((String) mockRequest.getAttribute("msg"))
                .isEqualTo("Security entry not found.");
            verify(mockSecurityDao, never()).remove(any());
        }

        @Test
        @DisplayName("should set error message when keyword is invalid integer")
        void shouldSetErrorMessage_whenKeywordIsInvalidInteger() throws Exception {
            SecurityDelete2Action action = createActionWithPrivilege();
            mockRequest.setParameter("keyword", "abc");

            action.execute();

            assertThat((String) mockRequest.getAttribute("msg"))
                .isEqualTo("Invalid security identifier.");
            verify(mockSecurityDao, never()).remove(any());
        }

        @Test
        @DisplayName("should set message when no keyword is provided")
        void shouldSetMessage_whenNoKeywordProvided() throws Exception {
            SecurityDelete2Action action = createActionWithPrivilege();

            action.execute();

            assertThat((String) mockRequest.getAttribute("msg"))
                .isEqualTo("No security identifier was provided.");
        }

        @Test
        @DisplayName("should set error message when DAO remove throws RuntimeException")
        void shouldSetErrorMessage_whenDaoRemoveThrows() throws Exception {
            SecurityDelete2Action action = createActionWithPrivilege();
            mockRequest.setParameter("keyword", "42");

            Security entity = mock(Security.class);
            when(entity.getUserName()).thenReturn("testuser");
            when(mockSecurityDao.find(42)).thenReturn(entity);
            doThrow(new RuntimeException("DB error")).when(mockSecurityDao).remove(entity);

            action.execute();

            assertThat((String) mockRequest.getAttribute("msg"))
                .isEqualTo("Failed to delete security entry.");
        }

        @Test
        @DisplayName("should not include HTML encoding in msg attribute (JSP handles encoding)")
        void shouldNotIncludeHtmlEncoding_inMsgAttribute() throws Exception {
            SecurityDelete2Action action = createActionWithPrivilege();
            mockRequest.setParameter("keyword", "42");

            Security entity = mock(Security.class);
            when(entity.getUserName()).thenReturn("O'Brien & <Co>");
            when(mockSecurityDao.find(42)).thenReturn(entity);

            action.execute();

            String msg = (String) mockRequest.getAttribute("msg");
            assertThat(msg)
                .as("msg should contain raw username — the JSP is responsible for OWASP encoding")
                .contains("O'Brien & <Co>")
                .doesNotContain("&amp;")
                .doesNotContain("&lt;");
        }
    }
}
