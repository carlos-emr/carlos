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

    public BillingDiagnosisService(@Qualifier("billingmasterDAO") BillingmasterDAO bills, DiagnosticCodeDao codes) {
        this.bills = bills;
        this.codes = codes;
    }

    public record DiagnosisDto(String serviceDate, String code, String description) { }

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
