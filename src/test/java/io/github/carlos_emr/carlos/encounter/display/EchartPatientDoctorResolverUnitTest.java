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
package io.github.carlos_emr.carlos.encounter.display;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ContactDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.Contact;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicContact;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.demographic.util.DemographicXml;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers where the Family Doctor and Referring Doctor panels find their names: the master record's
 * Referral Doctor field, the patient's professional contacts by role, and the legacy family_doc
 * element, in that priority.
 */
@Tag("unit")
@Tag("fast")
@Tag("encounter")
@DisplayName("EchartPatientDoctorResolver")
class EchartPatientDoctorResolverUnitTest extends CarlosUnitTestBase {

    private static final int DEMOGRAPHIC_NO = 42;

    private DemographicManager demographicManager;
    private ProviderDao providerDao;
    private ContactDao contactDao;
    private ProfessionalSpecialistDao professionalSpecialistDao;
    private EchartPatientDoctorResolver resolver;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        demographicManager = mock(DemographicManager.class);
        providerDao = mock(ProviderDao.class);
        contactDao = mock(ContactDao.class);
        professionalSpecialistDao = mock(ProfessionalSpecialistDao.class);
        loggedInInfo = mock(LoggedInInfo.class);
        resolver = new EchartPatientDoctorResolver(demographicManager, providerDao, contactDao, professionalSpecialistDao);
    }

    @Nested
    @DisplayName("referring doctor")
    class ReferringDoctor {

        @Test
        @DisplayName("should take the master record's Referral Doctor without reading contacts")
        void shouldTakeReferralDoctor_fromMasterRecord() {
            Demographic demographic = patient(DemographicXml.familyDoctor("123456", "FAKE-Ref, FAKE-Rita", null));

            assertThat(resolver.referringDoctorName(loggedInInfo, demographic)).isEqualTo("FAKE-Ref, FAKE-Rita");
            verify(demographicManager, never()).getDemographicContacts(any(), any());
        }

        @Test
        @DisplayName("should fall back to an active Referring Doctor contact")
        void shouldFallBackToContact_whenNoReferralDoctor() {
            ProfessionalSpecialist specialist = new ProfessionalSpecialist();
            specialist.setLastName("FAKE-Spec");
            specialist.setFirstName("FAKE-Sam");
            when(professionalSpecialistDao.find(Integer.valueOf(7))).thenReturn(specialist);
            contacts(contact("Referring Doctor", DemographicContact.TYPE_PROFESSIONALSPECIALIST, "7", true));

            assertThat(resolver.referringDoctorName(loggedInInfo, patient(""))).isEqualTo("FAKE-Spec, FAKE-Sam");
        }

        @Test
        @DisplayName("should return empty when nothing is recorded")
        void shouldReturnEmpty_whenNothingRecorded() {
            contacts();
            assertThat(resolver.referringDoctorName(loggedInInfo, patient(""))).isEmpty();
            assertThat(resolver.referringDoctorName(loggedInInfo, null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("family doctor")
    class FamilyDoctor {

        @Test
        @DisplayName("should take an active Family Doctor contact that is an internal provider")
        void shouldTakeFamilyDoctorContact_whenInternalProvider() {
            Provider provider = new Provider();
            provider.setLastName("FAKE-Fam");
            provider.setFirstName("FAKE-Fiona");
            when(providerDao.getProvider("101")).thenReturn(provider);
            contacts(contact("Family Doctor", DemographicContact.TYPE_PROVIDER, "101", true));

            assertThat(resolver.familyDoctorName(loggedInInfo, patient(""))).isEqualTo(provider.getFormattedName());
        }

        @Test
        @DisplayName("should accept the Family Physician role and an external contact")
        void shouldAcceptFamilyPhysicianRole_forExternalContact() {
            Contact contact = new Contact();
            contact.setLastName("FAKE-Ext");
            contact.setFirstName("FAKE-Eli");
            when(contactDao.find(Integer.valueOf(9))).thenReturn(contact);
            contacts(contact(" Family Physician ", DemographicContact.TYPE_CONTACT, "9", true));

            assertThat(resolver.familyDoctorName(loggedInInfo, patient(""))).isEqualTo("FAKE-Ext,FAKE-Eli");
        }

        @Test
        @DisplayName("should skip personal contacts, other roles, and contacts whose row is gone")
        void shouldSkipPersonalOtherRoleAndMissingContacts() {
            DemographicContact personal = contact("Family Doctor", DemographicContact.TYPE_CONTACT, "9", true);
            personal.setCategory(DemographicContact.CATEGORY_PERSONAL);
            Contact named = new Contact();
            named.setLastName("FAKE-Personal");
            named.setFirstName("FAKE-Pat");
            when(contactDao.find(Integer.valueOf(9))).thenReturn(named);
            contacts(personal,
                    contact("Specialist", DemographicContact.TYPE_CONTACT, "9", true),
                    contact("Family Doctor", DemographicContact.TYPE_PROFESSIONALSPECIALIST, "not-a-number", true),
                    contact("Family Doctor", DemographicContact.TYPE_PROFESSIONALSPECIALIST, "8", true));

            assertThat(resolver.familyDoctorName(loggedInInfo, patient(""))).isEmpty();
        }

        @Test
        @DisplayName("should read only the manager's active contacts and never unbox a legacy NULL active flag")
        void shouldNotUnboxNullActiveFlag_forLegacyContact() throws Exception {
            Provider provider = new Provider();
            provider.setLastName("FAKE-Fam");
            provider.setFirstName("FAKE-Fiona");
            when(providerDao.getProvider("101")).thenReturn(provider);
            DemographicContact legacy = contact("Family Doctor", DemographicContact.TYPE_PROVIDER, "101", true);
            java.lang.reflect.Field active = DemographicContact.class.getDeclaredField("active");
            active.setAccessible(true);
            active.set(legacy, null);
            contacts(legacy);

            assertThat(resolver.familyDoctorName(loggedInInfo, patient(""))).isEqualTo(provider.getFormattedName());
            verify(demographicManager, never()).getDemographicContacts(any(), any(), any());
        }

        @Test
        @DisplayName("should fall back to the legacy family_doc element of the master record")
        void shouldFallBackToFamilyDocElement_whenNoContact() {
            contacts();
            Demographic demographic = patient(DemographicXml.familyDoctor("", "", "FAKE-Legacy, FAKE-Lee"));

            assertThat(resolver.familyDoctorName(loggedInInfo, demographic)).isEqualTo("FAKE-Legacy, FAKE-Lee");
        }
    }

    private void contacts(DemographicContact... contacts) {
        when(demographicManager.getDemographicContacts(loggedInInfo, Integer.valueOf(DEMOGRAPHIC_NO)))
                .thenReturn(List.of(contacts));
    }

    /** The manager's active-contacts query has already dropped inactive rows; {@code active} is kept for realism. */
    private static DemographicContact contact(String role, int type, String contactId, boolean active) {
        DemographicContact contact = new DemographicContact();
        contact.setRole(role);
        contact.setType(type);
        contact.setContactId(contactId);
        contact.setActive(active);
        contact.setCategory(DemographicContact.CATEGORY_PROFESSIONAL);
        return contact;
    }

    private static Demographic patient(String familyDoctorXml) {
        Demographic demographic = new Demographic();
        demographic.setDemographicNo(DEMOGRAPHIC_NO);
        demographic.setFamilyDoctor(familyDoctorXml);
        return demographic;
    }
}
