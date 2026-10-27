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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.commn.dao.CdsImportFileDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportFileReferenceDao;
import io.github.carlos_emr.carlos.commn.model.CdsImportFile;
import io.github.carlos_emr.carlos.commn.model.CdsImportFileReference;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportFileDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Database ownership for immutable file generations. This service never unlinks bytes.
 * Filesystem publication/retirement must verify the protected location and immutable digest.
 */
@Service
public class CdsImportFileService {
    private final CdsImportLedgerService ledger;
    private final CdsImportFileDao files;
    private final CdsImportFileReferenceDao references;
    private final TransactionTemplate transaction;

    public CdsImportFileService(CdsImportLedgerService ledger, CdsImportFileDao files, CdsImportFileReferenceDao references,
            PlatformTransactionManager manager) {
        this.ledger=ledger; this.files=files; this.references=references;
        transaction=CdsImportLedgerService.template(manager);
    }

    /** Call only after durable exclusive publication; the same path can never acquire another generation. */
    public CdsImportFileDto published(String runId, long epoch, String location, String digest, long byteLength) {
        return publish(runId, epoch, location, digest, byteLength, false, false);
    }

    public void publishedVerified(String runId, long epoch, String location, String digest, long byteLength) {
        publish(runId, epoch, location, digest, byteLength, true, false);
    }

    public boolean verifiedFor(String runId, long epoch, String location, String digest, long byteLength) {
        CdsImportLedgerService.requireNoTransaction(); CdsImportKeys.digestValue(digest, "file digest");
        return transaction.execute(status -> {
            ledger.lockOwned(runId, epoch); CdsImportFile file=files.findLocation(CdsImportKeys.hash(CdsImportKeys.required(location, "file location", 1024)));
            return file!=null && runId.equals(file.getRunId()) && file.getVerifiedEpoch()==epoch && "PUBLISHED".equals(file.getState()) && digest.equals(file.getDigest()) && byteLength==file.getByteLength();
        });
    }

    /** A physical verifier may share its completed bounded-stream proof with this run's source preparation. */
    public void verifiedIfOwned(String runId, long epoch, String fileId, String digest, long byteLength) {
        CdsImportLedgerService.requireNoTransaction();
        transaction.executeWithoutResult(status -> { ledger.lockOwned(runId, epoch); CdsImportFile file=files.lock(fileId);
            if (file==null || !"PUBLISHED".equals(file.getState()) || !digest.equals(file.getDigest()) || byteLength!=file.getByteLength()) throw new CdsImportException("DURABLE_FILE_CONFLICT");
            if (runId.equals(file.getRunId())) file.setVerifiedEpoch(epoch);
        });
    }

    CdsImportFileDto reconcilePublication(String runId, long epoch, String location, String digest, long byteLength) {
        return publish(runId, epoch, location, digest, byteLength, true, true);
    }

    private CdsImportFileDto publish(String runId, long epoch, String location, String digest, long byteLength, boolean verified, boolean operational) {
        CdsImportLedgerService.requireNoTransaction();
        CdsImportKeys.required(location, "durable file location", 1024); CdsImportKeys.digestValue(digest, "file digest");
        if (byteLength<0) throw new IllegalArgumentException("Invalid file length");
        return transaction.execute(status -> {
            if (operational) ledger.lockOperational(runId, epoch); else ledger.lockOwned(runId, epoch);
            CdsImportFile existing=files.findLocation(CdsImportKeys.hash(location));
            if (existing!=null) {
                existing=files.lock(existing.getId());
                if (!"PUBLISHED".equals(existing.getState()) || !existing.getDigest().equals(digest)
                        || existing.getByteLength()!=byteLength) throw new CdsImportException("DURABLE_FILE_CONFLICT");
                // A verified prior-run immutable publication may be adopted. Its owner's proof
                // cache is not repurposed for this attempt; native receipt/file reconciliation remains required.
                if (verified && runId.equals(existing.getRunId())) existing.setVerifiedEpoch(epoch);
                return dto(existing);
            }
            CdsImportFile file=new CdsImportFile(); file.setId(UUID.randomUUID().toString()); file.setRunId(runId);
            file.setLocationKey(CdsImportKeys.hash(location)); file.setLocation(location); file.setDigest(digest);
            file.setByteLength(byteLength); file.setState("PUBLISHED"); file.setGeneration(1); if (verified) file.setVerifiedEpoch(epoch); files.persist(file);
            return dto(file);
        });
    }

    /** Joins the native reference's transaction; retirement and adoption lock the same file row. */
    public void reference(String runId, long epoch, String unitKey, String fileId, String digest) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CdsImportException("FILE_REFERENCE_REQUIRES_UNIT");
        CdsImportUnitService.requireActive(runId, epoch, unitKey);
        ledger.lockOwned(runId, epoch); ledger.unit(runId, unitKey);
        CdsImportFile file=files.lock(fileId);
        if (file==null || !"PUBLISHED".equals(file.getState()) || !file.getDigest().equals(digest)) throw new CdsImportException("FILE_NOT_AVAILABLE");
        String id=CdsImportKeys.hash(runId, unitKey, fileId);
        if (references.get(id)==null) {
            CdsImportFileReference reference=new CdsImportFileReference(); reference.setId(id); reference.setRunId(runId);
            reference.setUnitKey(unitKey); reference.setFileId(fileId); references.persist(reference);
        }
    }

    /** Reuse a previously verified whole-spool publication without rehashing the full blob for every field page. */
    public void referenceLocation(String runId, long epoch, String unitKey, String location) {
        CdsImportUnitService.requireActive(runId, epoch, unitKey);
        ledger.lockOwned(runId, epoch);
        CdsImportFile file=files.findLocation(CdsImportKeys.hash(CdsImportKeys.required(location, "durable file location", 1024)));
        if (file==null || !"PUBLISHED".equals(file.getState())) throw new CdsImportException("FILE_NOT_AVAILABLE");
        reference(runId, epoch, unitKey, file.getId(), file.getDigest());
    }

    CdsImportFileDto findLocation(String location) {
        CdsImportLedgerService.requireNoTransaction();
        return transaction.execute(status -> { CdsImportFile file=files.findLocation(CdsImportKeys.hash(CdsImportKeys.required(location, "file location", 1024))); return file==null?null:dto(file); });
    }

    public CdsImportFileDto findPublishedLocation(String location) {
        CdsImportLedgerService.requireNoTransaction();
        return transaction.execute(status -> {
            CdsImportFile file=files.findLocation(CdsImportKeys.hash(CdsImportKeys.required(location, "durable file location", 1024)));
            if (file==null || !"PUBLISHED".equals(file.getState())) throw new CdsImportException("FILE_NOT_AVAILABLE");
            return dto(file);
        });
    }

    /** A returned retiring generation excludes every future reference before the caller may unlink it. */
    public CdsImportFileDto beginRetirement(String runId, long epoch, String fileId) {
        CdsImportLedgerService.requireNoTransaction();
        return transaction.execute(status -> {
            ledger.lockOperational(runId, epoch); CdsImportFile file=files.lock(fileId);
            if (file==null || !file.getRunId().equals(runId)) throw new CdsImportException("FILE_NOT_OWNED");
            if ("DELETED".equals(file.getState())) return dto(file);
            if (references.countForFile(fileId)!=0) throw new CdsImportException("REFERENCED_FILE_CANNOT_BE_RETIRED");
            if (!"PUBLISHED".equals(file.getState()) && !"RETIRING".equals(file.getState())) throw new CdsImportException("FILE_RETIREMENT_CONFLICT");
            file.setState("RETIRING"); return dto(file);
        });
    }

    /** Call after verified unlink of this exact immutable generation, never for a replacement path. */
    public void retired(String runId, long epoch, String fileId, long generation) {
        CdsImportLedgerService.requireNoTransaction();
        transaction.executeWithoutResult(status -> {
            ledger.lockOperational(runId, epoch); CdsImportFile file=files.lock(fileId);
            if (file!=null && file.getRunId().equals(runId) && file.getGeneration()==generation && "DELETED".equals(file.getState())) return;
            if (file==null || !file.getRunId().equals(runId) || file.getGeneration()!=generation
                    || !"RETIRING".equals(file.getState()) || references.countForFile(fileId)!=0) throw new CdsImportException("FILE_RETIREMENT_CONFLICT");
            file.setState("DELETED");
        });
    }

    public CdsImportFileDto file(String fileId) {
        CdsImportLedgerService.requireNoTransaction();
        return transaction.execute(status -> {
            CdsImportFile file=files.get(fileId);
            if (file==null) throw new CdsImportException("FILE_NOT_FOUND");
            return dto(file);
        });
    }

    /** Includes immutable receipts inherited by verified no-op units from their original run. */
    public java.util.List<CdsImportFileDto> referencedFiles(String runId, String afterFileId, int limit) {
        CdsImportLedgerService.requireNoTransaction(); CdsImportKeys.pageSize(limit);
        return transaction.execute(status -> files.referenced(runId, afterFileId==null?"":afterFileId, limit).stream().map(CdsImportFileService::dto).toList());
    }

    public java.util.List<io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportFileOwnershipDto> ownedFiles(String runId, String afterId, int limit) {
        CdsImportLedgerService.requireNoTransaction(); CdsImportKeys.pageSize(limit);
        return transaction.execute(status -> files.owned(runId, afterId==null?"":afterId, limit).stream().map(row -> new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportFileOwnershipDto(dto((CdsImportFile) row[0]), ((Number) row[1]).longValue())).toList());
    }

    private static CdsImportFileDto dto(CdsImportFile file) {
        return new CdsImportFileDto(file.getId(), file.getLocation(), file.getDigest(), file.getByteLength(), file.getState(), file.getGeneration());
    }
}
