//CHECKSTYLE:OFF
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
 */


/*
 * DemographicMergedDAO.java
 *
 * Created on September 14, 2007, 1:13 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ca.openosp.openo.demographic.data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.DemographicMergedDao;
import ca.openosp.openo.commn.dao.RecycleBinDao;
import ca.openosp.openo.commn.dao.SecObjPrivilegeDao;
import ca.openosp.openo.commn.model.RecycleBin;
import ca.openosp.openo.commn.model.SecObjPrivilege;
import ca.openosp.openo.commn.model.SecObjPrivilegePrimaryKey;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.log.LogAction;

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

    public void Merge(LoggedInInfo loggedInInfo, String demographic_no, String head) {

        ca.openosp.openo.commn.model.DemographicMerged dm = new ca.openosp.openo.commn.model.DemographicMerged();

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

    public void UnMerge(LoggedInInfo loggedInInfo, String demographic_no, String curUser_no) {

        List<ca.openosp.openo.commn.model.DemographicMerged> dms = dao.findByDemographicNo(Integer.parseInt(demographic_no));
        for (ca.openosp.openo.commn.model.DemographicMerged dm : dms) {
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


    public Integer getHead(Integer demographic_no) {
        Integer head = null;

        List<ca.openosp.openo.commn.model.DemographicMerged> dms = dao.findCurrentByDemographicNo(demographic_no);
        for (ca.openosp.openo.commn.model.DemographicMerged dm : dms) {
            head = dm.getMergedTo();
        }

        if (head != null)
            head = getHead(head);
        else
            head = demographic_no;

        return head;
    }

    public ArrayList<String> getTail(String demographic_no) {
        ArrayList<String> tailArray = new ArrayList<String>();

        List<ca.openosp.openo.commn.model.DemographicMerged> dms = dao.findCurrentByMergedTo(Integer.parseInt(demographic_no));
        for (ca.openosp.openo.commn.model.DemographicMerged dm : dms) {
            tailArray.add(String.valueOf(dm.getDemographicNo()));
        }

        int size = tailArray.size();
        for (int i = 0; i < size; i++) {
            tailArray.addAll(getTail(tailArray.get(i)));
        }

        return tailArray;

    }
}
