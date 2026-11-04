/*
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License,
 * version 2 or (at your option) any later version.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.GroupNoteDao;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.GroupNoteLink;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

/** Covers the chart issue-list caller of the note-to-issue DAO query (#5569). */
@Tag("unit")
class CaseManagementView2ActionGroupIssuesUnitTest {

    @Test
    void shouldLookUpIssuesForEveryGroupNote_whenBuildingChartIssueList() {
        GroupNoteDao groupNoteDao = mock(GroupNoteDao.class);
        CaseManagementManager manager = mock(CaseManagementManager.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        Facility facility = new Facility();
        facility.setEnableGroupNotes(true);
        when(loggedInInfo.getCurrentFacility()).thenReturn(facility);
        GroupNoteLink first = new GroupNoteLink();
        first.setNoteId(101);
        GroupNoteLink second = new GroupNoteLink();
        second.setNoteId(102);
        when(groupNoteDao.findLinksByDemographic(71)).thenReturn(List.of(first, second));
        when(manager.getIssuesByNote(101)).thenReturn(List.of());
        when(manager.getIssuesByNote(102)).thenReturn(List.of());

        try (var servlet = mockStatic(ServletActionContext.class);
             var spring = mockStatic(SpringUtils.class)) {
            spring.when(() -> SpringUtils.getBean(GroupNoteDao.class)).thenReturn(groupNoteDao);
            spring.when(() -> SpringUtils.getBean(CaseManagementManager.class)).thenReturn(manager);
            CaseManagementView2Action action = new CaseManagementView2Action();

            action.addGroupIssues(loggedInInfo, new ArrayList<>(), 71, false);

            verify(manager).getIssuesByNote(101);
            verify(manager).getIssuesByNote(102);
            verifyNoMoreInteractions(manager);
        }
    }

    @Test
    void shouldSkipGroupIssueQueries_whenGroupNotesAreDisabled() {
        GroupNoteDao groupNoteDao = mock(GroupNoteDao.class);
        CaseManagementManager manager = mock(CaseManagementManager.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getCurrentFacility()).thenReturn(new Facility());

        try (var servlet = mockStatic(ServletActionContext.class);
             var spring = mockStatic(SpringUtils.class)) {
            spring.when(() -> SpringUtils.getBean(GroupNoteDao.class)).thenReturn(groupNoteDao);
            spring.when(() -> SpringUtils.getBean(CaseManagementManager.class)).thenReturn(manager);

            new CaseManagementView2Action().addGroupIssues(loggedInInfo, new ArrayList<>(), 71, false);

            verifyNoInteractions(groupNoteDao, manager);
        }
    }
}
