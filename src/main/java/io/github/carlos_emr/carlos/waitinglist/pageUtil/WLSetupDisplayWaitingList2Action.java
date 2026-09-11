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


package io.github.carlos_emr.carlos.waitinglist.pageUtil;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderPreference;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SessionConstants;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.providers.bean.ProviderNameBean;
import io.github.carlos_emr.carlos.providers.bean.ProviderNameBeanHandler;
import io.github.carlos_emr.carlos.providers.data.ProviderData;
import io.github.carlos_emr.carlos.waitinglist.bean.WLWaitingListBeanHandler;
import io.github.carlos_emr.carlos.waitinglist.bean.WLWaitingListNameBeanHandler;
import io.github.carlos_emr.carlos.waitinglist.util.WLWaitingListUtil;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Collection;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
public final class WLSetupDisplayWaitingList2Action extends ActionSupport {

    /**
     * The only shape the page's own JavaScript writes into the three selector parameters:
     * the indexed field names of one waiting-list row. The selectors are request-controlled
     * and are used as parameter NAMES for the lookup that follows, so without this check a
     * caller could point one at any other parameter, including names the packaged WAF exempts
     * by pattern for other pages (comments-&lt;n&gt;, test_&lt;n&gt;.labnotes), and have that
     * value persisted as a waiting-list note.
     */
    private static final Pattern ROW_SELECTOR =
            Pattern.compile("^waitingListBean\\[(\\d+)\\]\\.(demographicNo|note|onListSince)$");

    static boolean isRowSelector(String selector) {
        return selector != null && ROW_SELECTOR.matcher(selector).matches();
    }

    /**
     * Whether a selector names the given field of a waiting-list row. Each of the three
     * selectors has one field it may name: without this, a POST could point the demographic
     * selector at the row's note and have the note text persisted as the patient number.
     */
    static boolean isRowSelectorFor(String selector, String field) {
        return isRowSelector(selector) && selector.endsWith("." + field);
    }

    /**
     * The bracketed row index inside a valid selector name, or null when it is not one.
     * The three selectors a single update submits must all carry the SAME index: the shape
     * check alone would accept {@code waitingListBean[0].demographicNo} paired with
     * {@code waitingListBean[1].note}, and {@link #execute()} would then look up row 0's
     * patient while persisting row 1's note onto it. Callers compare this across the three
     * selectors and reject a mismatch before reading any value.
     */
    static String rowIndexOf(String selector) {
        if (selector == null) {
            return null;
        }
        Matcher matcher = ROW_SELECTOR.matcher(selector);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private Logger log = MiscUtils.getLogger();

    private final SecurityInfoManager securityInfoManager =
            SpringUtils.getBean(SecurityInfoManager.class);

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String execute()
            throws Exception {
        log.debug("WLSetupDisplayWaitingList2Action/execute(): just entering.");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)) {
            throw new SecurityException("missing required sec object (_demographic r)");
        }
        String update = request.getParameter("update");
        String remove = request.getParameter("remove"); //actually not used for now, may in future?

        // Mutation path (update=Y) requires write privilege + POST.
        if (update != null && update.equalsIgnoreCase("Y")) {
            if (!"POST".equalsIgnoreCase(request.getMethod())) {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return NONE;
            }
            if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "w", null)) {
                throw new SecurityException("missing required sec object (_demographic w)");
            }
        }

        String waitingListId = "";
        String demographicNo = "";
        String waitingListNote = "";
        String onListSince = "";
        String groupNo = "";
        String providerNo = "";

        log.debug("WLSetupDisplayWaitingList2Action/execute(): update = {}", LogSafe.sanitize(update));
        log.debug("WLSetupDisplayWaitingList2Action/execute(): remove = {}", LogSafe.sanitize(remove));

        //LazyValidatorForm wlForm = (LazyValidatorForm) form;
        log.debug("WLSetupDisplayWaitingList2Action/execute(): after  (LazyValidatorForm)form ");


        String demographicNumSelected = request.getParameter("demographicNumSelected");
        String wlNoteSelected = request.getParameter("wlNoteSelected");
        String onListSinceSelected = request.getParameter("onListSinceSelected");

        log.debug("WLSetupDisplayWaitingList2Action/execute(): demographicNumSelected = {}", LogSafe.sanitize(demographicNumSelected));
        log.debug("WLSetupDisplayWaitingList2Action/execute(): wlNoteSelected = {}", LogSafe.sanitize(wlNoteSelected));
        log.debug("WLSetupDisplayWaitingList2Action/execute(): onListSinceSelected = {}", LogSafe.sanitize(onListSinceSelected));


        String rawWaitingListId = request.getParameter("waitingListId");
        if (rawWaitingListId != null && !rawWaitingListId.trim().isEmpty()) {
            try {
                int parsedId = Integer.parseInt(rawWaitingListId.trim());
                if (parsedId > 0) {
                    waitingListId = String.valueOf(parsedId);
                } else {
                    log.warn("WLSetupDisplayWaitingList2Action/execute(): invalid waitingListId '{}': must be a positive integer", LogSafe.sanitize(rawWaitingListId)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
                }
            } catch (NumberFormatException e) {
                log.warn("WLSetupDisplayWaitingList2Action/execute(): invalid waitingListId '{}': not a valid integer", LogSafe.sanitize(rawWaitingListId)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            }
        }

        log.debug("WLSetupDisplayWaitingList2Action/execute(): waitingListId = {}", LogSafe.sanitize(waitingListId));
        if (update != null && update.equalsIgnoreCase("Y")) {
            // The page copies the clicked row's list id into waitingListId before it submits
            // update=Y, so an update without a usable id is malformed. The local defaults to ""
            // and the parser above leaves it there for a missing, non-numeric or non-positive
            // value, so the null check the legacy code kept around the mutation could never skip
            // it: rePositionWaitingList("") and updateWaitingListRecord("", ...) were reachable.
            if (isBlank(waitingListId)) {
                log.warn("WLSetupDisplayWaitingList2Action/execute(): rejected update without a valid waitingListId"); // NOSONAR javasecurity:S5145 — fixed text, no request data
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
            }
            // The page's update button submits the three selectors only when a row's note or
            // date field has been edited (setParameters() fills them on blur); clicking update
            // without editing a row leaves them blank and means "reposition", handled by the
            // fallback below. So the selector validation applies only when a selector is present.
            boolean anySelector = !isBlank(demographicNumSelected) || !isBlank(wlNoteSelected)
                    || !isBlank(onListSinceSelected);
            if (anySelector) {
                if (!isRowSelectorFor(demographicNumSelected, "demographicNo")
                        || !isRowSelectorFor(wlNoteSelected, "note")
                        || !isRowSelectorFor(onListSinceSelected, "onListSince")) {
                    log.warn("WLSetupDisplayWaitingList2Action/execute(): rejected row selector outside waitingListBean[n].<its field>"); // NOSONAR javasecurity:S5145 — fixed text, no request data
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return NONE;
                }
                // All three selectors must name the SAME row. Otherwise a crafted POST could pair
                // one row's demographicNo with another row's note, and updateWaitingListRecord
                // would persist the second row's note against the first row's patient.
                String selectorRow = rowIndexOf(demographicNumSelected);
                if (!selectorRow.equals(rowIndexOf(wlNoteSelected))
                        || !selectorRow.equals(rowIndexOf(onListSinceSelected))) {
                    log.warn("WLSetupDisplayWaitingList2Action/execute(): rejected selectors spanning more than one waiting-list row"); // NOSONAR javasecurity:S5145 — fixed text, no request data
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return NONE;
                }

                demographicNo = request.getParameter(demographicNumSelected);
                waitingListNote = request.getParameter(wlNoteSelected);
                onListSince = request.getParameter(onListSinceSelected);
            }
//	        demographicNo = (String)wlForm.get(demographicNumSelected);
//	        waitingListNote = (String)wlForm.get(wlNoteSelected);
//	        onListSince =  (String)wlForm.get(onListSinceSelected);

            /*if (waitingListId == null && wlForm.get("selectedWL") != null) {
                waitingListId = (String) wlForm.get("selectedWL");
            }*/

            try {
                // A selected row is updated even when its note is empty: clearing the note
                // is an edit, and treating the empty box as "no row selected" left the old
                // note on the record. The date stays required, since updateWaitingListRecord
                // would silently replace a blank one with today.
                if (anySelector && !isBlank(demographicNo) && !isBlank(onListSince)) {
                    WLWaitingListUtil.updateWaitingListRecord(waitingListId,
                            waitingListNote == null ? "" : waitingListNote, demographicNo, onListSince);
                } else {
                    WLWaitingListUtil.rePositionWaitingList(waitingListId);
                }

            } catch (Exception ex) {
                log.error("WLSetupDisplayWaitingList2Action/execute(): Exception: ", ex);
                return "failure";
            }
        }//end of if ( !update.equalsIgnoreCase("Y") ) -- could be remove also ???

        HttpSession session = request.getSession();

        ProviderPreference providerPreference = (ProviderPreference) session.getAttribute(SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE);

        if (providerPreference.getMyGroupNo() != null) {
            groupNo = providerPreference.getMyGroupNo();
        }
        providerNo = (String) session.getAttribute("user");

        log.debug("WLSetupDisplayWaitingList2Action/execute(): providerNo = {}", LogSafe.sanitize(providerNo));
        log.debug("WLSetupDisplayWaitingList2Action/execute(): groupno = {}", LogSafe.sanitize(groupNo));

        log.debug("WLSetupDisplayWaitingList2Action/execute(): waitingListId = {}", LogSafe.sanitize(waitingListId));
        log.debug("WLSetupDisplayWaitingList2Action/execute(): demographicNo = {}", LogSafe.sanitize(demographicNo));
        log.debug("WLSetupDisplayWaitingList2Action/execute(): waitingListNote = {}", LogSafe.sanitize(waitingListNote));
        log.debug("WLSetupDisplayWaitingList2Action/execute(): onListSince = {}", LogSafe.sanitize(onListSince));

        WLWaitingListBeanHandler hd = null;
        WLWaitingListNameBeanHandler wlNameHd = null;
        Collection allProviders = null;
        String nbPatients = "";
        String today = "";

        if (waitingListId != null && waitingListId.length() > 0) {
            hd = new WLWaitingListBeanHandler(waitingListId);
        } else {
            //even though waitingListId is null, still need to create hd for hd.getWaitingListArrayList()
            // to display in DisplayWaitingList.jsp
            hd = new WLWaitingListBeanHandler(waitingListId);
        }

        if (groupNo != null && providerNo != null) {
            wlNameHd = new WLWaitingListNameBeanHandler(groupNo, providerNo);
        }
        ProviderNameBeanHandler phd = new ProviderNameBeanHandler();


        if (groupNo != null) {
            phd.setThisGroupProviderVector(groupNo);
            allProviders = phd.getThisGroupProviderVector();
            if (allProviders.size() == 0 && groupNo.equals(".default")) {
                Provider p = loggedInInfo.getLoggedInProvider();
                ProviderNameBean pNameBean = new ProviderNameBean(p.getFormattedName(), p.getProviderNo());
                allProviders.add(pNameBean);
            }
            log.debug("WLSetupDisplayWaitingList2Action/execute(): allProviders.size() = {}", allProviders.size());
            if (allProviders.size() <= 0) {
                ProviderData proData = new ProviderData();
                proData.getProvider(groupNo);
                if (proData.getLast_name() != null && !proData.getLast_name().equals("") && proData.getFirst_name() != null && !proData.getFirst_name().equals("")) {
                    ProviderNameBean proNameBean = new ProviderNameBean(proData.getLast_name() + ", " + proData.getFirst_name(), groupNo);
                    allProviders.add(proNameBean);
                }
            }

            if (hd != null) {
                nbPatients = Integer.toString(hd.getWaitingList().size());
            } else {
                nbPatients = "0";
            }

        }

        today = UtilDateUtilities.DateToString(new Date(), "yyyy-MM-dd");

        request.setAttribute("WLId", waitingListId);
        session.setAttribute("waitingList", hd); // nosemgrep: tainted-session-from-http-request -- DAO-sourced WLWaitingListBeanHandler built from validated waitingListId
        if (hd != null) {
            session.setAttribute("waitingListName", hd.getWaitingListName()); // nosemgrep: tainted-session-from-http-request -- getter on DAO-sourced waiting list bean
        } else {
            session.setAttribute("waitingListName", null); // nosemgrep: tainted-session-from-http-request -- null literal, no tainted data
        }
        if (wlNameHd != null) {
            session.setAttribute("waitingListNames", wlNameHd.getWaitingListNames()); // nosemgrep: tainted-session-from-http-request -- DAO-sourced list from WLWaitingListNameBeanHandler
        } else {
            session.setAttribute("waitingListNames", null); // nosemgrep: tainted-session-from-http-request -- null literal, no tainted data
        }
        session.setAttribute("allProviders", allProviders); // nosemgrep: tainted-session-from-http-request -- DAO-sourced provider list from WaitingListManager

        session.setAttribute("nbPatients", nbPatients); // nosemgrep: tainted-session-from-http-request -- string count derived from DAO query result size

        //session.setAttribute("allWaitingListName", allWaitingListName);
        session.setAttribute("today", today); // nosemgrep: tainted-session-from-http-request -- server-generated date string from new Date()

        return "continue";
    }

    private String selectedWL;

    public String getSelectedWL() {
        return selectedWL;
    }

    @StrutsParameter
    public void setSelectedWL(String selectedWL) {
        this.selectedWL = selectedWL;
    }
}
