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
package io.github.carlos_emr.carlos.managers;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.apache.logging.log4j.Logger;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.ValueSet.ConceptReferenceComponent;
import org.hl7.fhir.r4.model.ValueSet.ConceptReferenceDesignationComponent;
import org.hl7.fhir.r4.model.ValueSet.ConceptSetComponent;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.CVCImmunizationDao;
import io.github.carlos_emr.carlos.commn.dao.CVCMedicationDao;
import io.github.carlos_emr.carlos.commn.dao.CVCMedicationGTINDao;
import io.github.carlos_emr.carlos.commn.dao.CVCMedicationLotNumberDao;
import io.github.carlos_emr.carlos.commn.dao.LookupListItemDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.CVCImmunization;
import io.github.carlos_emr.carlos.commn.model.CVCMedication;
import io.github.carlos_emr.carlos.commn.model.CVCMedicationGTIN;
import io.github.carlos_emr.carlos.commn.model.CVCMedicationLotNumber;
import io.github.carlos_emr.carlos.commn.model.LookupList;
import io.github.carlos_emr.carlos.commn.model.LookupListItem;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CanadianVaccineCatalogueManager {

    protected static FhirContext ctxR4 = null;
    Logger logger = MiscUtils.getLogger();

    private static final String NVC_DEFAULT_BASE_URL = "https://nvc-cnv.canada.ca/fhir/v2";
    private static final String CVC_FIRST_DATE_PROP = "cvc.firstdate";
    private static final String CVC_UPDATED_PROP = "cvc.updated";

    // Official NVC V2 canonical URLs for FHIR extension/value-set parsing.
    // These are fixed to the NVC canonical namespace and MUST NOT be derived from
    // getCVCURL() — the transport endpoint may be overridden to a proxy or mirror,
    // while FHIR canonicals embedded in the bundle always use the official base.
    private static final String NVC_CANONICAL_BASE = "https://nvc-cnv.canada.ca/fhir/v2";
    private static final String NVC_LINKED_GENERIC_CONCEPT_URL =
            NVC_CANONICAL_BASE + "/StructureDefinition/nvc-linked-generic-concept";
    private static final String NVC_MARKET_AUTH_HOLDERS_URL =
            NVC_CANONICAL_BASE + "/StructureDefinition/nvc-market-authorization-holders";
    private static final String NVC_MARKET_AUTH_HOLDER_URL =
            NVC_CANONICAL_BASE + "/StructureDefinition/nvc-market-authorization-holder";
    private static final String NVC_GENERIC_VALUESET_URL =
            NVC_CANONICAL_BASE + "/ValueSet/Generic";
    private static final String NVC_PRODUCT_STATUS_URL =
            NVC_CANONICAL_BASE + "/StructureDefinition/nvc-product-status";
    private static final String NVC_SHELF_STATUS_VALUESET_URL =
            NVC_CANONICAL_BASE + "/ValueSet/ShelfStatus";

    @Autowired
    CVCMedicationDao medicationDao;
    @Autowired
    CVCMedicationLotNumberDao lotNumberDao;
    @Autowired
    CVCMedicationGTINDao gtinDao;
    @Autowired
    CVCImmunizationDao immunizationDao;
    @Autowired
    UserPropertyDAO userPropertyDao;
    @Autowired
    LookupListItemDao lookupListItemDao;
    @Autowired
    LookupListManager lookupListManager;

    static {
        ctxR4 = FhirContext.forR4();
    }

    // Populated during updateBrandNameImmunizations, consumed by processMedicationBundle
    private Map<String, String> dinManufactureMap = new HashMap<>();
    private Map<String, String> dinDinMap = new HashMap<>();

    public List<CVCImmunization> getImmunizationList() {
        return immunizationDao.findAll(0, 1000);
    }

    public List<CVCImmunization> getImmunizationsByParent(String conceptId) {
        return immunizationDao.findByParent(conceptId);
    }

    public CVCMedication getMedicationBySnomedConceptId(String conceptId) {
        return medicationDao.findBySNOMED(conceptId);
    }

    public List<CVCImmunization> getGenericImmunizationList() {
        return immunizationDao.findAllGeneric();
    }

    public List<CVCMedication> getMedicationByDIN(LoggedInInfo loggedInInfo, String din) {
        List<CVCMedication> results = medicationDao.findByDIN(din);
        LogAction.addLogSynchronous(loggedInInfo, "CanadianVaccineCatalogueManager.getMedicationByDIN", null);
        return results;
    }

    /**
     * Downloads the NVC V2 bundle, validates connectivity, then atomically replaces all local CVC
     * data. Aborts without clearing local data if the bundle cannot be fetched or parsed. If a DB
     * write fails mid-update the transaction rolls back, preserving the previous catalogue state.
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(LoggedInInfo loggedInInfo) throws IOException {
        String jsonString;
        Bundle bundle;

        try {
            jsonString = fetchBundleJson();
            bundle = parseBundleJson(jsonString);
        } catch (Exception e) {
            logger.error("NVC V2 bundle fetch failed — aborting update, existing local data preserved", e);
            throw e;
        }

        CarlosProperties carlosProps = CarlosProperties.getInstance();
        if (carlosProps.hasProperty("CVC_BUNDLE_LOCAL_FILE")) {
            try {
                String rawPath = carlosProps.getProperty("CVC_BUNDLE_LOCAL_FILE");
                File rawFile = new File(rawPath);
                File parentDir = rawFile.getParentFile() != null ? rawFile.getParentFile() : new File(".");
                File safeFile = PathValidationUtils.validatePath(rawFile.getName(), parentDir);
                String prettyJson = ctxR4.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
                FileUtils.writeStringToFile(safeFile, prettyJson, StandardCharsets.UTF_8);
                logger.info("NVC bundle written to {}", safeFile.getAbsolutePath());
            } catch (Exception e) {
                logger.error("Failed to write NVC bundle to CVC_BUNDLE_LOCAL_FILE", e);
            }
        }

        dinManufactureMap.clear();
        dinDinMap.clear();
        clearCurrentData();

        for (BundleEntryComponent bec : bundle.getEntry()) {
            Resource res = bec.getResource();
            if (res.getResourceType() == ResourceType.ValueSet) {
                String id = res.getIdElement().getIdPart();
                if ("Generic".equals(id)) {
                    updateGenericImmunizations(loggedInInfo, (ValueSet) res);
                } else if ("Tradename".equals(id)) {
                    updateBrandNameImmunizations(loggedInInfo, (ValueSet) res);
                } else if ("AnatomicalSite".equals(id)) {
                    updateAnatomicalSites(loggedInInfo, (ValueSet) res);
                } else if ("RouteOfAdmin".equals(id)) {
                    updateRoutes(loggedInInfo, (ValueSet) res);
                } else {
                    logger.debug("Skipping ValueSet: {}", id);
                }
            } else if (res.getResourceType() == ResourceType.Bundle) {
                if ("Tradename".equals(res.getIdElement().getIdPart())) {
                    updateMedications(loggedInInfo, (Bundle) res);
                }
            } else {
                logger.debug("Skipping resource type: {}", res.getResourceType());
            }
        }

        setUpdatedInPropertyTable();
        setFirstDateInPropertyTable();
    }

    private String fetchBundleJson() throws IOException {
        String baseUrl = getCVCURL();
        String relUrl = CarlosProperties.getInstance().getProperty("NVC_BUNDLE", "/Bundle/NVC");
        String fullUrl = baseUrl + relUrl;
        String accept = CarlosProperties.getInstance().getProperty("NVC_ACCEPT", "application/json");
        String xAppDesc = CarlosProperties.getInstance().getProperty("NVC_X_APP", "CARLOSEMR");

        logger.debug("Fetching NVC V2 bundle from: {}", fullUrl);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(30))
                .setResponseTimeout(Timeout.ofSeconds(120))
                .build();

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(10))
                .build();

        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
        connManager.setDefaultConnectionConfig(connectionConfig);

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connManager)
                .setDefaultRequestConfig(requestConfig)
                .build()) {
            HttpGet request = new HttpGet(fullUrl);
            request.addHeader("Accept", accept);
            request.addHeader("x-app-desc", xAppDesc);
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getCode();
                logger.debug("NVC response: {} {}", statusCode, response.getReasonPhrase());
                if (statusCode != 200) {
                    throw new IOException("NVC bundle fetch returned HTTP " + statusCode + " from " + fullUrl);
                }
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                if (body == null || body.isBlank()) {
                    throw new IOException("NVC bundle response was empty from " + fullUrl);
                }
                logger.debug("NVC bundle fetched, length={}", body.length());
                return body;
            }
        }
    }

    private Bundle parseBundleJson(String jsonString) {
        IParser parser = ctxR4.newJsonParser();
        return parser.parseResource(Bundle.class, jsonString);
    }

    private void clearCurrentData() {
        medicationDao.removeAll();
        // lotNumberDao and gtinDao are intentionally NOT cleared here:
        // NVC V2 no longer provides lot-number or GTIN data (removed in V2 spec).
        // These tables are managed independently and must not be wiped during NVC refresh.
        immunizationDao.removeAll();
    }

    public void updateGenericImmunizations(LoggedInInfo loggedInInfo, ValueSet vs) {
        if (!vs.hasCompose()) {
            return;
        }
        for (ConceptSetComponent c : vs.getCompose().getInclude()) {
            for (ConceptReferenceComponent cc : c.getConcept()) {
                CVCImmunization imm = new CVCImmunization();
                imm.setSnomedConceptId(cc.getCode());

                String picklistTerm = null;
                String fullySpecifiedName = null;

                for (ConceptReferenceDesignationComponent cr : cc.getDesignation()) {
                    Coding use = cr.getUse();
                    if (use == null) continue;
                    if ("enAbbreviation".equals(use.getCode())) {
                        picklistTerm = cr.getValue();
                    } else if ("900000000000003001".equals(use.getCode())
                            || "Fully Specified Name".equals(use.getDisplay())) {
                        fullySpecifiedName = cr.getValue();
                    }
                }

                if (fullySpecifiedName != null) {
                    imm.setDisplayName(fullySpecifiedName + " (generic)");
                } else if (picklistTerm != null) {
                    imm.setDisplayName(picklistTerm);
                }
                if (picklistTerm != null) {
                    imm.setPicklistName(picklistTerm);
                }

                imm.setGeneric(true);
                saveImmunization(loggedInInfo, imm);
            }
        }
    }

    public void updateBrandNameImmunizations(LoggedInInfo loggedInInfo, ValueSet vs) {
        if (!vs.hasCompose()) {
            return;
        }
        for (ConceptSetComponent c : vs.getCompose().getInclude()) {
            for (ConceptReferenceComponent cc : c.getConcept()) {
                CVCImmunization imm = new CVCImmunization();
                imm.setSnomedConceptId(cc.getCode());

                String enAbbreviation = null;
                String fullySpecifiedName = null;

                for (ConceptReferenceDesignationComponent cr : cc.getDesignation()) {
                    Coding use = cr.getUse();
                    if (use == null) continue;
                    if ("enAbbreviation".equals(use.getCode())) {
                        enAbbreviation = cr.getValue();
                    } else if ("900000000000003001".equals(use.getCode())
                            || "Fully Specified Name".equals(use.getDisplay())) {
                        fullySpecifiedName = cr.getValue();
                    }
                }

                if (fullySpecifiedName != null) {
                    imm.setDisplayName(fullySpecifiedName);
                }
                if (enAbbreviation != null && fullySpecifiedName != null) {
                    // Brand picklist: first word of FSN + abbreviation, e.g. "Infanrix (INF)"
                    String firstWord = fullySpecifiedName.split(" ")[0];
                    imm.setPicklistName(firstWord + " (" + enAbbreviation + ")");
                } else if (enAbbreviation != null) {
                    imm.setPicklistName(enAbbreviation);
                }

                for (Extension ext : cc.getExtension()) {
                    String extUrl = ext.getUrl();
                    // nvc-parent-concept was renamed to nvc-linked-generic-concept in NVC V2
                    if (NVC_LINKED_GENERIC_CONCEPT_URL.equals(extUrl)) {
                        if (!(ext.getValue() instanceof CodeableConcept)) continue;
                        CodeableConcept parentC = (CodeableConcept) ext.getValue();
                        for (Coding parentCode : parentC.getCoding()) {
                            if (NVC_GENERIC_VALUESET_URL.equals(parentCode.getSystem())) {
                                imm.setParentConceptId(parentCode.getCode());
                                break;
                            }
                        }
                    } else if (NVC_MARKET_AUTH_HOLDERS_URL.equals(extUrl)) {
                        for (Extension mahExt : ext.getExtension()) {
                            if (NVC_MARKET_AUTH_HOLDER_URL.equals(mahExt.getUrl())) {
                                if (imm.getSnomedConceptId() != null && mahExt.getValue() != null) {
                                    dinManufactureMap.put(imm.getSnomedConceptId(),
                                            mahExt.getValue().primitiveValue());
                                }
                            }
                        }
                    }
                    // nvc-dins, nvc-route-of-admins, nvc-typical-dose-sizes, nvc-strengths,
                    // nvc-product-statuses, nvc-lots, nvc-lot-number, nvc-expiry-date
                    // are all removed in NVC V2 and not processed here.
                }

                imm.setGeneric(false);
                saveImmunization(loggedInInfo, imm);
            }
        }
    }

    public void updateMedications(LoggedInInfo loggedInInfo, Bundle bundle) {
        processMedicationBundle(loggedInInfo, bundle);
    }

    private void processMedicationBundle(LoggedInInfo loggedInInfo, Bundle bundle) {
        for (BundleEntryComponent entry : bundle.getEntry()) {
            CVCMedication cMed = new CVCMedication();
            Medication med = (Medication) entry.getResource();

            // Extract the SNOMED code first so it can be used as the map key for manufacturer
            // lookup — keying by resource ID risks mismatch if the Medication ID differs from
            // the SNOMED concept code used in the Tradename ValueSet.
            String snomedCode = null;
            for (Coding c : med.getCode().getCoding()) {
                if ("http://snomed.info/sct".equals(c.getSystem())) {
                    snomedCode = c.getCode();
                    cMed.setSnomedCode(c.getCode());
                    cMed.setSnomedDisplay(c.getDisplay());
                }
            }

            if (snomedCode != null && dinManufactureMap.containsKey(snomedCode)) {
                cMed.setManufacturerDisplay(dinManufactureMap.get(snomedCode));
            }

            if (med.getStatus() != null) {
                cMed.setStatus(med.getStatus().toString());
            }

            for (Extension ext : med.getExtension()) {
                String extUrl = ext.getUrl();
                if (NVC_MARKET_AUTH_HOLDER_URL.equals(extUrl)) {
                    if (ext.getValue() != null) {
                        cMed.setManufacturerDisplay(ext.getValue().primitiveValue());
                    }
                } else if (NVC_PRODUCT_STATUS_URL.equals(extUrl)) {
                    if (ext.getValue() instanceof CodeableConcept) {
                        CodeableConcept statusConcept = (CodeableConcept) ext.getValue();
                        for (Coding statusCode : statusConcept.getCoding()) {
                            if (NVC_SHELF_STATUS_VALUESET_URL.equals(statusCode.getSystem())) {
                                cMed.setStatus(statusCode.getDisplay());
                            }
                        }
                    }
                }
                // nvc-lots, nvc-lot-number, nvc-expiry-date removed in NVC V2
            }

            cMed.setBrand(true);
            saveMedication(loggedInInfo, cMed);
        }
    }

    public void updateAnatomicalSites(LoggedInInfo loggedInInfo, ValueSet vs) {
        LookupList ll = lookupListManager.findLookupListByName(loggedInInfo, "AnatomicalSite");
        if (ll == null) {
            ll = new LookupList();
            ll.setActive(true);
            ll.setCreatedBy("CARLOS");
            ll.setDateCreated(new Date());
            ll.setDescription("Anatomical Sites from NVC");
            ll.setName("AnatomicalSite");
            ll.setListTitle("Anatomical Site");
            ll = lookupListManager.addLookupList(loggedInInfo, ll);
        } else {
            // Deactivate existing items so the list is refreshed cleanly
            for (LookupListItem item : lookupListItemDao.findByLookupListId(ll.getId(), true)) {
                item.setActive(false);
                lookupListItemDao.merge(item);
            }
        }

        if (!vs.hasCompose()) {
            return;
        }
        int displayOrder = 0;
        for (ConceptSetComponent c : vs.getCompose().getInclude()) {
            for (ConceptReferenceComponent cc : c.getConcept()) {
                LookupListItem lli = new LookupListItem();
                lli.setActive(true);
                lli.setCreatedBy("CARLOS");
                lli.setDateCreated(new Date());
                lli.setLabel(cc.getDisplay());
                lli.setValue(cc.getCode());
                lli.setLookupListId(ll.getId());
                lli.setDisplayOrder(displayOrder++);
                lookupListManager.addLookupListItem(loggedInInfo, lli);
            }
        }
    }

    public void updateRoutes(LoggedInInfo loggedInInfo, ValueSet vs) {
        LookupList ll = lookupListManager.findLookupListByName(loggedInInfo, "RouteOfAdmin");
        if (ll == null) {
            ll = new LookupList();
            ll.setActive(true);
            ll.setCreatedBy("CARLOS");
            ll.setDateCreated(new Date());
            ll.setDescription("Routes of Administration from NVC");
            ll.setName("RouteOfAdmin");
            ll.setListTitle("Routes of Administration");
            ll = lookupListManager.addLookupList(loggedInInfo, ll);
        } else {
            // Deactivate existing items so the list is refreshed cleanly
            for (LookupListItem item : lookupListItemDao.findByLookupListId(ll.getId(), true)) {
                item.setActive(false);
                lookupListItemDao.merge(item);
            }
        }

        if (!vs.hasCompose()) {
            return;
        }
        int displayOrder = 0;
        for (ConceptSetComponent c : vs.getCompose().getInclude()) {
            for (ConceptReferenceComponent cc : c.getConcept()) {
                LookupListItem lli = new LookupListItem();
                lli.setActive(true);
                lli.setCreatedBy("CARLOS");
                lli.setDateCreated(new Date());
                lli.setLabel(cc.getDisplay());
                lli.setValue(cc.getCode());
                lli.setLookupListId(ll.getId());
                lli.setDisplayOrder(displayOrder++);
                lookupListManager.addLookupListItem(loggedInInfo, lli);
            }
        }
    }

    private void setUpdatedInPropertyTable() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        UserProperty up = userPropertyDao.getProp(CVC_UPDATED_PROP);
        if (up == null) {
            up = new UserProperty();
            up.setName(CVC_UPDATED_PROP);
        }
        up.setValue(formatter.format(new Date()));
        userPropertyDao.saveProp(up);
    }

    private void setFirstDateInPropertyTable() {
        UserProperty up = userPropertyDao.getProp(CVC_FIRST_DATE_PROP);
        if (up == null) {
            up = new UserProperty();
            up.setName(CVC_FIRST_DATE_PROP);
            up.setValue(String.valueOf(new Date().getTime()));
            userPropertyDao.saveProp(up);
        }
    }

    public void saveImmunization(LoggedInInfo loggedInInfo, CVCImmunization immunization) {
        immunizationDao.saveEntity(immunization);
        LogAction.addLogSynchronous(loggedInInfo, "CanadianVaccineCatalogueManager.saveImmunization",
                immunization.getId().toString());
    }

    public void saveMedication(LoggedInInfo loggedInInfo, CVCMedication medication) {
        Set<CVCMedicationGTIN> gtins = medication.getGtinList();
        Set<CVCMedicationLotNumber> lotNumbers = medication.getLotNumberList();

        medication.setGtinList(null);
        medication.setLotNumberList(null);
        medicationDao.saveEntity(medication);

        for (CVCMedicationGTIN g : gtins) {
            gtinDao.saveEntity(g);
        }
        for (CVCMedicationLotNumber l : lotNumbers) {
            lotNumberDao.saveEntity(l);
        }
        LogAction.addLogSynchronous(loggedInInfo, "CanadianVaccineCatalogueManager.saveMedication",
                medication.getId().toString());
    }

    public CVCMedicationLotNumber findByLotNumber(LoggedInInfo loggedInInfo, String lotNumber) {
        CVCMedicationLotNumber result = lotNumberDao.findByLotNumber(lotNumber);
        LogAction.addLogSynchronous(loggedInInfo, "CanadianVaccineCatalogueManager.findByLotNumber",
                "lotNumber:" + lotNumber);
        return result;
    }

    public CVCImmunization getBrandNameImmunizationBySnomedCode(LoggedInInfo loggedInInfo, String snomedCode) {
        CVCImmunization result = immunizationDao.findBySnomedConceptId(snomedCode);
        LogAction.addLogSynchronous(loggedInInfo, "CanadianVaccineCatalogueManager.getBrandNameImmunizationBySnomedCode",
                "snomedCode:" + snomedCode);
        return result;
    }

    public List<CVCImmunization> query(String term, boolean includeGenerics, boolean includeBrands,
            boolean includeLotNumbers, boolean includeGTINs, StringBuilder matchedLotNumber) {
        List<CVCImmunization> results = new ArrayList<>();

        if (includeGenerics || includeBrands) {
            results.addAll(immunizationDao.query(term, includeGenerics, includeBrands));
        }
        if (includeLotNumbers) {
            List<CVCMedicationLotNumber> res = lotNumberDao.query(term);
            if (res.size() == 1 && matchedLotNumber != null) {
                matchedLotNumber.append(res.get(0).getLotNumber());
            }
            for (CVCMedicationLotNumber t : res) {
                results.add(immunizationDao.findBySnomedConceptId(t.getMedication().getSnomedCode()));
            }
        }
        if (includeGTINs) {
            for (CVCMedicationGTIN t : gtinDao.query(term)) {
                results.add(immunizationDao.findBySnomedConceptId(t.getMedication().getSnomedCode()));
            }
        }

        // Deduplicate by SNOMED concept ID
        Map<String, CVCImmunization> tmp = new HashMap<>();
        for (CVCImmunization i : results) {
            tmp.put(i.getSnomedConceptId(), i);
        }
        List<CVCImmunization> uniqueResults = new ArrayList<>(tmp.values());
        Collections.sort(uniqueResults, new PrevalenceComparator());
        return uniqueResults;
    }

    /**
     * Returns the NVC base URL, allowing override from UserProperty then CarlosProperties.
     * Default is the NVC V2 base URL: {@value NVC_DEFAULT_BASE_URL}.
     * Trailing slashes are stripped so callers can safely concatenate path segments.
     */
    public static String getCVCURL() {
        String url = CarlosProperties.getInstance().getProperty("cvc.url", NVC_DEFAULT_BASE_URL);
        UserPropertyDAO upDao = SpringUtils.getBean(UserPropertyDAO.class);
        UserProperty up = upDao.getProp("cvc.url");
        if (up != null && up.getValue() != null && !up.getValue().isBlank()) {
            url = up.getValue();
        }
        // Normalize: strip trailing slash to prevent double-slash when concatenating path segments
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * Returns true if CVC data has been loaded and the given creation date falls after
     * the first CVC load date (i.e. the record was created after CVC was first activated).
     */
    public static boolean getCVCActive(Date creationDate) {
        UserPropertyDAO upDao = SpringUtils.getBean(UserPropertyDAO.class);
        UserProperty up = upDao.getProp(CVC_FIRST_DATE_PROP);
        if (up == null || up.getValue() == null || up.getValue().isBlank()) {
            return false;
        }
        if (creationDate == null) {
            return true;
        }
        try {
            Date cvcFirstDate = new Date(Long.parseLong(up.getValue()));
            return cvcFirstDate.before(creationDate);
        } catch (NumberFormatException e) {
            MiscUtils.getLogger().warn("CVC first-date property is not a valid long: {}", up.getValue());
            return false;
        }
    }
}

class PrevalenceComparator implements Comparator<CVCImmunization> {
    public int compare(CVCImmunization i1, CVCImmunization i2) {
        Integer d1 = i1.getPrevalence();
        Integer d2 = i2.getPrevalence();
        if (d1 == null && d2 != null) return 1;
        else if (d1 != null && d2 == null) return -1;
        else if (d1 == null) return 0;
        else return d1.compareTo(d2) * -1;
    }
}
