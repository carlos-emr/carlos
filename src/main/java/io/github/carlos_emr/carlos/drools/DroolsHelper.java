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
 * Replaces the legacy {@code org.drools.io.RuleBaseLoader} from Drools 2.0 with
 * equivalent functionality using the KIE API (Drools 7.x).
 *
 * Provides static methods to load DRL rules from InputStreams, URLs, and strings,
 * compiling them into KieBase instances for rule execution.
 *
 * @since 2026-02-17
 */
public final class DroolsHelper {

    private static final Logger log = MiscUtils.getLogger();
    private static final AtomicLong counter = new AtomicLong(0);

    private DroolsHelper() {
        // Utility class - prevent instantiation
    }

    /**
     * Loads DRL rules from an InputStream and returns a compiled KieBase.
     *
     * @param inputStream InputStream containing DRL rule text
     * @return KieBase compiled rule base ready for creating sessions
     * @throws RuntimeException if rule compilation fails
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
     * @param url URL pointing to a DRL rule file
     * @return KieBase compiled rule base ready for creating sessions
     * @throws RuntimeException if rule loading or compilation fails
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
     * @param drl String containing DRL rules
     * @return KieBase compiled rule base
     * @throws RuntimeException if compilation produces errors
     */
    public static KieBase createKieBaseFromDrl(String drl) {
        KieServices kieServices = KieServices.Factory.get();

        // Use a unique ReleaseId per compilation to avoid thread-safety issues
        // with the shared default KieRepository
        long id = counter.incrementAndGet();
        ReleaseId releaseId = kieServices.newReleaseId(
                "io.github.carlos_emr.carlos.drools",
                "dynamic-rules",
                "1.0." + id);

        KieFileSystem kfs = kieServices.newKieFileSystem();
        kfs.generateAndWritePomXML(releaseId);
        kfs.write("src/main/resources/rules.drl", drl);

        KieBuilder kieBuilder = kieServices.newKieBuilder(kfs).buildAll();
        Results results = kieBuilder.getResults();

        if (results.hasMessages(Message.Level.ERROR)) {
            String errors = results.getMessages(Message.Level.ERROR).toString();
            log.error("DRL compilation errors: {}", errors);
            throw new RuntimeException("DRL compilation errors: see log for details");
        }

        KieContainer kieContainer = kieServices.newKieContainer(releaseId);
        return kieContainer.getKieBase();
    }

    /**
     * Creates a KieBase from multiple DRL rule strings combined into a single rule set.
     *
     * Used by programmatic rule builders (e.g., RuleBaseCreator, DSGuidelineDrools)
     * that generate individual rule strings and need them compiled together.
     *
     * @param packageName String the DRL package name
     * @param imports List of String fully qualified class names to import
     * @param ruleStrings List of String individual DRL rule definitions
     * @return KieBase compiled rule base
     * @throws RuntimeException if compilation produces errors
     */
    public static KieBase createKieBaseFromRules(String packageName, List<String> imports, List<String> ruleStrings) {
        StringBuilder drl = new StringBuilder();
        drl.append("package ").append(packageName).append(";\n\n");

        for (String imp : imports) {
            drl.append("import ").append(imp).append(";\n");
        }
        drl.append("\n");

        for (String rule : ruleStrings) {
            drl.append(rule).append("\n\n");
        }

        log.debug("Generated DRL:\n{}", drl);
        return createKieBaseFromDrl(drl.toString());
    }
}
