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
package io.github.carlos_emr.carlos.commn.hl7.v2;

import ca.uhn.hl7v2.HL7Exception;

/**
 * Tells HAPI 2.x's structure-access failure apart from every other unchecked exception (issue #5414).
 *
 * <p>Under HAPI 1.0.1 a generated getter such as {@code getORDER_OBSERVATION(i)} threw a checked
 * {@link HL7Exception} when it could not reach the structure, and lab handlers caught it to show a
 * blank or a default. HAPI 2.x wraps that same {@code HL7Exception} in a {@link RuntimeException}.
 * A handler that now catches {@code RuntimeException} calls {@link #rethrowUnlessMissingStructure}
 * first, so a programming error or any other failure still propagates as it did under 1.0.1.</p>
 *
 * @since 2026-10-29
 */
public final class Hl7StructureAccess {

    private Hl7StructureAccess() {
    }

    /**
     * Returns when {@code failure} is HAPI's wrapped structure-access {@link HL7Exception};
     * rethrows {@code failure} otherwise.
     *
     * @param failure the unchecked exception a HAPI getter threw
     */
    public static void rethrowUnlessMissingStructure(RuntimeException failure) {
        if (!(failure.getCause() instanceof HL7Exception)) {
            throw failure;
        }
    }
}
