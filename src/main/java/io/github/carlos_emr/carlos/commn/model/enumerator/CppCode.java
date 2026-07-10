package io.github.carlos_emr.carlos.commn.model.enumerator;

import java.util.ArrayList;
import java.util.List;
/**
 * Enumeration representing CPP (Cumulative Patient Profile) codes.
 * Standardized codes used to categorize various clinical, demographic, or historical patient data points.
 *
 * @since 2026-07-09
 */

public enum CppCode {
    OMEDS("OMeds"),
    SOC_HISTORY("SocHistory"),
    MED_HISTORY("MedHistory"),
    CONCERNS("Concerns"),
    FAM_HISTORY("FamHistory"),
    REMINDERS("Reminders"),
    RISK_FACTORS("RiskFactors"),
    OCULAR_MEDICATION("OcularMedication"),
    TICKLER_NOTE("TicklerNote");

    private final String code;

    CppCode(String code) {
        this.code = code;
    }

    public String getCode() {
        // Return the standardized code value for clinical mapping
        return code;
    }

    public static String[] toArray() {
        CppCode[] values = CppCode.values();
        String[] array = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            array[i] = values[i].getCode();
        }
        return array;
    }

    public static List<String> toStringList() {
        List<String> list = new ArrayList<>();
        for (CppCode cppCode : values()) {
            list.add(cppCode.getCode());
        }
        return list;
    }
}
