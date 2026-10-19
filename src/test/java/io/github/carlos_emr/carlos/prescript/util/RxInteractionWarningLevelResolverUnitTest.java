/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * The interaction warning level precedence (facility, then provider, then patient) and which
 * significances each level shows (issue #4506).
 *
 * @since 2026-10-15
 */
@Tag("unit")
@Tag("rx")
@DisplayName("RxInteractionWarningLevelResolver")
class RxInteractionWarningLevelResolverUnitTest {

    private static final String PROVIDER = "999998";
    private static final int PATIENT = 42;

    private UserPropertyDAO userPropertyDao;
    private DemographicExtDao demographicExtDao;
    private LoggedInInfo loggedInInfo;
    private Facility facility;
    private RxInteractionWarningLevelResolver resolver;

    @BeforeEach
    void setUp() {
        userPropertyDao = mock(UserPropertyDAO.class);
        demographicExtDao = mock(DemographicExtDao.class);
        loggedInInfo = mock(LoggedInInfo.class);
        facility = new Facility();
        when(loggedInInfo.getCurrentFacility()).thenReturn(facility);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER);
        resolver = new RxInteractionWarningLevelResolver(userPropertyDao, demographicExtDao);
    }

    private void providerLevel(String value) {
        UserProperty property = new UserProperty();
        property.setName(RxInteractionWarningLevelResolver.PROPERTY_NAME);
        property.setValue(value);
        when(userPropertyDao.getProp(PROVIDER, RxInteractionWarningLevelResolver.PROPERTY_NAME)).thenReturn(property);
    }

    private void patientLevel(String value) {
        DemographicExt ext = new DemographicExt();
        ext.setKey(RxInteractionWarningLevelResolver.PROPERTY_NAME);
        ext.setValue(value);
        when(demographicExtDao.getLatestDemographicExt(PATIENT, RxInteractionWarningLevelResolver.PROPERTY_NAME))
                .thenReturn(ext);
    }

    @Test
    @DisplayName("should show every interaction when nothing is configured")
    void shouldResolveNotSpecified_whenNoScopeSetsALevel() {
        assertThat(resolver.resolve(loggedInInfo, PATIENT)).isEqualTo(RxInteractionWarningLevelResolver.NOT_SPECIFIED);
    }

    @Test
    @DisplayName("should use the facility level when neither provider nor patient sets one")
    void shouldUseFacilityLevel_whenProviderAndPatientAreUnset() {
        facility.setRxInteractionWarningLevel(RxInteractionWarningLevelResolver.MEDIUM);
        providerLevel("0");
        patientLevel("");

        assertThat(resolver.resolve(loggedInInfo, PATIENT)).isEqualTo(RxInteractionWarningLevelResolver.MEDIUM);
    }

    @Test
    @DisplayName("should let the provider preference replace the facility level")
    void shouldUseProviderLevel_overFacilityLevel() {
        facility.setRxInteractionWarningLevel(RxInteractionWarningLevelResolver.LOW);
        providerLevel("3");

        assertThat(resolver.resolve(loggedInInfo, PATIENT)).isEqualTo(RxInteractionWarningLevelResolver.HIGH);
    }

    @Test
    @DisplayName("should let the patient setting replace the provider and facility levels")
    void shouldUsePatientLevel_overProviderAndFacility() {
        facility.setRxInteractionWarningLevel(RxInteractionWarningLevelResolver.HIGH);
        providerLevel("4");
        patientLevel(" 1 ");

        assertThat(resolver.resolve(loggedInInfo, PATIENT)).isEqualTo(RxInteractionWarningLevelResolver.LOW);
    }

    @Test
    @DisplayName("should keep the facility default when the session has no current facility")
    void shouldResolveProviderLevel_whenSessionHasNoFacility() {
        when(loggedInInfo.getCurrentFacility()).thenReturn(null);
        providerLevel("2");

        assertThat(resolver.resolve(loggedInInfo, PATIENT)).isEqualTo(RxInteractionWarningLevelResolver.MEDIUM);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"high", "5", "-1", "33", "2.0"})
    @DisplayName("should ignore an unreadable stored value rather than fail or turn warnings off")
    void shouldIgnoreStoredValue_whenItIsNotALevel(String stored) {
        facility.setRxInteractionWarningLevel(RxInteractionWarningLevelResolver.MEDIUM);
        providerLevel(stored);
        patientLevel(stored);

        assertThat(resolver.resolve(loggedInInfo, PATIENT)).isEqualTo(RxInteractionWarningLevelResolver.MEDIUM);
    }

    @Test
    @DisplayName("should ignore an out-of-range facility level")
    void shouldIgnoreFacilityLevel_whenOutOfRange() {
        facility.setRxInteractionWarningLevel(9);

        assertThat(resolver.resolve(loggedInInfo, PATIENT)).isEqualTo(RxInteractionWarningLevelResolver.NOT_SPECIFIED);
    }

    @ParameterizedTest(name = "{0}")
    @NullAndEmptySource
    @DisplayName("should parse a missing value as not specified")
    void shouldParseNotSpecified_forMissingValue(String stored) {
        assertThat(RxInteractionWarningLevelResolver.parse(stored)).isEqualTo(RxInteractionWarningLevelResolver.NOT_SPECIFIED);
    }

    @ParameterizedTest(name = "level {0}, significance {1} -> {2}")
    @CsvSource({
            // not specified and low: everything
            "0,1,true", "0,2,true", "0,3,true", "0,0,true",
            "1,1,true", "1,2,true", "1,3,true", "1,0,true",
            // medium: moderate and major, plus ungraded
            "2,1,false", "2,2,true", "2,3,true", "2,0,true",
            // high: major, plus ungraded
            "3,1,false", "3,2,false", "3,3,true", "3,0,true",
            // none: nothing, not even ungraded
            "4,1,false", "4,2,false", "4,3,false", "4,0,false"
    })
    @DisplayName("should show only the significances the level allows")
    void shouldShowInteraction_byLevelAndSignificance(int level, int significance, boolean shown) {
        assertThat(RxInteractionWarningLevelResolver.isShown(level, significance)).isEqualTo(shown);
    }
}
