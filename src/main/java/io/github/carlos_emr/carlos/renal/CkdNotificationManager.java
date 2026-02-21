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
package io.github.carlos_emr.carlos.renal;

import java.text.SimpleDateFormat;
import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Dxresearch;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.tickler.TicklerCreator;

public class CkdNotificationManager {
    private Logger logger = MiscUtils.getLogger();

    private DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
    private MeasurementDao measurementDao = SpringUtils.getBean(MeasurementDao.class);
    private DxresearchDAO dxResearchDao = (DxresearchDAO) SpringUtils.getBean(DxresearchDAO.class);


    private SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

    public CkdNotificationManager() {

    }

    public void doNotify(LoggedInInfo loggedInInfo, Integer demographicNo, List<String> reasons) {
        String notificationScheme = CarlosProperties.getInstance().getProperty("ckd_notification_scheme", "dsa");
        if (notificationScheme.equalsIgnoreCase("tickler") || notificationScheme.equalsIgnoreCase("all")) {
            notifyByTickler(loggedInInfo, demographicNo, reasons);
        }
    }

    public void notifyByTickler(LoggedInInfo loggedInInfo, Integer demographicNo, List<String> reasons) {
        Demographic d = demographicDao.getDemographicById(demographicNo);

        String receiver = CarlosProperties.getInstance().getProperty("ckd_notification_receiver");
        if (receiver == null || receiver.length() == 0) {
            if (d != null) {
                receiver = d.getProviderNo();
            }
        }

        TicklerCreator tc = new TicklerCreator();
        String message = "Ontario Renal Network Pilot<br/>Please screen patient for CKD<br/>";
        for (String reason : reasons) {
            message += (reason + "<br/><br/>");
        }

        List<Measurement> egfrs = measurementDao.findByType(d.getDemographicNo(), "EGFR");
        List<Measurement> acrs = measurementDao.findByType(d.getDemographicNo(), "ACR");

        if (egfrs.size() > 0) {
            message += "EGFR: " + egfrs.get(0).getDataField() + " observed on " + formatter.format(egfrs.get(0).getDateObserved()) + "<br/>";
        } else {
            message += "EGFR: None" + "<br/>";
        }

        if (acrs.size() > 0) {
            message += "ACR: " + acrs.get(0).getDataField() + " observed on " + formatter.format(acrs.get(0).getDateObserved()) + "<br/>";
        } else {
            message += "ACR: None" + "<br/>";
        }

        String flowsheet = CarlosProperties.getInstance().getProperty("ckd_flowsheet", "indicators");
        if (flowsheet.equals("indicators")) {
            message += "Go to CDM Indicators <a href=\"javascript:void(0);\" onclick=\"popupPage(700,1000,'../oscarEncounter/oscarMeasurements/TemplateFlowSheet.jsp?demographic_no=" + demographicNo + "&amp;template=diab3');return false;\">here</a><br/>";
        } else if (flowsheet.equals("diabetes")) {
            message += "Go to Diabetes Flowsheet <a href=\"javascript:void(0)\" onclick=\"popupPage(700,1000,'../oscarEncounter/oscarMeasurements/TemplateFlowSheet.jsp?demographic_no=" + demographicNo + "&amp;template=diab2');return false;\">here</a><br/>";
        }

        Dxresearch screeningDx = null;
        List<Dxresearch> dxs = dxResearchDao.findByDemographicNoResearchCodeAndCodingSystem(d.getDemographicNo(), "CKDSCREEN", "OscarCode");
        for (Dxresearch dx : dxs) {
            if (dx.getStatus() == 'A')
                screeningDx = dx;
        }

        if (screeningDx != null) {
            message += "Screening complete - <a href=\"javascript:void(0)\" onClick=\"popupPage(580,900,'../oscarResearch/dxresearch/dxResearchUpdate.do?status=C&did=" + screeningDx.getId() + "&demographicNo=" + d.getDemographicNo() + "&providerNo=-1');\">Click Here</a>.<br/>";
            message += "Screening not appropriate - <a href=\"javascript:void(0)\" onClick=\"popupPage(580,900,'../oscarResearch/dxresearch/dxResearchUpdate.do?status=D&did=" + screeningDx.getId() + "&demographicNo=" + d.getDemographicNo() + "&providerNo=-1');\">Click Here</a>.<br/>";
        }

        dxs = dxResearchDao.findByDemographicNoResearchCodeAndCodingSystem(d.getDemographicNo(), "585", "icd9");
        if (dxs.size() == 0) {
            message += "<br/>Add 'Chronic Renal Failure' to Dx Registry, and prevent subsequent notifications - <a href=\"javascript:void(0);\" onClick=\"popupPage(580,900,'../oscarResearch/dxresearch/dxResearch.do?selectedCodingSystem=icd9&xml_research1=585&xml_research2=&xml_research3=&xml_research4=&xml_research5=&demographicNo=" + d.getDemographicNo() + "&quickList=default&forward=');\">Click Here</a></br/>";
        }


        String labReqVer = CarlosProperties.getInstance().getProperty("onare_labreqver", "07");
        if (labReqVer.equals("")) {
            labReqVer = "07";
        }


        message += "<br/>Order Labs - <a title=\"Create Lab Requisition\" href=\"javascript:void(0);\" onclick=\"generateRenalLabReq(" + d.getDemographicNo() + ");return false;\">Lab Requisition</a><br/><br/>";

        tc.createTickler(loggedInInfo, demographicNo.toString(), "-1", message, receiver);

        logger.debug("Sent tickler notification to " + receiver + " regarding " + demographicNo + ":" + message);
    }
}
