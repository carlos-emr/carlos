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


package io.github.carlos_emr.carlos.encounter.pageUtil;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctViewConsultationRequestsUtil;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import org.springframework.web.context.support.WebApplicationContextUtils;

import io.github.carlos_emr.carlos.util.DateUtils;
import io.github.carlos_emr.carlos.util.StringUtils;


/**
 * Retrieves consultation requests for demographic
 */

public class EctDisplayConsult2Action extends EctDisplayAction {
    /** Placeholder the consultation row builder emits when a consult has no specialist. */
    static final String NOT_APPLICABLE = "N/A";

    private String cmd = "consultation";

    public boolean getInfo(EctSessionBean bean, HttpServletRequest request, NavBarDisplayDAO Dao) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String appointmentNo = bean.appointmentNo;

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_con", "r", null)) {
            return true; //Consultations link won't show up on new CME screen.
        } else {
            //set lefthand module heading and link
            String winName = "Consultation" + bean.demographicNo;
            String consultPath = request.getContextPath() + "/encounter/oscarConsultationRequest/ViewDisplayDemographicConsultationRequests?de=" + bean.demographicNo;
            Dao.setLeftHeading(getText("encounter.LeftNavBar.Consult"));
            Dao.setLeftPopup(700, 960, winName, consultPath);

            //set the right hand heading link\
            winName = "newConsult" + bean.demographicNo;
            Dao.setRightPopup(700, 960, winName, request.getContextPath() + "/encounter/oscarConsultationRequest/ViewConsultationFormRequest?de=" + bean.demographicNo + "&teamVar=&appNo=" + appointmentNo);
            Dao.setRightHeadingID(cmd);  //no menu so set div id to unique id for this action 

            //grab all consultations for patient and add list item for each
            EctViewConsultationRequestsUtil theRequests;
            theRequests = new EctViewConsultationRequestsUtil();
            theRequests.estConsultationVecByDemographic(loggedInInfo, bean.demographicNo);

            //determine cut off period for highlighting
            UserPropertyDAO pref = (UserPropertyDAO) WebApplicationContextUtils.getWebApplicationContext(request.getSession().getServletContext()).getBean(UserPropertyDAO.class);

            UserProperty up = pref.getProp(bean.providerNo, UserProperty.CONSULTATION_TIME_PERIOD_WARNING);
            String timeperiod = null;

            if (up != null && up.getValue() != null && !up.getValue().trim().equals("")) {
                timeperiod = up.getValue();
            }

            Calendar cal = Calendar.getInstance();
            int countback = -1;
            if (timeperiod != null) {
                countback = Integer.parseInt(timeperiod);
                countback = countback * -1;
            }
            cal.add(Calendar.MONTH, countback);
            Date cutoffDate = cal.getTime();

            String red = "red";
            String dbFormat = "yyyy-MM-dd";
            String serviceDateStr;
            Date date;
            for (int idx = theRequests.ids.size() - 1; idx >= 0; --idx) {
                NavBarDisplayDAO.Item item = NavBarDisplayDAO.Item();

                String service = ""; 
                String specialist = "";
                String dateStr = "";
                String status = "";

                if (theRequests.service != null && !theRequests.service.isEmpty()) {
                    service = theRequests.service.get(idx);
                }
                if (theRequests.vSpecialist != null && !theRequests.vSpecialist.isEmpty()) {
                    specialist =  theRequests.vSpecialist.get(idx);
                }
                if (theRequests.date != null && !theRequests.date.isEmpty()) {
                    dateStr = theRequests.date.get(idx);
                }
                if (theRequests.status != null && !theRequests.status.isEmpty()) {
                    status = theRequests.status.get(idx);
                }

                DateFormat formatter = new SimpleDateFormat(dbFormat);
                try {
                    date = formatter.parse(dateStr);
                    serviceDateStr = DateUtils.formatDate(date, request.getLocale());
                    //if we are after cut off date and not completed set to red
                    // Legacy/imported requests can have no status. Keep them visible and
                    // overdue; only an explicit completed status suppresses the warning.
                    if (date.before(cutoffDate) && !"4".equals(status)) {
                        item.setColour(red);
                    }
                } catch (ParseException ex) {
                    MiscUtils.getLogger().debug("EctDisplayConsultationAction: Error creating date " + ex.getMessage());
                    serviceDateStr = "Error";
                    date = null;
                }
                String url = "popupPage(700, 960,'" + winName + "','" + request.getContextPath() + "/encounter/ViewRequest?de=" + bean.demographicNo + "&requestId=" + theRequests.ids.get(idx) + "'); return false;";
                
                // Specialist names come from the same row-builder pass that already loaded the
                // service (vSpecialist), so showing them adds no per-row query.
                String referralLabel = buildReferralLabel(service, specialist);
                // LeftNavBarDisplay.jsp attribute-encodes the link title itself, so it is stored
                // raw here to avoid double encoding.
                item.setLinkTitle(buildLinkTitle(referralLabel, serviceDateStr));
                // The visible title is printed raw by LeftNavBarDisplay.jsp, so it is encoded here;
                // truncate first so the length cap can never split an HTML entity.
                item.setTitle(SafeEncode.forHtmlContent(
                        StringUtils.maxLenString(referralLabel, MAX_LEN_TITLE, CROP_LEN_TITLE, ELLIPSES)));
                item.setURL(url);
                item.setDate(date);
                Dao.addItem(item);
            }

            return true;
        }
    }

    /**
     * Builds the unencoded row label for a consultation as {@code "Service - Specialist"}.
     *
     * <p>Both parts are trimmed. A blank part, or a specialist equal to the
     * {@value #NOT_APPLICABLE} placeholder that {@code EctViewConsultationRequestsUtil} emits when
     * no specialist is linked, is omitted along with the separator, so the label never carries a
     * stray {@code " - "}.</p>
     *
     * @param service the consultation service description; may be {@code null}
     * @param specialist the specialist/referrer display name; may be {@code null}
     * @return the combined label, or an empty string when neither part is present
     */
    static String buildReferralLabel(String service, String specialist) {
        String trimmedService = service == null ? "" : service.trim();
        String trimmedSpecialist = specialist == null ? "" : specialist.trim();
        boolean hasService = !trimmedService.isEmpty();
        boolean hasSpecialist = !trimmedSpecialist.isEmpty() && !NOT_APPLICABLE.equals(trimmedSpecialist);

        if (hasService && hasSpecialist) {
            return trimmedService + " - " + trimmedSpecialist;
        }
        if (hasSpecialist) {
            return trimmedSpecialist;
        }
        return hasService ? trimmedService : "";
    }

    /**
     * Builds the unencoded hover text: the full, untruncated referral label followed by the
     * referral date, without a leading or trailing space when either part is missing.
     *
     * @param referralLabel the label from {@link #buildReferralLabel(String, String)}; may be {@code null}
     * @param serviceDateStr the locale-formatted referral date; may be {@code null}
     * @return the hover text, or an empty string when both parts are missing
     */
    static String buildLinkTitle(String referralLabel, String serviceDateStr) {
        String label = referralLabel == null ? "" : referralLabel;
        String dateText = serviceDateStr == null ? "" : serviceDateStr.trim();
        if (label.isEmpty()) {
            return dateText;
        }
        return dateText.isEmpty() ? label : label + " " + dateText;
    }

    public String getCmd() {
        return cmd;
    }
}
