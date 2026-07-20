/**
 * Copyright (c) 2024-2026. CARLOS EMR Project. All Rights Reserved.
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
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.eform;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.context.ServletContextAware;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

/**
 * Deploys bundled Rich Text Letter (RTL) eForm assets from the WAR to the
 * eForm images directory on application startup.
 *
 * <h3>Architecture Context</h3>
 * <p>The RTL eForm stores its HTML in the database ({@code eform.form_html} column),
 * but references external JavaScript and template files via {@code displayImage}
 * URLs. These files must exist on disk in the eForm images directory for the editor
 * to function. This deployer bridges the gap between the WAR-bundled source files
 * and the runtime filesystem location.</p>
 *
 * <h3>Deployed Assets</h3>
 * <ul>
 *   <li>{@code editControl2.js} — WYSIWYG editor engine (toolbar, iframe, formatting)</li>
 *   <li>{@code blank.rtl} — Default blank letter template</li>
 *   <li>{@code editor_help.html} — Help popup for the editor toolbar</li>
 * </ul>
 *
 * <h3>Directory Bootstrap</h3>
 * <p>If the configured directory does not yet exist, this deployer creates it with
 * owner-only permissions (chmod 700 semantics) to satisfy HIPAA/PIPEDA requirements —
 * provider signatures and medical templates must not be world-readable. A warning is
 * logged when the OS cannot honour the permission restriction so operators are alerted.</p>
 *
 * <h3>Skip-if-Exists Behavior</h3>
 * <p>Files are only copied if they do not already exist in the target directory.
 * This prevents overwriting clinic-customized versions. To force an update,
 * an administrator must manually delete the file from the eForm images directory
 * and restart Tomcat.</p>
 *
 * <h3>Intentional Exclusion</h3>
 * <p>The {@code stamps.js} file is intentionally NOT auto-deployed because it
 * contains clinic-specific doctor signature image mappings that administrators
 * create themselves through the eForm admin UI.</p>
 *
 * <h3>JSoup Interaction Warning</h3>
 * <p>If the eForm images directory does not exist or the assets are not deployed,
 * JSoup's {@code ConvertToEdoc.validateResourcePaths()} will silently remove
 * the {@code <script>} tags from the eForm HTML during rendering, causing the
 * editor to fail without any visible error. Ensure this deployer runs successfully
 * before any RTL eForm is loaded.</p>
 *
 * @see io.github.carlos_emr.carlos.documentManager.ConvertToEdoc#validateResourcePaths
 * @see io.github.carlos_emr.CarlosProperties#getEformImageDirectory()
 * @since 2026-03-22
 */
public class EFormAssetDeployer implements InitializingBean, ServletContextAware {

    private static final Logger logger = LogManager.getLogger(EFormAssetDeployer.class);

    /** Path inside the WAR where bundled assets are stored (not web-accessible). */
    private static final String BUNDLED_ASSETS_PATH = "/WEB-INF/eform-assets/";
    private static final String SHARED_JAVASCRIPT_PATH = "/share/javascript/";
    private static final String JQUERY_RESOURCE_PATH = "/library/jquery/jquery-3.7.1.min.js";
    private static final byte[] BLANK_SIGNATURE_PNG = new byte[] {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x04, 0x00, 0x00, 0x00, (byte) 0xB5, 0x1C, 0x0C,
        0x02, 0x00, 0x00, 0x00, 0x0B, 0x49, 0x44, 0x41,
        0x54, 0x78, (byte) 0xDA, 0x63, (byte) 0xFC, (byte) 0xFF, 0x1F, 0x00,
        0x03, 0x03, 0x02, 0x00, (byte) 0xEF, (byte) 0xA2, (byte) 0xA7, (byte) 0x5B,
        0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
        (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    /**
     * Assets to deploy. These filenames must match exactly what the RTL eForm's
     * form_html references via {@code displayImage?imagefile=<filename>}.
     */
    private static final String[] ASSETS = {
        "editControl2.js",
        "blank.rtl",
        "editor_help.html"
    };

    private static final String[] LEGACY_SIGNATURE_ASSETS = {
        "signature_pad.min.js"
    };
    private static final String[] SAMPLE_LAB_BACKGROUND_ASSETS = {
        "SOPLR_BC_2018_Sans2.png",
        "BCCW_Lab_pg2.png",
        "FHA_Lab_Mar2018_Pg2.png",
        "VCH_PHC_Labs_2018.png",
        "LifeLabsPg2_2019.png",
        "CreativeCommonsIcon.png"
    };
    private static final Map<String, String> SAMPLE_LAB_COMPATIBILITY_SCRIPTS = buildSampleLabCompatibilityScripts();

    /** Injected by Spring via {@link ServletContextAware} before {@link #afterPropertiesSet()}. */
    private jakarta.servlet.ServletContext servletContext;

    @Override
    public void setServletContext(jakarta.servlet.ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    /**
     * Called by Spring after all properties are set. Deploys each bundled asset
     * to the eForm images directory if it doesn't already exist there.
     *
     * <p>If the directory does not yet exist it is created with owner-only permissions
     * (HIPAA/PIPEDA). Exits early (with a warning log) if the path is not configured or
     * the directory cannot be created.</p>
     */
    @Override
    public void afterPropertiesSet() {
        String imageDir = CarlosProperties.getInstance().getEformImageDirectory();
        if (imageDir == null || imageDir.isBlank()) {
            logger.warn("eForm image directory not configured; skipping asset deployment");
            return;
        }

        File targetDir;
        try {
            targetDir = PathValidationUtils.resolveConfiguredDirectory(imageDir, "EFORM_IMAGES_DIR");
        } catch (SecurityException e) {
            logger.warn("eForm image directory is invalid: {}; skipping asset deployment", imageDir, e);
            return;
        }
        if (!createDirectory(targetDir, imageDir)) {
            return;
        }

        for (String asset : ASSETS) {
            deployAsset(asset, targetDir);
        }
        for (String asset : LEGACY_SIGNATURE_ASSETS) {
            deploySharedJavascriptAsset(asset, targetDir);
        }
        deployGeneratedAsset("BNK.png", targetDir, BLANK_SIGNATURE_PNG);
        deploySampleLabCompatibilityAssets(targetDir);

        // Per-asset outcomes are logged above (deployed / already-exists / failed); this single line
        // makes the "deployer ran to completion" milestone visible without DEBUG.
        logger.info("eForm asset deployment complete for directory: {}", targetDir.getAbsolutePath());
    }

    /**
     * Creates the eForm image directory if it does not yet exist, then restricts its permissions
     * to owner-only to prevent unauthorized local users from reading or modifying provider
     * signatures and medical templates (HIPAA/PIPEDA).
     *
     * <p>Uses the concurrent-safe {@code !mkdirs() && !isDirectory()} idiom so a directory
     * created by a parallel thread does not trigger a spurious failure. Permission calls check
     * their return values and log a warning when the OS cannot honour the restriction — this
     * keeps startup non-fatal while alerting operators that default umask permissions remain.</p>
     *
     * @param targetDir the resolved eForm image directory
     * @param imageDir  the configured path string (for log messages)
     * @return {@code true} if the directory is ready for asset deployment
     */
    private boolean createDirectory(File targetDir, String imageDir) {
        Path targetPath = targetDir.toPath();
        List<Path> createdDirectories = collectMissingDirectories(targetPath);
        try {
            createDirectoriesWithOwnerOnlyPermissions(targetPath);
        } catch (IOException e) {
            logger.warn("eForm image directory does not exist and could not be created: {}; skipping asset deployment", imageDir, e);
            return false;
        }

        if (createdDirectories.isEmpty()) {
            applyOwnerOnlyPermissions(targetPath, imageDir);
            return true;
        }

        boolean targetVerified = true;
        for (Path createdDirectory : createdDirectories) {
            String directoryLabel = createdDirectory.equals(targetPath) ? imageDir : createdDirectory.toString();
            boolean verified = applyOwnerOnlyPermissions(createdDirectory, directoryLabel);
            if (createdDirectory.equals(targetPath)) {
                targetVerified = verified;
            }
        }
        if (targetVerified) {
            logger.info("Created eForm image directory with verified owner-only permissions: {}", imageDir);
        }
        return true;
    }

    List<Path> collectMissingDirectories(Path targetPath) {
        List<Path> missingDirectories = new ArrayList<>();
        Path current = targetPath;
        while (current != null && !Files.exists(current)) {
            missingDirectories.add(0, current);
            current = current.getParent();
        }
        return missingDirectories;
    }

    void createDirectoriesWithOwnerOnlyPermissions(Path targetPath) throws IOException {
        try {
            FileAttribute<Set<PosixFilePermission>> ownerOnlyAttributes =
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
            Files.createDirectories(targetPath, ownerOnlyAttributes);
        } catch (UnsupportedOperationException e) {
            Files.createDirectories(targetPath);
        }
    }

    boolean applyOwnerOnlyPermissions(Path targetPath, String imageDir) {
        Set<PosixFilePermission> ownerOnlyPermissions = PosixFilePermissions.fromString("rwx------");
        try {
            Files.setPosixFilePermissions(targetPath, ownerOnlyPermissions);
            Set<PosixFilePermission> actualPermissions = Files.getPosixFilePermissions(targetPath);
            if (!actualPermissions.equals(ownerOnlyPermissions)) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Could not verify owner-only permissions on eForm image directory: {}; actual permissions={}",
                            imageDir, PosixFilePermissions.toString(actualPermissions));
                }
                return false;
            }
            return true;
        } catch (UnsupportedOperationException e) {
            logger.warn("Could not restrict permissions on eForm image directory: {}; POSIX permissions are unsupported on this filesystem", imageDir);
        } catch (IOException e) {
            logger.warn("Could not restrict permissions on eForm image directory: {}; directory may be world-accessible", imageDir, e);
        }
        return false;
    }

    /**
     * Copies a single asset from the WAR to the target directory if it doesn't already exist.
     *
     * @param filename  String the asset filename (e.g., "editControl2.js")
     * @param targetDir File the eForm images directory to deploy into
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    private void deployAsset(String filename, File targetDir) {
        deployAssetFromPath(filename, BUNDLED_ASSETS_PATH + filename, targetDir);
    }

    private void deploySharedJavascriptAsset(String filename, File targetDir) {
        deployAssetFromPath(filename, SHARED_JAVASCRIPT_PATH + filename, targetDir);
    }

    private void deployGeneratedAsset(String filename, File targetDir, byte[] content) {
        try (ByteArrayInputStream stream = new ByteArrayInputStream(content)) {
            deployAssetFromStream(filename, targetDir, stream, "generated:" + filename);
        } catch (IOException e) {
            // ByteArrayInputStream.close() is a documented no-op; this only satisfies the
            // try-with-resources close() contract should a non-array stream type be used here later.
            logger.error("Failed to close generated asset stream for {}", filename, e);
        }
    }

    private void deploySampleLabCompatibilityAssets(File targetDir) {
        deployAssetFromPath("jquery-3.1.0.min.js", JQUERY_RESOURCE_PATH, targetDir);
        for (Map.Entry<String, String> entry : SAMPLE_LAB_COMPATIBILITY_SCRIPTS.entrySet()) {
            deployGeneratedAsset(entry.getKey(), targetDir, entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        // Expected and benign: these legacy sample-lab background images are intentionally not bundled.
        // Log once at DEBUG with the full list rather than a per-asset WARN on every startup, which
        // added recurring noise to production logs (copilot SIxUE). The message is emitted unconditionally
        // because SAMPLE_LAB_BACKGROUND_ASSETS is the (currently non-empty) fixed asset list; a length>0
        // guard here was an always-true test (github-code-quality SI9Wy / cubic SJD90).
        logger.debug("Sample lab compatibility background assets are not bundled and will not be synthesized: {}",
                List.of(SAMPLE_LAB_BACKGROUND_ASSETS));
    }

    private void deployAssetFromPath(String filename, String resourcePath, File targetDir) {
        File targetFile = PathValidationUtils.validateGeneratedChildPath(filename, targetDir);
        if (targetFile.exists()) {
            logger.debug("eForm asset already exists, skipping: {}", targetFile.getAbsolutePath());
            return;
        }

        InputStream resourceStream = servletContext.getResourceAsStream(resourcePath);
        if (resourceStream == null) {
            logger.warn("Bundled eForm asset not found in WAR: {}", resourcePath);
            return;
        }
        try (InputStream is = resourceStream) {
            deployAssetFromStream(filename, targetDir, is, resourcePath);
        } catch (IOException e) {
            logger.error("Failed to deploy eForm asset: {}", filename, e);
        }
    }

    private void deployAssetFromStream(String filename, File targetDir, InputStream is, String sourceLabel) {
        File targetFile = PathValidationUtils.validateGeneratedChildPath(filename, targetDir);
        Path targetPath = targetFile.toPath();
        Path tempFile = null;
        if (targetFile.exists()) {
            logger.debug("eForm asset already exists, skipping: {}", targetFile.getAbsolutePath());
            return;
        }

        try {
            tempFile = Files.createTempFile(targetDir.toPath(), filename + ".", ".tmp");
            Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
            moveTempFile(tempFile, targetPath);
            logger.info("Deployed eForm asset: {} -> {}", sourceLabel, targetFile.getAbsolutePath());
        } catch (FileAlreadyExistsException e) {
            logger.debug("eForm asset was created concurrently, skipping: {}", targetFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to deploy eForm asset: {}", filename, e);
        } finally {
            deleteTempFile(tempFile);
        }
    }

    void moveTempFile(Path tempFile, Path targetPath) throws IOException {
        try {
            moveTempFileAtomically(tempFile, targetPath);
        } catch (AtomicMoveNotSupportedException e) {
            logger.debug("Atomic move not supported for eForm asset deployment; falling back to regular move: {} -> {}", tempFile, targetPath);
            moveTempFileWithoutAtomicOption(tempFile, targetPath);
        }
    }

    void moveTempFileAtomically(Path tempFile, Path targetPath) throws IOException {
        Files.move(tempFile, targetPath, StandardCopyOption.ATOMIC_MOVE);
    }

    void moveTempFileWithoutAtomicOption(Path tempFile, Path targetPath) throws IOException {
        Files.move(tempFile, targetPath);
    }

    private void deleteTempFile(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException deleteEx) {
            logger.warn("Could not remove temporary eForm asset file: {}", tempFile, deleteEx);
        }
    }

    private static Map<String, String> buildSampleLabCompatibilityScripts() {
        Map<String, String> scripts = new LinkedHashMap<>();
        String compatibilityScript = """
                (function(global) {
                  function noop() { return null; }
                  function falsy() { return false; }
                  global.CheckCopyTo = global.CheckCopyTo || noop;
                  global.Reminders = global.Reminders || noop;
                  global.ToggleCopyTo = global.ToggleCopyTo || noop;
                  global.autoLabReqPop = global.autoLabReqPop || noop;
                  global.calculateTicklerDays = global.calculateTicklerDays || noop;
                  global.decisionSupport = global.decisionSupport || noop;
                  global.population = global.population || noop;
                  global.sendTickler = global.sendTickler || falsy;
                  global.setLabLocation = global.setLabLocation || noop;
                  global.ticklerReminder = global.ticklerReminder || noop;
                })(window);
                """;
        scripts.put("LocationsLab_Nov2020.js", compatibilityScript);
        scripts.put("LabDecisionSupport3_2024.js", compatibilityScript);
        scripts.put("LabEngine_2023.js", compatibilityScript);
        return scripts;
    }

}
