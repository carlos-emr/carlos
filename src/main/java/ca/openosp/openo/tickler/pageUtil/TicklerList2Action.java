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
package ca.openosp.openo.tickler.pageUtil;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts2.ServletActionContext;
import org.owasp.encoder.Encode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opensymphony.xwork2.ActionSupport;

import ca.openosp.OscarProperties;
import ca.openosp.openo.commn.dao.TicklerLinkDao;
import ca.openosp.openo.commn.model.CustomFilter;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.commn.model.Tickler;
import ca.openosp.openo.commn.model.TicklerComment;
import ca.openosp.openo.commn.model.TicklerLink;
import ca.openosp.openo.lab.ca.on.LabResultData;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.managers.TicklerManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

/**
 * Struts2 action that returns paginated tickler data as JSON for DataTables
 * server-side processing. Replaces the client-side rendering loop in
 * ticklerMain.jsp to avoid loading all ticklers into memory at once.
 *
 * @since 2024-01-01
 */
public class TicklerList2Action extends ActionSupport {

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);
    private TicklerLinkDao ticklerLinkDao = SpringUtils.getBean(TicklerLinkDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Handles DataTables server-side processing requests. Accepts standard
     * DataTables parameters (draw, start, length) plus tickler filter parameters.
     * Returns JSON with pre-rendered HTML cell content.
     *
     * @return null since the response is written directly
     * @throws IOException if writing the JSON response fails
     */
    public String execute() throws IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", "r", null)) {
            throw new SecurityException("missing required sec object (_tickler)");
        }

        int draw = parseIntParam("draw", 1);
        int start = parseIntParam("start", 0);
        int length = parseIntParam("length", 50);
        boolean showAll = (length <= 0);

        String ticklerview = getStringParam("ticklerview", "A");
        String providerview = getStringParam("providerview", "all");
        String assignedTo = getStringParam("assignedTo", "all");
        String mrpview = getStringParam("mrpview", "all");
        String dateBegin = getStringParam("xml_vdate", "1950-01-01");
        String dateEnd = getStringParam("xml_appointment_date", "");
        String demographicNo = getStringParam("demographic_no", "0");

        String userNo = (String) request.getSession().getAttribute("user");
        Locale locale = request.getLocale();
        String contextPath = request.getContextPath();

        CustomFilter filter = new CustomFilter();
        filter.setPriority(null);
        filter.setStatus(ticklerview);
        filter.setStartDateWeb(dateBegin);
        filter.setEndDateWeb(dateEnd);
        filter.setPriority(null);

        if (!mrpview.isEmpty() && !"all".equals(mrpview)) {
            filter.setMrp(mrpview);
        }
        if (!providerview.isEmpty() && !"all".equals(providerview)) {
            filter.setProvider(providerview);
        }
        if (!assignedTo.isEmpty() && !"all".equals(assignedTo)) {
            filter.setAssignee(assignedTo);
        }

        filter.setSort_order("desc");

        int targetDemographic = 0;
        try {
            targetDemographic = Integer.parseInt(demographicNo);
        } catch (NumberFormatException e) {
            // default to 0
        }

        int totalRecords;
        List<Tickler> ticklers;

        if (targetDemographic > 0) {
            ticklers = ticklerManager.search_tickler_bydemo(loggedInInfo, targetDemographic,
                    ticklerview, filter.getStartDate(), filter.getEndDate());
            totalRecords = ticklers.size();
            if (!showAll) {
                int end = Math.min(start + length, ticklers.size());
                if (start < ticklers.size()) {
                    ticklers = ticklers.subList(start, end);
                } else {
                    ticklers = java.util.Collections.emptyList();
                }
            }
        } else {
            totalRecords = ticklerManager.getNumTicklers(loggedInInfo, filter);
            if (showAll) {
                // Use non-paginated method for "Show All" (like original JSP)
                ticklers = ticklerManager.getTicklers(loggedInInfo, filter);
            } else {
                ticklers = ticklerManager.getTicklers(loggedInInfo, filter, start, length);
            }
        }

        String numDaysUntilWarn = OscarProperties.getInstance().getProperty("tickler_warn_period");
        if (numDaysUntilWarn == null || numDaysUntilWarn.isEmpty()) {
            numDaysUntilWarn = "0";
        }
        long ticklerWarnDays = Long.parseLong(numDaysUntilWarn);
        boolean ignoreWarning = (ticklerWarnDays <= 0);

        DateFormat datetimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale);
        DateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd", locale);
        DateFormat timeOnlyFormat = new SimpleDateFormat("HH:mm:ss", locale);

        ArrayNode dataArray = objectMapper.createArrayNode();

        for (Tickler tickler : ticklers) {
            Demographic demo = tickler.getDemographic();
            LocalDateTime serviceDate = tickler.getServiceDate().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDateTime();
            LocalDateTime currentDate = LocalDateTime.now();
            long daysDifference = Duration.between(serviceDate, currentDate).toDays();
            boolean warning = !ignoreWarning && (daysDifference >= ticklerWarnDays);

            ArrayNode row = objectMapper.createArrayNode();

            // Col 0: Checkbox
            row.add("<input type=\"checkbox\" name=\"checkbox\" value=\""
                    + tickler.getId() + "\" class=\"noprint\">");

            // Col 1: Edit icon
            row.add("<a href=\"javascript:void(0)\" title=\"Edit Tickler\" "
                    + "onClick=\"window.open('" + contextPath
                    + "/tickler/ticklerEdit.jsp?tickler_no=" + tickler.getId()
                    + "', 'edit_tickler', 'width=800, height=650')\">"
                    + "<span class=\"glyphicon glyphicon-pencil\"></span></a>");

            // Col 2: Demographic name link
            String demoLastName = "";
            String demoFirstName = "";
            int demoNo = 0;
            if (demo != null) {
                demoLastName = Encode.forHtml(demo.getLastName());
                demoFirstName = Encode.forHtml(demo.getFirstName());
                demoNo = demo.getDemographicNo();
            }
            row.add("<a href=\"javascript:void(0)\" "
                    + "onClick=\"popupPage(600,800,'" + contextPath
                    + "/demographic/demographiccontrol.jsp?demographic_no=" + demoNo
                    + "&amp;displaymode=edit&amp;dboperation=search_detail')\">"
                    + demoLastName + "," + demoFirstName + "</a>");

            // Col 3: Creator
            Provider creator = tickler.getProvider();
            row.add(creator == null ? "N/A" : Encode.forHtml(creator.getFormattedName()));

            // Col 4: Service date
            row.add(dateOnlyFormat.format(tickler.getServiceDate()));

            // Col 5: Creation date
            row.add(datetimeFormat.format(tickler.getCreateDate()));

            // Col 6: Priority
            row.add(String.valueOf(tickler.getPriority()));

            // Col 7: Assignee
            Provider assignee = tickler.getAssignee();
            if (assignee != null) {
                row.add(Encode.forHtml(assignee.getLastName() + ", " + assignee.getFirstName()));
            } else {
                row.add("N/A");
            }

            // Col 8: Status
            row.add(tickler.getStatusDesc(locale));

            // Col 9: Message + attachment links
            StringBuilder msgCell = new StringBuilder();
            msgCell.append("<span style=\"white-space:pre-wrap\">")
                    .append(Encode.forHtml(tickler.getMessage()))
                    .append("</span>");

            List<TicklerLink> linkList = ticklerLinkDao.getLinkByTickler(tickler.getId().intValue());
            if (linkList != null) {
                for (TicklerLink tl : linkList) {
                    String type = tl.getTableName();
                    msgCell.append(buildAttachmentLink(type, tl.getTableId(), userNo, contextPath));
                }
            }
            row.add(msgCell.toString());

            // Col 10: Note dialog icon
            row.add("<a href=\"javascript:void(0)\" class=\"noteDialogLink noprint\" "
                    + "onClick=\"openNoteDialog('" + demoNo + "','" + tickler.getId() + "')\" "
                    + "title=\"Add Encounter Note\">"
                    + "<span class=\"glyphicon glyphicon-comment\"></span></a>");

            // Col 11: Hidden group column (tickler ID)
            row.add(String.valueOf(tickler.getId()));

            // Col 12: Warning flag for row CSS
            row.add(warning ? "true" : "false");

            dataArray.add(row);

            // Comment rows
            Set<TicklerComment> tcomments = tickler.getComments();
            if (tcomments != null) {
                for (TicklerComment tc : tcomments) {
                    ArrayNode commentRow = objectMapper.createArrayNode();

                    // Col 0: Empty checkbox
                    commentRow.add("");
                    // Col 1: Empty edit
                    commentRow.add("");

                    // Col 2: Demographic name (transparent via CSS)
                    commentRow.add(demoLastName + "," + demoFirstName);

                    // Col 3: Comment author
                    Provider commentProvider = tc.getProvider();
                    String formattedName = "";
                    if (commentProvider != null) {
                        formattedName = Encode.forHtml(commentProvider.getFormattedName());
                    }
                    commentRow.add(formattedName);

                    // Col 4: Service date (for sorting alignment)
                    commentRow.add(dateOnlyFormat.format(tickler.getServiceDate()));

                    // Col 5: Comment date
                    if (tc.isUpdateDateToday()) {
                        commentRow.add(timeOnlyFormat.format(tc.getUpdateDate()));
                    } else {
                        commentRow.add(datetimeFormat.format(tc.getUpdateDate()));
                    }

                    // Col 6: Priority (for sorting alignment)
                    commentRow.add(String.valueOf(tickler.getPriority()));

                    // Col 7: Empty assignee
                    commentRow.add("");
                    // Col 8: Empty status
                    commentRow.add("");

                    // Col 9: Comment message
                    commentRow.add("<span style=\"white-space:pre-wrap\">"
                            + Encode.forHtml(tc.getMessage()) + "</span>");

                    // Col 10: Empty note icon
                    commentRow.add("");

                    // Col 11: Group column (parent tickler ID)
                    commentRow.add(String.valueOf(tickler.getId()));

                    // Col 12: Not a warning row
                    commentRow.add("comment");

                    dataArray.add(commentRow);
                }
            }
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.put("draw", draw);
        result.put("recordsTotal", totalRecords);
        result.put("recordsFiltered", totalRecords);
        result.set("data", dataArray);

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(result.toString());

        return null;
    }

    /**
     * Builds an HTML attachment link based on the lab result type.
     *
     * @param type        the table name indicating the lab result type
     * @param tableId     the ID of the linked record
     * @param userNo      the current user's provider number
     * @param contextPath the servlet context path
     * @return HTML string for the attachment paperclip icon link
     */
    private String buildAttachmentLink(String type, Long tableId, String userNo, String contextPath) {
        String href;
        if (LabResultData.isMDS(type)) {
            href = "javascript:reportWindow('SegmentDisplay.jsp?segmentID=" + tableId
                    + "&providerNo=" + Encode.forUriComponent(userNo)
                    + "&searchProviderNo=" + Encode.forUriComponent(userNo) + "&status=')";
        } else if (LabResultData.isCML(type)) {
            href = "javascript:reportWindow('" + contextPath
                    + "/lab/CA/ON/CMLDisplay.jsp?segmentID=" + tableId
                    + "&providerNo=" + Encode.forUriComponent(userNo)
                    + "&searchProviderNo=" + Encode.forUriComponent(userNo) + "&status=')";
        } else if (LabResultData.isHL7TEXT(type)) {
            href = "javascript:reportWindow('" + contextPath
                    + "/lab/CA/ALL/labDisplay.jsp?segmentID=" + tableId
                    + "&providerNo=" + Encode.forUriComponent(userNo)
                    + "&searchProviderNo=" + Encode.forUriComponent(userNo) + "&status=')";
        } else if (LabResultData.isDocument(type)) {
            href = "javascript:reportWindow('" + contextPath
                    + "/documentManager/ManageDocument.do?method=display&doc_no=" + tableId
                    + "&providerNo=" + Encode.forUriComponent(userNo)
                    + "&searchProviderNo=" + Encode.forUriComponent(userNo) + "&status=')";
        } else if (LabResultData.isHRM(type)) {
            href = "javascript:reportWindow('" + contextPath
                    + "/hospitalReportManager/Display.do?id=" + tableId
                    + "&segmentID=" + tableId + "')";
        } else {
            href = "javascript:reportWindow('" + contextPath
                    + "/lab/CA/BC/labDisplay.jsp?segmentID=" + tableId
                    + "&providerNo=" + Encode.forUriComponent(userNo)
                    + "&searchProviderNo=" + Encode.forUriComponent(userNo) + "&status=')";
        }
        return " <a title=\"View attachment\" href=\"" + href
                + "\"><i class=\"glyphicon glyphicon-paperclip\"></i></a>";
    }

    private int parseIntParam(String name, int defaultValue) {
        String val = request.getParameter(name);
        if (val == null || val.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String getStringParam(String name, String defaultValue) {
        String val = request.getParameter(name);
        if (val == null || val.isEmpty()) {
            return defaultValue;
        }
        return val;
    }
}
