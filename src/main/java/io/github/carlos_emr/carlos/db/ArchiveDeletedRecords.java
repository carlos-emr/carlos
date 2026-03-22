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


package io.github.carlos_emr.carlos.db;

import java.util.List;

import org.jdom2.Document;
import org.jdom2.output.XMLOutputter;
import io.github.carlos_emr.carlos.commn.dao.TableModificationDao;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.commn.model.TableModification;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Archives deleted or updated database rows into the {@code table_modification} table
 * for audit trail and recovery purposes.
 *
 * <p>Before records are permanently deleted from the database, this class serializes
 * the row data to XML format and stores it in the modification archive. This supports
 * healthcare data retention requirements and provides a recovery mechanism for
 * accidentally deleted records.</p>
 *
 * <p>The archive stores the modification type (delete or update), the source table name,
 * the provider who performed the action, and the full row data as XML.</p>
 *
 * @since 2001-01-01
 * @see io.github.carlos_emr.carlos.commn.model.TableModification
 */
public class ArchiveDeletedRecords {
    static String DELETE = "delete";
    static String UPDATE = "update";

    /**
     * Creates a new instance of ArchiveDeletedRecords
     */
    public ArchiveDeletedRecords() {
    }

    private String getStringXmlFromResultSet(ProviderLabRoutingModel record) throws Exception {
        ResultSetBuilder builder = new ResultSetBuilder(record);
        Document doc = builder.build();
        XMLOutputter xml = new XMLOutputter();
        String xmlStr = xml.outputString(doc);
        return xmlStr;
    }

    /**
     * Archives a list of records that are about to be deleted from the specified table.
     *
     * <p>Each record is serialized to XML and stored in the modification archive table
     * with a "delete" modification type.</p>
     *
     * @param records List of ProviderLabRoutingModel records to archive before deletion
     * @param provNo String the provider number performing the deletion
     * @param table String the source database table name
     * @return int always returns 0
     */
    public int recordRowsToBeDeleted(List<ProviderLabRoutingModel> records, String provNo, String table) {
        try {
            for (ProviderLabRoutingModel record : records) {
                String xmlStr = getStringXmlFromResultSet(record);
                addRowsToModifiedTable(null, provNo, ArchiveDeletedRecords.DELETE, table, null, xmlStr);
            }

        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return 0;
    }

    private void addRowsToModifiedTable(String demoNo, String provNo, String modType, String table, String rowId, String resultSet) {
        TableModification tm = new TableModification();
        tm.setDemographicNo(demoNo != null ? Integer.parseInt(demoNo) : null);
        tm.setProviderNo(provNo);
        tm.setModificationType(modType);
        tm.setTableName(table);
        tm.setRowId(rowId);
        tm.setResultSet(resultSet);

        TableModificationDao dao = SpringUtils.getBean(TableModificationDao.class);
        dao.persist(tm);

        MiscUtils.getLogger().debug("Added rows to modified table: " + tm);
    }

}
