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
package io.github.carlos_emr.carlos.demographic.cdsimport.support;

import io.github.carlos_emr.carlos.commn.model.Drug;

/** Explicit refusal at a legacy protocol that cannot carry imported clinical semantics. */
public final class CdsMedicationCompatibilityException extends IllegalStateException {
    public static final String CODE = "CDS_MEDICATION_PRECISION_AWARE_REVIEW_REQUIRED";
    public static final String RENEWAL_CODE = "CDS_MEDICATION_RECONCILIATION_REQUIRED_BEFORE_RENEWAL";

    public CdsMedicationCompatibilityException() {
        this(CODE);
    }

    private CdsMedicationCompatibilityException(String code) {
        super(code + "; use the protected imported-medication source view and make an explicit prescribing decision");
    }

    public static CdsMedicationCompatibilityException renewalRequiresReview() {
        return new CdsMedicationCompatibilityException(RENEWAL_CODE);
    }

    /** Untouched native rows retain their established protocol behavior. */
    public static void requireLegacyCompatible(Drug drug) {
        if (drug != null && drug.getCdsCurrentness() != null) {
            throw new CdsMedicationCompatibilityException();
        }
    }
}
