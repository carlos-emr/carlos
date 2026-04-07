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
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.encounter.pageUtil;

import java.util.List;
import java.util.Properties;
import java.util.Vector;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.decisionSupport.model.DSConsequence;
import io.github.carlos_emr.carlos.decisionSupport.model.DSGuideline;
import io.github.carlos_emr.carlos.decisionSupport.service.DSService;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.util.OscarRoleObjectPrivilege;
import io.github.carlos_emr.carlos.util.StringUtils;

/**
 * @author apavel
 */
import org.apache.struts2.ServletActionContext;

public class EctDisplayDecisionSupportAlerts2Action extends EctDisplayAction {
    private String cmd = "Guidelines";
    private static final Logger logger = MiscUtils.getLogger();

    public boolean getInfo(EctSessionBean bean, HttpServletRequest request, NavBarDisplayDAO Dao) {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        boolean a = true;
        Vector v = OscarRoleObjectPrivilege.getPrivilegeProp("_newCasemgmt.decisionSupportAlerts");
        String roleName = (String) request.getSession().getAttribute("userrole") + "," + (String) request.getSession().getAttribute("user");
        a = OscarRoleObjectPrivilege.checkPrivilege(roleName, (Properties) v.get(0), (Vector) v.get(1));
        if (!a) {
            return true; //decisionSupportAlerts link won't show up on new CME screen.
        } else {

            //set lefthand module heading and link
            String winName = "dsalert" + bean.demographicNo;
            String dsPath = request.getContextPath() + "/encounter/decisionSupport/guidelineAction.do?method=list&provider_no=" + bean.providerNo + "&demographic_no=" + bean.demographicNo + "&parentAjaxId=" + cmd;
            Dao.setLeftHeading(getText("global.decisionSupportAlerts"));
            Dao.setLeftPopup(500, 950, winName, dsPath);

            //set the right hand heading link
            Dao.setRightPopup(500, 950, winName, dsPath);
            winName = "AddeForm" + bean.demographicNo;

            Dao.setRightHeadingID(cmd);  //no menu so set div id to unique id for this action

            String url;

            WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(ServletActionContext.getServletContext());
            DSService dsService = (DSService) ctx.getBean(DSService.class);

            List<DSGuideline> dsGuidelines = dsService.getDsGuidelinesByProvider(bean.providerNo);

            String key;

            String BGCOLOUR = request.getParameter("hC");


            for (DSGuideline dsGuideline : dsGuidelines) {
                if (CarlosProperties.getInstance().getProperty("dsa.skip." + dsGuideline.getTitle().replaceAll(" ", "_"), "false").equals("true")) {
                    continue;
                }
                try {
                    List<DSConsequence> dsConsequences = dsGuideline.evaluate(loggedInInfo, bean.demographicNo);
                    if (dsConsequences == null) continue;
                    for (DSConsequence dsConsequence : dsConsequences) {
                        if (dsConsequence.getConsequenceType() != DSConsequence.ConsequenceType.warning)
                            continue;

                        NavBarDisplayDAO.Item item = NavBarDisplayDAO.Item();
                        winName = dsConsequence.getConsequenceType().toString() + bean.demographicNo;

                        url = "popupPage(500,950,'" + winName + "','" + request.getContextPath() + "/encounter/decisionSupport/guidelineAction.do?method=detail&guidelineId=" + dsGuideline.getId() + "&provider_no=" + bean.providerNo + "&demographic_no=" + bean.demographicNo + "&parentAjaxId=" + cmd + "'); return false;";
                        //Date date = (Date)curform.get("formDateAsDate");
                        //String formattedDate = DateUtils.getDate(date,dateFormat,request.getLocale());
                        key = StringUtils.maxLenString(dsConsequence.getText(), MAX_LEN_KEY, CROP_LEN_KEY, ELLIPSES);
                        item.setLinkTitle(dsGuideline.getTitle());
                        Dao.addAutoCompleteItem(key, url, BGCOLOUR);
                        url += "return false;";
                        item.setURL(url);
                        String strTitle = StringUtils.maxLenString(dsGuideline.getTitle(), MAX_LEN_TITLE, CROP_LEN_TITLE, ELLIPSES);
                        item.setTitle(strTitle);
                        if (dsConsequence.getConsequenceStrength() == DSConsequence.ConsequenceStrength.warning) {
                            item.setColour("#ff5409;");
                        }
                        //item.setDate(new Date());
                        Dao.addItem(item);
                    }
                } catch (Exception e) {
                    logger.error("Unable to evaluate patient against a DS guideline '" + dsGuideline.getTitle() + "' of UUID '" + dsGuideline.getUuid() + "'", e);
                }
            }

            return true;
        }
    }

    public String getCmd() {
        return cmd;
    }
}
