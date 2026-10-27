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

import java.util.Locale;
import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.prevention.dto.PreventionDatePrecisionException;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Service;

/** Refuses Date-only SQL decisions before cutoff/filter/aggregate operations can discard uncertainty. */
@Service
public class PreventionReadSafetyService {
    private final PreventionDao preventions;

    public PreventionReadSafetyService(PreventionDao preventions) {
        this.preventions = preventions;
    }

    /** Call after patient authorization. Null patient scopes a population report. */
    public void requireExactDates(Integer demographicNo) {
        if (preventions.hasUncertainPreventionDates(demographicNo)) throw new PreventionDatePrecisionException();
    }

    /** Native or installed SQL definitions get no implicit precision waiver. */
    public void requireSafeSql(String sql, Integer demographicNo) {
        if (readsPreventions(sql)) requireExactDates(demographicNo);
    }

    /** Structural table inspection handles aliases, nested queries and qualified/quoted names. */
    public static boolean readsPreventions(String sql) {
        if (sql == null || !sql.toLowerCase(Locale.ROOT).contains("prevent")) return false;
        try {
            String inspection = sql.replaceAll("\\$\\{[^}]+}", "?");
            for (String table : new TablesNamesFinder<Void>().getTables(CCJSqlParserUtil.parse(inspection))) {
                String name = table.replace("`", "").replace("\"", "");
                int dot = name.lastIndexOf('.');
                if ("preventions".equals(name.substring(dot + 1).toLowerCase(Locale.ROOT))) return true;
            }
            return false;
        } catch (JSQLParserException | RuntimeException unsupported) {
            // A legacy dialect that might reference prevention history cannot bypass the guard.
            throw new PreventionDatePrecisionException("Prevention SQL requires a supported precision-aware query or review");
        }
    }
}
