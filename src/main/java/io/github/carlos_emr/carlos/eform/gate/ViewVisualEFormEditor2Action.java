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
import java.util.regex.Pattern;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Read-only gate for {@code /eform/visualEformEditor}, the Visual eForm Editor.
 *
 * <p>With no {@code fid} the editor opens blank to create a new eForm, as it always has. With
 * {@code ?fid=<id>} it opens that stored template pre-loaded, so the eForm Library's Edit action can
 * point it at the form that was clicked (issue #3166). The id is validated here, before the page
 * renders: it must be a positive decimal integer naming a current (not deleted) template. The page
 * receives only the parsed integer, as the {@value #INITIAL_FID_ATTRIBUTE} request attribute, never
 * the raw parameter.</p>
 *
 * <p>The page itself makes no change. The template is fetched and saved by the editor through
 * {@code /ws/rs/eform}, whose endpoints enforce {@code _eform} read and write on their own; this gate
 * keeps the editor's {@code _admin.eform} write requirement and its GET/HEAD-only contract.</p>
 *
 * @since 2026-10-22
 */
public class ViewVisualEFormEditor2Action extends BaseEFormView2Action {

    /** Request attribute carrying the validated id of the template to pre-load. */
    static final String INITIAL_FID_ATTRIBUTE = "visualEditorInitialFid";

    private static final String ROUTE = "eform/visualEformEditor";

    // Up to nine digits always fits an int, so parsing can never overflow.
    private static final Pattern FID = Pattern.compile("[1-9][0-9]{0,8}");

    private final EFormDao eFormDao;

    /** Struts creates this action with no arguments; the DAO comes from the application context. */
    public ViewVisualEFormEditor2Action() {
        this(SpringUtils.getBean(EFormDao.class));
    }

    ViewVisualEFormEditor2Action(EFormDao eFormDao) {
        this.eFormDao = eFormDao;
    }

    @Override
    protected String executeView(
            HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        EFormViewRoutes.Route route = EFormViewRoutes.resolve(ROUTE);
        requirePrivilege(request, route.privilege());

        String rawFid = request.getParameter("fid");
        if (rawFid != null && !rawFid.isEmpty()) {
            if (!FID.matcher(rawFid).matches()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
            }
            Integer fid = Integer.valueOf(rawFid);
            EForm eForm = eFormDao.findById(fid);
            if (eForm == null || !eForm.isCurrent()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return NONE;
            }
            request.setAttribute(INITIAL_FID_ATTRIBUTE, fid);
        }
        return forwardToInternalView(request, response, route.internalView());
    }
}
