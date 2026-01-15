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


package ca.openosp.openo.encounter.pageUtil;

import ca.openosp.openo.commn.model.Document;
import ca.openosp.openo.managers.ConsultationManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.OscarProperties;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class EctDisplayEconsult2Action extends EctDisplayAction {

    private final String cmd = "eConsult";
    private final String backendEconsultUrl = OscarProperties.getInstance().getProperty("backendEconsultUrl");
    private ConsultationManager consultationManager = SpringUtils.getBean(ConsultationManager.class);

    /**
     * Generates the eConsult module in the eChart, making it so that when the user clicks on the title it takes them to the demographic's eConsults, when they click the
     * + in the header then it opens a new eConsult, and listing out the eConsults in the eChart
     *
     * @param bean     Current session information
     * @param request  Current request
     * @param Dao      View DAO responsible for rendering encounter
     * @return Always returns a true boolean
     */
    public boolean getInfo(EctSessionBean bean, HttpServletRequest request, NavBarDisplayDAO Dao) {

        // hide the econsult option if it is not available.
        if (backendEconsultUrl == null || backendEconsultUrl.equals("")) {
            return true;
        } else {

            String demographicNo = bean.getDemographicNo();
            SimpleDateFormat dateFormatter = new SimpleDateFormat("dd-MMM-yyyy");
            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

            //set left hand module heading and link 
            Dao.setLeftHeading(cmd);
            StringBuilder eConsultDisplayUrl = new StringBuilder(String.format("..%1$s%2$s", File.separator, "econsult.do"));
            eConsultDisplayUrl.append(String.format("?%1$s=%2$s", "demographicNo", demographicNo));
            eConsultDisplayUrl.append(String.format("&%1$s=%2$s", "method", "frontend"));
            StringBuilder createNewEconsultUrl = new StringBuilder(eConsultDisplayUrl.toString());
            eConsultDisplayUrl.append(String.format("&%1$s=%2$s", "task", "patientSummary"));
            String url = String.format("popupPage(700,960,'%1$s','%2$s');return false;", cmd, eConsultDisplayUrl);
            Dao.setLeftURL(url);

            //set the right hand heading link
            createNewEconsultUrl.append(String.format("&%1$s=%2$s", "task", "draft"));
            url = String.format("popupPage(700,960,'%1$s','%2$s');return false;", "new " + cmd + demographicNo, createNewEconsultUrl);
            Dao.setRightURL(url);
            Dao.setRightHeadingID(cmd);

            // build a list of completed eConsult PDF's to be displayed under the heading.
            List<Document> econsults = consultationManager.getEconsultDocuments(loggedInInfo, Integer.parseInt(demographicNo));
            String documentDateString = "";
            String title = "";
            int hash = 0;
            StringBuilder eConsultDocumentUrl = null;
            int documentId = 0;
            String user = loggedInInfo.getLoggedInProviderNo();
            Date documentDate = null;

            for (Document econsult : econsults) {
                hash = Math.abs(cmd.hashCode());
                documentId = econsult.getId();
                documentDate = econsult.getContentdatetime();
                if (documentDate != null) {
                    documentDateString = dateFormatter.format(documentDate);
                }

                NavBarDisplayDAO.Item item = new NavBarDisplayDAO.Item();

                // set the right side date column 
                item.setDate(documentDate);

                // set the href path to the actual document.
                eConsultDocumentUrl = new StringBuilder(request.getContextPath());
                eConsultDocumentUrl.append(String.format("%1$s%2$s%3$s%4$s", File.separator, "documentManager", File.separator, "ManageDocument.do"));
                eConsultDocumentUrl.append(String.format("%1$s%2$s=%3$s", "?", "method", "display"));
                eConsultDocumentUrl.append(String.format("%1$s%2$s=%3$s", "&", "doc_no", documentId));
                eConsultDocumentUrl.append(String.format("%1$s%2$s=%3$s", "&", "providerNo", user));
                url = String.format("popupPage(700,800,'%1$s','%2$s');return false;", hash, eConsultDocumentUrl);
                item.setURL(url);

                // set visible title for a link to the actual document
                title = econsult.getDocdesc();
                item.setTitle(title);

                // Set the link title attribute. This is displayed when the mouse hovers over the link 
                title += " " + documentDateString;
                item.setLinkTitle(title);

                Dao.addItem(item);
            }

            return true;
        }
    }

    public String getCmd() {
        return cmd;
    }

}
