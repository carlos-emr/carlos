package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.documentManager.ConvertToEdoc;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.form.util.FormTransportContainer;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("FormsManagerImpl")
@Tag("unit")
@Tag("fast")
class FormsManagerImplUnitTest extends CarlosUnitTestBase {

    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private EncounterFormDao encounterFormDao;
    @Mock private LoggedInInfo loggedInInfo;
    @Mock private FormTransportContainer formTransportContainer;

    private AutoCloseable mocks;
    private FormsManagerImpl manager;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        registerMock(NioFileManager.class, org.mockito.Mockito.mock(NioFileManager.class));
        registerMock(EncounterFormDao.class, encounterFormDao);
        EncounterForm encounterForm = new EncounterForm();
        encounterForm.setFormName("Annual");
        encounterForm.setFormValue("form/formannual.jsp");
        encounterForm.setFormTable("");
        when(encounterFormDao.findByFormName("Annual")).thenReturn(Collections.singletonList(encounterForm));
        manager = new FormsManagerImpl();
        injectDependency(manager, "securityInfoManager", securityInfoManager);
        injectDependency(manager, "encounterFormDao", encounterFormDao);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("should use demographic-scoped form read privilege when listing encounter forms")
    void shouldUseDemographicScopedPrivilege_whenListingEncounterForms() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, "123")).thenReturn(true);
        when(encounterFormDao.findAll()).thenReturn(Collections.emptyList());

        assertThat(manager.getEncounterFormsbyDemographicNumber(loggedInInfo, 123, false, true)).isEmpty();

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, "123");
        verify(securityInfoManager, never()).hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, null);
    }

    @Test
    @DisplayName("should use demographic-scoped form read privilege when rendering transport container")
    void shouldUseDemographicScopedPrivilege_whenRenderingTransportContainer() {
        when(formTransportContainer.getDemographicNo()).thenReturn("123");

        assertThatThrownBy(() -> manager.renderForm(loggedInInfo, formTransportContainer))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_form)");

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, "123");
        verify(securityInfoManager, never()).hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, null);
    }

    @Test
    @DisplayName("should use patient form demographic when rendering patient form")
    void shouldUsePatientFormDemographic_whenRenderingPatientForm() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        LoggedInInfo.setLoggedInInfoIntoRequest(request, loggedInInfo);
        EctFormData.PatientForm form = new EctFormData.PatientForm("formAnnual", "Annual", 45, 123);

        assertThatThrownBy(() -> manager.renderForm(request, response, form))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_form)");

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, "123");
        verify(securityInfoManager, never()).hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, null);
    }

    @Test
    @DisplayName("should use request demographic before patient form demographic when rendering patient form")
    void shouldUseRequestDemographic_beforePatientFormDemographic() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        LoggedInInfo.setLoggedInInfoIntoRequest(request, loggedInInfo);
        request.setParameter("demographicNo", "456");
        EctFormData.PatientForm form = new EctFormData.PatientForm("formAnnual", "Annual", 45, 123);

        assertThatThrownBy(() -> manager.renderForm(request, response, form))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_form)");

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, "456");
        verify(securityInfoManager, never()).hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, "123");
        verify(securityInfoManager, never()).hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, null);
    }

    @Test
    @DisplayName("should allow demographic-scoped form read privilege when rendering patient form")
    void shouldAllowDemographicScopedPrivilege_whenRenderingPatientForm() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        LoggedInInfo.setLoggedInInfoIntoRequest(request, loggedInInfo);
        EctFormData.PatientForm form = new EctFormData.PatientForm("formAnnual", "Annual", 45, 123);
        Path renderedPath = Path.of("target/form-preview.pdf");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, "123")).thenReturn(true);

        try (MockedStatic<ConvertToEdoc> convertToEdocMock = mockStatic(ConvertToEdoc.class)) {
            convertToEdocMock.when(() -> ConvertToEdoc.saveAsTempPDF(any(FormTransportContainer.class)))
                    .thenReturn(renderedPath);

            assertThat(manager.renderForm(request, response, form)).isEqualTo(renderedPath);
        }

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, "123");
        verify(securityInfoManager, never()).hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, null);
    }

    @Test
    @DisplayName("should throw PDFGenerationException when conversion silently returns null")
    void shouldThrowPdfGenerationException_whenConversionReturnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        LoggedInInfo.setLoggedInInfoIntoRequest(request, loggedInInfo);
        EctFormData.PatientForm form = new EctFormData.PatientForm("formAnnual", "Annual", 45, 123);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, "123")).thenReturn(true);

        try (MockedStatic<ConvertToEdoc> convertToEdocMock = mockStatic(ConvertToEdoc.class)) {
            // ConvertToEdoc swallows an internal conversion failure and returns null (does not throw).
            convertToEdocMock.when(() -> ConvertToEdoc.saveAsTempPDF(any(FormTransportContainer.class)))
                    .thenReturn(null);

            // Must fail with a named exception, not return null (which callers NPE on via path.toString()).
            assertThatThrownBy(() -> manager.renderForm(request, response, form))
                    .isInstanceOf(PDFGenerationException.class)
                    .hasMessageContaining("could not be converted into a PDF");
        }
    }

    @Test
    @DisplayName("should use request demographic when rendering without patient form")
    void shouldUseRequestDemographic_whenRenderingWithoutPatientForm() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        LoggedInInfo.setLoggedInInfoIntoRequest(request, loggedInInfo);
        request.setParameter("demographicNo", "123");

        assertThatThrownBy(() -> manager.renderForm(request, response, null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_form)");

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, "123");
        verify(securityInfoManager, never()).hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, null);
    }

    @Test
    @DisplayName("should use demographic-scoped form read privilege when fetching form by id")
    void shouldUseDemographicScopedPrivilege_whenFetchingFormById() {
        assertThatThrownBy(() -> manager.getFormById(loggedInInfo, 45, 123))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_form)");

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, "123");
        verify(securityInfoManager, never()).hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, null);
    }
}
