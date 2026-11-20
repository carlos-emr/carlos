/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.Misc;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Repository;

/**
 * Reads the application's trusted document-transfer definition. JDBC is needed here because
 * the definition exports physical column and table names as well as configured expressions.
 * No connection, cursor or metadata object escapes this DAO. The connection participates in
 * the caller's Spring transaction and is released on both mapping and execution failures.
 * @since 2026-11-06
 */
@Repository
public class DocumentTransferDao {
    private final DataSource dataSource;

    /** @param dataSource DataSource managed application connection source */
    public DocumentTransferDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * SQL expressions from trusted application configuration only; never include request data.
     * @param from String trusted table/join expression
     * @param link String trusted demographic-link expression
     * @param where String trusted optional additional predicate
     * @param order String trusted optional ordering
     * @param itemExpression String trusted exported item expression
     * @param fieldExpressions List&lt;String&gt; trusted exported field expressions
     */
    public record Definition(String from, String link, String where, String order,
                             String itemExpression, List<String> fieldExpressions) {
        /** Copies the configured expression list. */
        public Definition {
            fieldExpressions = List.copyOf(fieldExpressions);
        }
    }

    /**
     * Physical column metadata and legacy string conversion.
     * @param qualifiedName String table-qualified column name
     * @param value String trimmed value, empty for SQL NULL
     */
    public record Column(String qualifiedName, String value) { }

    /**
     * Immutable export projection.
     * @param columns List&lt;Column&gt; physical column metadata and values
     * @param item String configured item value
     * @param fields List&lt;String&gt; configured field values
     */
    public record Row(List<Column> columns, String item, List<String> fields) {
        /** Copies the column and field lists. */
        public Row {
            columns = List.copyOf(columns);
            fields = List.copyOf(fields);
        }
    }

    /**
     * Reads a configured export for one demographic. Definition expressions must come only
     * from trusted application configuration, never request data. Only demographicNo is bound.
     * @param definition Definition trusted classpath export configuration
     * @param demographicNo int demographic identifier bound to the link expression
     * @return List&lt;Row&gt; immutable mapped rows, empty when none match
     * @throws SQLException if validation, connection acquisition, execution or mapping fails
     */
    public List<Row> read(Definition definition, int demographicNo) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT *, ").append(definition.itemExpression()).append(" AS fldItem");
        for (int i = 0; i < definition.fieldExpressions().size(); i++) {
            sql.append(", ").append(definition.fieldExpressions().get(i)).append(" AS fld").append(i);
        }
        sql.append(" FROM ").append(definition.from()).append(" WHERE ").append(definition.link()).append(" = ?");
        if (!definition.where().isEmpty()) sql.append(" AND ").append(definition.where());
        if (!definition.order().isEmpty()) sql.append(" ORDER BY ").append(definition.order());
        LegacyJdbcQuery.validateReportSelectQuery(sql.toString());
        Connection connection = DataSourceUtils.doGetConnection(dataSource);
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setInt(1, demographicNo);
            try (ResultSet result = statement.executeQuery()) {
                ResultSetMetaData metadata = result.getMetaData();
                List<Row> rows = new ArrayList<>();
                while (result.next()) {
                    List<Column> columns = new ArrayList<>();
                    for (int i = 1; i <= metadata.getColumnCount(); i++) {
                        if (!metadata.getColumnName(i).startsWith("fld")) {
                            columns.add(new Column(metadata.getTableName(i) + "." + metadata.getColumnName(i),
                                    Misc.getString(result, i)));
                        }
                    }
                    List<String> fields = new ArrayList<>();
                    for (int i = 0; i < definition.fieldExpressions().size(); i++) {
                        fields.add(Misc.getString(result, "fld" + i));
                    }
                    rows.add(new Row(columns, Misc.getString(result, "fldItem"), fields));
                }
                return List.copyOf(rows);
            }
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}
