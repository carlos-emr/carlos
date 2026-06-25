/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2005, 2009 IBM Corporation and others.
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
 * Contributors:
 * <Quatro Group Software Systems inc.>  <OSCAR Team>
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.daos;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.Misc;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.dao.AbstractJpaDao;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.db.DBPreparedHandlerParam;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;

import io.github.carlos_emr.carlos.commons.KeyConstants;
import io.github.carlos_emr.carlos.model.FieldDefValue;
import io.github.carlos_emr.carlos.model.LookupCodeValue;
import io.github.carlos_emr.carlos.model.LookupTableDefValue;
import io.github.carlos_emr.carlos.model.LstOrgcd;
import io.github.carlos_emr.carlos.model.security.SecProvider;
import io.github.carlos_emr.carlos.util.SqlIdentifierValidator;
import io.github.carlos_emr.carlos.utils.Utility;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;
import io.github.carlos_emr.carlos.utility.JpqlQueryHelper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Transactional
public class LookupDaoImpl extends AbstractJpaDao implements LookupDao {

    private static final Logger log = MiscUtils.getLogger();

    /*
     * Column property mappings defined by the generic idx
     * 1 - Code 2 - Description 3 Active
     * 4 - Display Order, 5 - ParentCode 6 - Buf1 7 - CodeTree
     * 8 - Last Update User 9 - Last Update Date
     * 10 - 16 Buf3 - Buf9 17 - CodeCSV
     */
    private ProviderDao providerDao;

    @Override
    public List LoadCodeList(String tableId, boolean activeOnly, String code, String codeDesc) {
        return LoadCodeList(tableId, activeOnly, "", code, codeDesc);
    }

    @Override
    public LookupCodeValue GetCode(String tableId, String code) {
        if (code == null || "".equals(code))
            return null;
        List lst = LoadCodeList(tableId, false, code, "");
        LookupCodeValue lkv = null;
        if (lst.size() > 0) {
            lkv = (LookupCodeValue) lst.get(0);
        }
        return lkv;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public List LoadCodeList(String tableId, boolean activeOnly, String parentCode, String code, String codeDesc) {
        String pCd = parentCode;
        if ("USR".equals(tableId))
            parentCode = null;
        LookupTableDefValue tableDef = GetLookupTableDef(tableId);
        if (tableDef == null)
            return (new ArrayList<LookupCodeValue>());
        List fields = LoadFieldDefList(tableId);
        DBPreparedHandlerParam[] params = new DBPreparedHandlerParam[100];
        String fieldNames[] = new String[17];
        String sSQL1 = "";
        String tableName = validateSqlIdentifier(tableDef.getTableName());
        String sSQL = "select distinct ";
        boolean activeFieldExists = true;
        for (int i = 1; i <= 17; i++) {
            boolean ok = false;
            for (int j = 0; j < fields.size(); j++) {
                FieldDefValue fdef = (FieldDefValue) fields.get(j);
                if (fdef.getGenericIdx() == i) {
                    String fieldSqlExpression = validateFieldSql(fdef.getFieldSQL());
                    if (fieldSqlExpression.indexOf('(') >= 0) {
                        String fieldName = validateSqlAlias(fdef.getFieldName());
                        sSQL += fieldSqlExpression + " " + fieldName + ",";
                        fieldNames[i - 1] = fieldName;
                    } else {
                        String fieldName = validateLoadCodeListFieldName(fieldSqlExpression);
                        sSQL += qualifyLoadCodeListField(fieldSqlExpression) + ",";
                        fieldNames[i - 1] = fieldName;
                    }
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                if (i == 3) {
                    activeFieldExists = false;
                    sSQL += " 1 field" + i + ",";
                } else {
                    sSQL += " null field" + i + ",";
                }
                fieldNames[i - 1] = "field" + i;
            }
        }
        sSQL = sSQL.substring(0, sSQL.length() - 1);
        sSQL += " from " + tableName;
        sSQL1 = Misc.replace(sSQL, "s.", "a.") + " a,";
        sSQL += " s where 1=1";
        int i = 0;
        if (activeFieldExists && activeOnly) {
            sSQL += " and " + fieldNames[2] + "=?";
            params[i++] = new DBPreparedHandlerParam(1);
        }
        if (!Utility.IsEmpty(parentCode)) {
            sSQL += " and " + fieldNames[4] + "=?";
            params[i++] = new DBPreparedHandlerParam(parentCode);
        }
        if (!Utility.IsEmpty(code)) {
            // org table is different from other tables
            if (tableId.equals("ORG")) {
                sSQL += " and " + fieldNames[0] + " like ('%'||";
                String[] codes = code.split(",");
                sSQL += "?";
                params[i++] = new DBPreparedHandlerParam(codes[0]);
                for (int k = 1; k < codes.length; k++) {
                    sSQL += ",?";
                    params[i++] = new DBPreparedHandlerParam(codes[k]);
                }
                sSQL += ")";
            } else {
                sSQL += " and " + fieldNames[0] + " in (";
                String[] codes = code.split(",");
                sSQL += "?";
                params[i++] = new DBPreparedHandlerParam(codes[0]);
                for (int k = 1; k < codes.length; k++) {
                    if (codes[k].equals(""))
                        continue;
                    sSQL += ",?";
                    params[i++] = new DBPreparedHandlerParam(codes[k]);
                }
                sSQL += ")";
            }
        }
        if (!Utility.IsEmpty(codeDesc)) {
            sSQL += " and upper(" + fieldNames[1] + ") like ?";
            params[i++] = new DBPreparedHandlerParam("%" + codeDesc.toUpperCase() + "%");
        }

        if (tableDef.isTree()) {
            sSQL = sSQL1 + "(" + sSQL + ") b";
            sSQL += " where b." + fieldNames[6] + " like a." + fieldNames[6] + "||'%'";
        }
        // if (tableDef.isTree())
        // {
        // sSQL += " order by 7,1";
        // } else {
        sSQL += " order by 4,2";
        // }
        Query query = entityManager().createNativeQuery(sSQL); // nosemgrep: hibernate-sqli -- table/column names from internal LookupTableDef config, values bound via positional params below
        for (int j = 0; j < i; j++) {
            bindParam(query, j + 1, params[j]);
        }

        ArrayList<LookupCodeValue> list = new ArrayList<LookupCodeValue>();

        List<?> rawRows = query.getResultList();
        for (Object rawRow : rawRows) {
            Object[] row = (rawRow instanceof Object[]) ? (Object[]) rawRow : new Object[]{ rawRow };
            LookupCodeValue lv = new LookupCodeValue();
            lv.setPrefix(tableId);
            lv.setCode(asString(row[0]));
            lv.setDescription(asString(row[1]));
            // Misc.getString(ResultSet,...) used by the pre-JPA code returned "" for SQL NULL,
            // yielding "00" → 0. asString() preserves null, which would produce "0null" → NFE,
            // so route these flag columns through a null-safe helper instead.
            lv.setActive(parseIntWithZeroPrefix(row[2]) == 1);
            lv.setOrderByIndex(parseIntWithZeroPrefix(row[3]));
            lv.setParentCode(asString(row[4]));
            lv.setBuf1(asString(row[5]));
            lv.setCodeTree(asString(row[6]));
            lv.setLastUpdateUser(asString(row[7]));
            lv.setLastUpdateDate(MyDateFormat.getCalendar(asString(row[8])));
            lv.setBuf3(asString(row[9]));
            lv.setBuf4(asString(row[10]));
            lv.setBuf5(asString(row[11]));
            lv.setBuf6(asString(row[12]));
            lv.setBuf7(asString(row[13]));
            lv.setBuf8(asString(row[14]));
            lv.setBuf9(asString(row[15]));
            lv.setCodecsv(asString(row[16]));
            list.add(lv);
        }
        // filter by programId for user
        if ("USR".equals(tableId) && !Utility.IsEmpty(pCd)) {
            Integer programId = null;
            try {
                programId = Integer.valueOf(pCd);
            } catch (NumberFormatException e) {
                // Ignore invalid programId format and keep the unfiltered list.
            }
            if (programId != null) {
                List userLst = providerDao.getActiveProviders(programId);
                ArrayList<LookupCodeValue> newLst = new ArrayList<LookupCodeValue>();
                for (int n = 0; n < userLst.size(); n++) {
                    SecProvider sp = (SecProvider) userLst.get(n);
                    for (int m = 0; m < list.size(); m++) {
                        LookupCodeValue lv = list.get(m);
                        if (lv.getCode().equals(sp.getProviderNo()))
                            newLst.add(lv);
                    }
                }
                list = newLst;
            }
        }
        return list;
    }

    @Override
    public LookupTableDefValue GetLookupTableDef(String tableId) {
        String sSQL = "from LookupTableDefValue s where s.tableId= ?1";
        List<?> results = JpqlQueryHelper.find(entityManager(), sSQL, tableId);
        if (results.isEmpty()) {
            return null;
        }
        return (LookupTableDefValue) results.get(0);
    }

    @Override
    public List LoadFieldDefList(String tableId) {
        String sSql = "from FieldDefValue s where s.tableId=?1 order by s.fieldIndex ";
        ArrayList<String> paramList = new ArrayList<String>();
        paramList.add(tableId);
        Object params[] = paramList.toArray(new Object[paramList.size()]);

        return JpqlQueryHelper.find(entityManager(), sSql, params);
    }

    /**
     * Validates that a SQL identifier (table or column name) contains only safe characters.
     * Allows dotted identifiers (e.g. {@code table.column}).
     *
     * <p>Note: {@code LookupCodeEdit2Action} also validates {@code tableId} with a stricter
     * {@code ^[A-Z0-9_]+$} regex before it reaches this DAO. This method provides a
     * second layer of defense for all identifier usage within the DAO.</p>
     */
    private String validateSqlIdentifier(String identifier) {
        if (!SqlIdentifierValidator.isValidIdentifier(identifier)) {
            MiscUtils.getLogger().error("Invalid SQL identifier rejected in lookup configuration");
            throw new IllegalArgumentException("Invalid SQL identifier in lookup configuration");
        }
        return identifier;
    }

    private String validateSqlAlias(String alias) {
        if (!SqlIdentifierValidator.isValidIdentifier(alias) || alias.indexOf('.') >= 0) {
            MiscUtils.getLogger().error("Invalid SQL alias rejected in lookup configuration");
            throw new IllegalArgumentException("Invalid SQL alias in lookup configuration");
        }
        return alias;
    }

    /**
     * Returns the unqualified column name for a {@code LoadCodeList} field.
     *
     * @param fieldSql String the validated simple column or {@code s.column} reference
     * @return String the simple column name used by later WHERE/tree predicates
     * @throws IllegalArgumentException when the field is blank or uses an unexpected qualifier or nested path
     */
    private String validateLoadCodeListFieldName(String fieldSql) {
        requireConfiguredLoadCodeListField(fieldSql);
        int dotIndex = fieldSql.indexOf('.');
        if (dotIndex < 0) {
            return validateSqlAlias(fieldSql);
        }
        String qualifier = fieldSql.substring(0, dotIndex);
        String columnName = fieldSql.substring(dotIndex + 1);
        boolean hasMultipleSegments = columnName.indexOf('.') >= 0;
        if (hasMultipleSegments) {
            MiscUtils.getLogger().error("Nested path not allowed in lookup field SQL");
            throw new IllegalArgumentException("Nested path not allowed in lookup field SQL");
        }
        if (!"s".equals(qualifier)) {
            MiscUtils.getLogger().error("Only s qualifier allowed in lookup field SQL");
            throw new IllegalArgumentException("Only s qualifier allowed in lookup field SQL");
        }
        // LoadCodeList owns the "s" table alias; other qualifiers would escape that fixed FROM shape.
        return validateSqlAlias(columnName);
    }

    /**
     * Adds the {@code LoadCodeList} table alias when field metadata stores a bare column name.
     *
     * @param fieldSql String the validated simple column or {@code s.column} reference
     * @return String the field reference to emit in the SELECT list
     */
    private String qualifyLoadCodeListField(String fieldSql) {
        requireConfiguredLoadCodeListField(fieldSql);
        if (fieldSql.indexOf('.') >= 0) {
            return fieldSql;
        }
        return "s." + fieldSql;
    }

    /**
     * Rejects missing field metadata before the legacy SQL builder inspects it.
     *
     * @param fieldSql String the validated simple column or {@code s.column} reference
     * @throws IllegalArgumentException when the field metadata is missing or empty
     */
    private void requireConfiguredLoadCodeListField(String fieldSql) {
        if (fieldSql == null || fieldSql.isEmpty()) {
            MiscUtils.getLogger().error("Blank SQL field rejected in lookup configuration");
            throw new IllegalArgumentException("Blank SQL field in lookup configuration");
        }
    }

    /**
     * Validates a field SQL expression from {@code FieldDefValue.getFieldSQL()}.
     * Allows simple identifiers, dotted identifiers, and SQL function expressions
     * like {@code IFNULL(buf1,'')} that are stored in the {@code lookupfielddef.fieldsql}
     * column (varchar 32). Only alphanumeric characters, underscores, dots, parentheses,
     * single-quoted string literals, commas, and whitespace are permitted.
     */
    private String validateFieldSql(String fieldSql) {
        if (!SqlIdentifierValidator.isValidFieldExpression(fieldSql)) {
            MiscUtils.getLogger().error("Invalid field SQL expression rejected in lookup configuration");
            throw new IllegalArgumentException("Invalid field SQL expression in lookup configuration");
        }
        return fieldSql;
    }

    @Override
    public List GetCodeFieldValues(LookupTableDefValue tableDef, String code) {
        // tableName and field SQL come from LookupTableDef/FieldDefValue database config,
        // not from direct user input, so second-order injection risk is low.  The user-supplied
        // code value is parameterized below.
        String tableName = validateSqlIdentifier(tableDef.getTableName());
        List fs = LoadFieldDefList(tableDef.getTableId());
        if (fs.isEmpty()) return fs;
        String idFieldName = "";

        String sql = "select ";
        for (int i = 0; i < fs.size(); i++) {
            FieldDefValue fdv = (FieldDefValue) fs.get(i);
            String fieldSql = validateFieldSql(fdv.getFieldSQL());
            if (fdv.getGenericIdx() == 1)
                idFieldName = fieldSql;
            if (i == 0) {
                sql += fieldSql;
            } else {
                sql += "," + fieldSql;
            }
        }
        sql += " from " + tableName + " s";
        // Use a parameterized placeholder for the code value to prevent SQL injection.
        String whereClause = " where " + validateSqlIdentifier(idFieldName) + " = :code";
        Query query = entityManager().createNativeQuery(sql + whereClause); // nosemgrep: hibernate-sqli -- table/column names validated via validateSqlIdentifier/validateFieldSql, code value bound as :code
        query.setParameter("code", code);

        List<?> rows = query.getResultList();
        if (!rows.isEmpty()) {
            Object[] row = extractFirstRow(rows, fs.size());
            for (int i = 0; i < fs.size(); i++) {
                FieldDefValue fdv = (FieldDefValue) fs.get(i);
                String val = asString(row[i]);
                if ("D".equals(fdv.getFieldType()))
                    if (fdv.isEditable()) {
                        val = MyDateFormat.getStandardDate(MyDateFormat.getCalendarwithTime(val));
                    } else {
                        val = MyDateFormat.getStandardDateTime(MyDateFormat.getCalendarwithTime(val));
                    }
                fdv.setVal(val);
            }
        }
        for (int i = 0; i < fs.size(); i++) {
            FieldDefValue fdv = (FieldDefValue) fs.get(i);
            if (!Utility.IsEmpty(fdv.getLookupTable())) {
                LookupCodeValue lkv = GetCode(fdv.getLookupTable(), fdv.getVal());
                if (lkv != null)
                    fdv.setValDesc(lkv.getDescription());
            }
        }
        return fs;
    }

    @Override
    public List<List> GetCodeFieldValues(LookupTableDefValue tableDef) {
        String tableName = validateSqlIdentifier(tableDef.getTableName());
        List<FieldDefValue> fieldDefs = LoadFieldDefList(tableDef.getTableId());
        if (fieldDefs.isEmpty()) return new ArrayList<List>();
        ArrayList<List> codes = new ArrayList<List>();
        String sql = "select ";
        for (int i = 0; i < fieldDefs.size(); i++) {
            FieldDefValue fdv = fieldDefs.get(i);
            String fieldSql = validateFieldSql(fdv.getFieldSQL());
            if (i == 0) {
                sql += fieldSql;
            } else {
                sql += "," + fieldSql;
            }
        }
        sql += " from " + tableName;
        Query query = entityManager().createNativeQuery(sql); // nosemgrep: hibernate-sqli -- table/column names validated via validateSqlIdentifier/validateFieldSql
        List<?> rows = query.getResultList();
        for (Object rawRow : rows) {
            Object[] row = (rawRow instanceof Object[]) ? (Object[]) rawRow : new Object[]{ rawRow };
            List<FieldDefValue> rowFields = new ArrayList<>(fieldDefs.size());
            for (int i = 0; i < fieldDefs.size(); i++) {
                FieldDefValue fdv = copyFieldDefValue(fieldDefs.get(i));
                String val = asString(row[i]);
                if ("D".equals(fdv.getFieldType()))
                    val = MyDateFormat.getStandardDateTime(MyDateFormat.getCalendarwithTime(val));
                fdv.setVal(val);
                if (!Utility.IsEmpty(fdv.getLookupTable())) {
                    LookupCodeValue lkv = GetCode(fdv.getLookupTable(), val);
                    if (lkv != null)
                        fdv.setValDesc(lkv.getDescription());
                }
                rowFields.add(fdv);
            }
            codes.add(rowFields);
        }
        return codes;
    }

    /**
     * Creates a defensive copy of a field definition template before row-specific
     * values are applied.
     *
     * <p>{@link #GetCodeFieldValues(LookupTableDefValue)} returns one
     * {@link FieldDefValue} list per result row. Reusing the same
     * {@code FieldDefValue} instances across iterations causes aliasing, so every
     * returned row ends up reflecting the last processed values. Copying the
     * template metadata here lets each row keep its own {@code val}/{@code valDesc}
     * state.</p>
     *
     * @param source FieldDefValue the template field definition to copy
     * @return FieldDefValue a detached copy safe to mutate for one result row
     * @since 2026-04-17
     */
    private static FieldDefValue copyFieldDefValue(FieldDefValue source) {
        FieldDefValue copy = new FieldDefValue();
        copy.setTableId(source.getTableId());
        copy.setFieldName(source.getFieldName());
        copy.setFieldDesc(source.getFieldDesc());
        copy.setFieldType(source.getFieldType());
        copy.setLookupTable(source.getLookupTable());
        copy.setFieldSQL(source.getFieldSQL());
        copy.setEditable(source.isEditable());
        copy.setAuto(source.isAuto());
        copy.setUnique(source.isUnique());
        copy.setGenericIdx(source.getGenericIdx());
        copy.setFieldIndex(source.getFieldIndex());
        copy.setFieldLength(source.getFieldLength());
        return copy;
    }

    private int GetNextId(String idFieldName, String tableName) { // identifiers validated below
        validateSqlIdentifier(idFieldName);
        validateSqlIdentifier(tableName);
        String maxSql = buildSelectMax(idFieldName, tableName);

        Query query = entityManager().createNativeQuery(maxSql); // nosemgrep: hibernate-sqli -- idFieldName and tableName validated via validateSqlIdentifier
        Object result = query.getSingleResult();
        int id = result == null ? 0 : ((Number) result).intValue();
        return id + 1;
    }

    private static String buildSelectMax(String idFieldName, String tableName) {
        return String.join("", "select max(", idFieldName, ") from ", tableName);
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String SaveCodeValue(boolean isNew, LookupTableDefValue tableDef, List fieldDefList) throws SQLException {
        String id = "";
        if (isNew) {
            id = InsertCodeValue(tableDef, fieldDefList);
        } else {
            id = UpdateCodeValue(tableDef, fieldDefList);
        }
        String tableId = tableDef.getTableId();
        if ("OGN,SHL".indexOf(tableId) >= 0) {
            SaveAsOrgCode(GetCode(tableId, id), tableId);
        }
        if ("PRP".equals(tableId)) {
            CarlosProperties prp = CarlosProperties.getInstance();
            LookupCodeValue prpCd = GetCode(tableId, id);
            if (prp.getProperty(prpCd.getDescription()) != null)
                prp.remove(prpCd.getDescription());
            prp.setProperty(prpCd.getDescription(), prpCd.getBuf1().toLowerCase());
        }
        return id;
    }

    @Override
    public String SaveCodeValue(boolean isNew, LookupCodeValue codeValue) throws SQLException {
        String tableId = codeValue.getPrefix();
        LookupTableDefValue tableDef = GetLookupTableDef(tableId);
        List fieldDefList = this.LoadFieldDefList(tableId);
        for (int i = 0; i < fieldDefList.size(); i++) {
            FieldDefValue fdv = (FieldDefValue) fieldDefList.get(i);

            switch (fdv.getGenericIdx()) {
                case 1:
                    fdv.setVal(codeValue.getCode());
                    break;
                case 2:
                    fdv.setVal(codeValue.getDescription());
                    break;
                case 3:
                    fdv.setVal(codeValue.isActive() ? "1" : "0");
                    break;
                case 4:
                    fdv.setVal(String.valueOf(codeValue.getOrderByIndex()));
                    break;
                case 5:
                    fdv.setVal(codeValue.getParentCode());
                    break;
                case 6:
                    fdv.setVal(codeValue.getBuf1());
                    break;
                case 7:
                    fdv.setVal(codeValue.getCodeTree());
                    break;
                case 8:
                    fdv.setVal(codeValue.getLastUpdateUser());
                    break;
                case 9:
                    fdv.setVal(MyDateFormat.getStandardDateTime(codeValue.getLastUpdateDate()));
                    break;
                case 10:
                    fdv.setVal(codeValue.getBuf3());
                    break;
                case 11:
                    fdv.setVal(codeValue.getBuf4());
                    break;
                case 12:
                    fdv.setVal(codeValue.getBuf5());
                    break;
                case 13:
                    fdv.setVal(codeValue.getBuf6());
                    break;
                case 14:
                    fdv.setVal(codeValue.getBuf7());
                    break;
                case 15:
                    fdv.setVal(codeValue.getBuf8());
                    break;
                case 16:
                    fdv.setVal(codeValue.getBuf9());
                    break;
                case 17:
                    fdv.setVal(codeValue.getCodecsv());
                    break;
                default:
                    break;
            }
        }
        if (isNew) {
            return InsertCodeValue(tableDef, fieldDefList);
        } else {
            return UpdateCodeValue(tableDef, fieldDefList);
        }
    }

    private String InsertCodeValue(LookupTableDefValue tableDef, List fieldDefList) throws SQLException {
        String tableName = validateSqlIdentifier(tableDef.getTableName());
        String idFieldVal = "";

        DBPreparedHandlerParam[] params = new DBPreparedHandlerParam[fieldDefList.size()];
        String phs = "";
        String sql = "insert into  " + tableName + "(";
        for (int i = 0; i < fieldDefList.size(); i++) {
            FieldDefValue fdv = (FieldDefValue) fieldDefList.get(i);
            sql += validateSqlIdentifier(fdv.getFieldSQL()) + ",";
            phs += "?,";
            if (fdv.getGenericIdx() == 1) {
                if (fdv.isAuto()) {
                    idFieldVal = String.valueOf(GetNextId(fdv.getFieldSQL(), tableName));
                    fdv.setVal(idFieldVal);
                } else {
                    idFieldVal = fdv.getVal();
                }
            }
            if ("S".equals(fdv.getFieldType())) {
                params[i] = new DBPreparedHandlerParam(fdv.getVal());
            } else if ("D".equals(fdv.getFieldType())) {
                // for last update date Using calendar Instance
                params[i] = new DBPreparedHandlerParam(
                        new java.sql.Date(MyDateFormat.getCalendarwithTime(fdv.getVal()).getTime().getTime()));
            } else {
                params[i] = new DBPreparedHandlerParam(Integer.valueOf(fdv.getVal()).intValue());
            }
        }
        sql = sql.substring(0, sql.length() - 1);
        phs = phs.substring(0, phs.length() - 1);
        sql += ") values (" + phs + ")";

        // check the existence of the code
        LookupCodeValue lkv = GetCode(tableDef.getTableId(), idFieldVal);
        if (lkv != null) {
            throw new SQLException("The Code Already Exists.");
        }

        queryExecuteUpdate(sql, params);

        return idFieldVal;
    }

    private String UpdateCodeValue(LookupTableDefValue tableDef, List fieldDefList) throws SQLException {
        String tableName = validateSqlIdentifier(tableDef.getTableName());
        String idFieldName = "";
        String idFieldVal = "";

        DBPreparedHandlerParam[] params = new DBPreparedHandlerParam[fieldDefList.size() + 1];
        String sql = "update " + tableName + " set ";
        for (int i = 0; i < fieldDefList.size(); i++) {
            FieldDefValue fdv = (FieldDefValue) fieldDefList.get(i);
            String fieldSql = validateSqlIdentifier(fdv.getFieldSQL());
            if (fdv.getGenericIdx() == 1) {
                idFieldName = fieldSql;
                idFieldVal = fdv.getVal();
            }

            sql += fieldSql + "=?,";
            if ("S".equals(fdv.getFieldType())) {
                params[i] = new DBPreparedHandlerParam(fdv.getVal());
            } else if ("D".equals(fdv.getFieldType())) {
                if (fdv.isEditable()) {
                    params[i] = new DBPreparedHandlerParam(
                            new java.sql.Date(MyDateFormat.getCalendar(fdv.getVal()).getTime().getTime()));
                } else {
                    params[i] = new DBPreparedHandlerParam(
                            new java.sql.Date(MyDateFormat.getCalendarwithTime(fdv.getVal()).getTime().getTime()));
                }
            } else {
                params[i] = new DBPreparedHandlerParam(Integer.valueOf(fdv.getVal()).intValue());
            }
        }
        sql = sql.substring(0, sql.length() - 1);
        sql += " where " + idFieldName + "=?";
        params[fieldDefList.size()] = params[0];

        queryExecuteUpdate(sql, params);

        return idFieldVal;
    }

    @Override
    public void SaveAsOrgCode(Program program) throws SQLException {

        String programId = "0000000" + program.getId().toString();
        programId = "P" + programId.substring(programId.length() - 7);
        String fullCode = "P" + program.getId();

        int facilityIdValue = program.getFacilityId() != null ? program.getFacilityId() : 0;
        String facilityId = "0000000" + facilityIdValue;
        facilityId = "F" + facilityId.substring(facilityId.length() - 7);

        LookupCodeValue fcd = GetCode("ORG", "F" + facilityIdValue);
        if (fcd == null) {
            log.warn("SaveAsOrgCode: no ORG entry for facility F{}; skipping program {} ({})", facilityIdValue, program.getId(), program.getName());
            return;
        }
        fullCode = fcd.getBuf1() + fullCode;

        boolean isNew = false;
        LookupCodeValue pcd = GetCode("ORG", "P" + program.getId());
        if (pcd == null) {
            isNew = true;
            pcd = new LookupCodeValue();
        }
        pcd.setPrefix("ORG");
        pcd.setCode("P" + program.getId());
        pcd.setCodeTree(fcd.getCodeTree() + programId);
        pcd.setCodecsv(fcd.getCodecsv() + "P" + program.getId() + ",");
        pcd.setDescription(program.getName());
        pcd.setBuf1(fullCode);
        pcd.setActive(program.isActive());
        pcd.setOrderByIndex(0);
        pcd.setLastUpdateDate(Calendar.getInstance());
        pcd.setLastUpdateUser(program.getLastUpdateUser());
        if (!isNew) {
            this.updateOrgTree(pcd.getCode(), pcd);
            this.updateOrgStatus(pcd.getCode(), pcd);
        }
        this.SaveCodeValue(isNew, pcd);
    }

    private void updateOrgTree(String orgCd, LookupCodeValue newCd) {
        LookupCodeValue oldCd = GetCode("ORG", orgCd);
        if (!oldCd.getCodecsv().equals(newCd.getCodecsv())) {
            String oldFullCode = oldCd.getBuf1();
            String oldTreeCode = oldCd.getCodeTree();
            String oldCsv = oldCd.getCodecsv();

            String newFullCode = newCd.getBuf1();
            String newTreeCode = newCd.getCodeTree();
            String newCsv = newCd.getCodecsv();

            String sql = "update lst_orgcd set fullcode = replace(fullcode, :oldFullCode, :newFullCode), "
                    + "codetree = replace(codetree, :oldTreeCode, :newTreeCode), "
                    + "codecsv = replace(codecsv, :oldCsv, :newCsv) "
                    + "where codecsv like :oldCsvPattern";

            Query updateOrgTreeQuery = entityManager().createNativeQuery(sql);
            updateOrgTreeQuery.setParameter("oldFullCode", oldFullCode);
            updateOrgTreeQuery.setParameter("newFullCode", newFullCode);
            updateOrgTreeQuery.setParameter("oldTreeCode", oldTreeCode);
            updateOrgTreeQuery.setParameter("newTreeCode", newTreeCode);
            updateOrgTreeQuery.setParameter("oldCsv", oldCsv);
            updateOrgTreeQuery.setParameter("newCsv", newCsv);
            updateOrgTreeQuery.setParameter("oldCsvPattern", oldCsv + "_%");
            updateOrgTreeQuery.executeUpdate();

        }

    }

    private void updateOrgStatus(String orgCd, LookupCodeValue newCd) {
        LookupCodeValue oldCd = GetCode("ORG", orgCd);
        if (!newCd.isActive()) {
            String oldCsv = oldCd.getCodecsv() + "_%";

            List<LstOrgcd> o = (List<LstOrgcd>) JpqlQueryHelper.find(entityManager(),
                    "FROM LstOrgcd o WHERE o.codecsv like ?1", oldCsv);
            for (LstOrgcd l : o) {
                l.setActiveyn(0);
                entityManager().merge(l);
            }
        }
    }

    @Override
    public boolean inOrg(String org1, String org2) {
        boolean isInString = false;
        String sql = "From LstOrgcd a where a.fullcode like ?1";

        // Wildcard must be part of the parameter value, not the HQL query
        List<LstOrgcd> results1 = (List<LstOrgcd>) JpqlQueryHelper.find(entityManager(), sql, "%" + org1);
        List<LstOrgcd> results2 = (List<LstOrgcd>) JpqlQueryHelper.find(entityManager(), sql, "%" + org2);

        if (!results1.isEmpty() && !results2.isEmpty()) {
            LstOrgcd orgObj1 = results1.get(0);
            LstOrgcd orgObj2 = results2.get(0);
            if (orgObj2.getFullcode().indexOf(orgObj1.getFullcode()) >= 0)
                isInString = true;
        }
        return isInString;

    }

    @Override
    public void SaveAsOrgCode(Facility facility) throws SQLException {

        String facilityId = "0000000" + facility.getId().toString();
        facilityId = "F" + facilityId.substring(facilityId.length() - 7);
        String fullCode = "F" + facility.getId();

        String orgId = "0000000" + String.valueOf(facility.getOrgId());
        orgId = "S" + orgId.substring(orgId.length() - 7);

        LookupCodeValue ocd = GetCode("ORG", "S" + facility.getOrgId());
        fullCode = ocd.getBuf1() + fullCode;

        boolean isNew = false;
        LookupCodeValue fcd = GetCode("ORG", "F" + facility.getId());
        if (fcd == null) {
            isNew = true;
            fcd = new LookupCodeValue();
        }
        fcd.setPrefix("ORG");
        fcd.setCode("F" + facility.getId());
        fcd.setCodeTree(ocd.getCodeTree() + facilityId);
        fcd.setCodecsv(ocd.getCodecsv() + "F" + facility.getId() + ",");
        fcd.setDescription(facility.getName());
        fcd.setBuf1(fullCode);
        fcd.setActive(!facility.isDisabled());
        fcd.setOrderByIndex(0);
        fcd.setLastUpdateDate(Calendar.getInstance());
        // fcd.setLastUpdateUser(facility.getLastUpdateUser());
        if (!isNew) {
            this.updateOrgTree(fcd.getCode(), fcd);
            this.updateOrgStatus(fcd.getCode(), fcd);
        }
        this.SaveCodeValue(isNew, fcd);
    }

    @Override
    public void SaveAsOrgCode(LookupCodeValue orgVal, String tableId) throws SQLException {

        String orgPrefix = tableId.substring(0, 1);
        String orgPrefixP = "R1";
        if ("S".equals(orgPrefix))
            orgPrefixP = "O"; // parent of Organization is R, parent of Shelter is O.

        String orgId = "0000000" + orgVal.getCode();
        orgId = orgPrefix + orgId.substring(orgId.length() - 7);

        String orgCd = orgPrefix + orgVal.getCode();
        String parentCd = orgPrefixP + orgVal.getParentCode();

        LookupCodeValue pCd = GetCode("ORG", parentCd);
        if (pCd == null)
            return;

        LookupCodeValue ocd = GetCode("ORG", orgCd);
        boolean isNew = false;
        if (ocd == null) {
            isNew = true;
            ocd = new LookupCodeValue();
        }
        ocd.setPrefix("ORG");
        ocd.setCode(orgCd);
        ocd.setCodeTree(pCd.getCodeTree() + orgId);
        ocd.setCodecsv(pCd.getCodecsv() + orgCd + ",");
        ocd.setDescription(orgVal.getDescription());
        ocd.setBuf1(pCd.getBuf1() + orgCd);
        ocd.setActive(orgVal.isActive());
        ocd.setOrderByIndex(0);
        ocd.setLastUpdateDate(Calendar.getInstance());
        ocd.setLastUpdateUser(orgVal.getLastUpdateUser());
        if (!isNew) {
            this.updateOrgTree(ocd.getCode(), ocd);
            this.updateOrgStatus(ocd.getCode(), ocd);
        }
        this.SaveCodeValue(isNew, ocd);
    }

    @Override
    public void runProcedure(String procName, String[] params) throws SQLException {
        LegacyJdbcQuery.procExecute(procName, params);
    }

    @Override
    public int getCountOfActiveClient(String orgCd) throws SQLException {
        // Parameterized queries eliminate the SQL injection risk from orgCd.
        // CONCAT() is the correct MySQL/MariaDB string concatenation function;
        // the original code used '||' which is Oracle-style and acts as logical OR in MySQL.
        //
        // The LIKE expression uses an explicit {@code ESCAPE '\'} clause so that the
        // backslash-escaping of '%' / '_' below is honoured regardless of the target
        // dialect's default escape character or SQL mode (e.g. MySQL
        // {@code NO_BACKSLASH_ESCAPES}). Without the explicit clause, dialects that
        // don't default to backslash would treat the escape characters as literals.
        String sql = "select count(*) from admission where admission_status = :status and CONCAT('P', program_id) in ("
                + " select code from lst_orgcd where codecsv like :pattern escape '\\')";
        String sql1 = "select count(*) from program_queue where CONCAT('P', program_id) in ("
                + " select code from lst_orgcd where codecsv like :pattern escape '\\')";

        // Escape LIKE special characters in orgCd to prevent unexpected wildcard expansion.
        // Must stay in sync with the {@code ESCAPE '\'} clause in the SQL above.
        String escapedOrgCd = orgCd.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        String likePattern = "%" + escapedOrgCd + ",%";

        Number count = (Number) entityManager().createNativeQuery(sql)
                .setParameter("status", KeyConstants.INTAKE_STATUS_ADMITTED)
                .setParameter("pattern", likePattern)
                .getSingleResult();
        if (count != null && count.intValue() > 0) {
            return count.intValue();
        }

        Number count1 = (Number) entityManager().createNativeQuery(sql1)
                .setParameter("pattern", likePattern)
                .getSingleResult();
        return count1 != null ? count1.intValue() : 0;
    }

    @Override
    public void setProviderDao(ProviderDao providerDao) {
        this.providerDao = providerDao;
    }

    private int queryExecuteUpdate(String preparedSQL, DBPreparedHandlerParam[] params) {
        Query query = entityManager().createNativeQuery(preparedSQL); // nosemgrep: hibernate-sqli -- identifiers in preparedSQL validated by validateSqlIdentifier at call sites; values bound below
        for (int i = 0; i < params.length; i++) {
            bindParam(query, i + 1, params[i]);
        }
        return query.executeUpdate();
    }

    /**
     * Binds a {@link DBPreparedHandlerParam} typed value to a JPA {@link Query} at a
     * 1-based position, preserving the type-aware dispatch the legacy
     * prepared-query binding performed (String / int / Date / Timestamp).
     *
     * @param query the JPA query to bind on
     * @param position 1-based positional parameter index
     * @param param the typed parameter (may be null)
     */
    private static void bindParam(Query query, int position, DBPreparedHandlerParam param) {
        NativeQuery<?> nativeQuery = query.unwrap(NativeQuery.class);
        if (param == null) {
            // Explicit type hint avoids Hibernate "could not determine type" errors
            // when binding a bare null to a native-query positional parameter.
            nativeQuery.setParameter(position, (String) null, StandardBasicTypes.STRING);
        } else if (DBPreparedHandlerParam.PARAM_STRING.equals(param.getParamType())) {
            nativeQuery.setParameter(position, param.getStringValue(), StandardBasicTypes.STRING);
        } else if (DBPreparedHandlerParam.PARAM_DATE.equals(param.getParamType())) {
            nativeQuery.setParameter(position, param.getDateValue(), StandardBasicTypes.DATE);
        } else if (DBPreparedHandlerParam.PARAM_INT.equals(param.getParamType())) {
            nativeQuery.setParameter(position, param.getIntValue(), StandardBasicTypes.INTEGER);
        } else if (DBPreparedHandlerParam.PARAM_TIMESTAMP.equals(param.getParamType())) {
            nativeQuery.setParameter(position, param.getTimestampValue(), StandardBasicTypes.TIMESTAMP);
        }
    }

    /**
     * Null-safe conversion of a query result column to a String, matching the legacy
     * {@code Misc.getString(...)} behaviour that coerced SQL NULL to the empty string.
     */
    private static String asString(Object value) {
        return StringUtils.defaultString(value == null ? null : value.toString());
    }

    /**
     * Parses a numeric-flag column into an int, matching the pre-JPA behaviour of
     * {@code Integer.valueOf("0" + Misc.getString(rs, col)).intValue()} where
     * {@code Misc.getString} coerced SQL NULL to an empty string (giving "00" → 0).
     * A plain {@link #asString(Object)} call returns {@code null} for NULL, which would
     * concatenate to {@code "0null"} and throw {@link NumberFormatException}.
     */
    private static int parseIntWithZeroPrefix(Object value) {
        String s = value == null ? "" : value.toString();
        return Integer.parseInt("0" + s);
    }

    /**
     * Returns the first row from a native-query result list, normalizing to Object[] when
     * the underlying query returns scalar values for a single-column SELECT.
     */
    private static Object[] extractFirstRow(List<?> rows, int expectedColumns) {
        Object first = rows.get(0);
        if (first instanceof Object[]) {
            return (Object[]) first;
        }
        // Single-column native queries return scalars, not Object[]
        Object[] wrapped = new Object[expectedColumns];
        wrapped[0] = first;
        return wrapped;
    }

}
