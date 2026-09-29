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


package io.github.carlos_emr.carlos.form.pageUtil;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.form.FrmRecord;
import io.github.carlos_emr.carlos.form.FrmRecordFactory;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.workflow.WorkFlow;
import io.github.carlos_emr.carlos.workflow.WorkFlowFactory;
import io.github.carlos_emr.carlos.workflow.WorkFlowState;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

/*
 
 CREATE TABLE `formRhImmuneGlobulin` (
  `ID` int(10) NOT NULL auto_increment,
  `demographic_no` int(10) NOT NULL default '0',
  `provider_no` int(10) default NULL,
  `formCreated` date default NULL,
  `formEdited` timestamp(14) NOT NULL,
  `workflowId` int(10),
 
  `state`               char(1),
  `dateOfReferral`      datetime,
  `edd`                 datetime,
  `motherSurname`       varchar(30), 
  `motherFirstname`     varchar(30), 
  `dob`                 date,
  `motherHIN`           varchar(20),
  `motherVC`            varchar(30),
  `motherAddress`       varchar(60),
  `motherCity`          varchar(60),
  `motherABO`           char(3),
  `motherRHtype`        char(4),
  `hospitalForDelivery` varchar(255), 
  `refPhySurname`       varchar(30), 
  `refPhyFirstname`     varchar(30), 
  `refPhyAddress`       varchar(60), 
  `refPhyPhone`         varchar(20),
  `refPhyFax`           varchar(20),
                     
  
  PRIMARY KEY  (`ID`)
) TYPE=MyISAM
 
 
 
 */


/*
 * @author jay
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class FrmFormRHPrevention2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {
        String demographicNo = RhFormRequestGuard.authorize(request, response, securityInfoManager);
        if (demographicNo == null) return NONE;
        LoggedInInfo info = LoggedInInfo.getLoggedInInfoFromSession(request);
        final String patient = demographicNo;
        try {
            var transaction = new org.springframework.transaction.support.TransactionTemplate(
                    SpringUtils.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            Integer formId = transaction.execute(status -> {
                try {
                    return saveInTransaction(patient, info.getLoggedInProviderNo());
                } catch (SQLException failure) {
                    throw new IllegalStateException("Unable to save RH form", failure);
                }
            });
            request.setAttribute("demographic_no", patient);
            request.setAttribute("savedRhFormId", String.valueOf(formId));
            LogAction.addLog(info.getLoggedInProviderNo(), LogConst.ADD, "RhImmuneGlobulin",
                    String.valueOf(formId), request.getRemoteAddr());
            LogAction.addLog(info.getLoggedInProviderNo(), LogConst.UPDATE, "WF_RH",
                    (String) request.getAttribute("savedRhState"), request.getRemoteAddr());
            return SUCCESS;
        } catch (IllegalArgumentException invalid) {
            RhFormRequestGuard.rejectInvalidWorkflow(response);
            return NONE;
        } catch (RuntimeException failure) {
            MiscUtils.getLogger().error("RH form save failed", failure);
            RhFormRequestGuard.rejectSaveFailure(response);
            return NONE;
        }
    }

    private int saveInTransaction(String demographicNo, String providerNo) throws SQLException {
        String workflowId = request.getParameter("workflowId");
        String state = request.getParameter("state");
        if (state != null && !java.util.Set.of("1", "2", "3", "4", "5", "C").contains(state)) {
            throw new IllegalArgumentException("Invalid RH state");
        }
        WorkFlow flow = new WorkFlowFactory().getWorkFlow("RH");
        ArrayList current = flow.getActiveWorkFlowList(demographicNo);
        Hashtable selected = null;
        if (current != null) {
            for (Object candidate : current) {
                Hashtable row = (Hashtable) candidate;
                if (workflowId == null || workflowId.equals(row.get("ID"))) {
                    selected = row;
                    break;
                }
            }
        }
        if (workflowId != null && selected == null) {
            throw new IllegalArgumentException("RH workflow does not belong to this patient's active pregnancy");
        }
        Date endDate = UtilDateUtilities.StringToDate(request.getParameter("edd"));
        int workId;
        if (selected == null) {
            workId = flow.addToWorkFlow(providerNo, demographicNo, endDate);
            state = WorkFlowState.INIT_STATE;
        } else {
            workId = Integer.parseInt((String) selected.get("ID"));
            if (state == null) state = (String) selected.getOrDefault("current_state", WorkFlowState.INIT_STATE);
            new WorkFlowState().updateWorkFlowState(String.valueOf(workId), state, endDate);
        }
        if (workId <= 0) throw new IllegalStateException("RH workflow was not created");
        FrmRecord record = new FrmRecordFactory().factory("RhImmuneGlobulin");
        Properties props = new Properties();
        for (Enumeration names = request.getParameterNames(); names.hasMoreElements();) {
            String name = (String) names.nextElement();
            props.setProperty(name, request.getParameter(name));
        }
        props.setProperty("workflowId", String.valueOf(workId));
        props.setProperty("state", state);
        props.setProperty("provider_no", providerNo);
        props.setProperty("demographic_no", demographicNo);
        int formId = record.saveFormRecord(props);
        if (formId <= 0) throw new IllegalStateException("RH form was not saved");
        request.setAttribute("savedRhState", state);
        return formId;
    }
}
