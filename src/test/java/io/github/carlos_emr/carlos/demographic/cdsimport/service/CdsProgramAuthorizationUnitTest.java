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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderFacilityDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsProgramContextDto;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

@Tag("unit")
class CdsProgramAuthorizationUnitTest {
    private EntityManager em;
    private CdsProgramContextService programs;
    private CdsImportConfigurationService configuration;
    private CdsImportAccessService access;
    private ProviderFacilityDao memberships;

    @BeforeEach
    void prepare() {
        em = mock(EntityManager.class);
        programs = mock(CdsProgramContextService.class);
        configuration = mock(CdsImportConfigurationService.class);
        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilegeReadOnly(any(), eq("_demographic"), eq("w"), isNull()))
                .thenReturn(true);
        var providers = mock(ProviderDao.class);
        memberships = mock(ProviderFacilityDao.class);
        access =
                new CdsImportAccessService(
                        security,
                        configuration,
                        mock(CdsCourseService.class),
                        programs,
                        providers,
                        mock(SecurityDao.class),
                        memberships);
        ReflectionTestUtils.setField(access, "entityManager", em);
        var provider = new Provider();
        provider.setProviderNo("77");
        provider.setStatus("1");
        when(providers.getProvider("77")).thenReturn(provider);
    }

    private CdsImportCommand command(Integer facility, boolean synthetic) {
        return new CdsImportCommand(
                Path.of("/owned/source.xml"),
                "submission",
                "77",
                34,
                facility,
                "fixture",
                "omd-5.2",
                false,
                false,
                false,
                0,
                synthetic ? 7 : null,
                synthetic);
    }

    private CdsImportActorDto actor(Integer facility) {
        return new CdsImportActorDto("root-fixture", "77", null, null, facility, 34);
    }

    @Test
    void shouldReauthorizeMembershipOnEveryResume_withoutReadingClinicalProgramFormulas() {
        when(programs.metadata(34)).thenReturn(new CdsProgramContextDto(34, "Clinic", "active", 1));
        when(programs.member("77", 34)).thenReturn(true);
        assertThatCode(() -> access.context(actor(null), command(null, false), null))
                .doesNotThrowAnyException();
        when(programs.member("77", 34)).thenReturn(false);
        assertThatThrownBy(() -> access.context(actor(null), command(null, false), null))
                .isInstanceOf(SecurityException.class);
        verify(em, never()).find(eq(Program.class), any());
    }

    @Test
    void shouldRefuseMissingOrInactiveProgram_insteadOfUsingAnAuthorizedDefault() {
        assertThatThrownBy(() -> access.context(actor(null), command(null, false), null))
                .isInstanceOf(SecurityException.class);
        when(programs.metadata(34))
                .thenReturn(new CdsProgramContextDto(34, "Clinic", "inactive", 1));
        assertThatThrownBy(() -> access.context(actor(null), command(null, false), null))
                .isInstanceOf(SecurityException.class);
        verify(programs, never()).defaultProgramId(anyString());
    }

    @Test
    void shouldRefuseCrossFacilityProgram_evenWhenProviderBelongsToRequestedFacility() {
        when(em.find(Facility.class, 1)).thenReturn(new Facility());
        when(memberships.countByProviderAndFacility("77", 1)).thenReturn(1L);
        when(programs.metadata(34))
                .thenReturn(new CdsProgramContextDto(34, "Other facility", "active", 2));
        assertThatThrownBy(() -> access.context(actor(1), command(1, false), null))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldObserveRevokedFacilityMembership_betweenAuthorizedCalls() {
        when(em.find(Facility.class, 1)).thenReturn(new Facility());
        when(programs.metadata(34)).thenReturn(new CdsProgramContextDto(34, "Clinic", "active", 1));
        when(programs.member("77", 34)).thenReturn(true);
        when(memberships.countByProviderAndFacility("77", 1)).thenReturn(1L, 0L);
        access.context(actor(1), command(1, false), null);
        assertThatThrownBy(() -> access.context(actor(1), command(1, false), null))
                .isInstanceOf(SecurityException.class);
        verify(memberships, times(2)).countByProviderAndFacility("77", 1);
        verify(em, never()).createNativeQuery(anyString());
    }

    @Test
    void shouldRefuseMissingFacility_beforeConsultingMembership() {
        assertThatThrownBy(() -> access.context(actor(1), command(1, false), null))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(memberships);
    }

    @Test
    void shouldValidateSyntheticCourseAndPersonalProgramFromScalars_withExistingRoleChecks() {
        var course =
                new CdsCourseService(
                        configuration, mock(PlatformTransactionManager.class), programs);
        ReflectionTestUtils.setField(course, "entityManager", em);
        when(configuration.value("OSCAR_LEARNING", "no")).thenReturn("yes");
        when(configuration.value("cds.import.source.fixture.synthetic", "false"))
                .thenReturn("true");
        when(configuration.value("cds.import.course.7.educators", "")).thenReturn("77");
        when(programs.metadata(7)).thenReturn(new CdsProgramContextDto(7, "Course", "active", 1));
        when(programs.metadata(34))
                .thenReturn(new CdsProgramContextDto(34, "programstudent77", "active", 1));
        TypedQuery<Long> membership = mock(TypedQuery.class, RETURNS_SELF);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(membership);
        when(membership.getSingleResult()).thenReturn(1L);
        assertThatCode(() -> course.requireCopy(actor(1), command(1, true)))
                .doesNotThrowAnyException();
        when(membership.getSingleResult()).thenReturn(0L);
        assertThatThrownBy(() -> course.requireCopy(actor(1), command(1, true)))
                .isInstanceOf(SecurityException.class);
        verify(em, never()).find(eq(Program.class), any());
    }

    // The gates below compare a stored status or a server property with an ASCII word. They
    // accept the word in any ASCII letter case, as before, whatever the JVM default locale, and
    // no longer accept a letter that merely folds to an ASCII one (U+0131 dotless i, U+0130
    // dotted capital I, U+017F long s), which String.equalsIgnoreCase did.

    private void underTurkishLocale(Runnable check) {
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.of("tr", "TR"));
            check.run();
        } finally {
            java.util.Locale.setDefault(original);
        }
    }

    private CdsCourseService course(String learning, String courseStatus, String programStatus) {
        var course =
                new CdsCourseService(
                        configuration, mock(PlatformTransactionManager.class), programs);
        ReflectionTestUtils.setField(course, "entityManager", em);
        when(configuration.value("OSCAR_LEARNING", "no")).thenReturn(learning);
        when(configuration.value("cds.import.source.fixture.synthetic", "false"))
                .thenReturn("true");
        when(configuration.value("cds.import.course.7.educators", "")).thenReturn("77");
        when(programs.metadata(7))
                .thenReturn(new CdsProgramContextDto(7, "Course", courseStatus, 1));
        when(programs.metadata(34))
                .thenReturn(new CdsProgramContextDto(34, "programstudent77", programStatus, 1));
        @SuppressWarnings("unchecked")
        TypedQuery<Long> membership = mock(TypedQuery.class, RETURNS_SELF);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(membership);
        when(membership.getSingleResult()).thenReturn(1L);
        return course;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"active", "Active", "ACTIVE"})
    void shouldAdmitActiveProgramInAnyAsciiCase_underTurkishDefaultLocale(String status) {
        when(programs.metadata(34)).thenReturn(new CdsProgramContextDto(34, "Clinic", status, 1));
        when(programs.member("77", 34)).thenReturn(true);
        underTurkishLocale(
                () ->
                        assertThatCode(() -> access.context(actor(null), command(null, false), null))
                                .doesNotThrowAnyException());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {"inactive", "", " active", "act\u0131ve", "ACT\u0130VE"})
    void shouldRefuseProgram_whenStatusIsNotTheWordActive(String status) {
        when(programs.metadata(34)).thenReturn(new CdsProgramContextDto(34, "Clinic", status, 1));
        when(programs.member("77", 34)).thenReturn(true);
        assertThatThrownBy(() -> access.context(actor(null), command(null, false), null))
                .isInstanceOf(SecurityException.class);
        when(programs.metadata(34)).thenReturn(new CdsProgramContextDto(34, "Clinic", null, 1));
        assertThatThrownBy(() -> access.context(actor(null), command(null, false), null))
                .isInstanceOf(SecurityException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "yes,active,active",
        "YES,ACTIVE,Active",
        "Yes,Active,ACTIVE"
    })
    void shouldAdmitCourseCopyForRealValuesInAnyAsciiCase_underTurkishDefaultLocale(
            String learning, String courseStatus, String programStatus) {
        var course = course(learning, courseStatus, programStatus);
        underTurkishLocale(
                () ->
                        assertThatCode(() -> course.requireCopy(actor(1), command(1, true)))
                                .doesNotThrowAnyException());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"no", "", "true", "ye\u017f"})
    void shouldRefuseCourseCopy_whenLearningPropertyIsNotTheWordYes(String learning) {
        var course = course(learning, "active", "active");
        assertThatThrownBy(() -> course.requireCopy(actor(1), command(1, true)))
                .isInstanceOf(SecurityException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {"inactive", "", "act\u0131ve", "ACT\u0130VE"})
    void shouldRefuseCourseCopy_whenCourseStatusIsNotTheWordActive(String status) {
        var course = course("yes", status, "active");
        assertThatThrownBy(() -> course.requireCopy(actor(1), command(1, true)))
                .isInstanceOf(SecurityException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {"inactive", "", "act\u0131ve", "ACT\u0130VE"})
    void shouldRefuseCourseCopy_whenPersonalProgramStatusIsNotTheWordActive(String status) {
        var course = course("yes", "active", status);
        assertThatThrownBy(() -> course.requireCopy(actor(1), command(1, true)))
                .isInstanceOf(SecurityException.class);
    }
}
