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
package io.github.carlos_emr.carlos.clinical;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the "137 encounter + casemgmt JSPs return 404 when hit directly"
 * promise of PR #1682. The JSP files themselves are on disk under
 * {@code /WEB-INF/jsp/}; the only way they become publicly reachable again is
 * if the Struts config (a) gains a {@code <result>} that forwards a public
 * action to an {@code /encounter/*.jsp} or {@code /casemgmt/*.jsp} path
 * outside {@code /WEB-INF/}, or (b) silently drops the shared
 * {@code ViewClinical2Action} gate from an action mapping.
 *
 * <p>Mirrors {@code StrutsTicklerConfigTest} (PR #1670) and
 * {@code StrutsMessengerConfigTest} (PR #1629) which guard the same
 * invariant for smaller modules. This one is by far the largest surface
 * (~129 mappings in {@code struts-clinical.xml} alone).
 *
 * @since 2026-04-14
 */
@DisplayName("struts clinical config Tests")
@Tag("unit")
@Tag("fast")
@Tag("clinical")
class StrutsClinicalConfigTest {

    private static final String CLINICAL_CONFIG =
            "src/main/webapp/WEB-INF/classes/struts-clinical.xml";
    private static final String ENCOUNTER_CONFIG =
            "src/main/webapp/WEB-INF/classes/struts-encounter.xml";
    private static final String SCHEDULING_CONFIG =
            "src/main/webapp/WEB-INF/classes/struts-scheduling.xml";

    private static final String VIEW_CLINICAL_CLASS =
            "io.github.carlos_emr.carlos.clinical.gate.ViewClinical2Action";
    private static final String ECT_MEASUREMENTS_CLASS =
            "io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctMeasurements2Action";
    private static final String FLOWSHEET_CUSTOM_CLASS =
            "io.github.carlos_emr.carlos.commn.web.FlowSheetCustom2Action";

    /**
     * Sanity lower bound: PR #1682 introduced ~129 {@code ViewClinical2Action}
     * mappings in struts-clinical.xml. If this count drops far below that,
     * mappings have been deleted wholesale — either intentionally (document
     * the deletion) or accidentally (a merge conflict dropped the block).
     */
    private static final int MIN_GATE_MAPPINGS = 120;

    @Test
    @DisplayName("every ViewClinical2Action mapping should forward to a /WEB-INF/jsp/ result")
    void shouldRouteEveryViewClinicalGateInsideWebInf() throws Exception {
        Document doc = parse(CLINICAL_CONFIG);
        NodeList actions = doc.getElementsByTagName("action");

        List<String> offenders = new ArrayList<>();
        int gateMappings = 0;

        for (int i = 0; i < actions.getLength(); i++) {
            Element action = (Element) actions.item(i);
            if (!VIEW_CLINICAL_CLASS.equals(action.getAttribute("class"))) {
                continue;
            }
            gateMappings++;
            String actionName = action.getAttribute("name");
            NodeList results = action.getElementsByTagName("result");
            if (results.getLength() == 0) {
                offenders.add(actionName + " -- no <result> declared");
                continue;
            }
            for (int r = 0; r < results.getLength(); r++) {
                String path = extractResultPath((Element) results.item(r));
                if (path == null || path.isEmpty()) {
                    offenders.add(actionName + " -- empty result");
                } else if (!path.startsWith("/WEB-INF/")) {
                    offenders.add(actionName + " -- result=" + path);
                }
            }
        }

        assertThat(gateMappings)
                .as("struts-clinical.xml should declare at least %d ViewClinical2Action mappings "
                        + "(PR #1682 baseline was ~129); a large drop signals accidental deletion",
                        MIN_GATE_MAPPINGS)
                .isGreaterThanOrEqualTo(MIN_GATE_MAPPINGS);

        assertThat(offenders)
                .as("every ViewClinical2Action mapping must forward inside /WEB-INF/jsp/; "
                        + "a result outside /WEB-INF/ reintroduces the direct-JSP access "
                        + "PR #1682 closed for encounter/casemgmt")
                .isEmpty();
    }

    @Test
    @DisplayName("no result in clinical or encounter config should expose an /encounter/ or /casemgmt/ JSP publicly")
    void shouldForbidPublicEncounterOrCasemgmtJspResults() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (String configPath : new String[]{CLINICAL_CONFIG, ENCOUNTER_CONFIG}) {
            Document doc = parse(configPath);
            NodeList results = doc.getElementsByTagName("result");
            for (int i = 0; i < results.getLength(); i++) {
                String path = extractResultPath((Element) results.item(i));
                if (path == null || !path.endsWith(".jsp")) {
                    continue;
                }
                boolean gated = path.startsWith("/encounter/") || path.startsWith("/casemgmt/");
                if (gated && !path.startsWith("/WEB-INF/")) {
                    offenders.add(configPath + " -> " + path);
                }
            }
        }

        assertThat(offenders)
                .as("No <result> may forward to a public /encounter/*.jsp or /casemgmt/*.jsp path; "
                        + "every such JSP was moved under /WEB-INF/jsp/ by PR #1682 and a result "
                        + "outside /WEB-INF/ would either 404 in prod or re-expose the file")
                .isEmpty();
    }

    @Test
    @DisplayName("key view-gate action names should be declared")
    void shouldDeclareLoadBearingViewGateActions() throws Exception {
        List<String> actionNames = collectActionNames(CLINICAL_CONFIG);
        // Spot-check a handful of load-bearing action names referenced by
        // JSPs edited in PR #1682. If any of these silently drop, the JSP
        // that references them 404s even though the gated JSP still exists.
        String[] required = {
                "casemgmt/ViewNoteBrowser",
                "encounter/oscarMeasurements/ViewTemplateFlowSheet",
                "encounter/oscarMeasurements/ViewViewMeasurementMap",
                "encounter/immunization/config/ViewCreateImmunizationSetInit"
        };
        for (String name : required) {
            assertThat(actionNames)
                    .as("struts-clinical.xml must declare <action name=\"%s\"> "
                            + "(referenced from edited JSPs in PR #1682)", name)
                    .contains(name);
        }
    }

    @Test
    @DisplayName("dxresearch migration routes should stay gated and inside WEB-INF")
    void shouldKeepDxresearchMigrationRoutesInsideWebInf() throws Exception {
        Document doc = parse(CLINICAL_CONFIG);
        NodeList actions = doc.getElementsByTagName("action");

        List<String> offenders = new ArrayList<>();

        for (int i = 0; i < actions.getLength(); i++) {
            Element action = (Element) actions.item(i);
            String actionName = action.getAttribute("name");
            String actionClass = action.getAttribute("class");

            if ("io.github.carlos_emr.carlos.dxresearch.gate.ViewDxResearch2Action".equals(actionClass)) {
                NodeList results = action.getElementsByTagName("result");
                for (int r = 0; r < results.getLength(); r++) {
                    String path = extractResultPath((Element) results.item(r));
                    if (path == null || path.isEmpty() || !path.startsWith("/WEB-INF/")) {
                        offenders.add(actionName + " -- result=" + path);
                    }
                }
            }

            if ("oscarResearch/oscarDxResearch/dxResearch".equals(actionName)) {
                String failurePath = null;
                NodeList results = action.getElementsByTagName("result");
                for (int r = 0; r < results.getLength(); r++) {
                    Element result = (Element) results.item(r);
                    if ("failure".equals(result.getAttribute("name"))) {
                        failurePath = extractResultPath(result);
                    }
                }
                assertThat(failurePath)
                        .as("dxResearch action should keep a failure result that re-renders the migrated JSP "
                                + "inside /WEB-INF so invalid-code submissions do not redirect into setupDxResearch")
                        .isEqualTo("/WEB-INF/jsp/oscarResearch/oscarDxResearch/dxResearch.jsp");
            }
        }

        assertThat(offenders)
                .as("every ViewDxResearch2Action mapping must forward inside /WEB-INF/jsp/")
                .isEmpty();
    }

    @Test
    @DisplayName("measurement submission view should only be reached after POST-only measurement actions")
    void shouldRouteMeasurementSubmissionThroughPostOnlyActions() throws Exception {
        Document clinical = parse(CLINICAL_CONFIG);
        NodeList clinicalActions = clinical.getElementsByTagName("action");
        List<String> sharedGateOffenders = new ArrayList<>();
        for (int i = 0; i < clinicalActions.getLength(); i++) {
            Element action = (Element) clinicalActions.item(i);
            if (!VIEW_CLINICAL_CLASS.equals(action.getAttribute("class"))) {
                continue;
            }
            NodeList results = action.getElementsByTagName("result");
            for (int r = 0; r < results.getLength(); r++) {
                if ("/WEB-INF/jsp/encounter/oscarMeasurements/ProcessMeasurementsSubmission.jsp"
                        .equals(extractResultPath((Element) results.item(r)))) {
                    sharedGateOffenders.add(action.getAttribute("name"));
                }
            }
        }

        assertThat(sharedGateOffenders)
                .as("ProcessMeasurementsSubmission.jsp clears encounter submission state and must only be "
                        + "rendered after POST-only measurement actions, never through the shared read gate")
                .isEmpty();

        assertActionClass(ENCOUNTER_CONFIG, "encounter/Measurements", ECT_MEASUREMENTS_CLASS);
        assertActionClass(ENCOUNTER_CONFIG, "encounter/Measurements2", ECT_MEASUREMENTS_CLASS);
        assertActionClass(SCHEDULING_CONFIG, "encounter/oscarMeasurements/adminFlowsheet/FlowSheetCustomAction",
                FLOWSHEET_CUSTOM_CLASS);
    }

    private String extractResultPath(Element result) {
        // Results may specify their location either as element text or via
        // <param name="location">. Prefer the param form when present since
        // some result types (redirect, redirectAction) use it exclusively.
        NodeList params = result.getElementsByTagName("param");
        for (int j = 0; j < params.getLength(); j++) {
            Element p = (Element) params.item(j);
            if ("location".equals(p.getAttribute("name"))) {
                return p.getTextContent().trim();
            }
        }
        String text = result.getTextContent();
        return text == null ? "" : text.trim();
    }

    private List<String> collectActionNames(String configPath) throws Exception {
        Document doc = parse(configPath);
        NodeList actions = doc.getElementsByTagName("action");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < actions.getLength(); i++) {
            if (actions.item(i) instanceof Element e) {
                String name = e.getAttribute("name");
                if (name != null && !name.isEmpty()) {
                    out.add(name);
                }
            }
        }
        return out;
    }

    private void assertActionClass(String configPath, String actionName, String expectedClass) throws Exception {
        Document doc = parse(configPath);
        NodeList actions = doc.getElementsByTagName("action");
        for (int i = 0; i < actions.getLength(); i++) {
            Element action = (Element) actions.item(i);
            if (actionName.equals(action.getAttribute("name"))) {
                assertThat(action.getAttribute("class"))
                        .as("%s should route through %s", actionName, expectedClass)
                        .isEqualTo(expectedClass);
                return;
            }
        }
        throw new AssertionError("Missing action " + actionName + " in " + configPath);
    }

    private Document parse(String configPath) throws Exception {
        DocumentBuilder db = newHardenedDocumentBuilder();
        try (InputStream in = new FileInputStream(configPath)) {
            return db.parse(in);
        }
    }

    private DocumentBuilder newHardenedDocumentBuilder() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        dbf.setNamespaceAware(false);
        // Defense-in-depth XML hardening — inputs are trusted local config
        // files, but pinning secure-processing + disabling external entities
        // keeps the test robust across JAXP implementations.
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        db.setEntityResolver((publicId, systemId) ->
                new InputSource(new java.io.StringReader("")));
        return db;
    }
}
