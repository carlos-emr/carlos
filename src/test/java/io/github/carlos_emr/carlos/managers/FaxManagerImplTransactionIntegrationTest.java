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
package io.github.carlos_emr.carlos.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Real-transaction integration coverage for {@link FaxManagerImpl#createAndSaveFaxJob}: the
 * per-recipient {@code faxJobDao.persist} loop must commit atomically, so a mid-batch persist
 * failure rolls back recipients 1..N-1 instead of leaving orphaned WAITING rows that
 * {@code FaxSender} could transmit while the user only sees an error page.
 *
 * <p>The test method suspends the surrounding {@code CarlosTestBase} test transaction
 * ({@code Propagation.NOT_SUPPORTED}) so the manager's own {@code REQUIRED} transaction genuinely
 * commits or rolls back — inside a joined transaction, "manager rolled back" and "test rolled
 * back" are indistinguishable. Seeding and cleanup therefore run in their own committed
 * transactions via {@link TransactionTemplate}.</p>
 *
 * <p>Filesystem side effects (the temp-to-document promotion) are deliberately OUTSIDE the
 * transaction (see the manager's Javadoc); this test asserts database rows only.</p>
 */
@DisplayName("FaxManagerImpl transactional integration tests")
@Tag("integration")
@Tag("manager")
class FaxManagerImplTransactionIntegrationTest extends CarlosTestBase {

    private static final String SENDER_FAX_LINE = "5559990001";

    @Autowired
    private FaxManager faxManager;
    @Autowired
    private FaxJobDao faxJobDao;
    @Autowired
    private FaxConfigDao faxConfigDao;
    @Autowired
    private ClinicDAO clinicDAO;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private String originalDocumentDir;
    private Path documentDir;
    private Path tempWorkspace;
    private Integer seededConfigId;
    private Integer seededClinicId;

    @BeforeEach
    void setUpFixtures() throws IOException {
        transactionTemplate = new TransactionTemplate(transactionManager);

        // Promotion target: point DOCUMENT_DIR at a scratch directory so the real
        // copyFileToOscarDocuments succeeds; restored in tearDown (CarlosProperties is a
        // JVM-wide singleton shared with other tests in this fork).
        documentDir = Files.createTempDirectory("fax-tx-test-docdir-");
        originalDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());

        // Source PDF must live under the CARLOS-owned temp root for validateFilePath, and must be
        // a real PDF because createFaxJob counts its pages with PDFBox after promotion.
        tempWorkspace = Files.createTempDirectory(
                Files.createDirectories(Paths.get(System.getProperty("java.io.tmpdir"), "carlos-temp")),
                "tempPDF-tx-test-");
    }

    @AfterEach
    void tearDownFixtures() throws IOException {
        transactionTemplate.executeWithoutResult(status -> {
            // The manager's rollback removes the FaxJob rows; the committed seed rows are ours.
            faxJobDao.getReadyToSendFaxes(SENDER_FAX_LINE).forEach(faxJobDao::remove);
            if (seededConfigId != null) {
                faxConfigDao.remove((Object) seededConfigId);
            }
            if (seededClinicId != null) {
                clinicDAO.remove((Object) seededClinicId);
            }
        });

        if (originalDocumentDir == null) {
            CarlosProperties.getInstance().remove("DOCUMENT_DIR");
        } else {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", originalDocumentDir);
        }
        deleteRecursively(documentDir);
        deleteRecursively(tempWorkspace);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("should roll back every recipient row when a mid-batch persist fails")
    void shouldRollBackAllRecipients_whenMidBatchPersistFails() throws IOException {
        transactionTemplate.executeWithoutResult(status -> {
            FaxConfig config = new FaxConfig();
            config.setFaxNumber(SENDER_FAX_LINE);
            config.setFaxUser("tx-test-user");
            config.setActive(true);
            faxConfigDao.persist(config);
            seededConfigId = config.getId();

            Clinic clinic = new Clinic();
            clinic.setClinicName("Tx Test Clinic");
            clinic.setClinicAddress("1 Test Way");
            clinicDAO.persist(clinic);
            seededClinicId = clinic.getId();
        });

        Path sourcePdf = tempWorkspace.resolve("tx-test-fax.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(sourcePdf.toFile());
        }

        Map<String, Object> faxJobMap = new HashMap<>();
        faxJobMap.put("faxFilePath", sourcePdf.toString());
        faxJobMap.put("recipient", "Primary Recipient");
        faxJobMap.put("recipientFaxNumber", "4165550100");
        faxJobMap.put("senderFaxNumber", SENDER_FAX_LINE);
        faxJobMap.put("demographicNo", 1);
        faxJobMap.put("coverpage", "false");
        // Recipient 2's digit string overflows FaxJob.destination (bare @Column -> VARCHAR(255)
        // under hbm2ddl=create), so its persist throws AFTER recipient 1 persisted.
        faxJobMap.put("copyToRecipients", new String[] {
                "\"name\":\"Valid Copy\",\"fax\":\"4165550101\"",
                "\"name\":\"Overflow Copy\",\"fax\":\"" + "9".repeat(300) + "\""});

        assertThatThrownBy(() -> faxManager.createAndSaveFaxJob(loggedInProvider(), faxJobMap))
                .isInstanceOf(RuntimeException.class);

        // The manager's own REQUIRED transaction rolled back: neither the primary job nor the
        // valid copy-to recipient may survive as a WAITING row FaxSender would transmit.
        assertThat(faxJobDao.getReadyToSendFaxes(SENDER_FAX_LINE))
                .as("no WAITING FaxJob rows may survive the rolled-back batch")
                .isEmpty();
    }

    private static LoggedInInfo loggedInProvider() {
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        Security security = new Security();
        security.setProviderNo("999998");
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setLoggedInProvider(provider);
        loggedInInfo.setLoggedInSecurity(security);
        return loggedInInfo;
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    // best-effort test cleanup
                }
            });
        }
    }
}
