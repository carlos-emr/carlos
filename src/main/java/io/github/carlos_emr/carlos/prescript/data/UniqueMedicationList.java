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
package io.github.carlos_emr.carlos.prescript.data;

import io.github.carlos_emr.carlos.commn.model.Drug;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The patient's medication list with one entry per product and regimen (#4420).
 *
 * <p>Every "unique" medication view reads this list: the E-Chart Medications panel, the
 * consultation "Current Medications", the chart prints, the flowsheets, the drug profile, the
 * lab graph and the REST Rx summary. They expect one line per medication the patient is on, so
 * a renewal must replace the earlier copy rather than add a line. A drug prescribed again by
 * searching for it, rather than with ReRx, leaves the earlier row unarchived, and CDS-imported
 * history is never archived; keying on dated fields made each such renewal a new entry.
 *
 * <p>Two rows are the same entry when they name the same product and the same regimen: dose,
 * dose range, frequency, route, method, form and PRN, or the written instructions when the
 * regimen was entered as free text. Two concurrent regimens of one product (#4270) therefore
 * stay apart. Dates, quantity, repeats, duration, dispensing details, prescriber, pharmacy,
 * comments and term flags are prescription history, not regimen, and do not split an entry.
 *
 * <p>Of the rows that make up one entry the newest current, unarchived row is listed; when
 * none is current and unarchived, the newest row is. A newer archived copy therefore does not
 * hide a sibling the patient is still taking, and a discontinued renewal is not replaced by an
 * expired predecessor. Deleted rows never appear, and rows that do not identify their product
 * are never merged. The full history stays available through
 * {@link RxPrescriptionData#getPrescriptionsByPatient(int)}.
 *
 * @since 2026-10-08
 */
public final class UniqueMedicationList {

    /** The quantity line that {@code RxWriteScript2Action} appends to every written instruction. */
    private static final Pattern DISPENSE_LINE = Pattern.compile("^(?:qty|mitte)\\s*:.*$",
            Pattern.CASE_INSENSITIVE);

    private UniqueMedicationList() {
    }

    /**
     * Selects one row per product and regimen.
     *
     * @param drugs the patient's drug rows in any order, never null (a DAO result); not modified
     * @return the selected rows, newest (highest id) first
     */
    public static List<Drug> select(List<Drug> drugs) {
        List<Drug> newestFirst = new ArrayList<>(drugs);
        newestFirst.sort(new Drug.ComparatorIdDesc());

        Map<Object, Drug> chosen = new LinkedHashMap<>();
        for (Drug drug : newestFirst) {
            if (drug == null || drug.isDeleted()) {
                continue;
            }
            // An unidentified product is its own entry: equal blanks do not make two drugs the same.
            Object key = hasProductIdentity(drug) ? Entry.from(drug) : new Object();
            Drug current = chosen.get(key);
            if (current == null || (!isActive(current) && isActive(drug))) {
                chosen.put(key, drug);
            }
        }

        List<Drug> result = new ArrayList<>(chosen.values());
        result.sort(new Drug.ComparatorIdDesc());
        return result;
    }

    private static boolean hasProductIdentity(Drug drug) {
        return gcn(drug) != null
                || StringUtils.isNotBlank(drug.getCustomName())
                || StringUtils.isNotBlank(drug.getBrandName())
                || StringUtils.isNotBlank(drug.getGenericName())
                || StringUtils.isNotBlank(drug.getRegionalIdentifier());
    }

    /** Unarchived, and long-term or not yet past its end date. */
    private static boolean isActive(Drug drug) {
        return !drug.isArchived() && (drug.isLongTerm() || drug.isCurrent());
    }

    /** GCN {@code 0} is the "no code" value custom and drugref entries carry. */
    private static String gcn(Drug drug) {
        String gcn = StringUtils.trimToNull(drug.getGcnSeqNo());
        return "0".equals(gcn) ? null : gcn;
    }

    /**
     * The product and regimen of one row. Text is trimmed and blank text is treated as absent,
     * because legacy rows store the same "nothing" as null, empty or spaces.
     */
    private record Entry(List<Object> product, List<Object> regimen) {
        private static Entry from(Drug drug) {
            return new Entry(
                    values(gcn(drug), text(drug.getBrandName()), text(drug.getCustomName()),
                            text(drug.getGenericName()), text(drug.getAtc()), text(drug.getRegionalIdentifier()),
                            text(drug.getDosage()), text(drug.getUnit()), text(drug.getDrugForm())),
                    // Not unitName: RxWriteScript2Action stores the quantity's unit (and the Mitte marker) there.
                    values(drug.getTakeMin(), drug.getTakeMax(), text(drug.getFreqCode()), text(drug.getRoute()),
                            text(drug.getMethod()), drug.isPrn(), freeTextRegimen(drug)));
        }

        /**
         * The written instructions, when they are the only record of the regimen. Parsed
         * instructions also hold free-text duration and the prescriber's wording, which change
         * between renewals of one regimen, so they count only when nothing was parsed. The
         * quantity line is dispensing detail and is dropped either way.
         */
        private static String freeTextRegimen(Drug drug) {
            if (!drug.isCustomInstructions() && StringUtils.isNotBlank(drug.getFreqCode())) {
                return null;
            }
            String special = StringUtils.defaultString(drug.getSpecial());
            String instructions = Arrays.stream(special.split("\\R"))
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !DISPENSE_LINE.matcher(line).matches())
                    .collect(Collectors.joining("\n"));
            return instructions.isEmpty() ? null : instructions;
        }

        private static String text(String value) {
            return StringUtils.trimToNull(value);
        }

        private static List<Object> values(Object... values) {
            // List.of/copyOf reject the nulls that absent fields map to. The backing array is private.
            return Collections.unmodifiableList(Arrays.asList(values));
        }
    }
}
