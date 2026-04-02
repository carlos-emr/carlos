/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada

 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.form.pdfservlet;

import java.util.Map;
import java.util.Optional;

/**
 * Allowlist-based registry for {@link FrmPDFPostValueProcessor} implementations.
 *
 * <p>Replaces the previous pattern of constructing class names directly from user-supplied
 * {@code postProcessor} request parameters and loading them via {@code Class.forName()}.
 * That pattern allowed arbitrary class instantiation from user-controlled input, which
 * is a critical security vulnerability (SonarCloud rule javasecurity:S6173).
 *
 * <p>Only processors explicitly registered in {@link #ALLOWED} can be resolved.
 * Any unknown name returns {@link Optional#empty()} and the calling code silently
 * skips post-processing.
 *
 * <p>To add a new processor: add its short name (the value passed as the
 * {@code postProcessor} request parameter) and its class to the {@code ALLOWED} map below.
 *
 * @since 2026-04-02
 */
public final class PostProcessorRegistry {

    /**
     * Allowlist mapping short processor names (as provided by the {@code postProcessor}
     * request parameter) to their corresponding implementation classes.
     *
     * <p>New processors must be registered here before they can be used.
     * The map is intentionally empty because no {@link FrmPDFPostValueProcessor} implementations
     * currently exist in the codebase. Add entries here as implementations are introduced.
     *
     * <p>Implementation classes registered here must expose a public no-arg constructor
     * so that {@link Class#getConstructor()} can locate and invoke it.
     */
    private static final Map<String, Class<? extends FrmPDFPostValueProcessor>> ALLOWED =
            Map.of(
                // Add entries here as implementations are introduced:
                // e.g., "MyProcessor", MyProcessor.class
            );

    private PostProcessorRegistry() {
        // utility class — not instantiable
    }

    /**
     * Resolves a {@link FrmPDFPostValueProcessor} by its registered short name.
     *
     * @param name the short processor name from the {@code postProcessor} request parameter
     * @return an {@link Optional} containing a new processor instance if the name is
     *         on the allowlist, or {@link Optional#empty()} if the name is unknown or null
     * @throws RuntimeException wrapping a {@link ReflectiveOperationException} if an
     *         allowlisted class cannot be instantiated (e.g., no public no-arg constructor,
     *         or the constructor throws)
     */
    public static Optional<FrmPDFPostValueProcessor> resolve(String name) {
        if (name == null) {
            return Optional.empty();
        }
        Class<? extends FrmPDFPostValueProcessor> clazz = ALLOWED.get(name);
        if (clazz == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(clazz.getConstructor().newInstance());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to instantiate allowlisted post-processor: " + clazz.getName(), e);
        }
    }
}
