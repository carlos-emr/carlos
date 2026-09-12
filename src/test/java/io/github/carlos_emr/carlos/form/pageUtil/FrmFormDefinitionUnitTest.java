/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
package io.github.carlos_emr.carlos.form.pageUtil;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Vector;
import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementTypesBean;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("Measurement form definition validation before saving")
class FrmFormDefinitionUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest requestFor(String definition) {
        MockServletContext context = new MockServletContext() {
            @Override
            public InputStream getResourceAsStream(String path) {
                assertThat(path).isEqualTo("/form/Fixture.xml");
                return definition == null ? null : new ByteArrayInputStream(definition.getBytes(StandardCharsets.UTF_8));
            }
        };
        MockHttpServletRequest request = new MockHttpServletRequest(context);
        LoggedInInfo user = new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(user, "_form", "w", null)).thenReturn(true);
        registerMock(SecurityInfoManager.class, security);
        registerMock(MeasurementTypeDao.class, mock(MeasurementTypeDao.class));
        return request;
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-rule", "empty", "malformed", "missing"})
    @DisplayName("should reject incomplete or unreadable definitions before any clinical write")
    void shouldRejectDefinition_beforeSavingMeasurements(String scenario) throws Exception {
        String definition = switch (scenario) {
            case "no-rule" -> "<formProp><measurement><type>BP</type></measurement></formProp>";
            case "empty" -> "<formProp/>";
            case "malformed" -> "not XML";
            default -> null;
        };
        MockHttpServletRequest request = requestFor(definition);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MeasurementDao measurements = mock(MeasurementDao.class);
        EncounterFormDao forms = mock(EncounterFormDao.class);
        var registeredForm = new io.github.carlos_emr.carlos.commn.model.EncounterForm();
        registeredForm.setFormValue("SetupForm?formName=Fixture");
        when(forms.findByFormTable("formFixture")).thenReturn(java.util.List.of(registeredForm));
        registerMock(MeasurementDao.class, measurements);
        registerMock(EncounterFormDao.class, forms);
        try (MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            FrmForm2Action action = new FrmForm2Action();
            action.setValue("formName", "Fixture");
            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getErrorMessage()).contains("No measurements were saved").doesNotContain("/form/", "elementAt");
            verifyNoInteractions(measurements);
            verify(forms).findByFormTable("formFixture");
            verifyNoMoreInteractions(forms);
        }
    }

    @Test
    @DisplayName("should retain a valid definition and its validation rules")
    void shouldLoadDefinition_whenEveryMeasurementHasARule() {
        MockHttpServletRequest request = requestFor("<formProp><measurement><type>BP</type>"
                + "<validationRule><name>Fixture rule</name><regularExp>.*</regularExp></validationRule>"
                + "</measurement></formProp>");
        try (MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            Vector<EctMeasurementTypesBean> definitions = ReflectionTestUtils.invokeMethod(
                    new FrmForm2Action(), "loadMeasurementTypes", "Fixture");
            assertThat(definitions).hasSize(1);
            assertThat(definitions.firstElement().getValidationRules()).hasSize(1);
        }
    }
}
