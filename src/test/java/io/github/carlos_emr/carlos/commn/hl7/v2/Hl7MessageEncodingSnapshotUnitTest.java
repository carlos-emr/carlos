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
package io.github.carlos_emr.carlos.commn.hl7.v2;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.GregorianCalendar;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.commn.model.Provider;

/**
 * Pins the HL7 v2 text CARLOS generates, as it was under HAPI HL7 v2 1.0.1 (issue #5414).
 *
 * <p>The prescription QR code carries an OMP^O09 built by {@link OmpO09Builder} and encoded by
 * {@link Hl7ParserUtils}; the ADT^A04 export is built by {@link HL7A04Data}. Pharmacies and
 * downstream systems read these bytes, so the library upgrade must not change escaping,
 * trimming, empty-field handling or segment layout. The synthetic inputs carry every delimiter,
 * HL7 formatting escapes, leading and trailing spaces and non-ASCII text. Timestamps taken from
 * the clock and the build stamp are masked before comparison.</p>
 *
 * <p>Snapshots were recorded with HAPI 1.0.1. Re-record after a reviewed change with
 * {@code -Dcarlos.hl7.snapshot.update=true}.</p>
 */
@Tag("unit")
@Tag("fast")
@Tag("hl7")
@DisplayName("HL7 message encoding snapshots")
class Hl7MessageEncodingSnapshotUnitTest {

    private static final Path SNAPSHOTS = Path.of("src", "test", "resources", "labs", "hapi-characterization",
            "encoding");
    private static final boolean UPDATE = Boolean.getBoolean("carlos.hl7.snapshot.update");

    /** Tricky text: delimiters, HL7 escapes and formatting codes, padding and non-ASCII letters. */
    private static final String TRICKY = "  A|B^C&D~E\\F \\.br\\ \\H\\hi\\N\\ \\X0D0A\\ Zoë Çôté 测试  ";

    @Test
    @DisplayName("should encode the prescription QR code message as recorded with HAPI 1.0.1")
    void shouldEncodePrescriptionQrCodeMessage_asRecordedWithHapi101() throws Exception {
        Clinic clinic = new Clinic();
        clinic.setClinicName("SYNTHETIC CLINIC " + TRICKY);
        clinic.setClinicAddress("1 FAKE ST " + TRICKY);
        clinic.setClinicCity("FAKETOWN");
        clinic.setClinicProvince("ON");
        clinic.setClinicPostal("A1A 1A1");
        clinic.setClinicPhone("555-0100");

        Provider provider = new Provider();
        provider.setProviderNo("999998");
        provider.setLastName("FAKE^DOCTOR");
        provider.setFirstName(TRICKY);
        provider.setTitle("DR");

        Prescription prescription = new Prescription();
        prescription.setProviderNo("999998");
        prescription.setDemographicId(1);
        prescription.setDatePrescribed(new GregorianCalendar(2026, 9, 29, 9, 15, 0).getTime());
        prescription.setComments("Rx comment " + TRICKY);

        Drug first = drug("SYNTHETICOL " + TRICKY, "FAKEBRAND", "1.5", "Take with food " + TRICKY);
        Drug second = drug("PLACEBOMYCIN", null, "2", null);
        second.setNoSubs(true);

        String encoded = Hl7ParserUtils.encode(
                OmpO09Builder.makeOmpO09(clinic, provider, demographic(), prescription, List.of(first, second)));

        assertMatchesSnapshot("omp-o09-prescription-qr.txt", maskVolatileFields(encoded));
    }

    @Test
    @DisplayName("should encode free-text dosing as recorded with HAPI 1.0.1 when building the QR code message")
    void shouldEncodeFreeTextDosingAsRecordedWithHapi101_whenBuildingQrCodeMessage() throws Exception {
        // Prescribers type ranges and fractions; the numeric HL7 fields must carry them as typed
        // rather than fail the whole QR code, as HAPI 1.0.1 did not check them while building.
        Drug ranged = drug("RANGEOL", null, "1-2", null);
        ranged.setQuantity("30 (thirty)");
        ranged.setDuration("7-10");
        Drug fraction = drug("HALFINOL", null, "1/2", null);
        fraction.setQuantity("");
        Clinic clinic = new Clinic();
        clinic.setClinicName("SYNTHETIC CLINIC");
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        provider.setLastName("FAKE");
        provider.setFirstName("DOCTOR");
        Prescription prescription = new Prescription();
        prescription.setDatePrescribed(new GregorianCalendar(2026, 9, 29, 9, 15, 0).getTime());

        String encoded = Hl7ParserUtils.encode(
                OmpO09Builder.makeOmpO09(clinic, provider, demographic(), prescription, List.of(ranged, fraction)));

        assertMatchesSnapshot("omp-o09-free-text-dosing.txt", maskVolatileFields(encoded));
    }

    @Test
    @DisplayName("should encode the ADT A04 export as recorded with HAPI 1.0.1")
    void shouldEncodeAdtA04Export_asRecordedWithHapi101() throws Exception {
        HL7A04Data data = new HL7A04Data(demographic());

        assertMatchesSnapshot("adt-a04-export.txt", maskVolatileFields(data.getA04Message()));
    }

    private static Demographic demographic() {
        Demographic demographic = new Demographic();
        demographic.setDemographicNo(42);
        demographic.setLastName("FAKE|SURNAME^" + TRICKY);
        demographic.setFirstName(TRICKY);
        demographic.setBirthDay(new GregorianCalendar(1980, 0, 2));
        demographic.setSex("F");
        demographic.setChartNo("SYN-0001");
        demographic.setHin("9999999901");
        demographic.setVer("AB");
        demographic.setHcType("ON");
        demographic.setEffDate(new GregorianCalendar(2020, 4, 6).getTime());
        demographic.setHcRenewDate(new GregorianCalendar(2030, 4, 6).getTime());
        demographic.setAddress("2 FAKE AVE " + TRICKY);
        demographic.setCity("Montréal");
        demographic.setProvince("QC");
        demographic.setPostal("H0H 0H0");
        demographic.setPhone("555-0101");
        demographic.setSpokenLanguage("French");
        demographic.setCitizenship("Canadian");
        return demographic;
    }

    private static Drug drug(String genericName, String brandName, String dosage, String special) {
        Drug drug = new Drug();
        drug.setGenericName(genericName);
        drug.setBrandName(brandName);
        drug.setDosage(dosage);
        drug.setUnit("TAB");
        drug.setUnitName("tablet");
        drug.setDrugForm("TABLET " + TRICKY);
        drug.setSpecialInstruction("Instruction " + TRICKY);
        drug.setRoute("PO");
        drug.setMethod("Take");
        drug.setQuantity("30");
        drug.setFreqCode("BID");
        drug.setDuration("15");
        drug.setDurUnit("D");
        drug.setRxDate(new GregorianCalendar(2026, 9, 29).getTime());
        drug.setEndDate(new GregorianCalendar(2026, 10, 13).getTime());
        drug.setSpecial(special);
        return drug;
    }

    /**
     * Masks values taken from the clock (message date/time, control ID, file stamp) and the build
     * stamp (SFT), which change on every run, so only library-controlled output is compared.
     */
    private static String maskVolatileFields(String encoded) {
        StringBuilder masked = new StringBuilder();
        for (String segment : encoded.split("\r", -1)) {
            String[] fields = segment.split("\\|", -1);
            if (segment.startsWith("MSH") && fields.length > 9) {
                fields[6] = "<clock>";
                fields[9] = fields[9].isEmpty() ? "" : "<clock>";
            } else if (segment.startsWith("EVN") && fields.length > 2) {
                fields[2] = "<clock>";
            } else if (segment.startsWith("SFT")) {
                for (int i = 2; i < fields.length; i++) {
                    if (i != 3) {
                        fields[i] = fields[i].isEmpty() ? "" : "<build>";
                    }
                }
            }
            masked.append(String.join("|", fields)).append("\\r\n");
        }
        return masked.toString();
    }

    private static void assertMatchesSnapshot(String name, String actual) throws Exception {
        Path snapshot = SNAPSHOTS.resolve(name);
        if (UPDATE) {
            Files.createDirectories(SNAPSHOTS);
            Files.writeString(snapshot, actual, StandardCharsets.UTF_8);
            return;
        }
        assertThat(snapshot).as("recorded snapshot %s", snapshot).exists();
        assertThat(actual).isEqualTo(Files.readString(snapshot, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("should mask only clock and build fields when masking a message")
    void shouldMaskOnlyClockAndBuildFields_whenMaskingMessage() {
        String message = "MSH|^~\\&|OSCAR|FAC|||20261029091500||OMP^O09^OMP_O09|20261029|P|2.6\r"
                + "SFT|OSCARMcMaster|1.0|OSCAR|BUILD123\r"
                + "PID|1||9999999901\r";

        assertThat(maskVolatileFields(message)).isEqualTo(
                "MSH|^~\\&|OSCAR|FAC|||<clock>||OMP^O09^OMP_O09|<clock>|P|2.6\\r\n"
                        + "SFT|OSCARMcMaster|<build>|OSCAR|<build>\\r\n"
                        + "PID|1||9999999901\\r\n"
                        + "\\r\n");
    }
}
