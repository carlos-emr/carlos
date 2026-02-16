//CHECKSTYLE:OFF
/**
 * Copyright (c) 2026. Magenta Health. All Rights Reserved.
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
 *
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 *
 * <p>
 * Ported from openo-beta/Open-O PR #2268 by LiamStanziani.
 * Original: ca.openosp.openo.tickler.pageUtil.TicklerList2Action
 */
package io.github.carlos_emr.carlos.tickler.pageUtil;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts2.ServletActionContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opensymphony.xwork2.ActionSupport;

import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.commn.model.CustomFilter;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.tickler.dto.TicklerCommentDTO;
import io.github.carlos_emr.carlos.tickler.dto.TicklerLinkDTO;
import io.github.carlos_emr.carlos.tickler.dto.TicklerListDTO;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Struts2 action that returns paginated tickler data as JSON for DataTables
 * server-side processing. Returns raw data objects; HTML rendering is handled
 * by client-side DataTables column render functions.
 *
 * @since 2026-02-05
 */
public class TicklerList2Action extends ActionSupport {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_PAGE_SIZE = 500;

    private TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Handles DataTables server-side processing requests. Accepts standard
     * DataTables parameters (draw, start, length) plus tickler filter parameters.
     * Returns JSON with raw data fields for client-side rendering.
     *
     * @return null since the response is written directly
     * @throws IOException if writing the JSON response fails
     */
    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", "r", null)) {
            writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Access denied");
            return null;
        }

        int draw = parseIntParam(request, "draw", 1);
        int start = Math.max(0, parseIntParam(request, "start", 0));
        int length = parseIntParam(request, "length", 50);
        if (length > 0) {
            length = Math.min(length, MAX_PAGE_SIZE);
        }

        Locale locale = request.getLocale();

        CustomFilter filter;
        try {
            filter = buildFilterFromRequest(request);
        } catch (IllegalArgumentException e) {
            writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid date format, use yyyy-MM-dd");
            return null;
        }

        int totalRecords = ticklerManager.getNumTicklers(loggedInInfo, filter);

        List<TicklerListDTO> ticklers;
        if (length <= 0) {
            ticklers = ticklerManager.getTicklerDTOs(loggedInInfo, filter, 0, 0);
        } else {
            ticklers = ticklerManager.getTicklerDTOs(loggedInInfo, filter, start, length);
        }

        LogAction.addLogSynchronous(loggedInInfo, "TicklerList2Action.execute",
                "ticklers=" + ticklers.size() + ",total=" + totalRecords);

        long ticklerWarnDays = getTicklerWarnDays();
        DateTimeFormatter datetimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", locale);
        DateTimeFormatter dateOnlyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", locale);
        DateTimeFormatter timeOnlyFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", locale);

        ArrayNode dataArray = objectMapper.createArrayNode();
        ObjectNode commentsMap = objectMapper.createObjectNode();

        for (TicklerListDTO tickler : ticklers) {
            boolean warning = isWarning(tickler.getServiceDate(), ticklerWarnDays);
            dataArray.add(buildTicklerRow(tickler, warning, datetimeFormat, dateOnlyFormat, locale));

            List<TicklerCommentDTO> tcomments = tickler.getComments();
            if (tcomments != null && !tcomments.isEmpty()) {
                commentsMap.set(String.valueOf(tickler.getId()),
                        buildCommentsArray(tcomments, datetimeFormat, timeOnlyFormat));
            }
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.put("draw", draw);
        result.put("recordsTotal", totalRecords);
        result.put("recordsFiltered", totalRecords);
        result.set("data", dataArray);
        result.set("comments", commentsMap);

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(result.toString());

        return null;
    }

    /**
     * Checks whether a tickler's service date exceeds the configured warning period.
     *
     * @param serviceDate Date the tickler service date
     * @param warnDays long the warning threshold in days, 0 or negative disables warnings
     * @return boolean true if the service date is past the warning threshold
     */
    private boolean isWarning(java.util.Date serviceDate, long warnDays) {
        if (serviceDate == null || warnDays <= 0) {
            return false;
        }
        LocalDateTime service = serviceDate.toInstant()
                .atZone(ZoneId.systemDefault()).toLocalDateTime();
        long daysDifference = Duration.between(service, LocalDateTime.now()).toDays();
        return daysDifference >= warnDays;
    }

    /**
     * Builds a JSON object node representing a single tickler data row.
     *
     * @param tickler TicklerListDTO the tickler data
     * @param warning boolean whether this tickler has triggered a warning
     * @param datetimeFormat DateFormat for full datetime display
     * @param dateOnlyFormat DateFormat for date-only display
     * @param locale Locale for localized status text
     * @return ObjectNode the JSON row
     */
    private ObjectNode buildTicklerRow(TicklerListDTO tickler, boolean warning,
                                       DateFormat datetimeFormat, DateFormat dateOnlyFormat,
                                       Locale locale) {
        ObjectNode row = objectMapper.createObjectNode();
        row.put("id", tickler.getId());
        row.put("demoNo", tickler.getDemographicNo());
        row.put("demoLastName", tickler.getDemographicLastName());
        row.put("demoFirstName", tickler.getDemographicFirstName());
        row.put("creator", tickler.getCreatorFormattedName());
        row.put("serviceDate",
                tickler.getServiceDate() != null ? dateOnlyFormat.format(tickler.getServiceDate()) : "");
        row.put("createDate",
                tickler.getCreateDate() != null ? datetimeFormat.format(tickler.getCreateDate()) : "");
        row.put("priority", String.valueOf(tickler.getPriority()));
        row.put("assignee", tickler.getAssigneeFormattedName());
        row.put("status", tickler.getStatusDesc(locale));
        row.put("message", tickler.getMessage());
        row.put("warning", warning);

        ArrayNode linksArray = objectMapper.createArrayNode();
        List<TicklerLinkDTO> linkList = tickler.getLinks();
        if (linkList != null) {
            for (TicklerLinkDTO tl : linkList) {
                ObjectNode linkNode = objectMapper.createObjectNode();
                linkNode.put("tableName", tl.getTableName());
                linkNode.put("tableId", tl.getTableId());
                linksArray.add(linkNode);
            }
        }
        row.set("links", linksArray);

        return row;
    }

    /**
     * Builds a JSON array of comment objects for a tickler.
     *
     * @param comments List of TicklerCommentDTO the comments to serialize
     * @param datetimeFormat DateFormat for full datetime display
     * @param timeOnlyFormat DateFormat for time-only display (used for today's comments)
     * @return ArrayNode the JSON array of comments
     */
    private ArrayNode buildCommentsArray(List<TicklerCommentDTO> comments,
                                         DateFormat datetimeFormat, DateFormat timeOnlyFormat) {
        ArrayNode commentArray = objectMapper.createArrayNode();
        for (TicklerCommentDTO tc : comments) {
            ObjectNode commentObj = objectMapper.createObjectNode();
            commentObj.put("creator", tc.getProviderFormattedName());
            if (tc.getUpdateDate() == null) {
                commentObj.put("createDate", "");
            } else if (tc.isUpdateDateToday()) {
                commentObj.put("createDate", timeOnlyFormat.format(tc.getUpdateDate()));
            } else {
                commentObj.put("createDate", datetimeFormat.format(tc.getUpdateDate()));
            }
            commentObj.put("message", tc.getMessage());
            commentArray.add(commentObj);
        }
        return commentArray;
    }

    /**
     * Parses request parameters and constructs a CustomFilter for the tickler query.
     * Handles both general and demographic-specific filtering through a single path.
     *
     * @param request HttpServletRequest the current request
     * @return CustomFilter populated from request parameters
     * @throws IllegalArgumentException if date parameters are in an invalid format
     */
    private CustomFilter buildFilterFromRequest(HttpServletRequest request) {
        String ticklerview = getStringParam(request, "ticklerview", "A");
        String providerview = getStringParam(request, "providerview", "all");
        String assignedTo = getStringParam(request, "assignedTo", "all");
        String mrpview = getStringParam(request, "mrpview", "all");
        String dateBegin = getStringParam(request, "xml_vdate", "1950-01-01");
        String dateEnd = getStringParam(request, "xml_appointment_date", "");
        int targetDemographic = parseIntParam(request, "demographic_no", 0);

        if (targetDemographic > 0) {
            if (dateEnd.isEmpty()) {
                dateEnd = "8888-12-31";
            }
        } else {
            if (dateEnd.isEmpty()) {
                dateEnd = new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
            }
        }

        CustomFilter filter = new CustomFilter();
        filter.setPriority(null);
        filter.setStatus(ticklerview);
        filter.setStartDateWeb(dateBegin);
        filter.setEndDateWeb(dateEnd);

        if (targetDemographic > 0) {
            filter.setDemographicNo(String.valueOf(targetDemographic));
            filter.setMrp(null);
            filter.setProvider(null);
            filter.setAssignee(null);
        } else {
            if (!mrpview.isEmpty() && !"all".equals(mrpview)) {
                filter.setMrp(mrpview);
            }
            if (!providerview.isEmpty() && !"all".equals(providerview)) {
                filter.setProvider(providerview);
            }
            if (!assignedTo.isEmpty() && !"all".equals(assignedTo)) {
                filter.setAssignee(assignedTo);
            }
        }

        String sortDir = getStringParam(request, "order[0][dir]", "desc");
        if (!"asc".equalsIgnoreCase(sortDir)) {
            sortDir = "desc";
        }
        filter.setSort_order(sortDir);
        return filter;
    }

    /**
     * Writes a JSON error response with the given HTTP status code.
     *
     * @param response HttpServletResponse the response to write to
     * @param statusCode int the HTTP status code
     * @param message String the error message
     * @throws IOException if writing fails
     */
    private void writeJsonError(HttpServletResponse response, int statusCode,
                                String message) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ObjectNode error = objectMapper.createObjectNode();
        error.put("error", message);
        response.getWriter().write(error.toString());
    }

    /**
     * Reads the tickler warning period from application properties.
     *
     * @return the number of days after which a tickler triggers a warning, or 0 if not configured
     */
    private long getTicklerWarnDays() {
        String numDaysUntilWarn = OscarProperties.getInstance().getProperty("tickler_warn_period");
        if (numDaysUntilWarn == null || numDaysUntilWarn.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(numDaysUntilWarn);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int parseIntParam(HttpServletRequest request, String name, int defaultValue) {
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

    private String getStringParam(HttpServletRequest request, String name, String defaultValue) {
        String val = request.getParameter(name);
        if (val == null || val.isEmpty()) {
            return defaultValue;
        }
        return val;
    }
}
