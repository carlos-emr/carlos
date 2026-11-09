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
package io.github.carlos_emr.carlos.demographic.cds.contract;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class CdsNativeRowDigestUnitTest {
    private static final List<CdsNativeRowDigest.Column> COLUMNS =
            List.of(
                    new CdsNativeRowDigest.Column("id", "INT", 10, 0),
                            new CdsNativeRowDigest.Column("note", "MEDIUMTEXT", 16777215, 0),
                    new CdsNativeRowDigest.Column("absent", "VARCHAR", 10, 0),
                            new CdsNativeRowDigest.Column("empty", "VARCHAR", 10, 0));

    @Test
    void shouldMatchTheIndependentPythonVector_forTheUnchangedProtocol2Bytes() {
        var hash = CdsNativeRowDigest.hasher(COLUMNS);
        hash.append(false, 2, "670671cd97404156226e507973f2ab8330d3022ca96e0c93bdbdb320c41adcaf");
        hash.append(
                false,
                12,
                "D9F9AB3A08C8CE04A9E5A2971BC100F0A087AF7BD73908121753A70070AA229B"); // Existing
                                                                                     // receipt
                                                                                     // contract
                                                                                     // canonicalizes SQL hex case.
        hash.append(true, 0, null);
        hash.append(false, 0, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assertThat(hash.finish())
                .isEqualTo("8ed69a9427270643a2704375dc7afebbc6e35cc991ea3589fce34ab7b8230bc7");
        assertThatThrownBy(hash::finish).hasMessage("CDS_NATIVE_ROW_DIGEST_INVALID");
    }

    @Test
    void shouldRefuseMissingExtraOrInvalidCells_andRemainFailedIfTheCallerSwallowsTheError() {
        var missing = CdsNativeRowDigest.hasher(COLUMNS);
        assertThatThrownBy(missing::finish).hasMessage("CDS_NATIVE_ROW_DIGEST_INVALID");
        assertThatThrownBy(() -> missing.append(true, 0, null))
                .hasMessage("CDS_NATIVE_ROW_DIGEST_INVALID");
        var invalid = CdsNativeRowDigest.hasher(COLUMNS);
        assertThatThrownBy(() -> invalid.append(false, 1, "not-a-hash"))
                .hasMessage("CDS_NATIVE_ROW_DIGEST_INVALID");
        assertThatThrownBy(() -> invalid.append(true, 0, null))
                .hasMessage("CDS_NATIVE_ROW_DIGEST_INVALID");
        var extra = CdsNativeRowDigest.hasher(COLUMNS);
        for (int i = 0; i < 4; i++) extra.append(true, 0, null);
        assertThatThrownBy(() -> extra.append(true, 0, null))
                .hasMessage("CDS_NATIVE_ROW_DIGEST_INVALID");
        assertThatThrownBy(extra::finish).hasMessage("CDS_NATIVE_ROW_DIGEST_INVALID");
        assertThatThrownBy(
                        () ->
                                CdsNativeRowDigest.hasher(
                                        List.of(COLUMNS.getFirst(), COLUMNS.getFirst())))
                .hasMessage("CDS_NATIVE_ROW_COLUMNS_INVALID");
    }
}
