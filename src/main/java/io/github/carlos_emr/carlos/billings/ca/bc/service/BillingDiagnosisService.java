/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.bc.service;

import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao;
import io.github.carlos_emr.carlos.commn.model.DiagnosticCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reads the diagnosis picker through managed persistence, without exporting JDBC resources. */
@Service
@Transactional(readOnly = true)
public class BillingDiagnosisService {
    private final BillingmasterDAO bills;
    private final DiagnosticCodeDao codes;

    /**
     * Creates the diagnosis reader from managed persistence collaborators.
     *
     * @param bills BillingmasterDAO for bounded diagnosis projections
     * @param codes DiagnosticCodeDao for existing diagnosis descriptions
     */
    public BillingDiagnosisService(@Qualifier("billingmasterDAO") BillingmasterDAO bills, DiagnosticCodeDao codes) {
        this.bills = bills;
        this.codes = codes;
    }

    /**
     * A distinct diagnosis with the date of its first occurrence in the selected bills.
     *
     * @param serviceDate String unparsed legacy service date, or null
     * @param code String nonblank diagnosis code with its original whitespace
     * @param description String diagnosis description, or null when the code is unknown
     */
    public record DiagnosisDto(String serviceDate, String code, String description) { }

    /**
     * Expands diagnoses from the patient's ten most recent non-deleted bills.
     * Null and blank codes are skipped; exact duplicate codes keep their first date.
     * Codes and dates remain untrimmed and unparsed. Results follow bill order and then
     * diagnosis-column order, with descriptions supplied by the existing code lookup.
     *
     * @param demographicNo String patient identifier bound by the billing query
     * @return {@code List<DiagnosisDto>} of distinct nonblank codes, empty when no bills match
     */
    public List<DiagnosisDto> findRecentDiagnoses(String demographicNo) {
        List<DiagnosisDto> diagnoses = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (BillingmasterDAO.DiagnosisRow row : bills.findRecentDiagnosisRows(demographicNo)) {
            // Do not trim returned codes: the old picker only trimmed to reject blank values.
            addDiagnosis(diagnoses, seen, row.serviceDate(), row.firstCode());
            addDiagnosis(diagnoses, seen, row.serviceDate(), row.secondCode());
            addDiagnosis(diagnoses, seen, row.serviceDate(), row.thirdCode());
        }
        return diagnoses;
    }

    private void addDiagnosis(List<DiagnosisDto> diagnoses, Set<String> seen, String date, String code) {
        if (code == null || code.trim().isEmpty() || !seen.add(code)) return;
        DiagnosticCode diagnosticCode = codes.findByCode(code);
        diagnoses.add(new DiagnosisDto(date, code, diagnosticCode == null ? null : diagnosticCode.getDescription()));
    }
}
