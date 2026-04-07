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

import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.prevention.Prevention;
import io.github.carlos_emr.carlos.prevention.PreventionDS;
import io.github.carlos_emr.carlos.prevention.PreventionData;
import io.github.carlos_emr.carlos.prevention.PreventionDisplayConfig;
import io.github.carlos_emr.carlos.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

public class EctDisplayPrevention2Action extends EctDisplayAction {
    private static final String cmd = "preventions";

    // Unicode status prefix characters for the prevention sidebar
    private static final String PREFIX_CHECK = "\u2713 ";      // ✓ up-to-date / completed externally
    private static final String PREFIX_X = "\u2717 ";           // ✗ declined
    private static final String PREFIX_DASH = "\u2013 ";        // – ineligible
    private static final String PREFIX_HOURGLASS = "\u23F3 ";   // ⏳ pending
    private static final String PREFIX_CIRCLE = "\u25CB ";      // ○ not documented
    private static final String PREFIX_WARNING = "\u26A0 ";     // ⚠ due/overdue

    // Colour constants for prevention status indicators
    private static final String COLOUR_HIGHLIGHT = "#FF0000";
    private static final String COLOUR_INELIGIBLE = "#FF6600";
    private static final String COLOUR_PENDING = "#FF00FF";
    private static final String COLOUR_UP_TO_DATE = "#009900";
    private static final String COLOUR_NOT_DOCUMENTED = "#999999";

    public boolean getInfo(EctSessionBean bean, HttpServletRequest request, NavBarDisplayDAO Dao) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_prevention", "r", null)) {
            return true; //Prevention link won't show up on new CME screen.
        } else {

            //set lefthand module heading and link
            String winName = "prevention" + bean.demographicNo;
            int demographicNumber = Integer.valueOf(bean.demographicNo);
            String preventionPath = request.getContextPath() + "/oscarPrevention/index.jsp?demographic_no=" + bean.demographicNo;
            Dao.setLeftHeading(getText("encounter.LeftNavBar.Prevent"));
            Dao.setLeftPopup(700, 960, winName, preventionPath);

            //set righthand link to same as left so we have visual consistency with other modules
            Dao.setRightPopup(700, 960, winName, preventionPath);
            Dao.setRightHeadingID(cmd);  //no menu so set div id to unique id for this action

            //list warnings first as module items
            Prevention p = PreventionData.getPrevention(loggedInInfo, Integer.valueOf(bean.demographicNo));
            PreventionDS pf = SpringUtils.getBean(PreventionDS.class); //PreventionDS.getInstance();

            try {
                pf.getMessages(p);
            } catch (Exception dsException) {
                return false;
            }

            //now we list prevention modules as items
            PreventionDisplayConfig pdc = PreventionDisplayConfig.getInstance();
            ArrayList<HashMap<String, String>> prevList = pdc.getPreventions();
            Map warningTable = p.getWarningMsgs();

            Date date = null;

            String url = "popupPage(700, 960,'" + winName + "','" + preventionPath + "');return false;; return false;";
            ArrayList<NavBarDisplayDAO.Item> warnings = new ArrayList<NavBarDisplayDAO.Item>();
            ArrayList<NavBarDisplayDAO.Item> items = new ArrayList<NavBarDisplayDAO.Item>();
            String result;

            for (int i = 0; i < prevList.size(); i++) {
                NavBarDisplayDAO.Item item = NavBarDisplayDAO.Item();
                HashMap<String, String> h = prevList.get(i);
                String prevName = h.get("name");
                ArrayList<Map<String, Object>> alist = PreventionData.getPreventionData(loggedInInfo, prevName, demographicNumber);

                boolean show = pdc.display(loggedInInfo, h, bean.demographicNo, alist.size());
                if (show) {
                    String prefix;
                    String colour;

                    if (alist.size() > 0) {
                        Map<String, Object> hdata = alist.get(alist.size() - 1);
                        Map<String, String> hExt = PreventionData.getPreventionKeyValues((String) hdata.get("id"));
                        result = hExt.get("result");
                        String refused = (String) hdata.get("refused");

                        Object dateObj = hdata.get("prevention_date_asDate");
                        if (dateObj instanceof Date) {
                            date = (Date) dateObj;
                        } else if (dateObj instanceof java.util.GregorianCalendar) {
                            Calendar cal = (Calendar) dateObj;
                            date = cal.getTime();
                        }

                        item.setDate(date);

                        // Default for items with records: up-to-date
                        prefix = PREFIX_CHECK;
                        colour = COLOUR_UP_TO_DATE;

                        if ("1".equals(refused)) {
                            prefix = PREFIX_X;
                            colour = COLOUR_INELIGIBLE;
                        } else if ("2".equals(refused)) {
                            prefix = PREFIX_DASH;
                            colour = COLOUR_INELIGIBLE;
                        } else if (result != null && result.equalsIgnoreCase("pending")) {
                            prefix = PREFIX_HOURGLASS;
                            colour = COLOUR_PENDING;
                        }

                    } else {
                        item.setDate(null);
                        prefix = PREFIX_CIRCLE;
                        colour = COLOUR_NOT_DOCUMENTED;
                    }

                    boolean isWarning = warningTable.containsKey(prevName);
                    if (isWarning) {
                        prefix = PREFIX_WARNING;
                        colour = COLOUR_HIGHLIGHT;
                    }

                    String title = StringUtils.maxLenString(h.get("name"), MAX_LEN_TITLE, CROP_LEN_TITLE, ELLIPSES);
                    item.setTitle(prefix + title);
                    item.setColour(colour);
                    item.setLinkTitle(h.get("desc"));
                    item.setURL(url);

                    if (isWarning) {
                        warnings.add(item);
                    } else {
                        items.add(item);
                    }
                }
            }

            //sort items without warnings chronologically
            Dao.sortItems(items, NavBarDisplayDAO.DATESORT_ASC);

            //add warnings to Dao array first so they will be at top of list
            for (int idx = 0; idx < warnings.size(); ++idx) {
                Dao.addItem(warnings.get(idx));
            }

            //now copy remaining sorted items
            for (int idx = 0; idx < items.size(); ++idx) {
                Dao.addItem(items.get(idx));
            }

            return true;
        }
    }

    public String getCmd() {
        return cmd;
    }

}
