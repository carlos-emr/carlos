/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ContactDao;
import io.github.carlos_emr.carlos.commn.dao.ContactSpecialtyDao;
import io.github.carlos_emr.carlos.commn.dao.CtlRelationshipsDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.DemographicContact;
import io.github.carlos_emr.carlos.commn.model.ProfessionalContact;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * How {@link DemographicContactCreator} attaches details to health care team links whose target
 * is a directory specialist or a contact row (issue #6202): a deleted specialist must read as
 * deleted, and a link whose target row is gone must not fail the whole team list.
 */
@Tag("unit")
@Tag("consultation")
class DemographicContactCreatorHealthCareTeamUnitTest extends CarlosUnitTestBase {

    // DemographicContactCreator looks targets up with Integer.parseInt, so the find(int)
    // overload is the one to stub.
    private ProfessionalSpecialistDao specialists;
    private ContactDao contacts;

    @BeforeEach
    void registerCollaborators() {
        specialists = createAndRegisterMock(ProfessionalSpecialistDao.class);
        contacts = createAndRegisterMock(ContactDao.class);
        createAndRegisterMock(ContactSpecialtyDao.class);
        createAndRegisterMock(CtlRelationshipsDao.class);
        createAndRegisterMock(ProviderDao.class);
        createAndRegisterMock(SecurityInfoManager.class);
    }

    private static DemographicContact link(int type, String contactId) {
        DemographicContact link = new DemographicContact();
        link.setType(type);
        link.setCategory(DemographicContact.CATEGORY_PROFESSIONAL);
        link.setContactId(contactId);
        link.setRole("");
        return link;
    }

    private static ProfessionalSpecialist specialist(boolean deleted) {
        ProfessionalSpecialist specialist = new ProfessionalSpecialist();
        specialist.setFirstName("FAKE");
        specialist.setLastName("Specialist");
        specialist.setReferralNo("");
        specialist.setDeleted(deleted);
        return specialist;
    }

    @Test
    @DisplayName("should report a deleted directory specialist's details as deleted")
    void shouldMarkDetailsDeleted_whenDirectorySpecialistIsDeleted() {
        when(specialists.find(7)).thenReturn(specialist(true));
        when(specialists.find(8)).thenReturn(specialist(false));

        DemographicContact deleted = DemographicContactCreator.addContactDetailsToDemographicContact(
                link(DemographicContact.TYPE_PROFESSIONALSPECIALIST, "7"));
        DemographicContact live = DemographicContactCreator.addContactDetailsToDemographicContact(
                link(DemographicContact.TYPE_PROFESSIONALSPECIALIST, "8"));

        assertThat(deleted.getDetails()).isInstanceOf(ProfessionalContact.class);
        assertThat(deleted.getDetails().isDeleted()).isTrue();
        assertThat(live.getDetails().isDeleted()).isFalse();
    }

    @Test
    @DisplayName("should leave details empty instead of throwing when a link's target row is gone")
    void shouldLeaveDetailsEmpty_whenTargetRowIsMissing() {
        ProfessionalContact present = new ProfessionalContact();
        present.setLastName("Present");
        when(contacts.find(11)).thenReturn(present);
        DemographicContact missingSpecialist = link(DemographicContact.TYPE_PROFESSIONALSPECIALIST, "9");
        DemographicContact missingContact = link(DemographicContact.TYPE_CONTACT, "10");
        ReflectionTestUtils.setField(missingSpecialist, "id", 901);
        ReflectionTestUtils.setField(missingContact, "id", 902);
        List<DemographicContact> team = new ArrayList<>(List.of(missingSpecialist, missingContact,
                link(DemographicContact.TYPE_CONTACT, "11")));

        List<DemographicContact> filled;
        List<String> warnings;
        try (LogCapture logs = LogCapture.forLogger(DemographicContactCreator.class)) {
            filled = DemographicContactCreator.addContactDetailsToDemographicContact(team);
            warnings = logs.messages();
        }

        assertThat(filled.get(0).getDetails()).isNull();
        assertThat(filled.get(1).getDetails()).isNull();
        assertThat(filled.get(2).getDetails()).isSameAs(present);
        // Only the link's surrogate id is logged: no name, contact id or other detail.
        assertThat(warnings).containsExactly(
                "Health care team link 901 names a directory specialist that no longer exists",
                "Health care team link 902 names a contact that no longer exists");
    }

    @Test
    @DisplayName("should clear an enriched link's name and details when its target row disappears")
    void shouldClearStaleDetails_whenTargetRowDisappearsBetweenLookups() {
        ProfessionalContact present = new ProfessionalContact();
        present.setFirstName("FAKE");
        present.setLastName("Present");
        when(contacts.find(12)).thenReturn(present);
        when(specialists.find(13)).thenReturn(specialist(false));
        DemographicContact contactLink = DemographicContactCreator.addContactDetailsToDemographicContact(
                link(DemographicContact.TYPE_CONTACT, "12"));
        DemographicContact specialistLink = DemographicContactCreator.addContactDetailsToDemographicContact(
                link(DemographicContact.TYPE_PROFESSIONALSPECIALIST, "13"));
        assertThat(contactLink.getDetails()).isSameAs(present);
        assertThat(specialistLink.getContactName()).isNotNull();

        // DemographicManager enriches the same instance again (MRP, member by role).
        when(contacts.find(12)).thenReturn(null);
        when(specialists.find(13)).thenReturn(null);
        DemographicContactCreator.addContactDetailsToDemographicContact(contactLink);
        DemographicContactCreator.addContactDetailsToDemographicContact(specialistLink);

        assertThat(contactLink.getDetails()).isNull();
        assertThat(contactLink.getContactName()).isNull();
        assertThat(specialistLink.getDetails()).isNull();
        assertThat(specialistLink.getContactName()).isNull();
    }
}
