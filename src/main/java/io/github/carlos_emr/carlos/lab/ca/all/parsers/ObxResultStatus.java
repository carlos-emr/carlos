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
package io.github.carlos_emr.carlos.lab.ca.all.parsers;

import com.google.common.base.Ascii;

/**
 * Whether an OBX result status, as a {@link MessageHandler#getOBXResultStatus} returns it, says the
 * result is final: complete, or a correction of a complete result (#6122).
 *
 * <p>Handlers do not agree on how they report the status. Some pass the HL7 code through
 * ({@code F}, {@code C}: PATHL7/Excelleris, Alpha, IHA, MEDITECH, TRUENORTH, the generic handler),
 * and others turn it into a word: {@code Final} (CML, CDL, Epsilon, Spire, CLS, MDS and the CDS
 * import), {@code Corrected} (CLS) or {@code Edited} (MDS's word for {@code C}). Labs also send the
 * code in lower case, which CML already counts as final. This rule accepts all of them, in any ASCII
 * case and with surrounding spaces, so one lab's final result is never filed differently from
 * another's.
 *
 * <p>Not final: preliminary, pending, partial and unverified results, a deleted or cancelled one
 * ({@code D}, {@code X}), and {@code U} (MDS's {@code Changed to Final}), which marks results sent
 * earlier as preliminary as final without sending them again. GDML and BioTest handlers return
 * OBX-10, not the status, so none of their results is final here.
 *
 * @since 2026-11-11
 */
public final class ObxResultStatus {

    private ObxResultStatus() {
    }

    /**
     * @param status the status a handler returned for one OBX; may be {@code null}
     * @return {@code true} when it reports a final or corrected result
     */
    public static boolean isFinal(String status) {
        if (status == null) return false;
        String code = status.trim();
        return Ascii.equalsIgnoreCase("F", code) || Ascii.equalsIgnoreCase("C", code)
                || Ascii.equalsIgnoreCase("Final", code) || Ascii.equalsIgnoreCase("Corrected", code)
                || Ascii.equalsIgnoreCase("Edited", code);
    }
}
