/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;

/** Compatibility boundary for administrator-installed arbitrary-SQL templates only.
 * Fixed bundled reports must use typed data sources; configurable SQL is never sent to JPA.
 */
final class ConfiguredJasperTemplateRenderer {
    private ConfiguredJasperTemplateRenderer() { }
    static JasperPrint fill(JasperReport report, Map<String, Object> parameters) throws JRException, SQLException {
        try (Connection connection = LegacyJdbcQuery.getConnection()) {
            return JasperFillManager.fillReport(report, parameters, connection);
        }
    }
}
