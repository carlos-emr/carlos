/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.commn.NativeSql;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Both Ontario generators allocate claims and HTML from the same HOME_DIR under BillingOutputLock. */
@Service
@Transactional(readOnly = true)
public class BillingOnOutputFilenames {
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager em;

    /** A recorded name stays occupied even when old files, a deleted batch or import reconciliation are involved. */
    @NativeSql({"billing_on_diskname", "billing_on_filename", "billactivity", "billing_filename_import"})
    public boolean isInUse(String generatedBasename) {
        String filename = PathValidationUtils.validatePathComponent(generatedBasename, "OHIP output");
        try {
            var path = PathValidationUtils.getRequiredHomeDirectory().toPath().resolve(filename);
            Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return true;
        } catch (NoSuchFileException absent) {
            // Files can be archived externally. Their database names must still be reserved.
        } catch (IOException unavailable) {
            throw BillingFileWriteException.forReason(BillingFileWriteException.Reason.OUTPUT_FOLDER_UNAVAILABLE, unavailable);
        }
        Number recorded = (Number) em.createNativeQuery("""
                SELECT CASE WHEN EXISTS (SELECT 1 FROM billing_on_diskname WHERE ohipfilename = ?1)
                              OR EXISTS (SELECT 1 FROM billing_on_filename WHERE htmlfilename = ?1)
                              OR EXISTS (SELECT 1 FROM billactivity WHERE ohipfilename = ?1 OR htmlfilename = ?1)
                              OR EXISTS (SELECT 1 FROM billing_filename_import WHERE original_filename = ?1 OR imported_filename = ?1)
                            THEN 1 ELSE 0 END
                """).setParameter(1, filename).getSingleResult();
        return recorded.intValue() != 0;
    }
}
