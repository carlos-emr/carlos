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
 * This software was written for the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.form.pdfservlet;

import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.utility.LogSanitizer;

/**
 * Shared allowlist registry and factory for {@link FrmPDFPostValueProcessor} implementations.
 *
 * <p>Centralises the allowed-processor map and instantiation logic used by both
 * {@code EFormPDFServlet} and {@code FrmPDFServlet}. Both servlets accept a
 * user-supplied {@code postProcessor} request parameter; this class provides a
 * single authoritative list of permitted processors so the two servlets cannot
 * diverge.</p>
 *
 * <p>To add a new post-processor:
 * <ol>
 *   <li>Implement {@link FrmPDFPostValueProcessor} with a public no-arg constructor.</li>
 *   <li>Add a new entry to {@link #ALLOWED_PROCESSORS} mapping the short name (as it
 *       will appear in the {@code postProcessor} request parameter) to the class.</li>
 * </ol>
 * </p>
 *
 * <p>Usage in servlets:</p>
 * <pre>
 * String processorName = req.getParameter("postProcessor" + suffix);
 * if (processorName != null) {
 *     props = FrmPDFPostProcessorRegistry.apply(processorName, props, log);
 * }
 * </pre>
 *
 * @see FrmPDFPostValueProcessor
 * @since 2026-04-10
 */
public final class FrmPDFPostProcessorRegistry {

    /**
     * Allowlist of valid post-processor short names mapped to their concrete classes.
     * Only processors explicitly listed here may be instantiated via the
     * {@code postProcessor} request parameter.
     *
     * <p>The map is intentionally empty: no {@link FrmPDFPostValueProcessor} implementations
     * currently exist in the codebase. Future processors must be added here before they
     * can be used.</p>
     */
    static final Map<String, Class<? extends FrmPDFPostValueProcessor>> ALLOWED_PROCESSORS = Map.of();

    private FrmPDFPostProcessorRegistry() {
        // utility class — no instances
    }

    /**
     * Looks up {@code processorName} in the allowlist, instantiates it, and runs it
     * against {@code props}.
     *
     * <p>If the name is not on the allowlist, logs a warning and returns {@code props}
     * unchanged. If instantiation or execution fails, logs a warning and returns
     * {@code props} unchanged so the form renders without post-processing.</p>
     *
     * @param processorName String the short name supplied via the {@code postProcessor}
     *                      request parameter; must not be {@code null}
     * @param props         Properties the current form properties to process; must not be {@code null}
     * @param log           Logger the logger of the calling servlet (used for warn/error messages)
     * @return Properties the (possibly updated) properties; never {@code null}
     */
    public static Properties apply(String processorName, Properties props, Logger log) {
        Class<? extends FrmPDFPostValueProcessor> clazz = ALLOWED_PROCESSORS.get(processorName);
        if (clazz != null) {
            try {
                FrmPDFPostValueProcessor pp = clazz.getConstructor().newInstance();
                return pp.process(props);
            } catch (Exception e) {
                log.warn("Post-processor '{}' failed during execution - form rendered without post-processing",
                        LogSanitizer.sanitize(processorName), e);
            }
        } else {
            log.warn("Post-processor '{}' is not in the allowlist and will not be executed",
                    LogSanitizer.sanitize(processorName));
        }
        return props;
    }
}
