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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.Logger;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;

import io.github.carlos_emr.carlos.commn.dao.AbstractDao;
import io.github.carlos_emr.carlos.commn.dao.CVCImmunizationDao;
import io.github.carlos_emr.carlos.commn.dao.CVCMedicationDao;
import io.github.carlos_emr.carlos.commn.dao.CVCMedicationGTINDao;
import io.github.carlos_emr.carlos.commn.dao.CVCMedicationLotNumberDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.CVCImmunization;
import io.github.carlos_emr.carlos.commn.model.CVCMedication;
import io.github.carlos_emr.carlos.commn.model.CVCMedicationGTIN;
import io.github.carlos_emr.carlos.commn.model.CVCMedicationLotNumber;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * The locally stored Canadian vaccine catalogue, refreshed from the National Vaccine Catalogue
 * (NVC) V2, and the lookups the prevention screens make against it.
 *
 * <p>{@link #update(LoggedInInfo)} downloads and reads the whole NVC V2 bundle before it touches
 * the database, then replaces the stored catalogue in one transaction, so a failed download or a
 * malformed bundle leaves the previous catalogue in place.</p>
 */
@Service
public class CanadianVaccineCatalogueManager {

    private static final Logger logger = MiscUtils.getLogger();
    private static final FhirContext FHIR_R4 = FhirContext.forR4Cached();
    private static final String CVC_UPDATED_PROP = "cvc.updated";
    /** One refresh at a time per CARLOS instance. */
    private static final ReentrantLock UPDATE_LOCK = new ReentrantLock();

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
    PlatformTransactionManager transactionManager;

    NationalVaccineCatalogueClient catalogueClient = new NationalVaccineCatalogueClient();

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
     * Replaces the stored catalogue with the current NVC V2 bundle.
     *
     * <p>The download and parsing happen outside any database transaction. Only a bundle that
     * parsed and holds the Generic and Tradename value sets reaches the database, where the old
     * catalogue is deleted and the new one saved in a single transaction. A refresh already running
     * on this instance makes this call return without doing anything.</p>
     *
     * @throws IOException if the bundle cannot be downloaded or read; the stored catalogue is unchanged
     */
    public void update(LoggedInInfo loggedInInfo) throws IOException {
        if (!UPDATE_LOCK.tryLock()) {
            logger.warn("Vaccine catalogue update skipped: another update is running");
            return;
        }
        try {
            NationalVaccineCatalogueMapper.Catalogue catalogue = readCatalogue(catalogueClient.fetchBundleJson());
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> replaceCatalogue(catalogue));
            String counts = "immunizations=" + catalogue.immunizations().size()
                    + " medications=" + catalogue.medications().size()
                    + " lotNumbers=" + catalogue.lotNumberCount();
            LogAction.addLogSynchronous(loggedInInfo, "CanadianVaccineCatalogueManager.update", counts);
            logger.info("Vaccine catalogue updated from the National Vaccine Catalogue: {}", counts);
        } finally {
            UPDATE_LOCK.unlock();
        }
    }

    static NationalVaccineCatalogueMapper.Catalogue readCatalogue(String json) throws IOException {
        try {
            Bundle bundle = FHIR_R4.newJsonParser().parseResource(Bundle.class, json);
            return NationalVaccineCatalogueMapper.map(bundle);
        } catch (DataFormatException | IllegalArgumentException e) {
            throw new IOException("National Vaccine Catalogue bundle could not be read", e);
        }
    }

    private void replaceCatalogue(NationalVaccineCatalogueMapper.Catalogue catalogue) {
        // NVC V2 has no Ontario ISPA flag; keep what the previous catalogue recorded per vaccine,
        // since DHIR consent and ISPA checks read it.
        Set<String> ispaVaccines = new HashSet<>();
        for (CVCImmunization existing : immunizationDao.findAll(0, AbstractDao.MAX_LIST_RETURN_SIZE)) {
            if (existing.isIspa()) {
                ispaVaccines.add(existing.getSnomedConceptId());
            }
        }
        // Children first: lot numbers and GTINs reference their medication.
        lotNumberDao.removeAll();
        gtinDao.removeAll();
        medicationDao.removeAll();
        immunizationDao.removeAll();
        for (CVCImmunization immunization : catalogue.immunizations()) {
            immunization.setIspa(ispaVaccines.contains(immunization.getSnomedConceptId()));
            immunizationDao.persist(immunization);
        }
        for (CVCMedication medication : catalogue.medications()) {
            persistMedication(medication);
        }
        setUpdatedInPropertyTable();
    }

    /** Saves a medication, then its GTINs and lot numbers, which reference it. */
    private void persistMedication(CVCMedication medication) {
        Set<CVCMedicationGTIN> gtins = medication.getGtinList();
        Set<CVCMedicationLotNumber> lotNumbers = medication.getLotNumberList();
        medication.setGtinList(new HashSet<>());
        medication.setLotNumberList(new HashSet<>());
        medicationDao.persist(medication);
        for (CVCMedicationGTIN gtin : gtins) {
            gtinDao.persist(gtin);
        }
        for (CVCMedicationLotNumber lotNumber : lotNumbers) {
            lotNumberDao.persist(lotNumber);
        }
    }

    /**
     * Whether a catalogue has been loaded from the National Vaccine Catalogue, so the prevention
     * page can offer search by brand, generic or lot number.
     */
    public boolean hasCatalogue() {
        return userPropertyDao.getProp(CVC_UPDATED_PROP) != null;
    }

    private void setUpdatedInPropertyTable() {
        UserProperty updated = userPropertyDao.getProp(CVC_UPDATED_PROP);
        if (updated == null) {
            updated = new UserProperty();
            updated.setName(CVC_UPDATED_PROP);
        }
        updated.setValue(new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        userPropertyDao.saveProp(updated);
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
            // An imported lot/GTIN may reference a medication whose immunization
            // has not arrived yet; it must not abort the remaining suggestions.
            // A generic with no picklist name (inactive in the catalogue) cannot be recorded.
            if (i != null && !(i.isGeneric() && i.getPicklistName() == null)) tmp.put(i.getSnomedConceptId(), i);
        }
        List<CVCImmunization> uniqueResults = new ArrayList<>(tmp.values());
        Collections.sort(uniqueResults, new PrevalenceComparator());
        return uniqueResults;
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
