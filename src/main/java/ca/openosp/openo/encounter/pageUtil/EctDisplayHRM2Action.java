//CHECKSTYLE:OFF
/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */


package ca.openosp.openo.encounter.pageUtil;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.OscarLogDao;
import ca.openosp.openo.hospitalReportManager.HRMUtil;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.util.DateUtils;
import ca.openosp.openo.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class EctDisplayHRM2Action extends EctDisplayAction {

    private static Logger logger = MiscUtils.getLogger();
    private static final String cmd = "HRM";
    private OscarLogDao oscarLogDao = (OscarLogDao) SpringUtils.getBean(OscarLogDao.class);

    public boolean getInfo(EctSessionBean bean, HttpServletRequest request, NavBarDisplayDAO Dao) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_hrm", "r", null)) {
            return true; //Prevention link won't show up on new CME screen.
        } else {

            String winName = "docs" + bean.demographicNo;
            String url = "popupPage(500,1115,'" + winName + "', '" + request.getContextPath() + "/hospitalReportManager/displayHRMDocList.jsp?demographic_no=" + bean.demographicNo + "')";
            Dao.setLeftURL(url);
            Dao.setLeftHeading(getText("oscarEncounter.Index.msgHRMDocuments"));

            Dao.setRightHeadingID(cmd); //no menu so set div id to unique id for this action

            StringBuilder javascript = new StringBuilder("<script type=\"text/javascript\">");
            String js = "";
            String dbFormat = "yyyy-MM-dd";
            String serviceDateStr = "";
            String key;
            int hash;
            String BGCOLOUR = request.getParameter("hC");
            Date date;

            ArrayList<HashMap<String, ? extends Object>> allHRMDocuments = HRMUtil.listHRMDocuments(loggedInInfo, "report_date", false, bean.demographicNo, true);
            for (HashMap<String, ? extends Object> hrmDocument : allHRMDocuments) {
                String hrmDocumentId = String.valueOf(hrmDocument.get("id"));
                String displayHRMName = (String) hrmDocument.get("name");

                displayHRMName = StringUtils.maxLenString(displayHRMName, MAX_LEN_TITLE, CROP_LEN_TITLE, ELLIPSES);

                DateFormat formatter = new SimpleDateFormat(dbFormat);
                String dateStr = (String) hrmDocument.get("report_date");
                NavBarDisplayDAO.Item item = NavBarDisplayDAO.Item();
                try {
                    date = formatter.parse(dateStr);
                    serviceDateStr = DateUtils.formatDate(date, request.getLocale());
                } catch (ParseException ex) {
                    logger.debug("EctDisplayHRM2Action: Error creating date " + ex.getMessage());
                    serviceDateStr = "Error";
                    date = null;
                }

                String user = (String) request.getSession().getAttribute("user");
                item.setDate(date);
                hash = Math.abs(winName.hashCode());

                url = "popupPage(700,800,'" + hash + "', '" + request.getContextPath() + "/hospitalReportManager/Display.do?id=" + hrmDocumentId + "&segmentID=" + hrmDocumentId + "');";

                String labRead = "";
                if (!oscarLogDao.hasRead(user, "hrm", hrmDocumentId)) {
                    labRead = "*";
                }


                item.setLinkTitle(displayHRMName + serviceDateStr);
                item.setTitle(labRead + displayHRMName + labRead);
                key = StringUtils.maxLenString((String) hrmDocument.get("report_type"), MAX_LEN_KEY, CROP_LEN_KEY, ELLIPSES) + "(" + serviceDateStr + ")";
                key = StringEscapeUtils.escapeEcmaScript(key);


                js = "itemColours['" + key + "'] = '" + BGCOLOUR + "'; autoCompleted['" + key + "'] = \"" + url + "\"; autoCompList.push('" + key + "');";
                javascript.append(js);
                url += "return false;";
                item.setURL(url);
                Dao.addItem(item);
            }
            javascript.append("</script>");

            Dao.setJavaScript(javascript.toString());
            Dao.sortItems(NavBarDisplayDAO.DATESORT_ASC);

            return true;
        }
    }

    @Override
    public String getCmd() {
        return cmd;
    }

}
