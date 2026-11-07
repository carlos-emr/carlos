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
package io.github.carlos_emr.carlos.demographic.cdsexport.cli;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Parses the installed adapter's exact internal argv without file I/O or trusting an environment operator name. */
public record CdsExportCliArguments(String operation, String provider, Integer facility,
        CdsLogicalProfile profile, Set<CdsCategory> categories, List<String> physicians,
        Path patientsFile, String recipient, String auditPlan, String submissionKey,
        String runId, boolean detach, String artifactId, Path output) {
    public static final Set<String> OPERATIONS = Set.of("export", "status", "resume", "cancel", "report", "download");
    private static final Set<String> VALUE_OPTIONS = Set.of("--operation", "--provider", "--facility", "--profile", "--categories",
            "--physicians", "--patients-file", "--recipient", "--audit-plan", "--submission-key", "--run", "--artifact", "--output");
    public CdsExportCliArguments { categories = Set.copyOf(categories); physicians = List.copyOf(physicians); }

    public static CdsExportCliArguments parse(String[] argv) {
        Map<String, String> options = new HashMap<>(); boolean detach = false;
        for (int index = 0; index < argv.length; index++) {
            String option = argv[index];
            if (option.equals("--detach")) { if (detach) invalid(); detach = true; continue; }
            if (!VALUE_OPTIONS.contains(option) || index + 1 == argv.length || argv[index + 1].startsWith("--")
                    || options.putIfAbsent(option, argv[++index]) != null) invalid();
        }
        String operation = options.get("--operation"), provider = options.get("--provider");
        if (!OPERATIONS.contains(operation == null ? "" : operation) || provider == null || !provider.matches("[A-Za-z0-9_-]{1,16}")) invalid();
        Set<String> allowed = new HashSet<>(Set.of("--operation", "--provider"));
        Integer facility = null; CdsLogicalProfile profile = null; Set<CdsCategory> categories = Set.of();
        List<String> physicians = List.of(); Path selection = null, output = null; String recipient = null, audit = null;
        String submission = options.get("--submission-key"), run = options.get("--run"), artifact = null;
        if (submission != null) identifier(submission);
        if (operation.equals("export")) {
            allowed.addAll(Set.of("--facility", "--profile", "--categories", "--physicians", "--patients-file", "--recipient", "--audit-plan", "--submission-key"));
            if (options.containsKey("--physicians") == options.containsKey("--patients-file")) invalid();
            profile = CdsLogicalProfile.fromIdentifier(options.get("--profile"));
            categories = categories(options.getOrDefault("--categories", "all"));
            recipient = registry(options.get("--recipient")); audit = registry(options.get("--audit-plan"));
            if (options.containsKey("--facility")) facility = positive(options.get("--facility"));
            if (options.containsKey("--physicians")) {
                physicians = Arrays.asList(options.get("--physicians").split(",", -1));
                if (physicians.isEmpty() || physicians.size() > 100 || new HashSet<>(physicians).size() != physicians.size()
                        || physicians.stream().anyMatch(value -> !value.matches("[A-Za-z0-9_-]{1,16}"))) invalid();
                physicians = physicians.stream().sorted().toList();
            } else selection = absolute(options.get("--patients-file"));
        } else {
            if (run != null) { allowed.add("--run"); run(run); }
            if (operation.equals("status") && submission != null && run == null) allowed.add("--submission-key");
            else if (run == null || submission != null) invalid();
            if (operation.equals("download")) {
                allowed.addAll(Set.of("--artifact", "--output")); artifact = options.get("--artifact");
                identifier(artifact); output = absolute(options.get("--output"));
            }
        }
        if (detach && !Set.of("export", "resume").contains(operation) || !allowed.containsAll(options.keySet())) invalid();
        return new CdsExportCliArguments(operation, provider, facility, profile, categories, physicians, selection,
                recipient, audit, submission, run, detach, artifact, output);
    }
    /** Called only after root proof and protected selection-file capture; the immutable command fixes the actual selected IDs. */
    public CdsExportCommand command(List<Integer> capturedPatients, String verifiedNonce) {
        if (!operation.equals("export")) invalid();
        if (patientsFile == null && !capturedPatients.isEmpty()) invalid();
        return new CdsExportCommand(submissionKey == null ? verifiedNonce : submissionKey, provider, facility, profile,
                patientsFile == null ? CdsExportCommand.Selection.PHYSICIAN_ROSTER : CdsExportCommand.Selection.SELECTED_PATIENTS,
                physicians, capturedPatients, categories, recipient, auditPlan);
    }
    private static Set<CdsCategory> categories(String selected) {
        if (selected.equals("all")) return EnumSet.allOf(CdsCategory.class);
        var categories = EnumSet.noneOf(CdsCategory.class);
        for (String value : selected.split(",", -1)) if (!categories.add(CdsCategory.fromWireName(value))) invalid();
        if (!categories.contains(CdsCategory.DEMOGRAPHICS)) invalid(); return categories;
    }
    private static Integer positive(String value) {
        try { if (!value.matches("[0-9]{1,10}")) invalid(); int parsed = Integer.parseInt(value); if (parsed < 1) invalid(); return parsed; }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("CDS_EXPORT_ARGUMENTS_INVALID"); }
    }
    private static String registry(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) invalid(); return value;
    }
    private static void identifier(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) invalid();
    }
    private static Path absolute(String value) {
        if (value == null || value.isEmpty() || value.length() > 8192 || value.codePoints().anyMatch(Character::isISOControl)) invalid();
        Path path = Path.of(value); if (!path.isAbsolute() || !path.equals(path.normalize())) invalid(); return path;
    }
    private static void run(String value) {
        try { if (!UUID.fromString(value).toString().equals(value)) invalid(); }
        catch (RuntimeException malformed) { throw new IllegalArgumentException("CDS_EXPORT_ARGUMENTS_INVALID"); }
    }
    private static void invalid() { throw new IllegalArgumentException("CDS_EXPORT_ARGUMENTS_INVALID"); }
    @Override public String toString() { return "CdsExportCliArguments[protected]"; }
}
