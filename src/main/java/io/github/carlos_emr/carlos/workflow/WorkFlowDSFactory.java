/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * Originally written for the Department of Family Medicine, McMaster University.
 * Now maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 *
 * Modifications by CARLOS Contributors, 2026.
 */


package io.github.carlos_emr.carlos.workflow;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;

import org.kie.api.KieBase;
import io.github.carlos_emr.carlos.drools.DroolsCompilationException;
import io.github.carlos_emr.carlos.drools.DroolsHelper;
import io.github.carlos_emr.carlos.drools.RuleBaseFactory;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import io.github.carlos_emr.CarlosProperties;

/**
 * Factory for creating {@link WorkFlowDS} instances by loading and compiling DRL rule files.
 *
 * <p>This factory handles the discovery and loading of Drools Rule Language (DRL) files
 * used for workflow decision support in CARLOS EMR. It abstracts the rule loading
 * process from callers, which simply provide a DRL filename (e.g., {@code "prenatal.drl"})
 * and receive a fully initialized {@link WorkFlowDS} ready to execute rules against
 * {@link WorkFlowInfo} fact objects.</p>
 *
 * <h3>DRL Loading Priority</h3>
 * <p>The factory uses a two-tier loading strategy to allow site-specific rule customization:</p>
 * <ol>
 *   <li><strong>Filesystem first</strong> - If the {@code WORKFLOW_DS_DIRECTORY} property is
 *       set in {@link CarlosProperties}, the factory looks for the DRL file in that directory.
 *       This allows deployments to override default rules with site-specific versions without
 *       modifying the application archive.</li>
 *   <li><strong>Classpath fallback</strong> - If no filesystem file is found (or the property
 *       is not set), the factory loads the DRL from the classpath at
 *       {@code /oscar/oscarWorkflow/rules/<filename>}. These are the default rules
 *       bundled with the application.</li>
 * </ol>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * // Create a WorkFlowDS for prenatal workflow rules
 * WorkFlowDS workflowDS = WorkFlowDSFactory.getWorkFlowDS("prenatal.drl");
 *
 * // Execute the rules against a workflow fact object
 * WorkFlowInfo result = workflowDS.getMessages(workflowInfo);
 * }</pre>
 *
 * <h3>Migration Note</h3>
 * <p>Migrated from legacy Drools 2.0 {@code RuleBase} / {@code RuleBaseLoader} API to the
 * modern KIE API (Drools 10.0.0). Rule loading now delegates to
 * {@link DroolsHelper#loadFromInputStream(java.io.InputStream)} and
 * {@link DroolsHelper#loadFromUrl(URL)} instead of the removed
 * {@code org.drools.io.RuleBaseLoader}.</p>
 *
 * @since 2006-12-12
 * @see WorkFlowDS
 * @see WorkFlowInfo
 * @see io.github.carlos_emr.carlos.drools.DroolsHelper
 */
public final class WorkFlowDSFactory {

    /** Prevent instantiation of this static utility class. */
    private WorkFlowDSFactory() {
    }

    /**
     * Creates a {@link WorkFlowDS} instance by loading and compiling the specified DRL rule file.
     *
     * <p>This is the primary entry point for obtaining a workflow decision support engine.
     * It delegates to {@link #loadRuleBase(String)} to find and compile the DRL file,
     * then wraps the resulting {@link KieBase} in a new {@link WorkFlowDS} instance.</p>
     *
     * @param workflow String the DRL filename to load (e.g., {@code "prenatal.drl"});
     *                 resolved against the filesystem directory or classpath rules path
     * @return WorkFlowDS a new instance configured with the compiled rules, ready to
     *         execute against {@link WorkFlowInfo} objects via
     *         {@link WorkFlowDS#getMessages(WorkFlowInfo)}
     * @throws IllegalStateException if the DRL file cannot be found or compiled
     */
    public static WorkFlowDS getWorkFlowDS(String workflow) {
        KieBase ruleBase = loadRuleBase(workflow);
        if (ruleBase == null) {
            throw new IllegalStateException(
                    "Cannot create WorkFlowDS: failed to load rule base for '" + workflow
                            + "'. Check that the DRL file exists and is valid.");
        }
        return new WorkFlowDS(ruleBase);
    }


    /**
     * Loads and compiles a DRL rule file into a {@link KieBase}, checking the filesystem
     * first and falling back to the classpath.
     *
     * <p>The loading process follows a two-tier priority:</p>
     * <ol>
     *   <li><strong>Filesystem (highest priority)</strong> - Checks the directory specified by
     *       the {@code WORKFLOW_DS_DIRECTORY} property in {@link CarlosProperties}. If the
     *       property is set and the file exists and is readable at
     *       {@code <WORKFLOW_DS_DIRECTORY>/<filename>}, it is loaded via
     *       {@link DroolsHelper#loadFromInputStream(java.io.InputStream)}.</li>
     *   <li><strong>Classpath (fallback)</strong> - If no filesystem file was found, loads the
     *       DRL from the classpath resource at {@code /oscar/oscarWorkflow/rules/<filename>}
     *       via {@link DroolsHelper#loadFromUrl(URL)}.</li>
     * </ol>
     *
     * <p>Filesystem paths are validated using {@link PathValidationUtils#validatePath(String, File)}
     * to prevent path traversal attacks.</p>
     *
     * <p>If the DRL resource cannot be found on either the filesystem or the classpath, or
     * if I/O or compilation errors occur while loading or compiling the DRL, an
     * {@link IllegalStateException} is thrown. Callers should not rely on a {@code null}
     * return value to indicate failure.</p>
     *
     * @param drlFilename String the DRL filename to load (e.g., {@code "prenatal.drl"})
     * @return KieBase the compiled knowledge base containing the loaded rules
     * @throws IllegalStateException if the DRL resource cannot be found or if loading or
     *         compilation fails due to I/O or compilation errors
     */
    static KieBase loadRuleBase(String drlFilename) {
        String cacheKey = "workflow:" + drlFilename;
        KieBase cached = RuleBaseFactory.getRuleBase(cacheKey);
        if (cached != null) {
            return cached;
        }

        KieBase ruleBase = null;
        try {
            boolean fileFound = false;

            // Priority 1: Attempt to load DRL from the filesystem directory.
            // The WORKFLOW_DS_DIRECTORY property allows site-specific rule overrides
            // without modifying the deployed application.
            String workflowDirPath = CarlosProperties.getInstance().getProperty("WORKFLOW_DS_DIRECTORY");

            if (workflowDirPath != null) {
                File allowedDir = new File(workflowDirPath);
                File file = PathValidationUtils.validatePath(drlFilename, allowedDir);
                if (file.isFile() && file.canRead()) {
                    MiscUtils.getLogger().debug("Loading workflow from filesystem");
                    try (FileInputStream fis = new FileInputStream(file)) {
                        ruleBase = DroolsHelper.loadFromInputStream(fis);
                    }
                    fileFound = true;
                }
            }

            // Priority 2: Fall back to loading DRL from the classpath.
            // Default rules are bundled at /oscar/oscarWorkflow/rules/ within the WAR.
            if (!fileFound) {
                String resourcePath = "/oscar/oscarWorkflow/rules/" + drlFilename;
                MiscUtils.getLogger().debug("Looking for workflow DRL at classpath: {}", resourcePath);
                URL url = WorkFlowDSFactory.class.getResource(resourcePath);
                if (url == null) {
                    throw new IllegalStateException("Workflow DRL resource not found on classpath: " + resourcePath);
                }
                MiscUtils.getLogger().debug("Loading workflow DRL from: {}", url.getFile());
                ruleBase = DroolsHelper.loadFromUrl(url);
            }
        } catch (SecurityException e) {
            MiscUtils.getLogger().error("Security violation loading workflow DRL '{}': path traversal rejected", drlFilename, e);
            throw e;
        } catch (IOException | DroolsCompilationException e) {
            MiscUtils.getLogger().error("Failed to load workflow rule base for DRL file '{}'", drlFilename, e);
            throw new IllegalStateException("Failed to load workflow rule base for DRL file '" + drlFilename + "'", e);
        }
        if (ruleBase != null) {
            RuleBaseFactory.putRuleBase(cacheKey, ruleBase);
        }
        return ruleBase;
    }
}
