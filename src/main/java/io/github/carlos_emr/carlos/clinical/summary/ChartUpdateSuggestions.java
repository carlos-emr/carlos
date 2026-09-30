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
package io.github.carlos_emr.carlos.clinical.summary;

import java.io.Serializable;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Deterministic form suggestions from the quoted passage; never authority to write a chart. */
public final class ChartUpdateSuggestions {
    private ChartUpdateSuggestions() { }
    private static final Pattern DURATION = Pattern.compile(
            "\\b([0-9]{1,3}|a|an|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\\s+(days?|weeks?|months?|years?)\\b");
    private static final Pattern ISO_DATE = Pattern.compile("\\b[0-9]{4}-[0-9]{2}-[0-9]{2}\\b");
    private static final Pattern UNCERTAIN = Pattern.compile(
            "\\b(if|unless|after|before|ago|from|until|pending|when|once|or|between|no|not|never|cancelled|canceled|post|postop|postoperative|postoperatively|following|since)\\b|[0-9]\\s*[-–/.]\\s*[0-9]");
    private static final Pattern HISTORICAL = Pattern.compile(
            "^(?:past (?:medical |surgical )?history|history of|previous history of|resolved|previously treated for)\\b");
    private static final List<String> NUMBERS = List.of("zero", "one", "two", "three", "four", "five", "six",
            "seven", "eight", "nine", "ten", "eleven", "twelve");

    public record Suggestion(ChartUpdateReview.Draft draft, String dateBasis, String anchor) implements Serializable { }

    public static Suggestion suggest(ChartUpdateProposals.Proposal proposal, String documentDate, String assignee) {
        String evidence = proposal.evidence();
        String lower = evidence.toLowerCase(Locale.ROOT);
        if ("history".equals(proposal.kind())) {
            String destination = HISTORICAL.matcher(lower.strip()).find() ? "MedHistory" : "Concerns";
            return new Suggestion(new ChartUpdateReview.Draft(evidence, "", "", destination), "", "");
        }
        var date = date(lower, documentDate);
        return new Suggestion(new ChartUpdateReview.Draft(evidence, date[0], assignee, ""), date[1], date[2]);
    }

    private static String[] date(String evidence, String documentDate) {
        String[] unknown = {"", "", ""};
        // Alternative intervals, conditions and event-relative dates need clinician interpretation.
        if (UNCERTAIN.matcher(ISO_DATE.matcher(evidence).replaceAll("DATE")).find()
                || evidence.matches("(?s).*\\b(?:one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\\s+(?:to|or)\\s+.*")
                || evidence.matches("(?s).*\\b(?:as needed|prn)\\b.*")) return unknown;
        var duration = DURATION.matcher(evidence);
        boolean hasDuration = duration.find();
        var explicit = ISO_DATE.matcher(evidence);
        if (explicit.find()) {
            String value = explicit.group();
            if (!evidence.substring(0, explicit.start()).matches("(?s).*\\b(?:on|by|due|review|recheck)\\s*$")) return unknown;
            if (explicit.find() || hasDuration || evidence.contains("tomorrow")) return unknown;
            try { return new String[]{LocalDate.parse(value).toString(), "explicit", ""}; }
            catch (DateTimeException invalid) { return unknown; }
        }
        LocalDate anchor;
        try {
            if (documentDate == null || !documentDate.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}(?:[ T].*)?")) return unknown;
            anchor = LocalDate.parse(documentDate.substring(0, 10));
        } catch (DateTimeException invalid) { return unknown; }
        try {
            LocalDate due;
            if (Pattern.compile("\\btomorrow\\b").matcher(evidence).find()) {
                if (hasDuration) return unknown;
                due = anchor.plusDays(1);
            } else {
                if (!hasDuration) return unknown;
                String number = duration.group(1), unit = duration.group(2);
                String before = evidence.substring(0, duration.start());
                String after = evidence.substring(duration.end());
                if (after.matches("(?s)^\\s+(?:of\\b|(?:on|at)\\s+(?:the\\s+)?(?:discharge|admission|surgery|procedure|operation|treatment)\\b).*")) {
                    return unknown;
                }
                boolean timing = before.matches("(?s).*\\b(?:in|within|follow[- ]?up|review|recheck)\\s*$")
                        || after.matches("(?s)^\\s+(?:follow[- ]?up|review|recheck)\\b.*")
                        || evidence.strip().equals(duration.group());
                if (!timing) return unknown;
                if (duration.find()) return unknown;
                int count = number.matches("[0-9]+") ? Integer.parseInt(number)
                        : List.of("a", "an").contains(number) ? 1 : NUMBERS.indexOf(number);
                if (count < 1) return unknown;
                due = switch (unit.charAt(0)) {
                    case 'd' -> anchor.plusDays(count);
                    case 'w' -> anchor.plusWeeks(count);
                    case 'm' -> anchor.plusMonths(count);
                    default -> anchor.plusYears(count);
                };
            }
            if (due.getYear() > 9999) return unknown;
            return new String[]{due.toString(), "relative", anchor.toString()};
        } catch (DateTimeException invalid) { return unknown; }
    }
}
