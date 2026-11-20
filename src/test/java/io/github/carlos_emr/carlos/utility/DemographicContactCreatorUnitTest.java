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

import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.Contact;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.ArrayList;
import java.util.List;
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

    @BeforeEach
    void registerStaticCollaborators() {
        createAndRegisterMock(ProfessionalSpecialistDao.class);
        createAndRegisterMock(SecurityInfoManager.class);
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

    private static Contact contact(String lastName) {
        Contact contact = new Contact();
        contact.setLastName(lastName);
        return contact;
    }
}
