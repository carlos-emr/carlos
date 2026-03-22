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


/*
 * DemographicMergedDAO.java
 *
 * Created on September 14, 2007, 1:13 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.demographic.data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.DemographicMergedDao;
import io.github.carlos_emr.carlos.commn.dao.RecycleBinDao;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.RecycleBin;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilege;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilegePrimaryKey;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.log.LogAction;

/**
 * Manages the merging and unmerging of patient demographic records.
 *
 * <p>This class supports the clinical workflow of merging duplicate patient records
 * into a single "head" record, and reversing such merges when errors are identified.
 * Merged records form a chain where each merged demographic points to a head record,
 * and the chain is resolved recursively to find the ultimate head.</p>
 *
 * <p>Merge operations also create security object privileges to restrict access
 * to the merged record's eChart, and unmerge operations move those privileges
 * to the recycle bin for audit trail purposes.</p>
 *
 * <p>All merge and unmerge operations are logged via {@link LogAction} for
 * HIPAA/PIPEDA compliance.</p>
 *
 * @see io.github.carlos_emr.carlos.commn.dao.DemographicMergedDao
 * @see io.github.carlos_emr.carlos.commn.model.DemographicMerged
 * @since 2026-03-17
 */
public class DemographicMerged {

    Logger logger = MiscUtils.getLogger();
    private DemographicMergedDao dao = SpringUtils.getBean(DemographicMergedDao.class);
    private SecObjPrivilegeDao secObjPrivilegeDao = SpringUtils.getBean(SecObjPrivilegeDao.class);
    private RecycleBinDao recycleBinDao = SpringUtils.getBean(RecycleBinDao.class);

    /**
     * Constructs a new DemographicMerged instance with Spring-managed DAO dependencies.
     */
    public DemographicMerged() {
    }

    /**
     * Merges a demographic record into a head record.
     *
     * <p>If the source demographic is already merged to another head, that head is
     * re-merged to the new head instead. Creates an eChart security privilege
     * restriction for the merged demographic and logs the operation.</p>
     *
     * @param loggedInInfo LoggedInInfo the current user's session context for audit logging
     * @param demographic_no String the demographic number to merge (source)
     * @param head String the demographic number to merge into (target/head)
     */
    public void Merge(LoggedInInfo loggedInInfo, String demographic_no, String head) {

        io.github.carlos_emr.carlos.commn.model.DemographicMerged dm = new io.github.carlos_emr.carlos.commn.model.DemographicMerged();

        // always merge the head of records that have already been merged to the new head
        String record_head = getHead(demographic_no);
        if (record_head == null)
            dm.setDemographicNo(Integer.parseInt(demographic_no));
        else
            dm.setDemographicNo(Integer.parseInt(record_head));

        dm.setMergedTo(Integer.parseInt(head));

        dm.setLastUpdateUser(loggedInInfo.getLoggedInProviderNo());
        dm.setLastUpdateDate(new Date());
        dao.persist(dm);

        //only if it doesn't exist
        if (secObjPrivilegeDao.find(new SecObjPrivilegePrimaryKey("_all", "_eChart$" + demographic_no)) == null) {
            SecObjPrivilege sop = new SecObjPrivilege();
            SecObjPrivilegePrimaryKey pk = new SecObjPrivilegePrimaryKey();
            pk.setRoleUserGroup("_all");
            pk.setObjectName("_eChart$" + demographic_no);
            sop.setId(pk);
            sop.setPrivilege("|or|");
            sop.setPriority(0);
            sop.setProviderNo("0");
            secObjPrivilegeDao.persist(sop);
        }

        // Log for the source demographic being merged
        LogAction.addLog(loggedInInfo, "DemographicMerged.Merge", "demographic", demographic_no, demographic_no, "merged_from=" + demographic_no + " merged_to=" + head);

        // Log for the target demographic receiving the merge
        LogAction.addLog(loggedInInfo, "DemographicMerged.Merge", "demographic", head, head, "merged_from=" + demographic_no + " merged_to=" + head);


    }

    /**
     * Reverses a merge operation, restoring a demographic as an independent record.
     *
     * <p>Soft-deletes the merge record, removes the eChart security privilege,
     * saves the privilege to the recycle bin for audit, and logs the operation.</p>
     *
     * @param loggedInInfo LoggedInInfo the current user's session context for audit logging
     * @param demographic_no String the demographic number to unmerge
     * @param curUser_no String the provider number of the current user
     */
    public void UnMerge(LoggedInInfo loggedInInfo, String demographic_no, String curUser_no) {

        List<io.github.carlos_emr.carlos.commn.model.DemographicMerged> dms = dao.findByDemographicNo(Integer.parseInt(demographic_no));
        for (io.github.carlos_emr.carlos.commn.model.DemographicMerged dm : dms) {
            dm.setLastUpdateUser(loggedInInfo.getLoggedInProviderNo());
            dm.setLastUpdateDate(new Date());
            dm.setDeleted(1);
            dao.merge(dm);
        }

        String privilege = "";
        String priority = "";
        String provider_no = "";

        List<SecObjPrivilege> sops = this.secObjPrivilegeDao.findByRoleUserGroupAndObjectName("_all", "_eChart$" + demographic_no);
        for (SecObjPrivilege sop : sops) {
            privilege = sop.getPrivilege();
            priority = String.valueOf(sop.getPriority());
            provider_no = sop.getProviderNo();
            secObjPrivilegeDao.remove(sop.getId());
        }

        RecycleBin rb = new RecycleBin();
        rb.setProviderNo(curUser_no);
        rb.setUpdateDateTime(new Date());
        rb.setTableName("secObjPrivilege");
        rb.setKeyword("_all|_eChart$" + demographic_no);
        rb.setTableContent("<roleUserGroup>_all</roleUserGroup>" + "<objectName>_eChart$" + demographic_no + "</objectName><privilege>" + privilege + "</privilege>" + "<priority>" + priority + "</priority><provider_no>" + provider_no + "</provider_no>");
        recycleBinDao.persist(rb);

        // Log the unmerge operation for the demographic
        LogAction.addLog(loggedInInfo, "DemographicMerged.UnMerge", "demographic", demographic_no, demographic_no, "unmerged_demographic_no=" + demographic_no);

    }

    /**
     * Returns the ultimate head demographic number in a merge chain.
     *
     * @param demographic_no String the demographic number to resolve
     * @return String the head demographic number, or null if not part of a merge chain
     */
    public String getHead(String demographic_no) {
        Integer result = getHead(Integer.parseInt(demographic_no));
        if (result != null) {
            return result.toString();
        }
        return null;
    }


    /**
     * Recursively resolves the ultimate head demographic number in a merge chain.
     *
     * <p>Follows the merge chain to its end. If the demographic is not merged
     * to any other record, returns the demographic number itself.</p>
     *
     * @param demographic_no Integer the demographic number to resolve
     * @return Integer the head demographic number at the top of the merge chain
     */
    public Integer getHead(Integer demographic_no) {
        Integer head = null;

        List<io.github.carlos_emr.carlos.commn.model.DemographicMerged> dms = dao.findCurrentByDemographicNo(demographic_no);
        for (io.github.carlos_emr.carlos.commn.model.DemographicMerged dm : dms) {
            head = dm.getMergedTo();
        }

        if (head != null)
            head = getHead(head);
        else
            head = demographic_no;

        return head;
    }

    /**
     * Returns all demographic numbers that have been merged into the given head record.
     *
     * <p>Recursively traverses the merge tree to find all tail records,
     * including records merged into records that were themselves merged
     * into the given head.</p>
     *
     * @param demographic_no String the head demographic number
     * @return ArrayList&lt;String&gt; list of all merged demographic numbers (tails)
     */
    public ArrayList<String> getTail(String demographic_no) {
        ArrayList<String> tailArray = new ArrayList<String>();

        List<io.github.carlos_emr.carlos.commn.model.DemographicMerged> dms = dao.findCurrentByMergedTo(Integer.parseInt(demographic_no));
        for (io.github.carlos_emr.carlos.commn.model.DemographicMerged dm : dms) {
            tailArray.add(String.valueOf(dm.getDemographicNo()));
        }

        int size = tailArray.size();
        for (int i = 0; i < size; i++) {
            tailArray.addAll(getTail(tailArray.get(i)));
        }

        return tailArray;

    }
}
