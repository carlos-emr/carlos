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

import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.provider.web.CppPreferencesUIBean;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData.Prescription;
import io.github.carlos_emr.carlos.util.DateUtils;
import io.github.carlos_emr.carlos.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public class EctDisplayRx2Action extends EctDisplayAction {
    private String cmd = "Rx";

    public static final Comparator<Prescription> ACTIVE_FIRST =
        Comparator.comparingInt(drug -> isActiveDrug(drug) ? 0 : 1);

    public boolean getInfo(EctSessionBean bean, HttpServletRequest request, NavBarDisplayDAO Dao) {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "r", null)) {
            return true; //Prescription link won't show up on new CME screen.
        } else {

            //set lefthand module heading and link
            String winName = "Rx" + bean.demographicNo;
            String leftUrl = "popupPage(580,1027,'" + winName + "','" + request.getContextPath() + "/oscarRx/choosePatient.do?providerNo=" + bean.providerNo + "&demographicNo=" + bean.demographicNo + "')";
            String url = leftUrl;
            Dao.setLeftHeading(getText("oscarEncounter.NavBar.Medications"));
            Dao.setLeftURL(leftUrl);

            //set righthand link to same as left so we have visual consistency with other modules

            url += "; return false;";
            Dao.setRightURL(url);
            Dao.setRightHeadingID(cmd);  //no menu so set div id to unique id for this action

            //grab all of the diseases associated with patient and add a list item for each
            String dbFormat = "yyyy-MM-dd";
            String serviceDateStr;
            Date date;
            RxPrescriptionData prescriptData = new RxPrescriptionData();
            Prescription[] arr = prescriptData.getUniquePrescriptionsByPatient(Integer.parseInt(bean.demographicNo));

            ArrayList<Prescription> uniqueDrugs = new ArrayList<Prescription>();
            for (Prescription p : arr) uniqueDrugs.add(p);

            CppPreferencesUIBean prefsBean = new CppPreferencesUIBean(loggedInInfo.getLoggedInProviderNo());
            prefsBean.loadValues();

            // Sort active medications to the top of the list, preserving
            // relative order within each group (stable sort).
            // Lower value = sorted higher in the list (0 = active first, 1 = non-active after).
            uniqueDrugs.sort(ACTIVE_FIRST);

            long now = System.currentTimeMillis();
            long month = 1000L * 60L * 60L * 24L * 30L;
            for (Prescription drug : uniqueDrugs) {
                if (drug.isArchived())
                    continue;
                if (drug.isHideCpp()) {
                    continue;
                }

                NavBarDisplayDAO.Item item = NavBarDisplayDAO.Item();
                date = drug.getRxDate();
                serviceDateStr = DateUtils.formatDate(date, request.getLocale());

                if (prefsBean != null && "on".equals(prefsBean.getEnable())) {
                    Locale locale = request.getLocale();

                    String descr = "";
                    String title = "";

                    if (!StringUtils.isNullOrEmpty(drug.getCustomName())) {
                        descr = drug.getCustomName();
                    } else {
                        descr = drug.getBrandName();
                    }

                    if (prefsBean != null && "on".equals(prefsBean.getMedicationStartDate())) {
                        descr += " Start Date:" + DateUtils.formatDate(drug.getRxDate(), locale);
                    }
                    if (prefsBean != null && "on".equals(prefsBean.getMedicationEndDate()) && !drug.isLongTerm()) {
                        descr += " End Date:" + DateUtils.formatDate(drug.getEndDate(), locale);
                    }
                    if (prefsBean != null && "on".equals(prefsBean.getMedicationQty())) {
                        descr += " Qty:" + drug.getQuantity();
                    }
                    if (prefsBean != null && "on".equals(prefsBean.getMedicationRepeats())) {
                        descr += " Repeats:" + drug.getRepeat();
                    }

                    String tmp = "";
                    if (drug.getFullOutLine() != null)
                        tmp = drug.getFullOutLine().replaceAll(";", " ");

                    descr = "<span " + getClassColour(drug, now, month) + ">" + descr + "</span>";

                    item.setTitle(descr);
                    item.setLinkTitle(tmp + " " + serviceDateStr + " - " + drug.getEndDate());

                } else {
                    String tmp = "";
                    if (drug.getFullOutLine() != null)
                        tmp = drug.getFullOutLine().replaceAll(";", " ");

                    String strTitle = StringUtils.maxLenString(tmp, MAX_LEN_TITLE, CROP_LEN_TITLE, ELLIPSES);
                    // strTitle = "<span " + styleColor + ">" + strTitle + "</span>";
                    strTitle = "<span " + getClassColour(drug, now, month) + ">" + strTitle + "</span>";
                    item.setTitle(strTitle);
                    item.setLinkTitle(tmp + " " + serviceDateStr + " - " + drug.getEndDate());
                }

                item.setURL("return false;");
                Dao.addItem(item);
            }

            return true;
        }
    }

    String getClassColour(Prescription drug, long referenceTime, long durationToSoon) {
        StringBuilder sb = new StringBuilder("class=\"");

        if (!drug.isLongTerm() && (drug.isCurrent() && drug.getEndDate() != null && (drug.getEndDate().getTime() - referenceTime <= durationToSoon))) {
            sb.append("expireInReference ");
        }

        if (isActiveDrug(drug)) {
            sb.append("currentDrug ");
        }

        if (drug.isArchived()) {
            sb.append("archivedDrug ");
        }

        if (!drug.isLongTerm() && !drug.isCurrent()) {
            sb.append("expiredDrug ");
        }

        if (drug.isLongTerm()) {
            sb.append("longTermMed ");
        }

        if (drug.isDiscontinued()) {
            sb.append("discontinued ");
        }

        if (drug.getOutsideProviderName() != null && !drug.getOutsideProviderName().equals("")) {
            sb = new StringBuilder("class=\"");
            sb.append("external ");
        }

        String retval = sb.toString();

        if (retval.equals("class=\"")) {
            return "";
        }

        return retval.substring(0, retval.length()) + "\"";

    }


    /**
     * Determines whether a prescription is considered active for display purposes.
     *
     * <p>A drug is active if it is current and not archived, or if it is long-term.
     * This definition is shared between the medication sort order ({@link #ACTIVE_FIRST})
     * and the CSS class assignment in {@link #getClassColour}.</p>
     *
     * <p><b>Note:</b> This method does not filter archived long-term drugs — it will
     * return {@code true} for a drug that is long-term even if archived. In
     * {@link #getInfo}, archived drugs are filtered during iteration <em>after</em>
     * the {@code uniqueDrugs.sort(ACTIVE_FIRST)} call and are therefore not displayed.
     * If using this method in other contexts, additional checks (for example,
     * {@code !drug.isArchived()}) may be required to exclude archived items.</p>
     *
     * @param drug Prescription the prescription to evaluate
     * @return boolean {@code true} if the drug is considered active
     */
    public static boolean isActiveDrug(Prescription drug) {
        return (drug.isCurrent() && !drug.isArchived()) || drug.isLongTerm();
    }

    public String getCmd() {
        return cmd;
    }
}
