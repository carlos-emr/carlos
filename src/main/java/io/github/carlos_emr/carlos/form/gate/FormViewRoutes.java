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
package io.github.carlos_emr.carlos.form.gate;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.servlet.ServletContext;

/**
 * Resolves legacy form JSP paths to the new action routes and internal
 * WEB-INF locations.
 *
 * @since 2026-04-15
 */
public final class FormViewRoutes {

    static final String FORM_ACTION_PREFIX = "form/";
    private static final String PUBLIC_FORM_PREFIX = "/form/";
    private static final String INTERNAL_FORM_PREFIX = "/WEB-INF/jsp/form/";
    private static final Pattern SAFE_RELATIVE_VIEW_PATTERN =
            Pattern.compile("[A-Za-z0-9_/-]+");
    private static final Pattern SAFE_FORM_LINK_PATTERN =
            Pattern.compile("[A-Za-z0-9_-]+\\.(?:jsp|html)");
    private static final Set<String> RESERVED_FORM_ACTIONS = Set.of(
            "form/setupSelect",
            "form/select",
            "form/xmlUpload",
            "form/formname",
            "form/forwardshortcutname",
            "form/forwardname",
            "form/SetupForm",
            "form/SubmitForm",
            "form/RHPrevention",
            "form/AddRHWorkFlow",
            "form/BCAR2020");
    private static final Set<String> ALLOWED_WILDCARD_FORM_VIEWS =
            Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
                    "addRhInjection",
                    "chf",
                    "demographicMeasurementModal",
                    "form2minwalk",
                    "formadfv2",
                    "formalpha",
                    "formannual",
                    "formannualfemale",
                    "formannualfemaleprint",
                    "formannualfemaleV2",
                    "formannualmale",
                    "formannualmaleV2",
                    "formannualV2",
                    "formbcar",
                    "formbcar2007",
                    "formbcar2007pg1",
                    "formbcar2007pg2",
                    "formbcar2007pg3",
                    "formbcar2012",
                    "formbcar2012pg1",
                    "formbcar2012pg2",
                    "formbcar2012pg3",
                    "formBCAR2020Attachments",
                    "formBCAR2020pg1",
                    "formBCAR2020pg2",
                    "formBCAR2020pg3",
                    "formBCAR2020pg4",
                    "formBCAR2020pg5",
                    "formbcarpg1",
                    "formbcarpg1namepopup",
                    "formbcarpg2",
                    "formbcarpg3",
                    "formbcbirthsummo",
                    "formbcbirthsummo2008",
                    "formbcclientchartchecklist",
                    "formbchp",
                    "formbchppg1",
                    "formbchppg2",
                    "formbcinr",
                    "formbcnewborn",
                    "formBCNewBorn2008",
                    "formBCNewBorn2008pg1",
                    "formBCNewBorn2008pg2",
                    "formBCNewBorn2008pg3",
                    "formbcnewbornpg1",
                    "formbcnewbornpg2",
                    "formbcnewbornpg3",
                    "formcaregiver",
                    "formCESD",
                    "formchf",
                    "formConsultant",
                    "formcostquestionnaire",
                    "formCounseling",
                    "formcounsellorassessment",
                    "formDischargeSummary",
                    "formDischargeSummaryPrint",
                    "formfalls",
                    "formgripstrength",
                    "formGrowth0_36",
                    "formGrowth0_36Print",
                    "formGrowthChart",
                    "formGrowthChartPrint",
                    "formhomefalls",
                    "formimmunallergy",
                    "formintakeinfo",
                    "formInternetAccess",
                    "formInvoice",
                    "formlabreq",
                    "formlabreq07",
                    "formlabreq10",
                    "formlabreqprint",
                    "formlatelifedisabilityvisualAid1",
                    "formlatelifedisabilityvisualAid2",
                    "formlatelifeFDIdisability",
                    "formlatelifeFDIfunction",
                    "formlatelifefunctionvisualAid1",
                    "formlatelifefunctionvisualAid2",
                    "formmentalhealth",
                    "formMentalHealthForm1",
                    "formMentalHealthForm14",
                    "formMentalHealthForm42",
                    "formmhassessment",
                    "formmhassessmentprint",
                    "formmhoutcome",
                    "formmhoutcomeprint",
                    "formmhreferral",
                    "formmhreferralprint",
                    "formmmse",
                    "formpalliativecare",
                    "formperimenopausal",
                    "formPositionHazard",
                    "formreceptionassessment",
                    "formRhImmuneGlobulin",
                    "formrourke",
                    "formrourke1",
                    "formrourke2",
                    "formrourke2006",
                    "formRourke2006intro",
                    "formrourke2006p1",
                    "formrourke2006p2",
                    "formrourke2006p3",
                    "formrourke2006p4",
                    "formrourke2009complete",
                    "formrourke2009p1",
                    "formrourke2009p2",
                    "formrourke2009p3",
                    "formrourke2009p4",
                    "formrourke2017complete",
                    "formrourke2017p1",
                    "formrourke2017p2",
                    "formrourke2017p3",
                    "formrourke2017p4",
                    "formrourke2020complete",
                    "formRourke2020p1",
                    "formRourke2020p2",
                    "formRourke2020p3",
                    "formRourke2020p4",
                    "formrourke3",
                    "formrourkep1",
                    "formrourkep2",
                    "formrourkep3",
                    "formSatisfactionScale",
                    "formselect",
                    "formselfadministered",
                    "formSelfAssessment",
                    "formselfefficacy",
                    "formselfmanagement",
                    "formSF36",
                    "formSF36caregiver",
                    "formtreatmentpref",
                    "formVTForm",
                    "formXmlUpload",
                    "graphHeadCirc",
                    "graphLengthWeight",
                    "patientEncounterWorksheet",
                    "RhInjectionDisplay"
            )));

    private FormViewRoutes() {
    }

    /**
     * Returns the internal WEB-INF resource path for the given wildcard action
     * name when it maps to a moved form view.
     */
    public static String resolveInternalViewFromAction(
            String actionName,
            ServletContext servletContext) {
        if (actionName == null
                || servletContext == null
                || !actionName.startsWith(FORM_ACTION_PREFIX)
                || RESERVED_FORM_ACTIONS.contains(actionName)) {
            return null;
        }

        String relativeView = actionName.substring(FORM_ACTION_PREFIX.length());
        if (!isSafeRelativeView(relativeView)
                || !ALLOWED_WILDCARD_FORM_VIEWS.contains(relativeView)) {
            return null;
        }

        String jspPath = INTERNAL_FORM_PREFIX + relativeView + ".jsp";
        if (servletContext.getResourceAsStream(jspPath) != null) {
            return jspPath;
        }

        String htmlPath = INTERNAL_FORM_PREFIX + relativeView + ".html";
        if (servletContext.getResourceAsStream(htmlPath) != null) {
            return htmlPath;
        }

        return null;
    }

    /**
     * Converts a legacy public form JSP/HTML path into the new action route.
     */
    public static String resolveActionPath(String legacyPath) {
        if (legacyPath == null || legacyPath.isBlank()) {
            return null;
        }

        String trimmed = legacyPath.trim();
        if (trimmed.startsWith("../")) {
            trimmed = "/" + trimmed.substring(3);
        }
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }

        int queryIndex = trimmed.indexOf('?');
        String pathOnly = queryIndex >= 0 ? trimmed.substring(0, queryIndex) : trimmed;
        String query = queryIndex >= 0 ? trimmed.substring(queryIndex) : "";

        if ("/form/forwardshortcutname.jsp".equals(pathOnly)) {
            return "/form/forwardshortcutname" + query;
        }
        if ("/form/forwardname.jsp".equals(pathOnly)) {
            return "/form/forwardname" + query;
        }
        if ("/form/eCARES/formeCARES.jsp".equals(pathOnly)) {
            return "/formeCARES" + query;
        }
        if ("/form/pharmaForms/formBPMH.jsp".equals(pathOnly)) {
            return "/formBPMH" + query;
        }
        if (!pathOnly.startsWith(PUBLIC_FORM_PREFIX)) {
            return null;
        }
        if (!(pathOnly.endsWith(".jsp") || pathOnly.endsWith(".html"))) {
            return null;
        }

        String relativePath = pathOnly.substring(PUBLIC_FORM_PREFIX.length());
        int extensionIndex = relativePath.lastIndexOf('.');
        String viewName = relativePath.substring(0, extensionIndex);
        if (!isSafeRelativeView(viewName)
                || !ALLOWED_WILDCARD_FORM_VIEWS.contains(viewName)) {
            return null;
        }
        return PUBLIC_FORM_PREFIX + viewName + query;
    }

    /**
     * Validates the hidden {@code form_link} value and returns its internal
     * WEB-INF target.
     */
    public static String resolveInternalViewFromFormLink(String formLink) {
        if (formLink == null || !SAFE_FORM_LINK_PATTERN.matcher(formLink).matches()) {
            return null;
        }
        return INTERNAL_FORM_PREFIX + formLink;
    }

    static boolean isAllowedWildcardFormView(String relativeView) {
        return ALLOWED_WILDCARD_FORM_VIEWS.contains(relativeView);
    }

    private static boolean isSafeRelativeView(String relativeView) {
        return relativeView != null
                && !relativeView.isBlank()
                && !relativeView.contains("..")
                && !relativeView.startsWith("/")
                && !relativeView.endsWith("/")
                && SAFE_RELATIVE_VIEW_PATTERN.matcher(relativeView).matches();
    }
}
