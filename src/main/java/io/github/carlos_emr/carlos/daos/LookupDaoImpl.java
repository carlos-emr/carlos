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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import io.github.carlos_emr.Misc;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.utility.DbConnectionFilter;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.dao.AbstractHibernateDao;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.db.DBPreparedHandler;
import io.github.carlos_emr.carlos.db.DBPreparedHandlerParam;

import io.github.carlos_emr.carlos.commons.KeyConstants;
import io.github.carlos_emr.carlos.model.FieldDefValue;
import io.github.carlos_emr.carlos.model.LookupCodeValue;
import io.github.carlos_emr.carlos.model.LookupTableDefValue;
import io.github.carlos_emr.carlos.model.LstOrgcd;
import io.github.carlos_emr.carlos.model.security.SecProvider;
import io.github.carlos_emr.carlos.utils.Utility;
import io.github.carlos_emr.carlos.utility.HqlQueryHelper;

@Transactional
public class LookupDaoImpl extends AbstractHibernateDao implements LookupDao {

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
        String sSQL = "select distinct ";
        boolean activeFieldExists = true;
        for (int i = 1; i <= 17; i++) {
            boolean ok = false;
            for (int j = 0; j < fields.size(); j++) {
                FieldDefValue fdef = (FieldDefValue) fields.get(j);
                if (fdef.getGenericIdx() == i) {
                    if (fdef.getFieldSQL().indexOf('(') >= 0) {
                        sSQL += fdef.getFieldSQL() + " " + fdef.getFieldName() + ",";
                        fieldNames[i - 1] = fdef.getFieldName();
                    } else {
                        sSQL += "s." + fdef.getFieldSQL() + ",";
                        fieldNames[i - 1] = fdef.getFieldSQL();
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
        sSQL += " from " + tableDef.getTableName();
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
        DBPreparedHandlerParam[] pars = new DBPreparedHandlerParam[i];
        for (int j = 0; j < i; j++) {
            pars[j] = params[j];
        }

        DBPreparedHandler db = new DBPreparedHandler();
        ArrayList<LookupCodeValue> list = new ArrayList<LookupCodeValue>();

        try {
            ResultSet rs = db.queryResults(sSQL, pars);
            while (rs.next()) {
                LookupCodeValue lv = new LookupCodeValue();
                lv.setPrefix(tableId);
                lv.setCode(rs.getString(1));
                lv.setDescription(Misc.getString(rs, 2));
                lv.setActive(Integer.valueOf("0" + Misc.getString(rs, 3)).intValue() == 1);
                lv.setOrderByIndex(Integer.valueOf("0" + Misc.getString(rs, 4)).intValue());
                lv.setParentCode(Misc.getString(rs, 5));
                lv.setBuf1(Misc.getString(rs, 6));
                lv.setCodeTree(Misc.getString(rs, 7));
                lv.setLastUpdateUser(Misc.getString(rs, 8));
                lv.setLastUpdateDate(MyDateFormat.getCalendar(Misc.getString(rs, 9)));
                lv.setBuf3(Misc.getString(rs, 10));
                lv.setBuf4(Misc.getString(rs, 11));
                lv.setBuf5(Misc.getString(rs, 12));
                lv.setBuf6(Misc.getString(rs, 13));
                lv.setBuf7(Misc.getString(rs, 14));
                lv.setBuf8(Misc.getString(rs, 15));
                lv.setBuf9(Misc.getString(rs, 16));
                lv.setCodecsv(Misc.getString(rs, 17));
                list.add(lv);
            }
            rs.close();
            // filter by programId for user
            if ("USR".equals(tableId) && !Utility.IsEmpty(pCd)) {
                List userLst = providerDao.getActiveProviders(Integer.valueOf(pCd));
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
        } catch (SQLException e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return list;
    }

    @Override
    public LookupTableDefValue GetLookupTableDef(String tableId) {
        String sSQL = "from LookupTableDefValue s where s.tableId= ?1";
        List<?> results = HqlQueryHelper.find(currentSession(), sSQL, tableId);
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

        return HqlQueryHelper.find(currentSession(), sSql, params);
    }

    @Override
    public List GetCodeFieldValues(LookupTableDefValue tableDef, String code) {
        String tableName = tableDef.getTableName();
        List fs = LoadFieldDefList(tableDef.getTableId());
        String idFieldName = "";

        String sql = "select ";
        for (int i = 0; i < fs.size(); i++) {
            FieldDefValue fdv = (FieldDefValue) fs.get(i);
            if (fdv.getGenericIdx() == 1)
                idFieldName = fdv.getFieldSQL();
            if (i == 0) {
                sql += fdv.getFieldSQL();
            } else {
                sql += "," + fdv.getFieldSQL();
            }
        }
        sql += " from " + tableName + " s";
        sql += " where " + idFieldName + "='" + code + "'";
        DBPreparedHandler db = new DBPreparedHandler();
        try {
            ResultSet rs = db.queryResults(sql);
            if (rs.next()) {
                for (int i = 0; i < fs.size(); i++) {
                    FieldDefValue fdv = (FieldDefValue) fs.get(i);
                    String val = Misc.getString(rs, (i + 1));
                    if ("D".equals(fdv.getFieldType()))
                        if (fdv.isEditable()) {
                            val = MyDateFormat.getStandardDate(MyDateFormat.getCalendarwithTime(val));
                        } else {
                            val = MyDateFormat.getStandardDateTime(MyDateFormat.getCalendarwithTime(val));
                        }
                    fdv.setVal(val);
                }
            }
            rs.close();
            for (int i = 0; i < fs.size(); i++) {
                FieldDefValue fdv = (FieldDefValue) fs.get(i);
                if (!Utility.IsEmpty(fdv.getLookupTable())) {
                    LookupCodeValue lkv = GetCode(fdv.getLookupTable(), fdv.getVal());
                    if (lkv != null)
                        fdv.setValDesc(lkv.getDescription());
                }
            }
        } catch (SQLException e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return fs;
    }

    @Override
    public List<List> GetCodeFieldValues(LookupTableDefValue tableDef) {
        String tableName = tableDef.getTableName();
        List fs = LoadFieldDefList(tableDef.getTableId());
        ArrayList<List> codes = new ArrayList<List>();
        String sql = "select ";
        for (int i = 0; i < fs.size(); i++) {
            FieldDefValue fdv = (FieldDefValue) fs.get(i);
            if (i == 0) {
                sql += fdv.getFieldSQL();
            } else {
                sql += "," + fdv.getFieldSQL();
            }
        }
        sql += " from " + tableName;
        DBPreparedHandler db = new DBPreparedHandler();
        try {
            ResultSet rs = db.queryResults(sql);
            while (rs.next()) {
                for (int i = 0; i < fs.size(); i++) {
                    FieldDefValue fdv = (FieldDefValue) fs.get(i);
                    String val = Misc.getString(rs, (i + 1));
                    if ("D".equals(fdv.getFieldType()))
                        val = MyDateFormat.getStandardDateTime(MyDateFormat.getCalendarwithTime(val));
                    fdv.setVal(val);
                    if (!Utility.IsEmpty(fdv.getLookupTable())) {
                        LookupCodeValue lkv = GetCode(fdv.getLookupTable(), val);
                        if (lkv != null)
                            fdv.setValDesc(lkv.getDescription());
                    }
                }
                codes.add(fs);
            }
            rs.close();
        } catch (SQLException e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return codes;
    }

    private int GetNextId(String idFieldName, String tableName) throws SQLException {
        String sql = "select max(" + idFieldName + ")";
        sql += " from " + tableName;
        DBPreparedHandler db = new DBPreparedHandler();

        ResultSet rs = db.queryResults(sql);
        int id = 0;
        if (rs.next())
            id = rs.getInt(1);
        return id + 1;
    }

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
            OscarProperties prp = OscarProperties.getInstance();
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
        String tableName = tableDef.getTableName();
        String idFieldVal = "";

        DBPreparedHandlerParam[] params = new DBPreparedHandlerParam[fieldDefList.size()];
        String phs = "";
        String sql = "insert into  " + tableName + "(";
        for (int i = 0; i < fieldDefList.size(); i++) {
            FieldDefValue fdv = (FieldDefValue) fieldDefList.get(i);
            sql += fdv.getFieldSQL() + ",";
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
        String tableName = tableDef.getTableName();
        String idFieldName = "";
        String idFieldVal = "";

        DBPreparedHandlerParam[] params = new DBPreparedHandlerParam[fieldDefList.size() + 1];
        String sql = "update " + tableName + " set ";
        for (int i = 0; i < fieldDefList.size(); i++) {
            FieldDefValue fdv = (FieldDefValue) fieldDefList.get(i);
            if (fdv.getGenericIdx() == 1) {
                idFieldName = fdv.getFieldSQL();
                idFieldVal = fdv.getVal();
            }

            sql += fdv.getFieldSQL() + "=?,";
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

        int facilityIdValue = program.getFacilityId();
        String facilityId = "0000000" + facilityIdValue;
        facilityId = "F" + facilityId.substring(facilityId.length() - 7);

        LookupCodeValue fcd = GetCode("ORG", "F" + facilityIdValue);
        if (fcd == null) {
            MiscUtils.getLogger().warn("SaveAsOrgCode: no ORG entry for facility F" + facilityIdValue + "; skipping program " + program.getId() + " (" + program.getName() + ")");
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

            currentSession().createNativeQuery(sql)
                    .setParameter("oldFullCode", oldFullCode)
                    .setParameter("newFullCode", newFullCode)
                    .setParameter("oldTreeCode", oldTreeCode)
                    .setParameter("newTreeCode", newTreeCode)
                    .setParameter("oldCsv", oldCsv)
                    .setParameter("newCsv", newCsv)
                    .setParameter("oldCsvPattern", oldCsv + "_%")
                    .executeUpdate();

        }

    }

    private void updateOrgStatus(String orgCd, LookupCodeValue newCd) {
        LookupCodeValue oldCd = GetCode("ORG", orgCd);
        if (!newCd.isActive()) {
            String oldCsv = oldCd.getCodecsv() + "_%";

            List<LstOrgcd> o = (List<LstOrgcd>) HqlQueryHelper.find(currentSession(),
                    "FROM LstOrgcd o WHERE o.codecsv like ?1", oldCsv);
            for (LstOrgcd l : o) {
                l.setActiveyn(0);
                currentSession().update(l);
            }
        }
    }

    @Override
    public boolean inOrg(String org1, String org2) {
        boolean isInString = false;
        String sql = "From LstOrgcd a where a.fullcode like ?1";

        // Wildcard must be part of the parameter value, not the HQL query
        List<LstOrgcd> results1 = (List<LstOrgcd>) HqlQueryHelper.find(currentSession(), sql, "%" + org1);
        List<LstOrgcd> results2 = (List<LstOrgcd>) HqlQueryHelper.find(currentSession(), sql, "%" + org2);

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
        DBPreparedHandler db = new DBPreparedHandler();
        db.procExecute(procName, params);
    }

    @Override
    public int getCountOfActiveClient(String orgCd) throws SQLException {
        String sql = "select count(*) from admission where admission_status='" + KeyConstants.INTAKE_STATUS_ADMITTED
                + "' and  'P' || program_id in (" + " select code from lst_orgcd  where codecsv like '%' || '" + orgCd
                + ",' || '%')";
        String sql1 = "select count(*) from program_queue where  'P' || program_id in ("
                + " select code from lst_orgcd  where codecsv like '%' || '" + orgCd + ",' || '%')";

        DBPreparedHandler db = new DBPreparedHandler();

        ResultSet rs = db.queryResults(sql);
        int id = 0;
        if (rs.next())
            id = rs.getInt(1);
        if (id > 0)
            return id;

        rs.close();
        rs = db.queryResults(sql1);
        if (rs.next())
            id = rs.getInt(1);
        rs.close();
        return id;
    }

    @Override
    public void setProviderDao(ProviderDao providerDao) {
        this.providerDao = providerDao;
    }

    private int queryExecuteUpdate(String preparedSQL, DBPreparedHandlerParam[] params) throws SQLException {
        PreparedStatement preparedStmt = DbConnectionFilter.getThreadLocalDbConnection().prepareStatement(preparedSQL);
        for (int i = 0; i < params.length; i++) {
            DBPreparedHandlerParam param = params[i];

            if (param == null)
                preparedStmt.setObject(i + 1, null);
            else if (DBPreparedHandlerParam.PARAM_STRING.equals(param.getParamType())) {
                preparedStmt.setString(i + 1, param.getStringValue());
            } else if (DBPreparedHandlerParam.PARAM_DATE.equals(param.getParamType())) {
                preparedStmt.setDate(i + 1, param.getDateValue());
            } else if (DBPreparedHandlerParam.PARAM_INT.equals(param.getParamType())) {
                preparedStmt.setInt(i + 1, param.getIntValue());
            } else if (DBPreparedHandlerParam.PARAM_TIMESTAMP.equals(param.getParamType())) {
                preparedStmt.setTimestamp(i + 1, param.getTimestampValue());
            }
        }
        return (preparedStmt.executeUpdate());
    }

}
