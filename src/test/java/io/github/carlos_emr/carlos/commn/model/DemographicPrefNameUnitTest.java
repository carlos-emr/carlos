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
package io.github.carlos_emr.carlos.commn.model;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Transient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The preferred-name display value and its column mapping (issue #4835).
 *
 * @since 2026-10-21
 */
@DisplayName("Demographic preferred name")
@Tag("unit")
@Tag("demographic")
class DemographicPrefNameUnitTest {

    @Test
    void shouldShowTheAlias_whenOneIsSet() {
        Demographic demographic = new Demographic();
        demographic.setPrefName("FAKE-Pref");
        demographic.setAlias("FAKE-Alias");

        assertThat(demographic.getPrefName()).isEqualTo("FAKE-Alias");
    }

    @Test
    void shouldShowTheStoredPreferredName_whenThereIsNoAlias() {
        Demographic demographic = new Demographic();
        demographic.setPrefName("FAKE-Pref");
        demographic.setAlias("");

        assertThat(demographic.getPrefName()).isEqualTo("FAKE-Pref");
    }

    @Test
    @DisplayName("should copy the stored preferred name, not the alias the display getter answers")
    void shouldCopyTheStoredPreferredName_whenAnAliasIsSet() throws Exception {
        Demographic stored = new Demographic();
        stored.setPrefName("FAKE-Pref");
        stored.setAlias("FAKE-Alias");

        // The copy constructor copies bean properties, and getPrefName() answers the alias: a
        // copy saved as the patient's new version wrote the alias into pref_name.
        Demographic copy = new Demographic(stored);

        assertThat(storedPrefName(copy)).as("the pref_name value").isEqualTo("FAKE-Pref");
        assertThat(copy.getAlias()).as("the alias").isEqualTo("FAKE-Alias");
        assertThat(copy.getPrefName()).as("the name shown").isEqualTo("FAKE-Alias");
    }

    @Test
    @DisplayName("should copy the stored preferred name when there is no alias")
    void shouldCopyTheStoredPreferredName_whenThereIsNoAlias() throws Exception {
        Demographic stored = new Demographic();
        stored.setPrefName("FAKE-Pref");
        stored.setAlias("");

        Demographic copy = new Demographic(stored);

        assertThat(storedPrefName(copy)).as("the pref_name value").isEqualTo("FAKE-Pref");
        assertThat(copy.getAlias()).as("the alias").isEmpty();
    }

    @Test
    @DisplayName("should leave an unset preferred name unset in the copy when only an alias is set")
    void shouldLeaveThePreferredNameUnset_whenOnlyAnAliasIsSet() throws Exception {
        Demographic stored = new Demographic();
        stored.setPrefName(null);
        stored.setAlias("FAKE-Alias");

        Demographic copy = new Demographic(stored);

        assertThat(storedPrefName(copy)).as("the pref_name value").isNull();
        assertThat(copy.getAlias()).as("the alias").isEqualTo("FAKE-Alias");
    }

    /** The mapped field, which no getter answers once an alias is set. */
    private static String storedPrefName(Demographic demographic) throws Exception {
        Field field = Demographic.class.getDeclaredField("prefName");
        field.setAccessible(true);
        return (String) field.get(demographic);
    }

    @Test
    void shouldMapTheColumnToTheField_notTheDisplayGetter() throws Exception {
        Field field = Demographic.class.getDeclaredField("prefName");
        Method getter = Demographic.class.getMethod("getPrefName");

        assertThat(field.getAnnotation(Access.class).value()).isEqualTo(AccessType.FIELD);
        assertThat(field.getAnnotation(Column.class).name()).isEqualTo("pref_name");
        assertThat(getter.isAnnotationPresent(Transient.class)).isTrue();
        assertThat(getter.isAnnotationPresent(Column.class)).isFalse();
        assertThat(Demographic.class.getAnnotation(Access.class).value()).isEqualTo(AccessType.PROPERTY);
    }
}
