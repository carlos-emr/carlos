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
 * PreventionReport2Action.java
 *
 * Created on May 30, 2005, 7:52 PM
 */

package io.github.carlos_emr.carlos.prevention.pageUtil;


import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.openpdf.text.DocumentException;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;


/**
 * Struts2 action that generates a PDF of selected prevention/immunization records for a patient.
 *
 * <p>Delegates PDF generation to {@link PreventionPrintPdf} and streams the result directly
 * to the HTTP response as an {@code application/pdf} attachment. Requires the
 * {@code _prevention} read privilege.</p>
 *
 * @see PreventionPrintPdf
 * @see PreventionDisplayConfig
 * @since 2007-03-14
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class PreventionPrint2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);


    public PreventionPrint2Action() {
    }

    /**
     * Validates security privileges and generates the prevention PDF.
     *
     * @return String {@code null} on success (response written directly), or {@code "error"} if
     *         PDF generation fails due to a {@link DocumentException} or {@link IOException}
     * @throws SecurityException if the logged-in user lacks {@code _prevention} read privilege
     */
    public String execute() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_prevention", "r", null)) {
            throw new SecurityException("missing required sec object (_prevention)");
        }


        try {
            PreventionPrintPdf pdf = new PreventionPrintPdf();
            pdf.printPdf(request, response);

        } catch (DocumentException de) {
            logger.error("", de);
            request.setAttribute("printError", Boolean.valueOf(true));
            return "error";
        } catch (IOException ioe) {
            logger.error("", ioe);
            request.setAttribute("printError", Boolean.valueOf(true));
            return "error";
        }

        return null;
    }

}
