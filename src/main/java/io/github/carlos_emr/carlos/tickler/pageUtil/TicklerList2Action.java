/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 *
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.tickler.pageUtil;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * Struts 2 action that provides a JSON endpoint for server-side DataTables
 * pagination of the tickler list. Replaces the previous approach of loading
 * all ticklers at once with client-side-only pagination.
 *
 * <p>Accepts DataTables server-side processing parameters (draw, start, length)
 * and returns JSON with tickler data, total counts, comments, and links.</p>
 *
 * <p>Ported from open-o PR #2268 with namespace adaptations for CARLOS EMR.</p>
 *
 * @since 2026-02-27
 */
public class TicklerList2Action extends ActionSupport {

    private static final Logger logger = LoggerFactory.getLogger(TicklerList2Action.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_PAGE_SIZE = 500;
    private static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd";
    private static final String TIME_FORMAT_PATTERN = "HH:mm";

    private TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (loggedInInfo == null) {
            writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Session expired");
            return null;
        }

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", "r", null)) {
            writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Access denied");
            return null;
        }

        try {
            int draw = parseIntParam(request, "draw", 1);
            int start = parseIntParam(request, "start", 0);
            int length = parseIntParam(request, "length", 25);

            if (length > MAX_PAGE_SIZE) {
                length = MAX_PAGE_SIZE;
            }
            if (length < 0) {
                length = MAX_PAGE_SIZE;
            }

            CustomFilter filter = buildFilterFromRequest(request);

            // recordsTotal uses a filter without search so the total count reflects form filters only
            int totalRecords = ticklerManager.getNumTicklers(loggedInInfo, filter);
            // recordsFiltered accounts for full-text search when the DataTables search box is used
            int filteredRecords = (filter.getSearchTerm() != null && !filter.getSearchTerm().trim().isEmpty())
                    ? ticklerManager.getNumTicklersFiltered(loggedInInfo, filter)
                    : totalRecords;

            List<TicklerListDTO> ticklerDTOs = ticklerManager.getTicklerDTOs(loggedInInfo, filter, start, length);

            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, List<Map<String, Object>>> commentsMap = new HashMap<>();
            SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_PATTERN);
            SimpleDateFormat timeFormat = new SimpleDateFormat(TIME_FORMAT_PATTERN);
            Locale locale = request.getLocale();
            Date today = new Date();

            int ticklerWarnDays = getTicklerWarnDays();

            for (TicklerListDTO dto : ticklerDTOs) {
                rows.add(buildTicklerRow(dto, ticklerWarnDays, dateFormat, locale));

                if (dto.getComments() != null && !dto.getComments().isEmpty()) {
                    commentsMap.put(String.valueOf(dto.getId()), buildCommentsArray(dto.getComments(), dateFormat, timeFormat, today));
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("draw", draw);
            result.put("recordsTotal", totalRecords);
            result.put("recordsFiltered", filteredRecords);
            result.put("data", rows);
            result.put("comments", commentsMap);

            String json = objectMapper.writeValueAsString(result);
            response.getWriter().write(json);

            LogAction.addLogSynchronous(loggedInInfo, "TicklerList2Action",
                    "draw=" + draw + ",start=" + start + ",length=" + length + ",total=" + totalRecords);

        } catch (Exception e) {
            logger.error("Error loading ticklers", e);
            writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error loading ticklers");
        }

        return null;
    }

    /**
     * Builds a CustomFilter from DataTables request parameters.
     *
     * @param request HttpServletRequest the incoming request
     * @return CustomFilter populated with filter criteria
     */
    private CustomFilter buildFilterFromRequest(HttpServletRequest request) {
        CustomFilter filter = new CustomFilter();

        String status = getStringParam(request, "status", "A");
        filter.setStatus(status);

        String provider = getStringParam(request, "provider", "");
        filter.setProvider(provider);

        String assignee = getStringParam(request, "assignee", "");
        filter.setAssignee(assignee);

        String mrp = getStringParam(request, "mrp", "");
        filter.setMrp(mrp);

        String programId = getStringParam(request, "programId", "");
        filter.setProgramId(programId);

        String demographicNo = getStringParam(request, "demographicNo", "");
        filter.setDemographicNo(demographicNo);

        String client = getStringParam(request, "client", "");
        filter.setClient(client);

        String priority = getStringParam(request, "priority", "");
        filter.setPriority(priority);

        String startDateStr = getStringParam(request, "startDate", "");
        if (!startDateStr.isEmpty()) {
            filter.setStartDateWeb(startDateStr);
        }

        String endDateStr = getStringParam(request, "endDate", "");
        if (!endDateStr.isEmpty()) {
            filter.setEndDateWeb(endDateStr);
        }

        // DataTables server-side sort: order[0][column] maps to the column index,
        // order[0][dir] is "asc" or "desc". Only whitelisted columns are accepted.
        String orderColStr = getStringParam(request, "order[0][column]", "4");
        String orderDir = getStringParam(request, "order[0][dir]", "desc");
        filter.setSortColumn(mapColumnIndexToField(orderColStr));
        filter.setSort_order("asc".equalsIgnoreCase(orderDir) ? "asc" : "desc");

        // DataTables search box value — used for full-text search across message and patient name
        String searchValue = getStringParam(request, "search[value]", "");
        if (!searchValue.isEmpty()) {
            filter.setSearchTerm(searchValue);
        }

        return filter;
    }

    /**
     * Maps a DataTables column index string to the corresponding {@link CustomFilter} sort column name.
     * Unrecognised indices default to {@code serviceDate}.
     * Column mapping mirrors the {@code columns} array in {@code ticklerMain.jsp}:
     * 0=checkbox, 1=edit, 2=demographicName, 3=creatorName, 4=serviceDate, 5=createDate,
     * 6=priority, 7=assigneeName, 8=status, 9=message, 10=noteLink.
     *
     * @param colIndexStr String the DataTables column index from the request
     * @return String the CustomFilter sortColumn value
     */
    private String mapColumnIndexToField(String colIndexStr) {
        switch (colIndexStr) {
            case "6":  return "priority";
            case "4":  // fall-through to default
            default:   return "serviceDate";
        }
    }

    /**
     * Builds a map representation of a tickler row for JSON serialization.
     *
     * @param dto TicklerListDTO the tickler data
     * @param warnDays int the number of days to consider a tickler as warning
     * @param dateFormat SimpleDateFormat thread-local date formatter
     * @param locale Locale the request locale for status description i18n
     * @return Map containing the tickler row data
     */
    private Map<String, Object> buildTicklerRow(TicklerListDTO dto, int warnDays, SimpleDateFormat dateFormat, Locale locale) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", dto.getId());
        row.put("message", dto.getMessage() != null ? dto.getMessage() : "");
        row.put("serviceDate", dto.getServiceDate() != null ? dateFormat.format(dto.getServiceDate()) : "");
        row.put("createDate", dto.getCreateDate() != null ? dateFormat.format(dto.getCreateDate()) : "");
        row.put("status", dto.getStatus() != null ? dto.getStatus().name() : "");
        row.put("statusDesc", dto.getStatusDesc(locale));
        row.put("priority", dto.getPriority() != null ? dto.getPriority().name() : "Normal");
        row.put("demographicNo", dto.getDemographicNo());
        row.put("demographicName", dto.getDemographicFormattedName());
        row.put("creatorName", dto.getCreatorFormattedName());
        row.put("assigneeName", dto.getAssigneeFormattedName());
        row.put("warning", isWarning(dto, warnDays));
        row.put("hasComments", dto.getComments() != null && !dto.getComments().isEmpty());

        List<Map<String, Object>> links = new ArrayList<>();
        if (dto.getLinks() != null) {
            for (TicklerLinkDTO link : dto.getLinks()) {
                Map<String, Object> linkMap = new HashMap<>();
                linkMap.put("id", link.getId());
                linkMap.put("tableName", link.getTableName());
                linkMap.put("tableId", link.getTableId());
                links.add(linkMap);
            }
        }
        row.put("links", links);

        return row;
    }

    /**
     * Builds a list of comment maps for JSON serialization. When a comment was
     * made today, the updateDate field contains only the time (HH:mm) to match
     * the previous JSP rendering behaviour. For older comments the date (yyyy-MM-dd)
     * is returned. The raw isToday flag is also included so the client can
     * apply further formatting if needed.
     *
     * @param comments List of TicklerCommentDTO
     * @param dateFormat SimpleDateFormat date-only formatter (yyyy-MM-dd)
     * @param timeFormat SimpleDateFormat time-only formatter (HH:mm)
     * @param today Date pre-computed reference point for today comparisons
     * @return List of maps containing comment data
     */
    private List<Map<String, Object>> buildCommentsArray(List<TicklerCommentDTO> comments, SimpleDateFormat dateFormat, SimpleDateFormat timeFormat, Date today) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (TicklerCommentDTO comment : comments) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", comment.getId());
            map.put("message", comment.getMessage() != null ? comment.getMessage() : "");
            boolean isToday = comment.isSameDayAs(today);
            if (comment.getUpdateDate() != null) {
                // Show time for today's comments, date for older comments (matches old JSP behaviour)
                map.put("updateDate", isToday ? timeFormat.format(comment.getUpdateDate()) : dateFormat.format(comment.getUpdateDate()));
            } else {
                map.put("updateDate", "");
            }
            map.put("providerName", comment.getProviderFormattedName());
            map.put("isToday", isToday);
            result.add(map);
        }
        return result;
    }

    /**
     * Writes a JSON error response.
     *
     * @param response HttpServletResponse the response
     * @param statusCode int the HTTP status code
     * @param message String the error message
     * @throws IOException if writing fails
     */
    private void writeJsonError(HttpServletResponse response, int statusCode, String message) throws IOException {
        response.setStatus(statusCode);
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("draw", 0);
        error.put("recordsTotal", 0);
        error.put("recordsFiltered", 0);
        error.put("data", new ArrayList<>());
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }

    /**
     * Checks if a tickler should be displayed with a warning indicator
     * based on the service date being within the configured warn days.
     *
     * @param dto TicklerListDTO the tickler data
     * @param warnDays int the warning threshold in days
     * @return boolean true if the tickler should show a warning
     */
    private boolean isWarning(TicklerListDTO dto, int warnDays) {
        if (dto.getServiceDate() == null || warnDays <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        long serviceTime = dto.getServiceDate().getTime();
        long diffDays = (now - serviceTime) / (1000L * 60 * 60 * 24);
        // Warn when tickler is overdue by at least warnDays (service date is in the past by >= warnDays)
        return diffDays >= warnDays;
    }

    /**
     * Gets the tickler warning period from properties, defaulting to 0 (disabled).
     *
     * @return int the number of days for warning threshold
     */
    private int getTicklerWarnDays() {
        try {
            String val = OscarProperties.getInstance().getProperty("tickler_warn_period", "0");
            return Integer.parseInt(val);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Parses an integer parameter from the request with a default value.
     *
     * @param request HttpServletRequest the request
     * @param name String the parameter name
     * @param defaultValue int the default value if parameter is missing or invalid
     * @return int the parsed value or default
     */
    private int parseIntParam(HttpServletRequest request, String name, int defaultValue) {
        String val = request.getParameter(name);
        if (val == null || val.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Gets a string parameter from the request with a default value.
     *
     * @param request HttpServletRequest the request
     * @param name String the parameter name
     * @param defaultValue String the default value if parameter is missing
     * @return String the parameter value or default
     */
    private String getStringParam(HttpServletRequest request, String name, String defaultValue) {
        String val = request.getParameter(name);
        return (val != null) ? val.trim() : defaultValue;
    }
}
