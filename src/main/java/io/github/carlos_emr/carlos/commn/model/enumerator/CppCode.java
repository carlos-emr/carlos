package io.github.carlos_emr.carlos.commn.model.enumerator;

import java.util.ArrayList;
import java.util.List;
/**
 * Enumeration representing the valid Clinical Patient Profile (CPP) sections
 * such as social history, medical history, and risk factors.
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
        return code;
    }
/**
     * Converts the enum values into a simple String array for legacy compatibility
     * with older Struts UI components that require array iteration.
     *
     * @return An array of string codes.
     */

    public static String[] toArray() {
        CppCode[] values = CppCode.values();
        String[] array = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            array[i] = values[i].getCode();
        }
        return array;
    }
/**
     * Returns all CPP code string values as a List to facilitate standard Java Collections usage.
     *
     * @return A list of the string codes.
     */

    public static List<String> toStringList() {
        // Maps the enum instances to their underlying code string representation.
        List<String> list = new ArrayList<>();
        for (CppCode cppCode : values()) {
            list.add(cppCode.getCode());
        }
        return list;
    }
}
