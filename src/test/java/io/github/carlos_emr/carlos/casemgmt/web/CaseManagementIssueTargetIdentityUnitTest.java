/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.web.formbeans.CaseManagementEntryFormBean;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Issue #6090: issue change, delete and re-code requests name the patient issue the clinician
 * clicked (its casemgmt_issue id) and the server resolves that id in the editor's session list.
 * A list position from the rendered page is never used, because an editor refresh can rebuild
 * and reorder the session list between the rendering and the request.
 *
 * @since 2026-11-09
 */
@Tag("unit")
@Tag("casemgmt")
@DisplayName("Issue mutations resolve the clicked patient issue by identity")
class CaseManagementIssueTargetIdentityUnitTest extends CarlosUnitTestBase {

    private CaseManagementEntry2Action action;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private CaseManagementManager manager;
    private CaseManagementEntryFormBean form;
    private CaseManagementNote note;
    private MockedStatic<CarlosProperties> config;

    @BeforeEach
    void setUp() {
        action = mock(CaseManagementEntry2Action.class, CALLS_REAL_METHODS);
        request = new MockHttpServletRequest("POST", "/CaseManagementEntry");
        response = new MockHttpServletResponse();
        action.request = request;
        action.response = response;
        request.setParameter("demographicNo", "101");
        request.setParameter("ajax", "true");
        request.getSession().setAttribute("userrole", "doctor");
        request.getSession().setAttribute(ChartProgramContext.class.getName() + ".101", "0");
        LoggedInInfo user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        SecurityInfoManager permissions = mock(SecurityInfoManager.class);
        when(permissions.hasPrivilege(user, "_demographic", "r", "101")).thenReturn(true);
        when(permissions.hasPrivilege(user, "_casemgmt.issues", "w", "101")).thenReturn(true);
        when(permissions.isAllowedAccessToPatientRecord(user, 101)).thenReturn(true);
        injectDependency(action, "securityInfoManager", permissions);
        manager = mock(CaseManagementManager.class);
        action.caseManagementMgr = manager;
        doReturn("").when(action).getDemoName(anyString());
        doReturn("").when(action).getDemoAge(anyString());
        doReturn("").when(action).getDemoDOB(anyString());
        // An editor holding a new, unsaved note: issue deletion then changes only the issue.
        note = new CaseManagementNote();
        note.setDemographic_no("101");
        note.setNote("Unsaved FAKE editor text");
        form = new CaseManagementEntryFormBean();
        form.setCaseNote(note);
        form.setIssueCheckList(new ArrayList<>());
        request.getSession().setAttribute("caseManagementEntryForm101", form);
        CarlosProperties properties = mock(CarlosProperties.class);
        config = mockStatic(CarlosProperties.class);
        config.when(CarlosProperties::getInstance).thenReturn(properties);
    }

    @AfterEach
    void tearDown() {
        config.close();
    }

    private CheckBoxBean issue(long id) {
        CaseManagementIssue issue = new CaseManagementIssue();
        issue.setId(id);
        issue.setDemographic_no(101);
        Issue catalog = new Issue();
        catalog.setId(9000L + id);
        catalog.setDescription("FAKE issue " + id);
        issue.setIssue(catalog);
        issue.setIssue_id(catalog.getId());
        CheckBoxBean entry = new CheckBoxBean();
        entry.setIssue(issue);
        entry.getIssueDisplay().setDescription(catalog.getDescription());
        form.getIssueCheckList().add(entry);
        return entry;
    }

    private void click(String caseIssueId, String field, String value) {
        request.setParameter("caseIssueId", caseIssueId);
        if (field != null) request.setParameter("issueField", field);
        if (value != null) request.setParameter("issueValue", value);
    }

    /** The edit refresh rebuilds the session list from storage, in another order. */
    private void reorder(CheckBoxBean... order) {
        form.setIssueCheckList(new ArrayList<>(List.of(order)));
    }

    @Test
    void shouldChangeTheClickedIssue_whenARefreshReorderedTheList() throws Exception {
        CheckBoxBean first = issue(41);
        CheckBoxBean second = issue(42);
        reorder(second, first);
        // The page rendered 41 at position 0; that position now holds 42. Legacy index fields
        // posted by an old page must not steer the change either.
        click("41", "acute", "true");
        request.setParameter("lineId", "0");
        request.setParameter("issueCheckList[0].issue.acute", "false");

        assertThat(action.issueChange()).isEqualTo("issueList_ajax");

        assertThat(first.getIssue().isAcute()).isTrue();
        assertThat(first.getIssueDisplay().getAcute()).isEqualTo("acute");
        assertThat(second.getIssue().isAcute()).isFalse();
        verify(manager).saveAndUpdateCaseIssues(argThat(saved -> saved.size() == 1 && saved.get(0) == first.getIssue()));
    }

    @Test
    void shouldSetOnlyTheClickedAttribute_withOtherStoredFlagsKept() throws Exception {
        CheckBoxBean entry = issue(41);
        entry.getIssue().setCertain(true);
        entry.getIssue().setMajor(true);
        click("41", "resolved", "true");

        action.issueChange();

        assertThat(entry.getIssue().isResolved()).isTrue();
        assertThat(entry.getIssue().isCertain()).isTrue();
        assertThat(entry.getIssue().isMajor()).isTrue();
        assertThat(entry.getIssue().isAcute()).isFalse();
        assertThat(entry.getIssueDisplay().getResolved()).isEqualTo("resolved");
        assertThat(entry.getIssueDisplay().getCertain()).isEqualTo("certain");
    }

    @Test
    void shouldSetFalseValues_forEachAttribute() throws Exception {
        for (String field : List.of("acute", "certain", "major", "resolved")) {
            CheckBoxBean entry = issue(50 + field.length());
            entry.getIssue().setAcute(true);
            entry.getIssue().setCertain(true);
            entry.getIssue().setMajor(true);
            entry.getIssue().setResolved(true);
            click(String.valueOf(entry.getIssue().getId()), field, "false");

            action.issueChange();

            CaseManagementIssue changed = entry.getIssue();
            assertThat(List.of(changed.isAcute(), changed.isCertain(), changed.isMajor(), changed.isResolved()))
                    .as(field).containsOnlyOnce(false);
            form.getIssueCheckList().clear();
        }
    }

    @Test
    void shouldRefuseWithConflict_whenTheClickedIssueIsNoLongerInTheEditor() throws Exception {
        CheckBoxBean remaining = issue(42);
        note.setHourOfEncounterTime(1);
        request.setParameter("hourOfEncounterTime", "3");
        click("41", "acute", "true");

        assertThat(action.issueChange()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(remaining.getIssue().isAcute()).isFalse();
        assertThat(note.getHourOfEncounterTime()).as("a refused change leaves the editor untouched").isEqualTo(1);
        assertThat(request.getAttribute("change_flag")).isNull();
        verifyNoInteractions(manager);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "-41", "41 ", "4.1", "0x29", "abc", "99999999999999999999"})
    void shouldRefuseMalformedIssueIds_beforeAnyChange(String id) throws Exception {
        CheckBoxBean entry = issue(41);
        if (id != null) request.setParameter("caseIssueId", id);
        request.setParameter("issueField", "acute");
        request.setParameter("issueValue", "true");

        assertThat(action.issueChange()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(entry.getIssue().isAcute()).isFalse();
        verifyNoInteractions(manager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"role", "issue.acute", "Acute", "update_date", ""})
    void shouldRefuseUnknownAttributes_beforeAnyChange(String field) throws Exception {
        issue(41);
        click("41", field, "true");

        assertThat(action.issueChange()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(manager);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"TRUE", "1", "on", "yes", "true "})
    void shouldRefuseIssueValues_otherThanTrueOrFalse(String value) throws Exception {
        CheckBoxBean entry = issue(41);
        click("41", "acute", null);
        if (value != null) request.setParameter("issueValue", value);

        assertThat(action.issueChange()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(entry.getIssue().isAcute()).isFalse();
        verifyNoInteractions(manager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"certain", "major"})
    void shouldRefuseWriteOnlyAttributes_forAnIssueTheEditorShowsReadOnly(String field) throws Exception {
        CheckBoxBean entry = issue(41);
        entry.getIssueDisplay().setWriteAccess(false);
        click("41", field, "true");

        assertThat(action.issueChange()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(entry.getIssue().isCertain()).isFalse();
        assertThat(entry.getIssue().isMajor()).isFalse();
        verifyNoInteractions(manager);
    }

    @Test
    void shouldKeepAcuteAndResolvedAvailable_forAReadOnlyIssueAsTheEditorOffersThem() throws Exception {
        CheckBoxBean entry = issue(41);
        entry.getIssueDisplay().setWriteAccess(false);
        click("41", "resolved", "true");

        assertThat(action.issueChange()).isEqualTo("issueList_ajax");

        assertThat(entry.getIssue().isResolved()).isTrue();
    }

    @Test
    void shouldRefuseWithConflict_whenTwoRowsCarryTheSameIssueId() throws Exception {
        CheckBoxBean first = issue(41);
        CheckBoxBean copy = issue(41);
        click("41", "acute", "true");

        assertThat(action.issueChange()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(first.getIssue().isAcute()).isFalse();
        assertThat(copy.getIssue().isAcute()).isFalse();
        verifyNoInteractions(manager);
    }

    @Test
    void shouldRefuseAnotherPatientsIssue_evenWhenTheIdIsInTheSessionList() {
        issue(41).getIssue().setDemographic_no(102);
        click("41", "acute", "true");

        assertThatThrownBy(action::issueChange).isInstanceOf(SecurityException.class);
        verifyNoInteractions(manager);
    }

    @Test
    void shouldDeleteTheTwoClickedIssues_whenTheFirstDeletionShiftedTheList() throws Exception {
        issue(40);
        CheckBoxBean second = issue(41);
        CheckBoxBean third = issue(42);
        // Both Delete links were rendered from the same page: 41 at position 1, 42 at position 2.
        click("41", null, null);
        assertThat(action.issueDelete()).isEqualTo("issueList_ajax");
        click("42", null, null);
        assertThat(action.issueDelete()).isEqualTo("issueList_ajax");

        var deleted = org.mockito.ArgumentCaptor.forClass(CaseManagementIssue.class);
        verify(manager, times(2)).deleteIssueById(deleted.capture());
        assertThat(deleted.getAllValues()).containsExactly(second.getIssue(), third.getIssue());
        assertThat(form.getIssueCheckList()).extracting(entry -> entry.getIssue().getId()).containsExactly(40L);
    }

    @Test
    void shouldDeleteTheClickedIssue_whenARefreshReorderedTheList() throws Exception {
        CheckBoxBean first = issue(41);
        CheckBoxBean second = issue(42);
        reorder(second, first);
        click("41", null, null);
        request.setParameter("deleteId", "0");

        assertThat(action.issueDelete()).isEqualTo("issueList_ajax");

        verify(manager).deleteIssueById(first.getIssue());
        verify(manager, never()).deleteIssueById(second.getIssue());
        assertThat(form.getIssueCheckList()).containsExactly(second);
    }

    @Test
    void shouldRefuseDeletionWithConflict_whenTheIssueIsNoLongerInTheEditor() throws Exception {
        CheckBoxBean remaining = issue(42);
        var before = form.getIssueCheckList();
        click("41", null, null);

        assertThat(action.issueDelete()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(form.getIssueCheckList()).isSameAs(before).containsExactly(remaining);
        verify(manager, never()).deleteIssueById(any());
    }

    @Test
    void shouldRefuseDeletion_whenNoIssueIsNamed() throws Exception {
        issue(41);
        request.setParameter("deleteId", "0");

        assertThat(action.issueDelete()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(400);
        verify(manager, never()).deleteIssueById(any());
    }

    @Test
    void shouldRecodeTheIssueChosenByItsChangeLink_whenARefreshReorderedTheList() {
        CheckBoxBean first = issue(41);
        CheckBoxBean second = issue(42);
        reorder(second, first);
        Issue replacement = new Issue();
        replacement.setId(777L);
        replacement.setCode("FAKE777");
        replacement.setType("icd10");
        replacement.setDescription("FAKE replacement diagnosis");
        when(manager.getIssue("777")).thenReturn(replacement);
        request.setParameter("change_diagnosis_id", "41");
        request.setParameter("newIssueId", "777");

        assertThat(action.ajaxChangeDiagnosis()).isEqualTo("issueList_ajax");

        assertThat(first.getIssue().getIssue()).isSameAs(replacement);
        assertThat(first.getIssue().getIssue_id()).isEqualTo(777L);
        assertThat(first.getIssueDisplay().getDescription()).isEqualTo("FAKE replacement diagnosis");
        assertThat(first.getIssueDisplay().getCode()).isEqualTo("FAKE777");
        assertThat(second.getIssue().getIssue_id()).isEqualTo(9042L);
        verify(manager).saveCaseIssue(first.getIssue());
    }

    @Test
    void shouldRefuseRecodeWithConflict_whenTheChosenIssueIsGone() {
        CheckBoxBean remaining = issue(42);
        request.setParameter("change_diagnosis_id", "41");
        request.setParameter("newIssueId", "777");

        assertThat(action.ajaxChangeDiagnosis()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(remaining.getIssue().getIssue_id()).isEqualTo(9042L);
        verifyNoInteractions(manager);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "abc", "778"})
    void shouldRefuseRecode_whenTheReplacementDiagnosisIsNotAKnownIssue(String newIssueId) {
        CheckBoxBean entry = issue(41);
        if (newIssueId != null) request.setParameter("newIssueId", newIssueId);
        request.setParameter("change_diagnosis_id", "41");

        assertThat(action.ajaxChangeDiagnosis()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(entry.getIssue().getIssue_id()).isEqualTo(9041L);
        verify(manager, never()).saveCaseIssue(any());
    }

    /** The legacy IssueSearch page posts the chosen replacement as a checked new-issue row. */
    private void chooseLegacyReplacement(long issueId) {
        Issue chosen = new Issue();
        chosen.setId(issueId);
        CheckIssueBoxBean row = new CheckIssueBoxBean();
        row.setIssue(chosen);
        row.setChecked(true);
        action.setNewIssueCheckList(new CheckIssueBoxBean[] {row});
        injectDependency(action, "issueCheckList", form.getIssueCheckList());
    }

    @Test
    void shouldRecodeTheNamedIssue_whenTheLegacyIssueSearchPageSubmitsAfterAReorder() {
        CheckBoxBean first = issue(41);
        CheckBoxBean second = issue(42);
        reorder(second, first);
        Issue replacement = new Issue();
        replacement.setId(777L);
        replacement.setCode("FAKE777");
        replacement.setDescription("FAKE replacement diagnosis");
        when(manager.getIssue("777")).thenReturn(replacement);
        request.setParameter("change_diagnosis_id", "41");
        chooseLegacyReplacement(777L);

        assertThat(action.submitChangeDiagnosis()).isEqualTo("view");

        assertThat(first.getIssue().getIssue_id()).isEqualTo(777L);
        assertThat(first.getIssueDisplay().getDescription()).isEqualTo("FAKE replacement diagnosis");
        assertThat(second.getIssue().getIssue_id()).isEqualTo(9042L);
        verify(manager).saveCaseIssue(first.getIssue());
    }

    @Test
    void shouldRefuseTheLegacyRecode_whenTheReplacementDiagnosisIsUnknown() {
        CheckBoxBean entry = issue(41);
        request.setParameter("change_diagnosis_id", "41");
        chooseLegacyReplacement(778L);

        assertThat(action.submitChangeDiagnosis()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(entry.getIssue().getIssue_id()).isEqualTo(9041L);
        verify(manager, never()).saveCaseIssue(any());
        verify(manager, never()).changeIssueInCPP(any(), any(), any());
    }

    @Test
    void shouldCarryThePatientIssueId_toTheLegacyIssueSearchPage() {
        issue(41);
        request.setParameter("caseIssueId", "41");

        assertThat(action.changeDiagnosis()).isEqualTo("IssueSearch");

        assertThat(request.getAttribute("change_diagnosis_id")).isEqualTo("41");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "1;2"})
    void shouldRefuseTheLegacyIssueSearchPage_withoutAPatientIssueId(String id) {
        if (id != null) request.setParameter("caseIssueId", id);

        assertThat(action.changeDiagnosis()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(request.getAttribute("change_diagnosis_id")).isNull();
    }
}
