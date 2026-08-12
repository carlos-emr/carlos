package io.github.carlos_emr.carlos.commn.model.enumerator;

import java.util.ArrayList;
import java.util.List;
/**
 * Domain model representing CppCode data structures within the CARLOS EMR system, including state and relationships.
 *
 * <p>This class implements domain-specific functionality to support the CARLOS EMR platform,
 * ensuring backwards compatibility with legacy integrations and adherence to healthcare standards.</p>
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
        // Internal logic boundary for CppCode state management
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
