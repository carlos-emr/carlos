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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;

/** A caught mapper/writer rejection must not be upgraded into a serialized occurrence receipt. */
@Tag("unit")
@Tag("cds-export")
class CdsExportTypedWriterFailureFenceUnitTest {
    @Test
    void everyTypedWriterPoisonsTheDocumentWhenTheCallerCatchesItsInputFailure() {
        var writers =
                List.<CdsExportXmlComposer.Content>of(
                        xml -> new CdsExportDemographicXmlWriter().write(null, xml),
                        xml -> new CdsExportAllergyXmlWriter().write(null, xml),
                        xml -> new CdsExportMedicationXmlWriter().write(null, xml),
                        xml -> new CdsExportAppointmentXmlWriter().write(null, xml),
                        xml -> new CdsExportPublicProviderXmlWriter().write(null, xml, true));
        for (var writer : writers)
            assertThatThrownBy(
                            () ->
                                    compose(
                                            xml -> {
                                                try {
                                                    writer.write(xml);
                                                } catch (IOException
                                                        | RuntimeException deliberatelyCaught) {
                                                    /* Deliberately swallow the first refusal to prove the composer stays poisoned. */
                                                }
                                            }))
                    .isInstanceOf(IOException.class);
    }

    @Test
    void rejectedAllergyCannotBeReplacedAfterTheCallerCatchesTheFailure() throws Exception {
        var absent = new CdsExportDateDto(CdsExportDateDto.Precision.ABSENT, null);
        var rejected =
                new CdsExportAllergyDto(
                        null, null, null, null, null, absent, null, null, null, absent, null,
                        false);
        assertThatThrownBy(
                        () ->
                                compose(
                                        xml -> {
                                            try {
                                                new CdsExportAllergyXmlWriter()
                                                        .write(rejected, xml);
                                            } catch (IOException deliberatelyCaught) {
                                                /* Deliberately swallow the first refusal to prove the composer stays poisoned. */
                                            }
                                            xml.element(
                                                    "cds",
                                                    "OffendingAgentDescription",
                                                    "Invented replacement");
                                        }))
                .isInstanceOf(IOException.class);
    }

    @Test
    void rejectedMedicationCannotReturnAnEmissionReceiptAfterCaughtFailure() throws Exception {
        var absent = new CdsExportDateDto(CdsExportDateDto.Precision.ABSENT, null);
        var rejected =
                new CdsExportMedicationDto(
                        absent,
                        absent,
                        null,
                        null,
                        null,
                        Map.of(CdsExportMedicationDto.Field.DrugName, ""));
        assertThatThrownBy(
                        () ->
                                compose(
                                        xml -> {
                                            try {
                                                new CdsExportMedicationXmlWriter()
                                                        .write(rejected, xml);
                                            } catch (IOException deliberatelyCaught) {
                                                /* Deliberately swallow the first refusal to prove the composer stays poisoned. */
                                            }
                                        }))
                .isInstanceOf(IOException.class);
    }

    private void compose(CdsExportXmlComposer.Content content) throws IOException {
        var section =
                new CdsExportXmlComposer.Section(
                        new CdsExportIdentityDto(
                                "a".repeat(64), CdsCategory.DEMOGRAPHICS, "b".repeat(64)),
                        content);
        new CdsExportXmlComposer()
                .compose(
                        new ByteArrayOutputStream(),
                        "c".repeat(64),
                        "a".repeat(64),
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        Set.of(CdsCategory.DEMOGRAPHICS),
                        List.of(section).iterator(),
                        ignored -> {},
                        1L << 20,
                        () -> {});
    }
}
