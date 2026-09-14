/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
package io.github.carlos_emr.carlos.deb;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.logging.Log;
import org.flywaydb.core.api.logging.LogFactory;
import org.flywaydb.core.internal.sqlscript.DefaultSqlScriptExecutor;

/** Test launcher without a test-framework dependency, run against the deployed WAR's Flyway API during packaging. */
public final class FlywayRunnerNotesTest {
    private static final String INDEX = "DB: Duplicate key name 'existing_index' "
            + "(SQL State: 42000 - Error Code: 1061)";
    private static final String COLUMN = "DB: Duplicate column name 'existing_column' "
            + "(SQL State: 42S21 - Error Code: 1060)";

    public static void main(String[] args) {
        RecordingLog sink = new RecordingLog();
        FlywayRunner.ExistingObjectNotes notes = new FlywayRunner.ExistingObjectNotes(type -> sink);
        Log logger = notes.createLogger(DefaultSqlScriptExecutor.class);
        require(summary(notes).isEmpty(), "no notes must produce no summary");
        logger.warn(INDEX);
        logger.warn(COLUMN);
        require(sink.events.isEmpty(), "expected existing-object notes must not look like warnings");
        require(summary(notes).equals("schema setup: 1 index(es) and 1 column(s) were already present; "
                + "kept their existing definitions." + System.lineSeparator()), "summary must explain both no-ops");

        // Only the exact message/state/code combination is an expected note.
        // Real data collisions, truncation, unknown columns and format changes
        // must remain visible rather than being caught by an overbroad filter.
        List<String> warnings = List.of(
                "DB: Duplicate entry '1' for key 'PRIMARY' (SQL State: 23000 - Error Code: 1062)",
                "DB: Data truncated for column 'value' (SQL State: 01000 - Error Code: 1265)",
                "DB: Unknown column 'missing' (SQL State: 42S22 - Error Code: 1054)",
                INDEX.replace("42000", "01000"), COLUMN.replace("1060", "1062"),
                "different prefix: " + INDEX, INDEX + " more details", "duplicate key", "DB: other warning");
        for (String warning : warnings) {
            logger.warn(warning);
            require(sink.events.getLast().equals("warn:" + warning), "warning must pass through: " + warning);
        }
        require(sink.events.size() == warnings.size(), "all unexpected warnings must be forwarded");

        // Error paths are never filtered, even when their text matches a note.
        logger.error(INDEX);
        require(sink.events.getLast().equals("error:" + INDEX), "duplicate index errors must pass through");
        Exception cause = new IllegalStateException("migration failed");
        logger.error(COLUMN, cause);
        require(sink.events.getLast().equals("error:" + COLUMN) && sink.cause == cause,
                "duplicate column errors must retain their exception");
        logger.debug("debug");
        logger.info("info");
        logger.notice("notice");
        require(logger.isDebugEnabled(), "debug setting must remain unchanged");
        require(sink.events.subList(sink.events.size() - 3, sink.events.size())
                .equals(List.of("debug:debug", "info:info", "notice:notice")), "other levels must pass through");

        // Other Flyway/application logger categories keep their original object.
        require(notes.createLogger(FlywayRunnerNotesTest.class) == sink, "other logger categories must be untouched");
        sink.warn(INDEX);
        require(sink.events.getLast().equals("warn:" + INDEX), "other categories must retain duplicate warnings");
        // Exercise Flyway's real logging lifecycle too: a direct setLogCreator
        // override passes decorator-only tests but disappears during migrate.
        Configuration configuration = (Configuration) Proxy.newProxyInstance(
                Configuration.class.getClassLoader(), new Class<?>[] {Configuration.class},
                (proxy, method, arguments) -> {
                    if ("getLoggers".equals(method.getName())) {
                        return new String[] {"console"};
                    }
                    throw new AssertionError("unexpected configuration access: " + method.getName());
                });
        RecordingLog resetSink = new RecordingLog();
        FlywayRunner.ExistingObjectNotes resetNotes = new FlywayRunner.ExistingObjectNotes(type -> resetSink);
        LogFactory.setFallbackLogCreator(resetNotes);
        LogFactory.setConfiguration(configuration);
        Log evolving = LogFactory.getLog(DefaultSqlScriptExecutor.class);
        evolving.warn(INDEX);
        LogFactory.setConfiguration(configuration);
        evolving.warn(COLUMN);
        require(resetSink.events.isEmpty(), "expected notes must remain summarized after configuration resets");
        require(summary(resetNotes).contains("1 index(es) and 1 column(s)"), "both notes must survive reset");
        evolving.warn("retained warning after reset");
        require(resetSink.events.equals(List.of("warn:retained warning after reset")),
                "real warnings must still pass through after a reset");
        System.out.println("PASS Flyway existing-object notes, warnings, errors, delegation and configuration resets");
    }

    private static String summary(FlywayRunner.ExistingObjectNotes notes) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        notes.printSummary(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingLog implements Log {
        final List<String> events = new ArrayList<>();
        Exception cause;
        @Override public boolean isDebugEnabled() { return true; }
        @Override public void debug(String message) { events.add("debug:" + message); }
        @Override public void info(String message) { events.add("info:" + message); }
        @Override public void notice(String message) { events.add("notice:" + message); }
        @Override public void warn(String message) { events.add("warn:" + message); }
        @Override public void error(String message) { events.add("error:" + message); }
        @Override public void error(String message, Exception exception) {
            events.add("error:" + message);
            cause = exception;
        }
    }
}
