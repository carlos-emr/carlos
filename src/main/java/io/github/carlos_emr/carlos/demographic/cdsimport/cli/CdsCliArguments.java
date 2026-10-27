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
package io.github.carlos_emr.carlos.demographic.cdsimport.cli;

import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Strict, bounded adapter arguments; source identity and clinical policy remain in shared services. */
public record CdsCliArguments(String operation, String provider, String runId, Map<String, String> values) {
    public static final Set<String> OPERATIONS = Set.of("import", "status", "resume", "cancel", "report");
    private static final Set<String> FLAGS = Set.of("--merge-existing", "--dry-run", "--no-match-providers", "--synthetic");
    private static final Set<String> OPTIONS = Set.of("--operation", "--provider", "--run", "--input", "--program",
            "--facility", "--source-namespace", "--source-profile", "--timeshift", "--course");

    public CdsCliArguments {
        values = Map.copyOf(values);
    }

    public static CdsCliArguments parse(String[] arguments) {
        if (arguments.length > 32) throw invalid();
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < arguments.length; i++) {
            String option = arguments[i];
            String value;
            if (FLAGS.contains(option)) value = "true";
            else if (OPTIONS.contains(option) && ++i < arguments.length) value = arguments[i];
            else throw invalid();
            if (value == null || value.isBlank() || value.length() > 4096
                    || value.chars().anyMatch(c -> c < 32 || c == 127)
                    || values.putIfAbsent(option, value) != null) throw invalid();
        }
        String operation = values.get("--operation"), provider = values.get("--provider");
        if (!OPERATIONS.contains(operation == null ? "" : operation)
                || provider == null || !provider.matches("[A-Za-z0-9_-]{1,16}")) throw invalid();
        if ("import".equals(operation)) {
            if (values.containsKey("--run") || !values.containsKey("--input")) throw invalid();
            token(values.get("--source-namespace"), 128);
            token(values.get("--source-profile"), 32);
            // Construction also rejects invalid synthetic/course/merge combinations.
            new CdsCliArguments(operation, provider, null, values).command("validation");
        } else {
            if (!values.keySet().equals(Set.of("--operation", "--provider", "--run"))) throw invalid();
            token(values.get("--run"), 128);
        }
        return new CdsCliArguments(operation, provider, values.get("--run"), values);
    }

    public CdsImportCommand command(String submissionKey) {
        if (!"import".equals(operation)) throw invalid();
        Path input = Path.of(values.get("--input"));
        if (!input.isAbsolute()) throw invalid();
        int shift;
        try { shift = Integer.parseInt(values.getOrDefault("--timeshift", "0")); }
        catch (NumberFormatException failure) { throw invalid(); }
        return new CdsImportCommand(input, submissionKey, provider, number("--program"), number("--facility"),
                values.get("--source-namespace"), values.get("--source-profile"), values.containsKey("--merge-existing"),
                values.containsKey("--dry-run"), !values.containsKey("--no-match-providers"), shift,
                number("--course"), values.containsKey("--synthetic"));
    }

    private Integer number(String option) {
        String value = values.get(option);
        if (value == null) return null;
        try {
            if (!value.matches("[0-9]{1,10}")) throw invalid();
            int selected = Integer.parseInt(value);
            if (selected < 1) throw invalid();
            return selected;
        } catch (NumberFormatException failure) { throw invalid(); }
    }

    private static void token(String value, int maximum) {
        if (value == null || value.length() > maximum || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) throw invalid();
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("CDS_ARGUMENTS_INVALID"); }
    @Override public String toString() { return "CdsCliArguments[protected]"; }
}
