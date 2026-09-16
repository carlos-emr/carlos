/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.web;

import io.github.carlos_emr.carlos.commn.dao.EpisodeDao;
import io.github.carlos_emr.carlos.commn.model.Episode;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class Episode2ActionUnitTest extends CarlosWebTestBase {
    private EpisodeDao dao;
    private Episode2Action action;
    private Episode submitted;

    @BeforeEach void prepare() {
        dao = mock(EpisodeDao.class);
        replaceSpringUtilsBean(EpisodeDao.class, dao);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_demographic"), anyString(), anyInt())).thenReturn(true);
        mockRequest.setMethod("POST");
        submitted = new Episode();
        submitted.setDemographicNo(10);
        submitted.setDescription("Synthetic episode");
        submitted.setStatus("Current");
        submitted.setStartDateStr("2026-01-02");
        action = new Episode2Action();
        action.setEpisode(submitted);
    }

    private Episode stored(int patient) {
        Episode stored = new Episode();
        stored.setId(7);
        stored.setDemographicNo(patient);
        stored.setDescription("Original description");
        when(dao.find(Integer.valueOf(7))).thenReturn(stored);
        addRequestParameter("episode.id", "7");
        return stored;
    }

    @Test void cannotViewEpisodeWithoutPatientReadAccess() {
        addRequestParameter("demographicNo", "10");
        addRequestParameter("episode.id", "7");
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_demographic"), eq("r"), eq(10))).thenReturn(false);
        assertThatThrownBy(action::edit).isInstanceOf(SecurityException.class);
        verifyNoInteractions(dao);
        assertThat(mockRequest.getAttribute("episode")).isNull();
    }

    @Test void cannotViewEpisodeFromDifferentPatient() throws Exception {
        stored(20);
        addRequestParameter("demographicNo", "10");
        assertThat(action.edit()).isEqualTo("none");
        assertThat(mockResponse.getStatus()).isEqualTo(404);
        assertThat(mockRequest.getAttribute("episode")).isNull();
    }

    @Test void canViewOwnedEpisode() throws Exception {
        Episode existing = stored(10);
        addRequestParameter("demographicNo", "10");
        assertThat(action.edit()).isEqualTo("form");
        assertThat(mockRequest.getAttribute("episode")).isSameAs(existing);
    }

    @Test void existingEpisodeLinkWithoutPatientParameterAuthorizesStoredPatient() throws Exception {
        Episode existing = stored(10);
        assertThat(action.edit()).isEqualTo("form");
        verify(mockSecurityInfoManager).hasPrivilege(any(), eq("_demographic"), eq("r"), eq(10));
        assertThat(mockRequest.getAttribute("episode")).isSameAs(existing);
        assertThat(mockRequest.getAttribute("demographicNo")).isEqualTo("10");
    }

    @Test void existingEpisodeLinkCannotExposeDeniedStoredPatient() {
        stored(20);
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_demographic"), eq("r"), eq(20))).thenReturn(false);
        assertThatThrownBy(action::edit).isInstanceOf(SecurityException.class);
        assertThat(mockRequest.getAttribute("episode")).isNull();
    }

    @Test void missingPatientAndEpisodeCannotOpenBlankEditor() throws Exception {
        assertThat(action.edit()).isEqualTo("none");
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        verifyNoInteractions(dao);
    }

    @Test void cannotMoveAnotherPatientsEpisodeOrMutateManagedEntity() throws Exception {
        Episode existing = stored(20);
        assertThat(action.save()).isEqualTo("none");
        assertThat(mockResponse.getStatus()).isEqualTo(404);
        assertThat(existing.getDemographicNo()).isEqualTo(20);
        assertThat(existing.getDescription()).isEqualTo("Original description");
        verify(dao, never()).merge(any());
    }

    @Test void deniedSaveLeavesManagedEntityUntouched() {
        Episode existing = stored(10);
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_demographic"), eq("w"), eq(10))).thenReturn(false);
        assertThatThrownBy(action::save).isInstanceOf(SecurityException.class);
        assertThat(existing.getDescription()).isEqualTo("Original description");
        verifyNoInteractions(dao);
    }

    @Test void validUpdatePreservesIdentity() throws Exception {
        Episode existing = stored(10);
        assertThat(action.save()).isEqualTo("success");
        assertThat(existing.getId()).isEqualTo(7);
        assertThat(existing.getDemographicNo()).isEqualTo(10);
        assertThat(existing.getDescription()).isEqualTo("Synthetic episode");
        verify(dao).merge(existing);
    }

    @Test void canCreateEpisode() throws Exception {
        assertThat(action.save()).isEqualTo("success");
        verify(dao).persist(argThat((Episode e) -> e.getDemographicNo() == 10 && "Current".equals(e.getStatus())));
    }

    @Test void completionRequiresEndDateOnServer() throws Exception {
        Episode existing = stored(10);
        submitted.setStatus("Complete");
        assertThat(action.save()).isEqualTo("none");
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        assertThat(existing.getDescription()).isEqualTo("Original description");
        verify(dao, never()).merge(any());
    }

    @Test void completedEpisodeWithEndDateCanSave() throws Exception {
        submitted.setStatus("Complete");
        submitted.setEndDateStr("2026-02-03");
        assertThat(action.save()).isEqualTo("success");
        verify(dao).persist(any(Episode.class));
    }

    @ParameterizedTest @ValueSource(strings = {"bad", "-1", "2147483648"})
    void malformedIdCannotCreateNewEpisode(String id) throws Exception {
        addRequestParameter("episode.id", id);
        assertThat(action.save()).isEqualTo("none");
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        verifyNoInteractions(dao);
    }

    @Test void invalidCalendarDateCannotBeSilentlyNormalized() throws Exception {
        submitted.setStartDateStr("2026-02-31");
        addRequestParameter("episode.startDateStr", "2026-02-31");
        assertThat(action.save()).isEqualTo("none");
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        verifyNoInteractions(dao);
    }

    @Test void saveRequiresPost() throws Exception {
        mockRequest.setMethod("GET");
        assertThat(action.save()).isEqualTo("none");
        assertThat(mockResponse.getStatus()).isEqualTo(405);
        verifyNoInteractions(dao);
    }
}
