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
package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilege;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Finds the patients that have a security entry of their own, for the SMS queue page.
 * <p>
 * A viewer can only be restricted from a patient when the security tables hold an entry for that one
 * patient: a row whose object name is {@code _demographic$<number>} or {@code _eChart$<number>}. Those are
 * the only per-patient names {@code SecurityInfoManager} looks up, so a patient without such a row is never
 * restricted. The page therefore checks access only for the patients this class returns.
 * <p>
 * This class says which patients <i>could</i> be restricted, not which ones are: it does not look at who
 * the viewer is. It errs on the side of naming too many patients, because naming one without need only costs
 * one extra access check on the page, while missing one would show a restricted patient's messages. So it
 * trusts the database's {@code LIKE} for the prefix. The database compares object names by its collation,
 * which ignores letter case, accents and spaces at the end, so every spelling {@code SecurityInfoManager}
 * would find for a patient is among the names it returns; the extra names {@code LIKE} lets through (its
 * {@code _} stands for any one character) only name patients too many. Only the demographic number after the
 * prefix is read here.
 * <p>
 * That relies on the column's collation comparing one character with one character, as
 * {@code utf8mb4_general_ci} (the collation of {@code secObjPrivilege}) does: then the prefix of any matching
 * name is exactly as many characters long as {@code _demographic$} or {@code _eChart$}. A collation that
 * ignores some characters or expands others (the UCA collations) would break that, and this class with it.
 *
 * @since 2026-09-29
 */
@Service
public class SmsPatientRestrictionLookup {
    /** The object name prefixes of per-patient entries. The patient's demographic number follows. */
    static final List<String> PREFIXES = List.of("_demographic$", "_eChart$");
    // Digits, with a minus sign allowed because SecurityInfoManager would look a negative number up too, and
    // few enough to fit an int most of the time; the rest is caught when parsing.
    private static final Pattern DIGITS = Pattern.compile("-?[0-9]{1,10}");

    private final SecObjPrivilegeDao secObjPrivilegeDao;

    public SmsPatientRestrictionLookup(SecObjPrivilegeDao secObjPrivilegeDao) {
        this.secObjPrivilegeDao = secObjPrivilegeDao;
    }

    /**
     * @return the demographic numbers that have at least one {@code _demographic$<number>} or
     *         {@code _eChart$<number>} entry, for any role or user; empty when there are none
     */
    @Transactional(readOnly = true)
    public Set<Integer> patientsWithOwnEntries() {
        Set<Integer> patients = new TreeSet<>();
        for (String prefix : PREFIXES) {
            // In a LIKE pattern "_" stands for any one character, so the query can also return names such as
            // "Xdemographic$5". Those are kept: naming a patient too many is harmless.
            for (SecObjPrivilege entry : secObjPrivilegeDao.findByObjectName(prefix + "%")) {
                String objectName = entry.getId() == null ? null : entry.getId().getObjectName();
                Integer demographicNo = demographicNumber(prefix, objectName);
                if (demographicNo != null) {
                    patients.add(demographicNo);
                }
            }
        }
        return patients;
    }

    /**
     * @param prefix     the prefix the database matched the name against; only its length is used, since the
     *                   database has already decided that the name starts with it
     * @param objectName an object name the database returned for {@code prefix + "%"}
     * @return the demographic number after the first {@code prefix.length()} characters, spaces at the end
     *         ignored, or {@code null} when that is not a whole number that fits an int
     */
    static Integer demographicNumber(String prefix, String objectName) {
        if (objectName == null || objectName.length() <= prefix.length()) {
            return null;
        }
        String number = objectName.substring(prefix.length()).stripTrailing();
        if (!DIGITS.matcher(number).matches()) {
            return null;
        }
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException e) {
            // Ten digits, but more than an int holds: no patient has such a number.
            return null;
        }
    }
}
