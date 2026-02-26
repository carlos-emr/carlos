/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.lab.ca.all.pageUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;


import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class CreateLabelTDIS2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    Logger logger = MiscUtils.getLogger();

    public String execute() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_lab", "w", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }

        String label = this.getLabel(); //request.getParameter("label");
        logger.info("Label before db insert =" + label);
        String lab_no = this.getLab_no(); //request.getParameter("lab_no");

        String ajaxcall = request.getParameter("ajaxcall");

        if (label == null || label.equals("")) {
            request.setAttribute("error", "Please enter a label");

        }
        Hl7TextInfoDao hl7dao = (Hl7TextInfoDao) SpringUtils.getBean(Hl7TextInfoDao.class);

        try {

            int labNum = Integer.parseInt(lab_no);
            hl7dao.createUpdateLabelByLabNumber(label, labNum);

            logger.info("Label created successfully.");


        } catch (Exception e) {
            logger.error("Error inserting label into hl7TextInfo" + e);
            request.setAttribute("error", "There was an error creating a label.");

        }

        logger.info("Label =" + label);
        label = StringEscapeUtils.escapeEcmaScript(label);
        if (ajaxcall != null && !"null".equalsIgnoreCase(ajaxcall)) {
            return null;
        }
        return "complete";
    }
    private String lab_no;
    private String accessionNum;
    private String label;

    public String getLab_no() {
        return lab_no;
    }

    public void setLab_no(String lab_no) {
        this.lab_no = lab_no;
    }

    public String getAccessionNum() {
        return accessionNum;
    }

    public void setAccessionNum(String accessionNum) {
        this.accessionNum = accessionNum;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
