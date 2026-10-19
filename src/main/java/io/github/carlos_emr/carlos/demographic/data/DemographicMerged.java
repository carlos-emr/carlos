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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.DemographicMergedDao;
import io.github.carlos_emr.carlos.commn.dao.RecycleBinDao;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.RecycleBin;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilege;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilegePrimaryKey;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.log.LogAction;

/**
 * @author wrighd
 */
public class DemographicMerged {

    Logger logger = MiscUtils.getLogger();
    private DemographicMergedDao dao = SpringUtils.getBean(DemographicMergedDao.class);
    private SecObjPrivilegeDao secObjPrivilegeDao = SpringUtils.getBean(SecObjPrivilegeDao.class);
    private RecycleBinDao recycleBinDao = SpringUtils.getBean(RecycleBinDao.class);

    public DemographicMerged() {
    }

    /**
     * Records that {@code demographic_no} (or, when it was already merged, its current head) is merged into
     * {@code head}.
     *
     * <p>Callers that take ids from a request should reject merged-source records up front with
     * {@link #isMergedSource(int)}; this method additionally refuses any merge that would close a loop in the
     * {@code demographic_merged} chain, because {@link #getHead(Integer)} and every lab/appointment lookup that
     * follows the chain assumes it is acyclic.
     *
     * @throws IllegalArgumentException if the merge would link a record to itself or create a merge cycle
     */
    public void Merge(LoggedInInfo loggedInInfo, String demographic_no, String head) {

        io.github.carlos_emr.carlos.commn.model.DemographicMerged dm = new io.github.carlos_emr.carlos.commn.model.DemographicMerged();

        // always merge the head of records that have already been merged to the new head
        Integer sourceHead = getHead(Integer.parseInt(demographic_no));
        Integer target = Integer.valueOf(head);
        if (sourceHead.equals(target) || sourceHead.equals(getHead(target))) {
            throw new IllegalArgumentException("Refusing a demographic merge that would create a merge cycle");
        }
        dm.setDemographicNo(sourceHead);

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

    public String getHead(String demographic_no) {
        Integer result = getHead(Integer.parseInt(demographic_no));
        if (result != null) {
            return result.toString();
        }
        return null;
    }


    /**
     * Whether the record is currently merged into another record, i.e. it is a non-head "source" of a merge.
     * Only head records may be merged or used as a merge target.
     */
    public boolean isMergedSource(int demographicNo) {
        return !dao.findCurrentByDemographicNo(demographicNo).isEmpty();
    }

    /**
     * Follows the active {@code demographic_merged} chain from {@code demographic_no} to its head record.
     *
     * <p>Iterative with a visited set: a corrupt chain that loops back on itself (for example from a crafted
     * merge request before the cycle checks existed) would otherwise recurse until the stack overflows. When a
     * cycle is found there is no real head, so the starting record is returned unchanged. Callers that must
     * not treat such a record as a head (lab routing) use {@link #findHead(Integer)} instead.
     */
    public Integer getHead(Integer demographic_no) {
        Integer head = findHead(demographic_no);
        return head != null ? head : demographic_no;
    }

    /**
     * Like {@link #getHead(Integer)}, but returns {@code null} when the chain is a cycle and therefore has no
     * head. A cyclic record is neither a head nor a record whose head is known, so a caller deciding where to
     * deliver data (an incoming lab) must leave it unresolved rather than attach it to an arbitrary member.
     */
    public Integer findHead(Integer demographic_no) {
        Set<Integer> visited = new HashSet<>();
        Integer current = demographic_no;
        while (visited.add(current)) {
            Integer next = null;
            for (io.github.carlos_emr.carlos.commn.model.DemographicMerged dm : dao.findCurrentByDemographicNo(current)) {
                next = dm.getMergedTo();
            }
            if (next == null) {
                return current;
            }
            current = next;
        }
        logger.warn("Merge cycle detected in demographic_merged chain starting at demographic "
                + LogSafe.sanitizeObject(demographic_no));
        return null;
    }

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
