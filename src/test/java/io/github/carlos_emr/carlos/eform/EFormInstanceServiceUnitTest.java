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
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.EFormValueDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.EFormValue;
import io.github.carlos_emr.carlos.eform.EFormInstanceService.EFormInstanceException;
import io.github.carlos_emr.carlos.eform.EFormInstanceService.EFormInstanceException.Reason;
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
    private static final String TEMPLATE_HTML = "<html><body><form>"
            + "<input type=\"text\" name=\"bp\" value=\"\">"
            + "<textarea name=\"note\">template default</textarea>"
            + "<input type=\"text\" name=\"untouched\" value=\"kept\">"
            + "</form></body></html>";

    private SecurityInfoManager securityInfoManager;
    private EformDataManager eformDataManager;
    private EFormDao eFormDao;
    private EFormDataDao eFormDataDao;
    private EFormValueDao eFormValueDao;
    private DemographicDao demographicDao;
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
        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), nullable(String.class)))
                .thenReturn(true);
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
                eFormValueDao, demographicDao);
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

            EFormData saved = service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, values);

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

            EFormData saved = service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, "s", values);

            assertThat(saved.getFormData()).doesNotContain("<script>alert(1)</script>");
            assertThat(saved.getFormData()).contains("&lt;script&gt;");
        }

        @Test
        @DisplayName("should refuse when the template is retired")
        void shouldRefuse_whenTemplateRetired() {
            when(eFormDao.findById(FID)).thenReturn(template(false, null));

            assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, Map.of()))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.TEMPLATE_UNAVAILABLE);
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should refuse when the patient does not exist")
        void shouldRefuse_whenPatientMissing() {
            when(demographicDao.getDemographicById(DEMOGRAPHIC_NO)).thenReturn(null);

            assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, Map.of()))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.PATIENT_NOT_FOUND);
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should refuse when the caller may not write eForms for this patient")
        void shouldRefuse_whenPatientScopedWriteDenied() {
            denyPatientScoped(SecurityInfoManager.WRITE);

            assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, Map.of()))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_eform)");
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should refuse when the template is role restricted and the caller lacks the role object")
        void shouldRefuse_whenRoleTypeDenied() {
            when(eFormDao.findById(FID)).thenReturn(template(true, "doctor"));
            when(securityInfoManager.hasPrivilege(any(), eq("_eform.doctor"), anyString(), nullable(String.class)))
                    .thenReturn(false);

            assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, Map.of()))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_eform.doctor)");
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should refuse when a field name is blank")
        void shouldRefuse_whenFieldNameBlank() {
            assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, Map.of(" ", "x")))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.INVALID_FIELD_NAME);
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should use the caller's subject when one is given")
        void shouldUseCallerSubject_whenSubjectGiven() {
            EFormData saved = service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, "Caller subject", Map.of());

            assertThat(saved.getSubject()).isEqualTo("Caller subject");
        }

        @Test
        @DisplayName("should save the template defaults and no value rows when no values are given")
        void shouldSaveTemplateDefaultsWithoutValueRows_whenValuesNull() {
            EFormData saved = service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, null);

            assertThat(saved.getFormData()).contains("template default").contains("value=\"kept\"");
            verify(eFormValueDao, never()).persist(any());
        }

        @Test
        @DisplayName("should store no value row for a whitespace-only value, as the browser save does")
        void shouldStoreNoValueRow_whenValueIsWhitespace() {
            service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, Map.of("bp", "   "));

            verify(eFormValueDao, never()).persist(any());
        }

        @Test
        @DisplayName("should refuse when the template does not exist")
        void shouldRefuse_whenTemplateMissing() {
            when(eFormDao.findById(FID)).thenReturn(null);

            assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, Map.of()))
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

            assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, Map.of()))
                    .isInstanceOf(SecurityException.class);
            verifyNoInteractions(eFormDao, demographicDao, eformDataManager);
        }

        @Test
        @DisplayName("should fail rather than return nothing when the saved row cannot be read back")
        void shouldFail_whenSavedRowCannotBeReadBack() {
            when(eFormDataDao.find(SAVED_FDID)).thenReturn(null);

            assertThatThrownBy(() -> service.create(loggedInInfo, FID, DEMOGRAPHIC_NO, null, Map.of()))
                    .isInstanceOf(IllegalStateException.class);
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

            EFormData revision = service.revise(loggedInInfo, 5, null, Map.of("bp", "130/85"));

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

            assertThatThrownBy(() -> service.revise(loggedInInfo, 5, null, Map.of("bp", "1")))
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

            assertThatThrownBy(() -> service.revise(loggedInInfo, 5, null, Map.of()))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.NOT_FOUND);
        }

        @Test
        @DisplayName("should refuse without saving when the caller may not update this patient's eForms")
        void shouldRefuseWithoutSaving_whenPatientScopedUpdateDenied() {
            EFormData previous = instance(5, true);
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(previous);
            denyPatientScoped(SecurityInfoManager.UPDATE);

            assertThatThrownBy(() -> service.revise(loggedInInfo, 5, null, Map.of()))
                    .isInstanceOf(SecurityException.class);
            assertThat(previous.isCurrent()).isTrue();
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should give an omitted field the template default, since values replace the old ones")
        void shouldUseTemplateDefault_whenFieldOmittedFromRevision() {
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(instance(5, true));

            EFormData revision = service.revise(loggedInInfo, 5, null, Map.of("bp", "130/85"));

            assertThat(revision.getFormData()).contains("template default").contains("value=\"kept\"");
        }

        @Test
        @DisplayName("should use the caller's subject when one is given")
        void shouldUseCallerSubject_whenSubjectGiven() {
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(instance(5, true));

            EFormData revision = service.revise(loggedInInfo, 5, "Corrected", Map.of());

            assertThat(revision.getSubject()).isEqualTo("Corrected");
        }

        @Test
        @DisplayName("should still revise an instance whose template has since been retired, as the browser re-save does")
        void shouldSaveRevision_whenTemplateRetired() {
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(instance(5, true));
            when(eFormDao.findById(FID)).thenReturn(template(false, null));

            EFormData revision = service.revise(loggedInInfo, 5, null, Map.of("bp", "130/85"));

            assertThat(revision.getId()).isEqualTo(SAVED_FDID);
        }

        @Test
        @DisplayName("should refuse without retiring the old row when its template has been removed")
        void shouldRefuseWithoutRetiring_whenTemplateRemoved() {
            EFormData previous = instance(5, true);
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(previous);
            when(eFormDao.findById(FID)).thenReturn(null);

            assertThatThrownBy(() -> service.revise(loggedInInfo, 5, null, Map.of()))
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

            assertThatThrownBy(() -> service.revise(loggedInInfo, 5, null, Map.of()))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_eform.doctor)");
            assertThat(previous.isCurrent()).isTrue();
            verify(eformDataManager, never()).saveEformData(any(), any());
        }

        @Test
        @DisplayName("should refuse before locking the row when a field name is blank")
        void shouldRefuseBeforeLocking_whenFieldNameBlank() {
            assertThatThrownBy(() -> service.revise(loggedInInfo, 5, null, Map.of("", "x")))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.INVALID_FIELD_NAME);
            verify(eFormDataDao, never()).lockForAttachmentSync(anyInt());
        }

        @Test
        @DisplayName("should refuse before locking the row when the caller has no eForm update right")
        void shouldRefuseBeforeLocking_whenGlobalUpdateDenied() {
            when(securityInfoManager.hasPrivilege(any(), eq("_eform"), eq(SecurityInfoManager.UPDATE), nullable(String.class)))
                    .thenReturn(false);

            assertThatThrownBy(() -> service.revise(loggedInInfo, 5, null, Map.of()))
                    .isInstanceOf(SecurityException.class);
            verify(eFormDataDao, never()).lockForAttachmentSync(anyInt());
        }

        @Test
        @DisplayName("should audit the revision against the patient, naming the replaced row")
        void shouldAuditRevision_withReplacedRow() {
            when(eFormDataDao.lockForAttachmentSync(5)).thenReturn(instance(5, true));

            service.revise(loggedInInfo, 5, null, Map.of());

            logActionMock.verify(() -> LogAction.addLog(loggedInInfo, LogConst.UPDATE, EFormInstanceService.AUDIT_CONTENT,
                    String.valueOf(SAVED_FDID), String.valueOf(DEMOGRAPHIC_NO), "Revision of eform data 5"));
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("should retire the row with write access alone, as the browser delete does")
        void shouldRetireRow_withWriteAccessAlone() {
            when(securityInfoManager.hasPrivilege(any(), eq("_eform"), eq(SecurityInfoManager.DELETE), nullable(String.class)))
                    .thenReturn(false);
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

            assertThatThrownBy(() -> service.delete(loggedInInfo, 5)).isInstanceOf(SecurityException.class);
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

            assertThatThrownBy(() -> service.delete(loggedInInfo, 5)).isInstanceOf(SecurityException.class);
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
        @DisplayName("should refuse without reading values when the caller may not read this patient's eForms")
        void shouldRefuseWithoutReadingValues_whenPatientScopedReadDenied() {
            when(eFormDataDao.find(5)).thenReturn(instance(5, true));
            denyPatientScoped(SecurityInfoManager.READ);

            assertThatThrownBy(() -> service.get(loggedInInfo, 5)).isInstanceOf(SecurityException.class);
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

            assertThatThrownBy(() -> service.get(loggedInInfo, 5)).isInstanceOf(SecurityException.class);
            verify(eFormValueDao, never()).findByFormDataId(anyInt());
        }

        @Test
        @DisplayName("should audit the read against the patient")
        void shouldAuditRead_againstPatient() {
            when(eFormDataDao.find(5)).thenReturn(instance(5, true));

            service.get(loggedInInfo, 5);

            logActionMock.verify(() -> LogAction.addLog(loggedInInfo, LogConst.READ, EFormInstanceService.AUDIT_CONTENT,
                    "5", String.valueOf(DEMOGRAPHIC_NO), null));
        }
    }
}
