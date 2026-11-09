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
class CdsSchemaContentOrderUnitTest {
    @Test
    void shouldPreserveTheSelectedTelephoneBranch_insteadOfFlatteningTheSharedExtensionName()
            throws Exception {
        var type = CdsWireSchema.schemaModel().type("cds_dt", "phoneNumber");
        var order = new CdsSchemaContentOrder();
        var structured =
                order.order(
                        type,
                        List.of(
                                child("extension"),
                                child("exchange"),
                                child("number"),
                                child("areaCode")));
        assertThat(structured)
                .extracting(value -> value.name().local())
                .containsExactly("areaCode", "number", "extension", "exchange");
        assertThat(order.order(type, List.of(child("extension"), child("phoneNumber"))))
                .extracting(value -> value.name().local())
                .containsExactly("phoneNumber", "extension");
    }

    @Test
    void shouldRequireACompleteSingleChoice_andRefuseMissingDuplicateOrSkippedOccurrences() {
        var type = CdsWireSchema.schemaModel().type("cds_dt", "phoneNumber");
        var order = new CdsSchemaContentOrder();
        for (var invalid :
                List.of(
                        List.of(child("extension")),
                        List.of(child("phoneNumber"), child("areaCode"), child("number")),
                        List.of(child("phoneNumber"), child("phoneNumber")),
                        List.of(
                                new CdsSchemaContentOrder.Occurrence(
                                        new CdsSchemaModel.ExpandedName("cds_dt", "phoneNumber"),
                                        2)))) {
            assertThatThrownBy(() -> order.order(type, invalid))
                    .isInstanceOf(java.io.IOException.class);
        }
    }

    @Test
    void shouldPutPrimaryPhysicianBeforeEmail_usingTheActualDemographicSequence() throws Exception {
        var root = CdsWireSchema.schemaModel().element("cds", "OmdCds");
        var demographic = root.child("cds", "PatientRecord").child("cds", "Demographics");
        var names =
                List.of(
                        "SIN",
                        "Email",
                        "PrimaryPhysician",
                        "Gender",
                        "Names",
                        "DateOfBirth",
                        "UniqueVendorIdSequence",
                        "PersonStatusCode");
        var supplied =
                names.stream()
                        .map(
                                name ->
                                        new CdsSchemaContentOrder.Occurrence(
                                                new CdsSchemaModel.ExpandedName("cds", name), 1))
                        .toList();
        assertThat(new CdsSchemaContentOrder().order(demographic, supplied))
                .extracting(value -> value.name().local())
                .containsExactly(
                        "Names",
                        "DateOfBirth",
                        "Gender",
                        "UniqueVendorIdSequence",
                        "PrimaryPhysician",
                        "Email",
                        "PersonStatusCode",
                        "SIN");
        assertThatThrownBy(() -> demographic.content().children().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static CdsSchemaContentOrder.Occurrence child(String name) {
        return new CdsSchemaContentOrder.Occurrence(
                new CdsSchemaModel.ExpandedName("cds_dt", name), 1);
    }
}
