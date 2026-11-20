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

package io.github.carlos_emr.carlos.report.oscarMeasurements.pageUtil;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Reads the Struts 1 mapped-property fields ({@code value(key)}) that the CDM report JSPs still
 * post, for example {@code value(CDMgroup)} and {@code value(measurementType0)}.
 *
 * <p>Struts 7's {@code params} interceptor never binds these names. Its accepted-name pattern
 * allows only a numeric or quoted key inside the parentheses ({@code value(0)},
 * {@code value('key')}), and {@code struts.parameters.requireAnnotations=true} also rejects the
 * un-annotated {@code setValue(String, Object)}. The actions' {@code values} map therefore stayed
 * empty and every CDM screen rendered no rows. The actions read the raw request parameter
 * instead. The value is used only as a lookup key or a trusted-by-validation string, exactly as
 * the Struts 1 form bean would have supplied it.</p>
 *
 * @since 2026-09-24
 */
public final class MappedFormValues {

    private MappedFormValues() {
    }

    /**
     * Returns the posted value of {@code value(key)}.
     *
     * @param request the current request; may be {@code null} outside a request
     * @param key the mapped-property key, e.g. {@code CDMgroup}
     * @return the first posted value, or {@code null} when the field was not posted
     */
    public static String get(HttpServletRequest request, String key) {
        if (request == null || key == null) {
            return null;
        }
        return request.getParameter("value(" + key + ")");
    }
}
