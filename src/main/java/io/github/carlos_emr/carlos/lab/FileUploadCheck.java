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

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.commn.dao.FileUploadCheckDao;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.ConversionUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
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
     * <p>{@link #addFile} answers {@link #UNSUCCESSFUL_SAVE} both for a checksum it already holds
     * and for any failure it catches, so a caller that must tell a real duplicate from a failed
     * check asks this afterwards.</p>
     *
     * @param is the file content; read to the end but not closed
     * @return {@code true} only when a checksum row exists for the content
     * @throws IOException if the content cannot be read; a database failure propagates too
     */
    public static boolean isFileRecorded(InputStream is) throws IOException {
        return hasFileBeenUploaded(contentKey(is));
    }

    /**
     * Records a file's checksum, without the duplicate check or failure swallowing of
     * {@link #addFile}.
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

    /** Opens the upload's content; called once for the duplicate check and once to record it. */
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
     * <p>{@link #addFile} commits the checksum before the caller parses and saves the file, so a
     * failure part-way either leaves a checksum that refuses every retry as a duplicate, or must be
     * undone by a separate cleanup that can itself fail. Here the checksum is recorded with
     * {@link #recordFile} in the same transaction as the store step's writes: both commit, or both
     * roll back, including when the step rejects the content or throws. A commit whose outcome is
     * unknown likewise left both or neither.</p>
     *
     * <p>The lookup and the transaction run while holding the content's lock stripe, which
     * {@link #addFile} also takes for the same content. No other upload of the same bytes in the
     * same application instance can therefore see this content's checksum before it commits, or
     * claim the content in between; uploads of other content are not held up. The lock does not
     * reach across servers. The transaction reads at
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
            boolean recorded;
            try (InputStream in = content.open()) {
                recorded = isFileRecorded(in);
            } catch (IOException | RuntimeException lookupFailure) {
                throw new LookupFailedException(lookupFailure);
            }
            if (recorded) {
                return StoreOutcome.ALREADY_RECORDED;
            }
            TransactionTemplate transaction = new TransactionTemplate(SpringUtils.getBean(PlatformTransactionManager.class));
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            try {
                return transaction.execute(status -> {
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

    // FindSecBugs WEAK_MESSAGE_DIGEST_MD5: MD5 is this class's duplicate-detection key (addFile
    // stores it), never a password, signature or integrity check; it must match what addFile wrote.
    @SuppressFBWarnings(value = "WEAK_MESSAGE_DIGEST_MD5",
            justification = "MD5 is the stored duplicate-detection key written by addFile, not a security control")
    @SuppressWarnings("java:S4790") // Sonar: same MD5 duplicate-detection key as addFile, not a security control.
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

    public static final int UNSUCCESSFUL_SAVE = -1;

    /**
     * Used to add a new file to the database, checks to see if it already has been added
     */
    public static int addFile(String name, InputStream is, String provider) {
        int fileUploaded = UNSUCCESSFUL_SAVE;
        try {
            String md5sum = DigestUtils.md5Hex(IOUtils.toByteArray(is));
            fileUploaded = recordIfNew(name, md5sum, provider);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        MiscUtils.getLogger().debug("returning " + fileUploaded);
        return fileUploaded;
    }

    /**
     * The locked part of {@link #addFile}: checks and records the checksum under the same stripe
     * {@link #storeIfNew} takes, so neither sees the other's in-flight claim on these bytes.
     *
     * @return the new row's id, or {@link #UNSUCCESSFUL_SAVE} when the checksum was already recorded
     */
    private static int recordIfNew(String name, String md5sum, String provider) {
        ReentrantLock lock = contentLock(md5sum);
        lock.lock();
        try {
            if (hasFileBeenUploaded(md5sum)) {
                return UNSUCCESSFUL_SAVE;
            }
            io.github.carlos_emr.carlos.commn.model.FileUploadCheck f = new io.github.carlos_emr.carlos.commn.model.FileUploadCheck();
            f.setProviderNo(provider);
            f.setFilename(name);
            f.setMd5sum(md5sum);
            f.setDateTime(new Date());

            FileUploadCheckDao dao = SpringUtils.getBean(FileUploadCheckDao.class);
            dao.persist(f);
            return f.getId();
        } finally {
            lock.unlock();
        }
    }

}
