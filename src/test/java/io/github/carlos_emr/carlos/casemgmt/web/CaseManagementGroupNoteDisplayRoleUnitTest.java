/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.PMmodule.dao.SecUserRoleDao;
import io.github.carlos_emr.carlos.PMmodule.model.SecUserRole;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.GroupNoteDao;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.GroupNoteLink;
import io.github.carlos_emr.carlos.model.security.Secrole;
import io.github.carlos_emr.carlos.services.security.RolesManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Canonical group-note display requires an active assignment of the reporter's role. */
@Tag("unit")
@Tag("security")
class CaseManagementGroupNoteDisplayRoleUnitTest extends CarlosUnitTestBase {
    private CaseManagementView2Action action;
    private SecUserRoleDao heldRoles;
    private DemographicDao demographics;
    private LoggedInInfo user;
    private CaseManagementNote note;

    @BeforeEach
    void prepare() {
        action = mock(CaseManagementView2Action.class, CALLS_REAL_METHODS);
        heldRoles = mock(SecUserRoleDao.class);
        demographics = mock(DemographicDao.class);
        CaseManagementNoteDAO notes = mock(CaseManagementNoteDAO.class);
        GroupNoteDao groups = mock(GroupNoteDao.class);
        RolesManager reporters = mock(RolesManager.class);
        ReflectionTestUtils.setField(action, "secUserRoleDao", heldRoles);
        ReflectionTestUtils.setField(action, "demographicDao", demographics);
        ReflectionTestUtils.setField(action, "caseManagementNoteDao", notes);
        ReflectionTestUtils.setField(action, "groupNoteDao", groups);
        ReflectionTestUtils.setField(action, "roleMgr", reporters);
        user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("4249");
        Facility facility = new Facility();
        facility.setEnableGroupNotes(true);
        when(user.getCurrentFacility()).thenReturn(facility);
        GroupNoteLink link = new GroupNoteLink();
        link.setNoteId(502);
        link.setDemographicNo(101);
        link.setActive(true);
        when(groups.findLinksByDemographic(101)).thenReturn(List.of(link));
        note = new CaseManagementNote();
        note.setId(502L);
        note.setDemographic_no("202");
        note.setReporter_caisi_role("1");
        when(notes.getNote(502L)).thenReturn(note);
        Secrole doctor = new Secrole();
        doctor.setRoleName("doctor");
        when(reporters.getRole("1")).thenReturn(doctor);
    }

    private SecUserRole role(String name, boolean active) {
        SecUserRole role = new SecUserRole();
        role.setRoleName(name);
        role.setActive(active);
        return role;
    }

    private List<NoteDisplay> display() {
        ArrayList<NoteDisplay> visible = new ArrayList<>();
        ReflectionTestUtils.invokeMethod(action, "addGroupNotes", user, visible, 101, null);
        return visible;
    }

    @Test
    void shouldDisplayARecipientCopyWithRevisionMetadata_withoutMutatingOrHydratingTheOwner() {
        when(heldRoles.getUserRoles("4249")).thenReturn(List.of(role("doctor", true)));
        note.setDemographic_no("00202");
        note.setRevision("3");
        note.setRoleName("doctor");
        note.setProgramName("Group clinic");
        note.setUuid("shared-chain");
        note.setHistory("Original history");
        List<CaseManagementNote> copies = new ArrayList<>();
        try (var displays = mockConstruction(NoteDisplayLocal.class, (display, context) -> {
            assertThat(context.arguments().getFirst()).isSameAs(user);
            copies.add((CaseManagementNote) context.arguments().get(1));
        })) {
            assertThat(display()).containsExactlyElementsOf(displays.constructed());
            assertThat(displays.constructed()).hasSize(1);
            verify(displays.constructed().getFirst()).setReadOnly(true);
            verify(displays.constructed().getFirst()).setGroupNote(true);
            verify(displays.constructed().getFirst()).setLocation("202");
            assertThat(copies).hasSize(1);
            CaseManagementNote copy = copies.getFirst();
            assertThat(copy).isNotSameAs(note);
            assertThat(copy.getId()).isEqualTo(502L);
            assertThat(copy.getDemographic_no()).isEqualTo("101");
            assertThat(copy.getRevision()).isEqualTo("3");
            assertThat(copy.getRoleName()).isEqualTo("doctor");
            assertThat(copy.getProgramName()).isEqualTo("Group clinic");
            assertThat(copy.getUuid()).isEqualTo("shared-chain");
            assertThat(copy.getHistory()).isEqualTo("Original history");
            assertThat(note.getId()).isEqualTo(502L);
            assertThat(note.getDemographic_no()).isEqualTo("00202");
            assertThat(note.getRevision()).isEqualTo("3");
            assertThat(note.getRoleName()).isEqualTo("doctor");
            assertThat(note.getProgramName()).isEqualTo("Group clinic");
            assertThat(note.getHistory()).isEqualTo("Original history");
            verifyNoInteractions(demographics);
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0", "-1", "2147483648", "202');invalid()"})
    void shouldOmitTheGroupNote_whenTheOwnerIdentifierCannotBecomeANumericLocation(String owner) {
        when(heldRoles.getUserRoles("4249")).thenReturn(List.of(role("doctor", true)));
        note.setDemographic_no(owner);
        try (var displays = mockConstruction(NoteDisplayLocal.class)) {
            assertThat(display()).isEmpty();
            assertThat(displays.constructed()).isEmpty();
            verifyNoInteractions(demographics);
            assertThat(note.getDemographic_no()).isEqualTo(owner);
        }
    }

    @Test
    void shouldHideLinkedDoctorNote_whenOnlyTheNurseAssignmentIsActive() {
        when(heldRoles.getUserRoles("4249")).thenReturn(List.of(role("doctor", false), role("nurse", true)));
        try (var displays = mockConstruction(NoteDisplayLocal.class)) {
            assertThat(display()).isEmpty();
            assertThat(displays.constructed()).isEmpty();
            verify(demographics, never()).getDemographic(anyString());
            assertThat(note.getDemographic_no()).isEqualTo("202");
        }
    }
}
