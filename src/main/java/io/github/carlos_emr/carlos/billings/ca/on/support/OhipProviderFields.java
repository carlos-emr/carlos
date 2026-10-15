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
package io.github.carlos_emr.carlos.billings.ca.on.support;

/**
 * The provider's own fields in the fixed-width OHIP batch header: the OHIP
 * billing number fills six characters and the specialty code two.
 *
 * <p>Both are free text on the provider record (the specialty code in
 * {@code <xml_p_specialty_code>}, read as {@code 00} when blank), and the
 * claim file writer cannot repair a value of another length: the header comes
 * out the wrong size and the file is refused. The OHIP simulation reports such
 * a provider with an error line and keeps rendering, so generation checks the
 * same rule for every provider of a run before it allocates the first disk.
 * Only the length is checked, as the writer and the simulation always have;
 * the group number has its own rule in {@link BillingGroupNumber}.</p>
 *
 * @since 2026-10-15
 */
public final class OhipProviderFields {

    /** Width of the provider's OHIP billing number in the batch header. */
    public static final int BILLING_NUMBER_LENGTH = 6;

    /** Width of the provider's specialty code in the batch header. */
    public static final int SPECIALTY_CODE_LENGTH = 2;

    private OhipProviderFields() {
    }

    /**
     * Whether a stored OHIP billing number fills its batch-header field.
     *
     * @param ohipNo the provider's OHIP billing number, possibly {@code null}
     * @return {@code true} when it is exactly six characters long
     */
    public static boolean isWellFormedBillingNumber(String ohipNo) {
        return ohipNo != null && ohipNo.length() == BILLING_NUMBER_LENGTH;
    }

    /**
     * Whether a specialty code fills its batch-header field.
     *
     * @param specialtyCode the provider's specialty code, possibly {@code null}
     * @return {@code true} when it is exactly two characters long
     */
    public static boolean isWellFormedSpecialtyCode(String specialtyCode) {
        return specialtyCode != null && specialtyCode.length() == SPECIALTY_CODE_LENGTH;
    }
}
