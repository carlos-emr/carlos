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

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Refuses forged or patient-mismatched chart contexts before a chart or prescribing action runs.
 * Requests without a window context preserve the legacy standalone entry points.
 *
 * @since 2026-10-24
 */
public final class ChartProgramContextFilter implements Filter {
    private static final Pattern REST_PRINT_PATH = Pattern.compile("/rs/recordUX/([0-9]+)/print");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        if (http.getParameterValues(ChartProgramContext.PARAMETER) != null) {
            try {
                ChartProgramContext.token(contextValidationRequest(http));
            } catch (SecurityException invalidContext) {
                ((HttpServletResponse) response).sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Invalid chart context");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** REST print identifies its patient in the CXF path; query aliases cannot select another chart. */
    private static HttpServletRequest contextValidationRequest(HttpServletRequest request) {
        if (!("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))
                || !"/ws".equals(request.getServletPath()) || request.getPathInfo() == null) return request;
        Matcher path = REST_PRINT_PATH.matcher(request.getPathInfo());
        if (!path.matches()) return request;
        String patient;
        try {
            int identifier = Integer.parseInt(path.group(1));
            if (identifier <= 0) return request;
            patient = Integer.toString(identifier);
        } catch (NumberFormatException invalidPatient) {
            return request;
        }
        // Validation only: the resource receives the original request and authorizes its path patient.
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getParameter(String name) {
                return isPatientParameter(name) ? patient : super.getParameter(name);
            }

            @Override
            public String[] getParameterValues(String name) {
                return isPatientParameter(name) ? new String[]{patient} : super.getParameterValues(name);
            }

            @Override
            public Map<String, String[]> getParameterMap() {
                Map<String, String[]> parameters = new HashMap<>(super.getParameterMap());
                parameters.put("demographicNo", new String[]{patient});
                parameters.put("demographic_no", new String[]{patient});
                return Collections.unmodifiableMap(parameters);
            }

            @Override
            public Enumeration<String> getParameterNames() {
                return Collections.enumeration(getParameterMap().keySet());
            }

            private boolean isPatientParameter(String name) {
                return "demographicNo".equals(name) || "demographic_no".equals(name);
            }
        };
    }
}
