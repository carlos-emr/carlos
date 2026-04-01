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

import java.time.LocalDate;
import java.util.Calendar;
import java.util.GregorianCalendar;
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
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateCodeDao;
import io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.ScheduleTemplateCode;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Struts 2 action that finds the next available appointment slot across a set of schedule providers.
 *
 * <p>Searches forward from the given start date (or tomorrow if omitted), checking each provider's
 * schedule template for days with open slots. Returns the <em>first</em> available provider/day/time
 * combination found as JSON, suitable for driving the quick-search "Appt" badge navigation.</p>
 *
 * <h3>Request parameters</h3>
 * <ul>
 *   <li>{@code providerNos} – comma-separated list of provider numbers to search (required)</li>
 *   <li>{@code startDate}   – ISO date to start searching from, inclusive, {@code YYYY-MM-DD}
 *       (optional; defaults to tomorrow)</li>
 * </ul>
 *
 * <h3>Configuration (carlos.properties)</h3>
 * <ul>
 *   <li>{@code TARGET_SLOT_ORDINAL}  – which open slot to return (default: 1); must be &gt;= 1</li>
 *   <li>{@code MAX_LOOKAHEAD_DAYS}   – days to search before giving up (default: 90); must be &gt;= 1</li>
 * </ul>
 *
 * <h3>Response JSON (on success)</h3>
 * <pre>
 * { "found": true, "year": 2026, "month": 3, "day": 25,
 *   "providerNo": "10001", "startTime": "09:00", "duration": 15 }
 * </pre>
 * <pre>
 * { "found": false }
 * </pre>
 *
 * @since 2026-03-22
 */
public class FindNextAvailableSlot2Action extends ActionSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Default maximum days to search ahead; overridable via {@code MAX_LOOKAHEAD_DAYS} in carlos.properties. */
    private static final int DEFAULT_MAX_LOOKAHEAD_DAYS = 90;
    /**
     * Default slot ordinal to return; overridable via {@code TARGET_SLOT_ORDINAL} in carlos.properties.
     * Returns the first available slot (ordinal 1) to avoid false "no slots" results when only a few
     * slots exist within the lookahead window.
     */
    private static final int DEFAULT_TARGET_SLOT_ORDINAL = 1;

    /**
     * Reads {@code MAX_LOOKAHEAD_DAYS} from carlos.properties with validation.
     * Falls back to {@value #DEFAULT_MAX_LOOKAHEAD_DAYS} if the property is absent, non-numeric, or &lt;= 0.
     *
     * @return int the effective maximum lookahead days (always &gt;= 1)
     */
    static int resolveMaxLookaheadDays() {
        String raw = CarlosProperties.getInstance().getProperty("MAX_LOOKAHEAD_DAYS");
        if (raw != null) {
            try {
                int value = Integer.parseInt(raw.trim());
                if (value >= 1) return value;
                MiscUtils.getLogger().warn("FindNextAvailableSlot2Action: MAX_LOOKAHEAD_DAYS={} is not a positive integer; using default {}", value, DEFAULT_MAX_LOOKAHEAD_DAYS);
            } catch (NumberFormatException e) {
                MiscUtils.getLogger().warn("FindNextAvailableSlot2Action: MAX_LOOKAHEAD_DAYS='{}' is not a valid integer; using default {}", raw, DEFAULT_MAX_LOOKAHEAD_DAYS);
            }
        }
        return DEFAULT_MAX_LOOKAHEAD_DAYS;
    }

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

        // Determine start date (default: tomorrow)
        Calendar searchCal = new GregorianCalendar();
        String startDateParam = StringUtils.trimToNull(request.getParameter("startDate"));
        if (startDateParam != null) {
            java.util.Date parsedDate = ConversionUtils.fromDateString(startDateParam);
            if (parsedDate != null) {
                searchCal.setTime(parsedDate);
            } else {
                MiscUtils.getLogger().warn("FindNextAvailableSlot2Action: unparseable startDate '{}', defaulting to tomorrow",
                        startDateParam);
                searchCal.add(Calendar.DATE, 1);
            }
        } else {
            searchCal.add(Calendar.DATE, 1);
        }

        // Build templateMap: timecode character → duration in minutes
        ScheduleTemplateCodeDao templateCodeDao = SpringUtils.getBean(ScheduleTemplateCodeDao.class);
        Map<String, String> templateMap = new HashMap<>();
        for (ScheduleTemplateCode stc : templateCodeDao.findTemplateCodes()) {
            templateMap.put(String.valueOf(stc.getCode()), stc.getDuration());
        }

        ScheduleTemplateDao scheduleTemplateDao = SpringUtils.getBean(ScheduleTemplateDao.class);
        OscarAppointmentDao appointmentDao = SpringUtils.getBean(OscarAppointmentDao.class);

        // Read configurable limits from carlos.properties (validated, fall back to defaults if invalid)
        int maxLookaheadDays  = resolveMaxLookaheadDays();
        int targetSlotOrdinal = resolveTargetSlotOrdinal();

        // Search forward up to maxLookaheadDays, returning the Nth available slot
        int slotsFound = 0;
        int consecutiveErrors = 0;
        for (int day = 0; day < maxLookaheadDays; day++) {
            int searchYear  = searchCal.get(Calendar.YEAR);
            int searchMonth = searchCal.get(Calendar.MONTH) + 1;
            int searchDay   = searchCal.get(Calendar.DAY_OF_MONTH);
            String dateStr  = String.format("%04d-%02d-%02d", searchYear, searchMonth, searchDay);

            try {
                // Use java.sql.Date (pure calendar date, no time/timezone) so the native SQL
                // query binds a DATE parameter rather than a TIMESTAMP.  When java.util.Date is
                // passed to a native query Hibernate sends it as TIMESTAMP; MySQL Connector/J then
                // applies timezone conversion before comparing with the DATE column `sdate`, which
                // can shift the value to the previous day and cause every schedule lookup to miss.
                // The JPQL path used by the calendar view avoids this via @Temporal(TemporalType.DATE).
                java.sql.Date searchDate = java.sql.Date.valueOf(
                        LocalDate.of(searchYear, searchMonth, searchDay));

                for (String providerNo : providerNos) {
                    providerNo = providerNo.trim();
                    if (providerNo.isEmpty()) continue;

                    List<Object> timecodeResult = scheduleTemplateDao.findTimeCodeByProviderNo2(
                            providerNo, searchDate);

                    if (timecodeResult == null || timecodeResult.isEmpty()) {
                        MiscUtils.getLogger().debug(
                                "FindNextAvailableSlot: no scheduledate/template for provider={} date={}",
                                providerNo, dateStr);
                        continue; // No schedule template for this provider on this day
                    }

                    String timecode = StringUtils.trimToEmpty(String.valueOf(timecodeResult.get(0)));
                    if (timecode.isEmpty()) continue;

                    int timecodeLength   = timecode.length();
                    int timecodeInterval = 1440 / timecodeLength; // minutes per slot

                    // Build schedArr: 1 = open slot according to template
                    int[] schedArr = new int[timecodeLength];
                    for (int i = 0; i < timecodeLength; i++) {
                        String slotChar = String.valueOf(timecode.charAt(i));
                        if (!"_".equals(slotChar) && templateMap.containsKey(slotChar)) {
                            schedArr[i] = 1;
                        }
                    }

                    if (MiscUtils.getLogger().isDebugEnabled()) {
                        int openCount = 0;
                        for (int open : schedArr) if (open == 1) openCount++;
                        MiscUtils.getLogger().debug(
                                "FindNextAvailableSlot: provider={} date={} timecodeLen={} openSlots={}",
                                providerNo, dateStr, timecodeLength, openCount);
                    }

                    // Mark slots occupied by existing (non-cancelled/no-show) appointments as 0
                    List<Appointment> booked = appointmentDao.findByProviderAndDayandNotStatuses(
                            providerNo, searchDate, new String[]{"C", "CS", "CV", "N", "NS", "NV"});
                    for (Appointment appt : booked) {
                        String startStr = StringUtils.trimToEmpty(ConversionUtils.toTimeString(appt.getStartTime()));
                        String endStr   = StringUtils.trimToEmpty(ConversionUtils.toTimeString(appt.getEndTime()));
                        int startMins = timeStrToMins(startStr);
                        int endMins   = timeStrToMins(endStr);
                        // Skip malformed appointment times to avoid incorrectly blocking midnight slots
                        if (startMins < 0 || endMins < 0) continue;
                        int startIdx = startMins / timecodeInterval;
                        int endIdx   = endMins   / timecodeInterval;
                        startIdx = Math.max(0, startIdx);
                        endIdx   = Math.min(timecodeLength - 1, endIdx);
                        for (int i = startIdx; i <= endIdx; i++) {
                            schedArr[i] = 0;
                        }
                    }

                    // Find the first open slot that has enough consecutive room for its duration
                    for (int i = 0; i < timecodeLength; i++) {
                        if (schedArr[i] != 1) continue;

                        String slotChar = String.valueOf(timecode.charAt(i));
                        String durationStr = templateMap.get(slotChar);
                        if (durationStr == null || durationStr.isEmpty()) continue;

                        int slotDuration;
                        try {
                            slotDuration = Integer.parseInt(durationStr.trim());
                        } catch (NumberFormatException e) {
                            continue;
                        }

                        int slotsNeeded = Math.max(1, (slotDuration + timecodeInterval - 1) / timecodeInterval);
                        boolean enoughRoom = true;
                        for (int n = 1; n < slotsNeeded; n++) {
                            if ((i + n) >= timecodeLength || schedArr[i + n] != 1) {
                                enoughRoom = false;
                                break;
                            }
                        }

                        if (enoughRoom) {
                            slotsFound++;
                            if (slotsFound < targetSlotOrdinal) {
                                // Skip ahead past this slot and continue searching
                                i += Math.max(1, slotsNeeded) - 1;
                                continue;
                            }
                            int startHour = (i * timecodeInterval) / 60;
                            int startMin  = (i * timecodeInterval) % 60;
                            String startTime = String.format("%02d:%02d", startHour, startMin);

                            Map<String, Object> result = new HashMap<>();
                            result.put("found",      true);
                            result.put("year",       searchYear);
                            result.put("month",      searchMonth);
                            result.put("day",        searchDay);
                            result.put("providerNo", providerNo);
                            result.put("startTime",  startTime);
                            result.put("duration",   slotDuration);
                            writeJson(result);
                            return null;
                        }
                    }
                }
                consecutiveErrors = 0;
            } catch (Exception e) {
                consecutiveErrors++;
                MiscUtils.getLogger().error("FindNextAvailableSlot2Action: error checking date "
                        + dateStr + " for providers " + providerNosParam, e);
                if (consecutiveErrors >= 5) {
                    writeJson(Map.of("found", false, "error", "Scheduling system error. Please try again or contact support."));
                    return null;
                }
            }

            searchCal.add(Calendar.DATE, 1);
        }

        // No slot found within lookahead window
        writeJson(Map.of("found", false, "lookaheadDays", maxLookaheadDays));
        return null;
    }

    /**
     * Converts a time string in "HH:mm" or "HH:mm:ss" format to total minutes since midnight.
     *
     * @param timeStr String time in "HH:mm" or "HH:mm:ss" format (e.g. "09:30" or "09:30:00")
     * @return int total minutes since midnight, or -1 if the string cannot be parsed
     */
    private int timeStrToMins(String timeStr) {
        if (timeStr == null) return -1;
        String trimmed = timeStr.trim();
        String[] parts = trimmed.split(":");
        if (parts.length < 2) return -1;
        try {
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) return -1;
            return hours * 60 + minutes;
        } catch (NumberFormatException e) {
            return -1;
        }
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
