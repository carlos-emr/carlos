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
package io.github.carlos_emr.carlos.lab.ca.all.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Reads the comma-separated lab-version chain that the lab views build and post.
 *
 * <p>A lab arrives as a chain of versions sharing one accession number (preliminary, final,
 * corrected). The lab display renders that chain into the {@code multiID} hidden field, oldest
 * first, and every acknowledge path posts it back. Acting on the chain — filing the versions
 * older than the one the clinician read — is what removes the collapsed inbox row, so the
 * parsing has to be forgiving of anything a browser might send and must never throw.
 *
 * <p>Deliberately free of Spring and database dependencies so the ordering rules can be pinned
 * by a plain unit test.
 *
 * @since 2026-09-06
 */
public final class LabVersionChain {

    private LabVersionChain() {
    }

    /**
     * Parses a chain into lab numbers, preserving order (oldest version first).
     *
     * <p>Order is meaningful — it is the version ordering — so entries are never sorted or
     * de-duplicated. Tokens that are not lab numbers are dropped rather than failing the
     * acknowledgement they arrived with.
     *
     * @param chain comma-separated lab numbers; may be null, blank or malformed
     * @return the lab numbers in the order given, empty if there are none
     */
    public static List<Integer> parse(String chain) {
        if (StringUtils.isBlank(chain)) {
            return Collections.emptyList();
        }
        List<Integer> labNos = new ArrayList<>();
        for (String token : chain.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                labNos.add(Integer.valueOf(trimmed));
            } catch (NumberFormatException e) {
                MiscUtils.getLogger().warn("ignoring non-numeric entry in lab version chain");
            }
        }
        return labNos;
    }

    /**
     * Returns the versions of {@code labNo} that precede it in the chain, oldest first.
     *
     * <p>Versions AFTER {@code labNo} are deliberately excluded: a corrected result that arrived
     * later is a new clinical fact and has to stay in the inbox until somebody reads it.
     *
     * <p>A chain that does not contain {@code labNo} describes some other lab, so the result is
     * empty. The inline predecessor of this method walked the array until it hit {@code labNo}
     * and ran off the end with an ArrayIndexOutOfBoundsException in exactly that case, which
     * aborted the filing step and reported the whole acknowledgement as a failure.
     *
     * @param labNo the version being acknowledged
     * @param chain comma-separated version chain, oldest first; may be null or malformed
     * @return the older lab numbers, empty if there are none or the chain does not describe this lab
     */
    public static List<Integer> olderThan(int labNo, String chain) {
        List<Integer> older = new ArrayList<>();
        for (Integer candidate : parse(chain)) {
            if (candidate != null && candidate == labNo) {
                return older;
            }
            older.add(candidate);
        }
        return Collections.emptyList();
    }

    /**
     * Tells whether the chain describes the given lab, i.e. contains it as one of its versions.
     *
     * @param labNo the version being acknowledged
     * @param chain comma-separated version chain; may be null or malformed
     * @return true when {@code labNo} appears in the chain
     */
    public static boolean describes(int labNo, String chain) {
        return parse(chain).contains(labNo);
    }
}
