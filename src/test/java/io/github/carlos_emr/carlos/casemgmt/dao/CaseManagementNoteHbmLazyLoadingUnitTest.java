/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.casemgmt.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the CaseManagementNote Hibernate mapping.
 *
 * @since 2026-05-19
 */
@DisplayName("CaseManagementNote HBM lazy loading")
@Tag("unit")
@Tag("casemgmt")
class CaseManagementNoteHbmLazyLoadingUnitTest {

    private static final Path HBM_PATH = Path.of("src", "main", "resources", "io", "github", "carlos_emr",
            "carlos", "casemgmt", "model", "casemgmt_note.hbm.xml");

    @Test
    @DisplayName("should keep provider issues and extend relationships lazy")
    void shouldKeepRelationshipsLazy_forCaseManagementNoteMapping() throws IOException {
        String hbm = Files.readString(HBM_PATH);

        assertThat(hbm).contains("""
                <many-to-one name="provider" class="io.github.carlos_emr.carlos.commn.model.Provider"
                \t\t\tcolumn="provider_no" update="false" not-found='ignore' insert="false"
                \t\t\tlazy="proxy" />""");
        assertThat(hbm).contains("<set name=\"issues\" table=\"casemgmt_issue_notes\" lazy=\"true\">");
        assertThat(hbm).contains("<set name=\"extend\" table=\"casemgmt_note_ext\" lazy=\"true\">");
    }
}
