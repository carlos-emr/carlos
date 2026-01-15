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

import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.EncounterFormDao;
import ca.openosp.openo.commn.model.EncounterForm;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.OscarProperties;
import ca.openosp.openo.encounter.data.EctFormData;
import ca.openosp.openo.lab.LabRequestReportLink;
import ca.openosp.openo.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class EctDisplayForm2Action extends EctDisplayAction {

    private static Logger logger = MiscUtils.getLogger();

    private String cmd = "forms";
    private String menuId = "1";

    public boolean getInfo(EctSessionBean bean, HttpServletRequest request, NavBarDisplayDAO Dao) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String appointmentNo = bean.appointmentNo;
        if (appointmentNo == null && request.getSession().getAttribute("cur_appointment_no") != null) {
            appointmentNo = (String) request.getSession().getAttribute("cur_appointment_no");

        }


        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_form", "r", null)) {
            return true; // The link of form won't show up on new CME screen.
        } else {
            try {

                String winName = "Forms" + bean.demographicNo;
                StringBuilder url = new StringBuilder("popupPage(600, 700, '" + winName + "', '" + request.getContextPath() + "/oscarEncounter/formlist.jsp?demographic_no=" + bean.demographicNo + "')");

                // set text for lefthand module title
                Dao.setLeftHeading(getText("oscarEncounter.Index.msgForms"));
                // set link for lefthand module title
                Dao.setLeftURL(url.toString());

                // we're going to display a pop up menu of forms so we set the menu title and id num of menu
                Dao.setRightHeadingID(menuId);
                Dao.setMenuHeader(getText("oscarEncounter.LeftNavBar.AddFrm"));
                StringBuilder javascript = new StringBuilder("<script type=\"text/javascript\">");
                String js = "";
                String serviceDateStr;
                StringBuilder strTitle;
                String fullTitle;
                Date date = null;
                String key;
                int hash;
                // grab all of the forms
                EncounterFormDao encounterFormDao = (EncounterFormDao) SpringUtils.getBean(EncounterFormDao.class);
                List<EncounterForm> encounterForms = encounterFormDao.findAll();
                Collections.sort(encounterForms, EncounterForm.BC_FIRST_COMPARATOR);

                String BGCOLOUR = request.getParameter("hC");
                for (EncounterForm encounterForm : encounterForms) {
                    if (encounterForm.getFormName().equalsIgnoreCase("Discharge Summary")) {
                        String caisiProperty = OscarProperties.getInstance().getProperty("caisi");
                        if (caisiProperty != null && (caisiProperty.equalsIgnoreCase("yes")
                                || caisiProperty.equalsIgnoreCase("true")
                                || caisiProperty.equalsIgnoreCase("on"))) {

                        } else {
                            continue; //form out
                        }
                    }
                    winName = encounterForm.getFormName() + bean.demographicNo;

                    String table = encounterForm.getFormTable();
                    if (!table.equalsIgnoreCase("")) {

                        EctFormData.PatientForm[] pforms = EctFormData.getPatientFormsFromLocalAndRemote(loggedInInfo, bean.demographicNo, table);
                        // if a form has been started for the patient, create a module item for it
                        if (pforms.length > 0) {

                            NavBarDisplayDAO.Item item = NavBarDisplayDAO.Item();
                            EctFormData.PatientForm pfrm = pforms[0];

                            // convert date to that specified in base class
                            DateFormat formatter = new SimpleDateFormat(EctFormData.DATETIME_FORMAT);
                            serviceDateStr = pfrm.getEdited();

                            try {
                                date = formatter.parse(serviceDateStr);
                            } catch (ParseException ex) {
                                logger.debug("EctDisplayForm2Action: Error creating date " + ex.getMessage());
                            }

                            item.setDate(date);

                            fullTitle = encounterForm.getFormName();
                            strTitle = new StringBuilder(StringUtils.maxLenString(fullTitle, MAX_LEN_TITLE, CROP_LEN_TITLE, ELLIPSES));

                            if (table.equals("formLabReq07")) {
                                Long reportId = null;

                                HashMap<String, Object> res = LabRequestReportLink.getLinkByRequestId("formLabReq07", Long.valueOf(pfrm.getFormId()));
                                reportId = (Long) res.get("report_id");

                                if (reportId == null) {
                                    strTitle.insert(0, "*");
                                    strTitle.append("*");
                                }
                            }
                            winName = winName + serviceDateStr;
                            hash = Math.abs(winName.hashCode());
                            url = new StringBuilder(
                                    "popupPage(700,960,'" + hash + "started', '" +
                                            request.getContextPath() +
                                            "/form/forwardshortcutname.do?formname="
                                            + encounterForm.getFormName() +
                                            "&demographic_no=" + bean.demographicNo +
                                            (pfrm.getRemoteFacilityId() != null ? "&remoteFacilityId=" + pfrm.getRemoteFacilityId() : "") +
                                            (appointmentNo != null ? "&appointmentNo=" + appointmentNo : "")
                                            + "&formId=latest" + "');");

                            key = StringUtils.maxLenString(fullTitle, MAX_LEN_KEY, CROP_LEN_KEY, ELLIPSES) + "(" + serviceDateStr + ")";
                            key = StringEscapeUtils.escapeEcmaScript(key);

                            // auto completion arrays and colour code are set
                            js = "itemColours['" + key + "'] = '" + BGCOLOUR + "'; autoCompList.push('" + key + "'); autoCompleted['" + key + "'] = \"" + url + "\";";
                            javascript.append(js);

                            // set item href text
                            item.setTitle(strTitle.toString());
                            // set item link
                            url.append("return false;");
                            item.setURL(url.toString());
                            // set item link title text
                            item.setLinkTitle(fullTitle + " " + serviceDateStr);


                            //sorry I have to do this, since the "hidden" field, doesn't mean hidden.
                            //this is a fix so that when they've migrated to the enhanced form, the
                            //regular one is hidden. It's still accessible from the list mode off
                            //the tab header though, if they really need to get to it.
                            boolean dontAdd = false;
                            if (table.equals("formONAR")) {
                                //check to see if we have an enhanced one
                                EctFormData.PatientForm[] pf = EctFormData.getPatientFormsFromLocalAndRemote(loggedInInfo, bean.demographicNo, "formONAREnhancedRecord");
                                if (pf.length > 0) {
                                    dontAdd = true;
                                }
                            }
                            if (!dontAdd)
                                Dao.addItem(item);
                        }
                    }

                    // we add all unhidden forms to the pop up menu
                    if (!encounterForm.isHidden()) {
                        hash = Math.abs(winName.hashCode());
                        url = new StringBuilder("popupPage(700,960,'" + hash + "new', '" + encounterForm.getFormValue() + bean.demographicNo + "&formId=0&provNo=" + bean.providerNo + "&parentAjaxId=" + cmd + ((appointmentNo != null) ? "&appointmentNo=" + appointmentNo : "") + "')");
                        Dao.addPopUpUrl(url.toString());
                        key = StringUtils.maxLenString(encounterForm.getFormName(), MAX_LEN_KEY, CROP_LEN_KEY, ELLIPSES) + " (new)";
                        Dao.addPopUpText(encounterForm.getFormName());
                        key = StringEscapeUtils.escapeEcmaScript(key);

                        // auto completion arrays and colour code are set
                        js = "itemColours['" + key + "'] = '" + BGCOLOUR + "'; autoCompList.push('" + key + "'); autoCompleted['" + key + "'] = \"" + url + ";\";";
                        javascript.append(js);
                    }
                }
                url = new StringBuilder("return !showMenu('" + menuId + "', event);");
                Dao.setRightURL(url.toString());

                javascript.append("</script>");
                Dao.setJavaScript(javascript.toString());

                // sort module items, i.e. forms, from most recently started to more distant
                Dao.sortItems(NavBarDisplayDAO.DATESORT_ASC);
            } catch (Exception e) {
                logger.error("EctDisplayForm2Action SQL ERROR:", e);
                return false;
            }

            return true;
        }
    }

    @Override
    public String getCmd() {
        return cmd;
    }
}
