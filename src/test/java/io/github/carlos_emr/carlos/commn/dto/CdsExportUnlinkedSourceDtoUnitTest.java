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
package io.github.carlos_emr.carlos.commn.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The ledger safe code carries one table's count and nothing else, and reads back exactly. */
@Tag("unit")
@Tag("cds-export")
class CdsExportUnlinkedSourceDtoUnitTest {
    @Test
    void shouldRoundTripEveryReviewedInboxTable_throughItsLedgerSafeCode() {
        for (String table : CdsExportUnlinkedSourceDto.tables()) {
            var value = new CdsExportUnlinkedSourceDto(table, 43);
            assertThat(value.eventCode()).matches("CDS_EXPORT_[A-Z0-9_]{1,85}");
            assertThat(CdsExportUnlinkedSourceDto.fromEventCode(value.eventCode()))
                    .contains(value);
        }
        assertThat(new CdsExportUnlinkedSourceDto("document_review", 7).eventCode())
                .isEqualTo("CDS_EXPORT_UNLINKED_DOCUMENT_REVIEW_ROWS_7");
    }

    @Test
    void shouldRefuseAnUnreviewedTableOrEmptyCount_andIgnoreOtherCodes() {
        assertThatThrownBy(() -> new CdsExportUnlinkedSourceDto("casemgmt_note", 1))
                .hasMessage("CDS_EXPORT_UNLINKED_SOURCE_INVALID");
        assertThatThrownBy(() -> new CdsExportUnlinkedSourceDto("HRMDocument", 0))
                .hasMessage("CDS_EXPORT_UNLINKED_SOURCE_INVALID");
        assertThat(CdsExportUnlinkedSourceDto.fromEventCode("CDS_EXPORT_UNMAPPED_DATA")).isEmpty();
        assertThat(CdsExportUnlinkedSourceDto.fromEventCode("CDS_EXPORT_UNLINKED_CASEMGMT_NOTE_ROWS_1"))
                .isEmpty();
        assertThat(CdsExportUnlinkedSourceDto.fromEventCode(null)).isEmpty();
    }
}
