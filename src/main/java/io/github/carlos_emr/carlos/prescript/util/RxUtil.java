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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.prescript.data.RxCodesData;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBean;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class RxUtil {

    private static String defaultPattern = "yyyy/MM/dd";
    private static Locale locale = Locale.CANADA;
    private static String defaultQuantity = "30";
    private static final Logger logger = MiscUtils.getLogger();
    private static final String METHOD_APPLY = "Apply";
    /** The lower-case form of {@link #METHOD_APPLY}, as the topical-word set and the legacy text scan compare it. */
    private static final String APPLY_WORD = "apply";
    private static final String METHOD_RUB_WELL_IN = "Rub well in";
    @SuppressWarnings("java:S5843") // a vocabulary of unit spellings: the metric counts every spelling, and the match is linear
    private static final Pattern STRENGTH_UNIT = Pattern.compile(
            "(?:mc?gs?|[µμu]g|g(?:rams?|ms?)?|iu|meq|mmol|milligrams?|micrograms?"
                    + "|milliequivalents?|millimoles?|international\\s++units?)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DOSE_UNIT = Pattern.compile("(?:ml|units?|grams?|gms?|g|iu)\\b", Pattern.CASE_INSENSITIVE);
    /** Every spelling of a gram unit; "2 grams" and "2 gm" are classified exactly as "2 g". */
    private static final Set<String> GRAM_WORDS = Set.of("g", "gm", "gms", "gram", "grams");
    /** Dosage forms that are counted out one by one; a count written with one is an explicit administered count. */
    private static final Set<String> COUNTED_FORMS = Set.of("tab", "tabs", "tablet", "tablets", "cap", "caps",
            "capsule", "capsules", "pill", "pills", "puff", "puffs", "spray", "sprays", "drop", "drops", "gtt", "gtts",
            "patch", "patches", "supp", "supps", "suppository", "suppositories", "sachet", "sachets", "packet",
            "packets", "lozenge", "lozenges", "vial", "vials", "amp", "amps", "ampoule", "ampoules", "inhalation",
            "inhalations");
    /** Units an administered volume or unit count is written in ("5 mL", "10 units"). */
    private static final Set<String> MEASURED_DOSE_UNITS = Set.of("ml", "unit", "units");
    /** A number or a fraction ("2", "0.5", "1/2") that is not part of a larger token. */
    private static final Pattern AMOUNT_TOKEN = Pattern.compile("(?<![\\w.#/-])(\\d+(?:\\.\\d+)?(?:/\\d+)?)");
    /** Wording that gives a product by mouth when no route token was parsed ("by mouth", "orally"). */
    private static final Pattern ORAL_CONTEXT = Pattern.compile(
            "\\b(?:by\\s+mouth|orally|per\\s+os|p\\.o\\.)", Pattern.CASE_INSENSITIVE);
    /** Route words the route list does not parse that give the product other than by mouth ("IV", "sq"). */
    private static final Set<String> NON_ORAL_WORDS = Set.of("iv", "im", "sc", "sq", "subq");
    /** Beginnings of words for routes other than by mouth ("subcutaneously", "injection", "inhaled"). */
    private static final List<String> NON_ORAL_WORD_STARTS = List.of("subcut", "intramuscular", "intravenous", "inject", "inh");
    /**
     * Words after which an amount is not the dose: a vehicle or diluent ("in 100 mL", "with 250 mL"),
     * a ceiling ("max 8 tabs") or an amount to dispense ("dispense 30", "qty 30").
     */
    private static final Set<String> NOT_A_DOSE_WORDS = Set.of("in", "into", "with", "of", "mixed", "dissolved",
            "diluted", "over", "plus", "via", "max", "maximum", "limit", "upto", "dispense", "disp", "mitte", "qty",
            "quantity", "supply", "total");
    /** Phrases with the same meaning as {@link #NOT_A_DOSE_WORDS} ("up to 4 sachets", "no more than 8 tabs"). */
    private static final Set<String> NOT_A_DOSE_PHRASES = Set.of("up to", "no more than", "not more than",
            "not to exceed", "not exceeding");
    /**
     * Words that put a gram amount on the skin or the vaginal mucosa, where the grams are what is
     * applied. Rectal words are left out on purpose: a rectal gram amount is usually the strength of
     * one suppository or enema (mesalamine 1 g, 4 g/60 mL), not grams of cream.
     */
    private static final Set<String> TOPICAL_WORDS = Set.of("top", "topical", "topically", "externally", "pv",
            "vaginal", "vaginally", "intravaginal", "intravaginally", APPLY_WORD, "applied");
    /** Application sites that, after "to" or "to the", mark a topical instruction ("to knees", "to the scalp"). */
    private static final Set<String> APPLICATION_SITES = Set.of("skin", "face", "scalp", "hand", "hands", "feet",
            "foot", "knee", "knees", "elbow", "elbows", "back", "leg", "legs", "arm", "arms", "chest", "neck", "rash",
            "lesion", "lesions", "wound", "wounds", "nail", "nails", "lip", "lips");
    /** What follows "affected" in a topical instruction ("affected area", "affected skin"). */
    private static final Set<String> AFFECTED_SITES = Set.of("area", "areas", "skin", "site", "sites");
    /** A dose per kilogram or per square metre of body size, which states no fixed administered amount. */
    private static final Pattern BODY_SIZE_BASIS = Pattern.compile("(?:kg|m2|m²|m\\^2)(?!\\w)", Pattern.CASE_INSENSITIVE);
    /**
     * A number and the word after it in a product's dosage text, as the drug picker stores a strength
     * ("500.0mg"). A match starts only where a number starts and never backtracks, so a long run of
     * digits in a stored dosage costs linear time.
     */
    @SuppressWarnings("java:S8786") // linear: the lookbehind and possessive quantifiers never backtrack (60,000 digits parse in under 1 s)
    private static final Pattern PRODUCT_STRENGTH = Pattern.compile(
            "(?<!\\d)(\\d++(?:\\.\\d++)?+)\\s*+([a-zµμ]++)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** Runs of ASCII letters, digits and underscores: the words of an instruction, as {@code \\b} sees them. */
    private static final Pattern WORD = Pattern.compile("\\w+");
    /** A tablet form, the only dosage form a strength may be divided into halves or quarters of. */
    private static final Pattern TABLET_FORM = Pattern.compile("tab", Pattern.CASE_INSENSITIVE);
    /**
     * Tablet forms that must not be split ("TABLET (EXTENDED-RELEASE)", "TABLET (ENTERIC-COATED)"):
     * modified-release, enteric-coated, orally disintegrating, sublingual, buccal and effervescent.
     */
    private static final Pattern UNSPLITTABLE_TABLET = Pattern.compile(
            "release|enteric|disintegrat|sublingual|buccal|effervescent", Pattern.CASE_INSENSITIVE);
    /** A tablet, caplet or capsule form: a product counted out one by one. */
    private static final Pattern TABLET_OR_CAPSULE_FORM = Pattern.compile("tab|cap", Pattern.CASE_INSENSITIVE);
    /** A semi-solid form applied by the gram. */
    private static final Pattern TOPICAL_FORM = Pattern.compile(
            "\\b(?:cream|ointment|gel|lotion|paste|foam)\\b", Pattern.CASE_INSENSITIVE);
    /** A liquid dosage form, measured by volume rather than counted. */
    private static final Pattern LIQUID_FORM = Pattern.compile(
            "\\b(?:solution|suspension|syrup|elixir|liquid|emulsion|tincture)\\b", Pattern.CASE_INSENSITIVE);
    /** More units than this per administration means the strength and the product do not match. */
    private static final BigDecimal MAX_UNITS_PER_DOSE = BigDecimal.valueOf(20);
    private static final Pattern CONCENTRATION_SEPARATOR = Pattern.compile("(?:/|per\\b)\\s*", Pattern.CASE_INSENSITIVE);
    /**
     * The tail of a written daily count: "x daily", "times a day", "times per day", "x/day". The
     * optional separator carries its own trailing spaces, so no two space runs are adjacent (a
     * run of spaces then has one parse and the pattern stays linear).
     */
    private static final String TIMES_A_DAY = "\\s*(?:x|times?)\\s*(?:(?:/|per|a)\\s*)?(?:daily|day)(?![a-z])";
    /**
     * Daily counts written out, one pattern per code so that "3 times daily" is TID rather than the
     * "daily" it ends with: "3 times daily", "2x a day", "4 times per day", "3x/day", "once a day",
     * "twice a day". Matched before the plain "daily" patterns.
     */
    private static final String[] TIMES_A_DAY_PHRASES = {
            "(?:^|\\s)(?i)(?:once\\s*(?:/|per|a)\\s*(?:daily|day)(?![a-z])|(?:1|one)" + TIMES_A_DAY + ")",
            "(?:^|\\s)(?i)(?:twice\\s*(?:/|per|a)\\s*(?:daily|day)(?![a-z])|(?:2|two)" + TIMES_A_DAY + ")",
            "(?:^|\\s)(?i)(?:thrice\\s*(?:/|per|a)\\s*(?:daily|day)(?![a-z])|(?:3|three)" + TIMES_A_DAY + ")",
            "(?:^|\\s)(?i)(?:4|four)" + TIMES_A_DAY};
    /** The count word or figure that opens a written daily count, with the day word it ends with. */
    private static final Pattern DAILY_COUNT_PHRASE = Pattern.compile(
            "^(once|one|twice|two|thrice|three|four|[1-4])(?:(?=x(?![a-z]))|(?![a-z0-9])).*(?:daily|day)$",
            Pattern.CASE_INSENSITIVE);
    /**
     * The shorthand n/7, n/52 and n/12: a duration in days, weeks or months, optionally introduced by
     * "x" or "for" ("BID x 5/7", "daily for 3/12", "bid 5/7").
     */
    private static final Pattern SHORTHAND_DURATION = Pattern.compile(
            "(?:^|\\s)(?:(x|for)\\s*)?(\\d+)\\s*/\\s*(7|52|12)(?![0-9/])", Pattern.CASE_INSENSITIVE);
    /**
     * The word that joins the segments of a tapering course: "then", ", then", "followed by". Only
     * the word is matched; the spaces and comma around it stay on the segments and are trimmed
     * there, so that scanning a long run of spaces costs one step per character.
     */
    private static final Pattern TAPER_SEPARATOR = Pattern.compile(
            "\\b(?:then|followed by)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PER_DOSE = Pattern.compile("(?:/|per\\b)\\s*dose\\b", Pattern.CASE_INSENSITIVE);
    /** A figure or a number word: anything in the directions that could be the dose. */
    private static final Pattern DOSE_NUMBER = Pattern.compile(
            "\\d|\\b(?:zero|one|two|three|four|five|six|seven|eight|nine|ten)\\b", Pattern.CASE_INSENSITIVE);
    private static String[] zeroToTen = {"(?i)zero", "(?i)one", "(?i)two", "(?i)three", "(?i)four", "(?i)five", "(?i)six", "(?i)seven", "(?i)eight", "(?i)nine", "(?i)ten"};

    public static void setDefaultQuantity(String quantity) {
        defaultQuantity = quantity;
    }

    public static String getDefaultQuantity() {
        return defaultQuantity;
    }

    public static Date StringToDate(String Expression) {
        return StringToDate(Expression, defaultPattern);
    }

    public static Date StringToDate(String Expression, String pattern) {
        try {
            SimpleDateFormat df = new SimpleDateFormat(pattern, locale);

            return df.parse(Expression);
        } catch (Exception _) {
            return null;
        }
    }

    /**
     * Reads a prescription's pick-up time as the card's Pick Up Time box gives it: a 24-hour {@code H:mm}
     * or {@code HH:mm}, which is also how the card shows it again (issue #4799). It was read with the
     * 12-hour {@code hh:mm}, which turned 12:00 to 12:59 into 00:00 to 00:59.
     *
     * @param value the posted time
     * @return the time of day, or {@code null} if it cannot be read
     */
    public static Date parsePickupTime(String value) {
        return StringToDate(value, "HH:mm");
    }

    public static String DateToString(Date Expression) {
        return DateToString(Expression, defaultPattern);
    }

    public static String DateToString(Calendar Expression) {
        if (Expression == null) return DateToString(null, defaultPattern);
        return DateToString(Expression.getTime(), defaultPattern);
    }

    public static String DateToString(Date Expression, String pattern) {
        if (Expression != null) {
            SimpleDateFormat df = new SimpleDateFormat(pattern, locale);

            return df.format(Expression);
        } else {
            return "";
        }
    }

    public static String DateToString(Date Expression, String pattern, Locale locale2) {
        if (Expression != null) {
            SimpleDateFormat df = new SimpleDateFormat(pattern, locale2);

            return df.format(Expression);
        } else {
            return "";
        }
    }

    public static Date Today() {
        return (java.util.GregorianCalendar.getInstance().getTime());
    }

    public static int BoolToInt(boolean Expression) {
        if (Expression == true) {
            return 1;
        } else {
            return 0;
        }
    }

    public static boolean IntToBool(int Expression) {
        return (Expression != 0);
    }

    /**
     * Renders a structured dose as text. The Rx page and the quantity callback parse this text back
     * with {@code Double.parseDouble}, so the digits are never grouped (a 1000-unit dose rendered as
     * "1,000" made that parse throw) and the decimal separator is the dot whatever the server's
     * default locale is.
     */
    public static String FloatToString(float value) {
        java.text.NumberFormat fmt = java.text.NumberFormat.getNumberInstance(Locale.ROOT);
        fmt.setGroupingUsed(false);
        return fmt.format(value);
    }

    public static float StringToFloat(String value) {
        return Float.parseFloat(value);
    }

    public static Object IIf(boolean Expression, Object TruePart, Object FalsePart) {
        if (Expression == true) {
            return TruePart;
        } else {
            return FalsePart;
        }
    }

    public static String joinArray(Object[] array) {
        String ret = "";
        int i;
        for (i = 0; i < array.length; i++) {
            ret += "'" + String.valueOf(array[i]) + "'";
            if (i < array.length - 1) {
                ret += ", ";
            }
        }

        return ret;
    }

    public static String replace(String expression, String searchFor, String replaceWith) {
        if (expression != null) {
            StringBuilder buf = new StringBuilder(expression);

            int pos = -1;

            while (true) {
                pos = buf.indexOf(searchFor, pos);

                if (pos > -1) {
                    buf.delete(pos, pos + searchFor.length());
                    buf.insert(pos, replaceWith);

                    pos += replaceWith.length();
                } else {
                    break;
                }
            }

            return buf.toString();
        } else {
            return null;
        }
    }

    /**
     * Method for calculating creatinine clearance takes age, weight in kg and CREATININE values an returns Clcr
     * age must be greater than zero.
     * weight must be greater than zero
     */
    public static int getClcr(int age, double weight, double sCr, boolean female) throws Exception {
        if (age < 0) {
            throw new Exception("age must be greater than 0");
        }
        if (weight < 0) {
            throw new Exception("weight must be greater than 0");
        }
        if (sCr < 0) {
            throw new Exception("sCr must be greater than 0");
        }

        double Clcr = (140 - age) * weight / (sCr * 0.8);
        if (female) {
            Clcr = Clcr * 0.85;
        }

        return (int) Math.round(Clcr);
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public static double findNDays(String durationUnit) {
        double nDays = 0d;
        if (durationUnit.equalsIgnoreCase("D")) {
            nDays = 1;
        } else if (durationUnit.equalsIgnoreCase("W")) {
            nDays = 7;
        } else if (durationUnit.equalsIgnoreCase("M")) {
            nDays = 30;
        }
        return nDays;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public static Double findNPerDay(String frequency) {
        double nPerDay = 0d;
        if (frequency.equalsIgnoreCase("od")) {
            nPerDay = 1;
        } else if (frequency.equalsIgnoreCase("bid")) {
            nPerDay = 2;
        } else if (frequency.equalsIgnoreCase("tid")) {
            nPerDay = 3;
        } else if (frequency.equalsIgnoreCase("qid")) {
            nPerDay = 4;
        } else if (frequency.equalsIgnoreCase("Q1H")) {
            nPerDay = 24;
        } else if (frequency.equalsIgnoreCase("Q2H")) {
            nPerDay = 12;
        } else if (frequency.equalsIgnoreCase("Q1-2H")) {
            nPerDay = 24;
        } else if (frequency.equalsIgnoreCase("Q3-4H")) {
            nPerDay = 8;
        } else if (frequency.equalsIgnoreCase("Q4H")) {
            nPerDay = 6;
        } else if (frequency.equalsIgnoreCase("Q4-6H")) {
            nPerDay = 6;
        } else if (frequency.equalsIgnoreCase("Q6H")) {
            nPerDay = 4;
        } else if (frequency.equalsIgnoreCase("Q8H")) {
            nPerDay = 3;
        } else if (frequency.equalsIgnoreCase("Q12H")) {
            nPerDay = 2;
        } else if (frequency.equalsIgnoreCase("QAM")) {
            nPerDay = 1;
        } else if (frequency.equalsIgnoreCase("QPM")) {
            nPerDay = 1;
        } else if (frequency.equalsIgnoreCase("QHS")) {
            nPerDay = 1;
        } else if (frequency.equalsIgnoreCase("Q1Week")) {
            nPerDay = 0.14285714285714285;
        } else if (frequency.equalsIgnoreCase("Q2Week")) {
            nPerDay = 0.07142857142857142;
        } else if (frequency.equalsIgnoreCase("Q1Month")) {
            nPerDay = 0.03333333333333333;
        } else if (frequency.equalsIgnoreCase("Q3Month")) {
            nPerDay = 0.011111111111111112;
        }
        return nPerDay;
    }

    public static String getUnitNameFromQuantityText(String qStr) {
        if (qStr != null) {
            Pattern p1 = Pattern.compile("\\d+");
            Matcher m1 = p1.matcher(qStr);
            if (m1.find()) {
                String qNum = qStr.substring(m1.start(), m1.end());
                //get the quantity unit
                String qUnit = qStr.replace(qNum, "").trim();
                if (qUnit != null && qUnit.length() > 0) {
                    return qUnit;
                }
            }
        }
        return null;
    }

    public static String getQuantityFromQuantityText(String qStr) {
        if (qStr != null) {
            Pattern p1 = Pattern.compile("\\d+");
            Matcher m1 = p1.matcher(qStr);
            if (m1.find()) {
                String qNum = qStr.substring(m1.start(), m1.end());
                return qNum;
            }
        }
        return null;
    }

    public static boolean isMitte(String qStr) {
        boolean isMitte = false;
        String[] durationUnits = {"[0-9]+\\s+(?i)days\\s", "[0-9]+\\s+(?i)weeks\\s", "[0-9]+\\s+(?i)months\\s", "[0-9]+\\s+(?i)day\\s", "[0-9]+\\s+(?i)week\\s", "[0-9]+\\s+(?i)month\\s", "[0-9]+\\s+(?i)d\\s", "[0-9]+\\s+(?i)w\\s", "[0-9]+\\s+(?i)m\\s", "[0-9]+\\s+(?i)mo\\s", "[0-9]+\\s+(?i)days$", "[0-9]+\\s+(?i)weeks$", "[0-9]+\\s+(?i)months$", "[0-9]+\\s+(?i)day$", "[0-9]+\\s+(?i)week$", "[0-9]+\\s+(?i)month$", "[0-9]+\\s+(?i)d$", "[0-9]+\\s+(?i)w$", "[0-9]+\\s+(?i)m$", "[0-9]+\\s+(?i)mo$",
                "\\s[0-9]+(?i)days\\s", "\\s[0-9]+(?i)weeks\\s", "\\s[0-9]+(?i)months\\s", "\\s[0-9]+(?i)day\\s", "\\s[0-9]+(?i)week\\s", "\\s[0-9]+(?i)month\\s", "\\s[0-9]+(?i)d\\s", "\\s[0-9]+(?i)w\\s", "\\s[0-9]+(?i)m\\s", "\\s[0-9]+(?i)mo\\s", "\\s[0-9]+(?i)days$", "\\s[0-9]+(?i)weeks$", "\\s[0-9]+(?i)months$", "\\s[0-9]+(?i)day$", "\\s[0-9]+(?i)week$", "\\s[0-9]+(?i)month$", "\\s[0-9]+(?i)d$", "\\s[0-9]+(?i)w$", "\\s[0-9]+(?i)m$", "\\s[0-9]+(?i)mo$", "^[0-9]+(?i)days$", "^[0-9]+(?i)weeks$",
                "^[0-9]+(?i)months$", "^[0-9]+(?i)day$", "^[0-9]+(?i)week$", "^[0-9]+(?i)month$", "^[0-9]+(?i)d$", "^[0-9]+(?i)w$", "^[0-9]+(?i)m$", "^[0-9]+(?i)mo$", "^[0-9]+\\s+(?i)days$", "^[0-9]+\\s+(?i)weeks$", "^[0-9]+\\s+(?i)months$", "^[0-9]+\\s+(?i)day$", "^[0-9]+\\s+(?i)week$", "^[0-9]+\\s+(?i)month$", "^[0-9]+\\s+(?i)d$", "^[0-9]+\\s+(?i)w$", "^[0-9]+\\s+(?i)m$", "^[0-9]+\\s+(?i)mo$"};
        for (String s : durationUnits) {
            Pattern p = Pattern.compile(s);
            Matcher m = p.matcher(qStr);
            if (m.find()) {
                String foundStr = (qStr.substring(m.start(), m.end())).trim();
                qStr = qStr.replace(foundStr, "");
                qStr = qStr.trim();
                if (qStr.length() == 0) {
                    isMitte = true;
                    break;
                }
            }
        }
        return isMitte;
    }

    public static String getDurationFromQuantityText(String qStr) {
        String retStr = "";
        String[] durationUnits = {"[0-9]+\\s+(?i)days\\s", "[0-9]+\\s+(?i)weeks\\s", "[0-9]+\\s+(?i)months\\s", "[0-9]+\\s+(?i)day\\s", "[0-9]+\\s+(?i)week\\s", "[0-9]+\\s+(?i)month\\s", "[0-9]+\\s+(?i)d\\s", "[0-9]+\\s+(?i)w\\s", "[0-9]+\\s+(?i)m\\s", "[0-9]+\\s+(?i)mo\\s", "[0-9]+\\s+(?i)days$", "[0-9]+\\s+(?i)weeks$", "[0-9]+\\s+(?i)months$", "[0-9]+\\s+(?i)day$", "[0-9]+\\s+(?i)week$", "[0-9]+\\s+(?i)month$", "[0-9]+\\s+(?i)d$", "[0-9]+\\s+(?i)w$", "[0-9]+\\s+(?i)m$", "[0-9]+\\s+(?i)mo$",
                "\\s[0-9]+(?i)days\\s", "\\s[0-9]+(?i)weeks\\s", "\\s[0-9]+(?i)months\\s", "\\s[0-9]+(?i)day\\s", "\\s[0-9]+(?i)week\\s", "\\s[0-9]+(?i)month\\s", "\\s[0-9]+(?i)d\\s", "\\s[0-9]+(?i)w\\s", "\\s[0-9]+(?i)m\\s", "\\s[0-9]+(?i)mo\\s", "\\s[0-9]+(?i)days$", "\\s[0-9]+(?i)weeks$", "\\s[0-9]+(?i)months$", "\\s[0-9]+(?i)day$", "\\s[0-9]+(?i)week$", "\\s[0-9]+(?i)month$", "\\s[0-9]+(?i)d$", "\\s[0-9]+(?i)w$", "\\s[0-9]+(?i)m$", "\\s[0-9]+(?i)mo$", };
        for (String s : durationUnits) {
            Pattern p = Pattern.compile(s);
            Matcher m = p.matcher(qStr);
            if (m.find()) {
                String foundStr = (qStr.substring(m.start(), m.end())).trim();
                Pattern p2 = Pattern.compile("[0-9]+");
                Matcher m2 = p2.matcher(foundStr);
                if (m2.find()) {
                    String duration = (foundStr.substring(m2.start(), m2.end())).trim();
                    retStr = duration;
                }
                break;
            }
        }
        return retStr;
    }

    public static String getDurationUnitFromQuantityText(String qStr) {
        String retStr = "";
        String[] durationUnits = {"[0-9]+\\s+(?i)days\\s", "[0-9]+\\s+(?i)weeks\\s", "[0-9]+\\s+(?i)months\\s", "[0-9]+\\s+(?i)day\\s", "[0-9]+\\s+(?i)week\\s", "[0-9]+\\s+(?i)month\\s", "[0-9]+\\s+(?i)d\\s", "[0-9]+\\s+(?i)w\\s", "[0-9]+\\s+(?i)m\\s", "[0-9]+\\s+(?i)mo\\s", "[0-9]+\\s+(?i)days$", "[0-9]+\\s+(?i)weeks$", "[0-9]+\\s+(?i)months$", "[0-9]+\\s+(?i)day$", "[0-9]+\\s+(?i)week$", "[0-9]+\\s+(?i)month$", "[0-9]+\\s+(?i)d$", "[0-9]+\\s+(?i)w$", "[0-9]+\\s+(?i)m$", "[0-9]+\\s+(?i)mo$",
                "\\s[0-9]+(?i)days\\s", "\\s[0-9]+(?i)weeks\\s", "\\s[0-9]+(?i)months\\s", "\\s[0-9]+(?i)day\\s", "\\s[0-9]+(?i)week\\s", "\\s[0-9]+(?i)month\\s", "\\s[0-9]+(?i)d\\s", "\\s[0-9]+(?i)w\\s", "\\s[0-9]+(?i)m\\s", "\\s[0-9]+(?i)mo\\s", "\\s[0-9]+(?i)days$", "\\s[0-9]+(?i)weeks$", "\\s[0-9]+(?i)months$", "\\s[0-9]+(?i)day$", "\\s[0-9]+(?i)week$", "\\s[0-9]+(?i)month$", "\\s[0-9]+(?i)d$", "\\s[0-9]+(?i)w$", "\\s[0-9]+(?i)m$", "\\s[0-9]+(?i)mo$", };
        for (String s : durationUnits) {
            Pattern p = Pattern.compile(s);
            Matcher m = p.matcher(qStr);
            if (m.find()) {
                String foundStr = (qStr.substring(m.start(), m.end())).trim();
                Pattern p2 = Pattern.compile("[0-9]+");
                Matcher m2 = p2.matcher(foundStr);
                if (m2.find()) {
                    String duration = (foundStr.substring(m2.start(), m2.end())).trim();
                    String durationUnit = foundStr.replace(duration, "").trim();
                    if (durationUnit.startsWith("d") || durationUnit.startsWith("D")) retStr = "D";
                    else if (durationUnit.startsWith("w") || durationUnit.startsWith("W")) retStr = "W";
                    else if (durationUnit.startsWith("m") || durationUnit.startsWith("M")) retStr = "M";
                    else retStr = "";
                    break;
                }
            }
        }
        return retStr;
    }

    /**
     * Derives the duration from the quantity when the directions state none: the quantity divided
     * by the units taken per duration unit (dose per administration, administrations per day, days
     * per unit). A duration the directions state is returned as parsed. The parser resets the
     * quantity before reading the directions, so the typed quantity is captured first and restored
     * when the parser calculated none.
     *
     * @return the duration in the parsed duration unit, or null when a quantity unit (a Mitte
     *         quantity), a missing quantity or a zero dose leaves it incalculable
     */
    public static String findDuration(RxPrescriptionData.Prescription rx) {
        String typedQuantity = rx.getQuantity() == null ? "" : rx.getQuantity().trim();
        instrucParser(rx);
        if (rx.isDurationSpecifiedByUser()) {
            return rx.getDuration();
        }
        if (!typedQuantity.isEmpty() && calculatedNoQuantity(rx)) {
            rx.setQuantity(typedQuantity);
        }
        if (rx.getUnitName() != null) {
            return null;
        }
        Matcher digits = Pattern.compile("\\d+").matcher(typedQuantity);
        if (!digits.find()) {
            logger.error("quantity is not specified");
            return null;
        }
        String number = digits.group();
        String quantityUnit = typedQuantity.replace(number, "").trim();
        if (!quantityUnit.isEmpty()) {
            // A quantity typed with its own unit (a Mitte quantity) leaves the duration incalculable.
            rx.setUnitName(quantityUnit);
            return null;
        }
        return durationForQuantity(rx, Double.parseDouble(number));
    }

    /** True when the parser left no calculated quantity behind: null, empty or "0". */
    private static boolean calculatedNoQuantity(RxPrescriptionData.Prescription rx) {
        String quantity = rx.getQuantity();
        return quantity == null || quantity.isEmpty() || "0".equals(quantity);
    }

    /**
     * Divides the quantity by the units taken per duration unit: the maximum dose per
     * administration, the administrations per day and the days per duration unit.
     *
     * @return the whole duration, or null when the dose, the frequency or the duration unit is unknown
     */
    private static String durationForQuantity(RxPrescriptionData.Prescription rx, double quantity) {
        double takeMax = rx.getTakeMax();
        double nPerDay = findNPerDay(rx.getFrequencyCode());
        double nDays = findNDays(rx.getDurationUnit());
        if (takeMax == 0d || nPerDay == 0d || nDays == 0d) {
            return null;
        }
        return Integer.toString((int) (quantity / (takeMax * nPerDay * nDays)));
    }

    private static void setEmptyValues(RxPrescriptionData.Prescription rx) {
        rx.setRoute("");
        rx.setTakeMax(Float.parseFloat("0"));
        rx.setTakeMin(Float.parseFloat("0"));
        rx.setMethod("");
        rx.setFrequencyCode("");
        rx.setDuration("0");
        rx.setDurationUnit("");
        rx.setPrn(false);
        rx.setQuantity(Integer.toString(0));
        rx.setSpecial("");
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private static String changeToStandardFrequencyCode(String str) {
        String retVal = str;
        if (str.equalsIgnoreCase("daily")) {
            retVal = "OD";
        } else if (str.equalsIgnoreCase("once daily")) {
            retVal = "OD";
        } else if (str.equalsIgnoreCase("twice daily")) {
            retVal = "BID";
        } else if (str.equalsIgnoreCase("3x day")) {
            retVal = "TID";
        } else if (str.equalsIgnoreCase("3x daily")) {
            retVal = "TID";
        } else if (str.equalsIgnoreCase("4x day")) {
            retVal = "QID";
        } else if (str.equalsIgnoreCase("4x daily")) {
            retVal = "QID";
        } else if (str.equalsIgnoreCase("weekly")) {
            retVal = "Q1Week";
        } else if (str.equalsIgnoreCase("monthly")) {
            retVal = "Q1Month";
        }
        return retVal;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private static String convertWordToNumerical(String s) {

        s = s.trim();
        String retVal = s;
        if (s != null && !s.equalsIgnoreCase("null")) {
            if (s.equalsIgnoreCase("zero")) {
                retVal = "0";
            } else if (s.equalsIgnoreCase("one")) {
                retVal = "1";
            } else if (s.equalsIgnoreCase("two")) {
                retVal = "2";
            } else if (s.equalsIgnoreCase("three")) {
                retVal = "3";
            } else if (s.equalsIgnoreCase("four")) {
                retVal = "4";
            } else if (s.equalsIgnoreCase("five")) {
                retVal = "5";
            } else if (s.equalsIgnoreCase("six")) {
                retVal = "6";
            } else if (s.equalsIgnoreCase("seven")) {
                retVal = "7";
            } else if (s.equalsIgnoreCase("eight")) {
                retVal = "8";
            } else if (s.equalsIgnoreCase("nine")) {
                retVal = "9";
            } else if (s.equalsIgnoreCase("ten")) {
                retVal = "10";
            } else {
                if (!isStringToNumber(retVal)) retVal = "0";
            }
        } else retVal = "0";

        return retVal;
    }

    /**
     * Removes frequency wording that names a day ("3x day", "3 times a day") before the duration
     * scan, so that its "day" is not read as the duration unit.
     */
    private static String checkInstructionStr(String str, String frequencyPhrase) {
        String retVal = str;
        if (str.contains("3x day")) {
            retVal = retVal.replace("3x day", "");
        } else if (str.contains("4x day")) {
            retVal = retVal.replace("4x day", "");
        }
        if (!frequencyPhrase.isEmpty() && DAILY_COUNT_PHRASE.matcher(frequencyPhrase).matches()) {
            retVal = retVal.replace(frequencyPhrase, " ");
        }
        return retVal;
    }

    /**
     * Maps a written daily count ("3 times daily", "twice a day", "4x/day") to its frequency code;
     * any other phrase goes through the legacy synonym table.
     */
    // FindSecBugs IMPROPER_UNICODE: case folding of a parsed frequency word, not a security or authorization decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case folding of a parsed frequency word, not a security or authorization decision")
    private static String frequencyCodeForPhrase(String phrase) {
        Matcher count = DAILY_COUNT_PHRASE.matcher(phrase.trim());
        if (count.matches()) {
            return switch (count.group(1).toLowerCase(Locale.ROOT)) {
                case "once", "one", "1" -> "OD";
                case "twice", "two", "2" -> "BID";
                case "thrice", "three", "3" -> "TID";
                default -> "QID";
            };
        }
        return changeToStandardFrequencyCode(phrase);
    }

    /**
     * Parses the prescription's free-text instructions into dose, route, frequency,
     * duration and quantity fields, preserving the original instructions.
     *
     * <p>The number that opens the directions is read by what follows it (issue #3876):
     * <ul>
     *   <li>A product strength such as {@code 1000 mg} is turned into dispensable units per
     *       administration. When the selected product has one known strength in a comparable unit
     *       ({@code dosage} {@code "500.0mg"}), the strength is divided by it: two tablets here. A
     *       result that is not a whole number of units (or, for tablets, of halves or quarters), or a
     *       liquid product whose volume per dose is not written, leaves the dose unset. Without a
     *       comparable product strength (a custom drug, a combination product) one unit per
     *       administration is assumed, and a strength range keeps its proportions ({@code 500-1000 mg}
     *       is one to two units).</li>
     *   <li>A concentration such as {@code 250 mg/5 mL} or a dose per body size such as
     *       {@code 10 mg/kg} names the product and supplies no administered amount, so no dose or
     *       quantity is calculated from it.</li>
     *   <li>A volume, a unit count, a gram amount applied to the skin or a mucosa, a non-oral IU
     *       amount, and a gram or IU amount written with {@code /dose} or {@code per dose} are the
     *       administered dose as typed.</li>
     * </ul>
     * When the leading number named the product, an explicit amount written before the frequency
     * takes precedence: a count with its dosage form ({@code (2 tabs)}, {@code PO 2 tablets TID}),
     * a bare count before the route or the frequency, a volume or a unit count. An amount written as
     * a vehicle, a ceiling or a dispensing amount ({@code in 100 mL}, {@code max 8 tabs},
     * {@code #30 tabs}) is never the dose. Fractions keep their numerator and denominator until the
     * dispense quantity is rounded, once, upward, at the end; a mixed number ({@code 1 1/2}) is read
     * whole.
     *
     * @param rx prescription to update; null is ignored
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    public static void instrucParser(RxPrescriptionData.Prescription rx) {
        parseDirections(rx);
    }

    /**
     * The parse behind {@link #instrucParser}. It also hands back the exact course total the
     * directions calculate (dose per administration over the stated duration, as a fraction before
     * rounding), or null when nothing was calculated, so that a tapering course can sum its
     * segments exactly and round upward once.
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private static ExactAmount parseDirections(RxPrescriptionData.Prescription rx) {
        if (rx == null) {
            return null;
        }
        String instructions = rx.getSpecial();
        if (instructions == null) {
            instructions = "";
        }

        // A tapering course joins segments with "then"; each states its own dose, frequency and
        // duration, and the course dispenses their sum over their total days.
        if (TAPER_SEPARATOR.matcher(instructions).find() && sumTaperSegments(rx, instructions)) {
            return null;
        }

        // All values should be reset before each iteration as it is unknown if any previously entered
        // quantity values have been removed.
        setEmptyValues(rx);

        String route = "";
        String frequency = "";
        String duration = "0";
        String method = "";
        String durationUnit = "";
        String durationUnitSpec = "";
        boolean prn = false;
        String amountFrequency = "";
        String amountMethod = "";
        String takeMinFrequency = "";
        String takeMaxFrequency = "";
        String takeMinMethod = "";
        String takeMaxMethod = "";
        String takeMin = "0";
        String takeMax = "0";
        String durationSpec = "";
        int quantity = 0;
        int frequencyStart = -1;
        NumberToken fractionBeforeFrequency = null;

        //do we have some policies/restrictions we want to run?
        List<String> policyViolations = RxInstructionPolicy.checkInstructions(instructions.trim());

        // Only whether "prn" stands as a word matters. The last pattern starts where a run of
        // whitespace starts, so a long run of spaces is scanned once, not once per position.
        String[] prns = {"\\s(?i)prn$", "^(?i)prn\\s", "(?<!\\s)\\s++(?i)prn\\s"};
        for (String s : prns) {
            Pattern prnP = Pattern.compile(s);
            Matcher prnM = prnP.matcher(instructions);
            if (prnM.find()) {
                prn = true;
            }
        }

        String[] routes = {"\\s(?i)PO$", "\\s(?i)SL$", "\\s(?i)IM$", "\\s(?i)SC$", "\\s(?i)PATCH$", "\\s(?i)TOP\\.$", "\\s(?i)INH$", "\\s(?i)SUPP$", "\\s(?i)O\\.D\\.$", "\\s(?i)O\\.S\\.$", "\\s(?i)O\\.U\\.$", "\\s(?i)OD$", "\\s(?i)OS$", "\\s(?i)OU$", "\\s(?i)PO\\s", "\\s(?i)SL\\s", "\\s(?i)IM\\s", "\\s(?i)SC\\s", "\\s(?i)PATCH\\s", "\\s(?i)TOP\\.\\s", "\\s(?i)INH\\s", "\\s(?i)SUPP\\s", "\\s(?i)O\\.D\\.\\s", "\\s(?i)O\\.S\\.\\s", "\\s(?i)O\\.U\\.\\s", "\\s(?i)OD\\s", "\\s(?i)OS\\s", "\\s(?i)OU\\s"};
        String[] frequences = {"\\s(?i)OD\\s", "\\s(?i)BID\\s", "\\s(?i)TID\\s", "\\s(?i)QID\\s", "\\s(?i)Q1H\\s", "\\s(?i)Q2H\\s", "\\s(?i)Q1-2H\\s", "\\s(?i)Q3-4H\\s", "\\s(?i)Q4H\\s", "\\s(?i)Q4-6H\\s", "\\s(?i)Q6H\\s", "\\s(?i)Q8H\\s", "\\s(?i)Q12H\\s", "\\s(?i)QAM\\s", "\\s(?i)QPM\\s", "\\s(?i)QHS\\s", "\\s(?i)Q1Week\\s", "\\s(?i)weekly\\s", "\\s(?i)Q2Week\\s", "\\s(?i)Q1Month\\s", "\\s(?i)Q3Month\\s", "\\s(?i)monthly\\s", "\\s(?i)once daily\\s", "\\s(?i)twice daily\\s", "\\s(?i)3x day\\s",
                "\\s(?i)4x day\\s", "\\s(?i)3x daily\\s", "\\s(?i)4x daily\\s", "\\s(?i)OD$", "\\s(?i)BID$", "\\s(?i)TID$", "\\s(?i)QID$", "\\s(?i)Q1H$", "\\s(?i)Q2H$", "\\s(?i)Q1-2H$", "\\s(?i)Q3-4H$", "\\s(?i)Q4H$", "\\s(?i)Q4-6H$", "\\s(?i)Q6H$", "\\s(?i)Q8H$", "\\s(?i)Q12H$", "\\s(?i)QAM$", "\\s(?i)QPM$", "\\s(?i)QHS$", "\\s(?i)Q1Week$", "\\s(?i)weekly$", "\\s(?i)Q2Week$", "\\s(?i)Q1Month$", "\\s(?i)Q3Month$", "\\s(?i)monthly$", "\\s(?i)once daily$", "\\s(?i)twice daily$", "\\s(?i)3x day$",
                "\\s(?i)4x day$", "\\s(?i)3x daily$", "\\s(?i)4x daily$",
                TIMES_A_DAY_PHRASES[0], TIMES_A_DAY_PHRASES[1], TIMES_A_DAY_PHRASES[2], TIMES_A_DAY_PHRASES[3],
                "\\s(?i)daily\\s", "\\s(?i)daily$", // put at last because if frequency is 'twice daily', it will first be detected as 'daily'
        };
        // A method word is a whole word ("intake" and "mistake" are not "take"; "Take1" still is).
        // Which occurrence opens the dose is decided in scanInstruction: a trailing ", take with
        // food" after the dose and frequency is advice, while "Amoxicillin 250 mg/5 mL: take 7.5 mL"
        // and "Please take 1 tab" open the dose although the method is not the first word.
        String[] methods = {"(?i)\\btake(?![a-z])", "(?i)\\b" + METHOD_APPLY + "(?![a-z])", "(?i)\\b" + METHOD_RUB_WELL_IN + "\\b"};
        // A duration unit ends at whitespace, punctuation or the end of the text ("x 7 days, take
        // with food"), never inside a longer word ("daily", "months" against "m") and never before a
        // slash: "w/" (with) and "d/c" (discontinue) are abbreviations, not a week or a day.
        // A unit pattern starts only where a run of whitespace starts and takes the whole run at
        // once, so a long run of spaces in the directions is scanned in linear time; a pattern that
        // could start anywhere in the run backtracked over the rest of it at every position.
        String[] durationUnits = {"(?<!\\s)\\s++(?i)days(?![a-z/])", "(?<!\\s)\\s++(?i)weeks(?![a-z/])", "(?<!\\s)\\s++(?i)months(?![a-z/])", "(?<!\\s)\\s++(?i)day(?![a-z/])", "(?<!\\s)\\s++(?i)week(?![a-z/])", "(?<!\\s)\\s++(?i)month(?![a-z/])", "(?<!\\s)\\s++(?i)d(?![a-z/])", "(?<!\\s)\\s++(?i)w(?![a-z/])", "(?<!\\s)\\s++(?i)m(?![a-z/])", "(?<!\\s)\\s++(?i)mo(?![a-z/])"};
        String[] durUnits2 = {"\\s[0-9]+(?i)days(?![a-z/])", "\\s[0-9]+(?i)weeks(?![a-z/])", "\\s[0-9]+(?i)months(?![a-z/])", "\\s[0-9]+(?i)day(?![a-z/])", "\\s[0-9]+(?i)week(?![a-z/])", "\\s[0-9]+(?i)month(?![a-z/])", "\\s[0-9]+(?i)d(?![a-z/])", "\\s[0-9]+(?i)w(?![a-z/])", "\\s[0-9]+(?i)m(?![a-z/])", "\\s[0-9]+(?i)mo(?![a-z/])"};

        for (String s : routes) {
            Pattern p = Pattern.compile(s);
            Matcher matcher = p.matcher(instructions);
            if (matcher.find()) {
                route = (instructions.substring(matcher.start(), matcher.end())).trim();
                if (route.equalsIgnoreCase("OD")) {
                    p("route is OD");

                    //remove OD from instructions
                    String part = instructions.substring(0, matcher.start()) + " " + instructions.substring(matcher.end());
                    //if route =od, check if there is a valid frequency, there is one, then route is od.
                    //if not, set the route="", keep looping. then set frequency to be od;
                    p("part is " + part);
                    Pattern fPattern = Pattern.compile("\\s(?i)OD\\s*");
                    Matcher fMatcher = fPattern.matcher(part);
                    String frequencyStr = "";
                    if (fMatcher.find()) {
                        frequencyStr = part.substring(fMatcher.start(), fMatcher.end());
                        break;
                    }
                    if (frequencyStr.equals("")) {
                        frequency = "OD";
                        route = "";
                        continue;
                    } else {
                        frequency = frequencyStr;
                        break;
                    }
                } else {
                    break;
                }

            }
        }

        p("route", route);

        //find frequency
        String matchedFrequencyPhrase = "";
        for (String s : frequences) {
            Pattern p = Pattern.compile(s);
            Matcher matcher = p.matcher(instructions);
            if (matcher.find()) {
                String matchedFrequency = (instructions.substring(matcher.start(), matcher.end())).trim();
                matchedFrequencyPhrase = matchedFrequency;
                frequency = frequencyCodeForPhrase(matchedFrequency);
                frequencyStart = matcher.start();
                //     p("here11", instructions);
                //since "\\s+[0-9]+-[0-9]+\\s+" is a case in "\\s+[0-9]+\\s+", check the latter regex first.
                NumberRange frequencyRange = findRangeBeforeFrequency(instructions, matcher.start());
                if (frequencyRange != null) {
                    takeMinFrequency = frequencyRange.min;
                    takeMaxFrequency = frequencyRange.max;
                } else {
                    NumberToken frequencyAmount = findDecimalBeforeFrequency(instructions, matcher.start());
                    if (frequencyAmount == null) {
                        // Whether a fraction here is the dose depends on the leading amount, which is
                        // read below; keep it aside until then.
                        fractionBeforeFrequency = findFractionBefore(instructions, matcher.start());
                    }
                    if (frequencyAmount != null) {
                        amountFrequency = frequencyAmount.value;
                    } else if (fractionBeforeFrequency == null) {
                        p("word amount");
                        for (String word : zeroToTen) {
                            String r1 = "\\s" + word + "\\s*" + Pattern.quote(frequency);
                            String r2 = "^" + word + "\\s*" + Pattern.quote(frequency); //start at the begin of instructions
                            Pattern p5 = Pattern.compile(r1);
                            Matcher m5 = p5.matcher(instructions);
                            p("pattern word =" + r1);
                            if (m5.find()) {
                                amountFrequency = instructions.substring(m5.start(), m5.end());
                                amountFrequency = amountFrequency.replace(frequency, "").trim();
                                p("amountFreq=" + amountFrequency);
                                amountFrequency = convertWordToNumerical(amountFrequency);
                                p("num amountFreq=" + amountFrequency);
                                break;
                            }
                            p5 = Pattern.compile(r2);
                            m5 = p5.matcher(instructions);
                            if (m5.find()) {
                                amountFrequency = instructions.substring(m5.start(), m5.end());
                                amountFrequency = amountFrequency.replace(frequency, "").trim();
                                p("amountFreq=" + amountFrequency);
                                amountFrequency = convertWordToNumerical(amountFrequency);
                                p("num amountFreq=" + amountFrequency);
                                break;
                            }
                        }
                    }
                }
                //the string before frequency maybe the amount of drug
                //check if the string is a number, if it is, get the number
                //if not a number, check if it has "min-max" pattern, if yes, get min and max, if not, ignore

                break;
            }
        }
        //check if method is specified, if yes, check the number after method, which maybe the number to take for a frequency.
		//if no, check if a number is specified at beginning.
		if (!instructions.isEmpty()) {
			InstructionSegment instructionSegment = scanInstruction(instructions, methods, route, rx, frequencyStart);
            // A strength describes the product, not a count of its units: it becomes units per
            // administration (issue #3876). A product-only lead (a concentration, a dose per body
            // size) supplies no amount, so nothing is assumed for it. Explicit amounts written
            // before the frequency take precedence below.
            if (instructionSegment.productAmount == AmountKind.STRENGTH) {
                String[] units = keepsTypedGramAmount(instructionSegment, rx)
                        ? new String[] {instructionSegment.productMin, instructionSegment.productMax}
                        : unitsPerDose(instructionSegment, rx);
                takeMin = units[0];
                takeMax = units[1];
            }
			if (instructionSegment.method != null) {
				method = instructionSegment.method;
                    }
			if (instructionSegment.takeMin != null) {
				takeMinMethod = instructionSegment.takeMin;
                    }
			if (instructionSegment.takeMax != null) {
				takeMaxMethod = instructionSegment.takeMax;
			}
			if (instructionSegment.amountMethod != null) {
				amountMethod = instructionSegment.amountMethod;
                            }
			if (instructionSegment.amountFrequency != null) {
				amountFrequency = instructionSegment.amountFrequency;
                        }
            // A fraction just before the frequency is the dose when it completes a mixed number
            // ("Take 1 1/2 TID") or when nothing earlier gave an amount ("1000 mg po 1/2 tid"). It
            // never replaces an explicit count, where it is a schedule or a ratio rather than a dose
            // ("Take 1 tab x 3/7 BID", "if SBP > 140/90 BID").
            if (fractionBeforeFrequency != null && (fractionBeforeFrequency.mixedNumber
                    || (!instructionSegment.hasLeadingAmount() && isPlainDoseFraction(instructions, fractionBeforeFrequency)))) {
                amountFrequency = fractionBeforeFrequency.value;
            }
            // "250 mg/5 mL PO 10 mL TID", "1000 mg (2 tabs) po tid": when the leading number named
            // the product rather than a dose, an explicit amount written before the frequency is the
            // administered dose (review of #5077). A leading count such as "Take 1 sachet in 125 mL
            // BID" is kept, directions that name no product ("Mix medication in 100 mL BID") stay
            // unparsed, and vehicles, ceilings and dispensing amounts are skipped.
            boolean leadingNumberNamedProduct = instructionSegment.productAmount != null;
            if (leadingNumberNamedProduct && frequencyStart >= 0 && amountFrequency.isEmpty() && takeMinFrequency.isEmpty()) {
                UnitAmount unitAmount = findAmountWithUnitBeforeFrequency(instructions, frequencyStart, instructionSegment);
                if (unitAmount == null) {
                    unitAmount = findExplicitAmountAfterProduct(instructions, instructionSegment.productEnd, frequencyStart);
                }
                if (unitAmount != null && unitAmount.range()) {
                    takeMinFrequency = unitAmount.min();
                    takeMaxFrequency = unitAmount.max();
                } else if (unitAmount != null) {
                    amountFrequency = unitAmount.min();
                }
            }
                    }

		/*    p(takeMinMethod);
		p(takeMaxMethod);
		p(takeMinFrequency);
		p(takeMaxFrequency);*/
        if (!takeMinMethod.equals("") && takeMinFrequency.equals("")) {

            takeMin = takeMinMethod;
            takeMax = takeMaxMethod;
        } else if (takeMinMethod.equals("") && !takeMinFrequency.equals("")) {
            takeMin = takeMinFrequency;
            takeMax = takeMaxFrequency;
        } else if (!takeMinMethod.equals("") && !takeMinFrequency.equals("")) {// when method and frequency both gives values of takemin and takemax,
            //assume use the frequency one is correct.
            takeMin = takeMinFrequency;
            takeMax = takeMaxFrequency;
        }
        //check if a frequency is specified, if yes, check the number before the freq, which is the number to have, check if the number match the previous.
        //if yes, use that as the quantity per frequency, if not, use the frequency number as quantity.
        if (!amountMethod.equals("") && amountFrequency.equals("")) {
            takeMin = amountMethod;
            takeMax = takeMin;
        } else if (amountMethod.equals("") && !amountFrequency.equals("")) {
            takeMin = amountFrequency;
            takeMax = takeMin;
        } else if (!amountMethod.equals("") && !amountFrequency.equals("")) { // when method and frequency both gives values of takemin and takemax,
            //assume use the frequency one is correct.
            takeMin = amountFrequency;
            takeMax = amountFrequency;
        }

        //calculate the number of pills to have per frequency which is used to calculate the duration later on.
        //from frequency code we can deduce a duration unit.
        //check if a durationunit is already specified, if not, use that, if yes, check if they are equal, if not output an warning and use specified.
        for (String s : durationUnits) {
            // p(instructions);
            // p(s);
            String instructionToCheck = checkInstructionStr(instructions, matchedFrequencyPhrase);
            Pattern p = Pattern.compile(s);
            Matcher m = p.matcher(instructionToCheck);

            if (m.find()) {
                durationUnitSpec = (instructionToCheck.substring(m.start(), m.end())).trim();
                // The number and its unit must come from one match: in "on alternate days x 10
                // days" the first unit word carries no number, and cutting the text between the
                // two matches threw when the number came after that first word.
                // The number likewise starts where its digits start and takes them all at once.
                Pattern p1 = Pattern.compile("(?<![0-9])([0-9]++)" + s);
                Matcher m1 = p1.matcher(instructionToCheck);
                if (m1.find()) {
                    durationSpec = m1.group(1);
                    duration = durationSpec;
                    durationUnitSpec = instructionToCheck.substring(m1.end(1), m1.end()).trim();
                    break;
                }
            }
        }

        //match the pattern when there is no space between number and durationUnit.
        if (durationUnitSpec.equals("")) {
            for (String s : durUnits2) {
                String instructionToCheck = checkInstructionStr(instructions, matchedFrequencyPhrase);
                Pattern p = Pattern.compile(s);
                Matcher m = p.matcher(instructionToCheck);

                if (m.find()) {
                    p("FOUND");
                    p("instructionToCheck=" + instructionToCheck);
                    p(s);
                    String str1 = instructionToCheck.substring(m.start(), m.end());
                    //get numUnit out
                    Pattern p1 = Pattern.compile("[0-9]+");
                    Matcher m1 = p1.matcher(str1);
                    if (m1.find()) {
                        duration = str1.substring(m1.start(), m1.end());
                        durationUnitSpec = (str1.substring(m1.end())).trim();
                        break;
                    }
                }
            }
        }

        // n/7, n/52 and n/12 state a duration in days, weeks or months. The shorthand is read only
        // when no duration with a unit was written, and only after the frequency or introduced by
        // "x" or "for", so that a dose fraction such as "1/2 tab" or a mixed number is never taken
        // for one.
        if (durationUnitSpec.isEmpty()) {
            Matcher shorthand = SHORTHAND_DURATION.matcher(instructions);
            while (shorthand.find()) {
                boolean introduced = shorthand.group(1) != null;
                boolean afterFrequency = frequencyStart >= 0 && shorthand.start(2) > frequencyStart;
                if (introduced || afterFrequency) {
                    duration = shorthand.group(2);
                    durationUnitSpec = switch (shorthand.group(3)) {
                        case "7" -> "days";
                        case "52" -> "weeks";
                        default -> "months";
                    };
                    break;
                }
            }
        }

        //if durationUnit is not specified, deduce it
        if (durationUnitSpec.equals("")) {
            //    p("here?? if");
            String[] freq1 = {"\\s*(?i)OD\\s*", "\\s*(?i)BID\\s*", "\\s*(?i)TID\\s*", "\\s*(?i)QID\\s*", "\\s*(?i)Q1H\\s*", "\\s*(?i)Q2H\\s*", "\\s*(?i)Q1-2H\\s*", "\\s*(?i)Q3-4H\\s*", "\\s*(?i)Q4H\\s*", "\\s*(?i)Q4-6H\\s*", "\\s*(?i)Q6H\\s*", "\\s*(?i)Q8H\\s*", "\\s*(?i)Q12H\\s*", "\\s*(?i)QAM\\s*", "\\s*(?i)QPM\\s*", "\\s*(?i)QHS\\s*", "\\s*(?i)once daily\\s*", "\\s*(?i)twice daily\\s*", "\\s*(?i)3x day\\s*", "\\s*(?i)4x day\\s*", "\\s*(?i)3x daily\\s*", "\\s*(?i)4x daily\\s*", "\\s*(?i)daily\\s*"// put at last because if frequency is 'twice daily', it will first be detected as 'daily'
            }; //QPM is once a day in the evening, qhs once a day at night.
            String[] freq2 = {"\\s*(?i)Q1Week\\s*", "\\s*(?i)Q2Week\\s*"};
            String[] freq3 = {"\\s*(?i)Q1Month\\s*", "\\s*(?i)Q3Month\\s*"};
            boolean found = false;
            for (String f1 : freq1) {
                Pattern p = Pattern.compile(f1);
                Matcher m = p.matcher(frequency);
                // p(f1);
                // p(frequency);
                if (m.find()) {

                    durationUnit = "D";
                    found = true;
                }
            }

            if (!found) {
                for (String f2 : freq2) {
                    Pattern p2 = Pattern.compile(f2);
                    Matcher m2 = p2.matcher(frequency);
                    if (m2.find()) {
                        durationUnit = "W";
                        found = true;
                    }
                }
            }

            if (!found) {
                for (String f3 : freq3) {
                    Pattern p3 = Pattern.compile(f3);
                    Matcher m3 = p3.matcher(frequency);
                    if (m3.find()) {
                        durationUnit = "M";
                        found = true;
                    }
                }
            }
        } else {

            //D, W, M
            if (durationUnitSpec.equalsIgnoreCase("week") || durationUnitSpec.equalsIgnoreCase("weeks") || durationUnitSpec.equalsIgnoreCase("w")) {

                durationUnit = "W";
            } else if (durationUnitSpec.equalsIgnoreCase("day") || durationUnitSpec.equalsIgnoreCase("days") || durationUnitSpec.equalsIgnoreCase("d")) {

                durationUnit = "D";
            } else if (durationUnitSpec.equalsIgnoreCase("month") || durationUnitSpec.equalsIgnoreCase("months") || durationUnitSpec.equalsIgnoreCase("m") || durationUnitSpec.equalsIgnoreCase("mo")) {

                durationUnit = "M";
            }
        }

        // Amounts stay as text (decimals, or fractions such as 5/6) until here, so a fraction
        // is not approximated before the quantity is rounded.
        ExactAmount minimumDose = ExactAmount.parse(takeMin);
        ExactAmount maximumDose = ExactAmount.parse(takeMax);
        //make sure min is smaller than max
        if (maximumDose.isLessThan(minimumDose)) {
            //      p("max<min");
            ExactAmount swap = minimumDose;
            minimumDose = maximumDose;
            maximumDose = swap;
        }

        double nPerDay = 0d; //number of drugs per day
        double nDays = 0d; //number of days per duration unit

        boolean isUnitNameUsed = true;
        if (rx.getUnitName() == null || rx.getUnitName().trim().length() == 0) isUnitNameUsed = false;
        else if (rx.getUnitName().equalsIgnoreCase("null")) isUnitNameUsed = false;
        else isUnitNameUsed = true;
        //if duration is 0 or null or length==0, it means duration is not specified by user
        //if quantity, frequency, durationUnit are valid values, isUnitNameUsed==false
        //yes, calculate duration based on quantity because duration is not specified
        //no, leave duration an invalid value
        //else if duration is a valid value
        //if frequency, durationUnit, takeMax are valid too
        //yes, calculate quantity
        //no, leave quantity intact.
        //--start new code
        rx.setQuantity(rx.getQuantity().trim());
        ExactAmount courseTotal = null;
        if (duration.equals("0") || duration.length() == 0 || duration == null) {//if duration is not specified, find duration based on quantity
            rx.setDurationSpecifiedByUser(false);
            if (!isUnitNameUsed && rx.getQuantity() != null && !rx.getQuantity().equalsIgnoreCase("null") && !rx.getQuantity().equals("") && !durationUnit.equals("") && !frequency.equals("") && !maximumDose.isZero()) {
                quantity = Integer.parseInt(rx.getQuantity());
                double quantityD = quantity;
                nPerDay = findNPerDay(frequency);
                nDays = findNDays(durationUnit);
                double durationD = quantityD / (maximumDose.doubleValue() * nPerDay * nDays);
                Integer durationI = (int) durationD;
                duration = durationI.toString();
                rx.setDuration(duration);
            } else rx.setDuration("0");
        } else {//if duration is valid, find quantity based on duration
            rx.setDurationSpecifiedByUser(true);
            rx.setDuration(duration);
            if (!isUnitNameUsed && !durationUnit.equals("") && !minimumDose.isZero() && !maximumDose.isZero() && !frequency.equals("")) {
                courseTotal = exactDispenseTotal(maximumDose, duration, durationUnit, frequency);
                quantity = courseTotal.ceilingUnits();
            }

        }

        //if drug route is in prescript is different from specified, set it to specified.
        if (!route.equals("") && !route.equalsIgnoreCase(rx.getRoute())) {
            rx.setRoute(route);
        }

        rx.setTakeMax(maximumDose.floatValue());
        rx.setTakeMin(minimumDose.floatValue());
        rx.setMethod(method);
        rx.setFrequencyCode(frequency);
        rx.setDurationUnit(durationUnit);
        rx.setPrn(prn);
        if (!isUnitNameUsed && quantity != 0) {
            rx.setQuantity(Integer.toString(quantity));
        }

        rx.setSpecial(instructions);

        if (rx.isCustomNote()) {
            rx.setQuantity(null);
            rx.setUnitName(null);
        }

        rx.setPolicyViolations(policyViolations);

        p("below set special");
        HashMap<String, Object> hm = new HashMap<String, Object>();
        hm.put("takeMin", rx.getTakeMin());
        hm.put("takeMax", rx.getTakeMax());
        hm.put("method", rx.getMethod());
        hm.put("route", rx.getRoute());
        hm.put("frequency", rx.getFrequencyCode());
        hm.put("duration", rx.getDuration());
        hm.put("durationUnit", rx.getDurationUnit());
        hm.put("prn", rx.getPrn());
        hm.put("quantity", rx.getQuantity());
        hm.put("policyViolations", policyViolations);

        return courseTotal;
    }

    /**
     * Reads a tapering course whose segments are joined by "then" or "followed by", such as
     * {@code Take 3 tabs daily x 3 days, then 2 tabs daily x 3 days, then 1 tab daily x 3 days}. Every
     * segment must state a dose, a frequency and a duration; the course then dispenses the segments'
     * exact totals added together and rounded upward once (1 1/2 tablets for 3 days then 1/2 for 3 days
     * is 6, not 5 + 2) and lasts their total number of days, while the card shows the first segment's
     * dose, frequency, method and route. A segment
     * that cannot be read on its own (an open-ended "then stop") leaves the directions to the ordinary
     * single-segment parse, and a quantity typed with its own unit is never recalculated.
     *
     * @return true when the prescription was filled from the segments
     */
    private static boolean sumTaperSegments(RxPrescriptionData.Prescription rx, String instructions) {
        if (hasQuantityUnit(rx)) {
            return false;
        }
        String[] segments = TAPER_SEPARATOR.split(instructions);
        int segmentCount = segments.length;
        // A closing "then stop" states no dose; with only one dosing segment left, the ordinary
        // parse reads the directions (and keeps a duration unit other than days).
        if (segmentCount > 1 && isTaperEndMarker(segments[segmentCount - 1])) {
            segmentCount--;
        }
        if (segmentCount < 2) {
            return false;
        }
        ExactAmount courseTotal = ExactAmount.ZERO;
        int totalDays = 0;
        float firstTakeMin = 0f;
        float firstTakeMax = 0f;
        String firstFrequency = "";
        String firstMethod = "";
        String firstRoute = "";
        boolean firstPrn = false;
        try {
            for (int i = 0; i < segmentCount; i++) {
                String segment = stripTrailingCommas(segments[i]);
                if (segment.isEmpty()) {
                    return false;
                }
                rx.setSpecial(segment);
                ExactAmount segmentTotal = parseDirections(rx);
                if (segmentTotal == null || !rx.isDurationSpecifiedByUser() || rx.getFrequencyCode().isEmpty()
                        || rx.getTakeMax() == 0f) {
                    return false;
                }
                courseTotal = courseTotal.plus(segmentTotal);
                totalDays += (int) Math.round(Double.parseDouble(rx.getDuration()) * findNDays(rx.getDurationUnit()));
                if (i == 0) {
                    firstTakeMin = rx.getTakeMin();
                    firstTakeMax = rx.getTakeMax();
                    firstFrequency = rx.getFrequencyCode();
                    firstMethod = rx.getMethod();
                    firstRoute = rx.getRoute();
                    firstPrn = rx.getPrn();
                }
            }
        } finally {
            // The original text is what is saved and shown, whether or not the segments were read.
            rx.setSpecial(instructions);
        }
        rx.setTakeMin(firstTakeMin);
        rx.setTakeMax(firstTakeMax);
        rx.setFrequencyCode(firstFrequency);
        rx.setMethod(firstMethod);
        rx.setRoute(firstRoute);
        rx.setPrn(firstPrn);
        rx.setDuration(Integer.toString(totalDays));
        rx.setDurationUnit("D");
        rx.setDurationSpecifiedByUser(true);
        rx.setQuantity(Integer.toString(courseTotal.ceilingUnits()));
        if (rx.isCustomNote()) {
            rx.setQuantity(null);
            rx.setUnitName(null);
        }
        rx.setPolicyViolations(RxInstructionPolicy.checkInstructions(instructions.trim()));
        return true;
    }

    /** Words that close a tapering course without a dose: "then stop", "then discontinue", "then d/c". */
    private static final Set<String> TAPER_END_MARKERS = Set.of("stop", "discontinue", "d/c", "dc");

    /** True when the segment is only an end marker such as "stop" or "discontinue.", in any case. */
    // FindSecBugs IMPROPER_UNICODE: case folding of a parsed instruction word, not a security or authorization decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case folding of a parsed instruction word, not a security or authorization decision")
    private static boolean isTaperEndMarker(String segment) {
        String word = stripTrailingCommas(segment).toLowerCase(Locale.ROOT);
        while (word.endsWith(".")) {
            word = word.substring(0, word.length() - 1).trim();
        }
        return TAPER_END_MARKERS.contains(word);
    }

    /** Trims a taper segment and removes the comma that preceded "then" (", then 2 tabs"). */
    private static String stripTrailingCommas(String segment) {
        String trimmed = segment.trim();
        while (trimmed.endsWith(",")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    /** True when the quantity was typed with its own unit ("30 mL"), which the parser never recalculates. */
    // FindSecBugs IMPROPER_UNICODE: comparison with the literal "null" the page sends, not a security or authorization decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "comparison with the literal null the page sends, not a security or authorization decision")
    private static boolean hasQuantityUnit(RxPrescriptionData.Prescription rx) {
        String unitName = rx.getUnitName();
        return unitName != null && !unitName.trim().isEmpty() && !unitName.equalsIgnoreCase("null");
    }

    /**
     * The exact course total for the maximum dose over the duration, as a fraction: the caller
     * rounds it upward once ({@link ExactAmount#ceilingUnits()}), so a fractional dose such as 5/6
     * tablet for six days gives five, not six, and a tapering course can add its segments before
     * rounding. Periodic frequencies use exact day intervals.
     */
    // FindSecBugs IMPROPER_UNICODE: normalization of internal frequency codes, not a security or authorization decision. See docs/static-analysis-workflows.md.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "normalization of internal frequency codes, not a security or authorization decision")
    private static ExactAmount exactDispenseTotal(ExactAmount maximumDose, String duration, String durationUnit, String frequency) {
        BigDecimal total = maximumDose.numerator().multiply(new BigDecimal(duration))
                .multiply(BigDecimal.valueOf(findNDays(durationUnit)));
        BigDecimal divisor = maximumDose.denominator();
        // Use exact day intervals instead of the approximate daily frequency for
        // weekly/monthly prescriptions, so an exact total does not round up twice.
        int intervalDays = switch (frequency.toUpperCase(Locale.ROOT)) {
            case "Q1WEEK" -> 7;
            case "Q2WEEK" -> 14;
            case "Q1MONTH" -> 30;
            case "Q3MONTH" -> 90;
            default -> 0;
        };
        if (intervalDays == 0) {
            total = total.multiply(BigDecimal.valueOf(findNPerDay(frequency)));
        } else {
            divisor = divisor.multiply(BigDecimal.valueOf(intervalDays));
        }
        return new ExactAmount(total, divisor);
    }

    public static boolean isStringToNumber(String s) {//see if string contains decimal or integer
        if (s == null) {
            return false;
        }
        String trimmed = s.trim();
        NumberToken token = findDecimalAt(trimmed, 0);
        return token != null && token.start == 0 && token.end == trimmed.length();
    }

    private static NumberRange findRangeBeforeFrequency(String text, int frequencyStart) {
        return findRangeBefore(text, frequencyStart);
    }

    private static NumberToken findDecimalBeforeFrequency(String text, int frequencyStart) {
        return findDecimalBefore(text, frequencyStart);
    }

    private static NumberRange findRangeBefore(String text, int endExclusive) {
        int end = skipWhitespaceBackward(text, endExclusive);
        NumberToken max = findDecimalBefore(text, end, false);
        if (max == null || max.end != end) {
            return null;
        }

        int hyphenIndex = skipWhitespaceBackward(text, max.start) - 1;
        if (hyphenIndex < 0 || text.charAt(hyphenIndex) != '-') {
            return null;
        }

        NumberToken min = findDecimalBefore(text, hyphenIndex);
        if (min == null || min.end != hyphenIndex) {
            return null;
        }

        return new NumberRange(min.start, min.value, max.value, max.end);
    }

    private static NumberRange findRangeAt(String text, int start) {
        NumberToken min = findDecimalAt(text, start);
        if (min == null) {
            return null;
        }
        // "1-2 tabs" and "1 - 2 tabs" are the same range.
        int hyphen = skipWhitespaceForward(text, min.end);
        if (hyphen >= text.length() || text.charAt(hyphen) != '-') {
            return null;
        }

        int maxStart = skipWhitespaceForward(text, hyphen + 1);
        NumberToken max = findDecimalAt(text, maxStart);
        if (max == null) {
            return null;
        }

        return new NumberRange(min.start, min.value, max.value, max.end);
    }

    private static NumberToken findDecimalBefore(String text, int endExclusive) {
        return findDecimalBefore(text, endExclusive, true);
    }

    private static NumberToken findDecimalBefore(String text, int endExclusive, boolean requireBoundaryBefore) {
        int end = skipWhitespaceBackward(text, endExclusive);
        if (end == 0 || !isDigit(text.charAt(end - 1))) {
            return null;
        }

        int start = end;
        while (start > 0 && isDigit(text.charAt(start - 1))) {
            start--;
        }

        if (start > 0 && text.charAt(start - 1) == '.') {
            int dotIndex = start - 1;
            int tokenStart = dotIndex;
            while (tokenStart > 0 && isDigit(text.charAt(tokenStart - 1))) {
                tokenStart--;
            }
            if (requireBoundaryBefore && !hasTokenBoundaryBefore(text, tokenStart)) {
                return null;
            }
            return new NumberToken(text, tokenStart, end);
        }

        if (requireBoundaryBefore && !hasTokenBoundaryBefore(text, start)) {
            return null;
        }
        return new NumberToken(text, start, end);
    }

    private static NumberToken findDecimalAt(String text, int start) {
        if (start < 0 || start >= text.length()) {
            return null;
        }

        int index = start;
        while (index < text.length() && isDigit(text.charAt(index))) {
            index++;
        }

        if (index < text.length() && text.charAt(index) == '.') {
            int decimalPoint = index;
            index++;
            int fractionStart = index;
            while (index < text.length() && isDigit(text.charAt(index))) {
                index++;
            }
            if (index > fractionStart) {
                return new NumberToken(text, start, index);
            }
            if (decimalPoint > start) {
                return new NumberToken(text, start, decimalPoint);
            }
            return null;
        }

        if (index > start) {
            return new NumberToken(text, start, index);
        }

        return null;
    }

    /** Finds a leading fraction whose suffix is whitespace, an amount unit, or the end of the instruction. */
    private static String findFractionAt(String text, int start) {
        if (start < 0 || start >= text.length() || !isDigit(text.charAt(start))) {
            return null;
        }

        int index = start;
        while (index < text.length() && isDigit(text.charAt(index))) {
            index++;
        }

        if (index >= text.length() || text.charAt(index) != '/') {
            return null;
        }

        index++;
        int denominatorStart = index;
        while (index < text.length() && isDigit(text.charAt(index))) {
            index++;
        }
        if (index == denominatorStart) {
            return null;
        }
        if (!hasTokenBoundaryAt(text, index) && !hasAmountUnitAfter(text, index)) {
            return null;
        }

        return text.substring(start, index);
    }

    private static int skipWhitespaceBackward(String text, int endExclusive) {
        int index = Math.min(endExclusive, text.length());
        while (index > 0 && Character.isWhitespace(text.charAt(index - 1))) {
            index--;
        }
        return index;
    }

    private static int skipWhitespaceForward(String text, int start) {
        int index = Math.max(0, start);
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private static boolean hasTokenBoundaryAt(String text, int index) {
        return index >= text.length() || Character.isWhitespace(text.charAt(index));
    }

    private static boolean hasTokenBoundaryBefore(String text, int index) {
        return index <= 0 || Character.isWhitespace(text.charAt(index - 1));
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /** Recognizes supported amount-unit suffixes without applying route or method context. */
    private static boolean hasAmountUnitAfter(String instructions, int amountEnd) {
        return STRENGTH_UNIT.matcher(instructions)
                .region(skipWhitespaceForward(instructions, amountEnd), instructions.length()).lookingAt()
                || hasDoseUnitAfter(instructions, amountEnd);
    }

    /**
     * Reads what the unit after an amount says about it: a product strength, product-only
     * information, or an administered amount. Any unit followed by a concentration denominator
     * or a body-size basis ({@code /mL}, {@code /kg}) names the product only; grams and IU written
     * with {@code /dose} or {@code per dose} are administered; grams applied to the skin or a
     * mucosa, IU not given by mouth and mEq or mmol given by a written non-oral route are administered;
     * the remaining strength units are strengths. An amount with no recognized unit is
     * administered (callers check its boundary).
     */
    private static AmountKind classifyAmount(String instructions, int amountEnd, InstructionSegment segment) {
        int unitStart = skipWhitespaceForward(instructions, amountEnd);
        Matcher unit = STRENGTH_UNIT.matcher(instructions).region(unitStart, instructions.length());
        if (!unit.lookingAt()) {
            unit = DOSE_UNIT.matcher(instructions).region(unitStart, instructions.length());
            return unit.lookingAt() && hasDenominatorAfterUnit(instructions, unit.end())
                    ? AmountKind.PRODUCT_ONLY : AmountKind.ADMINISTERED;
        }
        if (hasDenominatorAfterUnit(instructions, unit.end())) {
            return AmountKind.PRODUCT_ONLY;
        }
        return classifyStrengthUnit(instructions, unit.group(), unit.end(), segment);
    }

    /**
     * Reads a strength unit that has no denominator after it: grams and IU written per dose, grams
     * applied to the skin or a mucosa, IU not taken by mouth, and mEq or mmol given by a written
     * non-oral route are administered amounts; everything else is a product strength.
     */
    private static AmountKind classifyStrengthUnit(String instructions, String unit, int unitEnd,
            InstructionSegment segment) {
        boolean grams = isGramUnit(unit);
        // Read through StrengthMeasure, every spelling counts: "20 milliequivalents" is "20 mEq" and
        // "1000 international units" is "1000 IU".
        StrengthMeasure measure = StrengthMeasure.of(unit);
        String measured = measure == null ? "" : measure.measure();
        boolean internationalUnits = "iu".equals(measured);
        boolean electrolyteUnits = "meq".equals(measured) || "mmol".equals(measured);
        // "17 g per dose" and "20 IU/dose" state an administered amount in a measurable unit. A mass
        // per dose in mg, mcg or mEq ("500 mg per dose", an inhaler's "250 mcg/dose") still does not
        // say how many dispensable units supply it, so it is read as a strength below.
        if ((grams || internationalUnits) && PER_DOSE.matcher(instructions)
                .region(skipWhitespaceForward(instructions, unitEnd), instructions.length()).lookingAt()) {
            return AmountKind.ADMINISTERED;
        }
        // Grams applied to the skin or a mucosa and non-oral IU specify administered amounts.
        if (grams) {
            return isTopical(instructions, segment) ? AmountKind.ADMINISTERED : AmountKind.STRENGTH;
        }
        if (internationalUnits) {
            return isOral(instructions, segment) ? AmountKind.STRENGTH : AmountKind.ADMINISTERED;
        }
        // Milliequivalents or millimoles infused or injected ("20 mEq IV daily", "20 mmol IV over 6 h")
        // are the amount given; by mouth, or with no route written, a potassium tablet's 20 mEq is its
        // strength, so only a written non-oral route makes them administered amounts (the IU rule
        // reads routeless IU as insulin).
        if (electrolyteUnits && hasNonOralRoute(instructions, segment)) {
            return AmountKind.ADMINISTERED;
        }
        return AmountKind.STRENGTH;
    }

    /** True when the directions name a route other than PO or SL, parsed ("IM") or as a word ("IV", "subcut"). */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of parsed instruction tokens, not a security or authorization decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of parsed instruction tokens, not a security or authorization decision")
    private static boolean hasNonOralRoute(String instructions, InstructionSegment segment) {
        boolean parsedNonOral = !segment.route.isEmpty()
                && !"PO".equalsIgnoreCase(segment.route) && !"SL".equalsIgnoreCase(segment.route);
        return parsedNonOral || hasNonOralWord(instructions);
    }

    /**
     * True when the directions give the product by mouth: the PO or SL route, or, with no non-oral
     * route written anywhere, oral wording ("by mouth", "orally"), the method "Take", or a selected
     * tablet or capsule whose strength is in IU (vitamin D). An IU amount with no route and no such
     * product ("20 IU qhs") stays an administered amount, as for insulin.
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of parsed instruction tokens, not a security or authorization decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of parsed instruction tokens, not a security or authorization decision")
    private static boolean isOral(String instructions, InstructionSegment segment) {
        if ("PO".equalsIgnoreCase(segment.route) || "SL".equalsIgnoreCase(segment.route)) {
            return true;
        }
        if (!segment.route.isEmpty() || hasNonOralWord(instructions)) {
            return false;
        }
        return ORAL_CONTEXT.matcher(instructions).find() || "Take".equalsIgnoreCase(segment.method)
                || segment.solidProductInInternationalUnits;
    }

    /**
     * True when the selected product is a tablet or capsule with one strength in international units,
     * such as vitamin D {@code "1000.0unit"} TABLET: a product counted out and taken by mouth. Insulin
     * and heparin are solutions with a strength per mL, so they never qualify.
     */
    private static boolean isSolidProductInInternationalUnits(RxPrescriptionData.Prescription rx) {
        String dosage = rx.getDosage();
        String form = rx.getDrugForm();
        if (dosage == null || form == null || dosage.indexOf('/') >= 0
                || !TABLET_OR_CAPSULE_FORM.matcher(form).find() || LIQUID_FORM.matcher(form).find()) {
            return false;
        }
        ProductStrength product = ProductStrength.single(dosage);
        return product != null && "iu".equals(product.measure().measure());
    }

    /** True when a route word the route list does not parse gives the product other than by mouth ("IV", "subcut"). */
    private static boolean hasNonOralWord(String instructions) {
        for (String word : wordsOf(instructions)) {
            if (NON_ORAL_WORDS.contains(word) || NON_ORAL_WORD_STARTS.stream().anyMatch(word::startsWith)) {
                return true;
            }
        }
        return false;
    }

    /** True when the directions apply the product to the skin or a mucosa rather than giving it by mouth. */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of parsed instruction tokens, not a security or authorization decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of parsed instruction tokens, not a security or authorization decision")
    private static boolean isTopical(String instructions, InstructionSegment segment) {
        return "TOP.".equalsIgnoreCase(segment.route) || METHOD_APPLY.equalsIgnoreCase(segment.method)
                || METHOD_RUB_WELL_IN.equalsIgnoreCase(segment.method) || hasTopicalWording(instructions)
                || segment.topicalProduct;
    }

    /**
     * True when the selected product is a cream, ointment, gel, lotion, paste or foam, which is applied
     * by the gram ("2-4 g qid" of diclofenac gel is grams of gel, not tubes).
     */
    private static boolean isTopicalProduct(RxPrescriptionData.Prescription rx) {
        String form = rx.getDrugForm();
        return form != null && TOPICAL_FORM.matcher(form).find() && !TABLET_OR_CAPSULE_FORM.matcher(form).find();
    }

    /**
     * True when the instruction names an application on the skin or a mucosa: a topical word ("TOP",
     * "topically", "PV"), "affected area" or similar, or "to" (or "to the") and a body site.
     */
    private static boolean hasTopicalWording(String instructions) {
        List<String> words = wordsOf(instructions);
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            String next = i + 1 < words.size() ? words.get(i + 1) : "";
            String afterNext = i + 2 < words.size() ? words.get(i + 2) : "";
            if (TOPICAL_WORDS.contains(word) || ("affected".equals(word) && AFFECTED_SITES.contains(next))
                    || ("to".equals(word) && (APPLICATION_SITES.contains(next)
                            || ("the".equals(next) && APPLICATION_SITES.contains(afterNext))))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Records a strength or product-only lead on the segment, with the amount as typed, its unit and
     * where it ends in the original instructions.
     *
     * @return true when the amount describes the product rather than an administered dose
     */
    private static boolean describesProduct(InstructionSegment segment, String instructions, int amountEnd,
            String minimum, String maximum) {
        AmountKind kind = classifyAmount(instructions, amountEnd, segment);
        if (kind == AmountKind.ADMINISTERED) {
            return false;
        }
        segment.productAmount = kind;
        segment.productMin = minimum;
        segment.productMax = maximum;
        Matcher unit = STRENGTH_UNIT.matcher(instructions)
                .region(skipWhitespaceForward(instructions, amountEnd), instructions.length());
        segment.productUnit = unit.lookingAt() ? unit.group() : null;
        segment.productEnd = amountEnd - segment.textOffset;
        return true;
    }

    /**
     * True when a prescription with no product strength carries an explicit administered dose unit
     * of grams ({@code unit} "gm", as the legacy dose editor stores it) and the instructions type a
     * gram amount ("Take 17 g PO daily"). The typed amount is then the administered dose in that
     * unit, so the structured dose and the unit the clinical export reads agree (17 gm, not 1 gm).
     */
    private static boolean keepsTypedGramAmount(InstructionSegment segment, RxPrescriptionData.Prescription rx) {
        return isGramUnit(rx.getUnit()) && isGramUnit(segment.productUnit)
                && (rx.getDosage() == null || rx.getDosage().isBlank());
    }

    /** True when the unit, typed or stored, is grams in any spelling: "g", "gm", "gms", "gram" or "grams". */
    // FindSecBugs IMPROPER_UNICODE: case folding of a parsed or stored unit abbreviation, not a security or authorization decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case folding of a parsed or stored unit abbreviation, not a security or authorization decision")
    private static boolean isGramUnit(String unit) {
        return unit != null && GRAM_WORDS.contains(unit.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Turns a leading strength into dispensable units per administration. When the selected product
     * has one known strength in a comparable unit, the stated strength is divided by it (1000 mg of a
     * 500 mg tablet is two tablets) and accepted only as a whole number of units, or for tablets that
     * may be split a whole number of halves or quarters, up to {@link #MAX_UNITS_PER_DOSE}. A
     * modified-release, enteric-coated or similar tablet is never split. Any other result, or a
     * liquid or rate product whose volume per dose is not written, leaves the dose unset so the
     * prescriber's quantity is kept. Without a comparable product strength (a custom drug, a
     * combination product, IU against a mass strength) one unit per administration is assumed, as
     * issue #3876 asks, and a strength range keeps its proportions from that unit.
     *
     * @return the minimum and maximum units per administration; {@code "0"} when none can be established
     */
    private static String[] unitsPerDose(InstructionSegment segment, RxPrescriptionData.Prescription rx) {
        ExactAmount statedMin = ExactAmount.parse(segment.productMin);
        ExactAmount statedMax = ExactAmount.parse(segment.productMax);
        if (statedMax.isLessThan(statedMin)) {
            ExactAmount lower = statedMax;
            statedMax = statedMin;
            statedMin = lower;
        }
        StrengthMeasure statedUnit = segment.productUnit == null ? null : StrengthMeasure.of(segment.productUnit);
        if (statedMin.isZero() || statedMax.isZero() || statedUnit == null) {
            return new String[] {"0", "0"};
        }
        if (isLiquidOrRate(rx)) {
            return new String[] {"0", "0"};
        }
        ProductStrength product = ProductStrength.single(rx.getDosage());
        if (product == null || !product.measure().measure().equals(statedUnit.measure()) || product.amount().signum() <= 0) {
            return unitsWithoutProductStrength(statedMin, statedMax);
        }
        int stepsPerUnit = stepsPerUnit(rx.getDrugForm());
        BigDecimal productSize = product.amount().multiply(product.measure().size());
        String minimum = unitsOf(statedMin.numerator().multiply(statedUnit.size()),
                statedMin.denominator().multiply(productSize), stepsPerUnit);
        String maximum = unitsOf(statedMax.numerator().multiply(statedUnit.size()),
                statedMax.denominator().multiply(productSize), stepsPerUnit);
        return minimum == null || maximum == null ? new String[] {"0", "0"} : new String[] {minimum, maximum};
    }

    /** True for a liquid or a rate (250 mg/5 mL, 25 mcg/hr): the strength says nothing about the volume given. */
    private static boolean isLiquidOrRate(RxPrescriptionData.Prescription rx) {
        String dosage = rx.getDosage();
        String form = rx.getDrugForm();
        return (dosage != null && dosage.indexOf('/') >= 0) || (form != null && LIQUID_FORM.matcher(form).find());
    }

    /**
     * Without a comparable product strength, one unit per administration; a range keeps its
     * proportions from its lower bound (500-1000 mg is one to two units).
     */
    private static String[] unitsWithoutProductStrength(ExactAmount statedMin, ExactAmount statedMax) {
        String upper = unitsOf(statedMax.numerator().multiply(statedMin.denominator()),
                statedMax.denominator().multiply(statedMin.numerator()), 2);
        return upper == null ? new String[] {"0", "0"} : new String[] {"1", upper};
    }

    /** Halves and quarters only for a tablet that may be split; any other form must come out whole. */
    private static int stepsPerUnit(String drugForm) {
        String form = drugForm == null ? "" : drugForm;
        return TABLET_FORM.matcher(form).find() && !UNSPLITTABLE_TABLET.matcher(form).find() ? 4 : 1;
    }

    /**
     * Divides two exact amounts into a count of units that must come out as a whole number of
     * {@code 1/stepsPerUnit} parts and lie between one part and {@link #MAX_UNITS_PER_DOSE}.
     *
     * @return the count as plain decimal text, or null when it is not such a count
     */
    private static String unitsOf(BigDecimal numerator, BigDecimal denominator, int stepsPerUnit) {
        BigDecimal steps = numerator.multiply(BigDecimal.valueOf(stepsPerUnit));
        if (denominator.signum() <= 0 || numerator.signum() <= 0 || steps.remainder(denominator).signum() != 0) {
            return null;
        }
        BigDecimal units = steps.divide(denominator, 0, RoundingMode.UNNECESSARY)
                .divide(BigDecimal.valueOf(stepsPerUnit), 2, RoundingMode.UNNECESSARY);
        return units.compareTo(MAX_UNITS_PER_DOSE) > 0 ? null : units.stripTrailingZeros().toPlainString();
    }

    /**
     * Finds the one explicit administered amount written between the leading product amount and the
     * frequency: a count with its dosage form ({@code (2 tabs)}), a bare count before the route
     * ({@code 500mg 2 po}), a volume or a unit count. A concentration denominator, a vehicle, a
     * ceiling or a dispensing amount is not a dose, and two or more amounts are ambiguous, so none
     * is read.
     *
     * @return the amount, or null when there is not exactly one
     */
    private static UnitAmount findExplicitAmountAfterProduct(String instructions, int from, int frequencyStart) {
        if (from < 0 || from >= frequencyStart) {
            return null;
        }
        Matcher amount = AMOUNT_TOKEN.matcher(instructions).region(from, frequencyStart).useTransparentBounds(true);
        UnitAmount found = null;
        while (amount.find()) {
            String minimum = amount.group(1);
            NumberToken upper = rangeUpperBound(instructions, amount.end(), frequencyStart);
            int amountEnd = upper == null ? amount.end() : upper.end;
            if (!namesExplicitAmount(instructions, amountEnd, frequencyStart)
                    || followsConcentrationSeparator(instructions, amount.start()) || isLabelledNotADose(instructions, amount.start())) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = upper == null ? new UnitAmount(minimum, minimum, false) : new UnitAmount(minimum, upper.value, true);
        }
        return found;
    }

    /**
     * Finds the upper bound of a range ("1-2 tabs", "1 - 2 tabs") whose lower bound ends at
     * {@code amountEnd}: a number after a hyphen, ending no later than {@code limit}.
     *
     * @return the upper bound, or null when no range is written there
     */
    private static NumberToken rangeUpperBound(String instructions, int amountEnd, int limit) {
        int hyphen = skipWhitespaceForward(instructions, amountEnd);
        if (hyphen >= limit || instructions.charAt(hyphen) != '-') {
            return null;
        }
        NumberToken upper = findDecimalAt(instructions, skipWhitespaceForward(instructions, hyphen + 1));
        return upper != null && upper.end <= limit ? upper : null;
    }

    /**
     * True when what follows an amount makes it an explicit administered amount: a counted dosage form
     * ({@code 2 tabs}), a volume or unit count ({@code 5 mL}), the oral route after a space
     * ({@code 2 po}, {@code 2 by mouth}) or a count of product units ({@code 2 x 500 mg tabs}).
     */
    private static boolean namesExplicitAmount(String text, int amountEnd, int limit) {
        int wordStart = skipWhitespaceForward(text, amountEnd);
        // "2 x 500 mg tabs" and "2x500mg tabs": the "x" may run straight into the strength.
        if (wordStart < limit && (text.charAt(wordStart) == 'x' || text.charAt(wordStart) == 'X')
                && countsProductUnits(text, wordStart + 1, limit)) {
            return true;
        }
        String word = wordAt(text, wordStart, limit);
        if (word == null) {
            return false;
        }
        if (isCountedForm(word) || MEASURED_DOSE_UNITS.contains(lowerCase(word))) {
            return true;
        }
        return wordStart > amountEnd && ("po".equals(lowerCase(word)) || "sl".equals(lowerCase(word))
                || ("by".equals(lowerCase(word)) && "mouth".equals(lowerCase(wordAt(text,
                        skipWhitespaceForward(text, wordStart + word.length()), text.length())))));
    }

    /** True for "500 mg tabs" after the "x" of {@code 2 x 500 mg tabs}: a strength, then a counted dosage form. */
    private static boolean countsProductUnits(String text, int start, int limit) {
        NumberToken strength = findDecimalAt(text, skipWhitespaceForward(text, start));
        if (strength == null) {
            return false;
        }
        int unitStart = skipWhitespaceForward(text, strength.end);
        String unit = wordAt(text, unitStart, limit);
        if (unit == null || StrengthMeasure.of(unit) == null) {
            return false;
        }
        String form = wordAt(text, skipWhitespaceForward(text, unitStart + unit.length()), limit);
        return form != null && isCountedForm(form);
    }

    /**
     * Returns the run of letters starting at {@code start} (ending before {@code limit}) when it is a
     * whole word, followed by neither a letter nor a digit; otherwise null.
     */
    private static String wordAt(String text, int start, int limit) {
        int end = start;
        int stop = Math.min(limit, text.length());
        while (end < stop && Character.isLetter(text.charAt(end))) {
            end++;
        }
        if (end == start || (end < text.length() && Character.isLetterOrDigit(text.charAt(end)))) {
            return null;
        }
        return text.substring(start, end);
    }

    /** True for a dosage-form word that is counted out one by one ("tabs", "capsule", "puffs"). */
    private static boolean isCountedForm(String word) {
        return COUNTED_FORMS.contains(lowerCase(word));
    }

    /** Lower-cases an instruction word for comparison with the word lists; null stays null. */
    // FindSecBugs IMPROPER_UNICODE: case folding of parsed instruction words for list lookups, not a security or authorization decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case folding of parsed instruction words for list lookups, not a security or authorization decision")
    private static String lowerCase(String word) {
        return word == null ? null : word.toLowerCase(Locale.ROOT);
    }

    /** The words of an instruction, lower-cased, as {@code \\b} delimits them. */
    private static List<String> wordsOf(String instructions) {
        List<String> words = new ArrayList<>();
        Matcher word = WORD.matcher(instructions);
        while (word.find()) {
            words.add(lowerCase(word.group()));
        }
        return words;
    }

    /**
     * True when the words just before an amount make it a vehicle or diluent ({@code in 100 mL}), a
     * ceiling ({@code max 8 tabs}, {@code up to 4}) or an amount to dispense ({@code #30},
     * {@code dispense 30}). Punctuation between the words and the amount is ignored.
     */
    private static boolean isLabelledNotADose(String instructions, int amountStart) {
        int start = amountStart;
        while (true) {
            int end = start;
            boolean afterHyphen = false;
            while (end > 0 && !isWordCharacter(instructions.charAt(end - 1))) {
                char c = instructions.charAt(end - 1);
                if (c == '#') {
                    return true;
                }
                afterHyphen |= c == '-';
                end--;
            }
            // The upper bound of a range written with spaces ("in 125 - 250 mL") carries the label
            // written before its lower bound. The walk back to that lower bound is a loop rather than a
            // recursion, so a long chain of hyphenated numbers cannot exhaust the stack.
            int lowerStart = afterHyphen ? numberStartBefore(instructions, end) : -1;
            if (lowerStart < 0) {
                return endsWithNotADoseLabel(wordsOf(instructions.substring(Math.max(0, end - 40), end)));
            }
            start = lowerStart;
        }
    }

    /**
     * Finds where a number that ends at {@code end} starts, such as the lower bound "125" of
     * "125 - 250".
     *
     * @return the start of the number, or -1 when no whole number ends there
     */
    private static int numberStartBefore(String instructions, int end) {
        int start = end;
        while (start > 0 && (isDigit(instructions.charAt(start - 1)) || instructions.charAt(start - 1) == '.')) {
            start--;
        }
        boolean wholeNumber = start < end && (start == 0 || !isWordCharacter(instructions.charAt(start - 1)));
        return wholeNumber ? start : -1;
    }

    /** True when the last word, or the last two or three words, mark what follows as not a dose ("in", "max", "up to"). */
    private static boolean endsWithNotADoseLabel(List<String> previous) {
        int count = previous.size();
        if (count == 0) {
            return false;
        }
        if (NOT_A_DOSE_WORDS.contains(previous.get(count - 1))) {
            return true;
        }
        for (int length = 2; length <= Math.min(3, count); length++) {
            if (NOT_A_DOSE_PHRASES.contains(String.join(" ", previous.subList(count - length, count)))) {
                return true;
            }
        }
        return false;
    }

    /** True for the characters {@code \\w} matches: ASCII letters, digits and underscore. */
    private static boolean isWordCharacter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || isDigit(c) || c == '_';
    }

    /**
     * True when a fraction found just before the frequency can stand alone as a dose: a proper
     * fraction that is not written as a schedule ({@code x 3/7}, {@code for 2/52}).
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of parsed instruction tokens, not a security or authorization decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of parsed instruction tokens, not a security or authorization decision")
    private static boolean isPlainDoseFraction(String instructions, NumberToken fraction) {
        ExactAmount amount = ExactAmount.parse(fraction.value);
        if (amount.isZero() || amount.numerator().compareTo(amount.denominator()) >= 0) {
            return false;
        }
        int end = skipWhitespaceBackward(instructions, fraction.start);
        int start = end;
        while (start > 0 && Character.isLetter(instructions.charAt(start - 1))) {
            start--;
        }
        String previousWord = instructions.substring(start, end);
        return !("x".equalsIgnoreCase(previousWord) || "for".equalsIgnoreCase(previousWord));
    }

    /**
     * Finds an administered amount written with its unit directly before the frequency,
     * such as {@code 10 mL}, {@code 5-10mL}, {@code 1/2 mL} or {@code 2 tablets} in
     * {@code ... PO 10 mL TID}. The unit must be one an administered amount can carry (a
     * dose unit or a dosage-form count word), and the amount must not read as a strength or
     * concentration in this instruction's route and method context, nor follow a vehicle, ceiling
     * or dispensing label ({@code in 100 mL}, {@code max 8 tabs}).
     *
     * @return the amount, or null when nothing before the frequency qualifies
     */
    private static UnitAmount findAmountWithUnitBeforeFrequency(String instructions, int frequencyStart, InstructionSegment segment) {
        int unitEnd = skipWhitespaceBackward(instructions, frequencyStart);
        int unitStart = unitEnd;
        while (unitStart > 0 && Character.isLetter(instructions.charAt(unitStart - 1))) {
            unitStart--;
        }
        if (unitStart == unitEnd || !(DOSE_UNIT.matcher(instructions).region(unitStart, unitEnd).matches()
                || isCountedForm(instructions.substring(unitStart, unitEnd)))) {
            return null;
        }
        NumberRange range = findRangeBefore(instructions, unitStart);
        if (range != null) {
            return !followsConcentrationSeparator(instructions, range.start)
                    && !isLabelledNotADose(instructions, range.start)
                    && classifyAmount(instructions, range.end, segment) == AmountKind.ADMINISTERED
                    ? new UnitAmount(range.min, range.max, true) : null;
        }
        NumberToken amount = findDecimalBefore(instructions, unitStart);
        if (amount == null) {
            amount = findFractionBefore(instructions, unitStart);
        }
        if (amount == null || followsConcentrationSeparator(instructions, amount.start)
                || isLabelledNotADose(instructions, amount.start)
                || classifyAmount(instructions, amount.end, segment) != AmountKind.ADMINISTERED) {
            return null;
        }
        return new UnitAmount(amount.value, amount.value, false);
    }

    /**
     * True when "/" or the word "per" directly precedes the amount: it is then the denominator
     * of a concentration such as {@code 250 mg per 5 mL}, not an administered amount.
     */
    private static boolean followsConcentrationSeparator(String text, int amountStart) {
        int end = skipWhitespaceBackward(text, amountStart);
        if (end > 0 && text.charAt(end - 1) == '/') {
            return true;
        }
        return end >= 3 && text.regionMatches(true, end - 3, "per", 0, 3)
                && (end == 3 || !Character.isLetterOrDigit(text.charAt(end - 4)));
    }

    /**
     * Finds a fraction such as {@code 1/2} ending (after optional whitespace) at the given index. A
     * whole number written before it belongs to the same amount: {@code 1 1/2} is returned as the one
     * fraction {@code 3/2}, marked as a mixed number. When the number before the fraction ends some
     * other token ({@code 1-1 1/2}), the fraction is not read on its own, because that would
     * understate the amount.
     */
    private static NumberToken findFractionBefore(String text, int endExclusive) {
        int end = skipWhitespaceBackward(text, endExclusive);
        int denominatorStart = end;
        while (denominatorStart > 0 && isDigit(text.charAt(denominatorStart - 1))) {
            denominatorStart--;
        }
        if (denominatorStart == end || denominatorStart == 0 || text.charAt(denominatorStart - 1) != '/') {
            return null;
        }
        int numeratorEnd = denominatorStart - 1;
        int start = numeratorEnd;
        while (start > 0 && isDigit(text.charAt(start - 1))) {
            start--;
        }
        if (start == numeratorEnd || !hasTokenBoundaryBefore(text, start)) {
            return null;
        }
        int wholeEnd = skipWhitespaceBackward(text, start);
        int wholeStart = wholeEnd;
        while (wholeStart > 0 && isDigit(text.charAt(wholeStart - 1))) {
            wholeStart--;
        }
        if (wholeStart == wholeEnd) {
            return new NumberToken(text, start, end);
        }
        if (!hasTokenBoundaryBefore(text, wholeStart)) {
            return null;
        }
        BigInteger denominator = new BigInteger(text.substring(denominatorStart, end));
        BigInteger numerator = new BigInteger(text.substring(wholeStart, wholeEnd)).multiply(denominator)
                .add(new BigInteger(text.substring(start, numeratorEnd)));
        return new NumberToken(wholeStart, end, numerator + "/" + denominator, true);
    }

    /**
     * Recognizes a denominator after a unit that leaves no fixed administered amount: a concentration
     * with a supported denominator unit and optional amount ({@code 250 mg/5 mL}, {@code IU/mL},
     * {@code g/g}), or a dose per body size ({@code 10 mg/kg}, {@code 100 mg/m2}).
     */
    private static boolean hasDenominatorAfterUnit(String instructions, int unitEnd) {
        Matcher separator = CONCENTRATION_SEPARATOR.matcher(instructions)
                .region(skipWhitespaceForward(instructions, unitEnd), instructions.length());
        if (!separator.lookingAt()) {
            return false;
        }
        int denominatorStart = separator.end();
        if (BODY_SIZE_BASIS.matcher(instructions).region(denominatorStart, instructions.length()).lookingAt()) {
            return true;
        }
        NumberToken denominatorAmount = findDecimalAt(instructions, denominatorStart);
        if (denominatorAmount != null) {
            denominatorStart = denominatorAmount.end;
        }
        // A recognized denominator unit distinguishes product concentrations
        // such as IU/mL and g/g from administered amounts such as IU per dose.
        return hasAmountUnitAfter(instructions, denominatorStart);
    }

    /** Recognizes unit suffixes permitted on administered doses when strength detection does not apply. */
    private static boolean hasDoseUnitAfter(String instructions, int amountEnd) {
        // Volumes, topical grams and injected/explicit unit counts are administered amounts,
        // including attached forms such as 5mL. Strength detection takes precedence.
        return DOSE_UNIT.matcher(instructions)
                .region(skipWhitespaceForward(instructions, amountEnd), instructions.length()).lookingAt();
    }


    /**
     * Extracts the method and the amount it opens, retaining route context for ambiguous units.
     *
     * @param frequencyStart where the parsed frequency starts, or -1 when none was found; a method
     *        word after it opens the dose only when nothing before it could be a dose
     */
    private static InstructionSegment scanInstruction(String instructions, String[] methods, String route,
            RxPrescriptionData.Prescription rx, int frequencyStart) {
        InstructionSegment segment = new InstructionSegment();
        segment.route = route;
        // What the selected product is decides how an IU or gram amount with no route is read.
        segment.solidProductInInternationalUnits = isSolidProductInInternationalUnits(rx);
        segment.topicalProduct = isTopicalProduct(rx);

        for (String s : methods) {
            Pattern p = Pattern.compile(s);
            Matcher m = p.matcher(instructions);
            if (m.find() && opensDose(instructions, m.start(), frequencyStart)) {
                segment.method = instructions.substring(m.start(), m.end()).trim();
                scanForFrequencyAndUpdateInstructionSegment(segment, instructions, segment.method, m.end());
                break;
            }
        }

        if (segment.method == null) {
            String placeholderMethod = "<NO_METHOD>";
            String instructionWithPlaceholderMethod = placeholderMethod + " " + instructions;
            // Positions recorded on the segment are mapped back to the original instructions.
            segment.textOffset = placeholderMethod.length() + 1;
            Pattern p = Pattern.compile("(?i)" + placeholderMethod);
            Matcher m = p.matcher(instructionWithPlaceholderMethod);
            if (m.find()) {
                scanForFrequencyAndUpdateInstructionSegment(segment, instructionWithPlaceholderMethod, placeholderMethod, m.end());
            }
        }

        return segment;
    }

    /**
     * True when the method word at {@code methodStart} opens the dose: it comes before the
     * frequency ("Amoxicillin 250 mg/5 mL: take 7.5 mL po tid"), no frequency was found, or nothing
     * before it could be a dose ("Twice daily take 1 tab"). After the frequency and a written dose
     * it is advice ("1 tab po bid x 7 days, take with food"), and the dose is read from the start.
     */
    private static boolean opensDose(String instructions, int methodStart, int frequencyStart) {
        return frequencyStart < 0 || methodStart < frequencyStart
                || !DOSE_NUMBER.matcher(instructions).region(0, methodStart).find();
    }

    private static void scanForFrequencyAndUpdateInstructionSegment(InstructionSegment segment, String instructions, String method, int methodEnd) {
        int amountStart = skipWhitespaceForward(instructions, methodEnd);
        if (setRangeAfterMethod(segment, instructions, amountStart)) {
            return;
        }
        if (setDecimalAfterMethod(segment, instructions, amountStart)) {
            return;
        }
        if (setFractionAfterMethod(segment, instructions, amountStart)) {
            return;
        }

        segment.amountMethod = findWordAmountAfterMethod(instructions, method);
    }

    /** Records a leading dose range, or the strength or concentration its suffix makes it. */
    private static boolean setRangeAfterMethod(InstructionSegment segment, String instructions, int amountStart) {
        NumberRange methodRange = findRangeAt(instructions, amountStart);
        if (methodRange == null) {
            return false;
        }
        if (describesProduct(segment, instructions, methodRange.end, methodRange.min, methodRange.max)) {
            return true;
        }
        if (!hasTokenBoundaryAt(instructions, methodRange.end) && !hasAmountUnitAfter(instructions, methodRange.end)) {
            return false;
        }

        p("else if 1");
        segment.takeMin = methodRange.min;
        segment.takeMax = methodRange.max;
        return true;
    }

    /** Records a leading decimal dose, or the strength or concentration its suffix makes it. */
    private static boolean setDecimalAfterMethod(InstructionSegment segment, String instructions, int amountStart) {
        NumberToken methodAmount = findDecimalAt(instructions, amountStart);
        if (methodAmount == null) {
            return false;
        }
        if (describesProduct(segment, instructions, methodAmount.end, methodAmount.value, methodAmount.value)) {
            return true;
        }
        // "Take 1 1/2 tabs": a whole number and a proper fraction are one amount, kept exact as 3/2.
        NumberToken mixed = findMixedNumberAt(instructions, methodAmount);
        if (mixed != null) {
            if (!describesProduct(segment, instructions, mixed.end, mixed.value, mixed.value)) {
                segment.amountMethod = mixed.value;
            }
            return true;
        }
        if (!hasTokenBoundaryAt(instructions, methodAmount.end) && !hasAmountUnitAfter(instructions, methodAmount.end)) {
            return false;
        }

        p("if 1");
        p("str1 ", instructions.substring(amountStart, methodAmount.end));
        p("found1");
        segment.amountMethod = methodAmount.value;
        //      p("amountMethod", amountMethod);
        return true;
    }

    /**
     * Reads a whole number followed by a proper fraction as one mixed number ({@code 1 1/2} is
     * {@code 3/2}). The fraction must end at a token boundary or an amount unit, as a leading
     * fraction must.
     *
     * @return the mixed number as an improper fraction ending where the fraction ends, or null
     */
    private static NumberToken findMixedNumberAt(String instructions, NumberToken whole) {
        int fractionStart = skipWhitespaceForward(instructions, whole.end);
        if (fractionStart == whole.end || whole.value.indexOf('.') >= 0) {
            return null;
        }
        String fraction = findFractionAt(instructions, fractionStart);
        if (fraction == null) {
            return null;
        }
        int separator = fraction.indexOf('/');
        BigInteger numerator = new BigInteger(fraction.substring(0, separator));
        BigInteger denominator = new BigInteger(fraction.substring(separator + 1));
        if (numerator.signum() <= 0 || numerator.compareTo(denominator) >= 0) {
            return null;
        }
        BigInteger improper = new BigInteger(whole.value).multiply(denominator).add(numerator);
        return new NumberToken(whole.start, fractionStart + fraction.length(), improper + "/" + denominator, true);
    }

    /**
     * Records a leading fraction as the administered dose, kept as written so the quantity
     * calculation can use its numerator and denominator, or the strength or concentration
     * its suffix makes it.
     */
    private static boolean setFractionAfterMethod(InstructionSegment segment, String instructions, int amountStart) {
        String fraction = findFractionAt(instructions, amountStart);
        if (fraction == null) {
            return false;
        }

        if (describesProduct(segment, instructions, amountStart + fraction.length(), fraction, fraction)) {
            return true;
        }

        segment.amountFrequency = fraction;
        return true;
    }

    private static String findWordAmountAfterMethod(String instructions, String method) {
        p("word amount");
        for (String word : zeroToTen) {
            String amountMethod = findWordAmountAfterMethod(instructions, method, word, "\\s");
            if (amountMethod != null) {
                return amountMethod;
            }
            amountMethod = findWordAmountAfterMethod(instructions, method, word, "$");
            if (amountMethod != null) {
                return amountMethod;
            }
        }
        return null;
    }

    private static String findWordAmountAfterMethod(String instructions, String method, String word, String suffix) {
        String safeMethod = Pattern.quote(method);
        String regex = safeMethod + "\\s+" + word + suffix;
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(instructions);
        p("pattern word =" + regex);
        if (!matcher.find()) {
            return null;
        }

        String amountMethod = instructions.substring(matcher.start(), matcher.end());
        amountMethod = amountMethod.replace(method, "").trim();
        p("amountMethod=" + amountMethod);
        amountMethod = convertWordToNumerical(amountMethod);
        p("num amountMethod=" + amountMethod);
        return amountMethod;
    }

    /** How the number that opens an instruction, or precedes its frequency, relates to the product. */
    private enum AmountKind {
        /** An administered amount or count: tablets, millilitres, units, topical grams, gram or IU per-dose amounts. */
        ADMINISTERED,
        /** A product strength such as 1000 mg, turned into units per administration unless an explicit amount is given. */
        STRENGTH,
        /**
         * Product information that supplies no administered amount: a concentration such as
         * 250 mg/5 mL or a dose per body size such as 10 mg/kg.
         */
        PRODUCT_ONLY
    }

    /**
     * The measure a strength unit belongs to (mass, international units, milliequivalents or
     * millimoles) and its size in that measure's base unit (mg, IU, mEq, mmol).
     */
    private record StrengthMeasure(String measure, BigDecimal size) {
        private static final BigDecimal MICROGRAM = new BigDecimal("0.001");
        private static final BigDecimal GRAM = BigDecimal.valueOf(1000);

        /** @return the measure of a strength unit such as {@code mg}, {@code MCG} or {@code units}, or null when unknown */
        // FindSecBugs IMPROPER_UNICODE: case folding of a parsed unit abbreviation, not a security or authorization decision.
        @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case folding of a parsed unit abbreviation, not a security or authorization decision")
        static StrengthMeasure of(String unit) {
            // "international  units" may be written with any run of spaces between its two words.
            return switch (unit.toLowerCase(Locale.ROOT).replaceAll("\\s++", " ")) {
                case "mg", "mgs", "milligram", "milligrams" -> new StrengthMeasure("mass", BigDecimal.ONE);
                case "mcg", "mcgs", "µg", "μg", "ug", "microgram", "micrograms" -> new StrengthMeasure("mass", MICROGRAM);
                case "g", "gm", "gms", "gram", "grams" -> new StrengthMeasure("mass", GRAM);
                case "iu", "unit", "units", "international unit", "international units" ->
                        new StrengthMeasure("iu", BigDecimal.ONE);
                case "meq", "milliequivalent", "milliequivalents" -> new StrengthMeasure("meq", BigDecimal.ONE);
                case "mmol", "millimole", "millimoles" -> new StrengthMeasure("mmol", BigDecimal.ONE);
                default -> null;
            };
        }
    }

    /** The one strength of a selected product, read from its dosage text as the drug picker stores it ("500.0mg"). */
    private record ProductStrength(BigDecimal amount, StrengthMeasure measure) {

        /** @return the product's strength, or null when the text names none or several (a combination product) */
        static ProductStrength single(String dosage) {
            Matcher strength = PRODUCT_STRENGTH.matcher(dosage == null ? "" : dosage);
            ProductStrength found = null;
            while (strength.find()) {
                StrengthMeasure measure = StrengthMeasure.of(strength.group(2));
                if (measure != null) {
                    if (found != null) {
                        return null;
                    }
                    found = new ProductStrength(new BigDecimal(strength.group(1)), measure);
                }
            }
            return found;
        }
    }

    /**
     * A dose amount kept exact until it is used: decimals as written, fractions as
     * numerator and denominator, so a course total is rounded only once, at the end.
     */
    private record ExactAmount(BigDecimal numerator, BigDecimal denominator) {
        private static final ExactAmount ZERO = new ExactAmount(BigDecimal.ZERO, BigDecimal.ONE);

        /** Parses a decimal such as {@code 2}, {@code 0.5} or {@code .5}, or a fraction such as {@code 5/6}; anything else is zero. */
        static ExactAmount parse(String text) {
            try {
                int separator = text.indexOf('/');
                if (separator < 0) {
                    return new ExactAmount(new BigDecimal(text.trim()), BigDecimal.ONE);
                }
                BigDecimal denominator = new BigDecimal(text.substring(separator + 1).trim());
                if (denominator.signum() <= 0) {
                    return ZERO;
                }
                return new ExactAmount(new BigDecimal(text.substring(0, separator).trim()), denominator);
            } catch (NumberFormatException _) {
                return ZERO;
            }
        }

        boolean isZero() {
            return numerator.signum() == 0;
        }

        boolean isLessThan(ExactAmount other) {
            return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator)) < 0;
        }

        double doubleValue() {
            return numerator.divide(denominator, MathContext.DECIMAL64).doubleValue();
        }

        float floatValue() {
            return (float) doubleValue();
        }

        /** The exact sum of this amount and another. */
        ExactAmount plus(ExactAmount other) {
            return new ExactAmount(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                    denominator.multiply(other.denominator));
        }

        /**
         * Whole units covering this amount, rounded upward once at this final division, within
         * the legacy integer quantity range: no under-dispensing of fractional totals and no
         * floating-point error from decimal dose products.
         */
        int ceilingUnits() {
            return numerator.divide(denominator, 0, RoundingMode.CEILING)
                    .min(BigDecimal.valueOf(Integer.MAX_VALUE)).intValue();
        }
    }

    /** An amount found with its unit before the frequency; {@code range} tells a min-max pair from a single value. */
    private record UnitAmount(String min, String max, boolean range) {
    }

    private static class InstructionSegment {
        AmountKind productAmount;
        /** The leading strength or product amount as typed, its unit, and where it ends in the original instructions. */
        String productMin;
        String productMax;
        String productUnit;
        int productEnd = -1;
        /** How far positions in the scanned text are shifted from the original instructions. */
        int textOffset;
        /** True when the selected product is a tablet or capsule with one strength in IU, so it is taken by mouth. */
        boolean solidProductInInternationalUnits;
        /** True when the selected product is a cream, ointment, gel or similar, applied by the gram. */
        boolean topicalProduct;
        String route;
        String method;
        String takeMin;
        String takeMax;
        String amountMethod;
        String amountFrequency;

        /** True when the leading amount is an explicit count or administered amount rather than a product amount. */
        boolean hasLeadingAmount() {
            return takeMin != null || amountMethod != null || amountFrequency != null;
        }
    }

    private static class NumberToken {
        final int start;
        final int end;
        final String value;
        /** True for a whole number and a fraction read as one amount ({@code 1 1/2}, kept as {@code 3/2}). */
        final boolean mixedNumber;

        NumberToken(String text, int start, int end) {
            this(start, end, text.substring(start, end), false);
        }

        NumberToken(int start, int end, String value, boolean mixedNumber) {
            this.start = start;
            this.end = end;
            this.value = value;
            this.mixedNumber = mixedNumber;
        }
    }

    private static class NumberRange {
        final int start;
        final String min;
        final String max;
        final int end;

        NumberRange(int start, String min, String max, int end) {
            this.start = start;
            this.min = min;
            this.max = max;
            this.end = end;
        }
    }


    /**
     * Removes drug names and structured prescription metadata from the free-text instructions.
     *
     * @param rx prescription containing the text and metadata to remove
     * @return remaining instructions, or an empty string when no text is present
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public static String trimSpecial(RxPrescriptionData.Prescription rx) {
        String special = rx.getSpecial();
        if (special == null || special.trim().length() == 0) return "";

        //if prescript has special instruction, remove it from special
        if (rx.getSpecialInstruction() != null && !rx.getSpecialInstruction().equalsIgnoreCase("null") && rx.getSpecialInstruction().trim().length() > 0) {
            special = special.replace(rx.getSpecialInstruction(), "");
        }

        //remove Qty:num
        String regex1 = "Qty:\\s*[0-9]*\\.?[0-9]*\\s*";
        String unitName = rx.getUnitName();
        if (unitName != null && special.indexOf(unitName) != -1) {
            regex1 += "\\Q" + unitName + "\\E";
        }
        Pattern p = Pattern.compile(regex1);
        Matcher m = p.matcher(special);
        special = m.replaceAll("");

        //remove Repeats:num from special
        String regex2 = "Repeats:\\s*[0-9]*\\.?[0-9]*\\s*";
        p = Pattern.compile(regex2);
        m = p.matcher(special);
        special = m.replaceAll("");

        //remove brand name
        String regex3 = rx.getBrandName();
        if (regex3 != null) {
            regex3 = regex3.trim();
            special = special.replace(regex3, "");
        }

        //remove generic name
        String regex4 = rx.getGenericName();
        if (regex4 != null) {
            regex4 = regex4.trim();
            special = special.replace(regex4, "");
        }

        //remove custom name
        String regex5 = rx.getCustomName();
        if (regex5 != null) {
            regex5 = regex5.trim();
            special = special.replace(regex5, "");
        }
        String regex6 = "Mitte:\\s*[0-9]+\\s*\\w+";
        p = Pattern.compile(regex6);
        m = p.matcher(special);
        special = m.replaceAll("");
        //assume drug name is before method and drug name is the first part of the instruction.
        String rx_enhance = CarlosProperties.getInstance().getProperty("rx_enhance");
        //rx_enhance changes the behavior by not deleting anything up to the words Take, apply..
        if (!(rx_enhance != null && rx_enhance.equals("true"))) {
            if (special.indexOf("Take") != -1) {
                special = special.substring(special.indexOf("Take"));
            } else if (special.indexOf("take") != -1) {
                special = special.substring(special.indexOf("take"));
            } else if (special.indexOf("TAKE") != -1) {
                special = special.substring(special.indexOf("TAKE"));
            } else if (special.indexOf(METHOD_APPLY) != -1) {
                special = special.substring(special.indexOf(METHOD_APPLY));
            } else if (special.indexOf(APPLY_WORD) != -1) {
                special = special.substring(special.indexOf(APPLY_WORD));
            } else if (special.indexOf("APPLY") != -1) {
                special = special.substring(special.indexOf("APPLY"));
            } else if (special.indexOf(METHOD_RUB_WELL_IN) != -1) {
                special = special.substring(special.indexOf(METHOD_RUB_WELL_IN));
            } else if (special.indexOf("rub well in") != -1) {
                special = special.substring(special.indexOf("rub well in"));
            } else if (special.indexOf("RUB WELL IN") != -1) {
                special = special.substring(special.indexOf("RUB WELL IN"));
            } else if (special.indexOf("Rub Well In") != -1) {
                special = special.substring(special.indexOf("Rub Well In"));
            }
        }

        return special.trim();

    }

    /**
     * Retains compatibility with legacy diagnostic callers without logging clinical data.
     *
     * @param bean prescription session; its clinical contents are not inspected
     * @deprecated prescription contents must not be written to application logs
     */
    @Deprecated
    public static void printStashContent(RxSessionBean bean) {
        // Deliberate compatibility no-op: staged prescriptions contain clinical information.
    }

    public static void setDefaultSpecialQuantityRepeat(RxPrescriptionData.Prescription rx) {

        String defaultRx = RxSettings.get("default_instruction");
        if (defaultRx != null) {
            rx.setSpecial(defaultRx);
        } else {
            rx.setSpecial("");
        }
        rx.setQuantity(getDefaultQuantity());
        rx.setRepeat(0);

    }

    private static void setResultSpecialQuantityRepeat(RxPrescriptionData.Prescription rx, Drug d) {
        String qStr = d.getQuantity() == null ? "" : d.getQuantity();
        Pattern p1 = Pattern.compile("\\d+");
        Matcher m1 = p1.matcher(qStr);
        if (m1.find()) {
            String qNum = qStr.substring(m1.start(), m1.end());
            rx.setQuantity(qNum);
            //get the quantity unit
            String qUnit = qStr.replace(qNum, "").trim();
            if (qUnit != null && qUnit.length() > 0) {
                rx.setUnitName(qUnit);
            }
        }
        rx.setUnitName(d.getUnitName());

        rx.setRepeat(d.getRepeat() == null ? 0 : d.getRepeat());
        rx.setSpecial(d.getSpecial());
        rx.setSpecial(trimSpecial(rx));
    }

    private static List<HashMap<String, String>> drugsTableQuery(String parameter, String value) {
        List<HashMap<String, String>> retList = new ArrayList<HashMap<String, String>>();

        DrugDao dao = (DrugDao) SpringUtils.getBean(DrugDao.class);
        for (Object[] i : dao.findByParameter(parameter, value)) {
            HashMap<String, String> hm = new HashMap<String, String>();
            hm.put("instruction", String.valueOf(i[0]));
            hm.put("special_instruction", String.valueOf(i[1]));
            retList.add(hm);
        }

        return retList;
    }

    private static List<HashMap<String, String>> getCustomNamePrevInstructions(String customName) {
        return drugsTableQuery("customName", customName);
    }

    private static List<HashMap<String, String>> getDinPrevInstructions(String din) {
        return drugsTableQuery("regional_identifier", din);
    }

    private static List<HashMap<String, String>> getBNPrevInstructions(String bn) {
        return drugsTableQuery("BN", bn);
    }

    public static List<HashMap<String, String>> getPreviousInstructions(RxPrescriptionData.Prescription rx) {
        List<HashMap<String, String>> retList = new ArrayList<HashMap<String, String>>();
        if (rx.isCustom()) {
            String cn = rx.getCustomName();
            if (cn != null && cn.trim().length() > 0) retList = getCustomNamePrevInstructions(cn);
        } else {
            String din = rx.getRegionalIdentifier();
            if (din != null && din.trim().length() > 0) {
                retList = getDinPrevInstructions(din);
                if (retList.size() == 0) {
                    retList = getBNPrevInstructions(rx.getBrandName());
                    //if (retList.size()==0)
                    //retList=getGNPrevInstructions(prescript.getGenericName());
                }
            } else {
                String bn = rx.getBrandName();
                if (bn != null && bn.trim().length() > 0) retList = getBNPrevInstructions(bn);
            }
        }
        if (retList.size() > 0) retList = trimMedHistoryList(rx, retList);
        return retList;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private static List<HashMap<String, String>> trimMedHistoryList(RxPrescriptionData.Prescription rx, List<HashMap<String, String>> l) {

        String customName = rx.getCustomName();
        String bn = rx.getBrandName();
        List<HashMap<String, String>> retList = new ArrayList<HashMap<String, String>>();
        if (l.size() > 0 || rx != null) {
            try {
                for (HashMap<String, String> hm : l) {
                    String ins = hm.get("instruction");
                    String specIns = hm.get("special_instruction");
                    if (ins != null && ins.length() > 0) {
                        if (customName != null && !customName.equalsIgnoreCase("null"))
                            ins = ins.replace(customName, "");
                        if (bn != null && !bn.equalsIgnoreCase("null")) ins = ins.replace(bn, "");
                        if (specIns != null && !specIns.equalsIgnoreCase("null")) ins = ins.replace(specIns, "");
                    }
                    if (ins != null) ins = ins.replace("\n", " ").trim();
                    if (specIns != null) specIns = specIns.replace("\n", " ").trim();
                    HashMap<String, String> h = new HashMap<String, String>();
                    h.put("instruction", removeQuantityMitteRepeat(ins));
                    h.put("special_instruction", specIns);
                    retList.add(h);
                }
            } catch (Exception e) {
                logger.error("Prescription utility operation failed ({})", e.getClass().getSimpleName());
            }
            retList = commonUniqueMedHistory(retList);
        }

        return retList;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<HashMap<String, String>> commonUniqueMedHistory(List<HashMap<String, String>> l) {

        if (l != null && l.size() > 0) {
            HashMap<HashMap, Integer> elementCount = new HashMap<HashMap, Integer>();
            List<HashMap<String, String>> retList = new ArrayList<HashMap<String, String>>();
            for (HashMap<String, String> hm : l) {
                if (!elementCount.containsKey(hm)) elementCount.put(hm, 1);
                else elementCount.put(hm, elementCount.get(hm) + 1);
            }
            List<Integer> count = new ArrayList(elementCount.values());
            Collections.sort(count); //ascending order
            try {
                for (int i = count.size() - 1; i >= 0; i--) {
                    Set set = elementCount.keySet();
                    Iterator iter = set.iterator();
                    while (iter.hasNext()) {
                        HashMap key = (HashMap) iter.next();
                        Integer value = elementCount.get(key);
                        if (value == count.get(i)) {
                            retList.add(key);
                            elementCount.remove(key);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Prescription utility operation failed ({})", e.getClass().getSimpleName());
            }
            return retList;
        } else return l;
    }

    private static String removeQuantityMitteRepeat(String s) {
        Pattern p;
        Matcher m;
        String regex2 = "Repeats:\\s*[0-9]*\\.?[0-9]*\\s*";
        p = Pattern.compile(regex2);
        m = p.matcher(s);
        s = m.replaceAll("");

        String regex1 = "Qty:\\s*[0-9]*\\.?[0-9]*\\s*\\w*";
        p = Pattern.compile(regex1);
        m = p.matcher(s);
        s = m.replaceAll("");

        String regex6 = "Mitte:\\s*[0-9]*\\.?[0-9]*\\s*\\w*";
        p = Pattern.compile(regex6);
        m = p.matcher(s);
        s = m.replaceAll("");
        s = s.trim();
        return s;
    }

    public static void setSpecialQuantityRepeat(RxPrescriptionData.Prescription rx) {
        DrugDao dao = (DrugDao) SpringUtils.getBean(DrugDao.class);

        if (rx.getRegionalIdentifier() != null && rx.getRegionalIdentifier().length() > 1) {
            p("if1");
            p(rx.getRegionalIdentifier());
            //query the database to see if there is a prescript with same din as this prescript.
            // String sql = "SELECT * FROM drugs WHERE regional_identifier='" + prescript.getRegionalIdentifier() + "' order by written_date desc"; //most recent is the first.

            List<Drug> drugs = dao.findByRegionBrandDemographicAndProvider(rx.getRegionalIdentifier(), rx.getBrandName(), rx.getDemographicNo(), rx.getProviderNo());
            if (drugs.isEmpty()) {
                setDefaultSpecialQuantityRepeat(rx);
            } else {
                setResultSpecialQuantityRepeat(rx, drugs.get(0));
            }
        } else {
            p("else2");
            if (rx.getBrandName() != null && rx.getBrandName().length() > 1) {
                p("if2");

                Drug drug = dao.findByBrandNameDemographicAndProvider(rx.getBrandName(), rx.getDemographicNo(), rx.getProviderNo());

                //if none, query database to see if there is prescript with same brandname.
                //if there are multiple, use latest.
                if (drug == null)
                    //else, set to special to "", quantity to "30", repeat to "0".
                    setDefaultSpecialQuantityRepeat(rx);
                else setResultSpecialQuantityRepeat(rx, drug);
            } else {
                p("if3");
                if (rx.getCustomName() != null && rx.getCustomName().length() > 1) {
                    p("customName is not null");

                    Drug drug = dao.findByCustomNameDemographicIdAndProviderNo(rx.getCustomName(), rx.getDemographicNo(), rx.getProviderNo());
                    //if none, query database to see if there is prescript with same customName.
                    //if there are multiple, use latest.
                    if (drug != null) {
                        setResultSpecialQuantityRepeat(rx, drug);
                    } else {
                        setDefaultSpecialQuantityRepeat(rx);
                    }
                } else {
                    //else, set to special to "", quantity to "30", repeat to "0".
                    setDefaultSpecialQuantityRepeat(rx);
                }
            }
        }
    }

    private static boolean checkLastPrescribed(RxPrescriptionData.Prescription rx, int drugId) {
        //make a another query to get the latest drug with same name but archived not equals one and arhived reason equals to deleted.
        //check if drugId is greater than that compare id
        //if yes, return true;
        //if not, return false;
        boolean lastPrescribed = true;
        //need the max drugId, not using DIN because it doesn't work with customed drugs.
        DrugDao dao = SpringUtils.getBean(DrugDao.class);
        Integer lastId = dao.findLastNotArchivedId(rx.getBrandName(), rx.getGenericName(), rx.getDemographicNo());

        if (lastId != null) {
            int compareId = lastId.intValue();
            if (drugId > compareId) {
                lastPrescribed = true;
            } else {
                lastPrescribed = false;
            }
        } else {
            lastPrescribed = true;
        }
        return lastPrescribed;
    }

    public static boolean checkDiscontinuedBefore(RxPrescriptionData.Prescription rx) {

        //check if this drug was discontinued before
        //String sql="SELECT * FROM drugs WHERE archived=1 AND (archived_reason>'' OR archived_reason<'' ) AND ATC='" + this.atcCode + "' AND regional_identifier='" + this.regionalIdentifier + "' AND demographic_no=" + this.demographicNo+" order by written_date desc";
        //the query will fail to check if a drug A is prescribed, and drug A is prescribed again, and then the first drug A is discontinued, when the second drug A is represcribed
        //or a third drug A is added, no warning will be given.
        boolean discontinuedLatest = false;

        DrugDao dao = SpringUtils.getBean(DrugDao.class);
        Drug drug = dao.findByDemographicIdRegionalIdentifierAndAtcCode(rx.getAtcCode(), rx.getRegionalIdentifier(), rx.getDemographicNo());

        if (drug != null) {//get the first result which has the largest drugid and hence the most recent result.

            int drugId = drug.getId();

            boolean isLastPrescribed = checkLastPrescribed(rx, drugId); //check if this drug was saved after discontinued.
            if (isLastPrescribed) {

                //get date discontinued
                //get reason for discontinued
                Date archivedDate = drug.getArchivedDate();
                // String archDate = rs.getProperty("archived_date");
                String archDate = RxUtil.DateToString(archivedDate);
                String archReason = drug.getArchivedReason();

                rx.setLastArchDate(archDate);
                rx.setLastArchReason(archReason);
                discontinuedLatest = true;
            } else {
                discontinuedLatest = false;
            }
        } else {
            discontinuedLatest = false;
        }

        return discontinuedLatest;
    }

    //check to see if a represcription of a med is clicked twice.
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public static boolean isRxUniqueInStash(final RxSessionBean beanRx, final RxPrescriptionData.Prescription rx) {
        boolean unique = true;
        if (rx.isCustom()) {
            for (int j = 0; j < beanRx.getStashSize(); j++) {
                try {
                    RxPrescriptionData.Prescription rxTemp = beanRx.getStashItem(j);
                    //p(""+rxTemp.isCustom());
                    //p(rxTemp.getCustomName());
                    //p(prescript.getCustomName());
                    //p(""+rxTemp.isCustomNote());
                    //p(""+prescript.isCustomNote());
                    //p(""+rxTemp.getRandomId());
                    //p(""+prescript.getRandomId());
                    if (rxTemp.isCustom() && rxTemp.getCustomName().equals(rx.getCustomName()) && rxTemp.isCustomNote() == rx.isCustomNote() && rxTemp.getRandomId() != rx.getRandomId()) {
                        p("1unique turning false");
                        unique = false;
                    }
                } catch (Exception e) {
                    logger.error("Prescription utility operation failed ({})", e.getClass().getSimpleName());
                }
            }
        } else {
            for (int j = 0; j < beanRx.getStashSize(); j++) {
                try {
                    RxPrescriptionData.Prescription rxTemp = beanRx.getStashItem(j);
                    //p(prescript.getBrandName());
                    //p(rxTemp.getBrandName());

                    //p(""+rxTemp.getRandomId());
                    //p(""+prescript.getRandomId());
                    if (rx.getBrandName() != null && !rx.getBrandName().equalsIgnoreCase("null") && rx.getBrandName().equals(rxTemp.getBrandName()) && rxTemp.getRandomId() != rx.getRandomId()) { //GCN_SWQNO changes when drugref database is updated
                        p("2unique turning false");
                        unique = false;
                    }
                } catch (Exception e) {
                    logger.error("Prescription utility operation failed ({})", e.getClass().getSimpleName());
                }
            }
        }
        if (unique) {
            p("unique is true");
        }
        return unique;
    }

    public static String getSpecialInstructions() {
        String retStr = "";
        RxCodesData codesData = new RxCodesData();
        String[] specArr = codesData.getSpecialInstructions();
        List<String> specList = Arrays.asList(specArr);
        // get all past record spec inst from drugs table

        DrugDao dao = SpringUtils.getBean(DrugDao.class);
        List<String> resultSpecInst = dao.findSpecialInstructions();
        resultSpecInst.addAll(specList);
        Set<String> specIntSet = new HashSet<String>(resultSpecInst); //remove duplicates
        specArr = specIntSet.toArray(specArr);
        for (int i = 0; i < specArr.length; i++) {
            retStr += specArr[i];
            if (i < specArr.length - 1) retStr += "*"; //use * as a delimiter
        }
        return retStr;

    }

    // MyDrugRef callWebserviceLite method removed - no longer needed

    public static <T> HashMap<Long, T> createKeyValPair(List<T> lst) {
        HashMap<Long, T> ret = new HashMap<Long, T>();
        for (T t : lst) {
            // Draw until the key is unused: a repeated key silently replaced an earlier entry (#3908).
            long rand;
            do {
                rand = io.github.carlos_emr.carlos.prescript.pageUtil.RxStashIds.next(
                        io.github.carlos_emr.carlos.prescript.pageUtil.RxStashIds.DEFAULT_BOUND);
            } while (ret.containsKey(rand));
            ret.put(rand, t);
        }
        return ret;
    }

    /**
     * Retains the legacy diagnostic API without recording potentially clinical values.
     *
     * @param str legacy diagnostic label
     * @param s legacy diagnostic value
     * @deprecated clinical debug values must not be written to application logs
     */
    @Deprecated
    public static void p(String str, String s) {
        // Deliberate compatibility no-op: callers may supply clinical information.
    }

    /**
     * Retains the legacy diagnostic API without recording potentially clinical text.
     *
     * @param str legacy diagnostic text
     * @deprecated clinical debug text must not be written to application logs
     */
    @Deprecated
    public static void p(String str) {
        // Deliberate compatibility no-op: callers may supply clinical information.
    }

    public static final java.util.Date combineDateTime(final java.util.Date date,
                                                       final java.util.Date time) {

        Calendar cdate = Calendar.getInstance();
        Calendar timeMerge = Calendar.getInstance();

        cdate.setTime(date);
        timeMerge.setTime(time);

        cdate.set(Calendar.HOUR_OF_DAY, timeMerge.get(Calendar.HOUR_OF_DAY));
        cdate.set(Calendar.MINUTE, timeMerge.get(Calendar.MINUTE));
        cdate.set(Calendar.SECOND, 00);

        return cdate.getTime();
    }
}
