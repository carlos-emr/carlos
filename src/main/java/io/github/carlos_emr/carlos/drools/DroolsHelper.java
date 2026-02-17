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

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.Logger;
import org.kie.api.KieBase;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.utils.KieHelper;

import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Utility class for loading and compiling Drools 7.x rules using the KIE API.
 *
 * <p>This class replaces the legacy Drools 2.0 {@code org.drools.io.RuleBaseLoader}
 * with modern KIE API equivalents. It provides methods for building {@link KieBase}
 * instances from DRL content strings, input streams, and URLs.</p>
 *
 * @since 2026-02-17
 * @see org.kie.api.KieBase
 * @see org.kie.api.runtime.KieSession
 */
public final class DroolsHelper {

    private static final Logger log = MiscUtils.getLogger();

    private DroolsHelper() {
        // Utility class - prevent instantiation
    }

    /**
     * Builds a KieBase from a DRL content string.
     *
     * @param drlContent String the DRL rule content
     * @return KieBase the compiled knowledge base
     * @throws DroolsCompilationException if the DRL content has compilation errors
     */
    public static KieBase buildKieBase(String drlContent) throws DroolsCompilationException {
        KieHelper kieHelper = new KieHelper();
        kieHelper.addContent(drlContent, ResourceType.DRL);
        return buildAndValidate(kieHelper);
    }

    /**
     * Builds a KieBase from an InputStream containing DRL content.
     *
     * @param inputStream InputStream containing the DRL rule content
     * @return KieBase the compiled knowledge base
     * @throws DroolsCompilationException if the DRL content has compilation errors
     */
    public static KieBase loadFromInputStream(InputStream inputStream) throws DroolsCompilationException {
        try {
            String drlContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return buildKieBase(drlContent);
        } catch (DroolsCompilationException e) {
            throw e;
        } catch (Exception e) {
            throw new DroolsCompilationException("Failed to read DRL from input stream", e);
        }
    }

    /**
     * Builds a KieBase from a URL pointing to a DRL resource.
     *
     * @param url URL pointing to the DRL file
     * @return KieBase the compiled knowledge base
     * @throws DroolsCompilationException if the DRL content has compilation errors
     */
    public static KieBase loadFromUrl(URL url) throws DroolsCompilationException {
        try (InputStream is = url.openStream()) {
            return loadFromInputStream(is);
        } catch (DroolsCompilationException e) {
            throw e;
        } catch (Exception e) {
            throw new DroolsCompilationException("Failed to load DRL from resource", e);
        }
    }

    /**
     * Creates a new KieSession from the given KieBase.
     *
     * <p>The caller is responsible for calling {@code kieSession.dispose()}
     * after rule execution to release resources.</p>
     *
     * @param kieBase KieBase the compiled knowledge base
     * @return KieSession a new stateful session for rule execution
     */
    public static KieSession newSession(KieBase kieBase) {
        return kieBase.newKieSession();
    }

    private static KieBase buildAndValidate(KieHelper kieHelper) throws DroolsCompilationException {
        Results results = kieHelper.verify();
        if (results.hasMessages(Message.Level.ERROR)) {
            StringBuilder sb = new StringBuilder("Drools compilation errors:\n");
            for (Message msg : results.getMessages(Message.Level.ERROR)) {
                sb.append("  - ").append(msg.getText()).append("\n");
            }
            throw new DroolsCompilationException(sb.toString());
        }
        return kieHelper.build();
    }
}
