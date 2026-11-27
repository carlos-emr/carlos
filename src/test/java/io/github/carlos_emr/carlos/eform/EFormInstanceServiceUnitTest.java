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
package io.github.carlos_emr.carlos.eform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.dao.EFormValueDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.EFormValue;
import io.github.carlos_emr.carlos.eform.EFormInstanceService.EFormInstanceException;
import io.github.carlos_emr.carlos.eform.EFormInstanceService.EFormInstanceException.Reason;
import io.github.carlos_emr.carlos.eform.EFormInstanceService.InstanceAccessDeniedException;
import io.github.carlos_emr.carlos.encounter.data.EctProgramManager;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Unit tests for {@link EFormInstanceService}: template filling, the new-revision-and-retire edit
 * model, the soft delete, and the global, patient-scoped and role-type privilege checks. The
 * template is filled by the real {@link io.github.carlos_emr.carlos.eform.data.EForm}; persistence
 * and security are mocked.
 *
 * @since 2026-11-17
 */
@Tag("unit")
@Tag("fast")
@Tag("eform")
@DisplayName("EFormInstanceService")
class EFormInstanceServiceUnitTest extends CarlosUnitTestBase {

    private static final int FID = 3;
    private static final int DEMOGRAPHIC_NO = 42;
    private static final int SAVED_FDID = 77;
    /** The context path the REST layer passes, for the links chart writes put in notes. */
    private static final String CONTEXT = "/carlos";
    private static final Map<String, String> NO_VALUES = Map.of();
    private static final String TEMPLATE_HTML = "<html><body><form>"
            + "<input type=\"text\" name=\"bp\" value=\"\">"
            + "<textarea name=\"note\">template default</textarea>"
            + "<input type=\"text\" name=\"untouched\" value=\"kept\">"
            + "<input type=\"checkbox\" name=\"smoker\" value=\"yes\" checked>"
            + "<input type=\"checkbox\" name=\"flag\">"
            + "<input type=\"checkbox\" name=\"pair\" value=\"a\"><input type=\"checkbox\" name=\"pair\" value=\"b\">"
            + "<input type=\"radio\" name=\"side\" value=\"L\"><input type=\"radio\" name=\"side\" value=\"R\">"
            + "<select name=\"route\"><option value=\"po\">PO</option><option value=\"iv\">IV</option></select>"
            + "<input type=\"number\" name=\"count\" value=\"\">"
            + "<input type=\"onclick\" name=\"script\">"
            + "</form></body></html>";

    private SecurityInfoManager securityInfoManager;
    private EformDataManager eformDataManager;
    private EFormDao eFormDao;
    private EFormDataDao eFormDataDao;
    private EFormValueDao eFormValueDao;
    private DemographicDao demographicDao;
    private EFormDocsDao eFormDocsDao;
    private ConsultDocsDao consultDocsDao;
    private EctProgramManager ectProgramManager;
    private LoggedInInfo loggedInInfo;
    private EFormInstanceService service;

    /** Rows "persisted" by the mocked manager, readable back through the mocked DAO. */
    private final Map<Integer, EFormData> savedRows = new HashMap<>();
    private final List<io.github.carlos_emr.carlos.eform.data.EForm> savedForms = new ArrayList<>();

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        eformDataManager = mock(EformDataManager.class);
        eFormDao = mock(EFormDao.class);
        eFormDataDao = mock(EFormDataDao.class);
        eFormValueDao = mock(EFormValueDao.class);
        demographicDao = mock(DemographicDao.class);
        eFormDocsDao = mock(EFormDocsDao.class);
        consultDocsDao = mock(ConsultDocsDao.class);
        ectProgramManager = mock(EctProgramManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), nullable(String.class)))
                .thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), any())).thenReturn(true);
        when(eFormDao.findById(FID)).thenReturn(template(true, null));
        when(demographicDao.getDemographicById(DEMOGRAPHIC_NO)).thenReturn(new Demographic());

        when(eformDataManager.saveEformData(eq(loggedInInfo), any())).thenAnswer(invocation -> {
            io.github.carlos_emr.carlos.eform.data.EForm form = invocation.getArgument(1);
            savedForms.add(form);
            EFormData row = EFormUtil.toEFormData(form);
            row.setId(SAVED_FDID);
            savedRows.put(SAVED_FDID, row);
            return SAVED_FDID;
        });
        when(eFormDataDao.find(SAVED_FDID)).thenAnswer(invocation -> savedRows.get(SAVED_FDID));

        service = new EFormInstanceService(securityInfoManager, eformDataManager, eFormDao, eFormDataDao,
                eFormValueDao, demographicDao, eFormDocsDao, consultDocsDao, ectProgramManager);
    }

    private static EForm template(boolean current, String roleType) {
        EForm template = new EForm();
        ReflectionTestUtils.setField(template, "id", FID); // the entity has no id setter
        template.setFormName("Vitals");
        template.setSubject("Template subject");
        template.setFormHtml(TEMPLATE_HTML);
        template.setCurrent(current);
        template.setRoleType(roleType);
        return template;
    }

    private static EFormData instance(int fdid, boolean current) {
        EFormData data = new EFormData();
        data.setId(fdid);
        data.setFormId(FID);
        data.setFormName("Vitals");
        data.setSubject("Earlier subject");
        data.setDemographicId(DEMOGRAPHIC_NO);
        data.setCurrent(current);
        data.setFormData("<html>earlier</html>");
        return data;
    }

    private void denyPatientScoped(String privilege) {
        when(securityInfoManager.hasPrivilege(any(), eq("_eform"), eq(privilege), eq(String.valueOf(DEMOGRAPHIC_NO))))
                .thenReturn(false);
    }

    private List<EFormValue> persistedValues() {
        ArgumentCaptor<EFormValue> captor = ArgumentCaptor.forClass(EFormValue.class);
        verify(eFormValueDao, org.mockito.Mockito.atLeast(0)).persist(captor.capture());
        return captor.getAllValues();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("should fill the template and store nonblank values when the template is active")
        void shouldFillTemplateAndStoreNonblankValues_whenTemplateActive() {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("bp", "120/80");
            values.put("note", "");          // explicit clear
            values.put("untouched", null);   // not submitted: template default stays

            EFormData saved = service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, values, CONTEXT);

            assertThat(saved.getId()).isEqualTo(SAVED_FDID);
            assertThat(saved.isCurrent()).isTrue();
            assertThat(saved.getDemographicId()).isEqualTo(DEMOGRAPHIC_NO);
            assertThat(saved.getProviderNo()).isEqualTo("999998");
            assertThat(saved.getSubject()).isEqualTo("Template subject");
            assertThat(saved.getFormData())
                    .contains("value=\"120/80\"")
                    .doesNotContain("template default")
                    .contains("value=\"kept\"")
                    .contains("addEForm?efmfid=3");

            List<EFormValue> rows = persistedValues();
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getVarName()).isEqualTo("bp");
            assertThat(rows.get(0).getVarValue()).isEqualTo("120/80");
            assertThat(rows.get(0).getFormDataId()).isEqualTo(SAVED_FDID);
            assertThat(rows.get(0).getFormId()).isEqualTo(FID);
            assertThat(rows.get(0).getDemographicId()).isEqualTo(DEMOGRAPHIC_NO);
        }

        @Test
        @DisplayName("should escape markup when a value contains HTML")
        void shouldEscapeMarkup_whenValueContainsHtml() {
            Map<String, String> values = Map.of("note", "</textarea><script>alert(1)</script>");

            EFormData saved = service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, "s", values, CONTEXT);

            assertThat(saved.getFormData()).doesNotContain("<script>alert(1)</script>");
            assertThat(saved.getFormData()).contains("&lt;script&gt;");
        }

        @Test
        @DisplayName("should refuse when the template is retired")
        void shouldRefuse_whenTemplateRetired() {
            when(eFormDao.findById(FID)).thenReturn(template(false, null));

            assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, NO_VALUES, CONTEXT))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.TEMPLATE_UNAVAILABLE);
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should refuse when the patient does not exist")
        void shouldRefuse_whenPatientMissing() {
            when(demographicDao.getDemographicById(DEMOGRAPHIC_NO)).thenReturn(null);

            assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, NO_VALUES, CONTEXT))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.PATIENT_NOT_FOUND);
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should answer as for a missing patient, before looking the patient up, when the caller may not write their eForms")
        void shouldReportPatientNotFound_whenPatientScopedWriteDenied() {
            denyPatientScoped(SecurityInfoManager.WRITE);

            assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, NO_VALUES, CONTEXT))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.PATIENT_NOT_FOUND);
            verifyNoInteractions(demographicDao);
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should refuse when the template is role restricted and the caller lacks the role object")
        void shouldRefuse_whenRoleTypeDenied() {
            when(eFormDao.findById(FID)).thenReturn(template(true, "doctor"));
            when(securityInfoManager.hasPrivilege(any(), eq("_eform.doctor"), anyString(), nullable(String.class)))
                    .thenReturn(false);

            assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, NO_VALUES, CONTEXT))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_eform.doctor)");
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should refuse when a field name is blank")
        void shouldRefuse_whenFieldNameBlank() {
            Map<String, String> values = Map.of(" ", "x");
            assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, values, CONTEXT))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.INVALID_FIELD_NAME);
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should refuse without saving when a value names a field the renderer cannot fill")
        void shouldRefuseWithoutSaving_whenFieldUnsupported() {
            for (String name : List.of("absent", "count", "script")) {
                Map<String, String> values = Map.of(name, "7");
                assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, values, CONTEXT))
                        .as(name)
                        .isInstanceOf(EFormInstanceException.class)
                        .extracting(e -> ((EFormInstanceException) e).getReason())
                        .isEqualTo(Reason.UNSUPPORTED_FIELD);
            }
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should refuse without saving when a radio or select value matches no choice")
        void shouldRefuseWithoutSaving_whenChoiceUnknown() {
            Map<String, String> badRadio = Map.of("side", "X");
            Map<String, String> badSelect = Map.of("route", "im");
            Map<String, String> blankSingleSelectWithoutEmptyOption = Map.of("route", "");
            assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, badRadio, CONTEXT))
                    .isInstanceOf(EFormInstanceException.class);
            assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, badSelect, CONTEXT))
                    .isInstanceOf(EFormInstanceException.class);
            // route has only PO and IV; clearing a single select with no empty option would silently show PO.
            assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null,
                    blankSingleSelectWithoutEmptyOption, CONTEXT))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.UNSUPPORTED_FIELD);
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should refuse whitespace on a single select even when it has an empty option")
        void shouldRefuseWhitespace_whenSingleSelectHasEmptyOption() {
            String templateHtml = "<html><body><form><select name=\"dose\">"
                    + "<option value=\"\">Choose</option><option value=\"5\" selected>5 mg</option>"
                    + "</select></form></body></html>";
            EForm template = template(true, null);
            template.setFormHtml(templateHtml);
            when(eFormDao.findById(FID)).thenReturn(template);
            Map<String, String> whitespace = Map.of("dose", "   ");

            assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, whitespace, CONTEXT))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.UNSUPPORTED_FIELD);
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should accept an empty string to clear every selection of a multiple select")
        void shouldClearSelections_whenMultipleSelectClearedWithEmptyString() {
            String templateHtml = "<html><body><form><select name=\"tags\" multiple>"
                    + "<option value=\"a\" selected>A</option><option value=\"b\" selected>B</option>"
                    + "</select></form></body></html>";
            EForm template = template(true, null);
            template.setFormHtml(templateHtml);
            when(eFormDao.findById(FID)).thenReturn(template);

            EFormData cleared = service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, Map.of("tags", ""), CONTEXT);

            assertThat(Jsoup.parse(cleared.getFormData()).select("select[name=tags] option[selected]")).isEmpty();
            assertThat(persistedValues()).isEmpty();
        }

        @Test
        @DisplayName("should uncheck a checked-by-default checkbox when its value is explicitly cleared")
        void shouldUncheckCheckbox_whenValueExplicitlyCleared() {
            EFormData cleared = service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, Map.of("smoker", ""), CONTEXT);
            assertThat(org.jsoup.Jsoup.parse(cleared.getFormData()).selectFirst("input[name=smoker]").hasAttr("checked"))
                    .isFalse();

            EFormData kept = service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, new HashMap<>(Map.of("bp", "1")), CONTEXT);
            assertThat(org.jsoup.Jsoup.parse(kept.getFormData()).selectFirst("input[name=smoker]").hasAttr("checked"))
                    .isTrue();
            assertThat(persistedValues()).extracting(EFormValue::getVarName).containsExactly("bp");
        }

        @Test
        @DisplayName("should accept a checkbox value only when it is the box's own value, as a browser submits it")
        void shouldAcceptCheckboxValue_onlyWhenItIsTheBoxValue() {
            EFormData checked = service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, Map.of("smoker", "yes", "flag", "on"), CONTEXT);
            var html = org.jsoup.Jsoup.parse(checked.getFormData());
            assertThat(html.selectFirst("input[name=smoker]").hasAttr("checked")).isTrue();
            assertThat(html.selectFirst("input[name=flag]").hasAttr("checked")).isTrue();

            for (Map<String, String> refused : List.of(Map.of("smoker", "on"), Map.of("smoker", "X"),
                    Map.of("flag", "yes"), Map.of("pair", "a"))) {
                assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, refused, CONTEXT))
                        .as(refused.toString())
                        .isInstanceOf(EFormInstanceException.class)
                        .extracting(e -> ((EFormInstanceException) e).getReason())
                        .isEqualTo(Reason.UNSUPPORTED_FIELD);
            }
        }

        @Test
        @DisplayName("should answer as for a missing patient, before looking the patient up, when the record is restricted")
        void shouldReportPatientNotFound_whenPatientRecordRestricted() {
            when(securityInfoManager.isAllowedAccessToPatientRecord(any(), eq(DEMOGRAPHIC_NO))).thenReturn(false);

            assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, NO_VALUES, CONTEXT))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.PATIENT_NOT_FOUND);
            verifyNoInteractions(demographicDao);
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should use the caller's subject when one is given")
        void shouldUseCallerSubject_whenSubjectGiven() {
            EFormData saved = service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, "Caller subject", Map.of(), CONTEXT);

            assertThat(saved.getSubject()).isEqualTo("Caller subject");
        }

        @Test
        @DisplayName("should save the template defaults and no value rows when no values are given")
        void shouldSaveTemplateDefaultsWithoutValueRows_whenValuesNull() {
            EFormData saved = service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, null, CONTEXT);

            assertThat(saved.getFormData()).contains("template default").contains("value=\"kept\"");
            verify(eFormValueDao, never()).persist(any());
        }

        @Test
        @DisplayName("should store no value row for a whitespace-only value, as the browser save does")
        void shouldStoreNoValueRow_whenValueIsWhitespace() {
            service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, Map.of("bp", "   "), CONTEXT);

            verify(eFormValueDao, never()).persist(any());
        }

        @Test
        @DisplayName("should refuse when the template does not exist")
        void shouldRefuse_whenTemplateMissing() {
            when(eFormDao.findById(FID)).thenReturn(null);

            assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, NO_VALUES, CONTEXT))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.TEMPLATE_UNAVAILABLE);
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should refuse before looking up the template or patient when the caller has no eForm write right")
        void shouldRefuseBeforeLookup_whenGlobalWriteDenied() {
            when(securityInfoManager.hasPrivilege(any(), eq("_eform"), eq(SecurityInfoManager.WRITE), nullable(String.class)))
                    .thenReturn(false);

            assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, NO_VALUES, CONTEXT))
                    .isInstanceOf(SecurityException.class);
            verifyNoInteractions(eFormDao, demographicDao, eformDataManager);
        }

        @Test
        @DisplayName("should fail rather than return nothing when the saved row cannot be read back")
        void shouldFail_whenSavedRowCannotBeReadBack() {
            when(eFormDataDao.find(SAVED_FDID)).thenReturn(null);

            assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, NO_VALUES, CONTEXT))
                    .isInstanceOf(IllegalStateException.class);
        }
        @Test
        @DisplayName("should refuse before any lookup when a field name is longer than eform_values.var_name")
        void shouldRefuseBeforeLookup_whenFieldNameTooLong() {
            Map<String, String> values = Map.of("f".repeat(EFormInstanceService.FIELD_NAME_MAX_LENGTH + 1), "x");

            assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, values, CONTEXT))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.INVALID_FIELD_NAME);
            verifyNoInteractions(eFormDao, demographicDao, eformDataManager);
        }

        @Test
        @DisplayName("should refuse a subject longer than eform_data.subject and accept one that fits")
        void shouldRefuseSubject_whenLongerThanColumn() {
            String tooLong = "s".repeat(EFormInstanceService.SUBJECT_MAX_LENGTH + 1);
            Map<String, String> empty = Map.of();

            assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, tooLong, empty, CONTEXT))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.INVALID_SUBJECT);
            verify(eformDataManager, never()).saveEformData(any(), any());

            String fits = "s".repeat(EFormInstanceService.SUBJECT_MAX_LENGTH);
            assertThat(service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, fits, empty, CONTEXT).getSubject()).isEqualTo(fits);
        }

        @Test
        @DisplayName("should fill oscarDB fields the caller leaves out, as the new-form page does, and store their values")
        void shouldPrefillDatabaseFields_whenCallerLeavesThemOut() {
            EForm template = template(true, null);
            template.setFormHtml("<html><body><form>"
                    + "<input type=\"text\" name=\"visit\" oscarDB=today>"
                    + "<input type=\"text\" name=\"seen\" oscarDB=today>"
                    + "<input type=\"text\" name=\"bp\" value=\"\">"
                    + "</form></body></html>");
            when(eFormDao.findById(FID)).thenReturn(template);

            try (org.mockito.MockedStatic<EFormUtil> util = org.mockito.Mockito.mockStatic(EFormUtil.class,
                    org.mockito.Mockito.CALLS_REAL_METHODS)) {
                util.when(() -> EFormUtil.getValues(org.mockito.ArgumentMatchers.anyList(),
                        any(io.github.carlos_emr.carlos.report.data.ParameterizedSql.class)))
                        .thenReturn(new ArrayList<>(List.of("2026-11-26")));

                EFormData saved = service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null,
                        Map.of("seen", "2026-11-01", "bp", "120/80"), CONTEXT);

                Document html = Jsoup.parse(saved.getFormData());
                assertThat(html.selectFirst("input[name=visit]").attr("value")).isEqualTo("2026-11-26");
                // The caller's value replaces the prefill rather than sitting beside it.
                assertThat(html.selectFirst("input[name=seen]").attr("value")).isEqualTo("2026-11-01");
                assertThat(saved.getFormData().split("2026-11-26", -1)).hasSize(2);
                assertThat(persistedValues()).extracting(EFormValue::getVarName, EFormValue::getVarValue)
                        .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("seen", "2026-11-01"),
                                org.assertj.core.groups.Tuple.tuple("bp", "120/80"),
                                org.assertj.core.groups.Tuple.tuple("visit", "2026-11-26"));
            }
        }

        @Test
        @DisplayName("should run the template's chart writes once with the saved fdid, the provider's program and the context path")
        void shouldRunTemplateChartWrites_withSavedFdidAndProgram() {
            when(ectProgramManager.getProgramBeans("999998", null)).thenReturn(
                    List.of(new io.github.carlos_emr.carlos.util.LabelValueBean("Clinic", "10016")));
            when(ectProgramManager.getDefaultProgramId("999998")).thenReturn(10016);

            try (org.mockito.MockedStatic<EFormUtil> util = org.mockito.Mockito.mockStatic(EFormUtil.class,
                    org.mockito.Mockito.CALLS_REAL_METHODS)) {
                service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, Map.of("bp", "120/80", "note", ""), CONTEXT);

                ArgumentCaptor<ArrayList<String>> names = ArgumentCaptor.forClass(ArrayList.class);
                ArgumentCaptor<ArrayList<String>> values = ArgumentCaptor.forClass(ArrayList.class);
                util.verify(() -> EFormUtil.writeEformTemplate(eq(loggedInInfo), names.capture(), values.capture(),
                        any(io.github.carlos_emr.carlos.eform.data.EForm.class), eq(String.valueOf(SAVED_FDID)),
                        eq("10016"), eq(CONTEXT)), times(1));
                // The same nonblank values the browser save passes: the cleared note is not among them.
                assertThat(names.getValue()).containsExactly("bp");
                assertThat(values.getValue()).containsExactly("120/80");
            }
        }

        @Test
        @DisplayName("should let a chart-write failure propagate out of create")
        void shouldPropagateFailure_whenChartWritesFail() {
            Map<String, String> values = Map.of("bp", "1");
            try (org.mockito.MockedStatic<EFormUtil> util = org.mockito.Mockito.mockStatic(EFormUtil.class,
                    org.mockito.Mockito.CALLS_REAL_METHODS)) {
                util.when(() -> EFormUtil.writeEformTemplate(any(), any(), any(), any(), any(), any(), any()))
                        .thenThrow(new IllegalStateException("note save failed"));

                // Only propagation is shown here: the service is built directly and persistence is
                // mocked. The rollback itself is @Transactional's, on the Spring-managed bean.
                assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, values, CONTEXT))
                        .isInstanceOf(IllegalStateException.class);
            }
        }
    }

    @Nested
    @DisplayName("revise")
    class Revise {

        @Test
        @DisplayName("should save a new revision and retire the old row when the instance is current")
        void shouldSaveRevisionAndRetireOldRow_whenInstanceCurrent() {
            EFormData previous = instance(5, true);
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(previous);

            EFormData revision = service.revise(loggedInInfo, 5, null, Map.of("bp", "130/85"), CONTEXT);

            assertThat(revision.getId()).isEqualTo(SAVED_FDID);
            assertThat(revision.isCurrent()).isTrue();
            assertThat(revision.getDemographicId()).isEqualTo(DEMOGRAPHIC_NO);
            assertThat(revision.getSubject()).isEqualTo("Earlier subject");
            assertThat(revision.getFormData()).contains("value=\"130/85\"");
            assertThat(previous.isCurrent()).isFalse();
            assertThat(previous.getFormData()).isEqualTo("<html>earlier</html>");
            verify(eFormDataDao).merge(previous);
        }

        @Test
        @DisplayName("should refuse without saving when the instance was already retired")
        void shouldRefuseWithoutSaving_whenInstanceRetired() {
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(instance(5, false));
            Map<String, String> values = Map.of("bp", "1");

            assertThatThrownBy(() -> service.revise(loggedInInfo, 5, null, values, CONTEXT))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.RETIRED);
            verify(eformDataManager, never()).saveEformData(any(), any());
            verify(eFormDataDao, never()).merge(any());
        }

        @Test
        @DisplayName("should report not found when no instance has the id")
        void shouldReportNotFound_whenInstanceMissing() {
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(null);

            assertThatThrownBy(() -> service.revise(loggedInInfo, 5, null, NO_VALUES, CONTEXT))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.NOT_FOUND);
        }

        @Test
        @DisplayName("should refuse as an instance-level denial without saving when the caller may not write this patient's eForms")
        void shouldRefuseWithoutSaving_whenPatientScopedWriteDenied() {
            EFormData previous = instance(5, true);
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(previous);
            denyPatientScoped(SecurityInfoManager.WRITE);

            assertThatThrownBy(() -> service.revise(loggedInInfo, 5, null, NO_VALUES, CONTEXT))
                    .isInstanceOf(InstanceAccessDeniedException.class);
            assertThat(previous.isCurrent()).isTrue();
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should refuse as an instance-level denial when the patient record is restricted")
        void shouldRefuseAsInstanceDenial_whenPatientRecordRestricted() {
            EFormData previous = instance(5, true);
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(previous);
            when(securityInfoManager.isAllowedAccessToPatientRecord(any(), eq(DEMOGRAPHIC_NO))).thenReturn(false);

            assertThatThrownBy(() -> service.revise(loggedInInfo, 5, null, NO_VALUES, CONTEXT))
                    .isInstanceOf(EFormInstanceService.InstanceAccessDeniedException.class);
            assertThat(previous.isCurrent()).isTrue();
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should carry forward a stored value the template cannot show, sent back or omitted")
        void shouldCarryForwardStoredUnsupportedValue_whenSentBackOrOmitted() {
            EFormValue stored = new EFormValue();
            stored.setVarName("count");
            stored.setVarValue("7");
            when(eFormValueDao.findByFormDataId(5)).thenReturn(List.of(stored));
            when(eFormDataDao.lockForAttachmentSync(5)).thenAnswer(call -> instance(5, true));

            service.revise(loggedInInfo, 5, null, Map.of("count", "7", "bp", "1"), CONTEXT);
            service.revise(loggedInInfo, 5, null, Map.of("bp", "2"), CONTEXT);

            assertThat(persistedValues()).extracting(EFormValue::getVarName, EFormValue::getVarValue)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("bp", "1"), org.assertj.core.groups.Tuple.tuple("count", "7"),
                            org.assertj.core.groups.Tuple.tuple("bp", "2"), org.assertj.core.groups.Tuple.tuple("count", "7"));
        }

        @Test
        @DisplayName("should refuse a changed value for a field the template cannot show")
        void shouldRefuse_whenUnsupportedStoredValueChanged() {
            EFormValue stored = new EFormValue();
            stored.setVarName("count");
            stored.setVarValue("7");
            when(eFormValueDao.findByFormDataId(5)).thenReturn(List.of(stored));
            EFormData previous = instance(5, true);
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(previous);

            Map<String, String> changed = Map.of("count", "8");
            assertThatThrownBy(() -> service.revise(loggedInInfo, 5, null, changed, CONTEXT))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.UNSUPPORTED_FIELD);
            assertThat(previous.isCurrent()).isTrue();
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should give an omitted field the template default, since values replace the old ones")
        void shouldUseTemplateDefault_whenFieldOmittedFromRevision() {
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(instance(5, true));

            EFormData revision = service.revise(loggedInInfo, 5, null, Map.of("bp", "130/85"), CONTEXT);

            assertThat(revision.getFormData()).contains("template default").contains("value=\"kept\"");
        }

        @Test
        @DisplayName("should use the caller's subject when one is given")
        void shouldUseCallerSubject_whenSubjectGiven() {
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(instance(5, true));

            EFormData revision = service.revise(loggedInInfo, 5, "Corrected", Map.of(), CONTEXT);

            assertThat(revision.getSubject()).isEqualTo("Corrected");
        }

        @Test
        @DisplayName("should still revise an instance whose template has since been retired, as the browser re-save does")
        void shouldSaveRevision_whenTemplateRetired() {
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(instance(5, true));
            when(eFormDao.findById(FID)).thenReturn(template(false, null));

            EFormData revision = service.revise(loggedInInfo, 5, null, Map.of("bp", "130/85"), CONTEXT);

            assertThat(revision.getId()).isEqualTo(SAVED_FDID);
        }

        @Test
        @DisplayName("should refuse without retiring the old row when its template has been removed")
        void shouldRefuseWithoutRetiring_whenTemplateRemoved() {
            EFormData previous = instance(5, true);
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(previous);
            when(eFormDao.findById(FID)).thenReturn(null);

            Map<String, String> empty = Map.of();
            assertThatThrownBy(() -> service.revise(loggedInInfo, 5, null, empty, CONTEXT))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.TEMPLATE_UNAVAILABLE);
            assertThat(previous.isCurrent()).isTrue();
            verify(eformDataManager, never()).saveEformData(any(), any());
            verify(eFormDataDao, never()).merge(any());
        }

        @Test
        @DisplayName("should refuse without saving when the instance is role restricted and the caller lacks the role object")
        void shouldRefuseWithoutSaving_whenInstanceRoleTypeDenied() {
            EFormData previous = instance(5, true);
            previous.setRoleType("doctor");
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(previous);
            when(securityInfoManager.hasPrivilege(any(), eq("_eform.doctor"), anyString(), nullable(String.class)))
                    .thenReturn(false);

            Map<String, String> empty = Map.of();
            assertThatThrownBy(() -> service.revise(loggedInInfo, 5, null, empty, CONTEXT))
                    .isInstanceOf(InstanceAccessDeniedException.class)
                    .hasMessage("missing required sec object (_eform.doctor)");
            assertThat(previous.isCurrent()).isTrue();
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should refuse before locking the row when a field name is blank")
        void shouldRefuseBeforeLocking_whenFieldNameBlank() {
            Map<String, String> blankName = Map.of("", "x");
            assertThatThrownBy(() -> service.revise(loggedInInfo, 5, null, blankName, CONTEXT))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.INVALID_FIELD_NAME);
            verify(eFormDataDao, never()).lockForAttachmentSync(anyInt());
        }

        @Test
        @DisplayName("should refuse before locking the row when the caller has no eForm write right")
        void shouldRefuseBeforeLocking_whenGlobalWriteDenied() {
            when(securityInfoManager.hasPrivilege(any(), eq("_eform"), eq(SecurityInfoManager.WRITE), nullable(String.class)))
                    .thenReturn(false);

            Map<String, String> empty = Map.of();
            assertThatThrownBy(() -> service.revise(loggedInInfo, 5, null, empty, CONTEXT))
                    .isInstanceOf(SecurityException.class);
            verify(eFormDataDao, never()).lockForAttachmentSync(anyInt());
        }

        @Test
        @DisplayName("should audit the revision against the patient, naming the replaced row")
        void shouldAuditRevision_withReplacedRow() {
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(instance(5, true));

            service.revise(loggedInInfo, 5, null, Map.of(), CONTEXT);

            logActionMock.verify(() -> LogAction.addLog(loggedInInfo, LogConst.UPDATE, EFormInstanceService.AUDIT_CONTENT,
                    String.valueOf(SAVED_FDID), String.valueOf(DEMOGRAPHIC_NO), "Revision of eform data 5"));
        }

        @Test
        @DisplayName("should refuse before locking the row when the body names no values, rather than reset the form")
        void shouldRefuseBeforeLocking_whenValuesMissing() {
            assertThatThrownBy(() -> service.revise(loggedInInfo, 5, "subject only", null, CONTEXT))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.VALUES_REQUIRED);
            verify(eFormDataDao, never()).lockForAttachmentSync(anyInt());
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should refuse a revision of a latest-only form that a newer form of the patient has replaced")
        void shouldRefuseAsSuperseded_whenNewerFormExistsForLatestOnlyTemplate() {
            EForm template = template(true, null);
            template.setShowLatestFormOnly(true);
            when(eFormDao.findById(FID)).thenReturn(template);
            EFormData previous = instance(5, true);
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(previous);
            // A clinician re-saved the form in the browser: a newer row, and the opened one still current.
            when(eFormDataDao.findByDemographicIdAndFormId(DEMOGRAPHIC_NO, FID))
                    .thenReturn(List.of(instance(9, true), previous));

            Map<String, String> values = Map.of("bp", "1");
            assertThatThrownBy(() -> service.revise(loggedInInfo, 5, null, values, CONTEXT))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.SUPERSEDED);
            assertThat(previous.isCurrent()).isTrue();
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should refuse a revision when the saved row itself is latest-only and a newer form exists")
        void shouldRefuseAsSuperseded_whenPreviousRowLatestOnlyHasNewerCurrentRow() {
            EFormData previous = instance(5, true);
            previous.setShowLatestFormOnly(true);
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(previous);
            when(eFormDataDao.findByDemographicIdAndFormId(DEMOGRAPHIC_NO, FID))
                    .thenReturn(List.of(instance(9, true), previous));

            Map<String, String> values = Map.of("bp", "1");
            assertThatThrownBy(() -> service.revise(loggedInInfo, 5, null, values, CONTEXT))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.SUPERSEDED);
            assertThat(previous.isCurrent()).isTrue();
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should revise a latest-only form when it is still the patient's newest")
        void shouldRevise_whenLatestOnlyFormIsNewest() {
            EForm template = template(true, null);
            template.setShowLatestFormOnly(true);
            when(eFormDao.findById(FID)).thenReturn(template);
            EFormData previous = instance(5, true);
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(previous);
            when(eFormDataDao.findByDemographicIdAndFormId(DEMOGRAPHIC_NO, FID))
                    .thenReturn(List.of(instance(3, true), previous));

            assertThat(service.revise(loggedInInfo, 5, null, Map.of("bp", "1"), CONTEXT).getId()).isEqualTo(SAVED_FDID);
        }

        @Test
        @DisplayName("should not look for newer forms of a template that lists every form")
        void shouldRevise_whenNewerFormExistsForOrdinaryTemplate() {
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(instance(5, true));

            service.revise(loggedInInfo, 5, null, Map.of("bp", "1"), CONTEXT);

            verify(eFormDataDao, never()).findByDemographicIdAndFormId(anyInt(), anyInt());
        }

        @Test
        @DisplayName("should give the revision the old row's attachments and the packets and eForms that attached it")
        void shouldMoveAttachments_toRevision() {
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(instance(5, true));
            io.github.carlos_emr.carlos.commn.model.EFormDocs ownLab =
                    new io.github.carlos_emr.carlos.commn.model.EFormDocs(5, 300, "L", "111");
            ownLab.setLabType("HL7");
            when(eFormDocsDao.findByFdid(5)).thenReturn(List.of(ownLab));
            io.github.carlos_emr.carlos.commn.model.ConsultDocs packet =
                    new io.github.carlos_emr.carlos.commn.model.ConsultDocs(40, 5, "E", "111");
            when(consultDocsDao.findActiveByAttachedDocumentForUpdate("E", 5)).thenReturn(List.of(packet));
            io.github.carlos_emr.carlos.commn.model.EFormDocs otherEForm =
                    new io.github.carlos_emr.carlos.commn.model.EFormDocs(88, 5, "E", "111");
            when(eFormDocsDao.findActiveByAttachedDocumentForUpdate("E", 5)).thenReturn(List.of(otherEForm));

            service.revise(loggedInInfo, 5, null, Map.of("bp", "1"), CONTEXT);

            ArgumentCaptor<io.github.carlos_emr.carlos.commn.model.EFormDocs> copies =
                    ArgumentCaptor.forClass(io.github.carlos_emr.carlos.commn.model.EFormDocs.class);
            verify(eFormDocsDao).persist(copies.capture());
            assertThat(copies.getValue().getFdid()).isEqualTo(SAVED_FDID);
            assertThat(copies.getValue().getDocumentNo()).isEqualTo(300);
            assertThat(copies.getValue().getDocType()).isEqualTo("L");
            assertThat(copies.getValue().getLabType()).isEqualTo("HL7");
            // The retired row keeps its own attachment, so its history still shows what it carried.
            assertThat(ownLab.getFdid()).isEqualTo(5);
            assertThat(packet.getDocumentNo()).isEqualTo(SAVED_FDID);
            verify(consultDocsDao).merge(packet);
            assertThat(otherEForm.getDocumentNo()).isEqualTo(SAVED_FDID);
            verify(eFormDocsDao).merge(otherEForm);
        }

        @Test
        @DisplayName("should refuse cleanly with attachment unavailable when EFormDocsDao rejects an unrouted lab copy")
        void shouldRefuseWithAttachmentUnavailable_whenLabLinkRejectedByDao() {
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(instance(5, true));
            io.github.carlos_emr.carlos.commn.model.EFormDocs unroutedLab =
                    new io.github.carlos_emr.carlos.commn.model.EFormDocs(5, 301, "L", "111");
            unroutedLab.setLabType("MDS");
            when(eFormDocsDao.findByFdid(5)).thenReturn(List.of(unroutedLab));
            doThrow(new IllegalArgumentException("Lab attachment does not belong to this patient"))
                    .when(eFormDocsDao).persist(any(io.github.carlos_emr.carlos.commn.model.EFormDocs.class));

            Map<String, String> values = Map.of("bp", "1");
            assertThatThrownBy(() -> service.revise(loggedInInfo, 5, null, values, CONTEXT))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.ATTACHMENT_UNAVAILABLE);
        }

        @Test
        @DisplayName("should refuse as an instance-level denial, not a 409, when a retired row's patient is closed to the caller")
        void shouldRefuseAsInstanceDenial_whenRetiredRowDenied() {
            EFormData previous = instance(5, false);
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(previous);
            denyPatientScoped(SecurityInfoManager.WRITE);

            Map<String, String> empty = Map.of();
            assertThatThrownBy(() -> service.revise(loggedInInfo, 5, null, empty, CONTEXT))
                    .isInstanceOf(InstanceAccessDeniedException.class);
            verify(eFormDataDao, never()).merge(any());
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should refuse plainly, not as not-found, when the template but not the row is role restricted")
        void shouldRefuseAsAccessDenied_whenOnlyTemplateRoleTypeDenied() {
            when(eFormDao.findById(FID)).thenReturn(template(true, "doctor"));
            EFormData previous = instance(5, true);
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(previous);
            when(securityInfoManager.hasPrivilege(any(), eq("_eform.doctor"), anyString(), nullable(String.class)))
                    .thenReturn(false);

            // The row passed its own checks, so the caller can GET and DELETE it: hiding it as
            // not found on PUT alone would be inconsistent.
            Map<String, String> empty = Map.of();
            assertThatThrownBy(() -> service.revise(loggedInInfo, 5, null, empty, CONTEXT))
                    .isInstanceOf(SecurityException.class)
                    .isNotInstanceOf(InstanceAccessDeniedException.class)
                    .hasMessage("missing required sec object (_eform.doctor)");
            assertThat(previous.isCurrent()).isTrue();
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should keep every choice of a multiple select sent back unchanged, and replace them when it changes")
        void shouldKeepMultipleSelections_whenValueSentBackUnchanged() {
            String templateHtml = "<html><body><form><select name=\"sites\" multiple>"
                    + "<option value=\"a\">A</option><option value=\"b\">B</option><option value=\"c\">C</option>"
                    + "</select></form></body></html>";
            EForm template = template(true, null);
            template.setFormHtml(templateHtml);
            when(eFormDao.findById(FID)).thenReturn(template);
            when(eFormDataDao.lockForAttachmentSync(5)).thenAnswer(call -> {
                EFormData data = instance(5, true);
                data.setFormData(templateHtml.replace("\"a\">", "\"a\" selected>").replace("\"c\">", "\"c\" selected>"));
                return data;
            });
            EFormValue stored = new EFormValue();
            stored.setVarName("sites");
            stored.setVarValue("a"); // the browser save stores the first choice only
            when(eFormValueDao.findByFormDataId(5)).thenReturn(List.of(stored));

            EFormData unchanged = service.revise(loggedInInfo, 5, null, Map.of("sites", "a"), CONTEXT);
            assertThat(Jsoup.parse(unchanged.getFormData()).select("select[name=sites] option[selected]"))
                    .extracting(option -> option.attr("value")).containsExactly("a", "c");

            EFormData changed = service.revise(loggedInInfo, 5, null, Map.of("sites", "b"), CONTEXT);
            assertThat(Jsoup.parse(changed.getFormData()).select("select[name=sites] option[selected]"))
                    .extracting(option -> option.attr("value")).containsExactly("b");
        }

        @Test
        @DisplayName("should keep remaining multiple select choices when the first stored choice was removed from the template")
        void shouldKeepRemainingMultipleChoices_whenFirstChoiceRemovedFromTemplate() {
            String oldHtml = "<html><body><form><select name=\"sites\" multiple>"
                    + "<option value=\"a\" selected>A</option><option value=\"b\">B</option><option value=\"c\" selected>C</option>"
                    + "</select></form></body></html>";
            String currentTemplateHtml = "<html><body><form><select name=\"sites\" multiple>"
                    + "<option value=\"b\">B</option><option value=\"c\">C</option>"
                    + "</select></form></body></html>";
            EForm template = template(true, null);
            template.setFormHtml(currentTemplateHtml);
            when(eFormDao.findById(FID)).thenReturn(template);
            when(eFormDataDao.lockForAttachmentSync(5)).thenAnswer(call -> {
                EFormData data = instance(5, true);
                data.setFormData(oldHtml);
                return data;
            });
            EFormValue stored = new EFormValue();
            stored.setVarName("sites");
            stored.setVarValue("a");
            when(eFormValueDao.findByFormDataId(5)).thenReturn(List.of(stored));

            EFormData revision = service.revise(loggedInInfo, 5, null, Map.of("sites", "a"), CONTEXT);

            assertThat(Jsoup.parse(revision.getFormData()).select("select[name=sites] option[selected]"))
                    .extracting(option -> option.attr("value")).containsExactly("c");
            assertThat(persistedValues()).extracting(EFormValue::getVarName, EFormValue::getVarValue)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("sites", "a"));
        }

        @Test
        @DisplayName("should uncheck every radio, as a browser re-save does, when a carried value is no longer a choice")
        void shouldUncheckEveryRadio_whenCarriedValueIsNoLongerAChoice() {
            // The template was edited from Y/N to Yes/No (No checked) after the row stored allergy=Y.
            String templateHtml = "<html><body><form>"
                    + "<input type=\"radio\" name=\"allergy\" value=\"Yes\">"
                    + "<input type=\"radio\" name=\"allergy\" value=\"No\" checked>"
                    + "</form></body></html>";
            EForm template = template(true, null);
            template.setFormHtml(templateHtml);
            when(eFormDao.findById(FID)).thenReturn(template);
            when(eFormDataDao.lockForAttachmentSync(5)).thenAnswer(call -> instance(5, true));
            EFormValue stored = new EFormValue();
            stored.setVarName("allergy");
            stored.setVarValue("Y");
            when(eFormValueDao.findByFormDataId(5)).thenReturn(List.of(stored));

            EFormData revision = service.revise(loggedInInfo, 5, null, Map.of("allergy", "Y"), CONTEXT);

            assertThat(Jsoup.parse(revision.getFormData()).select("input[name=allergy][checked]")).isEmpty();
            assertThat(persistedValues()).extracting(EFormValue::getVarName, EFormValue::getVarValue)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("allergy", "Y"));
        }

        @Test
        @DisplayName("should check the box a browser save checked when a carried checkbox group value is sent back")
        void shouldCheckBox_whenCarriedCheckboxGroupValueSentBack() {
            String templateHtml = "<html><body><form>"
                    + "<input type=\"checkbox\" name=\"symptoms\" value=\"cough\">"
                    + "<input type=\"checkbox\" name=\"symptoms\" value=\"fever\">"
                    + "</form></body></html>";
            EForm template = template(true, null);
            template.setFormHtml(templateHtml);
            when(eFormDao.findById(FID)).thenReturn(template);
            when(eFormDataDao.lockForAttachmentSync(5)).thenAnswer(call -> instance(5, true));
            EFormValue stored = new EFormValue();
            stored.setVarName("symptoms");
            stored.setVarValue("fever");
            when(eFormValueDao.findByFormDataId(5)).thenReturn(List.of(stored));

            // fever is the group's second box; setValues would check only the first (cough).
            EFormData revision = service.revise(loggedInInfo, 5, null, Map.of("symptoms", "fever"), CONTEXT);

            assertThat(Jsoup.parse(revision.getFormData()).select("input[name=symptoms][checked]"))
                    .extracting(box -> box.attr("value")).containsExactly("fever");
            assertThat(persistedValues()).extracting(EFormValue::getVarName, EFormValue::getVarValue)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("symptoms", "fever"));
        }

        @Test
        @DisplayName("should carry a left-out stored value that is no longer one of its control's choices")
        void shouldCarryOmittedValue_whenNoLongerAChoice() {
            // The template was edited from Y/N to Yes/No (No checked) after the row stored allergy=Y.
            String templateHtml = "<html><body><form>"
                    + "<input type=\"radio\" name=\"allergy\" value=\"Yes\">"
                    + "<input type=\"radio\" name=\"allergy\" value=\"No\" checked>"
                    + "<input type=\"text\" name=\"bp\" value=\"\">"
                    + "</form></body></html>";
            EForm template = template(true, null);
            template.setFormHtml(templateHtml);
            when(eFormDao.findById(FID)).thenReturn(template);
            when(eFormDataDao.lockForAttachmentSync(5)).thenAnswer(call -> instance(5, true));
            EFormValue stored = new EFormValue();
            stored.setVarName("allergy");
            stored.setVarValue("Y");
            when(eFormValueDao.findByFormDataId(5)).thenReturn(List.of(stored));

            EFormData revision = service.revise(loggedInInfo, 5, null, Map.of("bp", "120/80"), CONTEXT);

            // Shown as a browser re-save shows it (no choice checked), and its value row kept.
            assertThat(Jsoup.parse(revision.getFormData()).select("input[name=allergy][checked]")).isEmpty();
            assertThat(persistedValues()).extracting(EFormValue::getVarName, EFormValue::getVarValue)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("bp", "120/80"),
                            org.assertj.core.groups.Tuple.tuple("allergy", "Y"));
        }

        @Test
        @DisplayName("should clear only the checkbox named exactly as sent, not a differently cased one")
        void shouldClearOnlyExactName_whenCheckboxNamesDifferInCase() {
            String templateHtml = "<html><body><form>"
                    + "<input type=\"checkbox\" name=\"flag\" checked>"
                    + "<input type=\"checkbox\" name=\"Flag\" checked>"
                    + "</form></body></html>";
            EForm template = template(true, null);
            template.setFormHtml(templateHtml);
            when(eFormDao.findById(FID)).thenReturn(template);
            when(eFormDataDao.lockForAttachmentSync(5)).thenAnswer(call -> instance(5, true));

            EFormData revision = service.revise(loggedInInfo, 5, null, Map.of("flag", ""), CONTEXT);

            var html = Jsoup.parse(revision.getFormData());
            assertThat(html.select("input[checked]")).extracting(box -> box.attr("name")).containsExactly("Flag");
        }

    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("should retire the row with write access alone, as the browser delete does")
        void shouldRetireRow_withWriteAccessAlone() {
            // Deny by default, then grant _eform w (and the r and u it implies), globally and for
            // the patient. _eform d stays denied, so any extra requirement fails this test.
            when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), nullable(String.class)))
                    .thenReturn(false);
            for (String implied : List.of(SecurityInfoManager.WRITE, SecurityInfoManager.UPDATE, SecurityInfoManager.READ)) {
                when(securityInfoManager.hasPrivilege(any(), eq("_eform"), eq(implied), isNull())).thenReturn(true);
                when(securityInfoManager.hasPrivilege(any(), eq("_eform"), eq(implied), eq(String.valueOf(DEMOGRAPHIC_NO))))
                        .thenReturn(true);
            }
            EFormData data = instance(5, true);
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(data);

            service.delete(loggedInInfo, 5);

            assertThat(data.isCurrent()).isFalse();
            verify(eFormDataDao).merge(data);
        }

        @Test
        @DisplayName("should refuse when the instance was already retired")
        void shouldRefuse_whenAlreadyRetired() {
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(instance(5, false));

            assertThatThrownBy(() -> service.delete(loggedInInfo, 5))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.RETIRED);
            verify(eFormDataDao, never()).merge(any());
        }

        @Test
        @DisplayName("should refuse when the caller may not write this patient's eForms")
        void shouldRefuse_whenPatientScopedWriteDenied() {
            EFormData data = instance(5, true);
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(data);
            denyPatientScoped(SecurityInfoManager.WRITE);

            assertThatThrownBy(() -> service.delete(loggedInInfo, 5)).isInstanceOf(InstanceAccessDeniedException.class);
            assertThat(data.isCurrent()).isTrue();
            verify(eFormDataDao, never()).merge(any());
        }

        @Test
        @DisplayName("should report not found when no instance has the id")
        void shouldReportNotFound_whenInstanceMissing() {
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(null);

            assertThatThrownBy(() -> service.delete(loggedInInfo, 5))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.NOT_FOUND);
        }

        @Test
        @DisplayName("should refuse when the instance is role restricted and the caller lacks the role object")
        void shouldRefuse_whenInstanceRoleTypeDenied() {
            EFormData data = instance(5, true);
            data.setRoleType("doctor");
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(data);
            when(securityInfoManager.hasPrivilege(any(), eq("_eform.doctor"), anyString(), nullable(String.class)))
                    .thenReturn(false);

            assertThatThrownBy(() -> service.delete(loggedInInfo, 5)).isInstanceOf(InstanceAccessDeniedException.class);
            assertThat(data.isCurrent()).isTrue();
            verify(eFormDataDao, never()).merge(any());
        }

        @Test
        @DisplayName("should refuse before locking the row when the caller has no eForm write right")
        void shouldRefuseBeforeLocking_whenGlobalWriteDenied() {
            when(securityInfoManager.hasPrivilege(any(), eq("_eform"), eq(SecurityInfoManager.WRITE), nullable(String.class)))
                    .thenReturn(false);

            assertThatThrownBy(() -> service.delete(loggedInInfo, 5)).isInstanceOf(SecurityException.class);
            verify(eFormDataDao, never()).lockForAttachmentSync(anyInt());
        }

        @Test
        @DisplayName("should audit the deletion against the patient")
        void shouldAuditDeletion_againstPatient() {
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(instance(5, true));

            service.delete(loggedInInfo, 5);

            logActionMock.verify(() -> LogAction.addLog(loggedInInfo, LogConst.DELETE, EFormInstanceService.AUDIT_CONTENT,
                    "5", String.valueOf(DEMOGRAPHIC_NO), null));
        }

        @Test
        @DisplayName("should refuse as an instance-level denial when the patient record is restricted")
        void shouldRefuseAsInstanceDenial_whenPatientRecordRestricted() {
            EFormData data = instance(5, true);
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(data);
            when(securityInfoManager.isAllowedAccessToPatientRecord(any(), eq(DEMOGRAPHIC_NO))).thenReturn(false);

            assertThatThrownBy(() -> service.delete(loggedInInfo, 5)).isInstanceOf(InstanceAccessDeniedException.class);
            assertThat(data.isCurrent()).isTrue();
            verify(eFormDataDao, never()).merge(any());
        }

        @Test
        @DisplayName("should refuse as an instance-level denial, not a 409, when a retired row's patient is closed to the caller")
        void shouldRefuseAsInstanceDenial_whenRetiredRowDenied() {
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(instance(5, false));
            denyPatientScoped(SecurityInfoManager.WRITE);

            assertThatThrownBy(() -> service.delete(loggedInInfo, 5)).isInstanceOf(InstanceAccessDeniedException.class);
            verify(eFormDataDao, never()).merge(any());
        }
    }

    @Nested
    @DisplayName("get")
    class Get {

        @Test
        @DisplayName("should return the row and its stored values when the caller may read them")
        void shouldReturnRowAndValues_whenReadAllowed() {
            EFormData data = instance(5, false);
            when(eFormDataDao.find(5)).thenReturn(data);
            EFormValue value = new EFormValue();
            value.setVarName("bp");
            value.setVarValue("120/80");
            when(eFormValueDao.findByFormDataId(5)).thenReturn(List.of(value));

            EFormInstanceService.Instance instance = service.get(loggedInInfo, 5);

            assertThat(instance.data()).isSameAs(data);
            assertThat(instance.values()).containsExactly(Map.entry("bp", "120/80"));
        }

        @Test
        @DisplayName("should report a field cleared from its template default as an empty value")
        void shouldReportClearedDefault_asEmptyValue() {
            EFormData data = instance(5, true);
            data.setFormData(TEMPLATE_HTML
                    .replace("template default", "")
                    .replace(" checked>", ">"));
            when(eFormDataDao.find(5)).thenReturn(data);
            when(eFormValueDao.findByFormDataId(5)).thenReturn(List.of());

            EFormInstanceService.Instance instance = service.get(loggedInInfo, 5);

            // note and smoker were cleared; untouched still shows its default; bp has no default.
            assertThat(instance.values()).containsExactly(Map.entry("note", ""), Map.entry("smoker", ""));
        }

        @Test
        @DisplayName("should keep a select cleared to its empty placeholder when a read is sent back as a revision")
        void shouldKeepSelectPlaceholder_whenReadIsSentBackAsRevision() {
            String templateHtml = "<html><body><form><select name=\"dose\">"
                    + "<option value=\"\">Choose</option><option value=\"5\" selected>5 mg</option>"
                    + "</select></form></body></html>";
            EForm template = template(true, null);
            template.setFormHtml(templateHtml);
            when(eFormDao.findById(FID)).thenReturn(template);
            EFormData data = instance(5, true);
            data.setFormData(templateHtml
                    .replace("<option value=\"\">", "<option value=\"\" selected>")
                    .replace(" selected>5 mg", ">5 mg"));
            when(eFormDataDao.find(5)).thenReturn(data);
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(data);
            when(eFormValueDao.findByFormDataId(5)).thenReturn(List.of());

            Map<String, String> read = service.get(loggedInInfo, 5).values();
            EFormData revision = service.revise(loggedInInfo, 5, null, read, CONTEXT);

            assertThat(read).containsExactly(Map.entry("dose", ""));
            Document revised = Jsoup.parse(revision.getFormData());
            assertThat(revised.select("select[name=dose] option[selected]"))
                    .extracting(option -> option.attr("value"))
                    .containsExactly("");
            assertThat(persistedValues()).isEmpty();
        }

        @Test
        @DisplayName("should refuse as an instance-level denial when the caller may not read this patient's eForms")
        void shouldRefuseAsInstanceDenial_whenPatientScopedReadDenied() {
            when(eFormDataDao.find(5)).thenReturn(instance(5, true));
            denyPatientScoped(SecurityInfoManager.READ);

            assertThatThrownBy(() -> service.get(loggedInInfo, 5))
                    .isInstanceOf(EFormInstanceService.InstanceAccessDeniedException.class);
        }

        @Test
        @DisplayName("should refuse without reading values when the caller may not read this patient's eForms")
        void shouldRefuseWithoutReadingValues_whenPatientScopedReadDenied() {
            when(eFormDataDao.find(5)).thenReturn(instance(5, true));
            denyPatientScoped(SecurityInfoManager.READ);

            assertThatThrownBy(() -> service.get(loggedInInfo, 5)).isInstanceOf(InstanceAccessDeniedException.class);
            verify(eFormValueDao, never()).findByFormDataId(5);
        }

        @Test
        @DisplayName("should refuse before any lookup when the caller has no eForm read right")
        void shouldRefuseBeforeLookup_whenGlobalReadDenied() {
            when(securityInfoManager.hasPrivilege(any(), eq("_eform"), eq(SecurityInfoManager.READ), nullable(String.class)))
                    .thenReturn(false);

            assertThatThrownBy(() -> service.get(loggedInInfo, 5)).isInstanceOf(SecurityException.class);
            verify(eFormDataDao, never()).find(any());
        }

        @Test
        @DisplayName("should report not found when no instance has the id")
        void shouldReportNotFound_whenInstanceMissing() {
            when(eFormDataDao.find(5)).thenReturn(null);

            assertThatThrownBy(() -> service.get(loggedInInfo, 5))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.NOT_FOUND);
            verify(eFormValueDao, never()).findByFormDataId(anyInt());
        }

        @Test
        @DisplayName("should refuse without reading values when the instance is role restricted and the caller lacks the role object")
        void shouldRefuseWithoutReadingValues_whenInstanceRoleTypeDenied() {
            EFormData data = instance(5, true);
            data.setRoleType("doctor");
            when(eFormDataDao.find(5)).thenReturn(data);
            when(securityInfoManager.hasPrivilege(any(), eq("_eform.doctor"), anyString(), nullable(String.class)))
                    .thenReturn(false);

            assertThatThrownBy(() -> service.get(loggedInInfo, 5)).isInstanceOf(InstanceAccessDeniedException.class);
            verify(eFormValueDao, never()).findByFormDataId(anyInt());
        }

        @Test
        @DisplayName("should audit the read against the patient")
        void shouldAuditRead_againstPatient() {
            when(eFormDataDao.find(5)).thenReturn(instance(5, true));

            service.get(loggedInInfo, 5);

            // In its own transaction: the read's transaction is read-only (see EFormInstanceService.get).
            logActionMock.verify(() -> LogAction.addLogSynchronousInNewTransaction(loggedInInfo, LogConst.READ,
                    EFormInstanceService.AUDIT_CONTENT, "5", String.valueOf(DEMOGRAPHIC_NO), null));
            logActionMock.verify(() -> LogAction.addLog(any(LoggedInInfo.class), eq(LogConst.READ), anyString(),
                    anyString(), anyString(), any()), never());
        }

        @Test
        @DisplayName("should refuse as an instance-level denial without reading values when the patient record is restricted")
        void shouldRefuseAsInstanceDenial_whenPatientRecordRestricted() {
            when(eFormDataDao.find(5)).thenReturn(instance(5, true));
            when(securityInfoManager.isAllowedAccessToPatientRecord(any(), eq(DEMOGRAPHIC_NO))).thenReturn(false);

            assertThatThrownBy(() -> service.get(loggedInInfo, 5)).isInstanceOf(InstanceAccessDeniedException.class);
            verify(eFormValueDao, never()).findByFormDataId(anyInt());
        }

        @Test
        @DisplayName("should keep a checked blank-valued radio choice when a read is sent back as a revision")
        void shouldKeepBlankRadioChoice_whenReadIsSentBackAsRevision() {
            String templateHtml = "<html><body><form>"
                    + "<input type=\"radio\" name=\"side\" value=\"\">"
                    + "<input type=\"radio\" name=\"side\" value=\"L\">"
                    + "<input type=\"radio\" name=\"side\" value=\"R\" checked>"
                    + "</form></body></html>";
            EForm template = template(true, null);
            template.setFormHtml(templateHtml);
            when(eFormDao.findById(FID)).thenReturn(template);
            EFormData data = instance(5, true);
            data.setFormData(templateHtml.replace("value=\"\">", "value=\"\" checked>").replace(" checked></form>", "></form>"));
            when(eFormDataDao.find(5)).thenReturn(data);
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(data);
            when(eFormValueDao.findByFormDataId(5)).thenReturn(List.of());

            Map<String, String> read = service.get(loggedInInfo, 5).values();
            EFormData revision = service.revise(loggedInInfo, 5, null, read, CONTEXT);

            assertThat(read).containsExactly(Map.entry("side", ""));
            assertThat(Jsoup.parse(revision.getFormData()).select("input[name=side][checked]"))
                    .extracting(radio -> radio.attr("value")).containsExactly("");
        }

        @Test
        @DisplayName("should keep an implicit first-option single select cleared to empty when read is revised")
        void shouldKeepImplicitSelectCleared_whenReadIsSentBackAsRevision() {
            String templateHtml = "<html><body><form><select name=\"status\">"
                    + "<option>Normal</option><option value=\"\">Not assessed</option>"
                    + "</select></form></body></html>";
            EForm template = template(true, null);
            template.setFormHtml(templateHtml);
            when(eFormDao.findById(FID)).thenReturn(template);
            EFormData data = instance(5, true);
            data.setFormData(templateHtml.replace("value=\"\">", "value=\"\" selected>"));
            when(eFormDataDao.find(5)).thenReturn(data);
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(data);
            when(eFormValueDao.findByFormDataId(5)).thenReturn(List.of());

            Map<String, String> read = service.get(loggedInInfo, 5).values();
            EFormData revision = service.revise(loggedInInfo, 5, null, read, CONTEXT);

            assertThat(read).containsExactly(Map.entry("status", ""));
            assertThat(Jsoup.parse(revision.getFormData()).select("select[name=status] option[selected]"))
                    .extracting(option -> option.attr("value"))
                    .containsExactly("");
            assertThat(persistedValues()).isEmpty();
        }

        @Test
        @DisplayName("should skip disabled leading options and disabled optgroups when checking implicit select default")
        void shouldSkipDisabledLeadingOptions_whenDetectingClearedImplicitSelect() {
            String templateHtml = "<html><body><form><select name=\"status\">"
                    + "<option value=\"\" disabled>Choose</option>"
                    + "<optgroup label=\"Old\" disabled><option value=\"\">Legacy</option></optgroup>"
                    + "<option value=\"Normal\">Normal</option>"
                    + "<option value=\"\">Not assessed</option>"
                    + "</select></form></body></html>";
            EForm template = template(true, null);
            template.setFormHtml(templateHtml);
            when(eFormDao.findById(FID)).thenReturn(template);
            EFormData data = instance(5, true);
            data.setFormData(templateHtml.replace("<option value=\"\">Not assessed", "<option value=\"\" selected>Not assessed"));
            when(eFormDataDao.find(5)).thenReturn(data);
            when(eFormValueDao.findByFormDataId(5)).thenReturn(List.of());

            assertThat(service.get(loggedInInfo, 5).values()).containsExactly(Map.entry("status", ""));
        }

        @Test
        @DisplayName("should not report an untouched single select as cleared when its implicit leading option is empty")
        void shouldOmitEmptySelect_whenLeadingOptionIsAlreadyEmpty() {
            String templateHtml = "<html><body><form><select name=\"status\">"
                    + "<option value=\"\">Choose</option><option value=\"Normal\">Normal</option>"
                    + "</select></form></body></html>";
            EForm template = template(true, null);
            template.setFormHtml(templateHtml);
            when(eFormDao.findById(FID)).thenReturn(template);
            EFormData data = instance(5, true);
            data.setFormData(templateHtml);
            when(eFormDataDao.find(5)).thenReturn(data);
            when(eFormValueDao.findByFormDataId(5)).thenReturn(List.of());

            assertThat(service.get(loggedInInfo, 5).values()).isEmpty();
        }

        @Test
        @DisplayName("should keep a cleared multiple select empty when a read is sent back as a revision")
        void shouldKeepClearedMultipleSelect_whenReadIsSentBackAsRevision() {
            String templateHtml = "<html><body><form><select name=\"sites\" multiple>"
                    + "<option value=\"a\" selected>A</option><option value=\"b\">B</option>"
                    + "</select></form></body></html>";
            EForm template = template(true, null);
            template.setFormHtml(templateHtml);
            when(eFormDao.findById(FID)).thenReturn(template);
            EFormData data = instance(5, true);
            data.setFormData(templateHtml.replace(" selected", ""));
            when(eFormDataDao.find(5)).thenReturn(data);
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(data);
            when(eFormValueDao.findByFormDataId(5)).thenReturn(List.of());

            Map<String, String> read = service.get(loggedInInfo, 5).values();
            EFormData revision = service.revise(loggedInInfo, 5, null, read, CONTEXT);

            assertThat(read).containsExactly(Map.entry("sites", ""));
            assertThat(Jsoup.parse(revision.getFormData()).select("select[name=sites] option[selected]")).isEmpty();
            assertThat(persistedValues()).isEmpty();
        }
    }
}
