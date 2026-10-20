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

package io.github.carlos_emr.carlos.lab;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.codec.digest.DigestUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.commn.dao.FileUploadCheckDao;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.ConversionUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @author Jay Gallagher
 */
public final class FileUploadCheck {

    private FileUploadCheck() {
        // no instantiation allowed
    }

    // Serializes the duplicate check and the claim per content, not per JVM: storeIfNew holds its
    // stripe across the whole parse/save/commit, so a single class-wide monitor would make one slow
    // upload stall every other lab feed. Uploads of the same bytes always share a stripe; different
    // bytes collide only when their keys hash to the same one of the fixed, bounded set.
    private static final ReentrantLock[] CONTENT_LOCKS = new ReentrantLock[64];

    static {
        for (int i = 0; i < CONTENT_LOCKS.length; i++) {
            CONTENT_LOCKS[i] = new ReentrantLock();
        }
    }

    static ReentrantLock contentLock(String contentKey) {
        return CONTENT_LOCKS[Math.floorMod(contentKey.hashCode(), CONTENT_LOCKS.length)];
    }

    private static boolean hasFileBeenUploaded(String md5sum) {
        FileUploadCheckDao dao = SpringUtils.getBean(FileUploadCheckDao.class);
        List<io.github.carlos_emr.carlos.commn.model.FileUploadCheck> checks = dao.findByMd5Sum(md5sum);
        return !checks.isEmpty();
    }

    /**
     * Reports whether a file's content is already recorded, without swallowing failures.
     *
     * <p>A failed lookup throws rather than answering {@code false} or {@code true}: a database
     * fault must never read as "already uploaded" (which a sender treats as delivered) nor as
     * "new" (which would store a second copy).</p>
     *
     * @param is the file content; read to the end but not closed
     * @return {@code true} only when a checksum row exists for the content
     * @throws IOException if the content cannot be read; a database failure propagates too
     */
    public static boolean isFileRecorded(InputStream is) throws IOException {
        return hasFileBeenUploaded(contentKey(is));
    }

    /**
     * Records a file's checksum without a duplicate check, propagating every failure.
     *
     * <p>For a caller that has already confirmed the content is new and records it inside its own
     * transaction, so the checksum commits or rolls back together with what the file produced.
     * {@link #storeIfNew} is that caller.</p>
     *
     * @param name the file name to record
     * @param is the file content; read to the end but not closed
     * @param provider the uploading provider number
     * @return the new checksum row's id
     * @throws IOException if the content cannot be read; a database failure propagates too
     */
    public static int recordFile(String name, InputStream is, String provider) throws IOException {
        io.github.carlos_emr.carlos.commn.model.FileUploadCheck f = new io.github.carlos_emr.carlos.commn.model.FileUploadCheck();
        f.setProviderNo(provider);
        f.setFilename(name);
        f.setMd5sum(contentKey(is));
        f.setDateTime(new Date());
        SpringUtils.getBean(FileUploadCheckDao.class).persist(f);
        return f.getId();
    }

    /** What {@link #storeIfNew} did with an upload. */
    public enum StoreOutcome {
        /** The content was new; its checksum and everything the store step wrote committed together. */
        STORED,
        /** A checksum for the content was already recorded; nothing was stored. */
        ALREADY_RECORDED,
        /** The store step rejected the content; its writes and the checksum were rolled back. */
        REJECTED
    }

    /** Opens the same immutable upload content for locking, duplicate lookup, and recording. */
    @FunctionalInterface
    public interface ContentSource {
        InputStream open() throws IOException;
    }

    /** Stores an upload inside {@link #storeIfNew}'s transaction. */
    @FunctionalInterface
    public interface ContentStore {
        /**
         * @param checksumId the id of the checksum row recorded for this content, uncommitted
         * @return {@code false} to reject the content, rolling back the checksum and every write
         * @throws Exception to fail the upload; the transaction rolls back and the exception propagates
         */
        boolean store(int checksumId) throws Exception;
    }

    /** The duplicate lookup itself failed, so nothing is known about the content and a retry is safe. */
    public static final class LookupFailedException extends RuntimeException {
        LookupFailedException(Throwable cause) {
            super("The upload's checksum could not be checked", cause);
        }
    }

    // Carries a checked exception from the store step out of TransactionTemplate's callback.
    private static final class StoreFailure extends RuntimeException {
        StoreFailure(Exception cause) {
            super(cause);
        }
    }

    /**
     * Stores an upload once: its checksum exists exactly when what it produced does.
     *
     * <p>Committing the checksum before the caller parses and saves the file (as the retired
     * {@code addFile} did) meant a failure part-way left a checksum that refused every retry as a
     * duplicate, unless a separate cleanup, which could itself fail, removed it. Here the checksum is
     * recorded with {@link #recordFile} in the same transaction as the store step's writes: both
     * commit, or both roll back, including when the step rejects the content or throws. A commit
     * whose outcome is unknown likewise left both or neither.</p>
     *
     * <p>The lookup and the transaction run while holding the content's lock stripe. No other
     * upload of the same bytes in the same application instance can therefore see this content's
     * checksum before it commits, or claim the content in between; uploads on other stripes are
     * not held up. The lock does not
     * reach across servers. This method owns an independent transaction, suspending any caller
     * transaction until the upload has committed or rolled back. The duplicate lookup also runs
     * in that transaction, avoiding a stale snapshot from the caller. The transaction reads at
     * READ_COMMITTED, as {@code ProviderLabRouting.routeMagic} requires of the transaction it joins
     * under MariaDB's snapshot isolation.</p>
     *
     * @param name the file name to record with the checksum
     * @param content opens the upload's content
     * @param provider the uploading provider number
     * @param store writes what the upload produces, through DAOs that join the transaction
     * @return what happened to the upload
     * @throws LookupFailedException if the duplicate lookup fails; nothing was recorded or stored
     * @throws Exception whatever the store step or the transaction threw; nothing was left recorded,
     *         except that if the commit itself failed, its outcome is unknown: the checksum and the
     *         store step's writes committed together or not at all
     */
    public static StoreOutcome storeIfNew(String name, ContentSource content, String provider, ContentStore store)
            throws Exception {
        ReentrantLock lock;
        try (InputStream in = content.open()) {
            lock = contentLock(contentKey(in));
        } catch (IOException | RuntimeException lookupFailure) {
            throw new LookupFailedException(lookupFailure);
        }
        lock.lock();
        try {
            TransactionTemplate transaction = new TransactionTemplate(SpringUtils.getBean(PlatformTransactionManager.class));
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            // Own the commit boundary even when invoked by a transactional service. Otherwise
            // REQUIRED releases the content lock before the caller commits its checksum.
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            try {
                return transaction.execute(status -> {
                    boolean recorded;
                    try (InputStream in = content.open()) {
                        recorded = isFileRecorded(in);
                    } catch (IOException | RuntimeException lookupFailure) {
                        throw new LookupFailedException(lookupFailure);
                    }
                    if (recorded) {
                        return StoreOutcome.ALREADY_RECORDED;
                    }
                    try {
                        int checksumId;
                        try (InputStream in = content.open()) {
                            checksumId = recordFile(name, in, provider);
                        }
                        if (store.store(checksumId)) {
                            return StoreOutcome.STORED;
                        }
                        status.setRollbackOnly();
                        return StoreOutcome.REJECTED;
                    } catch (RuntimeException unchecked) {
                        throw unchecked;
                    } catch (Exception checked) {
                        throw new StoreFailure(checked);
                    }
                });
            } catch (StoreFailure failure) {
                throw (Exception) failure.getCause();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@link #storeIfNew} for an upload the caller has already written to disk, removing that file
     * whenever nothing can reference it.
     *
     * <p>Entry points save the upload (usually under {@code DOCUMENT_DIR}) before storing it, so
     * the handler can parse it from disk and a stored lab keeps it as its archive. Without this, a
     * duplicate, a failed duplicate lookup, a rejected parse or a rolled-back store each left an
     * orphan copy behind, and every sender retry wrote another. The file is removed when:</p>
     * <ul>
     *   <li>the content was {@linkplain StoreOutcome#ALREADY_RECORDED already recorded};</li>
     *   <li>the store step never ran, because the duplicate lookup failed or the transaction could
     *       not start; or</li>
     *   <li>the store step ran and its transaction is confirmed rolled back
     *       ({@linkplain StoreOutcome#REJECTED rejected}, or it threw).</li>
     * </ul>
     * <p>It is kept after a commit, and after a commit whose outcome is unknown, because the stored
     * rows may then reference it. The caller must have created {@code saved} exclusively for this
     * upload (the lab savers use {@code CREATE_NEW}), so removing it can never discard another
     * upload's file. A delete that fails is logged and retried when the JVM shuts down.</p>
     *
     * @param saved the file this upload wrote; also the content that is checked and recorded
     * @param savedDir the directory {@code saved} must lie in before it is ever deleted
     * @param name the file name to record with the checksum
     * @param provider the uploading provider number
     * @param store writes what the upload produces, through DAOs that join the transaction
     * @return what happened to the upload, as {@link #storeIfNew} reports it
     * @throws LookupFailedException if the duplicate lookup fails; the saved file was removed
     * @throws Exception whatever {@link #storeIfNew} threw
     */
    public static StoreOutcome storeSavedFileIfNew(File saved, File savedDir, String name, String provider,
            ContentStore store) throws Exception {
        AtomicBoolean storeRan = new AtomicBoolean();
        try {
            return storeIfNew(name, () -> Files.newInputStream(saved.toPath()), provider, checksumId -> {
                storeRan.set(true);
                // Registered before the store step runs, so a step that throws is covered too.
                discardOnRollback(saved, savedDir);
                return store.store(checksumId);
            });
        } finally {
            // A duplicate, a failed lookup or a transaction that never started: the store step did
            // not run, so nothing was recorded or stored and nothing references the file. Once it
            // ran, the rollback synchronization above decides.
            if (!storeRan.get()) {
                discardUnreferenced(saved, savedDir);
            }
        }
    }

    /**
     * Removes {@code saved} if the surrounding transaction rolls back.
     *
     * <p>A commit, a commit whose outcome is unknown (the rows may reference the file) or no active
     * transaction synchronization keeps the file.</p>
     *
     * @param saved a file written exclusively for the current upload
     * @param savedDir the directory {@code saved} must lie in before it is deleted
     */
    public static void discardOnRollback(File saved, File savedDir) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    discardUnreferenced(saved, savedDir);
                }
            }
        });
    }

    /**
     * Deletes a saved upload that no stored row references.
     *
     * @param saved a file written exclusively for the current upload, or {@code null}
     * @param savedDir the directory {@code saved} must lie in before it is deleted
     * @return {@code true} when no such file remains
     */
    public static boolean discardUnreferenced(File saved, File savedDir) {
        if (saved == null) {
            return true;
        }
        File target;
        try {
            target = PathValidationUtils.validateExistingPath(saved, savedDir);
        } catch (RuntimeException outsideSavedDir) {
            MiscUtils.getLogger().warn("Not removing an unstored lab upload outside its upload directory: {}",
                    LogSafe.exceptionTrace(outsideSavedDir));
            return false;
        }
        if (target == null) {
            return false;
        }
        try {
            Files.deleteIfExists(target.toPath());
            return true;
        } catch (IOException | RuntimeException e) {
            // No row references this file. Retry the delete at shutdown rather than forget it; there
            // is no persistent cleanup queue for uploads. exceptionTrace, not the throwable: the
            // message is the path, whose generated name embeds the sender's lab filename.
            MiscUtils.getLogger().warn("Could not remove an unstored lab upload; retrying at shutdown: {}",
                    LogSafe.exceptionTrace(e));
            target.deleteOnExit();
            return false;
        }
    }

    // FindSecBugs WEAK_MESSAGE_DIGEST_MD5: MD5 is this class's duplicate-detection key (recordFile
    // stores it), never a password, signature or integrity check; it must match the existing rows.
    @SuppressFBWarnings(value = "WEAK_MESSAGE_DIGEST_MD5",
            justification = "MD5 is the stored duplicate-detection key written by recordFile, not a security control")
    @SuppressWarnings("java:S4790") // Sonar: same MD5 duplicate-detection key as recordFile, not a security control.
    private static String contentKey(InputStream is) throws IOException {
        return DigestUtils.md5Hex(is);
    }

    public static Map<String, String> getFileInfo(Integer id) {
        Map<String, String> fileInfo = new HashMap<String, String>();
        FileUploadCheckDao dao = SpringUtils.getBean(FileUploadCheckDao.class);
        io.github.carlos_emr.carlos.commn.model.FileUploadCheck c = dao.find(id);
        if (c != null) {
            toMap(fileInfo, c);
        }
        return fileInfo;
    }

    private static void toMap(Map<String, String> fileInfo, io.github.carlos_emr.carlos.commn.model.FileUploadCheck c) {
        fileInfo.put("providerNo", c.getProviderNo());
        fileInfo.put("filename", c.getFilename());
        fileInfo.put("md5sum", c.getMd5sum());
        fileInfo.put("dateTime", ConversionUtils.toTimestampString(c.getDateTime()));
    }

    public static Hashtable<String, String> getFileInfo(String md5sum) {
        Hashtable<String, String> fileInfo = new Hashtable<String, String>();
        FileUploadCheckDao dao = SpringUtils.getBean(FileUploadCheckDao.class);
        List<io.github.carlos_emr.carlos.commn.model.FileUploadCheck> checks = dao.findByMd5Sum(md5sum);

        if (!checks.isEmpty()) {
            io.github.carlos_emr.carlos.commn.model.FileUploadCheck c = checks.get(0);
            toMap(fileInfo, c);
        }

        return fileInfo;
    }

}
