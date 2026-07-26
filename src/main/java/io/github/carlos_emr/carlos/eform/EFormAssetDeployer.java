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
 * <h3>Managed vs. Seeded Assets</h3>
 * <p>Deployment behavior depends on who owns the file:</p>
 * <ul>
 *   <li><b>Managed</b> ({@code editControl2.js}, the bundled JS libraries, {@code BNK.png} — see
 *       {@code MANAGED_ASSETS}): application code CARLOS owns. Deployed if absent, and replaced on
 *       startup whenever the on-disk bytes differ from the shipped version. Local edits are
 *       unsupported and are reverted.</li>
 *   <li><b>Seeded</b> (everything else — {@code blank.rtl}, {@code editor_help.html}, the generated
 *       lab decision-support stubs): deployed once if absent, then never touched, because a clinic
 *       is expected to customize them.</li>
 * </ul>
 * <p>An unchanged managed asset is compared and left alone, so a steady-state startup performs no
 * writes and logs nothing. Earlier versions skipped <em>every</em> existing file, which meant a
 * corrected asset could never reach an install that already had one.</p>
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
    /** Compatibility shim ($.browser, .andSelf, .size, .live/.die, .bind/.unbind) appended to the
     *  jQuery bundle deployed under legacy 1.x/3.1 filenames so pre-3.x forms keep working. */
    private static final String JQUERY_COMPAT_RESOURCE_PATH = "/library/jquery/jquery-compat.js";
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

    /**
     * Assets CARLOS owns outright and keeps at the shipped version: the editor engine, bundled
     * JavaScript libraries, and a generated placeholder image. These are application code, not
     * configuration — editing them locally is forking CARLOS, and a stale copy is a defect that
     * follows the install forever. On every startup a managed asset whose bytes differ from the
     * shipped version is replaced.
     *
     * <p>Everything else the deployer writes is <em>seeded</em>: deployed once if absent, then never
     * touched again, because a clinic is expected to customize it. That covers {@code blank.rtl}
     * (the default letter template), {@code editor_help.html}, and the generated lab
     * decision-support stubs, whose entire purpose is to be replaced by a clinic's real script.
     * {@code stamps.js} is not deployed at all and is unaffected.</p>
     *
     * <p>Why this split rather than a blanket never-clobber rule: the deployer previously skipped
     * every existing file, so a corrected {@code editControl2.js} could never reach an install that
     * already had one, and the only remedy was an administrator manually deleting the file and
     * restarting. That cost a real defect — a saved Rich Text Letter would not load back into the
     * editor, and the next save overwrote the stored letter with an empty one. The same hazard was
     * already noted for the jQuery bundle in {@link #deployJqueryWithCompat}, where a degraded
     * asset would have become permanent across redeploys.</p>
     *
     * <p>Adding a filename here declares "clinic edits to this file are unsupported and will be
     * reverted on restart." Do not add anything a clinic is expected to author.</p>
     */
    private static final java.util.Set<String> MANAGED_ASSETS = java.util.Set.of(
        "editControl2.js",
        "signature_pad.min.js",
        "BNK.png",
        "jquery-3.1.0.min.js",
        "jquery-1.12.0.min.js"
    );
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

        // Per-asset outcomes are logged above (deployed / already-exists / failed). This line marks
        // that the deployer RAN to the end, not that every asset succeeded — a failed asset above is
        // logged there — so it says "attempt finished" rather than "complete" to avoid misleading
        // startup monitoring into treating the assets as fully available.
        logger.info("eForm asset deployment attempt finished for directory: {}", targetDir.getAbsolutePath());
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
        // Deliberate aliasing: legacy sample-lab eForms reference "jquery-3.1.0.min.js" /
        // "jquery-1.12.0.min.js" in their stored HTML, so the current bundle (3.7.1) is deployed UNDER
        // those legacy filenames to keep the forms working without editing every stored form. Serving
        // 3.7.1 to a 1.x-era form would silently break removed APIs ($.browser, .size(), .andSelf), so
        // the jQuery bundle is combined with jquery-compat.js (which restores them) under these names.
        deployJqueryWithCompat("jquery-3.1.0.min.js", targetDir);
        deployJqueryWithCompat("jquery-1.12.0.min.js", targetDir);
        for (Map.Entry<String, String> entry : SAMPLE_LAB_COMPATIBILITY_SCRIPTS.entrySet()) {
            deployGeneratedAsset(entry.getKey(), targetDir, entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        // Expected and benign: these legacy sample-lab background images are intentionally not bundled.
        // Log once at DEBUG with the full list rather than a per-asset WARN on every startup (which is
        // recurring production-log noise for an expected condition).
        logger.debug("Sample lab compatibility background assets are not bundled and will not be synthesized: {}",
                List.of(SAMPLE_LAB_BACKGROUND_ASSETS));
    }

    private void deployAssetFromPath(String filename, String resourcePath, File targetDir) {
        File targetFile = PathValidationUtils.validateGeneratedChildPath(filename, targetDir);
        // Managed assets fall through to deployAssetFromStream, which compares the shipped bytes
        // against what is on disk; only seeded assets short-circuit here.
        if (targetFile.exists() && !isManagedAsset(filename)) {
            logger.debug("Seeded eForm asset already exists, leaving clinic copy in place: {}",
                    targetFile.getAbsolutePath());
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

    /**
     * Deploys the current jQuery bundle concatenated with {@code jquery-compat.js} under a legacy
     * jQuery filename, so a pre-3.x form that loads (say) {@code jquery-1.12.0.min.js} transparently
     * receives jQuery 3.7.1 plus the shims for the APIs 3.x removed ({@code $.browser}, {@code .size()},
     * {@code .andSelf()}, {@code .live}/{@code .die}, {@code .bind}/{@code .unbind}). The compat IIFE
     * extends {@code window.jQuery}, so it must run after the bundle — hence append, not prepend.
     */
    private void deployJqueryWithCompat(String filename, File targetDir) {
        File targetFile = PathValidationUtils.validateGeneratedChildPath(filename, targetDir);
        // See deployAssetFromPath: managed assets are compared, not skipped. This is the case the
        // "degraded asset would become permanent across redeploys" comment below was worried about.
        if (targetFile.exists() && !isManagedAsset(filename)) {
            logger.debug("Seeded eForm asset already exists, leaving clinic copy in place: {}",
                    targetFile.getAbsolutePath());
            return;
        }
        try (InputStream jq = servletContext.getResourceAsStream(JQUERY_RESOURCE_PATH);
             InputStream compat = servletContext.getResourceAsStream(JQUERY_COMPAT_RESOURCE_PATH)) {
            if (jq == null) {
                logger.warn("Bundled jQuery not found in WAR: {}", JQUERY_RESOURCE_PATH);
                return;
            }
            java.io.ByteArrayOutputStream combined = new java.io.ByteArrayOutputStream();
            jq.transferTo(combined);
            if (compat != null) {
                // Separator so the minified bundle (which may not end in ;/newline) cannot merge with
                // the compat IIFE's opening token.
                combined.write("\n;\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                compat.transferTo(combined);
            } else {
                // Fail loudly rather than publishing bare jQuery 3.7.1 under a legacy 1.x filename.
                // Without the shim a pre-3.x form loses $.browser/.size()/.live()/.bind() and its
                // build script throws mid-execution -- but the file still serves 200, so the render
                // network scan sees nothing wrong and the form is captured half-built. Worse, the
                // skip-if-exists check above would make that degraded asset permanent across
                // redeploys. Deploying nothing makes the legacy filename 404, which the render gate
                // does see.
                logger.error("jQuery compat shim not found in WAR ({}); refusing to deploy {} without it",
                        JQUERY_COMPAT_RESOURCE_PATH, filename);
                return;
            }
            deployGeneratedAsset(filename, targetDir, combined.toByteArray());
        } catch (IOException e) {
            logger.error("Failed to deploy combined jQuery+compat asset: {}", filename, e);
        }
    }

    private void deployAssetFromStream(String filename, File targetDir, InputStream is, String sourceLabel) {
        File targetFile = PathValidationUtils.validateGeneratedChildPath(filename, targetDir);
        Path targetPath = targetFile.toPath();
        Path tempFile = null;
        boolean managed = isManagedAsset(filename);
        if (targetFile.exists() && !managed) {
            logger.debug("Seeded eForm asset already exists, leaving clinic copy in place: {}",
                    targetFile.getAbsolutePath());
            return;
        }

        try {
            tempFile = Files.createTempFile(targetDir.toPath(), filename + ".", ".tmp");
            Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
            if (targetFile.exists()) {
                // Managed asset with a copy already on disk. Compare before writing so an unchanged
                // asset is neither rewritten nor logged on every startup — only a genuine version
                // change touches the filesystem.
                if (Files.mismatch(tempFile, targetPath) < 0) {
                    logger.debug("Managed eForm asset is already current: {}", targetFile.getAbsolutePath());
                    return;
                }
                replaceTempFile(tempFile, targetPath);
                logger.info("Updated managed eForm asset to the shipped version: {} -> {}",
                        sourceLabel, targetFile.getAbsolutePath());
                return;
            }
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

    /**
     * True when CARLOS owns this asset outright and keeps it at the shipped version.
     *
     * @see #MANAGED_ASSETS
     */
    static boolean isManagedAsset(String filename) {
        return MANAGED_ASSETS.contains(filename);
    }

    void moveTempFile(Path tempFile, Path targetPath) throws IOException {
        try {
            moveTempFileAtomically(tempFile, targetPath);
        } catch (AtomicMoveNotSupportedException e) {
            logger.debug("Atomic move not supported for eForm asset deployment; falling back to regular move: {} -> {}", tempFile, targetPath);
            moveTempFileWithoutAtomicOption(tempFile, targetPath);
        }
    }

    /**
     * Replaces an existing managed asset. Separate from {@link #moveTempFile} because that path
     * deliberately fails when the target already exists (its {@code FileAlreadyExistsException}
     * catch is how a concurrent first-deploy is detected); replacing needs the opposite semantics.
     */
    void replaceTempFile(Path tempFile, Path targetPath) throws IOException {
        try {
            replaceTempFileAtomically(tempFile, targetPath);
        } catch (AtomicMoveNotSupportedException e) {
            logger.debug("Atomic move not supported for eForm asset replacement; falling back to regular move: {} -> {}", tempFile, targetPath);
            replaceTempFileWithoutAtomicOption(tempFile, targetPath);
        }
    }

    void replaceTempFileAtomically(Path tempFile, Path targetPath) throws IOException {
        Files.move(tempFile, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    void replaceTempFileWithoutAtomicOption(Path tempFile, Path targetPath) throws IOException {
        Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
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
                  var warned = false;
                  function warnStub(fnName) {
                    if (warned) { return; }
                    if (global.console && console.warn) {
                      console.warn("CARLOS: a lab decision-support script is not deployed; its functions are stubbed and do nothing. Contact your administrator if lab decision support is expected.");
                    }
                    // A console warning is invisible to clinicians and to the headless PDF renderer.
                    // On the FIRST invocation of any stubbed function, make the degradation visible at
                    // the point of use (an in-form banner) and durable for operators (a server beacon),
                    // so a silently-disabled reminder/tickler is not mistaken for a completed action.
                    try {
                      var doc = global.document;
                      if (doc && doc.body && doc.createElement) {
                        // Latch only once the banner is actually in the DOM. Setting it earlier meant a
                        // first stub call from an inline <head> script (doc.body still null -- normal for
                        // this corpus) permanently suppressed both the banner and the beacon, leaving a
                        // console-only trace no clinician or renderer can see.
                        warned = true;
                        var banner = doc.createElement('div');
                        banner.setAttribute('id', 'carlos-lab-ds-unavailable');
                        banner.textContent = 'Lab decision support is not available on this server \\u2014 automated reminders/ticklers on this form will not run. Contact your administrator.';
                        banner.style.cssText = 'position:fixed;top:0;left:0;right:0;z-index:99999;background:#fdecea;color:#611a15;padding:8px 12px;font:14px sans-serif;border-bottom:2px solid #f5c6cb;';
                        doc.body.appendChild(banner);
                      }
                      var ctxEl = doc && doc.getElementById ? doc.getElementById('context') : null;
                      var fidEl = doc && doc.getElementById ? doc.getElementById('fid') : null;
                      var ctx = ctxEl ? ctxEl.value : '';
                      if (ctx && global.navigator && global.navigator.sendBeacon && global.URLSearchParams) {
                        var params = new global.URLSearchParams();
                        params.append('formId', fidEl ? fidEl.value : '');
                        params.append('error', 'lab decision-support stub invoked (not deployed): ' + (fnName || 'unknown'));
                        global.navigator.sendBeacon(ctx + '/eform/logEformError', params);
                      }
                    } catch (e) { /* best-effort visibility only; never break the form */ }
                  }
                  // Bind the name so the beacon reports which stub ran; an unbound warnStub() call
                  // always reported 'unknown' and told an operator nothing about which script is missing.
                  function noop(name) { return function () { warnStub(name); return null; }; }
                  function falsy(name) { return function () { warnStub(name); return false; }; }
                  global.CheckCopyTo = global.CheckCopyTo || noop('CheckCopyTo');
                  global.Reminders = global.Reminders || noop('Reminders');
                  global.ToggleCopyTo = global.ToggleCopyTo || noop('ToggleCopyTo');
                  global.autoLabReqPop = global.autoLabReqPop || noop('autoLabReqPop');
                  global.calculateTicklerDays = global.calculateTicklerDays || noop('calculateTicklerDays');
                  global.decisionSupport = global.decisionSupport || noop('decisionSupport');
                  global.population = global.population || noop('population');
                  global.sendTickler = global.sendTickler || falsy('sendTickler');
                  global.setLabLocation = global.setLabLocation || noop('setLabLocation');
                  global.ticklerReminder = global.ticklerReminder || noop('ticklerReminder');
                  // Render-gate marker. The stub is deployed under the REAL script filename, so the
                  // request returns 200 and the renderer's network scan cannot tell a stubbed form from
                  // a working one -- a requisition would fax with unpopulated fields and no tickler
                  // while the render reported complete. Mirrors the signature path's
                  // #carlos-signature-unrendered marker, which readPageGeometry already reports.
                  try {
                    var markerDoc = global.document;
                    if (markerDoc && markerDoc.createElement && !markerDoc.getElementById('carlos-lab-ds-stubbed')) {
                      var marker = markerDoc.createElement('div');
                      marker.setAttribute('id', 'carlos-lab-ds-stubbed');
                      marker.style.cssText = 'display:none';
                      (markerDoc.body || markerDoc.documentElement).appendChild(marker);
                    }
                  } catch (e) { /* marker is best-effort; never break the form */ }
                })(window);
                """;
        scripts.put("LocationsLab_Nov2020.js", compatibilityScript);
        scripts.put("LabDecisionSupport3_2024.js", compatibilityScript);
        scripts.put("LabEngine_2023.js", compatibilityScript);
        return scripts;
    }

}
