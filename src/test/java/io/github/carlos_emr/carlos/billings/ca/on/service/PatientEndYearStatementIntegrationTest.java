/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Real DAO/JDBC/Jasper/PDF coverage for the statement's invoice eligibility and totals. */
@Tag("integration")
@Tag("billing")
class PatientEndYearStatementIntegrationTest extends CarlosTestBase {
    @Autowired private BillingONCHeader1Dao headerDao;
    @Autowired private BillingONItemDao itemDao;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;
    private PatientEndYearStatementService service;
    private Demographic patient;

    @BeforeEach
    void setUp() {
        service = new PatientEndYearStatementService(headerDao, itemDao, mock(DemographicManager.class));
        patient = new Demographic();
        patient.setFirstName("Statement");
        patient.setLastName("Fixture");
        patient.setHin("1234567890");
        patient.setSex("F");
        entityManager.persist(patient);
        entityManager.flush();
    }

    private BillingONCHeader1 invoice(String program, String status, String date, String total, String paid) {
        var header = new BillingONCHeader1();
        header.setHeaderId(0);
        header.setDemographicNo(patient.getDemographicNo());
        header.setDemographicName("Fixture, Statement");
        header.setProviderNo("999998");
        header.setPayProgram(program);
        header.setStatus(status);
        header.setBillingDate(Date.valueOf(date));
        header.setBillingTime(Date.valueOf(date));
        header.setTotal(new BigDecimal(total));
        header.setPaid(new BigDecimal(paid));
        header.setAppointmentNo(0);
        header.setApptProviderNo("999998");
        header.setCreator("test");
        entityManager.persist(header);
        entityManager.flush();
        return header;
    }

    private void item(BillingONCHeader1 header, String code, String status, String fee) {
        var item = new BillingONItem();
        item.setCh1Id(header.getId());
        item.setServiceCode(code);
        item.setStatus(status);
        item.setFee(fee);
        item.setServiceCount("1");
        item.setServiceDate(header.getBillingDate());
        entityManager.persist(item);
        entityManager.flush();
    }

    @Test
    void shouldMatchHtmlAndPdf_whenInvoicesIncludeDeletedSettledAndOutOfRangeRows() throws Exception {
        var active = invoice("PAT", "O", "2025-01-01", "34.70", "25.00");
        var settled = invoice("PAT", "S", "2025-12-31", "45.25", "45.25");
        var legacy = invoice("PAT", null, "2025-06-01", "0.10", "0.00");
        item(active, "A007A", "O", "34.7");
        item(settled, "K030A", "S", "45.25");
        item(legacy, "A001A", null, "0.1");
        item(active, "DEL99", "D", "333.33");
        item(invoice("PAT", "D", "2025-06-15", "999.99", "888.88"), "DEL88", "O", "999.99");
        item(invoice("HCP", "O", "2025-06-15", "31.00", "0.00"), "HCP99", "O", "31.00");
        item(invoice("PAT", "O", "2024-12-31", "32.00", "0.00"), "OLD99", "O", "32.00");
        item(invoice("PAT", "O", "2026-01-01", "33.00", "0.00"), "NEW99", "O", "33.00");
        var otherPatient = invoice("PAT", "O", "2025-06-15", "44.00", "0.00");
        otherPatient.setDemographicNo(patient.getDemographicNo() + 1000);
        item(otherPatient, "OTH99", "O", "44.00");

        var result = service.aggregateInvoices(patient, Date.valueOf("2025-01-01"), Date.valueOf("2025-12-31"));
        assertThat(result.invoices()).extracting("invoiceNo").containsExactly(active.getId(), settled.getId(), legacy.getId());
        assertThat(result.invoices().get(0).invoiced()).isEqualTo("34.70");
        assertThat(result.invoices().get(0).paid()).isEqualTo("25.00");
        assertThat(result.invoices().get(0).services()).extracting("code").containsExactly("A007A");
        assertThat(result.invoices().get(1).services()).extracting("code").containsExactly("K030A");
        assertThat(result.invoices().get(2).services()).extracting("code").containsExactly("A001A");
        assertThat(result.summary().getCount()).isEqualTo("3");
        assertThat(result.summary().getInvoiced()).isEqualTo("80.05");
        assertThat(result.summary().getPaid()).isEqualTo("70.25");

        String text = pdfText(result);
        assertThat(text).contains("End Year Statement", "Fixture", "1234567890", "Invoiced:", "2025-01-01", "2025-12-31",
                "A007A", "K030A", "A001A", "34.70", "25.00", "80.05", "70.25")
                .doesNotContain("DEL99", "DEL88", "HCP99", "OLD99", "NEW99", "OTH99", "999.99", "888.88");
    }

    @Test
    void shouldProduceZeroTotalsAndAValidPdf_whenOnlyDeletedInvoicesExist() throws Exception {
        invoice("PAT", "D", "2025-06-15", "999.99", "888.88");
        var result = service.aggregateInvoices(patient, Date.valueOf("2025-01-01"), Date.valueOf("2025-12-31"));
        assertThat(result.invoices()).isEmpty();
        assertThat(result.summary().getCount()).isEqualTo("0");
        assertThat(result.summary().getInvoiced()).isEqualTo("0.00");
        assertThat(result.summary().getPaid()).isEqualTo("0.00");
        assertThat(pdfText(result)).contains("End Year Statement", "0.00").doesNotContain("999.99", "888.88");
    }

    @Test
    void shouldPrintBothInvoiceAndSummaryAmounts_withoutClippingLargeValues() throws Exception {
        invoice("PAT", "O", "2025-06-15", "1234567.89", "1234560.12");
        var result = service.aggregateInvoices(patient, Date.valueOf("2025-01-01"), Date.valueOf("2025-12-31"));
        String text = pdfText(result);
        assertThat(org.apache.commons.lang3.StringUtils.countMatches(text, "1234567.89")).isEqualTo(2);
        assertThat(org.apache.commons.lang3.StringUtils.countMatches(text, "1234560.12")).isEqualTo(2);
    }

    @Test
    void shouldKeepClaimExportSemantics_whenStatementIncludesSettledServices() {
        var header = invoice("PAT", "O", "2025-01-01", "10.00", "0.00");
        item(header, "A001A", "O", "5.00");
        item(header, "K030A", "S", "5.00");
        item(header, "DEL99", "D", "5.00");
        assertThat(itemDao.findByCh1IdsExcludingDeleted(List.of(header.getId())))
                .extracting(BillingONItem::getServiceCode).containsExactly("A001A", "K030A");
        assertThat(itemDao.findByCh1IdsExcludingDeletedAndSettled(List.of(header.getId())))
                .extracting(BillingONItem::getServiceCode).containsExactly("A001A");
        assertThat(itemDao.findByCh1IdsExcludingDeleted(List.of())).isEmpty();
        assertThat(itemDao.findByCh1IdsExcludingDeleted(null)).isEmpty();
    }

    private String pdfText(PatientEndYearStatementService.Result result) throws Exception {
        var response = new MockHttpServletResponse();
        service.writePdfResponse(response, "statement", result.summary(), "2025-01-01", "2025-12-31");
        byte[] bytes = response.getContentAsByteArray();
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getContentLength()).isEqualTo(bytes.length);
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment;filename=statement.pdf");
        assertThat(new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (var document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isPositive();
            return new PDFTextStripper().getText(document);
        }
    }
}
