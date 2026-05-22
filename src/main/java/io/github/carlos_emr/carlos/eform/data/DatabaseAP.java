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


package io.github.carlos_emr.carlos.eform.data;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.carlos_emr.carlos.report.data.ParameterizedSql;

@XmlAccessorType(XmlAccessType.NONE)
public class DatabaseAP {
    @XmlElement(name = "ap-name")
    private String apName;
    @XmlElement(name = "ap-sql")
    private String apSQL;
    @XmlElement(name = "ap-output")
    private String apOutput;
    @XmlElement(name = "ap-insql")
    private String apInSQL;
    @XmlElement(name = "ap-json-output")
    private boolean apJsonOutput = false;
    private boolean isInputField = false;
    @XmlElement(name = "archive")
    private String archive;


    /**
     * Creates a new instance of DatabaseAP
     */
    public DatabaseAP() {
    }

    public DatabaseAP(String apName, String apSQL, String apOutput) {
        set(apName, apSQL, apOutput);
    }


    public DatabaseAP(String apName, String apSQL, String apOutput, String apInSQL) {
        set(apName, apSQL, apOutput, apInSQL);
    }

    public DatabaseAP(DatabaseAP ap2) {
        this.apName = ap2.getApName();
        this.apSQL = ap2.getApSQL();
        this.apOutput = ap2.getApOutput();
        this.apInSQL = ap2.getApInSQL();
    }

    public void set(String apName, String apSQL, String apOutput) {
        this.apName = apName;
        this.apSQL = apSQL;
        this.apOutput = apOutput;
    }

    public void set(String apName, String apSQL, String apOutput, String apInSQL) {
        this.apName = apName;
        this.apSQL = apSQL;
        this.apOutput = apOutput;
        this.apInSQL = apInSQL;
    }

    public void set(String apName, String apSQL, String apOutput, String apInSQL, String archive) {
        this.apName = apName;
        this.apSQL = apSQL;
        this.apOutput = apOutput;
        this.apInSQL = apInSQL;
        this.archive = archive;
    }

    public void setApName(String apName) {
        this.apName = apName;
    }

    public void setApSQL(String apSQL) {
        this.apSQL = apSQL;
    }

    public void setApOutput(String apOutput) {
        this.apOutput = apOutput;
    }

    public String getApName() {
        if (apName == null) {
            return "";
        }
        return apName;
    }

    public String getApSQL() {
        if (apSQL == null) {
            return "";
        }
        return apSQL;
    }

    public String getApOutput() {
        if (apOutput == null) {
            return "";
        }
        return apOutput;
    }


    public String getApInSQL() {
        if (apInSQL == null) {
            return "";
        }
        return apInSQL;
    }

    public void setApInSQL(String apInSQL) {
        this.isInputField = true;
        this.apInSQL = apInSQL;
    }

    public void setApJsonOutput(boolean apJsonOutput) {
        this.apJsonOutput = apJsonOutput;
    }


    public String getArchive() {
        return archive;
    }

    public void setArchive(String archive) {
        this.archive = archive;
    }

    public boolean isInputField() {
        return isInputField;
    }

    public boolean isJsonOutput() {
        return apJsonOutput;
    }

    public static String parserReplace(String name, String var, String str) {
        //replaces <$name$> with var in str
        StringBuilder strb = new StringBuilder(str);
        int tagstart = -2;
        int tagend;
        while ((tagstart = strb.indexOf("${", tagstart + 2)) >= 0) {
            tagend = strb.indexOf("}", tagstart);
            if (strb.substring(tagstart + 2, tagend).equals(name)) {
                strb.replace(tagstart, tagend + 1, var == null ? "" : var);
            }
        }
        return strb.toString();
    }

    public static String parserReplace(String name, String var, DatabaseAP dbap, boolean inSql) {
        String sql;
        if (inSql) sql = dbap.getApInSQL();
        else sql = dbap.getApSQL();

        sql = DatabaseAP.parserReplace(name, var, sql);

        return sql;
    }

    public static ArrayList<String> parserGetNames(String str) {
        StringBuilder strb = new StringBuilder(str);
        ArrayList<String> names = new ArrayList<String>();
        int tagstart = -2;
        int tagend;
        while ((tagstart = strb.indexOf("${", tagstart + 2)) >= 0) {
            tagend = strb.indexOf("}", tagstart);
            names.add(strb.substring(tagstart + 2, tagend));
        }
        return names;
    }

    public static String parserClean(String str) {
        //removes left over ${...} in str; replaces with ""
        StringBuilder strb = new StringBuilder(str);
        int tagstart = -2;
        int tagend;
        while ((tagstart = strb.indexOf("${", tagstart + 2)) >= 0) {
            strb.replace(tagstart, tagstart + 2, "\"");
            tagend = strb.indexOf("}", tagstart);
            strb.replace(tagend, tagend + 1, "\"");
        }
        return strb.toString();
    }

    /**
     * Parameterizes request-backed AP placeholders. Unknown placeholders are
     * preserved for {@link #parserClean(String)} because legacy AP templates use
     * {@code ${name}} both for request inputs and output column aliases.
     */
    public static ParameterizedSql parameterizeSql(String sql, Map<String, ?> replacements) {
        StringBuilder parameterized = new StringBuilder();
        List<Object> params = new ArrayList<>();
        int position = 0;
        while (position < sql.length()) {
            position = appendNextParameterizedSegment(sql, replacements, parameterized, params, position);
        }

        // Unknown placeholders are legacy AP output names, not request values.
        // parserClean preserves the old behavior by turning ${name} into "name".
        return new ParameterizedSql(parserClean(parameterized.toString()), params);
    }

    private static int appendNextParameterizedSegment(String sql, Map<String, ?> replacements,
            StringBuilder parameterized, List<Object> params, int position) {
        int start = sql.indexOf("${", position);
        if (start < 0) {
            parameterized.append(sql.substring(position));
            return sql.length();
        }

        int end = sql.indexOf("}", start);
        if (end < 0) {
            parameterized.append(sql.substring(position));
            return sql.length();
        }

        String name = sql.substring(start + 2, end);
        if (!replacements.containsKey(name)) {
            parameterized.append(sql, position, end + 1);
            return end + 1;
        }

        return appendKnownPlaceholder(sql, parameterized, params, position, start, end, replacements);
    }

    private static int appendKnownPlaceholder(String sql, StringBuilder parameterized, List<Object> params,
            int position, int start, int end, Map<String, ?> replacements) {
        String name = sql.substring(start + 2, end);
        Object value = replacements.get(name);
        QuotedPlaceholder quotedPlaceholder = findQuotedPlaceholder(sql, start, end);
        if (quotedPlaceholder == null) {
            parameterized.append(sql, position, start);
            parameterized.append("?");
            params.add(value);
            return end + 1;
        }

        parameterized.append(sql, position, quotedPlaceholder.start());
        parameterized.append("?");
        params.add(placeholderValue(value, quotedPlaceholder, replacements));
        return quotedPlaceholder.end();
    }

    private static Object placeholderValue(Object value, QuotedPlaceholder quotedPlaceholder, Map<String, ?> replacements) {
        String prefix = substituteQuotedLiteralPart(quotedPlaceholder.prefix(), quotedPlaceholder.quote(), replacements);
        String suffix = substituteQuotedLiteralPart(quotedPlaceholder.suffix(), quotedPlaceholder.quote(), replacements);
        return prefix.isEmpty() && suffix.isEmpty() ? value : prefix + nullSafeSqlLiteralValue(value) + suffix;
    }

    private static String substituteQuotedLiteralPart(String value, char quote, Map<String, ?> replacements) {
        StringBuilder substituted = new StringBuilder();
        int position = 0;
        while (position < value.length()) {
            int start = value.indexOf("${", position);
            if (start < 0) {
                substituted.append(unescapeSqlLiteralPart(value.substring(position), quote));
                break;
            }

            int end = value.indexOf("}", start);
            if (end < 0) {
                substituted.append(unescapeSqlLiteralPart(value.substring(position), quote));
                break;
            }

            String name = value.substring(start + 2, end);
            if (replacements.containsKey(name)) {
                substituted.append(unescapeSqlLiteralPart(value.substring(position, start), quote));
                substituted.append(nullSafeSqlLiteralValue(replacements.get(name)));
            } else {
                substituted.append(unescapeSqlLiteralPart(value.substring(position, end + 1), quote));
            }
            position = end + 1;
        }
        return substituted.toString();
    }

    private static QuotedPlaceholder findQuotedPlaceholder(String sql, int placeholderStart, int placeholderEnd) {
        QuoteContext quoteContext = quoteContext(sql, placeholderStart);
        if (quoteContext == null) {
            return null;
        }

        int closeQuote = findClosingQuote(sql, placeholderEnd + 1, quoteContext.quote());
        if (closeQuote < 0) {
            return null;
        }

        return new QuotedPlaceholder(
                quoteContext.openQuote(),
                closeQuote + 1,
                sql.substring(quoteContext.openQuote() + 1, placeholderStart),
                sql.substring(placeholderEnd + 1, closeQuote),
                quoteContext.quote());
    }

    private static QuoteContext quoteContext(String sql, int position) {
        QuoteTracker tracker = new QuoteTracker();
        int index = 0;
        while (index < position) {
            char current = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';
            index += tracker.consume(current, next, index) ? 2 : 1;
        }
        return tracker.context();
    }

    private static int findClosingQuote(String sql, int after, char quote) {
        int index = after;
        while (index < sql.length()) {
            char current = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';
            if (current == quote && next == quote) {
                index += 2;
            } else if (current == quote) {
                return index;
            } else {
                index++;
            }
        }
        return -1;
    }

    private static String unescapeSqlLiteralPart(String value, char quote) {
        String doubledQuote = String.valueOf(quote) + quote;
        return value.replace(doubledQuote, String.valueOf(quote));
    }

    private static String nullSafeSqlLiteralValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private record QuotedPlaceholder(int start, int end, String prefix, String suffix, char quote) {
    }

    private record QuoteContext(char quote, int openQuote) {
    }

    private static final class QuoteTracker {
        private char quote;
        private int openQuote = -1;

        private boolean consume(char current, char next, int index) {
            if (quote == '\0') {
                openQuote(current, index);
                return false;
            }
            if (current == quote && next == quote) {
                return true;
            }
            if (current == quote) {
                closeQuote();
            }
            return false;
        }

        private void openQuote(char current, int index) {
            if (current == '\'' || current == '"') {
                quote = current;
                openQuote = index;
            }
        }

        private void closeQuote() {
            quote = '\0';
            openQuote = -1;
        }

        private QuoteContext context() {
            return quote == '\0' ? null : new QuoteContext(quote, openQuote);
        }
    }

}
