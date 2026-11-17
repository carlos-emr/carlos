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
package io.github.carlos_emr.carlos.prevention.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionExtDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.prevention.PreventionData;
import io.github.carlos_emr.carlos.prevention.PreventionPageData;
import io.github.carlos_emr.carlos.prevention.pageUtil.PreventionPrintLoader.Entry;
import io.github.carlos_emr.carlos.prevention.pageUtil.PreventionPrintLoader.Printout;
import io.github.carlos_emr.carlos.prevention.pageUtil.PreventionPrintLoader.Section;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * The printout read from preventions stored the way {@code AddPrevention2Action} stores them: the
 * keys {@link PreventionPrintLoader} reads must be the ones {@link PreventionData} writes and lists,
 * or a print would silently drop a lot number or comment.
 */
@Tag("integration")
@Tag("prevention")
@Isolated("PreventionData retains static DAO references")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("PreventionPrintLoader reads stored preventions")
class PreventionPrintLoaderIntegrationTest extends CarlosTestBase {

    private static final int PATIENT = 89004101;
    private static final String COMMENT = "Reviewed labs with the patient; find attached the CBC and lytes.";

    @Autowired private PreventionDao preventionDao;
    @Autowired private PreventionExtDao extensionDao;
    @Autowired private PartialDateDao partialDateDao;
    @Autowired private PlatformTransactionManager transactionManager;
    @PersistenceContext private EntityManager em;

    private final Map<String, Object> originalBeans = new HashMap<>();
    private final LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
    private Demographic patient;
    private PreventionPrintLoader loader;

    @BeforeEach
    void setUp() throws Exception {
        // PreventionData keeps static DAO references that can belong to another test context.
        replaceBean("preventionDao", preventionDao);
        replaceBean("preventionExtDao", extensionDao);
        replaceBean("partialDateDao", partialDateDao);
        assertThat(preventionDao.findByDemographicId(PATIENT)).isEmpty();

        patient = new Demographic();
        patient.setDemographicNo(PATIENT);
        patient.setLastName("FAKE-PRINT");
        patient.setFirstName("Integration");
        patient.setYearOfBirth("1980");
        patient.setMonthOfBirth("01");
        patient.setDateOfBirth("02");
        patient.setHcType("ON");
        patient.setProviderNo("999998");
        DemographicManager demographicManager = mock(DemographicManager.class);
        when(demographicManager.getDemographic(loggedInInfo, String.valueOf(PATIENT))).thenReturn(patient);
        Constructor<PreventionPageData> pageData = PreventionPageData.class.getDeclaredConstructor(
                LoggedInInfo.class, String.class, DemographicManager.class);
        pageData.setAccessible(true);

        List<Map<String, String>> preventionTypes = List.of(type("PAP", "Screenings", "PAPMAM"),
                type("Inf", "Immunizations", "injection"), type("Td", "Immunizations", "injection"));

        loader = new PreventionPrintLoader(() -> preventionTypes, (info, demographicNo) -> {
            try {
                return pageData.newInstance(info, demographicNo, demographicManager);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }, PreventionData::getPreventionKeyValues, providerNo -> "Integration MRP", () -> true, () -> true);
    }

    @AfterEach
    void tearDown() {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                for (Prevention stored : preventionDao.findByDemographicId(PATIENT)) {
                    em.createQuery("delete from PreventionExt e where e.preventionId = :id")
                            .setParameter("id", stored.getId()).executeUpdate();
                    em.createQuery("delete from PartialDate p where p.tableName = :tableName and p.tableId = :id")
                            .setParameter("tableName", io.github.carlos_emr.carlos.commn.model.PartialDate.PREVENTION)
                            .setParameter("id", stored.getId()).executeUpdate();
                    em.clear();
                    em.createQuery("delete from Prevention p where p.id = :id")
                            .setParameter("id", stored.getId()).executeUpdate();
                }
            });
        } finally {
            originalBeans.forEach((name, bean) -> ReflectionTestUtils.setField(PreventionData.class, name, bean));
        }
    }

    @Test
    @DisplayName("should print stored immunizations with their extensions and leave deleted records out")
    void shouldLoadStoredRecords_withExtensions() {
        insert("Inf", "2025-10-01", "0", ext("comments", COMMENT, "lot", "L-INT-1", "name", "FLUZONE High-Dose",
                "manufacture", "Sanofi Pasteur", "location", "left deltoid", "dose", "0.5 mL"));
        insert("PAP", "2024-05-05", "0", ext("result", "other", "reason", "repeat in 6 months"));
        Integer deleted = insert("Td", "2020-01-01", "0", ext("lot", "L-DELETED"));
        PreventionData.deletePreventionData(String.valueOf(deleted), String.valueOf(PATIENT));

        Printout printout = loader.load(loggedInInfo, String.valueOf(PATIENT), new String[]{"PAP", "Td", "Inf"}, false);

        assertThat(printout.patient()).isSameAs(patient);
        assertThat(printout.sections()).extracting(Section::preventionType).containsExactly("Inf", "PAP");
        Entry flu = printout.sections().get(0).entries().get(0);
        assertThat(flu.date()).isEqualTo("2025-10-01");
        assertThat(flu.age()).isEqualTo("45 years");
        assertThat(flu.status()).isEqualTo("0");
        assertThat(flu.comments()).isEqualTo(COMMENT);
        assertThat(flu.lot()).isEqualTo("L-INT-1");
        assertThat(flu.vaccineName()).isEqualTo("FLUZONE High-Dose");
        assertThat(flu.manufacturer()).isEqualTo("Sanofi Pasteur");
        assertThat(flu.location()).isEqualTo("left deltoid");
        assertThat(flu.dose()).isEqualTo("0.5 mL");
        Entry pap = printout.sections().get(1).entries().get(0);
        assertThat(pap.result()).isEqualTo("other");
        assertThat(pap.reason()).isEqualTo("repeat in 6 months");
    }

    @Test
    @DisplayName("should draw the stored records into the PDF")
    void shouldDrawStoredRecords_intoPdf() throws Exception {
        insert("Inf", "2025-10-01", "1", ext("comments", COMMENT, "lot", "L-INT-2"));

        Printout printout = loader.load(loggedInInfo, String.valueOf(PATIENT), new String[]{"Inf"}, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new PreventionPrintPdf().printPdf(printout, out);

        String text;
        try (PDDocument document = Loader.loadPDF(out.toByteArray())) {
            text = new PDFTextStripper().getText(document);
        }
        assertThat(text.replaceAll("\\s+", " ")).contains("FAKE-PRINT, Integration", "MRP: Integration MRP",
                "Prevention Inf", "2025-10-01", "Status: Refused", "Lot#: L-INT-2", COMMENT);
        assertThat(text).doesNotContain("null");
    }

    private Integer insert(String type, String date, String refused, ArrayList<Map<String, String>> extensions) {
        return PreventionData.insertPreventionData("999998", String.valueOf(PATIENT), date, "999998", "",
                type, refused, "", "0", extensions, null, null);
    }

    /** Extension rows shaped the way AddPrevention2Action passes them: one map per key. */
    private static ArrayList<Map<String, String>> ext(String... keyValues) {
        ArrayList<Map<String, String>> list = new ArrayList<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            list.add(Map.of(keyValues[i], keyValues[i + 1]));
        }
        return list;
    }

    private static HashMap<String, String> type(String name, String heading, String layout) {
        HashMap<String, String> type = new HashMap<>();
        type.put("name", name);
        type.put("headingName", heading);
        type.put("layout", layout);
        return type;
    }

    private void replaceBean(String name, Object bean) {
        originalBeans.put(name, ReflectionTestUtils.getField(PreventionData.class, name));
        ReflectionTestUtils.setField(PreventionData.class, name, bean);
    }
}
