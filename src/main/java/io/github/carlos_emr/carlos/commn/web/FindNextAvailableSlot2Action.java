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
 * schedule template for days with open slots. Returns the <em>third</em> available provider/day/time
 * combination found as JSON, suitable for driving the quick-search "Appt" badge navigation.</p>
 *
 * <h3>Request parameters</h3>
 * <ul>
 *   <li>{@code providerNos} – comma-separated list of provider numbers to search (required)</li>
 *   <li>{@code startDate}   – ISO date to start searching from, inclusive, {@code YYYY-MM-DD}
 *       (optional; defaults to tomorrow)</li>
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
    /** Maximum days to search ahead before giving up. */
    private static final int MAX_LOOKAHEAD_DAYS = 60;
    /** Number of available slots to skip before returning (returns the Nth available slot). */
    private static final int TARGET_SLOT_ORDINAL = 3;

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

        // Search forward up to MAX_LOOKAHEAD_DAYS, returning the Nth available slot
        int slotsFound = 0;
        int consecutiveErrors = 0;
        for (int day = 0; day < MAX_LOOKAHEAD_DAYS; day++) {
            int searchYear  = searchCal.get(Calendar.YEAR);
            int searchMonth = searchCal.get(Calendar.MONTH) + 1;
            int searchDay   = searchCal.get(Calendar.DAY_OF_MONTH);
            String dateStr  = String.format("%04d-%02d-%02d", searchYear, searchMonth, searchDay);

            try {
                java.util.Date searchDate = ConversionUtils.fromDateString(dateStr);

                for (String providerNo : providerNos) {
                    providerNo = providerNo.trim();
                    if (providerNo.isEmpty()) continue;

                    List<Object> timecodeResult = scheduleTemplateDao.findTimeCodeByProviderNo2(
                            providerNo, searchDate);

                    if (timecodeResult == null || timecodeResult.isEmpty()) {
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
                            if (slotsFound < TARGET_SLOT_ORDINAL) {
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
        writeJson(Map.of("found", false));
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
