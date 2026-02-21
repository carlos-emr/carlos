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
package io.github.carlos_emr.carlos.drools;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.kie.api.KieBase;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.io.ResourceType;
import org.kie.internal.utils.KieHelper;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

/**
 * Bridge utility for loading Drools rules and creating KieBase instances.
 *
 * <p>This class replaces the legacy {@code org.drools.io.RuleBaseLoader} from Drools 2.0
 * with equivalent functionality using the modern KIE API (Drools 7.74.1). It provides
 * static methods to load DRL (Drools Rule Language) rules from various sources and
 * compile them into executable {@link KieBase} instances.</p>
 *
 * <h3>Architecture Overview</h3>
 * <p>The KIE compilation pipeline uses {@link KieHelper} as follows:</p>
 * <ol>
 *   <li>A new {@link KieHelper} instance is created (lightweight, no global state)</li>
 *   <li>DRL content is added as a {@link ResourceType#DRL} resource</li>
 *   <li>Compilation results are verified for errors</li>
 *   <li>The {@link KieBase} is built from the helper for rule execution</li>
 * </ol>
 *
 * <h3>Thread Safety</h3>
 * <p>Uses {@link KieHelper} which creates self-contained rule compilations without
 * installing modules into the global KIE repository. This avoids unbounded metadata
 * growth and thread-safety issues with the shared {@code KieRepository}.</p>
 *
 * <h3>Usage in CARLOS EMR</h3>
 * <p>This helper is used by:</p>
 * <ul>
 *   <li>{@code PreventionDSImpl} - for loading immunization schedule rules (via compiled KieBase)</li>
 *   <li>{@code MeasurementFlowSheet} - for clinical measurement decision support</li>
 *   <li>{@code WorkFlowDSFactory} - for workflow automation rules</li>
 *   <li>{@code DroolsNumerator} variants - for clinical reporting rules</li>
 * </ul>
 *
 * @since 2026-02-17
 * @see RuleBaseFactory
 * @see org.kie.api.KieBase
 */
public final class DroolsHelper {

    private static final Logger log = MiscUtils.getLogger();

    /** Utility class - prevent instantiation. */
    private DroolsHelper() {
    }

    /**
     * Loads DRL rules from an InputStream and returns a compiled KieBase.
     *
     * <p>Reads the entire stream content as UTF-8 text and delegates to
     * {@link #createKieBaseFromDrl(String)} for compilation.</p>
     *
     * <p><strong>Important</strong>: This method does NOT close the InputStream.
     * The caller is responsible for closing it, typically via try-with-resources.</p>
     *
     * <p>Used by {@code PreventionDSImpl} to load prevention rules from the filesystem
     * and by {@code DroolsNumerator} variants to load measurement decision support rules.</p>
     *
     * @param inputStream InputStream containing DRL rule text (UTF-8 encoded)
     * @return KieBase compiled rule base ready for creating KieSessions
     * @throws DroolsCompilationException if the stream cannot be read or rule compilation fails
     */
    public static KieBase loadFromInputStream(InputStream inputStream) throws DroolsCompilationException {
        try {
            String drl = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            return createKieBaseFromDrl(drl);
        } catch (IOException e) {
            throw new DroolsCompilationException("Failed to read DRL from InputStream", e);
        }
    }

    /**
     * Loads DRL rules from a URL and returns a compiled KieBase.
     *
     * <p>Opens a stream to the URL, reads the content as UTF-8 text, and compiles
     * the DRL. The stream is automatically closed via try-with-resources.</p>
     *
     * <p>Typically used to load DRL files from the classpath, e.g.:
     * {@code DroolsHelper.loadFromUrl(getClass().getResource("/oscar/oscarPrevention/prevention.drl"))}</p>
     *
     * @param url URL pointing to a DRL rule file (classpath or filesystem)
     * @return KieBase compiled rule base ready for creating KieSessions
     * @throws DroolsCompilationException if the URL cannot be read or rule compilation fails
     */
    public static KieBase loadFromUrl(URL url) throws DroolsCompilationException {
        try (InputStream is = url.openStream()) {
            String drl = IOUtils.toString(is, StandardCharsets.UTF_8);
            return createKieBaseFromDrl(drl);
        } catch (IOException e) {
            throw new DroolsCompilationException("Failed to read DRL from URL", e);
        }
    }

    /**
     * Creates a KieBase from a single DRL string.
     *
     * <p>This is the core compilation method. It uses {@link KieHelper} which compiles
     * DRL rules without installing modules into the global KIE repository, avoiding
     * unbounded metadata growth in long-running server processes.</p>
     *
     * <p>Compilation steps:</p>
     * <ol>
     *   <li>Creates a new {@link KieHelper} instance (lightweight, no global state)</li>
     *   <li>Adds the DRL content as a {@link ResourceType#DRL} resource</li>
     *   <li>Verifies compilation results for errors</li>
     *   <li>Builds and returns the {@link KieBase}</li>
     * </ol>
     *
     * @param drl String containing complete DRL rules (typically includes package declaration,
     *            imports, and rule definitions; DRL without a package declaration defaults
     *            to the Drools "defaultpkg" package)
     * @return KieBase compiled rule base containing all successfully compiled rules
     * @throws DroolsCompilationException if the DRL contains syntax errors or compilation fails;
     *                                    the exception message includes the specific compilation errors
     */
    public static KieBase createKieBaseFromDrl(String drl) throws DroolsCompilationException {
        if (drl == null || drl.trim().isEmpty()) {
            throw new DroolsCompilationException("DRL content must not be null or empty");
        }

        // KieHelper creates a self-contained compilation context that does not
        // register modules in the global KIE repository, preventing unbounded
        // metadata growth over time.
        KieHelper kieHelper = new KieHelper();
        kieHelper.addContent(drl, ResourceType.DRL);

        // Check for compilation errors (warnings are acceptable)
        Results results = kieHelper.verify();
        if (results.hasMessages(Message.Level.ERROR)) {
            List<Message> errorMessages = results.getMessages(Message.Level.ERROR);
            log.error("DRL compilation errors: {}", errorMessages);
            throw new DroolsCompilationException("DRL compilation failed with "
                    + errorMessages.size() + " error(s): " + errorMessages);
        }

        return kieHelper.build();
    }

    /**
     * Loads a measurement decision support DRL file using the standard two-tier loading strategy.
     *
     * <p>This method centralizes the measurement rule loading logic previously duplicated
     * across {@code DroolsNumerator} (1-5) and {@code MeasurementFlowSheet}. It uses a
     * two-tier priority strategy:</p>
     * <ol>
     *   <li><strong>Filesystem</strong> -- If the {@code MEASUREMENT_DS_DIRECTORY} property is
     *       set in {@link CarlosProperties}, attempts to load the DRL file from that directory.
     *       The path is validated via {@link PathValidationUtils#validatePath(String, File)}.</li>
     *   <li><strong>Classpath fallback</strong> -- If no external file is found, loads from the
     *       classpath at {@code /oscar/oscarEncounter/oscarMeasurements/flowsheets/decisionSupport/}.</li>
     * </ol>
     *
     * @param drlFilename the DRL filename to load (e.g. "bp_check.drl")
     * @param classpathAnchor the class used to resolve classpath resources (determines the classloader)
     * @return KieBase the compiled rule base, or {@code null} if loading fails
     */
    public static KieBase loadMeasurementRuleBase(String drlFilename, Class<?> classpathAnchor) {
        KieBase measurementRuleBase = null;
        try {
            boolean fileFound = false;

            // Priority 1: Try loading from the external MEASUREMENT_DS_DIRECTORY
            String measurementDirPath = CarlosProperties.getInstance().getProperty("MEASUREMENT_DS_DIRECTORY");

            if (measurementDirPath != null) {
                File allowedDir = new File(measurementDirPath);
                File file = PathValidationUtils.validatePath(drlFilename, allowedDir);
                if (file.isFile() && file.canRead()) {
                    log.debug("Loading measurement DRL from file: {}", file.getName());
                    try (FileInputStream fis = new FileInputStream(file)) {
                        measurementRuleBase = loadFromInputStream(fis);
                    }
                    fileFound = true;
                }
            }

            // Priority 2: Fall back to classpath-bundled DRL resource
            if (!fileFound) {
                String resourcePath = "/oscar/oscarEncounter/oscarMeasurements/flowsheets/decisionSupport/" + drlFilename;
                URL url = classpathAnchor.getResource(resourcePath);
                if (url == null) {
                    log.warn("Measurement DRL resource not found on classpath: {}", resourcePath);
                    return null;
                }
                log.debug("Loading measurement DRL from classpath: {}", url.getFile());
                measurementRuleBase = loadFromUrl(url);
            }
        } catch (SecurityException e) {
            log.error("Security violation loading measurement DRL '{}': path traversal rejected", drlFilename, e);
            throw e;
        } catch (IOException | DroolsCompilationException e) {
            log.error("Failed to load measurement rule base for DRL file '{}'", drlFilename, e);
        }
        return measurementRuleBase;
    }

}
