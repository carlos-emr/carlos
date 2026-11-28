/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.data.MeasurementTypes;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Field-length tests for the Add Measurement Type form (issue #6342).
 *
 * <p>The description, display name and measuring instruction are stored in the VARCHAR(255)
 * {@code typeDescription}, {@code typeDisplayName} and {@code measuringInstruction} columns of
 * {@code measurementType} (V1 baseline schema). Each submitted value must be measured itself: a
 * value that fills its column is stored whole, and one character more is reported on the form,
 * naming that field, before anything is written. The measurement code keeps its own 50-character
 * check, so a short code no longer lets a longer text field through.</p>
 *
 * <p>{@code getText} echoes the message key and its arguments, so the assertions read exactly what
 * the form's {@code actionErrors} list is asked to show.</p>
 *
 * @since 2026-11-25
 */
@Tag("unit")
class EctAddMeasurementType2ActionFieldLengthUnitTest extends CarlosUnitTestBase {

    /** Already upper case, so the duplicate lookup sees the code unchanged. */
    private static final String CODE = "SYNLEN";
    private static final int COLUMN_WIDTH = 255;
    /** Characters that the form's own pattern ({@code ^[\w\s,.?]*$}) accepts. */
    private static final String PERMITTED = "Synthetic text, v1.0? ok_";

    private MeasurementTypeDao dao;
    private SecurityInfoManager security;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servletContext;
    private MockedStatic<LoggedInInfo> loginContext;
    private MockedStatic<MeasurementTypes> catalog;
    private EctAddMeasurementType2Action action;

    /** The three free-text columns, each named on the form by the label the action gives it. */
    private enum TextField {
        DESCRIPTION("The type description ",
                EctAddMeasurementType2Action::setTypeDesc, MeasurementType::getTypeDescription),
        DISPLAY_NAME("The display name ",
                EctAddMeasurementType2Action::setTypeDisplayName, MeasurementType::getTypeDisplayName),
        MEASURING_INSTRUCTION("The measuring instruction ",
                EctAddMeasurementType2Action::setMeasuringInstrc, MeasurementType::getMeasuringInstruction);

        private final String label;
        private final BiConsumer<EctAddMeasurementType2Action, String> submit;
        private final Function<MeasurementType, String> stored;

        TextField(String label, BiConsumer<EctAddMeasurementType2Action, String> submit,
                  Function<MeasurementType, String> stored) {
            this.label = label;
            this.submit = submit;
            this.stored = stored;
        }
    }

    @BeforeEach
    void setUp() {
        dao = mock(MeasurementTypeDao.class);
        security = mock(SecurityInfoManager.class);
        LoggedInInfo login = mock(LoggedInInfo.class);
        registerMock(MeasurementTypeDao.class, dao);
        registerMock(SecurityInfoManager.class, security);
        request = new MockHttpServletRequest("POST", "/encounter/oscarMeasurements/AddMeasurementType");
        response = new MockHttpServletResponse();
        servletContext = mockStatic(ServletActionContext.class);
        servletContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletContext.when(ServletActionContext::getResponse).thenReturn(response);
        loginContext = mockStatic(LoggedInInfo.class);
        loginContext.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
        catalog = mockStatic(MeasurementTypes.class);
        MeasurementTypes mockCatalog = mock(MeasurementTypes.class);
        catalog.when(MeasurementTypes::getInstance).thenReturn(mockCatalog);
        when(security.hasPrivilege(login, "_admin", "w", null)).thenReturn(true);

        action = spy(new EctAddMeasurementType2Action());
        doAnswer(invocation -> rendered(invocation.getArgument(0), invocation.getArgument(1)))
                .when(action).getText(anyString(), any(String[].class));
        // The success message uses the (key, default text) overload.
        doAnswer(invocation -> invocation.getArgument(0)).when(action).getText(anyString(), anyString());
        action.setType(CODE);
        action.setTypeDesc("Synthetic description");
        action.setTypeDisplayName("Synthetic display name");
        action.setMeasuringInstrc("Synthetic instruction");
        action.setValidation("1");
    }

    @AfterEach
    void tearDown() {
        catalog.close();
        loginContext.close();
        servletContext.close();
    }

    @ParameterizedTest
    @EnumSource(TextField.class)
    void shouldStoreTheWholeValue_whenATextFieldFillsItsColumn(TextField field) throws Exception {
        String value = permittedText(COLUMN_WIDTH);
        field.submit.accept(action, value);

        assertThat(action.execute()).isEqualTo(EctAddMeasurementType2Action.SUCCESS);

        assertThat(request.getAttribute("actionErrors")).isNull();
        ArgumentCaptor<MeasurementType> saved = ArgumentCaptor.forClass(MeasurementType.class);
        verify(dao).persist(saved.capture());
        assertThat(field.stored.apply(saved.getValue())).isEqualTo(value).hasSize(COLUMN_WIDTH);
        assertThat(saved.getValue().getType()).isEqualTo(CODE);
    }

    @ParameterizedTest
    @EnumSource(TextField.class)
    void shouldReportTheFieldWithoutWriting_whenATextFieldExceedsItsColumn(TextField field) throws Exception {
        String value = permittedText(COLUMN_WIDTH + 1);
        field.submit.accept(action, value);

        assertThat(action.execute()).isEqualTo("failure");

        // Only the length error: every character is one the form accepts.
        assertThat(request.getAttribute("actionErrors")).isEqualTo(List.of(lengthError(field.label + value)));
        verifyNoWrite();
    }

    @Test
    void shouldReportEveryOverlongTextField_whenAllThreeExceedTheirColumns() throws Exception {
        String value = permittedText(COLUMN_WIDTH + 1);
        for (TextField field : TextField.values()) {
            field.submit.accept(action, value);
        }

        assertThat(action.execute()).isEqualTo("failure");

        assertThat(request.getAttribute("actionErrors")).isEqualTo(List.of(
                lengthError(TextField.DESCRIPTION.label + value),
                lengthError(TextField.DISPLAY_NAME.label + value),
                lengthError(TextField.MEASURING_INSTRUCTION.label + value)));
        verifyNoWrite();
    }

    @Test
    void shouldHtmlEncodeTheSubmittedText_whenReportingAnOverlongField() throws Exception {
        String tail = permittedText(COLUMN_WIDTH - 2);
        action.setTypeDesc("<b>" + tail);

        assertThat(action.execute()).isEqualTo("failure");

        // The form prints each action error as markup, so the submitted text must arrive encoded.
        assertThat(request.getAttribute("actionErrors")).isEqualTo(List.of(
                rendered("errors.invalid", new String[]{TextField.DESCRIPTION.label + "&lt;b&gt;" + tail}),
                lengthError(TextField.DESCRIPTION.label + "&lt;b&gt;" + tail)));
        verifyNoWrite();
    }

    @Test
    void shouldStillReportAnOverlongField_whenMeasuringInstructionIsOmitted() throws Exception {
        String value = permittedText(COLUMN_WIDTH + 1);
        action.setMeasuringInstrc(null);
        action.setTypeDisplayName(value);

        assertThat(action.execute()).isEqualTo("failure");

        assertThat(request.getAttribute("actionErrors"))
                .isEqualTo(List.of(lengthError(TextField.DISPLAY_NAME.label + value)));
        verifyNoWrite();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \t "})
    void shouldReportRequiredFieldsWithoutWriting_whenDescriptionAndDisplayNameAreBlank(String value) throws Exception {
        action.setTypeDesc(value);
        action.setTypeDisplayName(value);

        assertThat(action.execute()).isEqualTo("failure");

        assertThat(request.getAttribute("actionErrors")).isEqualTo(List.of(
                rendered("errors.required", new String[]{"The type description"}),
                rendered("errors.required", new String[]{"The display name"})));
        verifyNoWrite();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \t "})
    void shouldRejectBlankTypeWithoutQueryingOrWriting_whenTypeIsMissingOrWhitespace(String value) throws Exception {
        action.setType(value);

        assertThat(action.execute()).isEqualTo("failure");

        assertThat(request.getAttribute("actionErrors")).isEqualTo(List.of(
                rendered("errors.invalid", new String[]{"Measurement type is required"})));
        verifyNoInteractions(dao);
    }

    @Test
    void shouldDetectDuplicateType_whenSubmittedCodeHasSurroundingWhitespace() throws Exception {
        when(dao.findByType(CODE)).thenReturn(List.of(new MeasurementType()));
        doAnswer(invocation -> invocation.getArgument(0)).when(action).getText("error.encounter.Measurements.duplicateTypeName");
        action.setType("  syNlEn  ");

        assertThat(action.execute()).isEqualTo("failure");

        assertThat(request.getAttribute("actionErrors"))
                .isEqualTo(List.of("error.encounter.Measurements.duplicateTypeName"));
        verifyNoWrite();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \t "})
    void shouldReportRequiredValidationWithoutWriting_whenValidationIsBlank(String value) throws Exception {
        action.setValidation(value);

        assertThat(action.execute()).isEqualTo("failure");

        assertThat(request.getAttribute("actionErrors")).isEqualTo(List.of(
                rendered("errors.required", new String[]{"The validation"})));
        verifyNoWrite();
    }

    @Test
    void shouldReportInvalidAndOverlongValidationWithoutWriting_whenValidationExceedsColumnWidth() throws Exception {
        String overlong = "1".repeat(100) + "<";
        action.setValidation(overlong);

        assertThat(action.execute()).isEqualTo("failure");

        String encoded = "The validation " + "1".repeat(100) + "&lt;";
        assertThat(request.getAttribute("actionErrors")).isEqualTo(List.of(
                rendered("errors.invalid", new String[]{encoded}),
                rendered("errors.maxlength", new String[]{encoded, "100"})));
        verifyNoWrite();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldRefuseAnOverlongSubmission_whenNotPosted(String method) throws Exception {
        request.setMethod(method);
        action.setTypeDesc(permittedText(COLUMN_WIDTH + 1));

        assertThat(action.execute()).isEqualTo(EctAddMeasurementType2Action.NONE);

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(dao, security);
    }

    /** A rejection may only have looked the code up for duplicates: no persist, merge or remove. */
    private void verifyNoWrite() {
        verify(dao).findByType(CODE);
        verifyNoMoreInteractions(dao);
    }

    private static String permittedText(int length) {
        return PERMITTED.repeat(length / PERMITTED.length() + 1).substring(0, length);
    }

    private static String lengthError(String errorField) {
        return rendered("errors.maxlength", new String[]{errorField, String.valueOf(COLUMN_WIDTH)});
    }

    private static String rendered(String key, String[] args) {
        return key + "|" + String.join("|", args);
    }
}
