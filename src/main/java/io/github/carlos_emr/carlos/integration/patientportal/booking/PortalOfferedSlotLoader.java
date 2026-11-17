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
package io.github.carlos_emr.carlos.integration.patientportal.booking;

import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateCodeDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.ScheduleTemplateCode;
import io.github.carlos_emr.carlos.managers.DayWorkSchedule;
import io.github.carlos_emr.carlos.managers.ScheduleManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Deque;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Finds open times on a provider's real schedule that may be offered to a patient (#3850).
 *
 * <p>A time is offered only when the doctor's own day template has a clinic-chosen bookable code
 * for every template slot it covers, no appointment that is not cancelled overlaps it, and it starts
 * after the lead time. Nothing is held: the same check runs again under a lock when a patient picks
 * the time ({@link #isOpen}), because the schedule can change in between.
 *
 * <p>This deliberately does not use {@code AppointmentSearchManager}: its configuration table is not
 * created by Flyway, its existing-appointment filter fails without a logged-in user, and its slot
 * handles encode the provider and time.
 */
@Service
public class PortalOfferedSlotLoader {
    /** The longest window staff can ask for, so a single request cannot walk the whole calendar. */
    static final int MAX_WINDOW_DAYS = 92;
    private static final String CANCELLED = "C";

    private final ScheduleManager scheduleManager;
    private final OscarAppointmentDao appointmentDao;
    private final ScheduleTemplateCodeDao templateCodeDao;

    public PortalOfferedSlotLoader(ScheduleManager scheduleManager, OscarAppointmentDao appointmentDao,
            ScheduleTemplateCodeDao templateCodeDao) {
        this.scheduleManager = scheduleManager;
        this.appointmentDao = appointmentDao;
        this.templateCodeDao = templateCodeDao;
    }

    /** One open time on a provider's schedule. */
    public record OpenTime(String providerNo, ZonedDateTime start, int durationMinutes, char templateCode) {}

    /**
     * Open times between {@code from} and {@code to} (inclusive), spread across days and across
     * mornings and afternoons, at most {@code count}, skipping any start in {@code exclude}.
     */
    public List<OpenTime> load(String providerNo, LocalDate from, LocalDate to, int count,
            Set<ZonedDateTime> exclude, PortalBookingSettings settings, ZonedDateTime now) {
        if (count <= 0 || settings.offerableCodes().isEmpty() || to.isBefore(from)) {
            return List.of();
        }
        LocalDate last = to.isAfter(from.plusDays(MAX_WINDOW_DAYS)) ? from.plusDays(MAX_WINDOW_DAYS) : to;
        ZonedDateTime earliest = now.plusHours(settings.leadHours());
        List<Deque<OpenTime>> days = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(last); day = day.plusDays(1)) {
            List<OpenTime> open = openTimes(providerNo, day, settings.offerableCodes(), now.getZone()).stream()
                    .filter(time -> !time.start().isBefore(earliest))
                    .filter(time -> exclude.stream().noneMatch(taken -> taken.isEqual(time.start())))
                    .toList();
            if (!open.isEmpty()) {
                days.add(alternatingHalves(open));
            }
        }
        // Round-robin across days, so the patient sees a spread rather than one busy morning.
        List<OpenTime> picked = new ArrayList<>();
        while (picked.size() < count && !days.isEmpty()) {
            for (var iterator = days.iterator(); iterator.hasNext() && picked.size() < count; ) {
                Deque<OpenTime> day = iterator.next();
                picked.add(day.removeFirst());
                if (day.isEmpty()) {
                    iterator.remove();
                }
            }
        }
        picked.sort((left, right) -> left.start().compareTo(right.start()));
        return List.copyOf(picked);
    }

    /**
     * True when the time is still bookable: on a bookable code for its whole length, free, and not
     * started. The lead time applies only when offering; a patient may still pick an offered time
     * inside it.
     */
    public boolean isOpen(String providerNo, ZonedDateTime start, int durationMinutes,
            PortalBookingSettings settings, ZonedDateTime now) {
        if (!start.isAfter(now)) {
            return false;
        }
        return openTimes(providerNo, start.toLocalDate(), settings.offerableCodes(), start.getZone()).stream()
                .anyMatch(time -> time.start().isEqual(start) && time.durationMinutes() == durationMinutes);
    }

    private List<OpenTime> openTimes(String providerNo, LocalDate day, Set<Character> codes, ZoneId zone) {
        if (codes.isEmpty()) {
            return List.of();
        }
        Calendar calendar = GregorianCalendar.from(day.atStartOfDay(zone));
        DayWorkSchedule schedule = scheduleManager.getDayWorkSchedule(providerNo, calendar);
        if (schedule == null || schedule.isHoliday() || schedule.getTimeSlots() == null
                || schedule.getTimeSlotDurationMin() == null || schedule.getTimeSlotDurationMin() <= 0) {
            return List.of();
        }
        int slotMinutes = schedule.getTimeSlotDurationMin();
        Map<Character, Integer> durations = new HashMap<>();
        List<Appointment> booked = appointmentDao.findByProviderAndDayandNotStatus(
                providerNo, Date.from(day.atStartOfDay(zone).toInstant()), CANCELLED);
        List<OpenTime> open = new ArrayList<>();
        for (Map.Entry<Calendar, Character> slot : schedule.getTimeSlots().entrySet()) {
            Character code = slot.getValue();
            if (code == null || !codes.contains(code)) {
                continue;
            }
            ZonedDateTime start = slot.getKey().toInstant().atZone(zone);
            int duration = durations.computeIfAbsent(code, unknown -> durationOf(unknown, slotMinutes));
            if (coveredByCodes(schedule, start, duration, slotMinutes, codes, zone)
                    && booked.stream().noneMatch(appointment -> overlaps(appointment, start, duration, zone))) {
                open.add(new OpenTime(providerNo, start, duration, code));
            }
        }
        return open;
    }

    /** The template code's own length, else one template slot. */
    private int durationOf(char code, int slotMinutes) {
        ScheduleTemplateCode templateCode = templateCodeDao.getByCode(code);
        try {
            int minutes = templateCode == null || templateCode.getDuration() == null
                    ? slotMinutes : Integer.parseInt(templateCode.getDuration().strip());
            return minutes >= 5 && minutes <= 480 ? minutes : slotMinutes;
        } catch (NumberFormatException invalid) {
            return slotMinutes;
        }
    }

    /** Never off the template: every template slot the visit covers must carry a bookable code. */
    private static boolean coveredByCodes(DayWorkSchedule schedule, ZonedDateTime start, int duration,
            int slotMinutes, Set<Character> codes, ZoneId zone) {
        for (int offset = 0; offset < duration; offset += slotMinutes) {
            Calendar covered = GregorianCalendar.from(start.plusMinutes(offset).withZoneSameInstant(zone));
            Character code = schedule.getTimeSlots().get(covered);
            if (code == null || !codes.contains(code)) {
                return false;
            }
        }
        return true;
    }

    /** CARLOS stores an appointment's end as its last minute (09:00-09:14 for 15 minutes). */
    private static boolean overlaps(Appointment appointment, ZonedDateTime start, int duration, ZoneId zone) {
        if (appointment.getStartTime() == null || appointment.getEndTime() == null) {
            return false;
        }
        LocalDate day = start.toLocalDate();
        ZonedDateTime bookedStart = day.atTime(timeOf(appointment.getStartTime(), zone)).atZone(zone);
        ZonedDateTime bookedEnd = day.atTime(timeOf(appointment.getEndTime(), zone)).atZone(zone).plusMinutes(1);
        ZonedDateTime end = start.plus(Duration.ofMinutes(duration));
        return bookedStart.isBefore(end) && start.isBefore(bookedEnd);
    }

    /**
     * An appointment's TIME column as a clock time. Hibernate loads it as {@link java.sql.Time},
     * whose {@code toInstant()} always throws; any other {@link Date} is read in the server's zone,
     * like the schedule.
     */
    static LocalTime timeOf(Date time, ZoneId zone) {
        LocalTime clock = time instanceof java.sql.Time sqlTime ? sqlTime.toLocalTime()
                : Instant.ofEpochMilli(time.getTime()).atZone(zone).toLocalTime();
        return clock.withSecond(0).withNano(0);
    }

    /** Morning and afternoon times taken in turn, so a day's offers are not all in one half. */
    private static Deque<OpenTime> alternatingHalves(List<OpenTime> open) {
        Deque<OpenTime> morning = new ArrayDeque<>();
        Deque<OpenTime> afternoon = new ArrayDeque<>();
        open.forEach(time -> (time.start().getHour() < 12 ? morning : afternoon).add(time));
        Deque<OpenTime> ordered = new ArrayDeque<>();
        while (!morning.isEmpty() || !afternoon.isEmpty()) {
            if (!morning.isEmpty()) {
                ordered.add(morning.removeFirst());
            }
            if (!afternoon.isEmpty()) {
                ordered.add(afternoon.removeFirst());
            }
        }
        return ordered;
    }
}
