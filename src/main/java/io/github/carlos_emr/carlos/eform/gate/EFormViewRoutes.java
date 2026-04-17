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

import java.util.Map;

/**
 * Registry of explicit eForm page routes moved behind WEB-INF.
 *
 * @since 2026-04-15
 */
public final class EFormViewRoutes {

    public enum Privilege {
        EFORM_READ,
        EFORM_WRITE,
        ADMIN_EFORM_WRITE
    }

    public record Route(String internalView, Privilege privilege) {
    }

    private static final Map<String, Route> ROUTES = Map.ofEntries(
            Map.entry("eform/attachEform",
                    new Route("/WEB-INF/jsp/eform/attachEform.jsp", Privilege.EFORM_READ)),
            Map.entry("eform/displayAttachedFiles",
                    new Route("/WEB-INF/jsp/eform/displayAttachedFiles.jsp", Privilege.EFORM_READ)),
            Map.entry("eform/efmformapconfig_lookup",
                    new Route("/WEB-INF/jsp/eform/efmformapconfig_lookup.jsp", Privilege.EFORM_READ)),
            Map.entry("eform/efmformrtl_templates",
                    new Route("/WEB-INF/jsp/eform/efmformrtl_templates.jsp", Privilege.EFORM_READ)),
            Map.entry("eform/efmformmanager",
                    new Route("/WEB-INF/jsp/eform/efmformmanager.jsp", Privilege.EFORM_WRITE)),
            Map.entry("eform/efmformmanagerdeleted",
                    new Route("/WEB-INF/jsp/eform/efmformmanagerdeleted.jsp", Privilege.EFORM_WRITE)),
            Map.entry("eform/efmformmanageredit",
                    new Route("/WEB-INF/jsp/eform/efmformmanageredit.jsp", Privilege.EFORM_WRITE)),
            Map.entry("eform/efmformslistadd",
                    new Route("/WEB-INF/jsp/eform/efmformslistadd.jsp", Privilege.EFORM_WRITE)),
            Map.entry("eform/efmimagemanager",
                    new Route("/WEB-INF/jsp/eform/efmimagemanager.jsp", Privilege.EFORM_WRITE)),
            Map.entry("eform/efmmanageformgroups",
                    new Route("/WEB-INF/jsp/eform/efmmanageformgroups.jsp", Privilege.EFORM_WRITE)),
            Map.entry("eform/efmmanageindependent",
                    new Route("/WEB-INF/jsp/eform/efmmanageindependent.jsp", Privilege.EFORM_WRITE)),
            Map.entry("eform/efmmanageindependentdeleted",
                    new Route("/WEB-INF/jsp/eform/efmmanageindependentdeleted.jsp", Privilege.EFORM_WRITE)),
            Map.entry("eform/efmpatientformlist",
                    new Route("/WEB-INF/jsp/eform/efmpatientformlist.jsp", Privilege.EFORM_READ)),
            Map.entry("eform/efmpatientformlistsingle",
                    new Route("/WEB-INF/jsp/eform/efmpatientformlistsingle.jsp", Privilege.EFORM_READ)),
            Map.entry("eform/efmpatientformlistdeleted",
                    new Route("/WEB-INF/jsp/eform/efmpatientformlistdeleted.jsp", Privilege.EFORM_READ)),
            Map.entry("eform/eformGenerator",
                    new Route("/WEB-INF/jsp/eform/eformGenerator.jsp", Privilege.ADMIN_EFORM_WRITE)),
            Map.entry("eform/Eform_dbtags",
                    new Route("/WEB-INF/jsp/eform/Eform_dbtags.html", Privilege.EFORM_WRITE)),
            Map.entry("eform/eformFloatingToolbar/eform_floating_toolbar",
                    new Route("/WEB-INF/jsp/eform/eformFloatingToolbar/eform_floating_toolbar.jspf",
                            Privilege.EFORM_READ)),
            Map.entry("eform/visualEformEditor",
                    new Route("/WEB-INF/jsp/eform/visualEformEditor.jsp", Privilege.ADMIN_EFORM_WRITE)),
            Map.entry("eform/partials/upload",
                    new Route("/WEB-INF/jsp/eform/partials/upload.jsp", Privilege.EFORM_WRITE)),
            Map.entry("eform/partials/import",
                    new Route("/WEB-INF/jsp/eform/partials/import.jsp", Privilege.EFORM_WRITE)),
            Map.entry("eform/partials/upload_image",
                    new Route("/WEB-INF/jsp/eform/partials/upload_image.jsp", Privilege.EFORM_WRITE)),
            Map.entry("eform/fieldNoteReport/fieldnotereport",
                    new Route("/WEB-INF/jsp/eform/fieldNoteReport/fieldnotereport.jsp", Privilege.EFORM_READ)),
            Map.entry("eform/fieldNoteReport/fieldnotereportdetail",
                    new Route("/WEB-INF/jsp/eform/fieldNoteReport/fieldnotereportdetail.jsp",
                            Privilege.EFORM_READ)),
            Map.entry("eform/fieldNoteReport/fieldnoteselect",
                    new Route("/WEB-INF/jsp/eform/fieldNoteReport/fieldnoteselect.jsp", Privilege.EFORM_READ))
    );

    private EFormViewRoutes() {
    }

    public static Route resolve(String actionName) {
        return ROUTES.get(actionName);
    }
}
