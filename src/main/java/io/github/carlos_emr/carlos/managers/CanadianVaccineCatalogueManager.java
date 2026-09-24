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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.util.Timeout;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
import io.github.carlos_emr.carlos.prevention.nvc.NvcBundleException;
import io.github.carlos_emr.carlos.prevention.nvc.NvcBundleParser;
import io.github.carlos_emr.carlos.prevention.nvc.NvcCatalogue;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Local copy of the Canadian vaccine catalogue used by the Prevention module.
 *
 * <p>The catalogue is sourced from the Public Health Agency of Canada National Vaccine Catalogue
 * (NVC) v2 FHIR R4 bundle, which replaced the retired Canadian Vaccine Catalogue (CVC) v1 DSTU3
 * API. The local {@code CVC*} tables and this class name are kept for schema and caller
 * compatibility.
 *
 * <p>{@link #update(LoggedInInfo)} downloads and fully parses the bundle <em>before</em> opening
 * a transaction, then replaces the catalogue in a single transaction. A failed download or an
 * unusable bundle therefore leaves the installed catalogue untouched, and a failed write rolls
 * back to it; the (up to two-minute) download never holds a database connection.
 */
@Service
public class CanadianVaccineCatalogueManager {

    /** NVC v2 FHIR base used when {@code cvc.url} is not set. */
    public static final String NVC_DEFAULT_BASE_URL = "https://nvc-cnv.canada.ca/fhir/v2";
    static final String NVC_BUNDLE_PATH = "/Bundle/NVC";
    static final String NVC_ACCEPT = "application/fhir+json";

    static final String CVC_UPDATED_PROP = "cvc.updated";
    static final String CVC_FIRST_DATE_PROP = "cvc.firstdate";
    static final String CVC_VERSION_PROP = "cvc.version";

    static final String ANATOMICAL_SITE_LIST = "AnatomicalSite";
    static final String ROUTE_OF_ADMIN_LIST = "RouteOfAdmin";
    private static final String CATALOGUE_AUTHOR = "NVC";

    /** The September 2026 bundle is ~9 MB; the cap only guards against a runaway response. */
    static final int MAX_BUNDLE_BYTES = 64 * 1024 * 1024;

    private final Logger logger = MiscUtils.getLogger();

    private final CVCMedicationDao medicationDao;
    private final CVCMedicationLotNumberDao lotNumberDao;
    private final CVCMedicationGTINDao gtinDao;
    private final CVCImmunizationDao immunizationDao;
    private final UserPropertyDAO userPropertyDao;
    private final LookupListManager lookupListManager;
    private final LookupListItemDao lookupListItemDao;
    private final SecurityInfoManager securityInfoManager;
    private final TransactionTemplate transactionTemplate;
    /**
     * Serializes updates end to end (download and replace). Without it two administrators could
     * download different NVC versions and the older one could commit last. A later caller waits,
     * then downloads afresh, so the last install is always the newest bundle.
     */
    private final ReentrantLock updateLock = new ReentrantLock();

    public CanadianVaccineCatalogueManager(CVCMedicationDao medicationDao,
                                           CVCMedicationLotNumberDao lotNumberDao,
                                           CVCMedicationGTINDao gtinDao,
                                           CVCImmunizationDao immunizationDao,
                                           UserPropertyDAO userPropertyDao,
                                           LookupListManager lookupListManager,
                                           LookupListItemDao lookupListItemDao,
                                           SecurityInfoManager securityInfoManager,
                                           PlatformTransactionManager transactionManager) {
        this.medicationDao = medicationDao;
        this.lotNumberDao = lotNumberDao;
        this.gtinDao = gtinDao;
        this.immunizationDao = immunizationDao;
        this.userPropertyDao = userPropertyDao;
        this.lookupListManager = lookupListManager;
        this.lookupListItemDao = lookupListItemDao;
        this.securityInfoManager = securityInfoManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

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
     * Downloads the NVC bundle and replaces the local catalogue with it.
     *
     * @param loggedInInfo the administrator running the update; requires {@code _admin} write
     * @return the catalogue that was installed
     * @throws SecurityException   if the caller lacks {@code _admin} write
     * @throws IOException         if the bundle could not be downloaded; nothing was changed
     * @throws NvcBundleException  if the download is not a usable NVC bundle; nothing was changed
     */
    public NvcCatalogue update(LoggedInInfo loggedInInfo) throws IOException, NvcBundleException {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }
        String url = getCVCURL() + NVC_BUNDLE_PATH;
        NvcCatalogue catalogue;
        updateLock.lock();
        try {
            catalogue = NvcBundleParser.parse(fetchBundleJson(url));
            transactionTemplate.executeWithoutResult(status -> replaceCatalogue(loggedInInfo, catalogue));
        } finally {
            updateLock.unlock();
        }
        logger.info("NVC catalogue {} installed: {} generics, {} tradenames, {} lots",
                catalogue.version(), catalogue.generics().size(), catalogue.tradenames().size(),
                catalogue.products().stream().mapToInt(p -> p.lots().size()).sum());
        return catalogue;
    }

    /**
     * Fetches the raw bundle. Package-private so tests can substitute a fixture without a network.
     */
    String fetchBundleJson(String url) throws IOException {
        URI uri = URI.create(url);
        if (!"https".equals(uri.getScheme())) {
            // The catalogue feeds clinical documentation; never accept it over cleartext.
            throw new IOException("NVC catalogue URL must use https");
        }
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(30))
                .setResponseTimeout(Timeout.ofSeconds(120))
                .build();
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(15))
                .build();

        // useSystemProperties() honours https.proxyHost/proxyPort and the JVM trust store, which
        // clinic and hospital networks commonly require for outbound HTTPS. Redirects are not
        // followed so the https-only rule above cannot be bypassed by a downgrade redirect; a
        // moved endpoint surfaces as an HTTP 3xx and is fixed by setting cvc.url.
        try (CloseableHttpClient client = HttpClients.custom()
                .useSystemProperties()
                .disableRedirectHandling()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(connectionConfig)
                        .build())
                .setDefaultRequestConfig(requestConfig)
                .build()) {
            return client.execute(bundleRequest(uri), response -> {
                if (response.getCode() != HttpStatus.SC_OK) {
                    throw new IOException("NVC bundle download returned HTTP " + response.getCode());
                }
                return readBounded(response.getEntity());
            });
        }
    }

    /**
     * The bundle GET. NVC answers {@code 406 Not Acceptable} to any Accept header that lists
     * more than one media type (including {@code *}{@code /*}), so exactly one is sent.
     */
    static HttpGet bundleRequest(URI uri) {
        HttpGet request = new HttpGet(uri);
        request.addHeader("Accept", NVC_ACCEPT);
        request.addHeader("x-app-desc", "CARLOS EMR");
        return request;
    }

    private static String readBounded(HttpEntity entity) throws IOException {
        if (entity == null) {
            throw new IOException("NVC bundle download returned no body");
        }
        if (entity.getContentLength() > MAX_BUNDLE_BYTES) {
            throw new IOException("NVC bundle exceeds " + MAX_BUNDLE_BYTES + " bytes");
        }
        try (InputStream in = entity.getContent()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[64 * 1024];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BUNDLE_BYTES) {
                    throw new IOException("NVC bundle exceeds " + MAX_BUNDLE_BYTES + " bytes");
                }
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    /**
     * Replaces every catalogue row with {@code catalogue}. Must run inside a transaction.
     */
    void replaceCatalogue(LoggedInInfo loggedInInfo, NvcCatalogue catalogue) {
        // Children first: lots and GTINs reference CVCMedication rows.
        lotNumberDao.removeAll();
        gtinDao.removeAll();
        medicationDao.removeAll();
        immunizationDao.removeAll();

        for (NvcCatalogue.Vaccine generic : catalogue.generics()) {
            immunizationDao.persist(toImmunization(generic, true));
        }
        for (NvcCatalogue.Vaccine tradename : catalogue.tradenames()) {
            immunizationDao.persist(toImmunization(tradename, false));
        }

        int lotCount = 0;
        for (NvcCatalogue.Product product : catalogue.products()) {
            CVCMedication medication = new CVCMedication();
            medication.setBrand(true);
            medication.setSnomedCode(product.snomedCode());
            medication.setSnomedDisplay(product.displayName());
            medication.setDin(product.din());
            medication.setDinDisplayName(product.displayName());
            medication.setManufacturerDisplay(product.manufacturer());
            medication.setStatus(product.status());
            medicationDao.persist(medication);
            for (NvcCatalogue.Lot lot : product.lots()) {
                Date expiry = lot.expiryDate() == null ? null : java.sql.Date.valueOf(lot.expiryDate());
                lotNumberDao.persist(new CVCMedicationLotNumber(medication, lot.lotNumber(), expiry));
                lotCount++;
            }
        }

        syncLookupList(loggedInInfo, ANATOMICAL_SITE_LIST, "Anatomical Site",
                "Anatomical sites of administration from the National Vaccine Catalogue", catalogue.anatomicalSites());
        syncLookupList(loggedInInfo, ROUTE_OF_ADMIN_LIST, "Routes of Administration",
                "Routes of administration from the National Vaccine Catalogue", catalogue.routes());

        userPropertyDao.saveProp(CVC_UPDATED_PROP, new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()));
        // Always overwritten: an unversioned bundle must not leave the previous install's
        // version on display (a blank value reads back as "no version").
        userPropertyDao.saveProp(CVC_VERSION_PROP, catalogue.version() == null ? "" : catalogue.version());
        if (userPropertyDao.getProp(CVC_FIRST_DATE_PROP) == null) {
            userPropertyDao.saveProp(CVC_FIRST_DATE_PROP, String.valueOf(System.currentTimeMillis()));
        }

        // One audit row per update, not one per catalogue row (~5,000 inserts).
        LogAction.addLogSynchronous(loggedInInfo, "CanadianVaccineCatalogueManager.update",
                "version=" + catalogue.version() + " generics=" + catalogue.generics().size()
                        + " tradenames=" + catalogue.tradenames().size() + " lots=" + lotCount);
    }

    private static CVCImmunization toImmunization(NvcCatalogue.Vaccine vaccine, boolean generic) {
        CVCImmunization immunization = new CVCImmunization();
        immunization.setVersionId(0);
        immunization.setSnomedConceptId(vaccine.snomedConceptId());
        immunization.setDisplayName(vaccine.displayName());
        immunization.setPicklistName(vaccine.picklistName());
        immunization.setParentConceptId(vaccine.parentConceptId());
        immunization.setGeneric(generic);
        return immunization;
    }

    /**
     * Upserts a lookup list by item value: NVC concepts are (re)activated with their current
     * label and order, and NVC-created items NVC no longer publishes are deactivated rather than
     * deleted so that any record already referencing them still resolves. Locally added items
     * are never touched.
     */
    private void syncLookupList(LoggedInInfo loggedInInfo, String name, String title, String description,
                                List<NvcCatalogue.CodedValue> values) {
        if (values.isEmpty()) {
            // An absent subset is not evidence that every site/route was withdrawn.
            return;
        }
        LookupList list = lookupListManager.findLookupListByName(loggedInInfo, name);
        if (list == null) {
            list = new LookupList();
            list.setName(name);
            list.setListTitle(title);
            list.setDescription(description);
            list.setActive(true);
            list.setCreatedBy(CATALOGUE_AUTHOR);
            list.setDateCreated(new Date());
            list = lookupListManager.addLookupList(loggedInInfo, list);
        }

        Map<String, LookupListItem> existing = new HashMap<>();
        for (boolean active : new boolean[]{true, false}) {
            for (LookupListItem item : lookupListItemDao.findByLookupListId(list.getId(), active)) {
                existing.putIfAbsent(item.getValue(), item);
            }
        }

        int order = 0;
        for (NvcCatalogue.CodedValue value : values) {
            LookupListItem item = existing.remove(value.code());
            if (item == null) {
                item = new LookupListItem();
                item.setLookupListId(list.getId());
                item.setValue(value.code());
                item.setLabel(value.label());
                item.setDisplayOrder(order++);
                item.setActive(true);
                item.setCreatedBy(CATALOGUE_AUTHOR);
                item.setDateCreated(new Date());
                lookupListManager.addLookupListItem(loggedInInfo, item);
            } else {
                item.setLabel(value.label());
                item.setDisplayOrder(order++);
                item.setActive(true);
                // Through the manager, not the DAO: it evicts the shared lookup-list cache.
                lookupListManager.updateLookupListItem(loggedInInfo, item);
            }
        }
        for (LookupListItem withdrawn : existing.values()) {
            // Only retire values this synchronisation created. Items an administrator added
            // through Lookup List Manager (or a clinic list that predates the catalogue) stay.
            if (withdrawn.isActive() && CATALOGUE_AUTHOR.equals(withdrawn.getCreatedBy())) {
                withdrawn.setActive(false);
                lookupListManager.updateLookupListItem(loggedInInfo, withdrawn);
            }
        }
    }

    /**
     * @return when the catalogue was last installed ({@code yyyy-MM-dd HH:mm}), or {@code null}
     *         if it never has been
     */
    public String getLastUpdated() {
        return propertyValue(CVC_UPDATED_PROP);
    }

    /**
     * @return the NVC version stamp of the installed catalogue, or {@code null}
     */
    public String getInstalledVersion() {
        return propertyValue(CVC_VERSION_PROP);
    }

    /**
     * Whether a catalogue has been installed. The Prevention screen uses this to decide between
     * the catalogue-backed brand/generic/lot search and the static prevention list.
     */
    public boolean isCatalogueInstalled() {
        return getLastUpdated() != null;
    }

    private String propertyValue(String name) {
        UserProperty property = userPropertyDao.getProp(name);
        return property == null || property.getValue() == null || property.getValue().isBlank()
                ? null : property.getValue();
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
            if (i != null) tmp.put(i.getSnomedConceptId(), i);
        }
        List<CVCImmunization> uniqueResults = new ArrayList<>(tmp.values());
        Collections.sort(uniqueResults, new PrevalenceComparator());
        return uniqueResults;
    }

    /**
     * Returns the NVC FHIR base URL: the {@code cvc.url} property when set (for a mirror or
     * proxy), otherwise {@value #NVC_DEFAULT_BASE_URL}. Trailing slashes are stripped so callers
     * can append a path.
     */
    public static String getCVCURL() {
        String url = CarlosProperties.getInstance().getProperty("cvc.url");
        if (url == null || url.isBlank()) {
            url = NVC_DEFAULT_BASE_URL;
        }
        url = url.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}

class PrevalenceComparator implements Comparator<CVCImmunization> {
    @Override
    public int compare(CVCImmunization i1, CVCImmunization i2) {
        Integer d1 = i1.getPrevalence();
        Integer d2 = i2.getPrevalence();
        if (d1 == null && d2 != null) return 1;
        else if (d1 != null && d2 == null) return -1;
        else if (d1 == null) return 0;
        else return d1.compareTo(d2) * -1;
    }
}
