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
package io.github.carlos_emr.carlos.lab.ca.all.upload.handlers;

import io.github.carlos_emr.carlos.commn.dao.utils.AuthUtils;
import io.github.carlos_emr.carlos.lab.FileUploadCheck;
import io.github.carlos_emr.carlos.lab.ca.all.upload.MessageUploader;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.test.logging.HibernateSessionAssertions;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.CarlosProperties;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A lab whose rows the database rejects must be reported as the database rejection (#4436).
 *
 * <p>Drives the real {@link HL7Handler}, {@link MessageUploader} and
 * {@link FileUploadCheck#storeSavedFileIfNew} against H2, the path every upload entry point takes.
 * The patient's last name is longer than the {@code hl7TextInfo.last_name} column, so the parse
 * succeeds, {@code hl7TextMessage} is stored and the {@code hl7TextInfo} insert is rejected, the
 * same sequence as the {@code lab-upload-rollback} trigger probe on a packaged install.</p>
 *
 * @since 2026-10-08
 */
@Tag("integration")
@Tag("lab")
@Tag("upload")
@Isolated("MessageUploader caches Spring beans in static fields; DOCUMENT_DIR is process-global")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HL7HandlerStorageFailureIntegrationTest extends CarlosTestBase {
    @TempDir
    Path documentDir;
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager em;
    @Autowired
    private PlatformTransactionManager transactions;
    private final Map<Field, Object> originalBeans = new LinkedHashMap<>();
    private String originalDocumentDir;

    @BeforeEach
    void setUp() throws Exception {
        // A unit test may have initialized this legacy class first with mocked Spring beans.
        for (Field field : MessageUploader.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())
                    && (field.getName().endsWith("Dao") || field.getName().endsWith("Manager"))) {
                field.setAccessible(true);
                originalBeans.put(field, field.get(null));
                field.set(null, applicationContext.getBean(field.getType()));
            }
        }
        originalDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        for (var entry : originalBeans.entrySet()) entry.getKey().set(null, entry.getValue());
        if (originalDocumentDir == null) CarlosProperties.getInstance().remove("DOCUMENT_DIR");
        else CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", originalDocumentDir);
    }

    /** A minimal ORU^R01 whose patient last name (PID-5-1) cannot fit {@code hl7TextInfo.last_name}. */
    private static String labWithOversizedLastName() {
        return String.join("\r",
                "MSH|^~\\&|Reports|SYNTHETIC|||20261008120000||ORU^R01||1|2.3",
                "PID|1|||^^ON|" + "X".repeat(400) + "^Probe||19800102|F",
                "ORC|NW|ACC4436|||F|||||||999998^DR. PROBE|||20261008",
                "OBR|1|ACC4436||ML70^SYNTHETIC PANEL||20261008|20261008|||||||||999998^DR. PROBE|||||||||F",
                "OBX|1|ST|7010^SYNTHETIC RESULT|^^CHEMISTRY|NORMAL|||N|||F||765^1007010||70",
                "");
    }

    private long count(String entity) {
        return new TransactionTemplate(transactions).execute(status ->
                em.createQuery("select count(x) from " + entity + " x", Long.class).getSingleResult());
    }

    @Test
    void shouldLogRejectedInsertAsError_whenDatabaseRejectsLabRows() throws Exception {
        File saved = Files.writeString(documentDir.resolve("LabUpload.hl7-probe-4436"),
                labWithOversizedLastName(), StandardCharsets.UTF_8).toFile();
        long messagesBefore = count("Hl7TextMessage");
        long checksumsBefore = count("FileUploadCheck");

        FileUploadCheck.StoreOutcome outcome;
        try (LogCapture handlerLog = LogCapture.forLogger(HL7Handler.class);
             LogCapture hibernateCore = HibernateSessionAssertions.capture()) {
            outcome = FileUploadCheck.storeSavedFileIfNew(saved, documentDir.toFile(), "hl7-probe-4436", "999998",
                    checksumId -> new HL7Handler().parse(AuthUtils.initLoginContext(), "synthetic",
                            saved.getPath(), checksumId, "127.0.0.1") != null);

            assertThat(HibernateSessionAssertions.in(hibernateCore))
                    .as("no Hibernate HHH000099 assertion may replace the real failure").isEmpty();
            assertThat(handlerLog.events())
                    .filteredOn(event -> event.getLevel() == Level.ERROR)
                    .as("the handler logs the storage failure at ERROR").hasSize(1)
                    .allSatisfy(event -> assertThat(causeChain(event))
                            .as("the logged exception is the database rejection, not a session assertion")
                            .anySatisfy(cause -> assertThat(cause.getClass().getName())
                                    .isEqualTo("org.hibernate.exception.DataException"))
                            .noneSatisfy(cause -> assertThat(cause).isInstanceOf(org.hibernate.AssertionFailure.class)));
        }

        assertThat(outcome).as("the rejected upload is reported, not thrown").isEqualTo(FileUploadCheck.StoreOutcome.REJECTED);
        assertThat(count("Hl7TextMessage")).as("the raw message rolled back").isEqualTo(messagesBefore);
        assertThat(count("FileUploadCheck")).as("the checksum rolled back, so the sender can retry").isEqualTo(checksumsBefore);
        assertThat(saved).as("an unstored upload's saved copy is removed").doesNotExist();
    }

    private static java.util.List<Throwable> causeChain(LogEvent event) {
        java.util.List<Throwable> chain = new java.util.ArrayList<>();
        for (Throwable cause = event.getThrown(); cause != null && chain.size() < 16; cause = cause.getCause()) {
            chain.add(cause);
        }
        return chain;
    }
}
