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
package io.github.carlos_emr.carlos.report.reportByTemplate;

import java.sql.SQLException;
import java.util.List;

import org.jdom2.Element;

import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;

/**
 * Save-time SQL policy for Report by Template definitions.
 *
 * <p>A template carries SQL in two places, and both reach the database: the {@code <query>}
 * body runs when the report is generated, and every {@code <param>}'s {@code <param-query>}
 * runs as soon as anyone opens the template, to fill that parameter's choice list. Before this
 * check a template was stored with whatever SQL it held, so an UPDATE or DELETE was accepted at
 * upload and only refused (for {@code <query>}) once someone ran the report, while a
 * {@code <param-query>} was never checked at all.</p>
 *
 * <p>The rule applied here is exactly the one the run-time boundary already enforces
 * ({@link LegacyJdbcQuery#validateReportSelectQuery(String)}): a single SELECT, no comments,
 * no stacked statements and no file access. Applying it when the template is saved means a
 * template that could never run is rejected with a message the author can act on, and the same
 * rule is re-checked before a stored {@code <param-query>} executes, which covers templates
 * saved before this check existed.</p>
 *
 * <p>This is also the application-side control the packaged WAF exclusion for the template
 * editor relies on: the front door stops scoring {@code ARGS:xmltext} for SQL keywords,
 * because SQL is what that argument is for, so the statement shape has to be enforced here.</p>
 *
 * @since 2026-10-01
 */
public final class ReportTemplateSqlValidator {

    private ReportTemplateSqlValidator() {
    }

    /**
     * Checks every SQL statement a {@code <report>} element carries.
     *
     * @param report the parsed {@code <report>} element about to be stored
     * @return {@code null} when every statement is an allowed SELECT, otherwise a user-facing
     *         message (starting with {@code "Error:"}) naming the statement that was refused
     */
    public static String validateReport(Element report) {
        if (report == null) {
            return null;
        }
        String query = report.getChildText("query");
        if (query != null && !query.isBlank()) {
            boolean sequence = Boolean.parseBoolean(report.getAttributeValue("sequence"));
            List<String> statements = sequence
                    ? ReportObjectGeneric.splitSequencedSql(query)
                    : List.of(query);
            for (int i = 0; i < statements.size(); i++) {
                String refusal = refusalFor(statements.get(i));
                if (refusal != null) {
                    String which = sequence ? " (statement " + (i + 1) + ")" : "";
                    return "Error: The <query>" + which + " was refused: " + refusal;
                }
            }
        }
        for (Element param : report.getChildren("param")) {
            String paramQuery = param.getChildText("param-query");
            if (paramQuery == null) {
                continue;
            }
            String refusal = refusalFor(paramQuery);
            if (refusal != null) {
                String id = param.getAttributeValue("id");
                return "Error: The <param-query> of parameter '" + (id == null ? "" : id)
                        + "' was refused: " + refusal;
            }
        }
        return null;
    }

    /**
     * Returns whether one stored statement may be executed by the template machinery.
     *
     * @param sql statement text, with any {@code {param}} placeholders still in place
     * @return {@code true} when the statement passes the report SELECT policy
     */
    public static boolean isAllowedStatement(String sql) {
        return refusalFor(sql) == null;
    }

    private static String refusalFor(String sql) {
        try {
            LegacyJdbcQuery.validateReportSelectQuery(sql);
            return null;
        } catch (SQLException e) {
            // The validator's messages are fixed strings ("Only SELECT statements are allowed",
            // ...); they never echo the SQL back, so they are safe to show the template author.
            return e.getMessage();
        }
    }
}
