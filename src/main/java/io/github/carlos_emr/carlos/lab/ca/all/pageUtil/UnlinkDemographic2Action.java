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
package io.github.carlos_emr.carlos.lab.ca.all.pageUtil;

import com.opensymphony.xwork2.ActionSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.OscarAuditLogger;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.form.JSONUtil;
import io.github.carlos_emr.carlos.log.LogConst;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.List;

public class UnlinkDemographic2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final Logger logger = MiscUtils.getLogger();
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private final PatientLabRoutingDao plrDao = SpringUtils.getBean(PatientLabRoutingDao.class);

    private final ProviderLabRoutingDao providerLabRoutingDao = SpringUtils.getBean(ProviderLabRoutingDao.class);

    public UnlinkDemographic2Action() {
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String execute() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", "u", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }
        boolean success = false;
        //set the demographicNo in the patientLabRouting table
        String reason = request.getParameter("reason");
        String labNoStr = request.getParameter("labNo");
        Integer labNo = Integer.parseInt(labNoStr);

        List<PatientLabRouting> plr = plrDao.findByLabNoAndLabType(labNo, ProviderLabRoutingDao.LAB_TYPE.HL7.name());
        Integer demoNo = PatientLabRoutingDao.UNMATCHED;

        for (PatientLabRouting patientLabRouting : plr) {
            demoNo = patientLabRouting.getDemographicNo();
            patientLabRouting.setDemographicNo(PatientLabRoutingDao.UNMATCHED);
            patientLabRouting.setDateModified(new Date());
            plrDao.merge(patientLabRouting);
            if (patientLabRouting.getDemographicNo().equals(PatientLabRoutingDao.UNMATCHED)) {
                success = true;
                logger.debug("Unlinked lab with segmentID: " + labNo + " from eChart of Demographic " + demoNo);
                OscarAuditLogger.getInstance().log(loggedInInfo, LogConst.UNLINK, LogConst.CON_HL7_LAB, String.valueOf(labNo), request.getRemoteAddr(), demoNo, reason);
            } else {
                break;
            }
        }

        if (success) {
            List<ProviderLabRoutingModel> providerLabRoutingModel = providerLabRoutingDao.findAllLabRoutingByIdandType(labNo, ProviderLabRoutingDao.LAB_TYPE.HL7.name());
            for (ProviderLabRoutingModel providerLabRouting : providerLabRoutingModel) {
                String currentStatus = providerLabRouting.getStatus();
                providerLabRouting.setStatus(ProviderLabRoutingDao.STATUS.N.name());
                String comment = providerLabRouting.getComment() + " Lab unlinked from incorrect demographic number: " + demoNo;
                providerLabRouting.setComment(comment.trim());
                providerLabRouting.setTimestamp(new Date());
                providerLabRoutingDao.merge(providerLabRouting);
                OscarAuditLogger.getInstance().log(loggedInInfo, LogConst.UNLINK, LogConst.CON_HL7_LAB, String.valueOf(labNo), request.getRemoteAddr(), demoNo, "Provider " + providerLabRouting.getProviderNo() + " Ack status from " + currentStatus + " to " + ProviderLabRoutingDao.STATUS.N.name());
            }
        }

        ObjectNode jsonObject = objectMapper.createObjectNode();
        jsonObject.put("success", success);
        jsonObject.put("unlinkedDemographicNo", demoNo);
        jsonObject.put("labNo", labNo);
        jsonObject.put("reason", reason);

        JSONUtil.jsonResponse(response, jsonObject);

        return null;
    }


}
