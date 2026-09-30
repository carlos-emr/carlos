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

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveArtifactCensusLoader;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveKeyring;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveKeyringException;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveKeyringParser;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import jakarta.servlet.ServletContext;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.context.ServletContextAware;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.OptionalInt;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Owns the outbound email archive keyring file (#3448): loads it at startup, creates it on a fresh
 * install, refuses to start when creating one could orphan encrypted archives, and applies a
 * requested key rotation.
 *
 * <h2>Where the keyring lives</h2>
 * <ol>
 *   <li>The environment variable {@value #KEYRING_FILE_ENV_VAR}, when set (a mounted secret file).</li>
 *   <li>Otherwise the property {@value #KEYRING_FILE_PROPERTY}.</li>
 *   <li>Otherwise {@code <context>}{@value #DEFAULT_KEYRING_FILE_SUFFIX} in the home directory of the
 *       user CARLOS runs as, beside the {@code <context>.properties} file that holds
 *       {@code encryption.util.secret.key}. The webapp's context name keeps two CARLOS contexts in
 *       one Tomcat from sharing a keyring.</li>
 * </ol>
 * <p>The archive has its own key file, separate from {@code encryption.util.secret.key}.</p>
 *
 * <h2>Startup</h2>
 * <ul>
 *   <li><b>File present:</b> loaded and validated. Unreadable or malformed stops startup, always:
 *       a keyring file is never replaced, not even with the override. The newest encrypted archive's
 *       key id is then checked against it; a missing key is logged as an ERROR (an out-of-date
 *       keyring restored from backup), not refused.</li>
 *   <li><b>File missing, and no archived artifact is encrypted</b> (a fresh install, or an upgrade
 *       from plaintext-only archives): a keyring with key 1 is created, owner-only, never replacing a
 *       file another process created meanwhile.</li>
 *   <li><b>File missing, and encrypted artifacts exist or cannot be ruled out</b> (a file could not
 *       be checked, or the database could not be read): startup is refused with one ERROR naming the
 *       fix, restore the keyring from backup. {@value #ACKNOWLEDGE_LOSS_PROPERTY}{@code =true}
 *       accepts the loss for one start, in the style of {@code encryption.util.secret.key.acknowledge_loss}
 *       (#4098, pending). The new keyring's first key id must not reuse a lost one: it is one above
 *       the highest key id on disk when every archive could be read, and otherwise a random id
 *       between 2<sup>24</sup> and 2<sup>30</sup>.</li>
 * </ul>
 *
 * <h2>Rotation</h2>
 * <p>{@value #ROTATE_TO_PROPERTY}{@code =N} makes key N current at the next start, generating it
 * when the file does not already hold it, and keeps every older key. It is idempotent: once key N is
 * current the setting does nothing, so a later restart cannot rotate again. Before generating key N
 * every archived artifact's header is read once, and the rotation is refused if an archive already
 * uses an id of N or above that the keyring does not hold, since the keyring is then an out-of-date
 * copy and key N would reuse a lost id. When CARLOS cannot write the file (a read-only mount), an
 * operator adds {@code key.N} and sets {@code current=N} by hand instead; the format is documented in
 * {@link OutboundEmailArchiveKeyringParser}.</p>
 *
 * <p><strong>Nothing sensitive is logged:</strong> messages carry the file path, key ids, counts,
 * property names and exception class names. Never key material, file content or archive content.</p>
 *
 * @since 2026-09-30
 */
@Service
public class OutboundEmailArchiveKeyringService implements InitializingBean, ServletContextAware {

    /** Path of the keyring file; takes precedence over {@link #KEYRING_FILE_PROPERTY}. */
    public static final String KEYRING_FILE_ENV_VAR = "CARLOS_OUTBOUND_EMAIL_ARCHIVE_KEYRING_FILE";

    /** Path of the keyring file, in carlos.properties. */
    public static final String KEYRING_FILE_PROPERTY = "email.archive.keyring.file";

    /**
     * {@code true}, {@code yes} or {@code on}: create a new keyring although the old one is missing
     * and archived artifacts may be encrypted with it, accepting they become unreadable.
     */
    public static final String ACKNOWLEDGE_LOSS_PROPERTY = "email.archive.keyring.acknowledge_loss";

    /** A positive key id to make current at startup, generating that key when needed. */
    public static final String ROTATE_TO_PROPERTY = "email.archive.keyring.rotate_to";

    /** Appended to the webapp's context name for the default file in the CARLOS user's home directory. */
    public static final String DEFAULT_KEYRING_FILE_SUFFIX = "-outbound-email-archive.keyring";

    /** Context name used when Spring runs outside a servlet container. */
    static final String DEFAULT_CONTEXT_NAME = "carlos";

    /** Lowest random first key id after an acknowledged loss whose check was incomplete. */
    static final int RANDOM_KEY_ID_FLOOR = 1 << 24;

    /** Exclusive upper bound of that random range. */
    static final int RANDOM_KEY_ID_BOUND = 1 << 30;

    /** How many of the newest archive files the startup check reads for the newest key id. */
    static final int NEWEST_ARTIFACTS_CHECKED = 20;

    private static final Logger logger = MiscUtils.getLogger();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> GROUP_OR_OTHER = EnumSet.of(
            PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE);
    private static final String FILE_OVERRIDES = KEYRING_FILE_PROPERTY + " or " + KEYRING_FILE_ENV_VAR;

    private final OutboundEmailArchiveArtifactCensusLoader censusLoader;
    /** Built at {@link #afterPropertiesSet()}, after the servlet context has supplied the context name. */
    private Settings settings;
    private String contextName = DEFAULT_CONTEXT_NAME;
    private volatile OutboundEmailArchiveKeyring keyring;

    /**
     * Configuration read once at startup.
     *
     * @param keyringFile       configured or default keyring path, not yet validated
     * @param acknowledgeLoss   whether {@link #ACKNOWLEDGE_LOSS_PROPERTY} is set to an active value
     * @param rotateTo          raw {@link #ROTATE_TO_PROPERTY} value, or null
     * @param documentDirectory {@code DOCUMENT_DIR}, for the census; may be null
     */
    record Settings(String keyringFile, boolean acknowledgeLoss, String rotateTo, String documentDirectory) {

        static Settings fromConfiguration(CarlosProperties properties, String environmentKeyringFile,
                                          String contextName) {
            String keyringFile = blankToNull(environmentKeyringFile);
            if (keyringFile == null) {
                keyringFile = property(properties, KEYRING_FILE_PROPERTY);
            }
            if (keyringFile == null) {
                keyringFile = System.getProperty("user.home") + File.separator + contextName
                        + DEFAULT_KEYRING_FILE_SUFFIX;
            }
            // containsKey first: CarlosProperties.getProperty logs a warning for every absent key, and
            // these settings are absent on every healthy server.
            boolean acknowledgeLoss = properties.containsKey(ACKNOWLEDGE_LOSS_PROPERTY)
                    && properties.isPropertyActive(ACKNOWLEDGE_LOSS_PROPERTY);
            return new Settings(keyringFile, acknowledgeLoss, property(properties, ROTATE_TO_PROPERTY),
                    property(properties, "DOCUMENT_DIR"));
        }

        private static String property(CarlosProperties properties, String key) {
            return properties.containsKey(key) ? blankToNull(properties.getProperty(key)) : null;
        }

        private static String blankToNull(String value) {
            return value == null || value.isBlank() ? null : value.strip();
        }
    }

    /**
     * Spring constructor: settings come from carlos.properties and the environment at startup.
     *
     * @param dataSource the application data source, used read-only by the startup census
     */
    @Autowired
    public OutboundEmailArchiveKeyringService(@Qualifier("dataSource") DataSource dataSource) {
        this(null, new OutboundEmailArchiveArtifactCensusLoader(dataSource));
    }

    OutboundEmailArchiveKeyringService(Settings settings, OutboundEmailArchiveArtifactCensusLoader censusLoader) {
        this.settings = settings;
        this.censusLoader = censusLoader;
    }

    /** Takes the webapp's context name for the default keyring file name. */
    @Override
    public void setServletContext(ServletContext servletContext) {
        contextName = contextNameOf(servletContext.getContextPath());
    }

    /**
     * @param contextPath a servlet context path such as {@code /carlos}; empty for the root context
     * @return a file-name-safe context name: {@code carlos}, or {@code ROOT} for the root context
     */
    static String contextNameOf(String contextPath) {
        String name = contextPath == null ? "" : contextPath.replaceFirst("^/+", "");
        if (name.isEmpty()) {
            return "ROOT";
        }
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /**
     * Resolves the keyring when the Spring context starts.
     *
     * @throws OutboundEmailArchiveKeyringException when the keyring cannot be used safely; CARLOS
     *         then does not start
     */
    @Override
    public void afterPropertiesSet() {
        if (settings == null) {
            settings = Settings.fromConfiguration(CarlosProperties.getInstance(), System.getenv(KEYRING_FILE_ENV_VAR),
                    contextName);
        }
        keyring = resolveKeyring();
    }

    /**
     * @return the keyring loaded at startup
     * @throws IllegalStateException when startup did not load one
     */
    public OutboundEmailArchiveKeyring getKeyring() {
        OutboundEmailArchiveKeyring current = keyring;
        if (current == null) {
            throw new IllegalStateException("The outbound email archive keyring has not been loaded");
        }
        return current;
    }

    OutboundEmailArchiveKeyring resolveKeyring() {
        Path file = keyringPath();
        String path = describe(file);
        OutboundEmailArchiveKeyring resolved;
        boolean existed = Files.exists(file);
        if (existed) {
            resolved = load(file, path);
            if (settings.acknowledgeLoss()) {
                logger.warn("{} is set but has no effect while the outbound email archive keyring exists. Remove it,"
                                + " so that a future loss of the keyring stops startup instead of being accepted.",
                        ACKNOWLEDGE_LOSS_PROPERTY);
            }
        } else if (Files.notExists(file)) {
            resolved = createForMissingFile(file, path);
        } else {
            // Neither exists nor notExists: the parent directory cannot be searched.
            throw refuse("Outbound email archive keyring " + path + " could not be checked (its directory"
                    + " cannot be read). Refusing to start. Fix: make the directory readable by the user CARLOS runs"
                    + " as, then restart.");
        }
        resolved = rotateIfRequested(file, path, resolved);
        restrictToOwner(file, path);
        if (existed) {
            reportIfNewestKeyIsMissing(path, resolved);
        }
        int currentKeyId = resolved.currentKeyId();
        String keyIds = resolved.keyIds().toString();
        if (!resolved.isCurrentKeyNewest()) {
            int newestKeyId = resolved.keyIds().last();
            logger.warn("Outbound email archive keyring {}: current key {} is not the newest key ({}). New archived"
                    + " emails are encrypted with key {}.", path, currentKeyId, newestKeyId, currentKeyId);
        }
        logger.info("Loaded the outbound email archive keyring from {}: current key {}, keys {}.",
                path, currentKeyId, keyIds);
        return resolved;
    }

    private Path keyringPath() {
        try {
            return PathValidationUtils.resolveConfiguredFile(settings.keyringFile(), "outbound email archive keyring file")
                    .toPath();
        } catch (SecurityException e) {
            throw refuse("Outbound email archive keyring path " + LogSafe.sanitize(settings.keyringFile(), 1024)
                    + " is not a usable file path. Refusing to start. Fix: set " + FILE_OVERRIDES
                    + " to the keyring file's path, then restart.");
        }
    }

    // ------------------------------------------------------------------ load

    private OutboundEmailArchiveKeyring load(Path file, String path) {
        if (!Files.isRegularFile(file)) {
            throw refuse(unreadableMessage(path, "not a regular file"));
        }
        byte[] content = null;
        try {
            content = readBounded(file);
            return OutboundEmailArchiveKeyringParser.parse(content);
        } catch (IOException e) {
            throw refuse(unreadableMessage(path, e.getClass().getSimpleName()));
        } catch (IllegalArgumentException e) {
            // The parser's message names a line and a field, never a value.
            throw refuse("Outbound email archive keyring " + path + " is not a valid keyring ("
                    + e.getMessage() + "). Refusing to start. Fix: restore the keyring file from backup, then restart."
                    + " Do not replace it with a new keyring: archived emails encrypted with it would become"
                    + " unreadable.");
        } finally {
            if (content != null) {
                Arrays.fill(content, (byte) 0);
            }
        }
    }

    private static String unreadableMessage(String path, String cause) {
        return "Outbound email archive keyring " + path + " exists but could not be read (" + cause + ")."
                + " Refusing to start. Fix: make it a regular file readable by the user CARLOS runs as (owner-only,"
                + " mode 0600), or restore it from backup, then restart.";
    }

    private static byte[] readBounded(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            // One byte over the limit is enough for the parser to reject an oversized file.
            return input.readNBytes(OutboundEmailArchiveKeyringParser.MAX_FILE_BYTES + 1);
        }
    }

    /**
     * A loaded keyring that lacks the key of the newest encrypted archive is most likely an older
     * backup copy. Reported, not refused: every other archive may still read, and the rotation check
     * stops a new key from reusing the missing id.
     */
    private void reportIfNewestKeyIsMissing(String path, OutboundEmailArchiveKeyring loaded) {
        OptionalInt newest = censusLoader.newestEncryptedKeyId(settings.documentDirectory(), NEWEST_ARTIFACTS_CHECKED);
        if (newest.isPresent() && !loaded.keyIds().contains(newest.getAsInt())) {
            int keyId = newest.getAsInt();
            logger.error("Outbound email archive keyring {} does not hold key {}, which encrypted the most recent"
                            + " encrypted archived email. Archived emails encrypted with key {} cannot be read. This"
                            + " keyring may be an out-of-date copy: restore the newest keyring file from backup, then"
                            + " restart.", path, keyId, keyId);
        }
    }

    // ------------------------------------------------------------------ create

    private OutboundEmailArchiveKeyring createForMissingFile(Path file, String path) {
        OutboundEmailArchiveArtifactCensusLoader.Census census = censusLoader.load(settings.documentDirectory(),
                settings.acknowledgeLoss()
                        ? OutboundEmailArchiveArtifactCensusLoader.Scan.ALL
                        : OutboundEmailArchiveArtifactCensusLoader.Scan.UNTIL_FIRST_ENCRYPTED);
        boolean mayOrphan = !census.nothingToOrphan();
        if (mayOrphan && !settings.acknowledgeLoss()) {
            throw refuse(missingKeyringMessage(path, census));
        }

        int firstKeyId = firstKeyId(path, census);
        OutboundEmailArchiveKeyring created = OutboundEmailArchiveKeyring.generate(firstKeyId, RANDOM);
        try {
            writeKeyringFile(file, created, false);
        } catch (FileAlreadyExistsException e) {
            // Another process created the keyring after the existence check above. Publishing never
            // replaces a file, so that keyring is intact: use it rather than ours.
            return load(file, path);
        } catch (IOException e) {
            throw refuse("Could not create the outbound email archive keyring at " + path + " ("
                    + e.getClass().getSimpleName() + "). Refusing to start. Fix: make its directory writable by the"
                    + " user CARLOS runs as, or set " + FILE_OVERRIDES + " to a writable location that is backed up"
                    + " with the server configuration, then restart.");
        }

        if (mayOrphan) {
            // ERROR, not WARN: archived patient email is now unreadable and people must act on it.
            String what = describeCensus(census);
            logger.error("{} is set: created a new outbound email archive keyring at {} (key {}) although {}."
                            + " Archived emails encrypted with the lost keyring are now unreadable; reading one fails"
                            + " with an audited error. Back up the new keyring file now, then remove {} from the"
                            + " properties file.",
                    ACKNOWLEDGE_LOSS_PROPERTY, path, firstKeyId, what, ACKNOWLEDGE_LOSS_PROPERTY);
        } else {
            logger.warn("Created the outbound email archive keyring at {} (key {}, owner-only). Back this file up"
                            + " with the server configuration now: without it, archived patient emails cannot be read.",
                    path, firstKeyId);
            if (settings.acknowledgeLoss()) {
                logger.warn("{} is set but no archived email was encrypted with a lost keyring, so nothing was lost."
                                + " Remove it, so that a future loss of the keyring stops startup instead of being"
                                + " accepted.",
                        ACKNOWLEDGE_LOSS_PROPERTY);
            }
        }
        return created;
    }

    /**
     * The first key id of a new keyring. It must never be the id of a lost key, or a recovered old
     * keyring could not be merged back and a lost key's artifacts would fail as tampering rather than
     * as a missing key. When every archive was read, one above the highest id in use is safe. When
     * the check was incomplete the ids in use are unknown, so a guess low would likely collide: the
     * id is drawn at random from a range far above any id a keyring reaches by rotation.
     */
    private static int firstKeyId(String path, OutboundEmailArchiveArtifactCensusLoader.Census census) {
        if (census.complete() && census.highestKeyId() < OutboundEmailArchiveKeyring.MAX_KEY_ID) {
            return census.highestKeyId() + 1;
        }
        int keyId;
        do {
            keyId = RANDOM_KEY_ID_FLOOR + RANDOM.nextInt(RANDOM_KEY_ID_BOUND - RANDOM_KEY_ID_FLOOR);
        } while (census.keyIdsFound().contains(keyId));
        logger.warn("The check for archived emails encrypted with the lost outbound email archive keyring was"
                        + " incomplete, so the new keyring at {} starts at a random key id, {}, rather than one above the"
                        + " highest id found: the lost keyring's ids are not all known, and a random id in {}..{} is"
                        + " very unlikely to reuse one.",
                path, keyId, RANDOM_KEY_ID_FLOOR, RANDOM_KEY_ID_BOUND - 1);
        return keyId;
    }

    /** The one refusal an operator sees when the keyring is gone. Counts and names only. */
    private static String missingKeyringMessage(String path,
                                                OutboundEmailArchiveArtifactCensusLoader.Census census) {
        StringBuilder message = new StringBuilder("Outbound email archive keyring ").append(path);
        if (census.failure() != null) {
            message.append(" is missing, and CARLOS could not check the database for archived emails encrypted with"
                            + " it (could not read outboundEmailArchive: ").append(census.failure())
                    .append("). Refusing to start rather than risk making them unreadable.");
        } else if (census.encryptedFound()) {
            message.append(" is missing, but archived emails in the database are encrypted with it (")
                    .append(census.archiveRows()).append(" archived emails; encrypted artifacts found)."
                            + " Refusing to start: a new keyring cannot decrypt them.");
        } else {
            message.append(" is missing, and CARLOS could not confirm that no archived email is encrypted with it (")
                    .append(census.archiveRows()).append(" archived emails, ").append(census.uncheckable())
                    .append(" of them could not be checked because the stored file is missing or unreadable)."
                            + " Refusing to start rather than risk making them unreadable.");
        }
        message.append(" Fix: restore the keyring file from backup to ").append(path)
                .append(" (or point ").append(FILE_OVERRIDES).append(" at it), then restart.");
        if (census.failure() != null) {
            message.append(" If the database could not be reached, fix that and restart so the check can run.");
        } else if (!census.encryptedFound()) {
            message.append(" If the document store is not mounted or DOCUMENT_DIR is wrong, fix that and restart so"
                    + " the check can run.");
        }
        return message.append(" Only if the keyring is lost for good: set ").append(ACKNOWLEDGE_LOSS_PROPERTY)
                .append("=true and restart. CARLOS then creates a new keyring, and every archived email encrypted"
                        + " with the old one stays unreadable.")
                .toString();
    }

    private static String describeCensus(OutboundEmailArchiveArtifactCensusLoader.Census census) {
        if (census.failure() != null) {
            return "the database could not be checked for encrypted archived emails (" + census.failure() + ")";
        }
        return census.archiveRows() + " archived emails exist (encrypted artifacts found: "
                + (census.encryptedFound() ? "yes" : "none") + ", not checkable: " + census.uncheckable() + ")";
    }

    // ------------------------------------------------------------------ rotate

    private OutboundEmailArchiveKeyring rotateIfRequested(Path file, String path, OutboundEmailArchiveKeyring current) {
        String raw = settings.rotateTo();
        if (raw == null) {
            return current;
        }
        int target = parseRotateTo(raw);
        int currentKeyId = current.currentKeyId();
        if (target < currentKeyId) {
            logger.warn("{}={} is ignored: key {} is already current and keys are never rolled back. Remove the"
                    + " setting.", ROTATE_TO_PROPERTY, target, currentKeyId);
            return current;
        }
        if (target == currentKeyId) {
            logger.info("{}={}: key {} is already current. The setting can be removed.",
                    ROTATE_TO_PROPERTY, target, target);
            return current;
        }
        if (!current.keyIds().contains(target)) {
            refuseIfKeyIdIsInUse(path, current, target);
        }
        OutboundEmailArchiveKeyring rotated = current.withCurrentKey(target, RANDOM);
        try {
            writeKeyringFile(file, rotated, true);
        } catch (IOException e) {
            throw refuse("Could not rotate the outbound email archive keyring at " + path + " to key "
                    + target + " (" + e.getClass().getSimpleName() + "). Refusing to start rather than skip the"
                    + " requested rotation. Fix: make the keyring file and its directory writable by the user CARLOS"
                    + " runs as, or add the key by hand (see \"Rotating the archive key\" in"
                    + " docs/outbound-email-archive.md), or remove " + ROTATE_TO_PROPERTY + ", then restart.");
        }
        String keyIds = rotated.keyIds().toString();
        logger.warn("Rotated the outbound email archive keyring at {}: key {} now encrypts new archived emails, and"
                        + " keys {} are kept to read older ones. Back the keyring file up now.",
                path, target, keyIds);
        return rotated;
    }

    /**
     * Reads every archived artifact's key id once, before a rotation generates key {@code target}.
     * An archive already using an id of {@code target} or above that this keyring lacks means the
     * keyring is an out-of-date copy, and a new key under that id would be a second, different key
     * for the same id.
     */
    private void refuseIfKeyIdIsInUse(String path, OutboundEmailArchiveKeyring current, int target) {
        OutboundEmailArchiveArtifactCensusLoader.Census census = censusLoader.load(settings.documentDirectory(),
                OutboundEmailArchiveArtifactCensusLoader.Scan.ALL);
        String rotation = "Could not rotate the outbound email archive keyring at " + path + " to key " + target;
        if (census.failure() != null) {
            throw refuse(rotation + ": CARLOS could not read the database to check that no archived email already"
                    + " uses that key id (" + census.failure() + "). Refusing to start rather than skip the requested"
                    + " rotation. Fix: restore database access and restart, or remove " + ROTATE_TO_PROPERTY
                    + ", then restart.");
        }
        SortedSet<Integer> lostKeyIds = new TreeSet<>(census.keyIdsFound().tailSet(target));
        lostKeyIds.removeAll(current.keyIds());
        if (!lostKeyIds.isEmpty()) {
            throw refuse(rotation + ": archived emails are already encrypted with key ids " + lostKeyIds
                    + ", which this keyring does not hold. It looks like an out-of-date copy, and a new key " + target
                    + " could reuse an id already in use. Refusing to start. Fix: restore the newest keyring file from"
                    + " backup, then restart. Only if those keys are lost for good: set " + ROTATE_TO_PROPERTY
                    + " above " + lostKeyIds.last() + " and restart.");
        }
        if (census.uncheckable() > 0) {
            long uncheckable = census.uncheckable();
            logger.warn("Rotating the outbound email archive keyring at {} to key {}: {} archived emails could not be"
                    + " checked for key ids already in use, because the stored file is missing or unreadable.",
                    path, target, uncheckable);
        }
    }

    private static int parseRotateTo(String raw) {
        if (raw.matches("[1-9][0-9]{0,9}")) {
            long value = Long.parseLong(raw);
            if (value <= OutboundEmailArchiveKeyring.MAX_KEY_ID) {
                return (int) value;
            }
        }
        throw refuse(ROTATE_TO_PROPERTY + " must be a positive whole number, but is '" + LogSafe.sanitize(raw, 40)
                + "'. Refusing to start rather than skip the requested rotation. Fix: correct or remove it, then"
                + " restart.");
    }

    // ------------------------------------------------------------------ file writes

    /**
     * Writes the keyring through an owner-only temporary file in the same directory, synced before it
     * is published, so a crash leaves either the old file or the new one, never half of one. Reads the
     * result back and compares it with what was meant to be written.
     *
     * @param replace true for a rotation, which replaces the keyring atomically; false for a new
     *        keyring, which is never allowed to replace an existing file
     * @throws FileAlreadyExistsException when {@code replace} is false and the file already exists
     */
    private static void writeKeyringFile(Path file, OutboundEmailArchiveKeyring intended, boolean replace)
            throws IOException {
        Path directory = file.getParent();
        if (directory == null || !Files.isDirectory(directory)) {
            throw new NoSuchFileException(String.valueOf(directory));
        }
        byte[] content = OutboundEmailArchiveKeyringParser.format(intended);
        Path temporary = createOwnerOnlyTemporaryFile(directory);
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                writeAndSync(channel, content);
            }
            if (replace) {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } else {
                publishWithoutReplacing(temporary, file, content);
            }
            syncDirectory(directory);
            byte[] written = readBounded(file);
            try {
                if (!MessageDigest.isEqual(content, written)) {
                    throw new IOException("The keyring file did not read back as written");
                }
            } finally {
                Arrays.fill(written, (byte) 0);
            }
        } finally {
            Files.deleteIfExists(temporary);
            Arrays.fill(content, (byte) 0);
        }
    }

    /**
     * Publishes a new keyring under its final name without ever replacing a file that is already
     * there. A hard link to the finished temporary file is atomic and fails when the name is taken.
     * Where the file system has no hard links, the file is created with {@code CREATE_NEW}, which
     * cannot replace one either.
     */
    private static void publishWithoutReplacing(Path temporary, Path file, byte[] content) throws IOException {
        try {
            Files.createLink(file, temporary);
            return;
        } catch (FileAlreadyExistsException e) {
            throw e;
        } catch (UnsupportedOperationException | FileSystemException e) {
            // No hard links on this file system (some network and FAT mounts): create it directly.
            logger.debug("Archive keyring: hard link not available here, creating the file directly");
        }
        Set<OpenOption> options = Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try (FileChannel channel = FileChannel.open(file, options, ownerOnlyAttributes(file.getParent()))) {
            writeAndSync(channel, content);
        }
    }

    private static void writeAndSync(FileChannel channel, byte[] content) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(content);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
        channel.force(true);
    }

    private static Path createOwnerOnlyTemporaryFile(Path directory) throws IOException {
        return Files.createTempFile(directory, ".archive-keyring-", ".tmp", ownerOnlyAttributes(directory));
    }

    /** Owner-only where the platform has POSIX permissions; nothing extra elsewhere. */
    private static FileAttribute<?>[] ownerOnlyAttributes(Path directory) {
        if (directory.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            return new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(OWNER_ONLY)};
        }
        return new FileAttribute<?>[0];
    }

    /** Makes the rename or link durable. Best effort: not every platform can open a directory. */
    private static void syncDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | RuntimeException e) {
            // Nothing to report: the file itself was synced before it was published.
            logger.debug("Archive keyring directory sync is not supported on this platform");
        }
    }

    /** Owner-only where the platform has POSIX permissions; a warning when it cannot be enforced. */
    private static void restrictToOwner(Path file, String path) {
        PosixFileAttributeView view = Files.getFileAttributeView(file, PosixFileAttributeView.class);
        if (view == null) {
            return;
        }
        try {
            Set<PosixFilePermission> permissions = view.readAttributes().permissions();
            if (permissions.stream().noneMatch(GROUP_OR_OTHER::contains)) {
                return;
            }
            view.setPermissions(OWNER_ONLY);
            logger.info("Restricted the outbound email archive keyring {} to owner-only access (0600).", path);
        } catch (IOException | SecurityException e) {
            String failure = e.getClass().getSimpleName();
            logger.warn("Outbound email archive keyring {} can be read by users other than its owner, and CARLOS could"
                    + " not restrict it ({}). Fix: chmod 600 on the file.", path, failure);
        }
    }

    // ------------------------------------------------------------------ messages

    private static String describe(Path file) {
        return LogSafe.sanitize(file.toString(), 1024);
    }

    /** Logs the single operator-facing ERROR and returns the exception that stops startup. */
    private static OutboundEmailArchiveKeyringException refuse(String message) {
        logger.error(message);
        return new OutboundEmailArchiveKeyringException(message);
    }
}
