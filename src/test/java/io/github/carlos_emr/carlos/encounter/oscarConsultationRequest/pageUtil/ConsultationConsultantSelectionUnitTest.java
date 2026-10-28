/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.ContactDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicContactDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.Contact;
import io.github.carlos_emr.carlos.commn.model.DemographicContact;
import io.github.carlos_emr.carlos.commn.model.ProfessionalContact;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class ConsultationConsultantSelectionUnitTest {
    private final ProfessionalSpecialistDao specialists = mock(ProfessionalSpecialistDao.class);
    private final DemographicContactDao demographicContacts = mock(DemographicContactDao.class);
    private final ContactDao contacts = mock(ContactDao.class);

    private DemographicContact contact(int type) {
        DemographicContact contact = new DemographicContact();
        contact.setDemographicNo(1);
        contact.setType(type);
        contact.setCategory(DemographicContact.CATEGORY_PROFESSIONAL);
        contact.setContactId("7");
        when(demographicContacts.find((Object) 12)).thenReturn(contact);
        return contact;
    }

    @Test
    void shouldResolveOnlyActivePatientSpecialist_forHealthCareTeam() {
        DemographicContact contact = contact(DemographicContact.TYPE_PROFESSIONALSPECIALIST);
        ProfessionalSpecialist specialist = new ProfessionalSpecialist();
        when(specialists.find((Object) 7)).thenReturn(specialist);
        assertThat(resolve().specialist()).isSameAs(specialist);
        assertThat(resolve().contact()).isSameAs(contact);
        contact.setDeleted(true);
        assertThat(resolve()).isNull();
        contact.setDeleted(false);
        contact.setActive(false);
        assertThat(resolve()).isNull();
        contact.setActive(true);
        contact.setConsentToContact(false);
        assertThat(resolve()).isNull();
        contact.setConsentToContact(true);
        contact.setDemographicNo(2);
        assertThat(resolve()).isNull();
        contact.setDemographicNo(1);
        contact.setType(DemographicContact.TYPE_PROVIDER);
        assertThat(resolve()).isNull();
        contact.setType(DemographicContact.TYPE_PROFESSIONALSPECIALIST);
        contact.setContactId("invalid");
        assertThat(resolve()).isNull();
        contact.setContactId("7");
        specialist.setDeleted(true);
        assertThat(resolve()).isNull();
        when(demographicContacts.find((Object) 12)).thenReturn(null);
        assertThat(resolve()).isNull();
    }

    @Test
    void shouldResolveProfessionalContact_withoutCreatingSpecialistPlaceholder() {
        DemographicContact contact = contact(DemographicContact.TYPE_CONTACT);
        ProfessionalContact professional = new ProfessionalContact();
        professional.setFirstName("Nurse");
        professional.setLastName("Practitioner");
        when(contacts.find((Object) 7)).thenReturn(professional);
        assertThat(resolve().contact()).isSameAs(contact);
        assertThat(resolve().specialist()).isNull();
        assertThat(resolve().displayName()).contains("Practitioner");
        verifyNoInteractions(specialists);
        professional.setDeleted(true);
        assertThat(resolve()).isNull();
        when(contacts.find((Object) 7)).thenReturn(new Contact());
        assertThat(resolve()).isNull();
        when(contacts.find((Object) 7)).thenReturn(null);
        assertThat(resolve()).isNull();
        contact.setCategory(DemographicContact.CATEGORY_PERSONAL);
        assertThat(resolve()).isNull();
    }

    private ConsultationConsultantSelection resolve() {
        return ConsultationConsultantSelection.resolve(specialists, demographicContacts, contacts, 1, "12", true);
    }
}
