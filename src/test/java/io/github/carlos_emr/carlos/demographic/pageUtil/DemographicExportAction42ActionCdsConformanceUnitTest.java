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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;

import cds.OmdCdsDocument;
import io.github.carlos_emr.carlos.utility.LegacyXmlBeans;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.xmlbeans.XmlOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Legacy CDS 4 export conformance fixes: weight units and the patient alert (#3959) and
 * re-reading a large valid export for schema validation (#5845).
 *
 * @since 2026-11-13
 */
@Tag("unit")
@Tag("demographic")
@DisplayName("DemographicExportAction42Action CDS conformance")
class DemographicExportAction42ActionCdsConformanceUnitTest extends DemographicExportActionUnitTestBase {

    @TempDir Path root;

    @Test
    @DisplayName("should export a weight whose unit text was edited, and convert pounds exactly")
    void shouldExportWeightInKilograms_forEditedOrPoundUnits() {
        assertThat(DemographicExportAction42Action.weightInKg("in kg", "72.5")).isEqualTo("72.5");
        assertThat(DemographicExportAction42Action.weightInKg("KG", "72.5")).isEqualTo("72.5");
        assertThat(DemographicExportAction42Action.weightInKg("Kilograms", "72")).isEqualTo("72");
        assertThat(DemographicExportAction42Action.weightInKg("in lbs", "150")).isEqualTo("68.04");
        assertThat(DemographicExportAction42Action.weightInKg("lbs", "heavy")).isNull();
        assertThat(DemographicExportAction42Action.weightInKg("stone", "11")).isNull();
        assertThat(DemographicExportAction42Action.weightInKg(null, "72")).isNull();
    }

    @Test
    @DisplayName("should put the patient alert first in the note about the patient")
    void shouldIncludeThePatientAlert_inNoteAboutPatient() {
        assertThat(DemographicExportAction42Action.noteAboutPatient("Prefers mornings", " Latex allergy "))
                .isEqualTo("Alert: Latex allergy\nPrefers mornings");
        assertThat(DemographicExportAction42Action.noteAboutPatient("", "Latex allergy"))
                .isEqualTo("Alert: Latex allergy");
        assertThat(DemographicExportAction42Action.noteAboutPatient("Prefers mornings", ""))
                .isEqualTo("Prefers mornings");
    }

    @Test
    @DisplayName("should cap the document embedding limit and fall back to the default")
    void shouldBoundTheEmbeddedDocumentLimit_forIssue3968() {
        assertThat(action.maxEmbeddedDocumentBytes())
                .isEqualTo(DemographicExportAction42Action.DEFAULT_MAX_EMBEDDED_DOCUMENT_BYTES);
    }

    @Test
    @DisplayName("should validate a large chart with many escaped characters, and tell unreadable from invalid")
    void shouldValidateALargeEscapedChart_andDistinguishUnreadableFromInvalid() throws Exception {
        Path large = export("Notes &amp; results &lt;ok&gt; ".repeat(40_000));
        assertThat(action.checkExport(large.toFile()))
                .isEqualTo(DemographicExportAction42Action.ExportValidation.VALID);

        Path invalid = root.resolve("invalid.xml");
        Files.writeString(invalid, Files.readString(large).replace("<cds:Demographics>", "<cds:Unknown/><cds:Demographics>"));
        assertThat(action.checkExport(invalid.toFile()))
                .isEqualTo(DemographicExportAction42Action.ExportValidation.SCHEMA_INVALID);

        Path truncated = root.resolve("truncated.xml");
        String text = Files.readString(large);
        Files.writeString(truncated, text.substring(0, text.length() / 2));
        assertThat(action.checkExport(truncated.toFile()))
                .isEqualTo(DemographicExportAction42Action.ExportValidation.UNREADABLE);
    }

    private Path export(String escapedNote) throws Exception {
        var document = LegacyXmlBeans.newInstance(OmdCdsDocument.class, OmdCdsDocument.type);
        var record = document.addNewOmdCds().addNewPatientRecord();
        var demographics = record.addNewDemographics();
        var name = demographics.addNewNames().addNewLegalName();
        var first = name.addNewFirstName();
        first.setPart("FAKE");
        first.setPartType(cdsDt.PersonNamePartTypeCode.GIV);
        var last = name.addNewLastName();
        last.setPart("Export");
        last.setPartType(cdsDt.PersonNamePartTypeCode.FAMC);
        name.setNamePurpose(cdsDt.PersonNamePurposeCode.L);
        demographics.setDateOfBirth(Util.calDate("1980-01-02"));
        demographics.setGender(cdsDt.Gender.F);
        demographics.setUniqueVendorIdSequence("5845");
        demographics.addNewPersonStatusCode().setPersonStatusAsEnum(cdsDt.PersonStatus.A);
        record.addNewClinicalNotes().setMyClinicalNotesContent("PLACEHOLDER");
        Path file = root.resolve("export.xml");
        document.save(file.toFile(), new XmlOptions().setSaveAggressiveNamespaces());
        // Over 100,000 predefined entity references, the JDK's default ceiling.
        String xml = Files.readString(file, StandardCharsets.UTF_8).replace("PLACEHOLDER", escapedNote);
        assertThat(xml.split("&", -1).length - 1).isGreaterThan(100_000);
        Files.writeString(file, xml, StandardCharsets.UTF_8);
        return file;
    }
}
