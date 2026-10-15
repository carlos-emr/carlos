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

import java.util.HashMap;
import java.util.Map;

import cds.CareElementsDocument.CareElements;
import cds.NewCategoryDocument.NewCategory;
import cds.PatientRecordDocument.PatientRecord;
import cdsDt.DiabetesComplicationScreening;
import cdsDt.DiabetesComplicationScreening.ExamCode;
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
 *       {@code NewCategory} named {@link #CATEGORY_NAME}. The block carries the measurement type,
 *       the screening's <em>ordinal</em> (its 0-based position among the record's {@code 67536-3}
 *       screenings, in document order), the exam date (lexical {@code yyyy-MM-dd}, as written in
 *       the screening) and the recorded result.</li>
 *   <li>On import, the importer counts {@code 67536-3} screenings in the same document order.
 *       A screening is restored as NRTF only when a marker names its ordinal <em>and</em> the
 *       marker's date matches the screening's date. Every other {@code 67536-3} screening is
 *       imported as FTLS with {@code "Yes"}, exactly as before.</li>
 * </ul>
 *
 * <p>The per-screening ordinal is what keeps an FTLS and an NRTF recorded on the same day apart,
 * whichever order the exporter wrote them in. Requiring the date to match as well means a file
 * whose screenings were reordered or dropped by another system falls back to FTLS rather than
 * attaching an NRTF result to the wrong exam. Markers that match no screening are ignored, and
 * files without the marker (from other EMRs or older CARLOS builds) import unchanged.</p>
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
    static final String ELEMENT_ORDINAL = "ScreeningOrdinal";
    static final String ELEMENT_DATE = "ExamDate";
    static final String ELEMENT_RESULT = "Result";
    private static final String DATA_TYPE = "string";

    private CdsNeurologicalExam() {
    }

    /**
     * Records that {@code screening} is an NRTF reading, creating the marker category on first use.
     *
     * <p>Must be called right after {@code screening} was appended to the record and given the
     * {@code 67536-3} exam code: its ordinal is taken as the position of the last
     * {@code 67536-3} screening in the record.</p>
     *
     * @param patientRec the patient record being exported
     * @param category the marker category already created for this patient, or {@code null}
     * @param screening the {@code 67536-3} screening just added for the NRTF reading
     * @param result the NRTF measurement value; {@code null} and blank are both exported as empty,
     *               which the importer restores as an empty value ({@code measurements.dataField}
     *               is {@code NOT NULL}, so empty is the only faithful form of "no value")
     * @return the marker category to pass in for the next NRTF reading of the same patient
     */
    public static NewCategory addNrtfMarker(PatientRecord patientRec, NewCategory category,
                                            DiabetesComplicationScreening screening, String result) {
        int ordinal = countNeurologicalExams(patientRec) - 1;
        NewCategory target = category;
        if (target == null) {
            target = patientRec.addNewNewCategory();
            target.setCategoryName(CATEGORY_NAME);
            target.setCategoryDescription(CATEGORY_DESCRIPTION);
        }
        ResidualInformation ri = target.addNewResidualInfo();
        addElement(ri, ELEMENT_TYPE, NRTF);
        addElement(ri, ELEMENT_ORDINAL, Integer.toString(ordinal));
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
     * Returns whether {@code screening} carries the neurological exam code shared by FTLS and NRTF.
     *
     * @param screening an imported or exported screening; may be {@code null}
     * @return {@code true} for exam code {@code 67536-3}
     */
    public static boolean isNeurologicalExam(DiabetesComplicationScreening screening) {
        return screening != null && ExamCode.X_67536_3.equals(screening.getExamCode());
    }

    /**
     * Collects the NRTF markers of an imported patient record.
     *
     * @param categories the record's {@code NewCategory} array; may be {@code null}
     * @return the markers keyed by screening ordinal; empty when the record has none
     */
    public static NrtfMarkers readNrtfMarkers(NewCategory[] categories) {
        NrtfMarkers markers = new NrtfMarkers();
        if (categories == null) {
            return markers;
        }
        for (NewCategory category : categories) {
            if (!isNrtfMarkerCategory(category)) {
                continue;
            }
            for (ResidualInformation ri : category.getResidualInfoArray()) {
                addMarker(markers, ri);
            }
        }
        return markers;
    }

    /** Adds the marker carried by one residual-information block when it is an NRTF marker. */
    private static void addMarker(NrtfMarkers markers, ResidualInformation ri) {
        Map<String, String> elements = new HashMap<>();
        for (DataElement element : ri.getDataElementArray()) {
            elements.put(element.getName(), elementContent(element));
        }
        if (NRTF.equals(elements.get(ELEMENT_TYPE))) {
            markers.add(elements.get(ELEMENT_ORDINAL),
                    elements.getOrDefault(ELEMENT_DATE, ""),
                    elements.getOrDefault(ELEMENT_RESULT, ""));
        }
    }

    private static String elementContent(DataElement element) {
        return element.getContent() == null ? "" : element.getContent().trim();
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

    private static int countNeurologicalExams(PatientRecord patientRec) {
        int count = 0;
        for (CareElements care : patientRec.getCareElementsArray()) {
            for (DiabetesComplicationScreening screening : care.getDiabetesComplicationsScreeningArray()) {
                if (isNeurologicalExam(screening)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void addElement(ResidualInformation ri, String name, String content) {
        DataElement element = ri.addNewDataElement();
        element.setName(name);
        element.setDataType(DATA_TYPE);
        element.setContent(content);
    }

    /**
     * The NRTF markers of one imported patient record, consumed as the importer walks the
     * record's {@code 67536-3} screenings in document order.
     */
    public static final class NrtfMarkers {

        private final Map<Integer, String[]> byOrdinal = new HashMap<>();

        NrtfMarkers() {
        }

        private void add(String ordinal, String date, String result) {
            if (ordinal == null) {
                return;
            }
            try {
                int index = Integer.parseInt(ordinal);
                if (index >= 0) {
                    // First marker for an ordinal wins; a duplicate is ignored rather than guessed at.
                    byOrdinal.putIfAbsent(index, new String[] {date, result});
                }
            } catch (NumberFormatException _) {
                // A malformed ordinal cannot be tied to a screening, so the marker is ignored.
            }
        }

        /**
         * Claims the marker for the {@code ordinal}-th {@code 67536-3} screening of the record.
         *
         * @param ordinal the 0-based position of {@code screening} among the record's
         *                {@code 67536-3} screenings, counted in document order
         * @param screening the imported neurological exam screening
         * @return the recorded NRTF result when the screening is an NRTF reading; an empty string
         *         means the reading had no value and must be stored as such, never as {@code "Yes"};
         *         or {@code null} when it should be imported as FTLS
         */
        public String claim(int ordinal, DiabetesComplicationScreening screening) {
            String[] marker = byOrdinal.get(ordinal);
            if (marker == null || !marker[0].equals(dateKey(screening))) {
                return null;
            }
            byOrdinal.remove(ordinal);
            return marker[1];
        }

        /** @return {@code true} when no unclaimed marker is left */
        public boolean isEmpty() {
            return byOrdinal.isEmpty();
        }
    }
}
