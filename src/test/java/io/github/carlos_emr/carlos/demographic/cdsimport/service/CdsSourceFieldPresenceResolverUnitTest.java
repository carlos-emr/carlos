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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.CdsImportFieldDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportPatientDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportReviewDao;
import io.github.carlos_emr.carlos.commn.dto.CdsImportReviewProvenanceDto;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * The display decision behind every "CDS source fields" link: a chart no import touched shows none,
 * and a viewer who may not read a category is not told that imported data exists in it.
 * The lookups and the access policy are mocked; the queries themselves run against MariaDB in the
 * CDS import tier, and the policy has its own tests.
 */
@DisplayName("CDS source link presence")
class CdsSourceFieldPresenceResolverUnitTest extends CarlosUnitTestBase {
    private CdsImportFieldDao fields;
    private CdsImportPatientDao patients;
    private CdsImportReviewDao reviews;
    private CdsImportAccessService access;
    private CdsSourceFieldPresenceResolver resolver;
    private MockHttpServletRequest request;
    private LoggedInInfo viewer;

    @BeforeEach
    void setUp() {
        fields = mock(CdsImportFieldDao.class);
        patients = mock(CdsImportPatientDao.class);
        reviews = mock(CdsImportReviewDao.class);
        // An unstubbed requireClinicalRead returns normally: the viewer may read.
        access = mock(CdsImportAccessService.class);
        resolver = new CdsSourceFieldPresenceResolver(fields, patients, reviews, access);
        request = new MockHttpServletRequest();
        viewer = new LoggedInInfo();
        request.getSession().setAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY", viewer);
    }

    @Test
    @DisplayName("should hide every link when no import touched the chart")
    void shouldHideLinks_whenNoImportTouchedChart() {
        when(fields.categories(42)).thenReturn(List.of());
        when(patients.target(42)).thenReturn(false);

        assertThat(resolver.hasSourceFields(request, "42", "ClinicalNotes")).isFalse();
        assertThat(resolver.hasSourceFields(request, "42", "NewCategory")).isFalse();
        assertThat(resolver.hasSourceFields(request, "42", "Demographics")).isFalse();

        // One chart render asks for several categories; an ordinary patient costs two lookups in
        // total and no authorization work.
        verify(fields, times(1)).categories(42);
        verify(patients, times(1)).target(42);
        verifyNoInteractions(reviews, access);
    }

    @Test
    @DisplayName("should show only the categories that hold source fields")
    void shouldShowLink_forCategoryWithSourceFields() {
        when(fields.categories(42)).thenReturn(List.of("ClinicalNotes", "Demographics"));
        when(patients.target(42)).thenReturn(true);
        when(reviews.provenance(42, "NewCategory", "", 1)).thenReturn(List.of());

        assertThat(resolver.hasSourceFields(request, "42", "ClinicalNotes")).isTrue();
        assertThat(resolver.hasSourceFields(request, "42", "NewCategory")).isFalse();
        verify(fields, times(1)).categories(42);
        verify(access).requireClinicalRead(viewer, 42, "ClinicalNotes");
        verify(access, never()).requireClinicalRead(any(), anyInt(), eq("NewCategory"));
    }

    @Test
    @DisplayName("should show the link when an imported chart has review provenance but no field in the category")
    void shouldShowLink_whenImportedChartHasReviewProvenance() {
        when(fields.categories(42)).thenReturn(List.of("Demographics"));
        when(patients.target(42)).thenReturn(true);
        when(reviews.provenance(42, "ClinicalNotes", "", 1)).thenReturn(List.of(
                new CdsImportReviewProvenanceDto("review", "operator", Instant.EPOCH, "DECLINE_PATIENT", "DECLINE_READY", "", 1L, "")));

        assertThat(resolver.hasSourceFields(request, "42", "ClinicalNotes")).isTrue();
        assertThat(resolver.hasSourceFields(request, "42", "ClinicalNotes")).isTrue();
        verify(reviews, times(1)).provenance(42, "ClinicalNotes", "", 1);
        verify(access, times(1)).requireClinicalRead(viewer, 42, "ClinicalNotes");
    }

    @Test
    @DisplayName("should not tell a viewer that imported data exists in a category they may not read")
    void shouldHideLink_whenViewerMayNotReadCategory() {
        when(fields.categories(42)).thenReturn(List.of("ClinicalNotes", "AllergiesAndAdverseReactions"));
        doThrow(new SecurityException("missing required sec object (_demographic)"))
                .when(access).requireClinicalRead(viewer, 42, "ClinicalNotes");

        assertThat(resolver.hasSourceFields(request, "42", "ClinicalNotes")).isFalse();
        assertThat(resolver.hasSourceFields(request, "42", "ClinicalNotes")).isFalse();
        // The refusal is per category: one the viewer may read still shows its link.
        assertThat(resolver.hasSourceFields(request, "42", "AllergiesAndAdverseReactions")).isTrue();
        verify(access, times(1)).requireClinicalRead(viewer, 42, "ClinicalNotes");
    }

    @Test
    @DisplayName("should hide the link when the authorization check itself fails")
    void shouldHideLink_whenAuthorizationCheckFails() {
        when(fields.categories(42)).thenReturn(List.of("ClinicalNotes"));
        doThrow(new IllegalStateException("policy unavailable")).when(access).requireClinicalRead(viewer, 42, "ClinicalNotes");

        assertThat(resolver.hasSourceFields(request, "42", "ClinicalNotes")).isFalse();
    }

    @Test
    @DisplayName("should keep each patient's answer separate within one request")
    void shouldKeepAnswersSeparate_forDifferentPatients() {
        when(fields.categories(42)).thenReturn(List.of("ClinicalNotes"));
        when(fields.categories(43)).thenReturn(List.of());
        when(patients.target(43)).thenReturn(false);

        assertThat(resolver.hasSourceFields(request, "42", "ClinicalNotes")).isTrue();
        assertThat(resolver.hasSourceFields(request, "43", "ClinicalNotes")).isFalse();
    }

    @ParameterizedTest(name = "[{index}] demographicNo=''{0}'' category=''{1}''")
    @CsvSource(value = {
            "NULL|ClinicalNotes", "' '|ClinicalNotes", "0|ClinicalNotes", "-7|ClinicalNotes", "12abc|ClinicalNotes",
            "2147483648|ClinicalNotes", "99999999999|ClinicalNotes", "42|NULL", "42|NotACategory"
    }, delimiter = '|', nullValues = "NULL")
    @DisplayName("should hide the link without a lookup when the patient or category cannot be real")
    void shouldHideLink_whenPatientOrCategoryInvalid(String demographicNo, String category) {
        assertThat(resolver.hasSourceFields(request, demographicNo, category)).isFalse();

        verifyNoInteractions(fields, patients, reviews, access);
    }

    @Test
    @DisplayName("should accept the largest demographic number an int can hold")
    void shouldAcceptDemographicNumber_upToIntMax() {
        when(fields.categories(Integer.MAX_VALUE)).thenReturn(List.of("ClinicalNotes"));
        when(fields.categories(1_000_000_000)).thenReturn(List.of("ClinicalNotes"));

        assertThat(resolver.hasSourceFields(request, "2147483647", "ClinicalNotes")).isTrue();
        assertThat(resolver.hasSourceFields(request, " 1000000000 ", "ClinicalNotes")).isTrue();
    }

    @Test
    @DisplayName("should show the links to an authorized viewer, and stop asking, when the lookup fails")
    void shouldShowLinks_whenLookupFails() {
        when(fields.categories(anyInt())).thenThrow(new IllegalStateException("lookup unavailable"));
        doThrow(new SecurityException("missing required sec object (_demographic)"))
                .when(access).requireClinicalRead(viewer, 42, "LaboratoryResults");

        // Hiding the only route to preserved source values would be the worse failure.
        assertThat(resolver.hasSourceFields(request, "42", "ClinicalNotes")).isTrue();
        assertThat(resolver.hasSourceFields(request, "42", "NewCategory")).isTrue();
        // An unknown presence is still no reason to show a category the viewer may not read.
        assertThat(resolver.hasSourceFields(request, "42", "LaboratoryResults")).isFalse();
        verify(fields, times(1)).categories(42);
    }

    @Test
    @DisplayName("should hide the link when there is no request or no signed-in session to name the viewer")
    void shouldHideLink_withoutViewer() {
        when(fields.categories(42)).thenReturn(List.of("ClinicalNotes"));
        doThrow(new SecurityException("missing required sec object (_demographic)"))
                .when(access).requireClinicalRead(null, 42, "ClinicalNotes");

        assertThat(resolver.hasSourceFields(null, "42", "ClinicalNotes")).isFalse();
        assertThat(resolver.hasSourceFields(new MockHttpServletRequest(), "42", "ClinicalNotes")).isFalse();
    }
}
