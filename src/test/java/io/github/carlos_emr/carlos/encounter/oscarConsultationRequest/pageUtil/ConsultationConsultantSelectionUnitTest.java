/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.DemographicContact;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class ConsultationConsultantSelectionUnitTest {
    @Test
    void shouldResolveOnlyActivePatientSpecialist_forHealthCareTeam() {
        ProfessionalSpecialistDao specialists = mock(ProfessionalSpecialistDao.class);
        DemographicManager demographics = mock(DemographicManager.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        DemographicContact contact = new DemographicContact();
        contact.setDemographicNo(1);
        contact.setType(DemographicContact.TYPE_PROFESSIONALSPECIALIST);
        contact.setContactId("7");
        ProfessionalSpecialist specialist = new ProfessionalSpecialist();
        when(specialists.find((Object) 7)).thenReturn(specialist);
        when(demographics.getHealthCareMemberbyId(loggedInInfo, 12)).thenReturn(contact);

        assertThat(resolve(specialists, demographics, loggedInInfo).specialist()).isSameAs(specialist);
        assertThat(resolve(specialists, demographics, loggedInInfo).contact()).isSameAs(contact);
        contact.setDeleted(true);
        assertThat(resolve(specialists, demographics, loggedInInfo)).isNull();
        contact.setDeleted(false);
        contact.setActive(false);
        assertThat(resolve(specialists, demographics, loggedInInfo)).isNull();
        contact.setActive(true);
        contact.setDemographicNo(2);
        assertThat(resolve(specialists, demographics, loggedInInfo)).isNull();
        contact.setDemographicNo(1);
        contact.setType(DemographicContact.TYPE_PROVIDER);
        assertThat(resolve(specialists, demographics, loggedInInfo)).isNull();
        contact.setType(DemographicContact.TYPE_PROFESSIONALSPECIALIST);
        contact.setContactId("invalid");
        assertThat(resolve(specialists, demographics, loggedInInfo)).isNull();
        contact.setContactId("7");
        specialist.setDeleted(true);
        assertThat(resolve(specialists, demographics, loggedInInfo)).isNull();
        when(demographics.getHealthCareMemberbyId(loggedInInfo, 12)).thenReturn(null);
        assertThat(resolve(specialists, demographics, loggedInInfo)).isNull();
    }

    private ConsultationConsultantSelection resolve(ProfessionalSpecialistDao specialists,
            DemographicManager demographics, LoggedInInfo loggedInInfo) {
        return ConsultationConsultantSelection.resolve(specialists, demographics, loggedInInfo, 1, "12", true);
    }
}
