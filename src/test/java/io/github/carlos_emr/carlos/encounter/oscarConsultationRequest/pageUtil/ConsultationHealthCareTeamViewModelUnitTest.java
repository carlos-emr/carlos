/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.ContactDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicContactDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationServices;
import io.github.carlos_emr.carlos.commn.model.Contact;
import io.github.carlos_emr.carlos.commn.model.DemographicContact;
import io.github.carlos_emr.carlos.commn.model.ProfessionalContact;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins which health care team members a new consultation request offers (issue #6202): a member
 * without a college, MSP or billing number is offered and reported, not silently dropped, while
 * the members the save would turn away stay out of the list.
 */
@Tag("unit")
@Tag("consultation")
class ConsultationHealthCareTeamViewModelUnitTest {

    private static final Function<String, ConsultationServices> NO_SERVICES = role -> null;

    private static DemographicContact member(int id, int type, String number) {
        DemographicContact member = new DemographicContact();
        // DemographicContact has no id setter; the id is database-generated.
        ReflectionTestUtils.setField(member, "id", id);
        member.setDemographicNo(1);
        member.setType(type);
        member.setCategory(DemographicContact.CATEGORY_PROFESSIONAL);
        member.setContactId(String.valueOf(100 + id));
        member.setRole("FAKE role " + id);
        member.setConsentToContact(true);
        member.setActive(true);
        ProfessionalContact details = new ProfessionalContact();
        details.setFirstName("FAKE");
        details.setLastName("Member" + id);
        details.setCpso(number);
        member.setDetails(details);
        return member;
    }

    private static ConsultationServices service(int id, String description) {
        ConsultationServices service = new ConsultationServices();
        service.setServiceId(id);
        service.setServiceDesc(description);
        return service;
    }

    @Test
    @DisplayName("should offer members without a college or billing number and report them")
    void shouldOfferMember_withoutReferralNumber() {
        DemographicContact withNumber = member(1, DemographicContact.TYPE_CONTACT, "999998");
        DemographicContact dietitian = member(2, DemographicContact.TYPE_CONTACT, null);
        DemographicContact blankNumber = member(3, DemographicContact.TYPE_CONTACT, "   ");
        DemographicContact specialistWithoutReferralNo = member(4, DemographicContact.TYPE_PROFESSIONALSPECIALIST, "");

        var choices = ConsultationHealthCareTeamViewModel.from(
                List.of(withNumber, dietitian, blankNumber, specialistWithoutReferralNo), NO_SERVICES);

        assertThat(choices.getConsultants())
                .containsExactly(withNumber, dietitian, blankNumber, specialistWithoutReferralNo);
        assertThat(choices.getWithoutReferralNumber()).containsExactly(2, 3, 4);
        assertThat(choices.getWithoutConsentCount()).isZero();
    }

    @Test
    @DisplayName("should leave out the links the consultation save rejects")
    void shouldExcludeMembers_whenSaveWouldRejectThem() {
        DemographicContact deleted = member(1, DemographicContact.TYPE_CONTACT, "1");
        deleted.setDeleted(true);
        DemographicContact inactive = member(2, DemographicContact.TYPE_CONTACT, "1");
        inactive.setActive(false);
        DemographicContact internalProvider = member(3, DemographicContact.TYPE_PROVIDER, null);
        DemographicContact patientContact = member(4, DemographicContact.TYPE_DEMOGRAPHIC, "1");
        DemographicContact personal = member(5, DemographicContact.TYPE_CONTACT, "1");
        personal.setCategory(DemographicContact.CATEGORY_PERSONAL);
        DemographicContact deletedDetails = member(6, DemographicContact.TYPE_CONTACT, "1");
        deletedDetails.getDetails().setDeleted(true);
        DemographicContact plainContact = member(7, DemographicContact.TYPE_CONTACT, "1");
        plainContact.setDetails(new Contact());
        DemographicContact noDetails = member(8, DemographicContact.TYPE_CONTACT, "1");
        noDetails.setDetails(null);

        List<DemographicContact> team = new ArrayList<>(Arrays.asList(deleted, inactive, internalProvider,
                patientContact, personal, deletedDetails, plainContact, noDetails, null));
        var choices = ConsultationHealthCareTeamViewModel.from(team, NO_SERVICES);

        assertThat(choices.getConsultants()).isEmpty();
        assertThat(choices.getWithoutReferralNumber()).isEmpty();
        // None of these can be fixed by recording consent, so none is reported as missing consent.
        assertThat(choices.getWithoutConsentCount()).isZero();
    }

    @Test
    @DisplayName("should count members left out only because consent to contact is not recorded")
    void shouldCountMembers_withoutConsentToContact() {
        DemographicContact refused = member(1, DemographicContact.TYPE_CONTACT, "1");
        refused.setConsentToContact(false);
        DemographicContact unrecorded = member(2, DemographicContact.TYPE_PROFESSIONALSPECIALIST, null);
        unrecorded.setConsentToContact((Boolean) null);
        DemographicContact inactiveWithoutConsent = member(3, DemographicContact.TYPE_CONTACT, "1");
        inactiveWithoutConsent.setActive(false);
        inactiveWithoutConsent.setConsentToContact(false);
        DemographicContact offered = member(4, DemographicContact.TYPE_CONTACT, "1");

        var choices = ConsultationHealthCareTeamViewModel.from(
                List.of(refused, unrecorded, inactiveWithoutConsent, offered), NO_SERVICES);

        assertThat(choices.getConsultants()).containsExactly(offered);
        assertThat(choices.getWithoutConsentCount()).isEqualTo(2);
        assertThat(choices.getWithoutReferralNumber()).isEmpty();
    }

    @Test
    @DisplayName("should collect the consultation services that offered members' roles map to")
    void shouldCollectServices_forOfferedMembersOnly() {
        ConsultationServices cardiology = service(13, "Cardiology");
        ConsultationServices dietetics = service(14, "Dietetics");
        Map<String, ConsultationServices> byDescription = Map.of("Cardiology", cardiology, "Dietetics", dietetics);
        DemographicContact cardiologist = member(1, DemographicContact.TYPE_CONTACT, "1");
        cardiologist.setRole("Cardiology");
        DemographicContact secondCardiologist = member(2, DemographicContact.TYPE_PROFESSIONALSPECIALIST, "2");
        secondCardiologist.setRole("Cardiology");
        DemographicContact withoutConsent = member(3, DemographicContact.TYPE_CONTACT, null);
        withoutConsent.setRole("Dietetics");
        withoutConsent.setConsentToContact(false);
        DemographicContact unmapped = member(4, DemographicContact.TYPE_CONTACT, null);

        var choices = ConsultationHealthCareTeamViewModel.from(
                List.of(cardiologist, secondCardiologist, withoutConsent, unmapped), byDescription::get);

        assertThat(choices.getServices()).containsExactly(cardiology);
        assertThat(choices.getConsultants()).containsExactly(cardiologist, secondCardiologist, unmapped);
    }

    @Test
    @DisplayName("should treat a missing team as empty and expose unmodifiable views")
    void shouldReturnEmptyChoices_forNullTeam() {
        var choices = ConsultationHealthCareTeamViewModel.from(null, NO_SERVICES);

        assertThat(choices.getConsultants()).isEmpty();
        assertThat(choices.getServices()).isEmpty();
        assertThat(choices.getWithoutReferralNumber()).isEmpty();
        assertThat(choices.getWithoutConsentCount()).isZero();
        List<DemographicContact> consultants = choices.getConsultants();
        DemographicContact extra = new DemographicContact();
        assertThatThrownBy(() -> consultants.add(extra))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * Every member the form offers must be one the save accepts, or the user picks a consultant
     * and is told on submit to pick an active one. Drives the real save-side resolver with the
     * same members, including ones with no number.
     */
    @Test
    @DisplayName("should offer only members that ConsultationConsultantSelection accepts")
    void shouldOfferOnlyMembers_acceptedBySave() {
        DemographicContactDao links = mock(DemographicContactDao.class);
        ContactDao contacts = mock(ContactDao.class);
        ProfessionalSpecialistDao specialists = mock(ProfessionalSpecialistDao.class);
        List<DemographicContact> team = new ArrayList<>();
        int id = 1;
        for (int type : new int[] {DemographicContact.TYPE_PROVIDER, DemographicContact.TYPE_DEMOGRAPHIC,
                DemographicContact.TYPE_CONTACT, DemographicContact.TYPE_PROFESSIONALSPECIALIST}) {
            for (String number : new String[] {null, "", "999998"}) {
                for (boolean consent : new boolean[] {true, false}) {
                    DemographicContact member = member(id++, type, number);
                    member.setConsentToContact(consent);
                    team.add(member);
                    when(links.find((Object) member.getId())).thenReturn(member);
                    int target = Integer.parseInt(member.getContactId());
                    when(contacts.find((Object) target)).thenReturn((ProfessionalContact) member.getDetails());
                    when(specialists.find((Object) target)).thenReturn(new ProfessionalSpecialist());
                }
            }
        }

        var offered = ConsultationHealthCareTeamViewModel.from(team, NO_SERVICES).getConsultants();

        assertThat(offered).hasSize(6);
        for (DemographicContact member : team) {
            boolean accepted = ConsultationConsultantSelection.resolve(specialists, links, contacts, 1,
                    String.valueOf(member.getId()), true) != null;
            assertThat(offered.contains(member))
                    .as("member %s (type %s, number %s, consent %s)", member.getId(), member.getType(),
                            ((ProfessionalContact) member.getDetails()).getCpso(), member.isConsentToContact())
                    .isEqualTo(accepted);
        }
    }
}
