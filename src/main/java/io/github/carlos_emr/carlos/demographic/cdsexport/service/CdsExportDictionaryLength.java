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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

/**
 * The two lengths of the CDS 5.2 {@code cds_dt} text types {@code text1K}, {@code text32K} and
 * {@code text64K}. The migration data dictionary counts them in thousands of characters (1,000,
 * 32,000 and 64,000); the schema's {@code maxLength} facets count them in multiples of 1,024
 * (1,024, 32,768 and 65,536).
 *
 * <p>Which one applies depends on who wrote the text, never on the patient:
 *
 * <ul>
 *   <li>Text CARLOS writes into such an element (entered or edited in CARLOS, or composed by the
 *       exporter from stored values and its own statements) keeps the dictionary length by the
 *       documented rule of its writer: the element holds a valid value (whole parts, or the text
 *       cut before a mark where a category has that rule) and the exact value travels in the
 *       record's CARLOS carrier. A writer's own check against the dictionary length is an
 *       integrity check its mapper's rule never trips.
 *   <li>An imported element retained unchanged is exported exactly as it was imported when the
 *       schema accepts it, the lengths between the two included. The export does not change a
 *       value nobody changed, and a CARLOS import of the export stores that same value again.
 * </ul>
 *
 * <p>{@code CdsExportXmlValidator} enforces the schema alone, so a length between the two never
 * holds a chart. Every element of these types, its writers and their rules are listed in {@code
 * CdsExportDictionaryLengthContractUnitTest}, which fails when a new class names one of them
 * without a rule.
 */
final class CdsExportDictionaryLength {
    /** {@code text1K} as the data dictionary counts it. */
    static final int TEXT_1K = 1_000;

    /** {@code text32K} as the data dictionary counts it. */
    static final int TEXT_32K = 32_000;

    /** {@code text64K} as the data dictionary counts it. */
    static final int TEXT_64K = 64_000;

    /** The schema's {@code maxLength} of {@code text1K}. */
    static final int SCHEMA_TEXT_1K = 1_024;

    /** The schema's {@code maxLength} of {@code text32K}. */
    static final int SCHEMA_TEXT_32K = 32_768;

    /** The schema's {@code maxLength} of {@code text64K}. */
    static final int SCHEMA_TEXT_64K = 65_536;

    private CdsExportDictionaryLength() {}

    /**
     * The schema's length of the type whose dictionary length is {@code dictionary}.
     *
     * @throws IllegalArgumentException when {@code dictionary} is not one of the three lengths
     */
    static int schema(int dictionary) {
        return switch (dictionary) {
            case TEXT_1K -> SCHEMA_TEXT_1K;
            case TEXT_32K -> SCHEMA_TEXT_32K;
            case TEXT_64K -> SCHEMA_TEXT_64K;
            default -> throw new IllegalArgumentException("CDS_EXPORT_DICTIONARY_LENGTH_UNKNOWN");
        };
    }

    /**
     * The most characters (code points) a value written in such an element may have.
     *
     * @param dictionary the element's dictionary length ({@link #TEXT_1K}, {@link #TEXT_32K} or
     *     {@link #TEXT_64K})
     * @param imported true when the value is the imported element's own text, unchanged
     * @return the schema's length for an unchanged imported value, the dictionary's otherwise
     */
    static int maximum(int dictionary, boolean imported) {
        return imported ? schema(dictionary) : dictionary;
    }
}
