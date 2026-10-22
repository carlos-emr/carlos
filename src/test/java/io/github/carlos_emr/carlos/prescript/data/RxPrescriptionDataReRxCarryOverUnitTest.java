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
package io.github.carlos_emr.carlos.prescript.data;

import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Re-prescribing (ReRx) keeps the old card's Comment and Non-Authoritative tick (#5056).
 *
 * <p>Every ReRx route loads the saved drug with {@code getPrescription} or
 * {@code getPrescriptionIfPresent} and stages a copy with the re-prescribe
 * {@code newPrescription} overload, whose values pre-fill the restaged card. The loader did not
 * read the Non-Authoritative flag and the copy took neither value, so both were lost. The
 * long-term toggle saves the loaded prescription as a new drugs row through the same loader.
 *
 * @since 2026-10-22
 */
@Tag("unit")
@Tag("prescription")
@DisplayName("Re-prescribing carries the Comment and Non-Authoritative tick")
class RxPrescriptionDataReRxCarryOverUnitTest extends CarlosUnitTestBase {

    private static final int DRUG_ID = 50561;
    private static final int DEMOGRAPHIC_NO = 50562;
    private static final String PROVIDER_NO = "999998";

    private DrugDao drugDao;

    @BeforeEach
    void setUp() {
        drugDao = mock(DrugDao.class);
        registerMock(DrugDao.class, drugDao);
    }

    private Drug savedDrug(String comment, Boolean nonAuthoritative) {
        Drug drug = new Drug();
        drug.setId(DRUG_ID);
        drug.setDemographicId(DEMOGRAPHIC_NO);
        drug.setProviderNo(PROVIDER_NO);
        drug.setCustomName("FAKE Synthetic ReRx drug");
        drug.setSpecial("FAKE Synthetic ReRx drug 1 tab PO OD x 30 days");
        drug.setDuration("30");
        drug.setDurUnit("D");
        drug.setQuantity("30");
        drug.setScriptNo(77);
        Date lastYear = Date.from(LocalDate.now().minusYears(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        drug.setRxDate(lastYear);
        drug.setWrittenDate(lastYear);
        drug.setEndDate(lastYear);
        drug.setComment(comment);
        drug.setNonAuthoritative(nonAuthoritative);
        when(drugDao.find(DRUG_ID)).thenReturn(drug);
        return drug;
    }

    private static boolean isToday(Date date) {
        return date != null && date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().equals(LocalDate.now());
    }

    @ParameterizedTest(name = "[{index}] comment={0}, nonAuthoritative={1}, loaded by id={2}")
    @CsvSource(nullValues = "NULL", value = {
            "'FAKE keep with food',true,true,'FAKE keep with food',true",
            "'FAKE keep with food',true,false,'FAKE keep with food',true",
            "'FAKE keep with food',false,true,'FAKE keep with food',false",
            "NULL,true,false,NULL,true",
            // Drug.setComment stores a blank comment as null, so there is nothing to carry.
            "'  ',NULL,true,NULL,false",
            "NULL,NULL,false,NULL,false",
    })
    @DisplayName("should stage the old comment and Non-Authoritative tick on the ReRx copy")
    void shouldCarryCommentAndNonAuthoritative_whenRePrescribing(String comment, Boolean nonAuthoritative, boolean loadedById,
                                                                 String expectedComment, boolean expectedNonAuthoritative) {
        Drug drug = savedDrug(comment, nonAuthoritative);
        RxPrescriptionData data = new RxPrescriptionData();
        RxPrescriptionData.Prescription oldRx = loadedById
                ? data.getPrescription(DRUG_ID) : data.getPrescriptionIfPresent(DRUG_ID);

        RxPrescriptionData.Prescription staged = data.newPrescription(PROVIDER_NO, DEMOGRAPHIC_NO, oldRx);

        assertThat(staged.getComment()).isEqualTo(expectedComment);
        assertThat(staged.isNonAuthoritative()).isEqualTo(expectedNonAuthoritative);
        // The saved row is only read: the ReRx copy never edits the prescription it came from.
        assertThat(drug.getComment()).isEqualTo(expectedComment);
        assertThat(drug.isNonAuthoritative()).isEqualTo(expectedNonAuthoritative);
    }

    @Test
    @DisplayName("should still restage the copy as a new script written today that points at its source")
    void shouldResetDatesAndLinkSource_whenRePrescribingWithCommentAndNonAuthoritative() {
        savedDrug("FAKE keep with food", true);
        RxPrescriptionData data = new RxPrescriptionData();

        RxPrescriptionData.Prescription staged =
                data.newPrescription(PROVIDER_NO, DEMOGRAPHIC_NO, data.getPrescription(DRUG_ID));

        assertThat(staged.getDrugId()).isZero();
        assertThat(staged.getDrugReferenceId()).isEqualTo(DRUG_ID);
        assertThat(staged.getDemographicNo()).isEqualTo(DEMOGRAPHIC_NO);
        assertThat(staged.getProviderNo()).isEqualTo(PROVIDER_NO);
        assertThat(isToday(staged.getRxDate())).as("start date is today").isTrue();
        assertThat(isToday(staged.getWrittenDate())).as("written date is today").isTrue();
        assertThat(staged.getEndDate()).isNull();
        assertThat(staged.getComment()).isEqualTo("FAKE keep with food");
        assertThat(staged.isNonAuthoritative()).isTrue();
    }

    @ParameterizedTest(name = "[{index}] nonAuthoritative={0}, expected={1}")
    @CsvSource(nullValues = "NULL", value = {"true,true", "false,false", "NULL,false"})
    @DisplayName("should read the Non-Authoritative flag like the history list does")
    void shouldLoadNonAuthoritative_whenReadingSavedDrugById(Boolean nonAuthoritative, boolean expected) {
        Drug drug = savedDrug("FAKE keep with food", nonAuthoritative);
        RxPrescriptionData data = new RxPrescriptionData();

        assertThat(data.getPrescription(DRUG_ID).isNonAuthoritative()).isEqualTo(expected);
        assertThat(data.getPrescriptionIfPresent(DRUG_ID).isNonAuthoritative()).isEqualTo(expected);
        assertThat(data.toPrescription(drug, DEMOGRAPHIC_NO).isNonAuthoritative()).isEqualTo(expected);
    }

    @Test
    @DisplayName("should keep the Non-Authoritative tick and comment when the long-term toggle saves a new row")
    void shouldKeepNonAuthoritative_whenSavingLoadedPrescriptionAsNewRow() {
        savedDrug("FAKE keep with food", true);
        when(drugDao.getMaxPosition(DEMOGRAPHIC_NO)).thenReturn(4);
        // Save reads the generated id back, as the database would assign it.
        doAnswer(invocation -> {
            invocation.<Drug>getArgument(0).setId(DRUG_ID + 1);
            return null;
        }).when(drugDao).persist(any(Drug.class));
        RxPrescriptionData.Prescription loaded = new RxPrescriptionData().getPrescriptionIfPresent(DRUG_ID);
        loaded.setLongTerm(true);
        loaded.setShortTerm(false);

        assertThat(loaded.Save(loaded.getScript_no())).isTrue();
        assertThat(loaded.getDrugId()).isEqualTo(DRUG_ID + 1);

        ArgumentCaptor<Drug> persisted = ArgumentCaptor.forClass(Drug.class);
        verify(drugDao).persist(persisted.capture());
        assertThat(persisted.getValue().isNonAuthoritative()).isTrue();
        assertThat(persisted.getValue().getComment()).isEqualTo("FAKE keep with food");
        assertThat(persisted.getValue().getLongTerm()).isTrue();
    }
}
