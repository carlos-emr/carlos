/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.github.carlos_emr.OscarDocumentCreator;
import io.github.carlos_emr.carlos.entities.MSPBill;
import net.sf.jasperreports.engine.JRParameter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The BC Accounting Reports' Rejection report (csv_ and pdf_rep_rej.jrxml), filled the way CreateBillingReport2Action
 * fills it: each rejected claim prints its whole claim number, patient, PHN and fee code, the patient's real birth
 * date, the column headings, and no "null" for a missing value (#4939). The PDF embeds its font, so it prints each
 * line as the fill measured it.
 *
 * <p>The text is compared with its whitespace removed, so a value that wraps onto a second line in the PDF still
 * counts as printed in full.</p>
 */
@DisplayName("BC Rejection report template")
@Tag("unit")
@Tag("billing")
@Tag("report")
class RejectionReportTemplateUnitTest {

    private static final String REPORTS = "oscar/oscarBilling/ca/bc/reports/";

    /** The report's own dd-MMM-yy dates; the page header's print time is followed by "at". */
    private static final Pattern REPORT_DATE = Pattern.compile("\\d{2}-[A-Z][a-z]{2}-\\d{2}(?!at)");

    /**
     * One FAKE rejected claim with full-length claim numbers and PHN, and a patient with no phone numbers. The
     * service times and to-day are left null, as billingmaster allows.
     */
    private static MSPBill rejectedClaim(String demoDOB) {
        MSPBill bill = new MSPBill();
        bill.billing_no = "1234567";
        bill.billMasterNo = "7654321";
        bill.demoName = "FAKE-Rejected,Patient";
        bill.demoNo = "123456";
        bill.demoDOB = demoDOB;
        bill.demoPhone = null;
        bill.demoPhone2 = null;
        bill.demoSex = "F";
        bill.serviceDate = "20251215";
        bill.rejectionDate = "20251220";
        bill.amount = "23.00";
        bill.code = "00100";
        bill.billingUnit = "1";
        bill.hin = "9000000000";
        bill.dx1 = "250";
        bill.billingtype = "MSP";
        bill.expString = "AA";
        bill.providerFirstName = "FAKE";
        bill.providerLastName = "Doctor";
        bill.serviceLocation = "A";
        bill.mvaCode = "N";
        return bill;
    }

    /** The report's text with whitespace removed, filled and exported through OscarDocumentCreator as the action does. */
    private static String render(String format, MSPBill bill) throws Exception {
        byte[] bytes = renderBytes(format, bill);
        String text;
        if (OscarDocumentCreator.PDF.equals(format)) {
            try (var pdf = Loader.loadPDF(bytes)) {
                text = new PDFTextStripper().getText(pdf);
            }
        } else {
            text = new String(bytes, StandardCharsets.UTF_8);
        }
        return text.replaceAll("\\s+", "");
    }

    private static byte[] renderBytes(String format, MSPBill bill) throws Exception {
        HashMap<String, Object> params = new HashMap<>();
        params.put("startDate", "2025-12-01");
        params.put("endDate", "2025-12-31");
        params.put("insurers", "ALL");
        params.put("account", "ALL");
        // The report prints its dates in the JVM's locale; English keeps the expected text the same on every machine.
        params.put(JRParameter.REPORT_LOCALE, Locale.ENGLISH);
        OscarDocumentCreator creator = new OscarDocumentCreator();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream design = creator.getDocumentStream(REPORTS + format + "_rep_rej.jrxml")) {
            assertThat(design).as("%s_rep_rej.jrxml is on the classpath", format).isNotNull();
            creator.fillDocumentStream(params, out, format, design, List.of(bill));
        }
        byte[] bytes = out.toByteArray();
        // fillDocumentStream logs and swallows a fill error, leaving the output empty.
        assertThat(bytes).as("the %s report rendered", format).isNotEmpty();
        return bytes;
    }

    private static List<String> reportDates(String text) {
        List<String> dates = new ArrayList<>();
        Matcher m = REPORT_DATE.matcher(text);
        while (m.find()) {
            dates.add(m.group());
        }
        return dates;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {OscarDocumentCreator.CSV, OscarDocumentCreator.PDF})
    @DisplayName("should print the whole claim number, patient, PHN and fee code")
    void shouldPrintWholeClaimCells_forRejectedClaim(String format) throws Exception {
        String text = render(format, rejectedClaim("19800102"));

        assertThat(text).contains("1234567-7654321", "FAKE-Rejected,Patient(123456)", "9000000000", "00100");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {OscarDocumentCreator.CSV, OscarDocumentCreator.PDF})
    @DisplayName("should print a name too long for its cell in full")
    void shouldPrintWholeName_whenNameWraps(String format) throws Exception {
        MSPBill bill = rejectedClaim("19800102");
        bill.demoName = "FAKE-PW2178788e5cf628dc,Workflow";
        bill.demoNo = "190";

        String text = render(format, bill);

        assertThat(text).contains("FAKE-PW2178788e5cf628dc,Workflow(190)", "1234567-7654321");
    }

    @Test
    @DisplayName("should embed the PDF's fonts, so each line prints as it was measured")
    void shouldEmbedFonts_forPdfReport() throws Exception {
        try (var pdf = Loader.loadPDF(renderBytes(OscarDocumentCreator.PDF, rejectedClaim("19800102")))) {
            var resources = pdf.getPage(0).getResources();
            assertThat(resources.getFontNames()).isNotEmpty();
            for (var name : resources.getFontNames()) {
                assertThat(resources.getFont(name).isEmbedded()).as("font %s is embedded", resources.getFont(name).getName()).isTrue();
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {OscarDocumentCreator.CSV, OscarDocumentCreator.PDF})
    @DisplayName("should print the patient's real birth date")
    void shouldPrintPatientBirthDate_forRejectedClaim(String format) throws Exception {
        String text = render(format, rejectedClaim("19800102"));

        // 1980-01-02 read as yyyyddmm (mm = minutes) would be 1980-01-01.
        assertThat(reportDates(text)).containsExactlyInAnyOrder("02-Jan-80", "15-Dec-25", "20-Dec-25");
    }

    static Stream<Arguments> blankBirthDates() {
        List<Arguments> cases = new ArrayList<>();
        for (String format : List.of(OscarDocumentCreator.CSV, OscarDocumentCreator.PDF)) {
            for (String dob : new String[] {null, "", "00000000"}) {
                cases.add(Arguments.of(format, dob));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}, birth date [{1}]")
    @MethodSource("blankBirthDates")
    @DisplayName("should leave an unknown birth date blank and still print the claim")
    void shouldLeaveBirthDateBlank_whenBillHasNoBirthDate(String format, String dob) throws Exception {
        String text = render(format, rejectedClaim(dob));

        assertThat(text).contains("1234567-7654321", "DOB:").doesNotContainPattern("\\d+/\\d+/\\d+");
        assertThat(reportDates(text)).containsExactlyInAnyOrder("15-Dec-25", "20-Dec-25");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {OscarDocumentCreator.CSV, OscarDocumentCreator.PDF})
    @DisplayName("should leave missing phones and service times blank, not print null")
    void shouldLeaveMissingValuesBlank_forPatientWithoutPhones(String format) throws Exception {
        String text = render(format, rejectedClaim("19800102"));

        assertThat(text).contains("Phone1:", "Phone2:", "Start:", "End:").doesNotContain("null");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {OscarDocumentCreator.CSV, OscarDocumentCreator.PDF})
    @DisplayName("should print the column headings")
    void shouldPrintColumnHeadings_forRejectionReport(String format) throws Exception {
        String text = render(format, rejectedClaim("19800102"));

        assertThat(text).contains("Patient", "Invoice#", "ServDate", "RejDate", "ID#", "Qty", "Item", "DX", "Balance");
    }
}
