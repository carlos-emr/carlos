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

import static org.assertj.core.api.Assertions.*;

import io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineTestKeys;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Origin proof of an imported vaccine from its sealed import receipt (review finding F10). */
@Tag("unit")
@Tag("cds-export")
class CdsExportImmunizationImportReceiptsUnitTest {
    private static final int PATIENT = 27, ROW = 17;

    private static Set<Integer> prove(Map<String, String> unit) throws IOException {
        var proof =
                new CdsExportImmunizationImportReceipts.Proof(
                        PATIENT, Set.of(ROW, ROW + 1), CdsNativeBaselineTestKeys.keys());
        proof.accept(unit);
        return proof.proven();
    }

    @Test
    void shouldProveTheRow_whenAnAppliedImmunizationsUnitCarriesItsSealedWitness() throws Exception {
        assertThat(prove(CdsExportImmunizationImportReceiptFixture.unit(PATIENT, ROW, CdsNativeBaselineTestKeys.keys())))
                .containsExactly(ROW);
        // A witness sealed under a retired key that the installation still holds still proves.
        assertThat(
                        prove(
                                CdsExportImmunizationImportReceiptFixture.unit(
                                        PATIENT, ROW, CdsNativeBaselineTestKeys.rotated())))
                .containsExactly(ROW);
    }

    @Test
    void shouldProveNothing_whenTheReceiptWasRewrittenToNameAnotherRow() throws Exception {
        // The witness seal binds the original receipt digest; a recomputed digest cannot reuse it.
        assertThat(
                        prove(
                                CdsExportImmunizationImportReceiptFixture.forged(
                                        PATIENT, ROW, ROW + 1, CdsNativeBaselineTestKeys.keys())))
                .isEmpty();
    }

    @Test
    void shouldProveNothing_whenTheSealedWitnessIsTampered() throws Exception {
        var unit =
                new LinkedHashMap<>(
                        CdsExportImmunizationImportReceiptFixture.unit(
                                PATIENT, ROW, CdsNativeBaselineTestKeys.keys()));
        String sealed = unit.get("native_baseline");
        int at = sealed.indexOf("\"sealed\":\"") + 10;
        char flipped = sealed.charAt(at) == 'A' ? 'B' : 'A';
        unit.put("native_baseline", sealed.substring(0, at) + flipped + sealed.substring(at + 1));
        assertThat(prove(unit)).isEmpty();
        unit.put("native_baseline", "{\"protocol\":2}");
        assertThat(prove(unit)).isEmpty();
    }

    @Test
    void shouldProveNothing_forALegacyUnitWithoutAWitness() throws Exception {
        assertThat(prove(CdsExportImmunizationImportReceiptFixture.unwitnessed(PATIENT, ROW)))
                .isEmpty();
    }

    @Test
    void shouldProveNothing_whenTheWitnessRecordsAnotherPatient() throws Exception {
        assertThat(
                        prove(
                                CdsExportImmunizationImportReceiptFixture.unit(
                                        PATIENT + 1, ROW, CdsNativeBaselineTestKeys.keys())))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"EXPECTED", "REFUSED", "FAILED_BEFORE_WRITE", "committed"})
    void shouldProveNothing_forAUnitThatWasNotApplied(String state) throws Exception {
        assertThat(
                        prove(
                                CdsExportImmunizationImportReceiptFixture.unit(
                                        PATIENT,
                                        ROW,
                                        "Immunizations",
                                        state,
                                        CdsNativeBaselineTestKeys.keys())))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"Immunizations_FIELDS", "PastHealth", "AllergiesAndAdverseReactions", "immunizations"})
    void shouldProveNothing_forAUnitOfAnotherCategory(String category) throws Exception {
        assertThat(
                        prove(
                                CdsExportImmunizationImportReceiptFixture.unit(
                                        PATIENT,
                                        ROW,
                                        category,
                                        "ALREADY_COMPLETE",
                                        CdsNativeBaselineTestKeys.keys())))
                .isEmpty();
    }

    @Test
    void shouldProveNothing_forARowThatIsNotACandidate() throws Exception {
        assertThat(
                        prove(
                                CdsExportImmunizationImportReceiptFixture.unit(
                                        PATIENT, ROW + 5, CdsNativeBaselineTestKeys.keys())))
                .isEmpty();
    }

    @Test
    void shouldRefuseTheJob_whenTheInstallationKeyIsUnavailable() throws Exception {
        var unit =
                CdsExportImmunizationImportReceiptFixture.unit(
                        PATIENT, ROW, CdsNativeBaselineTestKeys.keys());
        var proof =
                new CdsExportImmunizationImportReceipts.Proof(
                        PATIENT, Set.of(ROW), CdsNativeBaselineTestKeys.none());
        assertThatThrownBy(() -> proof.accept(unit))
                .isInstanceOf(IOException.class)
                .hasMessage("CDS_EXPORT_NATIVE_BASELINE_KEY_UNAVAILABLE");
    }

    @Test
    void shouldNameNoRow_whenTheReceiptNamesTwoParentsOrADigestMismatch() throws Exception {
        var unit =
                CdsExportImmunizationImportReceiptFixture.unit(
                        PATIENT, ROW, CdsNativeBaselineTestKeys.keys());
        String references = unit.get("target_references");
        assertThat(
                        CdsExportImmunizationImportReceipts.named(
                                Set.of(ROW), "Immunizations", "COMMITTED", references, "0".repeat(64), true))
                .isNull();
        String doubled =
                references.replace(
                        "\"refs\":[",
                        "\"refs\":[{\"table\":\"preventions\",\"id\":\"18\",\"digest\":\""
                                + "a".repeat(64)
                                + "\"},");
        assertThat(
                        CdsExportImmunizationImportReceipts.named(
                                Set.of(ROW, ROW + 1),
                                "Immunizations",
                                "COMMITTED",
                                doubled,
                                CdsExportImmunizationImportReceiptFixture.sha(doubled),
                                true))
                .isNull();
        assertThat(
                        CdsExportImmunizationImportReceipts.named(
                                Set.of(ROW),
                                "Immunizations",
                                "COMMITTED",
                                references,
                                unit.get("target_digest"),
                                true))
                .isEqualTo(ROW);
    }

    @Test
    void shouldKeepTheProofOpaque_forPreventionReceiptLogging() {
        var proof =
                new CdsExportImmunizationImportReceipts.Proof(
                        PATIENT, Set.of(ROW), CdsNativeBaselineTestKeys.keys());
        assertThat(proof.toString()).doesNotContain("27", "17");
    }
}
