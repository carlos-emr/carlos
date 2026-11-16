/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cds.contract;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/** The CDS element projection of the native CPP editor's extension rows. */
public final class CdsCppExtensionProjection {
    public static final Set<String> SECTIONS = Set.of("FamilyHistory", "PastHealth", "ProblemList",
            "RiskFactors", "AlertsAndSpecialNeeds", "PersonalHistory");
    public static final Set<String> DATE_KEYS = Set.of("Start Date", "Resolution Date", "Procedure Date");
    public static final Set<String> VALUE_KEYS = Set.of("Age at Onset", "Treatment", "Problem Status",
            "Exposure Details", "Relationship", "Life Stage", "Problem Description", "Procedure");

    private CdsCppExtensionProjection() {}

    public static String path(String section, String key) {
        return switch (key) {
            case "Start Date" -> switch (section) {
                case "FamilyHistory", "RiskFactors" -> "StartDate";
                case "PastHealth" -> "OnsetOrEventDate";
                case "ProblemList" -> "OnsetDate";
                case "AlertsAndSpecialNeeds" -> "DateActive";
                default -> null;
            };
            case "Resolution Date" -> switch (section) {
                case "PastHealth" -> "ResolvedDate";
                case "ProblemList" -> "ResolutionDate";
                case "RiskFactors", "AlertsAndSpecialNeeds" -> "EndDate";
                default -> null;
            };
            case "Procedure Date" -> section.equals("PastHealth") ? "ProcedureDate" : null;
            case "Age at Onset" -> section.equals("FamilyHistory") ? "AgeAtOnset"
                    : section.equals("RiskFactors") ? "AgeOfOnset" : null;
            case "Treatment" -> section.equals("FamilyHistory") ? "Treatment" : null;
            case "Problem Status" -> Set.of("PastHealth", "ProblemList").contains(section) ? "ProblemStatus" : null;
            case "Exposure Details" -> section.equals("RiskFactors") ? "ExposureDetails" : null;
            case "Relationship" -> section.equals("FamilyHistory") ? "Relationship" : null;
            case "Life Stage" -> Set.of("FamilyHistory", "PastHealth", "ProblemList", "RiskFactors")
                    .contains(section) ? "LifeStage" : null;
            case "Problem Description" -> section.equals("ProblemList") ? "ProblemDescription" : null;
            default -> null;
        };
    }

    public static boolean fits(String target, String value) {
        if (value == null) return false;
        if (target.equals("LifeStage")) return Set.of("N", "I", "C", "T", "A").contains(value);
        int maximum = switch (target) {
            case "Treatment", "ProblemDescription" -> 250;
            case "ExposureDetails" -> 120;
            default -> 50;
        };
        boolean fits = value.codePointCount(0, value.length()) <= maximum
                && CdsNativeExtensionContract.xmlElementText(value).equals(value);
        if (target.equals("AgeAtOnset") || target.equals("AgeOfOnset"))
            fits &= value.matches("[0-9]+") && !(target.equals("AgeOfOnset")
                    && value.chars().allMatch(c -> c == '0'));
        return fits;
    }

    /** A stored DATE as the CPP editor displays it; a zero or invalid date has no projection. */
    public static String date(String stored, String marker) {
        LocalDate date = calendarDay(stored);
        if (date == null) return null;
        String full = date.toString();
        if ("YYYY".equals(marker)) return full.substring(0, 4);
        if ("YYYY-MM".equals(marker)) return full.substring(0, 7);
        return full;
    }

    public static LocalDate calendarDay(String stored) {
        if (stored == null) return null;
        try {
            var result = stored.length() == 10 ? LocalDate.parse(stored)
                    : LocalDateTime.parse(stored.replace(' ', 'T')).toLocalDate();
            return result.getYear() < 1 || result.getYear() > 9999 ? null : result;
        } catch (DateTimeException invalid) {
            return null;
        }
    }
}
