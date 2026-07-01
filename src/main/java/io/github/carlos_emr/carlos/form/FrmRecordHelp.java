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


package io.github.carlos_emr.carlos.form;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import io.github.carlos_emr.Misc;
import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.w3c.dom.Document;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.util.JDBCUtil;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.utility.CachedDateFormats;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class FrmRecordHelp {
    private static final Pattern INSERT_ROW_ID_FILTER = Pattern.compile("(?i)\\bID\\s*=\\s*0\\b");

    private String _dateFormat = "yyyy/MM/dd";
    private String _newDateFormat = "yyyy-MM-dd"; //handles both date formats, but yyyy/MM/dd is displayed to avoid deprecation

    private static final HashSet VALID_ACTION_VALUES = new HashSet<String>() {
        {
            add("print");
            add("save");
            add("exit");
            add("graph");
            add("printAll");
            add("printLabReq");
            add("printConsultLetter");
            add("printNewOBConsult");
            add("printMaleConsultLetter");
            add("printIUDTemplate");
            add("printAllJasperReport");
            add("formEpistaxisLetter");
            add("formOtologicLetter");
            add("formSinusLetter");
            add("followUpLetter");
        }
    };

    public void setDateFormat(String s) {
        _dateFormat = s;
    }

    /**
     * @deprecated Use {@link #getFormRecord(String, Object...)} with parameterized SQL instead.
     */
    @Deprecated
    public Properties getFormRecord(String sql) //int demographicNo, int existingID)
            throws SQLException {
        return getFormRecord(sql, new Object[0]);
    }

    /**
     * Retrieves a form record using a parameterized SQL query.
     *
     * @param sql    the SQL query with {@code ?} placeholders
     * @param params the parameter values to bind to the placeholders
     * @return Properties containing the column name/value pairs from the first result row
     * @throws SQLException if a database access error occurs
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public Properties getFormRecord(String sql, Object... params)
            throws SQLException {
        Properties props = new Properties();

        try (ResultSet rs = LegacyJdbcQuery.getPreparedResultSet(sql, params)) {
            if (rs.next()) {
                ResultSetMetaData md = rs.getMetaData();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    String name = md.getColumnName(i);
                    String value;

                    String colTypeName = md.getColumnTypeName(i);
                    if (colTypeName != null && (colTypeName.regionMatches(true, 0, "TINYINT", 0, 7) || colTypeName.equalsIgnoreCase("bit"))) {
                        if (rs.getInt(i) == 1)
                            value = "checked='checked'";
                        else
                            value = "";
                    } else if (md.getColumnTypeName(i).equalsIgnoreCase("date"))
                        value = UtilDateUtilities.DateToString(rs.getDate(i), _dateFormat);
                    else if (md.getColumnTypeName(i).equalsIgnoreCase("timestamp"))
                        value = UtilDateUtilities.DateToString(rs.getTimestamp(i), "yyyy/MM/dd HH:mm:ss");
                    else
                        value = Misc.getString(rs, i);

                    if (value != null)
                        props.setProperty(name, value);
                }
            }
        }
        return props;
    }

    /**
     * @deprecated Use {@link #saveFormRecord(Properties, String, Object...)} with parameterized SQL instead.
     */
    @Deprecated
    public synchronized int saveFormRecord(Properties props, String sql) throws SQLException {
        throw new UnsupportedOperationException("Form record SQL must be parameterized");
    }

    /**
     * Saves a form record using a parameterized SQL query.
     *
     * @param props  the form properties to save
     * @param sql    the SQL query with {@code ?} placeholders
     * @param params the parameter values to bind to the placeholders
     * @return the auto-generated ID of the inserted record
     * @throws SQLException if a database access error occurs
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public synchronized int saveFormRecord(Properties props, String sql, Object... params) throws SQLException {

        try (Connection connection = LegacyJdbcQuery.getConnection()) {
            LegacyJdbcQuery.TrustedSql saveSql = LegacyJdbcQuery.trustedSelectSql(sql);
            int insertedId;
            try (PreparedStatement ps = prepareResultSetStatement(connection, saveSql, true, params);
                 ResultSet rs = ps.executeQuery()) {
                rs.moveToInsertRow();
                updateResultSet(props, rs, true);
                rs.insertRow();
                insertedId = getLastInsertedId(connection);
                String saveAsXml = CarlosProperties.getInstance().getProperty("save_as_xml", "false");

                if (saveAsXml.equalsIgnoreCase("true")) {

                    String demographicNo = props.getProperty("demographic_no");
                    int index = sql.indexOf("form");
                    int spaceIndex = sql.indexOf(" ", index);
                    String formClass = sql.substring(index, spaceIndex);
                    Date d = new Date();
                    String now = UtilDateUtilities.DateToString(d, "yyyyMMddHHmmss");
                    String place = CarlosProperties.getInstance().getProperty("form_record_path", "/root");
                    String archiveFileName = formClass + "_" + demographicNo + "_" + now + ".xml";

                    try {
                        if (!place.endsWith(System.getProperty("file.separator")))
                            place = place + System.getProperty("file.separator");
                        String fileName = place + archiveFileName;
                        try (PreparedStatement archiveStatement = prepareResultSetStatement(connection,
                                LegacyJdbcQuery.trustedSelectSql(archiveSelectSql(sql)), false,
                                archiveParams(params, insertedId))) {
                            try (ResultSet archiveResult = archiveStatement.executeQuery()) {
                                Document doc = JDBCUtil.toDocument(archiveResult);
                                JDBCUtil.saveAsXML(doc, fileName);
                            }
                        }
                    } catch (SQLException | ParserConfigurationException | TransformerException | IOException |
                            RuntimeException e) {
                        MiscUtils.getLogger().error(
                                "Unable to archive form record XML after database save; continuing without XML copy",
                                e);
                    }
                }
            }

            return insertedId;
        }
    }

    private String archiveSelectSql(String sql) throws SQLException {
        Matcher matcher = INSERT_ROW_ID_FILTER.matcher(sql);
        if (!matcher.find()) {
            throw new SQLException("Form record SQL must include ID=0 for XML archive");
        }
        return matcher.replaceFirst("ID = ?");
    }

    private Object[] archiveParams(Object[] params, int insertedId) {
        Object[] archiveParams = new Object[params.length + 1];
        System.arraycopy(params, 0, archiveParams, 0, params.length);
        archiveParams[params.length] = insertedId;
        return archiveParams;
    }

    private PreparedStatement prepareResultSetStatement(Connection connection, LegacyJdbcQuery.TrustedSql sql,
            boolean updatable,
            Object... params) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(sql.sql(), ResultSet.TYPE_SCROLL_SENSITIVE,
                updatable ? ResultSet.CONCUR_UPDATABLE : ResultSet.CONCUR_READ_ONLY);
        try {
            bindParams(ps, params);
            return ps;
        } catch (SQLException | RuntimeException e) {
            try {
                ps.close();
            } catch (SQLException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    private void bindParams(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object value = params[i];
            if (value == null) {
                ps.setNull(i + 1, Types.NULL);
            } else {
                ps.setObject(i + 1, value);
            }
        }
    }

    private int getLastInsertedId(Connection connection) throws SQLException {
        String dbType = CarlosProperties.getInstance() != null ? CarlosProperties.getInstance().getProperty("db_type",
                "") : "";
        String idSql = lastInsertedIdSql(dbType);

        try (PreparedStatement ps = prepareResultSetStatement(connection, LegacyJdbcQuery.trustedSelectSql(idSql),
                false);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }

    static String lastInsertedIdSql(String dbType) throws SQLException {
        if (StringUtils.isBlank(dbType) || dbType.equalsIgnoreCase("mysql")) {
            return "SELECT LAST_INSERT_ID()";
        }
        if (dbType.equalsIgnoreCase("postgresql")) {
            // Legacy ResultSet insert mode does not expose the table sequence name
            // here. LASTVAL() is safe only on the same JDBC session immediately
            // after this insert; new PostgreSQL insert paths should use RETURNING
            // or Statement.RETURN_GENERATED_KEYS instead.
            return "SELECT LASTVAL()";
        }
        throw new SQLException("ERROR: Database " + dbType + " unrecognized.");
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public ResultSet updateResultSet(Properties props, ResultSet rs, boolean bInsert) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();

        for (int i = 1; i <= md.getColumnCount(); i++) {
            String name = md.getColumnName(i);
            if (name.equalsIgnoreCase("ID")) {
                if (bInsert)
                    rs.updateInt(name, 0);
                continue;
            }

            String value = props.getProperty(name, null);

            String colTypeName = md.getColumnTypeName(i);
            if (colTypeName != null && (colTypeName.regionMatches(true, 0, "TINYINT", 0, 7) || colTypeName.equalsIgnoreCase("bit"))) {
                if (value != null) {
                    if (value.equalsIgnoreCase("on") || value.equalsIgnoreCase("checked='checked'")) {
                        rs.updateInt(name, 1);

                    } else {
                        rs.updateInt(name, 0);
                    }
                } else {
                    rs.updateInt(name, 0);
                }
                continue;
            }

            if (md.getColumnTypeName(i).equalsIgnoreCase("date")) {
                java.util.Date d;
                if (md.getColumnName(i).equalsIgnoreCase("formEdited")) {
                    d = new Date();
                } else {
                    if ((value == null) || (value.indexOf('/') != -1))
                        d = UtilDateUtilities.StringToDate(value, _dateFormat);
                    else
                        d = UtilDateUtilities.StringToDate(value, _newDateFormat);
                }
                if (d == null)
                    rs.updateNull(name);
                else
                    rs.updateDate(name, new java.sql.Date(d.getTime()));
                continue;
            }

            if (md.getColumnTypeName(i).equalsIgnoreCase("timestamp")) {
                Date d;
                if (md.getColumnName(i).equalsIgnoreCase("formEdited")) {
                    d = new Date();
                } else {
                    d = UtilDateUtilities.StringToDate(value, "yyyyMMddHHmmss");
                }
                if (d == null)
                    rs.updateNull(name);
                else
                    rs.updateTimestamp(name, new java.sql.Timestamp(d.getTime()));
                continue;
            }

            if (value == null)
                rs.updateNull(name);
            else
                rs.updateString(name, value);
        }

        return rs;
    }

    /**
     * @deprecated Use {@link #updateFormRecord(Properties, String, Object...)} with parameterized SQL instead.
     */
    @Deprecated
    //for page form
    public void updateFormRecord(Properties props, String sql) throws SQLException {
        updateFormRecord(props, sql, new Object[0]);
    }

    /**
     * Updates a form record using a parameterized SQL query.
     *
     * @param props  the form properties to update
     * @param sql    the SQL query with {@code ?} placeholders
     * @param params the parameter values to bind to the placeholders
     * @throws SQLException if a database access error occurs
     */
    public void updateFormRecord(Properties props, String sql, Object... params) throws SQLException {


        try (ResultSet rs = LegacyJdbcQuery.getPreparedResultSet(sql, true, params)) {
            if (!rs.next()) {
                throw new SQLException("No form record found for update");
            }
            updateResultSet(props, rs, false);
            rs.updateRow();
        }
    }

    /**
     * @deprecated Use {@link #getPrintRecord(String, Object...)} with parameterized SQL instead.
     */
    @Deprecated
    public Properties getPrintRecord(String sql) throws SQLException {
        return getPrintRecord(sql, new Object[0]);
    }

    /**
     * Retrieves a single print record using a parameterized SQL query.
     *
     * @param sql    the SQL query with {@code ?} placeholders
     * @param params the parameter values to bind to the placeholders
     * @return Properties containing the column name/value pairs from the first result row
     * @throws SQLException if a database access error occurs
     */
    public Properties getPrintRecord(String sql, Object... params) throws SQLException {
        Properties props = new Properties();

        try (ResultSet rs = LegacyJdbcQuery.getPreparedResultSet(sql, params)) {
            if (rs.next()) {
                props = getResultsAsProperties(rs);
            }
        }
        return props;
    }

    /**
     * @deprecated Use {@link #getPrintRecords(String, Object...)} with parameterized SQL instead.
     */
    @Deprecated
    public List<Properties> getPrintRecords(String sql) throws SQLException {
        return getPrintRecords(sql, new Object[0]);
    }

    /**
     * Retrieves multiple print records using a parameterized SQL query.
     *
     * @param sql    the SQL query with {@code ?} placeholders
     * @param params the parameter values to bind to the placeholders
     * @return List of Properties, one per result row
     * @throws SQLException if a database access error occurs
     */
    public List<Properties> getPrintRecords(String sql, Object... params) throws SQLException {
        ArrayList<Properties> results = new ArrayList<Properties>();

        try (ResultSet rs = LegacyJdbcQuery.getPreparedResultSet(sql, params)) {
            while (rs.next()) {
                Properties p = getResultsAsProperties(rs);
                results.add(p);
            }
        }

        return results;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private Properties getResultsAsProperties(ResultSet rs) throws SQLException {
        Properties p = new Properties();
        ResultSetMetaData md = rs.getMetaData();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            String name = md.getColumnName(i);
            String value;

            String colTypeName = md.getColumnTypeName(i);
            if (colTypeName != null && (colTypeName.regionMatches(true, 0, "TINYINT", 0, 7) || colTypeName.equalsIgnoreCase("bit")) && md.getScale(i) == 1) {
                if (rs.getInt(i) == 1)
                    value = "on";
                else
                    value = "off";
            } else if (md.getColumnTypeName(i).equalsIgnoreCase("date"))
                value = UtilDateUtilities.DateToString(rs.getDate(i), _dateFormat);
            else
                value = Misc.getString(rs, i);

            if (value != null)
                p.setProperty(name, value);
        }

        return (p);
    }

    /**
     * @deprecated Use {@link #getDemographicIds(String, Object...)} with parameterized SQL instead.
     */
    @Deprecated
    public List<Integer> getDemographicIds(String sql) throws SQLException {
        return getDemographicIds(sql, new Object[0]);
    }

    /**
     * Retrieves demographic IDs using a parameterized SQL query.
     *
     * @param sql    the SQL query with {@code ?} placeholders
     * @param params the parameter values to bind to the placeholders
     * @return List of demographic_no values from the result set
     * @throws SQLException if a database access error occurs
     */
    public List<Integer> getDemographicIds(String sql, Object... params) throws SQLException {
        List<Integer> results = new ArrayList<Integer>();

        try (ResultSet rs = LegacyJdbcQuery.getPreparedResultSet(sql, params)) {
            while (rs.next()) {
                results.add(rs.getInt("demographic_no"));
            }
        }

        return results;
    }

    public String findActionValue(String submit) {
        return VALID_ACTION_VALUES.contains(submit) ? submit : "failure";
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String createActionURL(String where, String action, String demoId, String formId) {
        String temp = null;

        if (action.equalsIgnoreCase("print")) {
            temp = where + "?demoNo=" + demoId + "&formId=" + formId; // + "&study_no=" + studyId +
            // "&study_link" + studyLink;
        } else if (action.equalsIgnoreCase("save")) {
            temp = where + "?demographic_no=" + demoId + "&formId=" + formId; // "&study_no=" +
            // studyId +
            // "&study_link" +
            // studyLink; //+
        } else if (action.equalsIgnoreCase("exit")) {
            temp = where;
        } else if (action.equals("printAll")) {
            temp = where + "?demographic_no=" + demoId + "&formId=" + formId;
        } else if (action.equalsIgnoreCase("printConsultLetter")) {
            temp = where + "?formId=" + formId;
        } else if (action.equalsIgnoreCase("printMaleConsultLetter")) {
            temp = where + "?formId=" + formId;
        } else if (isOpenHealthCustomForm(action)) {
            temp = where + "?demographic_no=" + demoId + "&formId=" + formId;
        } else {
            temp = where;
        }

        return temp;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private boolean isOpenHealthCustomForm(String action) {
        return "formEpistaxisLetter".equalsIgnoreCase(action)
                || "formOtologicLetter".equalsIgnoreCase(action)
                || "followUpLetter".equalsIgnoreCase(action)
                || "formSinusLetter".equalsIgnoreCase(action);
    }

    public static void convertBooleanToChecked(Properties p) {
        HashSet<Object> keySet = new HashSet<Object>();
        keySet.addAll(p.keySet());

        for (Object key : keySet) {
            String keyName = (String) key;
            if (keyName.startsWith("b_")) {
                String value = StringUtils.trimToNull(p.getProperty(keyName));

                if ("1".equals(value)) {
                    p.setProperty(keyName, "checked='checked'");
                } else {
                    p.setProperty(keyName, "");
                }
            }
        }
    }

    public Date getDateFieldOrNull(Properties props, String fieldName) {
        String value = props.getProperty(fieldName);
        Date result = null;
        if (value != null) {
            try {
                String pattern = value.contains("/") ? _dateFormat : _newDateFormat;
                result = CachedDateFormats.parse(value, pattern);
            } catch (ParseException e) {
                MiscUtils.getLogger().debug("Unparseable date for field {}: {}", fieldName, value);
            }
        }
        return result;
    }

    public String parseDateFieldOrNull(Date date) {
        String result = null;
        if (date != null) {
            result = CachedDateFormats.format(date, _dateFormat);
        }
        return result;
    }
}
