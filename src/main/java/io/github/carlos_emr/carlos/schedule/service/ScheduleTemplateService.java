/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.schedule.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.dao.RScheduleDao;
import io.github.carlos_emr.carlos.commn.model.RSchedule;
import io.github.carlos_emr.carlos.commn.model.ScheduleDate;

/** Applies a weekly schedule rule and its generated dates in the same transaction. */
@Service
public class ScheduleTemplateService {
    private final RScheduleDao rules;
    private final ScheduleDateOverrideService dates;

    public ScheduleTemplateService(RScheduleDao rules, ScheduleDateOverrideService dates) {
        this.rules = rules;
        this.dates = dates;
    }

    /** The provider lookup and the existing overlap warning shown by the month editor. */
    public record Result(boolean providerFound, boolean overlaps, boolean stalePeriod) {}

    /** Applies the editor's rule, retaining custom dates and the existing same-start rule semantics. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result apply(RSchedule submittedRule, boolean replaceSameStart, Date originalEnd,
                        List<ScheduleDate> generatedDays) {
        String providerNo = submittedRule.getProviderNo();
        Date start = submittedRule.getsDate();
        Date end = submittedRule.geteDate();
        // Acquire the provider lock before this command establishes its first consistent read.
        if (!dates.lockProviderForChange(providerNo)) {
            return new Result(false, false, false);
        }
        Date retireThrough = later(originalEnd, end);
        if (replaceSameStart) {
            // The editor can load historical availability codes as well as current weekly rules.
            if (rules.search_rschedule_exists(providerNo, start, originalEnd) == 0) {
                return new Result(true, false, true);
            }
            var previousRules = new ArrayList<>(rules.findByProviderAvailableAndDate(providerNo, "1", start));
            previousRules.addAll(rules.findByProviderAvailableAndDate(providerNo, "A", start));
            for (RSchedule previous : previousRules) {
                if ("A".equals(previous.getStatus())) {
                    // Older data can have overlapping active headers at the same start.
                    retireThrough = later(previous.geteDate(), retireThrough);
                }
                previous.setStatus("D");
                rules.merge(previous);
            }
        }
        dates.replaceLockedRuleDays(providerNo, start, retireThrough, generatedDays);
        boolean overlaps = rules.search_rschedule_overlaps(providerNo, start, end, start, end, start, end,
                start, end, start, end, start, end, start, end) > 0;
        if (rules.search_rschedule_exists(providerNo, start, end) > 0) {
            for (RSchedule existing : rules.findByProviderAvailableAndDate(providerNo, submittedRule.getAvailable(), start)) {
                existing.setDayOfWeek(submittedRule.getDayOfWeek());
                existing.setAvailHourB(submittedRule.getAvailHourB());
                existing.setAvailHour(submittedRule.getAvailHour());
                existing.setCreator(submittedRule.getCreator());
                existing.setStatus(submittedRule.getStatus());
                rules.merge(existing);
            }
        } else {
            rules.persist(submittedRule);
        }
        return new Result(true, overlaps, false);
    }

    private static Date later(Date previousEnd, Date end) {
        return previousEnd != null && previousEnd.after(end) ? previousEnd : end;
    }
}
