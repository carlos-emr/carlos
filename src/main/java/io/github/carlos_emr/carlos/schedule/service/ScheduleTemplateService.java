/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.schedule.service;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
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
    public record Result(boolean providerFound, boolean overlaps) {}

    /** Applies the editor's rule, retaining custom dates and the existing same-start rule semantics. */
    @Transactional
    public Result apply(RSchedule submittedRule, boolean replaceSameStart, Date retireThrough,
                        List<ScheduleDate> generatedDays) {
        String providerNo = submittedRule.getProviderNo();
        Date start = submittedRule.getsDate();
        Date end = submittedRule.geteDate();
        // The date service takes the provider lock before its first read. It remains held through
        // the rule updates and the commit of this enclosing transaction.
        if (!dates.replaceRuleDays(providerNo, start, retireThrough, generatedDays)) {
            return new Result(false, false);
        }
        if (replaceSameStart) {
            for (String availability : List.of("1", "A")) {
                for (RSchedule previous : rules.findByProviderAvailableAndDate(providerNo, availability, start)) {
                    previous.setStatus("D");
                    rules.merge(previous);
                }
            }
        }
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
        return new Result(true, overlaps);
    }
}
