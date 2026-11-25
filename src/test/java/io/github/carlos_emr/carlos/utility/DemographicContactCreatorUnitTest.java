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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package io.github.carlos_emr.carlos.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.Contact;
import io.github.carlos_emr.carlos.commn.model.ProfessionalContact;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import org.springframework.test.util.ReflectionTestUtils;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pins the collation contract of {@link DemographicContactCreator#byLastName}.
 *
 * <p>The comparator must use {@code toUpperCase(Locale.ROOT)} followed by {@code compareTo},
 * not {@code compareToIgnoreCase}: {@code String.toUpperCase} applies full case mapping so
 * {@code "ß"} becomes {@code "SS"}, whereas {@code compareToIgnoreCase} folds one
 * {@code char} at a time and leaves {@code "ß"} untouched, moving names such as
 * {@code "Weiß"} from sorting as {@code "WEISS"} to sorting after every Latin letter.
 * This ordering is user-visible via the contact search screen.
 *
 * <p>Extends {@link CarlosUnitTestBase} because {@code DemographicContactCreator} resolves two
 * collaborators through {@code SpringUtils.getBean} in its static initializer; without the
 * mocked registry, merely touching {@code byLastName} throws {@code ExceptionInInitializerError}.
 * The comparator itself never uses those collaborators, so plain mocks suffice.
 *
 * @since 2026-09-16
 */
@DisplayName("DemographicContactCreator.byLastName collation")
@Tag("unit")
class DemographicContactCreatorUnitTest extends CarlosUnitTestBase {

    private ProfessionalSpecialistDao specialistDao;
    private SecurityInfoManager security;
    private Object originalDao;
    private Object originalSecurity;

    @BeforeEach
    void registerStaticCollaborators() {
        specialistDao = createAndRegisterMock(ProfessionalSpecialistDao.class);
        security = createAndRegisterMock(SecurityInfoManager.class);
        // The creator captures both collaborators once, in its static initializer, so a class
        // loaded earlier by another test would hold that test's mocks. Point it at this test's.
        originalDao = ReflectionTestUtils.getField(DemographicContactCreator.class, "professionalSpecialistDao");
        originalSecurity = ReflectionTestUtils.getField(DemographicContactCreator.class, "securityInfoManager");
        ReflectionTestUtils.setField(DemographicContactCreator.class, "professionalSpecialistDao", specialistDao);
        ReflectionTestUtils.setField(DemographicContactCreator.class, "securityInfoManager", security);
    }

    @AfterEach
    void restoreStaticCollaborators() {
        ReflectionTestUtils.setField(DemographicContactCreator.class, "professionalSpecialistDao", originalDao);
        ReflectionTestUtils.setField(DemographicContactCreator.class, "securityInfoManager", originalSecurity);
    }

    @Test
    @DisplayName("should fold sharp s to double s so it sorts with SS rather than after Z")
    void shouldFoldSharpSToDoubleS_whenSortingByLastName() {
        Contact eszett = contact("Weiß");   // folds to WEISS
        Contact ta = contact("Wet");             // WET sorts after WEISS

        assertThat(DemographicContactCreator.byLastName.compare(eszett, ta)).isNegative();
        assertThat(DemographicContactCreator.byLastName.compare(ta, eszett)).isPositive();
        assertThat(DemographicContactCreator.byLastName.compare(eszett, contact("WEISS"))).isZero();
    }

    @Test
    @DisplayName("should ignore case when sorting by last name")
    void shouldIgnoreCase_whenSortingByLastName() {
        assertThat(DemographicContactCreator.byLastName.compare(contact("lovelace"), contact("LOVELACE"))).isZero();
        assertThat(DemographicContactCreator.byLastName.compare(contact("adams"), contact("Baker"))).isNegative();
    }

    @Test
    @DisplayName("should sort null last names first instead of throwing")
    void shouldSortNullLastNamesFirst_whenSortingByLastName() {
        List<Contact> contacts = new ArrayList<>();
        contacts.add(contact("Baker"));
        contacts.add(contact(null));
        contacts.add(contact("Adams"));

        contacts.sort(DemographicContactCreator.byLastName);

        assertThat(contacts.get(0).getLastName()).isNull();
        assertThat(contacts.get(1).getLastName()).isEqualTo("Adams");
        assertThat(contacts.get(2).getLastName()).isEqualTo("Baker");
        assertThat(DemographicContactCreator.byLastName.compare(contact(null), contact(null))).isZero();
    }

    @Test
    @DisplayName("should parse the stored specialist street address into contact fields")
    void shouldParseSpecialistAddress_whenBuildingContact() {
        ProfessionalSpecialist specialist = new ProfessionalSpecialist(5);
        specialist.setStreetAddress("1 FAKE St, Faketown, A1A 1A1, ON, Canada, ");
        specialist.setFirstName("FAKE-First");
        specialist.setLastName("FAKE-Last");
        specialist.setPhoneNumber("555-0100");
        specialist.setReferralNo("123456");

        Contact contact = DemographicContactCreator.buildContact((Object) specialist);

        assertThat(contact.getAddress()).isEqualTo("1 FAKE St");
        assertThat(contact.getCity()).isEqualTo("Faketown");
        assertThat(contact.getPostal()).isEqualTo("A1A 1A1");
        assertThat(contact.getProvince()).isEqualTo("ON");
        assertThat(contact.getCountry()).isEqualTo("Canada");
        assertThat(contact.getId()).isEqualTo(5);
        assertThat(contact.getWorkPhone()).isEqualTo("555-0100");
    }

    @Test
    @DisplayName("should keep an unparseable stored address whole as the street")
    void shouldKeepFreeTextAddressAsStreet_whenBuildingContact() {
        ProfessionalSpecialist specialist = new ProfessionalSpecialist(6);
        specialist.setStreetAddress("Suite 9, FAKE Medical Building");

        Contact contact = DemographicContactCreator.buildContact((Object) specialist);

        assertThat(contact.getAddress()).isEqualTo("Suite 9, FAKE Medical Building");
        assertThat(contact.getCity()).isEmpty();
        assertThat(contact.getCountry()).isEmpty();
    }

    @Test
    @DisplayName("should refuse the conversion without _demographic read rights")
    void shouldRefuseConversion_whenDemographicReadIsMissing() {
        LoggedInInfo info = new LoggedInInfo();
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"), nullable(String.class)))
                .thenReturn(false);

        assertThatThrownBy(() -> DemographicContactCreator.convertProfessionalContactAsProfessionalSpecialist(
                info, new ProfessionalContact()))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_demographic)");
    }

    @Test
    @DisplayName("should compose the phone extension, trim the referral number and write a new address")
    void shouldConvertContactFields_whenIdIsNotPositive() {
        grantDemographicRead();
        ProfessionalContact form = editorContact(null);
        form.setWorkPhone(" 555-0100 ");
        form.setWorkPhoneExtension(" 22 ");
        form.setCpso("  987654  ");

        ProfessionalSpecialist result = DemographicContactCreator.convertProfessionalContactAsProfessionalSpecialist(
                new LoggedInInfo(), form);

        assertThat(result.getPhoneNumber()).isEqualTo("555-0100 ext 22");
        assertThat(result.getReferralNo()).isEqualTo("987654");
        assertThat(result.getStreetAddress()).isEqualTo("1 FAKE St, Faketown, A1A 1A1, ON, Canada, ");
        assertThat(result.getFirstName()).isEqualTo("FAKE-First");
        assertThat(result.getLastName()).isEqualTo("FAKE-Last");
        assertThat(result.getEmailAddress()).isEqualTo("fake@example.invalid");
        assertThat(result.getFaxNumber()).isEqualTo("555-0101");
        assertThat(result.getSpecialtyType()).isEqualTo("FAKE-Specialty");
        assertThat(result.getPrivatePhoneNumber()).isEqualTo("555-0102");
        assertThat(result.getCellPhoneNumber()).isEqualTo("555-0103");
    }

    @Test
    @DisplayName("should keep the bare phone and null referral number when extension and cpso are absent")
    void shouldKeepBarePhoneAndNullReferral_whenExtensionAndCpsoAreAbsent() {
        grantDemographicRead();
        ProfessionalContact form = editorContact(null);
        form.setWorkPhone("555-0100");
        form.setWorkPhoneExtension("   ");
        form.setCpso(null);

        ProfessionalSpecialist bare = DemographicContactCreator.convertProfessionalContactAsProfessionalSpecialist(
                new LoggedInInfo(), form);
        assertThat(bare.getPhoneNumber()).isEqualTo("555-0100");
        assertThat(bare.getReferralNo()).isNull();

        form.setWorkPhone("  ");
        form.setWorkPhoneExtension("22");
        ProfessionalSpecialist none = DemographicContactCreator.convertProfessionalContactAsProfessionalSpecialist(
                new LoggedInInfo(), form);
        assertThat(none.getPhoneNumber()).isNull();
    }

    @Test
    @DisplayName("should merge edits into the stored specialist and keep an unchanged legacy address byte for byte")
    void shouldKeepLegacyAddressBytes_whenStoredSpecialistAddressIsUnchanged() {
        grantDemographicRead();
        String legacy = "1 FAKE St,Faketown ,A1A 1A1,ON,Canada,";
        ProfessionalSpecialist stored = new ProfessionalSpecialist(9);
        stored.setStreetAddress(legacy);
        when(specialistDao.find(9)).thenReturn(stored);

        ProfessionalSpecialist result = DemographicContactCreator.convertProfessionalContactAsProfessionalSpecialist(
                new LoggedInInfo(), editorContact(9));

        assertThat(result).isSameAs(stored);
        assertThat(result.getStreetAddress()).isEqualTo(legacy);
        assertThat(result.getLastName()).isEqualTo("FAKE-Last");
    }

    @Test
    @DisplayName("should write the edited address components in writer order when the stored address differs")
    void shouldWriteEditedAddress_whenStoredSpecialistAddressDiffers() {
        grantDemographicRead();
        ProfessionalSpecialist stored = new ProfessionalSpecialist(9);
        stored.setStreetAddress("Old FAKE Road, Oldtown, Z9Z 9Z9, BC, Canada, ");
        when(specialistDao.find(9)).thenReturn(stored);

        ProfessionalSpecialist result = DemographicContactCreator.convertProfessionalContactAsProfessionalSpecialist(
                new LoggedInInfo(), editorContact(9));

        assertThat(result.getStreetAddress()).isEqualTo("1 FAKE St, Faketown, A1A 1A1, ON, Canada, ");
    }

    private void grantDemographicRead() {
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"), nullable(String.class)))
                .thenReturn(true);
    }

    private static ProfessionalContact editorContact(Integer id) {
        ProfessionalContact form = new ProfessionalContact();
        form.setId(id);
        form.setFirstName("FAKE-First");
        form.setLastName("FAKE-Last");
        form.setEmail("fake@example.invalid");
        form.setFax("555-0101");
        form.setSpecialty("FAKE-Specialty");
        form.setResidencePhone("555-0102");
        form.setCellPhone("555-0103");
        form.setAddress("1 FAKE St");
        form.setCity("Faketown");
        form.setPostal("A1A 1A1");
        form.setProvince("ON");
        form.setCountry("Canada");
        return form;
    }

    private static Contact contact(String lastName) {
        Contact contact = new Contact();
        contact.setLastName(lastName);
        return contact;
    }
}
