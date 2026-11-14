/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import java.util.LinkedHashMap;

/**
 * A patient document as the exporter writes it for one stored Alert, for tests on the import
 * side that prove a transfer between two CARLOS installations.
 */
public final class CdsExportPatientAlertFixture {
    private CdsExportPatientAlertFixture() {}

    /** The composed, schema-valid patient document holding the alert's occurrence. */
    public static byte[] document(String alert) throws Exception {
        var values = new LinkedHashMap<String, String>();
        values.put("demographic_no", "27");
        values.put("cust3", alert);
        var mapped =
                CdsExportPatientAlertMapper.map(
                        27,
                        new CdsExportCapturedTextRowDto(
                                CdsExportPatientAlertMapper.TABLE,
                                CdsExportPatientTableSourceReader.key(27),
                                values));
        return CdsExportNativeHistoryMapperUnitTest.render(
                CdsCategory.ALERTS_AND_SPECIAL_NEEDS,
                writer -> CdsExportPatientAlertMapper.write(mapped, writer));
    }
}
