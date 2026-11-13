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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.commn.dao.CdsExportDataSummaryReadDao;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The accepted schema digests of a capture scope are derived from the packaged contracts once and
 * then reused by every read; a scope the contracts refuse is refused on every read.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportSourceSchemaValidatorUnitTest {
    private final CdsExportSourceSchemaValidator validator = new CdsExportSourceSchemaValidator();

    @Test
    void shouldReturnTheSameDigests_onEveryReadOfAScope() throws Exception {
        var columns = Set.copyOf(CdsExportDataSummaryReadDao.plan("tickler").columns());
        var first = validator.acceptedSha256("tickler", columns);
        // Another equal set, in another order and of another class, finds the same digests.
        var again = validator.acceptedSha256("tickler", new HashSet<>(columns));

        assertThat(first).isNotEmpty().allMatch(digest -> digest.matches("[0-9a-f]{64}"));
        assertThat(again).isEqualTo(first);
    }

    @Test
    void shouldRefuseAScopeOutsideItsContract_onEveryRead() throws Exception {
        var columns = new HashSet<>(CdsExportDataSummaryReadDao.plan("tickler").columns());
        columns.add("not_a_column");
        for (int read = 0; read < 2; read++)
            assertThatThrownBy(() -> validator.acceptedSha256("tickler", columns))
                    .isInstanceOf(IOException.class)
                    .hasMessage("CDS_EXPORT_SOURCE_SCHEMA_REQUIRES_REVIEW");
        for (int read = 0; read < 2; read++)
            assertThatThrownBy(() -> validator.acceptedSha256("not_a_reviewed_table", Set.of("id")))
                    .isInstanceOf(IOException.class);
    }
}
