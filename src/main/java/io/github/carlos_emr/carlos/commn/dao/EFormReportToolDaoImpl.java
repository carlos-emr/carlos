/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Date;
import java.util.List;

import jakarta.persistence.Query;

import org.apache.commons.lang3.time.DateFormatUtils;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.commn.model.EFormReportTool;
import io.github.carlos_emr.carlos.commn.model.EFormValue;
import org.springframework.stereotype.Repository;

@Repository
public class EFormReportToolDaoImpl extends AbstractDaoImpl<EFormReportTool> implements EFormReportToolDao {

    public EFormReportToolDaoImpl() {
        super(EFormReportTool.class);
    }

    @SuppressWarnings("unchecked")
    public void markLatest(Integer eformReportToolId) {
        EFormReportTool eft = find(eformReportToolId);
        if (eft != null) {
            String safeTableName = eft.getTableName().replaceAll("[^a-zA-Z0-9_]", "");
            //get all distinct demographicNos
            Query q = entityManager.createNativeQuery("select distinct demographicNo from  " + safeTableName);
            List<Integer> demoNos = q.getResultList();
            for (Integer demoNo : demoNos) {
                Query q2 = entityManager.createNativeQuery("select id from " + safeTableName + " where demographicNo = ?1 order by dateFormCreated desc,fdid desc").setMaxResults(1);
                q2.setParameter(1, demoNo);
                List<Integer> idList = q2.getResultList();

                if (!idList.isEmpty()) {
                    //update the first result
                    Query q3 = entityManager.createNativeQuery("update " + safeTableName + " set eft_latest=1 where id= ?1");
                    q3.setParameter(1, idList.get(0));
                    q3.executeUpdate();
                }
            }

            eft.setLatestMarked(true);
            merge(eft);
        }
    }

    public void addNew(EFormReportTool eformReportTool, EForm eform, List<String> fields, String providerNo) {
        //generate the create table statement
        String cleanName = eformReportTool.getName() != null ? eformReportTool.getName().replaceAll("[^a-zA-Z0-9_]", "") : "TOOL";
        String tableName = "ERT_" + cleanName + (new BigInteger(130, new SecureRandom()).toString(8).substring(0, 8));
        StringBuilder sql = new StringBuilder("CREATE TABLE " + tableName + " (");
        sql.append("id int (10) NOT NULL auto_increment primary key,");
        sql.append("fdid int (10) NOT NULL, ");
        sql.append("demographicNo int (10) NOT NULL, ");
        sql.append("dateFormCreated datetime NOT NULL, ");
        sql.append("providerNo varchar(6) NOT NULL, ");
        sql.append("eft_latest tinyint(1) NOT NULL, ");
        sql.append("dateCreated timestamp NOT NULL ");
        for (String field : fields) {
            String cleanField = field.replaceAll("[^a-zA-Z0-9_]", "");
            if (!cleanField.isEmpty()) {
                sql.append(",`").append(cleanField).append("` text");
            }
        }
        sql.append(")");

        //logger.debug("sql=" + sql);

        //commit the table
        Query q = entityManager.createNativeQuery(sql.toString());
        q.executeUpdate();

        //save the EformReportTool
        eformReportTool.setDateLastPopulated(null);
        eformReportTool.setId(null);
        eformReportTool.setTableName(tableName);
        eformReportTool.setProviderNo(providerNo);
        eformReportTool.setLatestMarked(false);
        persist(eformReportTool);

    }

    public void populateReportTableItem(EFormReportTool eft, List<EFormValue> values, Integer fdid, Integer demographicNo, Date dateFormCreated, String providerNo) {
        //create an insert statement
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ");
        // Ensure table name is sanitized, as a precaution
        String safeTableName = eft.getTableName().replaceAll("[^a-zA-Z0-9_]", "");
        sb.append(safeTableName);
        sb.append(" (");
        sb.append("fdid,");
        sb.append("demographicNo,");
        sb.append("dateFormCreated,");
        sb.append("providerNo,");
        sb.append("eft_latest,");
        sb.append("dateCreated,");

        int paramIndex = 1;
        for (EFormValue v : values) {
            String cleanVarName = v.getVarName().replaceAll("[^a-zA-Z0-9_]", "");
            if (!cleanVarName.isEmpty()) {
                sb.append("`").append(cleanVarName).append("`");
                sb.append(",");
            }
        }

        sb.deleteCharAt(sb.length() - 1);

        sb.append(" ) VALUES (");
        sb.append("?").append(paramIndex++).append(","); // fdid
        sb.append("?").append(paramIndex++).append(","); // demographicNo
        sb.append("?").append(paramIndex++).append(","); // dateFormCreated
        sb.append("?").append(paramIndex++).append(","); // providerNo
        sb.append("0,");
        sb.append("now(),");

        for (EFormValue v : values) {
            String cleanVarName = v.getVarName().replaceAll("[^a-zA-Z0-9_]", "");
            if (!cleanVarName.isEmpty()) {
                sb.append("?").append(paramIndex++).append(",");
            }
        }
        sb.deleteCharAt(sb.length() - 1);

        sb.append(")");

        //logger.debug("sql=" + sb.toString());

        Query q = entityManager.createNativeQuery(sb.toString());

        int bindIndex = 1;
        q.setParameter(bindIndex++, fdid);
        q.setParameter(bindIndex++, demographicNo);
        q.setParameter(bindIndex++, DateFormatUtils.format(dateFormCreated, "yyyy-MM-dd HH:mm:ss"));
        q.setParameter(bindIndex++, providerNo);

        for (EFormValue v : values) {
            String cleanVarName = v.getVarName().replaceAll("[^a-zA-Z0-9_]", "");
            if (!cleanVarName.isEmpty()) {
                q.setParameter(bindIndex++, v.getVarValue());
            }
        }

        q.executeUpdate();
    }

    public void deleteAllData(EFormReportTool eft) {
        if (eft != null) {
            String safeTableName = eft.getTableName().replaceAll("[^a-zA-Z0-9_]", "");
            Query q = entityManager.createNativeQuery("delete from " + safeTableName);
            q.executeUpdate();
        }
    }

    public void drop(EFormReportTool eft) {
        if (eft != null) {
            String safeTableName = eft.getTableName().replaceAll("[^a-zA-Z0-9_]", "");
            Query q = entityManager.createNativeQuery("drop table " + safeTableName);
            q.executeUpdate();
        }
    }

    public Integer getNumRecords(EFormReportTool eformReportTool) {
        if (eformReportTool != null) {
            String safeTableName = eformReportTool.getTableName().replaceAll("[^a-zA-Z0-9_]", "");
            Query q = entityManager.createNativeQuery("select count(*) from " + safeTableName);
            List<BigInteger> results = q.getResultList();
            if (!results.isEmpty()) {
                return results.get(0).intValue();
            }
        }
        return null;
    }

}
