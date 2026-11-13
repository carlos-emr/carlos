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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.CdsImportFieldDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportPatientDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportReviewDao;
import io.github.carlos_emr.carlos.commn.dto.CdsImportReviewProvenanceDto;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * The display decision behind every "CDS source fields" link: a chart no import touched shows none.
 * The lookups are mocked; the queries themselves run against MariaDB in the CDS import tier.
 */
@DisplayName("CDS source link presence")
class CdsSourceFieldPresenceResolverUnitTest extends CarlosUnitTestBase {
    private CdsImportFieldDao fields;
    private CdsImportPatientDao patients;
    private CdsImportReviewDao reviews;
    private CdsSourceFieldPresenceResolver resolver;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        fields = mock(CdsImportFieldDao.class);
        patients = mock(CdsImportPatientDao.class);
        reviews = mock(CdsImportReviewDao.class);
        resolver = new CdsSourceFieldPresenceResolver(fields, patients, reviews);
        request = new MockHttpServletRequest();
    }

    @Test
    @DisplayName("should hide every link when no import touched the chart")
    void shouldHideLinks_whenNoImportTouchedChart() {
        when(fields.categories(42)).thenReturn(List.of());
        when(patients.target(42)).thenReturn(false);

        assertThat(resolver.hasSourceFields(request, "42", "ClinicalNotes")).isFalse();
        assertThat(resolver.hasSourceFields(request, "42", "NewCategory")).isFalse();
        assertThat(resolver.hasSourceFields(request, "42", "Demographics")).isFalse();

        // One chart render asks for several categories; an ordinary patient costs two lookups in total.
        verify(fields, times(1)).categories(42);
        verify(patients, times(1)).target(42);
        verifyNoInteractions(reviews);
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

    @Test
    @DisplayName("should hide the link without a lookup when the patient or category cannot be real")
    void shouldHideLink_whenPatientOrCategoryInvalid() {
        assertThat(resolver.hasSourceFields(request, null, "ClinicalNotes")).isFalse();
        assertThat(resolver.hasSourceFields(request, " ", "ClinicalNotes")).isFalse();
        assertThat(resolver.hasSourceFields(request, "0", "ClinicalNotes")).isFalse();
        assertThat(resolver.hasSourceFields(request, "-7", "ClinicalNotes")).isFalse();
        assertThat(resolver.hasSourceFields(request, "12abc", "ClinicalNotes")).isFalse();
        assertThat(resolver.hasSourceFields(request, "99999999999", "ClinicalNotes")).isFalse();
        assertThat(resolver.hasSourceFields(request, "42", null)).isFalse();
        assertThat(resolver.hasSourceFields(request, "42", "NotACategory")).isFalse();

        verifyNoInteractions(fields, patients, reviews);
    }

    @Test
    @DisplayName("should show the links, and stop asking, when the lookup fails")
    void shouldShowLinks_whenLookupFails() {
        when(fields.categories(anyInt())).thenThrow(new IllegalStateException("lookup unavailable"));

        // Hiding the only route to preserved source values would be the worse failure.
        assertThat(resolver.hasSourceFields(request, "42", "ClinicalNotes")).isTrue();
        assertThat(resolver.hasSourceFields(request, "42", "NewCategory")).isTrue();
        verify(fields, times(1)).categories(42);
    }

    @Test
    @DisplayName("should answer without a request to remember on")
    void shouldAnswer_withoutRequest() {
        when(fields.categories(42)).thenReturn(List.of("ClinicalNotes"));

        assertThat(resolver.hasSourceFields(null, " 42 ", "ClinicalNotes")).isTrue();
        verify(reviews, times(0)).provenance(anyInt(), anyString(), anyString(), anyInt());
    }
}
