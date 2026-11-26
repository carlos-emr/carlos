/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.ProfessionalContact;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link DemographicContactCreator#convertProfessionalContactAsProfessionalSpecialist}: the contact
 * editor's conversion of a professional contact into the directory specialist it edits. It is
 * guarded by {@code _demographic} read rights, merges into the existing specialist for a positive
 * id, and never stores the text "null" for a missing phone or CPSO number.
 *
 * <p>The class keeps its DAO and security collaborators in static fields captured when it first
 * loads, so each test installs its own mocks there and restores the previous values afterwards.
 */
@Tag("unit")
class DemographicContactCreatorSpecialistConversionUnitTest extends CarlosUnitTestBase {

    private final ProfessionalSpecialistDao specialists = mock(ProfessionalSpecialistDao.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final LoggedInInfo user = new LoggedInInfo();
    private Object previousSpecialists;
    private Object previousSecurity;

    @BeforeEach
    void installCollaborators() {
        createAndRegisterMock(ProfessionalSpecialistDao.class);
        createAndRegisterMock(SecurityInfoManager.class);
        previousSpecialists = ReflectionTestUtils.getField(DemographicContactCreator.class, "professionalSpecialistDao");
        previousSecurity = ReflectionTestUtils.getField(DemographicContactCreator.class, "securityInfoManager");
        ReflectionTestUtils.setField(DemographicContactCreator.class, "professionalSpecialistDao", specialists);
        ReflectionTestUtils.setField(DemographicContactCreator.class, "securityInfoManager", security);
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"), (String) isNull())).thenReturn(true);
    }

    @AfterEach
    void restoreCollaborators() {
        ReflectionTestUtils.setField(DemographicContactCreator.class, "professionalSpecialistDao", previousSpecialists);
        ReflectionTestUtils.setField(DemographicContactCreator.class, "securityInfoManager", previousSecurity);
    }

    private static ProfessionalContact contact(Integer id) {
        ProfessionalContact contact = new ProfessionalContact();
        contact.setId(id);
        contact.setFirstName("FAKE");
        contact.setLastName("Consultant");
        contact.setAddress("1 FAKE Street");
        contact.setCity("Faketown");
        contact.setPostal("A1A 1A1");
        contact.setProvince("ON");
        contact.setCountry("CA");
        contact.setFax("555-0101");
        return contact;
    }

    @Test
    @DisplayName("should refuse the conversion without demographic read rights")
    void shouldThrowSecurityException_withoutDemographicReadRight() {
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"), (String) isNull())).thenReturn(false);

        assertThatThrownBy(() -> DemographicContactCreator.convertProfessionalContactAsProfessionalSpecialist(user, contact(7)))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_demographic)");
        verifyNoInteractions(specialists);
    }

    @Test
    @DisplayName("should build a new specialist with phone extension and trimmed number for a contact without an id")
    void shouldBuildNewSpecialist_forContactWithoutId() {
        ProfessionalContact submitted = contact(null);
        submitted.setWorkPhone(" 555-0100 ");
        submitted.setWorkPhoneExtension(" 12 ");
        submitted.setCpso(" 999998 ");

        ProfessionalSpecialist converted =
                DemographicContactCreator.convertProfessionalContactAsProfessionalSpecialist(user, submitted);

        verifyNoInteractions(specialists);
        assertThat(converted.getId()).isNull();
        assertThat(converted.getLastName()).isEqualTo("Consultant");
        assertThat(converted.getPhoneNumber()).isEqualTo("555-0100 ext 12");
        assertThat(converted.getReferralNo()).isEqualTo("999998");
        assertThat(converted.getFaxNumber()).isEqualTo("555-0101");
        assertThat(converted.getStreetAddress()).contains("1 FAKE Street").contains("Faketown");
    }

    @Test
    @DisplayName("should merge into the existing specialist and keep missing phone and number empty")
    void shouldMergeIntoExistingSpecialist_forPositiveId() {
        ProfessionalSpecialist existing = new ProfessionalSpecialist();
        existing.setStreetAddress("9 FAKE Old Road, Oldtown, B2B 2B2, ON, CA");
        existing.setPhoneNumber("555-0199");
        existing.setReferralNo("111111");
        when(specialists.find((Object) 7)).thenReturn(existing);
        ProfessionalContact submitted = contact(7);
        submitted.setWorkPhoneExtension("12");

        ProfessionalSpecialist converted =
                DemographicContactCreator.convertProfessionalContactAsProfessionalSpecialist(user, submitted);

        assertThat(converted).isSameAs(existing);
        assertThat(converted.getStreetAddress()).contains("1 FAKE Street").doesNotContain("Old Road");
        // No work phone: the extension alone is not stored, and nothing reads "null".
        assertThat(converted.getPhoneNumber()).isNull();
        assertThat(converted.getReferralNo()).isNull();
    }

    @Test
    @DisplayName("should keep the stored address text when the submitted address is unchanged")
    void shouldKeepStoredAddress_whenAddressUnchanged() {
        ProfessionalSpecialist existing = new ProfessionalSpecialist();
        existing.setStreetAddress(SpecialistContactAddress.write(null, contact(7)));
        // The entity setter normalises the text; compare with what it actually holds.
        String stored = existing.getStreetAddress();
        when(specialists.find((Object) 7)).thenReturn(existing);
        ProfessionalContact submitted = contact(7);
        submitted.setWorkPhone("555-0100");

        ProfessionalSpecialist converted =
                DemographicContactCreator.convertProfessionalContactAsProfessionalSpecialist(user, submitted);

        assertThat(converted.getStreetAddress()).isSameAs(stored);
        assertThat(converted.getPhoneNumber()).isEqualTo("555-0100");
    }
}
