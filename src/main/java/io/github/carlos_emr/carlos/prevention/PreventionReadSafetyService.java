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
package io.github.carlos_emr.carlos.prevention;

import java.util.LinkedHashSet;
import java.util.Set;
import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.prevention.dto.PreventionDatePrecisionException;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Service;

/** Refuses Date-only SQL decisions before cutoff/filter/aggregate operations can discard uncertainty. */
@Service
public class PreventionReadSafetyService {
    /** The native prevention table, compared without regard to ASCII case. */
    private static final String PREVENTIONS_TABLE = "preventions";

    private final PreventionDao preventions;

    public PreventionReadSafetyService(PreventionDao preventions) {
        this.preventions = preventions;
    }

    /** Call after patient authorization. Population SQL does not gate unrelated patient charts. */
    public void requireExactDates(Integer demographicNo) {
        if (demographicNo != null && preventions.hasUncertainPreventionDates(demographicNo)) throw new PreventionDatePrecisionException();
    }

    /** Native or installed SQL definitions get no implicit precision waiver. */
    public void requireSafeSql(String sql, Integer demographicNo) {
        if (readsPreventions(sql)) requireExactDates(demographicNo);
    }

    /**
     * Whether an installed SQL definition reads the native {@code preventions} table.
     *
     * <p>Two inspections decide, and either one finding the table is enough. {@link SqlTableReferences}
     * reads the tables named in MariaDB table positions under MariaDB's own lexical rules; JSqlParser
     * inspects the statement structurally. Neither alone matches MariaDB (issue #6344): JSqlParser
     * rejects both shipped influenza dashboard queries, and it takes text MariaDB runs, after
     * {@code 1--} or inside an executable comment, for a comment. A definition that names no
     * prevention table in either view keeps its original execution boundary, so an unparseable
     * definition is never refused only because the parser could not read it.</p>
     *
     * @param sql an SQL definition, possibly holding {@code ${...}} template placeholders; may be null
     * @return true when the definition names {@code preventions} as a table, in any schema or ASCII case
     */
    public static boolean readsPreventions(String sql) {
        if (sql == null || !SqlTableReferences.foldAsciiCase(sql).contains("prevent")) {
            return false;
        }
        String inspection = sql.replaceAll("\\$\\{[^}]+}", "?");
        return namesPreventions(SqlTableReferences.tableNames(inspection))
                || namesPreventions(parsedTableNames(inspection));
    }

    private static boolean namesPreventions(Set<String> tables) {
        for (String table : tables) {
            if (PREVENTIONS_TABLE.equals(SqlTableReferences.foldAsciiCase(table))) {
                return true;
            }
        }
        return false;
    }

    /** JSqlParser's tables, unqualified and unquoted; none when it cannot read the statement. */
    private static Set<String> parsedTableNames(String sql) {
        try {
            Set<String> tables = new LinkedHashSet<>();
            for (String table : new TablesNamesFinder<Void>().getTables(CCJSqlParserUtil.parse(sql))) {
                String name = table.replace("`", "").replace("\"", "");
                tables.add(name.substring(name.lastIndexOf('.') + 1));
            }
            return tables;
        } catch (JSQLParserException | RuntimeException unsupported) {
            // Legacy MariaDB SQL the parser rejects has already been read by the table-position scan.
            // The diagnostic never includes the SQL text.
            MiscUtils.getLogger().debug("Prevention SQL structural inspection unavailable");
            return Set.of();
        }
    }
}
