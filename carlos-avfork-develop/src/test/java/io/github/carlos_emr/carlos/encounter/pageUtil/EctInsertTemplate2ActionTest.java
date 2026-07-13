package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.EncounterTemplateDao;
import io.github.carlos_emr.carlos.commn.model.EncounterTemplate;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EctInsertTemplate2Action}, which handles clinical note template
 * insertion with security privilege checks and XSS-safe output.
 *
 * <p>Verifies: security gating, null/blank templateName handling, null template value
 * from database, version-based routing, and that raw values are set for JSP-layer encoding.</p>
 *
 * @since 2026-04-01
 */
@DisplayName("EctInsertTemplate2Action - Clinical Note Template Security")
@Tag("unit")
@Tag("fast")
@Tag("encounter")
@Tag("security")
class EctInsertTemplate2ActionTest extends CarlosUnitTestBase {

    private HttpServletRequest mockRequest;
    private HttpServletResponse mockResponse;
    private SecurityInfoManager mockSecurityInfoManager;
    private EncounterTemplateDao mockDao;
    private LoggedInInfo mockLoggedInInfo;

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    private EctInsertTemplate2Action action;

    @BeforeEach
    void setUp() {
        mockRequest = Mockito.mock(HttpServletRequest.class);
        mockResponse = Mockito.mock(HttpServletResponse.class);
        mockSecurityInfoManager = Mockito.mock(SecurityInfoManager.class);
        mockDao = Mockito.mock(EncounterTemplateDao.class);
        mockLoggedInInfo = Mockito.mock(LoggedInInfo.class);

        // Register Spring beans BEFORE constructing the Action (field initializers call SpringUtils.getBean)
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(EncounterTemplateDao.class, mockDao);

        // Mock ServletActionContext BEFORE constructing the Action (field initializers call getRequest/getResponse)
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        // Mock LoggedInInfo.getLoggedInInfoFromSession() used in execute()
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(mockRequest))
                .thenReturn(mockLoggedInInfo);

        // Construct the Action — field initializers use the mocked statics
        action = new EctInsertTemplate2Action();

        // Default: user has privilege
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo),
                eq("_newCasemgmt.templates"), eq("r"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    /** Security privilege check tests. */
    @Nested
    @DisplayName("Security privilege check")
    class SecurityTests {

        @Test
        @DisplayName("should throw SecurityException when user lacks template read privilege")
        void shouldThrowSecurityException_whenUserLacksPrivilege() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_newCasemgmt.templates"),
                    eq("r"), isNull()))
                    .thenReturn(false);

            assertThatThrownBy(() -> action.execute())
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("_newCasemgmt.templates");

            verifyNoInteractions(mockDao);
        }

        @Test
        @DisplayName("should not throw when user has template read privilege")
        void shouldNotThrow_whenUserHasPrivilege() {
            when(mockRequest.getParameter("templateName")).thenReturn(null);

            assertThatCode(() -> action.execute()).doesNotThrowAnyException();
        }
    }

    /** Null/blank templateName validation tests. */
    @Nested
    @DisplayName("Template name validation")
    class TemplateNameValidation {

        @Test
        @DisplayName("should return SUCCESS without querying DAO when templateName is null")
        void shouldReturnSuccess_whenTemplateNameIsNull() throws Exception {
            when(mockRequest.getParameter("templateName")).thenReturn(null);

            String result = action.execute();

            assertThat(result).isEqualTo("success");
            verify(mockRequest, never()).setAttribute(eq("templateValue"), any());
            verifyNoInteractions(mockDao);
        }

        @Test
        @DisplayName("should return SUCCESS without querying DAO when templateName is empty")
        void shouldReturnSuccess_whenTemplateNameIsEmpty() throws Exception {
            when(mockRequest.getParameter("templateName")).thenReturn("");

            String result = action.execute();

            assertThat(result).isEqualTo("success");
            verify(mockRequest, never()).setAttribute(eq("templateValue"), any());
            verifyNoInteractions(mockDao);
        }

        @Test
        @DisplayName("should return SUCCESS without querying DAO when templateName is blank")
        void shouldReturnSuccess_whenTemplateNameIsBlank() throws Exception {
            when(mockRequest.getParameter("templateName")).thenReturn("   ");

            String result = action.execute();

            assertThat(result).isEqualTo("success");
            verify(mockRequest, never()).setAttribute(eq("templateValue"), any());
            verifyNoInteractions(mockDao);
        }
    }

    /** Template lookup and value handling tests. */
    @Nested
    @DisplayName("Template lookup and value handling")
    class TemplateLookup {

        @Test
        @DisplayName("should set raw templateValue when template is found")
        void shouldSetTemplateValue_whenTemplateExists() throws Exception {
            when(mockRequest.getParameter("templateName")).thenReturn("SOAP");
            EncounterTemplate template = new EncounterTemplate();
            template.setEncounterTemplateValue("S:\nO:\nA:\nP:");
            when(mockDao.find("SOAP")).thenReturn(template);

            action.execute();

            verify(mockRequest).setAttribute("templateValue", "S:\nO:\nA:\nP:");
        }

        @Test
        @DisplayName("should not set templateValue when template is not found")
        void shouldNotSetTemplateValue_whenTemplateNotFound() throws Exception {
            when(mockRequest.getParameter("templateName")).thenReturn("nonexistent");
            when(mockDao.find("nonexistent")).thenReturn(null);

            action.execute();

            verify(mockRequest, never()).setAttribute(eq("templateValue"), any());
        }

        @Test
        @DisplayName("should set empty string when template value is null in database")
        void shouldSetEmptyString_whenTemplateValueIsNull() throws Exception {
            when(mockRequest.getParameter("templateName")).thenReturn("emptyTemplate");
            EncounterTemplate template = new EncounterTemplate();
            template.setEncounterTemplateValue(null);
            when(mockDao.find("emptyTemplate")).thenReturn(template);

            action.execute();

            verify(mockRequest).setAttribute("templateValue", "");
        }

        @Test
        @DisplayName("should preserve special characters in template value for JSP-layer encoding")
        void shouldPreserveSpecialChars_whenTemplateContainsHtml() throws Exception {
            when(mockRequest.getParameter("templateName")).thenReturn("htmlTemplate");
            String xssPayload = "<script>alert('xss')</script> & \"quotes\"";
            EncounterTemplate template = new EncounterTemplate();
            template.setEncounterTemplateValue(xssPayload);
            when(mockDao.find("htmlTemplate")).thenReturn(template);

            action.execute();

            // Action sets raw value — encoding is the JSP's responsibility
            verify(mockRequest).setAttribute("templateValue", xssPayload);
        }
    }

    /** Version parameter routing tests. */
    @Nested
    @DisplayName("Version routing")
    class VersionRouting {

        @Test
        @DisplayName("should return success2 when version is 2 (AJAX path)")
        void shouldReturnSuccess2_whenVersionIs2() throws Exception {
            when(mockRequest.getParameter("templateName")).thenReturn("SOAP");
            when(mockRequest.getParameter("version")).thenReturn("2");
            EncounterTemplate template = new EncounterTemplate();
            template.setEncounterTemplateValue("content");
            when(mockDao.find("SOAP")).thenReturn(template);

            String result = action.execute();

            assertThat(result).isEqualTo("success2");
        }

        @Test
        @DisplayName("should return SUCCESS when version is not 2 (popup path)")
        void shouldReturnSuccess_whenVersionIsNot2() throws Exception {
            when(mockRequest.getParameter("templateName")).thenReturn("SOAP");
            when(mockRequest.getParameter("version")).thenReturn("1");
            EncounterTemplate template = new EncounterTemplate();
            template.setEncounterTemplateValue("content");
            when(mockDao.find("SOAP")).thenReturn(template);

            String result = action.execute();

            assertThat(result).isEqualTo("success");
        }

        @Test
        @DisplayName("should return SUCCESS when version is null (popup path)")
        void shouldReturnSuccess_whenVersionIsNull() throws Exception {
            when(mockRequest.getParameter("templateName")).thenReturn("SOAP");
            when(mockRequest.getParameter("version")).thenReturn(null);
            EncounterTemplate template = new EncounterTemplate();
            template.setEncounterTemplateValue("content");
            when(mockDao.find("SOAP")).thenReturn(template);

            String result = action.execute();

            assertThat(result).isEqualTo("success");
        }
    }
}
