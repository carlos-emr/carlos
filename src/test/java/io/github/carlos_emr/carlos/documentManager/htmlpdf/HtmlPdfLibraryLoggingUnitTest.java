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
package io.github.carlos_emr.carlos.documentManager.htmlpdf;

import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderRequest;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderResult;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderSource;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.service.HtmlPdfTestServices;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xhtmlrenderer.util.Configuration;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flying Saucer can log the text it prints (with {@code xr.renderer.replace-missing-characters} on, every
 * line that has a character missing from its font), and the documents are patients' records. These tests
 * pin the three things that keep that out of the logs: the module never switches the library's logging or
 * that replacement on, a render that meets characters no font draws logs no document text through either
 * of the library's two logging routes, and {@code log4j2.xml} holds the library at WARN whatever
 * {@code LOG_VERBOSITY} says.
 */
@Tag("unit")
@Tag("htmlpdf")
@Tag("security")
@DisplayName("HTML-to-PDF library logging")
class HtmlPdfLibraryLoggingUnitTest {

    private static final String LIBRARY_LOGGER = "org.xhtmlrenderer";

    @TempDir
    Path webRoot;

    /** Collects every {@code java.util.logging} record of the library, with its parameters. */
    private static final class RecordingHandler extends Handler {
        final List<String> texts = new CopyOnWriteArrayList<>();

        @Override
        public void publish(LogRecord record) {
            texts.add(record.getMessage() + " " + Arrays.toString(record.getParameters())
                    + (record.getThrown() == null ? "" : " " + record.getThrown().getMessage()));
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    /** The level the application's own {@code log4j2.xml} gives the library, which the test configuration does not load. */
    private static org.apache.logging.log4j.Level levelInApplicationConfig() throws Exception {
        return levelInApplicationConfig(LIBRARY_LOGGER);
    }

    /** The level the application's own {@code log4j2.xml} gives a logger of the library. */
    private static org.apache.logging.log4j.Level levelInApplicationConfig(String loggerName) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Element configuration = factory.newDocumentBuilder().parse(Path.of("src/main/resources/log4j2.xml").toFile()).getDocumentElement();
        NodeList loggers = configuration.getElementsByTagName("Logger");
        for (int i = 0; i < loggers.getLength(); i++) {
            Element logger = (Element) loggers.item(i);
            if (loggerName.equals(logger.getAttribute("name"))) {
                return org.apache.logging.log4j.Level.toLevel(logger.getAttribute("level"));
            }
        }
        throw new AssertionError("log4j2.xml has no logger for " + loggerName);
    }

    /** Sets the level of the library's logger in the running configuration; the capture removes the logger when it closes. */
    private static void setLibraryLevel(org.apache.logging.log4j.Level level) {
        setLevel(LIBRARY_LOGGER, level);
    }

    private static void setLevel(String loggerName, org.apache.logging.log4j.Level level) {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        LoggerConfig config = context.getConfiguration().getLoggerConfig(loggerName);
        assertThat(config.getName()).as("logger config made by the capture").isEqualTo(loggerName);
        config.setLevel(level);
        context.updateLoggers();
    }

    /** A document with characters no bundled font draws, a CSS value that names a file and an unknown font. */
    private static String documentWithMissingGlyphs() {
        return "<html><body><p>FAKE-Patient " + new String(Character.toChars(0xE000)) + " "
                + new String(Character.toChars(0x378)) + " FAKE-Name</p>"
                + "<p style=\"font-family: 'FAKE-Unknown Font'; background: url(FAKE-patient-123.png)\">FAKE-Styled</p></body></html>";
    }

    private HtmlPdfRenderResult render(String html) throws Exception {
        return HtmlPdfTestServices.newService().render(new HtmlPdfRenderRequest(html, webRoot, List.of(webRoot), HtmlPdfRenderSource.FORM));
    }

    @Test
    @DisplayName("should log no document text through either of the library's logging routes, at the level the application configures")
    void shouldLogNoDocumentText_whenGlyphMissing() throws Exception {
        Logger jul = Logger.getLogger(LIBRARY_LOGGER);
        RecordingHandler handler = new RecordingHandler();
        handler.setLevel(Level.ALL);
        Level previousLevel = jul.getLevel();
        jul.setLevel(Level.ALL);
        jul.addHandler(handler);
        try (LogCapture log4j = LogCapture.forLogger(LIBRARY_LOGGER)) {
            setLibraryLevel(levelInApplicationConfig());
            // Positive controls: the capture sees what the library's loggers write at that level, so silence means silence.
            jul.warning("control-jul");
            LogManager.getLogger(LIBRARY_LOGGER).warn("control-log4j");
            assertThat(handler.texts).contains("control-jul null");
            assertThat(log4j.messages()).contains("control-log4j");

            HtmlPdfRenderResult result = render(documentWithMissingGlyphs());

            assertThat(result.pdf()).isNotEmpty();
            List<String> everything = new ArrayList<>(handler.texts);
            everything.addAll(log4j.messages());
            assertThat(everything).noneMatch(text -> text.contains("FAKE-"));
        } finally {
            jul.removeHandler(handler);
            jul.setLevel(previousLevel);
        }
    }

    @Test
    @DisplayName("should log nothing of an SVG that cannot be drawn, through either of the library's routes, at the levels the application configures")
    void shouldLogNoSvgContent_whenSvgCannotBeDrawn() throws Exception {
        String svgLogger = "org.xhtmlrenderer.pdf.SvgImage";
        Files.writeString(webRoot.resolve("broken.svg"), "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" "
                + "width=\"20\" height=\"20\"><use xlink:href=\"#FAKE-Patient-Fragment\"/></svg>");
        Logger jul = Logger.getLogger(LIBRARY_LOGGER);
        RecordingHandler handler = new RecordingHandler();
        handler.setLevel(Level.ALL);
        Level previousLevel = jul.getLevel();
        jul.setLevel(Level.ALL);
        jul.addHandler(handler);
        try (LogCapture library = LogCapture.forLogger(LIBRARY_LOGGER); LogCapture svg = LogCapture.forLogger(svgLogger)) {
            setLibraryLevel(levelInApplicationConfig());
            setLevel(svgLogger, levelInApplicationConfig(svgLogger));
            // Positive control: Flying Saucer's own logger for SVG writes what the library wrote there before it was pinned.
            org.slf4j.LoggerFactory.getLogger(svgLogger).warn("control-slf4j-warn FAKE-Control");
            org.slf4j.LoggerFactory.getLogger(svgLogger).error("control-slf4j-error");
            assertThat(svg.messages()).contains("control-slf4j-error").doesNotContain("control-slf4j-warn FAKE-Control");

            HtmlPdfRenderResult result = render("<html><body><p>FAKE-Body</p><img src=\"broken.svg\"/></body></html>");

            assertThat(result.pdf()).isNotEmpty();
            assertThat(result.degradations()).as("one SVG is left out; the document is not simplified")
                    .containsExactly(io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation.IMAGE_UNAVAILABLE);
            List<String> everything = new ArrayList<>(handler.texts);
            everything.addAll(library.messages());
            everything.addAll(svg.messages());
            assertThat(everything).noneMatch(text -> text.contains("FAKE-"));
        } finally {
            jul.removeHandler(handler);
            jul.setLevel(previousLevel);
        }
    }

    @Test
    @DisplayName("should hold the SVG logger of the library at ERROR in the application's log4j2.xml")
    void shouldPinSvgLoggerAtError_inLog4jConfig() throws Exception {
        assertThat(levelInApplicationConfig("org.xhtmlrenderer.pdf.SvgImage")).isEqualTo(org.apache.logging.log4j.Level.ERROR);
    }

    @Test
    @DisplayName("should show why the library is held at WARN: at TRACE it logs the CSS values of the document")
    void shouldExplainThePin_whenLibraryLogsAtTrace() throws Exception {
        try (LogCapture log4j = LogCapture.forLogger(LIBRARY_LOGGER)) {
            setLibraryLevel(org.apache.logging.log4j.Level.ALL);

            render(documentWithMissingGlyphs());

            // If a newer Flying Saucer stops logging document values, the pin is no longer needed and this is skipped.
            Assumptions.assumeTrue(log4j.messages().stream().anyMatch(text -> text.contains("FAKE-")),
                    "the library no longer logs document text at TRACE");
            assertThat(levelInApplicationConfig()).as("the application's setting for the library")
                    .isEqualTo(org.apache.logging.log4j.Level.WARN);
        }
    }

    @Test
    @DisplayName("should leave the library's logging and missing-character switches off")
    void shouldLeaveLibraryLoggingSwitchesOff_forRenders() throws Exception {
        HtmlPdfTestServices.newService().render(new HtmlPdfRenderRequest(
                "<html><body><p>FAKE-Body " + new String(Character.toChars(0xE000)) + "</p></body></html>", webRoot, List.of(webRoot),
                HtmlPdfRenderSource.FORM));

        assertThat(System.getProperty("xr.renderer.replace-missing-characters")).isNull();
        assertThat(System.getProperty("xr.util-logging.loggingEnabled")).isNull();
        assertThat(Configuration.isTrue("xr.renderer.replace-missing-characters", true)).isFalse();
        assertThat(Configuration.isTrue("xr.util-logging.loggingEnabled", true)).isFalse();
    }

    @Test
    @DisplayName("should hold the library's logger at WARN in the application's log4j2.xml")
    void shouldPinLibraryLoggerAtWarn_inLog4jConfig() throws Exception {
        // The tests run with src/test/resources/log4j2.xml, so the application's own file is read directly.
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Element configuration = factory.newDocumentBuilder().parse(Path.of("src/main/resources/log4j2.xml").toFile()).getDocumentElement();

        NodeList loggers = configuration.getElementsByTagName("Logger");
        List<String> levels = new ArrayList<>();
        for (int i = 0; i < loggers.getLength(); i++) {
            Element logger = (Element) loggers.item(i);
            if (LIBRARY_LOGGER.equals(logger.getAttribute("name"))) {
                levels.add(logger.getAttribute("level"));
            }
        }

        assertThat(levels).as("exactly one <Logger name=\"org.xhtmlrenderer\">").containsExactly("warn");
    }
}
