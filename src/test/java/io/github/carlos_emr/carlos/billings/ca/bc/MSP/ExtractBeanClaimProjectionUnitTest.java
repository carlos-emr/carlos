/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.billing.CA.BC.dao.LogTeleplanTxDao;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BcBillingRows.ExtractBillRow;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BcBillingRows.ExtractClaimRow;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.entities.Billingmaster;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * The legacy group Teleplan export ({@code NEW_BC_TELEPLAN=no}) formats each C02 claim record from a
 * column projection, not from the {@link Billingmaster} entity. A column the projection does not load
 * goes out blank without any error, as the claim comment (P58) did. These tests build the record through
 * the projection path and compare it, field for field, with the record the entity produces.
 */
@Tag("unit")
@Tag("billing")
@DisplayName("Legacy Teleplan export claim projection")
class ExtractBeanClaimProjectionUnitTest extends CarlosUnitTestBase {
    private static final int INVOICE = 41;
    /** Zero-based offset and width of P58, after P00 to P56 (147 characters). */
    private static final int P58_OFFSET = 147;
    private static final int P58_WIDTH = 20;

    private BillingmasterDAO masters;

    @BeforeEach
    void registerExtractDaos() {
        createAndRegisterMock(LogTeleplanTxDao.class);
        createAndRegisterMock(BillingDao.class);
        // The C02 checker validates the service location against the configured visit types.
        when(createAndRegisterMock(BillingServiceDao.class).findVisitTypes()).thenReturn(List.of("A", "O"));
        masters = createAndRegisterMock(BillingmasterDAO.class);
        when(masters.hasBcExtractInvoices()).thenReturn(true);
        when(masters.findBcExtractBills(eq("91234"), any(), any()))
                .thenReturn(List.of(new ExtractBillRow(INVOICE, "FAKE,PATIENT", "MSP")));
    }

    /** One claim with a different, recognisable value in every column the C02 record uses. */
    private static ExtractClaimRow claim(String claimComment) {
        return new ExtractClaimRow("123.45", 7654321, "9876543210", "20261015", "00100", "250", "401", "4660",
                "C02", "T1234", "81234", "91234", "FAPA", "66", "2", "07", "LT", "E", "09", "0", "17", "D", "A",
                "1", "11111", "2", "22222", "0815", "0830", "0915", "19700131", "0", claimComment, "Y", "12345678",
                "00000000000000000042", "33333", "44444", "AB", "000123456789", "19600229", "FAKEFIRST", "M",
                "FAKESURNAME", "F", "1 FAKE STREET", "UNIT 2", "FAKETOWN", "ALBERTA", "T2T2T2");
    }

    /** The same claim as the entity the export formatted before it read a projection. */
    private static Billingmaster entity(String claimComment) {
        Billingmaster bm = new Billingmaster();
        bm.setBillingNo(INVOICE);
        bm.setBillAmount("123.45");
        bm.setBillingmasterNo(7654321);
        bm.setPhn("9876543210");
        bm.setServiceDate("20261015");
        bm.setBillingCode("00100");
        bm.setDxCode1("250");
        bm.setDxCode2("401");
        bm.setDxCode3("4660");
        bm.setClaimcode("C02");
        bm.setDatacenter("T1234");
        bm.setPayeeNo("81234");
        bm.setPractitionerNo("91234");
        bm.setNameVerify("FAPA");
        bm.setDependentNum("66");
        bm.setBillingUnit("2");
        bm.setClarificationCode("07");
        bm.setAnatomicalArea("LT");
        bm.setAfterHour("E");
        bm.setNewProgram("09");
        bm.setPaymentMode("0");
        bm.setServiceToDay("17");
        bm.setSubmissionCode("D");
        bm.setServiceLocation("A");
        bm.setReferralFlag1("1");
        bm.setReferralNo1("11111");
        bm.setReferralFlag2("2");
        bm.setReferralNo2("22222");
        bm.setTimeCall("0815");
        bm.setServiceStartTime("0830");
        bm.setServiceEndTime("0915");
        bm.setBirthDate("19700131");
        bm.setCorrespondenceCode("0");
        bm.setClaimComment(claimComment);
        bm.setMvaClaimCode("Y");
        bm.setIcbcClaimNo("12345678");
        bm.setOriginalClaim("00000000000000000042");
        bm.setFacilityNo("33333");
        bm.setFacilitySubNo("44444");
        bm.setOinInsurerCode("AB");
        bm.setOinRegistrationNo("000123456789");
        bm.setOinBirthdate("19600229");
        bm.setOinFirstName("FAKEFIRST");
        bm.setOinSecondName("M");
        bm.setOinSurname("FAKESURNAME");
        bm.setOinSexCode("F");
        bm.setOinAddress("1 FAKE STREET");
        bm.setOinAddress2("UNIT 2");
        bm.setOinAddress3("FAKETOWN");
        bm.setOinAddress4("ALBERTA");
        bm.setOinPostalcode("T2T2T2");
        return bm;
    }

    /** The one C02 record the simulation builds for the invoice, without its line terminators. */
    private String exportedRecord(ExtractClaimRow claim) {
        when(masters.findBcExtractClaims(INVOICE)).thenReturn(List.of(claim));
        ExtractBean bean = new ExtractBean();
        bean.seteFlag("0");
        bean.setVSFlag(1);
        bean.setProviderNo("91234");

        bean.dbQuery();

        String value = bean.getValue();
        assertThat(value).startsWith("\n").endsWith("\r");
        return value.substring(1, value.length() - 1);
    }

    @Test
    @DisplayName("should write the claim comment in P58 of a record built from the projection")
    void shouldCarryClaimCommentInP58_whenRecordIsBuiltFromTheProjection() {
        String record = exportedRecord(claim("FAKE COMMENT 123"));

        assertThat(record).hasSize(424);
        assertThat(record.substring(P58_OFFSET, P58_OFFSET + P58_WIDTH)).isEqualTo("FAKE COMMENT 123    ");
        // Its neighbours are where they were: correspondence code (P56) before, MVA code (P60) after.
        assertThat(record.charAt(P58_OFFSET - 1)).isEqualTo('0');
        assertThat(record.charAt(P58_OFFSET + P58_WIDTH)).isEqualTo('Y');
    }

    @Test
    @DisplayName("should build the same record from the projection as from the claim entity, field for field")
    void shouldMatchTheEntityRecord_forEveryFieldOfTheClaim() {
        String fromProjection = exportedRecord(claim("FAKE COMMENT 123"));
        String fromEntity = new ExtractBean().getClaimDetailRecord(entity("FAKE COMMENT 123"), "1");

        assertThat(fromEntity).hasSize(424);
        assertThat(withoutSequence(fromProjection)).isEqualTo(withoutSequence(fromEntity));
        assertThat(fromProjection.substring(8, 15)).isEqualTo("0000001");
    }

    /**
     * The record without P04, the run's own sequence number (the simulation's is 1; a bean that has not
     * run has none). Every other character comes from the claim.
     */
    private static String withoutSequence(String record) {
        return record.substring(0, 8) + record.substring(15);
    }

    @Test
    @DisplayName("should keep the record length for a missing, short or over-long comment")
    void shouldKeepRecordLength_whateverTheCommentLength() {
        String none = exportedRecord(claim(null));
        assertThat(none).hasSize(424);
        assertThat(none.substring(P58_OFFSET, P58_OFFSET + P58_WIDTH)).isEqualTo(" ".repeat(20));

        String exact = exportedRecord(claim("12345678901234567890"));
        assertThat(exact).hasSize(424);
        assertThat(exact.substring(P58_OFFSET, P58_OFFSET + P58_WIDTH)).isEqualTo("12345678901234567890");

        // The entity path cut an over-long comment to the field width; the projection path does the same.
        String tooLong = exportedRecord(claim("12345678901234567890-OVERFLOW"));
        assertThat(withoutSequence(tooLong)).isEqualTo(withoutSequence(
                new ExtractBean().getClaimDetailRecord(entity("12345678901234567890-OVERFLOW"), "1")));
        assertThat(tooLong).hasSize(424);
        assertThat(tooLong.substring(P58_OFFSET, P58_OFFSET + P58_WIDTH)).isEqualTo("12345678901234567890");
        assertThat(tooLong.charAt(P58_OFFSET + P58_WIDTH)).isEqualTo('Y');
    }
}
