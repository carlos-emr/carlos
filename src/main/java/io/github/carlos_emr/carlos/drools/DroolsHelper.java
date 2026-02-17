/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * CARLOS EMR - https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.drools;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.Results;
import org.kie.api.runtime.KieContainer;

import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Bridge utility for loading Drools rules and creating KieBase instances.
 *
 * <p>This class replaces the legacy {@code org.drools.io.RuleBaseLoader} from Drools 2.0
 * with equivalent functionality using the modern KIE API (Drools 7.74.1). It provides
 * static methods to load DRL (Drools Rule Language) rules from various sources and
 * compile them into executable {@link KieBase} instances.</p>
 *
 * <h3>Architecture Overview</h3>
 * <p>The KIE compilation pipeline works as follows:</p>
 * <ol>
 *   <li>DRL content is written to a virtual {@link KieFileSystem}</li>
 *   <li>A {@link KieBuilder} compiles the DRL into an internal knowledge module</li>
 *   <li>A {@link KieContainer} is created from the compiled module</li>
 *   <li>The {@link KieBase} is extracted from the container for rule execution</li>
 * </ol>
 *
 * <h3>Thread Safety</h3>
 * <p>Each compilation uses a unique {@link ReleaseId} generated via an {@link AtomicLong}
 * counter. This prevents collisions when multiple threads compile rules concurrently,
 * as the shared {@code KieRepository} indexes modules by their release ID.</p>
 *
 * <h3>Usage in CARLOS EMR</h3>
 * <p>This helper is used by:</p>
 * <ul>
 *   <li>{@link RuleBaseFactory} - for caching compiled knowledge bases</li>
 *   <li>{@code PreventionDSImpl} - for loading immunization schedule rules</li>
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

    /**
     * Thread-safe counter for generating unique ReleaseId version numbers.
     * Each compilation increments this counter to avoid collisions in the
     * shared KIE repository when multiple threads compile rules simultaneously.
     */
    private static final AtomicLong counter = new AtomicLong(0);

    /** Utility class - prevent instantiation. */
    private DroolsHelper() {
    }

    /**
     * Loads DRL rules from an InputStream and returns a compiled KieBase.
     *
     * <p>Reads the entire stream content as UTF-8 text and delegates to
     * {@link #createKieBaseFromDrl(String)} for compilation. The caller is
     * responsible for closing the InputStream after this method returns.</p>
     *
     * <p>Used by {@code PreventionDSImpl} to load prevention rules from the filesystem
     * and by {@code DroolsNumerator} variants to load measurement decision support rules.</p>
     *
     * @param inputStream InputStream containing DRL rule text (UTF-8 encoded)
     * @return KieBase compiled rule base ready for creating KieSessions
     * @throws RuntimeException if the stream cannot be read or rule compilation fails
     */
    public static KieBase loadFromInputStream(InputStream inputStream) {
        try {
            String drl = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            return createKieBaseFromDrl(drl);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read DRL from InputStream", e);
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
     * @throws RuntimeException if the URL cannot be read or rule compilation fails
     */
    public static KieBase loadFromUrl(URL url) {
        try (InputStream is = url.openStream()) {
            String drl = IOUtils.toString(is, StandardCharsets.UTF_8);
            return createKieBaseFromDrl(drl);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read DRL from URL", e);
        }
    }

    /**
     * Creates a KieBase from a single DRL string.
     *
     * <p>This is the core compilation method. It performs the following steps:</p>
     * <ol>
     *   <li>Obtains the singleton {@link KieServices} instance</li>
     *   <li>Generates a unique {@link ReleaseId} using the atomic counter to avoid
     *       thread-safety issues with the shared KIE repository</li>
     *   <li>Creates a virtual {@link KieFileSystem} and writes the DRL content
     *       to {@code src/main/resources/rules.drl}</li>
     *   <li>Builds the rules using {@link KieBuilder#buildAll()}</li>
     *   <li>Checks for compilation errors and throws if any are found</li>
     *   <li>Creates a {@link KieContainer} and extracts the default {@link KieBase}</li>
     * </ol>
     *
     * @param drl String containing complete DRL rules (must include package declaration,
     *            imports, and rule definitions)
     * @return KieBase compiled rule base containing all successfully compiled rules
     * @throws RuntimeException if the DRL contains syntax errors or compilation fails;
     *                          error details are logged at ERROR level before throwing
     */
    public static KieBase createKieBaseFromDrl(String drl) {
        KieServices kieServices = KieServices.Factory.get();

        // Generate a unique ReleaseId per compilation to prevent thread collisions.
        // The KIE repository is a global singleton; using the same ReleaseId from
        // concurrent threads would cause unpredictable behavior.
        long id = counter.incrementAndGet();
        ReleaseId releaseId = kieServices.newReleaseId(
                "io.github.carlos_emr.carlos.drools",
                "dynamic-rules",
                "1.0." + id);

        // Write DRL content to a virtual filesystem for KIE compilation.
        // The path "src/main/resources/rules.drl" follows the KIE convention
        // for automatic resource discovery within a KIE module.
        KieFileSystem kfs = kieServices.newKieFileSystem();
        kfs.generateAndWritePomXML(releaseId);
        kfs.write("src/main/resources/rules.drl", drl);

        // Compile the DRL rules
        KieBuilder kieBuilder = kieServices.newKieBuilder(kfs).buildAll();
        Results results = kieBuilder.getResults();

        // Check for compilation errors (warnings are acceptable)
        if (results.hasMessages(Message.Level.ERROR)) {
            String errors = results.getMessages(Message.Level.ERROR).toString();
            log.error("DRL compilation errors: {}", errors);
            throw new RuntimeException("DRL compilation errors: see log for details");
        }

        // Create a container from the compiled module and extract the KieBase
        KieContainer kieContainer = kieServices.newKieContainer(releaseId);
        return kieContainer.getKieBase();
    }

    /**
     * Creates a KieBase from multiple DRL rule strings combined into a single rule set.
     *
     * <p>Assembles a complete DRL file from the given package name, imports, and
     * individual rule strings, then delegates to {@link #createKieBaseFromDrl(String)}
     * for compilation.</p>
     *
     * <p>This method is available for programmatic rule builders (e.g., {@code RuleBaseCreator},
     * {@code DSGuidelineDrools}) that generate individual rule strings and need them
     * compiled together into a single knowledge base. Currently unused but retained
     * as a utility for future refactoring of the programmatic DRL generation pipeline.</p>
     *
     * @param packageName String the DRL package name (e.g., "preventions", "measurements")
     * @param imports List of String fully qualified class names to import
     *                (e.g., "io.github.carlos_emr.carlos.prevention.Prevention")
     * @param ruleStrings List of String individual DRL rule definitions, each containing
     *                    a complete {@code rule "name" when ... then ... end} block
     * @return KieBase compiled rule base containing all provided rules
     * @throws RuntimeException if compilation produces errors
     */
    public static KieBase createKieBaseFromRules(String packageName, List<String> imports, List<String> ruleStrings) {
        StringBuilder drl = new StringBuilder();

        // Build the DRL package header
        drl.append("package ").append(packageName).append(";\n\n");

        // Add import statements for all required fact classes
        for (String imp : imports) {
            drl.append("import ").append(imp).append(";\n");
        }
        drl.append("\n");

        // Append each individual rule definition
        for (String rule : ruleStrings) {
            drl.append(rule).append("\n\n");
        }

        log.debug("Generated DRL:\n{}", drl);
        return createKieBaseFromDrl(drl.toString());
    }
}
