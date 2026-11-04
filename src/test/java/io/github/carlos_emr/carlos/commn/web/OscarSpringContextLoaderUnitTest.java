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
package io.github.carlos_emr.carlos.commn.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pins the Spring XML files {@link OscarSpringContextLoader} builds the root context from.
 *
 * <p>Issue #3446: the OAuth JAX-RS servers in {@code applicationContextREST.xml} answered 404
 * because the loader read that file only when {@code ModuleNames} listed {@code REST}, and no
 * shipped configuration does. These tests keep it loaded for every {@code ModuleNames} value.
 *
 * @since 2026-10-08
 */
@DisplayName("OscarSpringContextLoader config location Tests")
@Tag("unit")
@Tag("rest")
class OscarSpringContextLoaderUnitTest {

    private static final String MAIN = "classpath:applicationContext.xml";
    private static final String REST = "classpath:applicationContextREST.xml";

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", ",", " , "})
    @DisplayName("should load the main and OAuth REST contexts when no module is listed")
    void shouldLoadMainAndRestContexts_whenNoModuleListed(String moduleNames) {
        assertThat(OscarSpringContextLoader.resolveConfigLocations(moduleNames))
                .containsExactly(MAIN, REST);
    }

    @Test
    @DisplayName("should append listed modules after the core contexts in the order given")
    void shouldAppendModulesAfterCoreContexts_inListedOrder() {
        assertThat(OscarSpringContextLoader.resolveConfigLocations("Fax,Caisi"))
                .containsExactly(MAIN, REST,
                        "classpath:applicationContextFax.xml",
                        "classpath:applicationContextCaisi.xml");
    }

    @Test
    @DisplayName("should load the OAuth REST context once when a legacy configuration lists REST")
    void shouldLoadRestContextOnce_whenLegacyConfigListsRest() {
        assertThat(OscarSpringContextLoader.resolveConfigLocations("REST,Caisi, REST"))
                .containsExactly(MAIN, REST, "classpath:applicationContextCaisi.xml");
    }

    @Test
    @DisplayName("should trim names and skip blank and repeated entries")
    void shouldTrimAndDeduplicate_withPaddedAndBlankEntries() {
        assertThat(OscarSpringContextLoader.resolveConfigLocations(" Caisi , ,HRM,Caisi "))
                .containsExactly(MAIN, REST,
                        "classpath:applicationContextCaisi.xml",
                        "classpath:applicationContextHRM.xml");
    }
}
