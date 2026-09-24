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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import cds.NewCategoryDocument.NewCategory;
import cds.PatientRecordDocument.PatientRecord;
import cdsDt.DiabetesComplicationScreening;
import cdsDt.ResidualInformation;
import cdsDt.ResidualInformation.DataElement;

/**
 * Maps the two diabetes neurological foot exams onto the single OntarioMD CDS slot for them.
 *
 * <p>CARLOS records two separate neurological exams on the diabetes flowsheets: {@code FTLS}
 * (10 g monofilament, loss of sensation) and {@code NRTF} (128 Hz tuning fork, vibration sense).
 * The CDS {@code DiabetesComplicationsScreening} element only has an {@code ExamCode}
 * (LOINC {@code 67536-3} for the neurological exam) and a {@code Date}; it has no free-text or
 * residual slot, so the two exams cannot be told apart from the care element alone.</p>
 *
 * <p>Chosen representation:</p>
 * <ul>
 *   <li>Both exams are exported as a standard {@code 67536-3} screening, so any conformant
 *       receiving EMR still sees that a neurological exam was performed on that date.</li>
 *   <li>Each NRTF reading additionally gets one {@code ResidualInfo} block in a patient-level
 *       {@code NewCategory} named {@link #CATEGORY_NAME}, carrying the measurement type, the exam
 *       date (lexical {@code yyyy-MM-dd}, as written in the screening) and the recorded result.</li>
 *   <li>On import, a {@code 67536-3} screening whose date matches an unclaimed NRTF marker is
 *       restored as NRTF with the recorded result; every other {@code 67536-3} screening is
 *       imported as FTLS with {@code "Yes"}, exactly as before. Markers are consumed one per
 *       screening, so an FTLS and an NRTF on the same day both survive the round trip.</li>
 * </ul>
 *
 * <p>Files without the marker (from other EMRs or older CARLOS builds) import unchanged.</p>
 *
 * @since 2026-09-24
 */
public final class CdsNeurologicalExam {

    /** Measurement type of the 10 g monofilament exam. */
    public static final String FTLS = "FTLS";

    /** Measurement type of the 128 Hz tuning fork exam. */
    public static final String NRTF = "NRTF";

    /** {@code NewCategory/CategoryName} that identifies the NRTF marker category. */
    public static final String CATEGORY_NAME = "CARLOS Diabetes Neurological Exam Detail";

    static final String CATEGORY_DESCRIPTION =
            "Marks DiabetesComplicationsScreening 67536-3 entries that record the 128 Hz tuning fork test (NRTF)";

    static final String ELEMENT_TYPE = "MeasurementType";
    static final String ELEMENT_DATE = "ExamDate";
    static final String ELEMENT_RESULT = "Result";
    private static final String DATA_TYPE = "string";

    private CdsNeurologicalExam() {
    }

    /**
     * Records that {@code screening} is an NRTF reading, creating the marker category on first use.
     *
     * @param patientRec the patient record being exported
     * @param category the marker category already created for this patient, or {@code null}
     * @param screening the {@code 67536-3} screening already added for the NRTF reading
     * @param result the NRTF measurement value; {@code null} is exported as empty
     * @return the marker category to pass in for the next NRTF reading of the same patient
     */
    public static NewCategory addNrtfMarker(PatientRecord patientRec, NewCategory category,
                                            DiabetesComplicationScreening screening, String result) {
        NewCategory target = category;
        if (target == null) {
            target = patientRec.addNewNewCategory();
            target.setCategoryName(CATEGORY_NAME);
            target.setCategoryDescription(CATEGORY_DESCRIPTION);
        }
        ResidualInformation ri = target.addNewResidualInfo();
        addElement(ri, ELEMENT_TYPE, NRTF);
        addElement(ri, ELEMENT_DATE, dateKey(screening));
        addElement(ri, ELEMENT_RESULT, result == null ? "" : result.trim());
        return target;
    }

    /**
     * Returns whether {@code category} is the CARLOS NRTF marker category.
     *
     * @param category a patient-level {@code NewCategory}; may be {@code null}
     * @return {@code true} when it is the marker written by {@link #addNrtfMarker}
     */
    public static boolean isNrtfMarkerCategory(NewCategory category) {
        return category != null && CATEGORY_NAME.equals(category.getCategoryName());
    }

    /**
     * Collects the NRTF markers of an imported patient record, keyed by exam date.
     *
     * @param categories the record's {@code NewCategory} array; may be {@code null}
     * @return a mutable map from exam date to the recorded results for that date, in file order
     */
    public static Map<String, Deque<String>> readNrtfMarkers(NewCategory[] categories) {
        Map<String, Deque<String>> markers = new HashMap<>();
        if (categories == null) {
            return markers;
        }
        for (NewCategory category : categories) {
            if (!isNrtfMarkerCategory(category)) {
                continue;
            }
            for (ResidualInformation ri : category.getResidualInfoArray()) {
                String type = null;
                String date = "";
                String result = "";
                for (DataElement element : ri.getDataElementArray()) {
                    String name = element.getName();
                    String content = element.getContent() == null ? "" : element.getContent().trim();
                    if (ELEMENT_TYPE.equals(name)) {
                        type = content;
                    } else if (ELEMENT_DATE.equals(name)) {
                        date = content;
                    } else if (ELEMENT_RESULT.equals(name)) {
                        result = content;
                    }
                }
                if (NRTF.equals(type)) {
                    markers.computeIfAbsent(date, k -> new ArrayDeque<>()).addLast(result);
                }
            }
        }
        return markers;
    }

    /**
     * Claims the NRTF marker for an imported {@code 67536-3} screening, if one is left for its date.
     *
     * @param markers the map returned by {@link #readNrtfMarkers}; the claimed entry is removed
     * @param screening the imported neurological exam screening
     * @return the recorded NRTF result (possibly empty) when the screening is an NRTF reading,
     *         or {@code null} when it should be imported as FTLS
     */
    public static String claimNrtfResult(Map<String, Deque<String>> markers, DiabetesComplicationScreening screening) {
        if (markers == null || markers.isEmpty()) {
            return null;
        }
        Deque<String> results = markers.get(dateKey(screening));
        if (results == null || results.isEmpty()) {
            return null;
        }
        return results.pollFirst();
    }

    /**
     * Uses the lexical date written in the XML rather than a {@code Calendar}, so export and
     * import compare the exact same text regardless of JVM time zone.
     */
    static String dateKey(DiabetesComplicationScreening screening) {
        if (screening == null || screening.xgetDate() == null) {
            return "";
        }
        String lexical = screening.xgetDate().getStringValue();
        return lexical == null ? "" : lexical.trim();
    }

    private static void addElement(ResidualInformation ri, String name, String content) {
        DataElement element = ri.addNewDataElement();
        element.setName(name);
        element.setDataType(DATA_TYPE);
        element.setContent(content);
    }
}
