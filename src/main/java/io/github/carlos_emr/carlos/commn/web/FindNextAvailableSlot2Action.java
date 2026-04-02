/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

package io.github.carlos_emr.carlos.commn.web;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.appointment.web.NextAppointmentSearchBean;
import io.github.carlos_emr.carlos.appointment.web.NextAppointmentSearchHelper;
import io.github.carlos_emr.carlos.appointment.web.NextAppointmentSearchResult;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Struts 2 action that finds the next available appointment slot across a set of schedule providers.
 *
 * <p>Delegates to {@link NextAppointmentSearchHelper} — the same search logic used by
 * {@code appointmentsearch.jsp} — to locate available slots. Searches forward from today
 * up to {@value NextAppointmentSearchHelper#MAX_DAYS_TO_SEARCH} days, checking each provider's
 * schedule template for days with open slots. Returns the <em>Nth</em> available slot (across
 * all specified providers, sorted by date) as JSON, suitable for driving the quick-search
 * "Appt" badge navigation.</p>
 *
 * <h3>Request parameters</h3>
 * <ul>
 *   <li>{@code providerNos} – comma-separated list of provider numbers to search (required)</li>
 * </ul>
 *
 * <h3>Configuration (carlos.properties)</h3>
 * <ul>
 *   <li>{@code TARGET_SLOT_ORDINAL} – which available slot to return (default: 3); must be &gt;= 1.
 *       For example, {@code 1} returns the very first open slot, {@code 3} returns the third.</li>
 * </ul>
 *
 * <h3>Response JSON (on success)</h3>
 * <pre>
 * { "found": true, "year": 2026, "month": 3, "day": 25,
 *   "providerNo": "10001", "startTime": "09:00", "duration": 15 }
 * </pre>
 * <pre>
 * { "found": false, "lookaheadDays": 180 }
 * </pre>
 *
 * @since 2026-03-22
 */
public class FindNextAvailableSlot2Action extends ActionSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Default slot ordinal to return; overridable via {@code TARGET_SLOT_ORDINAL} in carlos.properties.
     * Returns the third available slot (ordinal 3) by default.
     */
    private static final int DEFAULT_TARGET_SLOT_ORDINAL = 3;

    /**
     * Reads {@code TARGET_SLOT_ORDINAL} from carlos.properties with validation.
     * Falls back to {@value #DEFAULT_TARGET_SLOT_ORDINAL} if the property is absent, non-numeric, or &lt;= 0.
     *
     * @return int the effective target slot ordinal (always &gt;= 1)
     */
    static int resolveTargetSlotOrdinal() {
        String raw = CarlosProperties.getInstance().getProperty("TARGET_SLOT_ORDINAL");
        if (raw != null) {
            try {
                int value = Integer.parseInt(raw.trim());
                if (value >= 1) return value;
                MiscUtils.getLogger().warn("FindNextAvailableSlot2Action: TARGET_SLOT_ORDINAL={} is not a positive integer; using default {}", value, DEFAULT_TARGET_SLOT_ORDINAL);
            } catch (NumberFormatException e) {
                MiscUtils.getLogger().warn("FindNextAvailableSlot2Action: TARGET_SLOT_ORDINAL='{}' is not a valid integer; using default {}", raw, DEFAULT_TARGET_SLOT_ORDINAL);
            }
        }
        return DEFAULT_TARGET_SLOT_ORDINAL;
    }

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "r", null)) {
            throw new SecurityException("missing required sec object (_appointment)");
        }

        String providerNosParam = StringUtils.trimToNull(request.getParameter("providerNos"));
        if (providerNosParam == null) {
            writeJson(Map.of("found", false, "error", "providerNos parameter is required"));
            return null;
        }

        String[] providerNos = providerNosParam.split(",");

        int targetSlotOrdinal = resolveTargetSlotOrdinal();

        // Search each provider using NextAppointmentSearchHelper — the same logic used by
        // appointmentsearch.jsp.  Request up to targetSlotOrdinal results per provider so
        // that after aggregating across all providers we have enough to pick the Nth slot.
        List<NextAppointmentSearchResult> allResults = new ArrayList<>();
        for (String providerNo : providerNos) {
            providerNo = providerNo.trim();
            if (providerNo.isEmpty()) continue;

            NextAppointmentSearchBean searchBean = new NextAppointmentSearchBean();
            searchBean.setProviderNo(providerNo);
            searchBean.setDayOfWeek("");
            searchBean.setStartTimeOfDay("0");
            searchBean.setEndTimeOfDay("24");
            searchBean.setCode("");
            searchBean.setNumResults(targetSlotOrdinal);

            allResults.addAll(NextAppointmentSearchHelper.search(searchBean));
        }

        if (allResults.isEmpty()) {
            writeJson(Map.of("found", false, "lookaheadDays", NextAppointmentSearchHelper.MAX_DAYS_TO_SEARCH));
            return null;
        }

        // Sort all results by date ascending so that ordinal selection is globally ordered
        allResults.sort(Comparator.comparing(NextAppointmentSearchResult::getDate));

        // Return the Nth slot (1-based); if fewer slots exist, return the last available one
        NextAppointmentSearchResult slot = allResults.get(Math.min(targetSlotOrdinal, allResults.size()) - 1);

        Map<String, Object> result = new HashMap<>();
        result.put("found",      true);
        result.put("year",       Integer.parseInt(slot.getYear()));
        result.put("month",      Integer.parseInt(slot.getMonth()));
        result.put("day",        Integer.parseInt(slot.getDay()));
        result.put("providerNo", slot.getProviderNo());
        result.put("startTime",  slot.getStartTime());
        result.put("duration",   slot.getDuration());
        writeJson(result);
        return null;
    }

    /**
     * Writes an object as JSON to the HTTP response.
     *
     * @param obj Object the object to serialize and write
     * @throws Exception if JSON serialization or writing fails
     */
    private void writeJson(Object obj) throws Exception {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print(OBJECT_MAPPER.writeValueAsString(obj));
        response.getWriter().flush();
    }
}
