/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.prescript.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.carlos_emr.CarlosProperties;

/**
 * Validates prescription instruction text for prohibited abbreviations
 * based on configurable hospital policies.
 *
 * <p>Currently implements the St. Joseph's Healthcare Hamilton policy,
 * which enforces the ISMP "Do Not Use" abbreviation list to prevent
 * medication errors from misread shorthand notations.</p>
 *
 * <p>All regex patterns are pre-compiled as class-level constants to avoid
 * repeated compilation overhead and to eliminate polynomial-complexity
 * backtracking (ReDoS) vulnerabilities that existed in the original
 * per-invocation pattern compilation approach.</p>
 *
 * <h3>Boundary strategy</h3>
 * <ul>
 *   <li>{@code (?:^|\s)} at the start ensures the abbreviation is preceded
 *       by whitespace or is at the start of the string.</li>
 *   <li>{@code (?=\s|$)} at the end ensures the abbreviation is followed
 *       by whitespace or is at the end of the string.</li>
 *   <li>{@code \b} is used only where the abbreviation ends with a letter
 *       (word boundary works correctly for letters).</li>
 *   <li>{@code \d} prefix handles number-adjacent forms like "100I.U."
 *       or "5CC".</li>
 * </ul>
 *
 * @since 2006 (OSCAR McMaster)
 */
public class RxInstructionPolicy {

    /* -- Pre-compiled pattern arrays for each prohibited abbreviation -- */

    /** U (units) — misread as '0', causing 10× overdose. */
    private static final Pattern[] PATTERNS_U = compile(
            "(?:^|\\s)(?i)U\\b",
            "\\d(?i)U\\b"
    );

    /** I.U. (international units) — misread as 'IV', causing route error. */
    private static final Pattern[] PATTERNS_IU = compile(
            "(?:^|\\s)(?i)I\\.U\\.(?=\\s|$)",
            "\\d(?i)I\\.U\\.(?=\\s|$)"
    );

    /** S.C. (subcutaneous, with dots) — confused with SL (sublingual). */
    private static final Pattern[] PATTERNS_SC_DOT = compile(
            "(?:^|\\s)(?i)S\\.C\\.(?=\\s|$)"
    );

    /** SC (subcutaneous, no dots) — confused with SL (sublingual). */
    private static final Pattern[] PATTERNS_SC = compile(
            "(?:^|\\s)(?i)SC\\b"
    );

    /** CC (cubic centimeters) — should use ml/mL. */
    private static final Pattern[] PATTERNS_CC = compile(
            "(?:^|\\s)(?i)CC\\b",
            "\\d(?i)CC\\b"
    );

    /** C.C. (cubic centimeters, with dots) — should use ml/mL. */
    private static final Pattern[] PATTERNS_CC_DOT = compile(
            "(?:^|\\s)(?i)C\\.C\\.(?=\\s|$)",
            "\\d(?i)C\\.C\\.(?=\\s|$)"
    );

    /** µg (microgram symbol) — misread as mg, causing 1000× overdose. */
    private static final Pattern[] PATTERNS_UG = compile("µg");

    /** Greater-than symbol — misread as a digit on handwritten prescriptions. */
    private static final Pattern[] PATTERNS_GT = compile(">");

    /** Less-than symbol — misread as a digit on handwritten prescriptions. */
    private static final Pattern[] PATTERNS_LT = compile("<");

    /** At symbol — ambiguous in prescription context. */
    private static final Pattern[] PATTERNS_AT = compile("@");

    /** A.S. (auris sinistra, left ear) — easily confused with A.D./A.U. */
    private static final Pattern[] PATTERNS_AS = compile(
            "(?:^|\\s)(?i)A\\.?S\\.?(?=\\s|$)"
    );

    /** A.D. (auris dextra, right ear) — easily confused with A.S./A.U. */
    private static final Pattern[] PATTERNS_AD = compile(
            "(?:^|\\s)(?i)A\\.?D\\.?(?=\\s|$)"
    );

    /** A.U. (auris uterque, both ears) — easily confused with A.S./A.D. */
    private static final Pattern[] PATTERNS_AU = compile(
            "(?:^|\\s)(?i)A\\.?U\\.?(?=\\s|$)"
    );

    /** Trailing zero (e.g. 10.0) — should be written as integer (10). */
    private static final Pattern[] PATTERNS_TRAILING_ZERO = compile("\\b\\.0\\b");

    /** Leading decimal (e.g. .1) — should be written with leading zero (0.1). */
    private static final Pattern[] PATTERNS_LEADING_DECIMAL = compile(
            "(?:^|\\s)\\.\\d+"
    );

    /** O.S. (oculus sinister, left eye) — easily confused with O.D./O.U. */
    private static final Pattern[] PATTERNS_OS = compile(
            "(?:^|\\s)(?i)O\\.?S\\.?(?=\\s|$)"
    );

    /** O.D. (oculus dexter, right eye) — easily confused with O.S./O.U. */
    private static final Pattern[] PATTERNS_OD_EYE = compile(
            "(?:^|\\s)(?i)O\\.?D\\.?(?=\\s|$)"
    );

    /** O.U. (oculus uterque, both eyes) — easily confused with O.S./O.D. */
    private static final Pattern[] PATTERNS_OU = compile(
            "(?:^|\\s)(?i)O\\.?U\\.?(?=\\s|$)"
    );

    /** q.o.d. (every other day) — misread as q.d. (daily), causing 2× dose. */
    private static final Pattern[] PATTERNS_QOD = compile(
            "(?:^|\\s)(?i)q\\.?o\\.?d\\.?(?=\\s|$)"
    );

    /** q.d. (daily) — should spell out 'daily'. */
    private static final Pattern[] PATTERNS_QD = compile(
            "(?:^|\\s)(?i)q\\.?d\\.?(?=\\s|$)"
    );

    /** o.d. (once daily) — should spell out 'daily'. */
    private static final Pattern[] PATTERNS_OD_DAILY = compile(
            "(?:^|\\s)(?i)o\\.?d\\.?(?=\\s|$)"
    );

    /**
     * Compiles one or more regex strings into an array of {@link Pattern} objects.
     *
     * @param regexes one or more regex strings to compile
     * @return array of compiled patterns
     */
    private static Pattern[] compile(String... regexes) {
        Pattern[] patterns = new Pattern[regexes.length];
        for (int i = 0; i < regexes.length; i++) {
            patterns[i] = Pattern.compile(regexes[i]);
        }
        return patterns;
    }

    /**
     * Validates prescription instruction text against configured policies.
     *
     * <p>Reads the {@code prescript.policy} property from {@link CarlosProperties}.
     * If the value contains "stjoes" (comma-separated), the St. Joseph's
     * ISMP "Do Not Use" abbreviation policy is applied.</p>
     *
     * @param instr the prescription instruction text to validate
     * @return a list of policy violation messages (empty if no violations)
     */
    public static List<String> checkInstructions(String instr) {
        List<String> errors = new ArrayList<>();

        String policies = CarlosProperties.getInstance().getProperty("prescript.policy");
        if (policies != null) {
            String[] policiesArray = policies.split(",");
            for (String policy : policiesArray) {
                if (policy.equalsIgnoreCase("stjoes")) {
                    applyStJoesPolicy(instr, errors);
                }
            }
        }

        return errors;
    }

    /**
     * Applies the St. Joseph's Healthcare Hamilton prescription instruction policy.
     *
     * <p>Checks for ISMP "Do Not Use" abbreviations that are known to cause
     * medication errors when misread (e.g., 'U' misread as '0' causing 10× overdose,
     * 'µg' misread as 'mg' causing 1000× overdose).</p>
     *
     * @param instr  the prescription instruction text to validate
     * @param errors list to which policy violation messages are added
     */
    public static void applyStJoesPolicy(String instr, List<String> errors) {
        // Unit abbreviations
        addPolicy(PATTERNS_U, instr, errors, "U", "Unit");
        addPolicy(PATTERNS_IU, instr, errors, "I.U.", "Unit");

        // Subcutaneous
        addPolicy(PATTERNS_SC_DOT, instr, errors, "S.C.", "Subcutaneous or subcut");
        addPolicy(PATTERNS_SC, instr, errors, "SC", "Subcutaneous or subcut");

        // Cubic centimeters
        addPolicy(PATTERNS_CC, instr, errors, "CC", "ml or mL");
        addPolicy(PATTERNS_CC_DOT, instr, errors, "C.C.", "ml or mL");

        // Microgram symbol
        addPolicy(PATTERNS_UG, instr, errors, "µg", "microgram or mcg");

        // Symbols that can be misread
        addPolicy(PATTERNS_GT, instr, errors, ">", "greater than");
        addPolicy(PATTERNS_LT, instr, errors, "<", "less than");
        addPolicy(PATTERNS_AT, instr, errors, "@", "at");

        // Ear abbreviations (ISMP "Do Not Use")
        addPolicy(PATTERNS_AS, instr, errors, "A.S.", "left ear");
        addPolicy(PATTERNS_AD, instr, errors, "A.D.", "right ear");
        addPolicy(PATTERNS_AU, instr, errors, "A.U.", "both ears");

        // Trailing zero / leading decimal
        addPolicy(PATTERNS_TRAILING_ZERO, instr, errors, "10.0", "10");
        addPolicy(PATTERNS_LEADING_DECIMAL, instr, errors, ".1", "0.1");

        // Eye abbreviations (ISMP "Do Not Use")
        addPolicy(PATTERNS_OS, instr, errors, "O.S.", "left eye");
        addPolicy(PATTERNS_OD_EYE, instr, errors, "O.D.", "right eye");
        addPolicy(PATTERNS_OU, instr, errors, "O.U.", "both eyes");

        // Frequency abbreviations
        addPolicy(PATTERNS_QOD, instr, errors, "q.o.d", "every other day");
        addPolicy(PATTERNS_QD, instr, errors, "q.d", "daily");
        addPolicy(PATTERNS_OD_DAILY, instr, errors, "o.d.", "daily");
    }

    /**
     * Tests pre-compiled patterns against instruction text and adds a violation
     * message if any pattern matches. Returns after the first match to avoid
     * duplicate violations for the same abbreviation rule.
     *
     * @param patterns     pre-compiled patterns to test
     * @param instructions the instruction text to check
     * @param errors       list to which violation messages are added
     * @param violation    the abbreviation that was found (for the error message)
     * @param replacement  the preferred alternative text
     */
    private static void addPolicy(Pattern[] patterns, String instructions, List<String> errors, String violation, String replacement) {
        for (Pattern p : patterns) {
            Matcher m = p.matcher(instructions);
            if (m.find()) {
                errors.add("Policy Violation: '" + violation + "'\nPlease use: " + replacement);
                return;
            }
        }
    }

}
