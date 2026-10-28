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
package io.github.carlos_emr.carlos.casemgmt.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Preserves the source chart's signed program while a PMM copy selects a different target patient.
 * The source token is validated against the stored note's patient and never becomes the target
 * request's chart context or changes its remembered program.
 *
 * @since 2026-10-25
 */
public final class NoteAttachContext {
    public static final String SOURCE_PARAMETER = "sourceChartContext";

    private NoteAttachContext() { }

    /** Returns the validated source token, or null for a genuinely tokenless legacy popup. */
    public static String sourceToken(HttpServletRequest request, String patient) {
        if (request.getParameterValues(SOURCE_PARAMETER) == null) return null;
        return ChartProgramContext.token(new SourceRequest(request, patient));
    }

    /** Returns the supplied source program; a valid legacy token may itself capture null. */
    public static String sourceProgram(HttpServletRequest request, String patient) {
        if (request.getParameterValues(SOURCE_PARAMETER) == null) return null;
        return ChartProgramContext.resolve(new SourceRequest(request, patient));
    }

    /** Presents all parameter views consistently to the shared signed-token validator. */
    private static final class SourceRequest extends HttpServletRequestWrapper {
        private final Map<String, String[]> parameters;

        SourceRequest(HttpServletRequest request, String patient) {
            super(request);
            Map<String, String[]> source = new LinkedHashMap<>(request.getParameterMap());
            source.put("demographicNo", new String[]{patient});
            source.put("demographic_no", new String[]{patient});
            source.put(ChartProgramContext.PARAMETER, request.getParameterValues(SOURCE_PARAMETER));
            parameters = Collections.unmodifiableMap(source);
        }

        @Override
        public String getParameter(String name) {
            String[] values = getParameterValues(name);
            return values == null || values.length == 0 ? null : values[0];
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = parameters.get(name);
            return values == null ? null : values.clone();
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> copy = new LinkedHashMap<>();
            parameters.forEach((name, values) -> copy.put(name, values == null ? null : values.clone()));
            return Collections.unmodifiableMap(copy);
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(parameters.keySet());
        }
    }
}
