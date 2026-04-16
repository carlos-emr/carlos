/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform.gate;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Gate for the saved eForm renderer.
 *
 * @since 2026-04-15
 */
public class ViewEFormShowData2Action extends BaseEFormView2Action {

    private static final String INTERNAL_VIEW =
            "/WEB-INF/jsp/eform/efmshowform_data.jsp";

    @Override
    protected String executeView(
            HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        requirePrivilege(request, EFormViewRoutes.Privilege.EFORM_READ);
        return forwardToInternalView(request, response, INTERNAL_VIEW);
    }
}
