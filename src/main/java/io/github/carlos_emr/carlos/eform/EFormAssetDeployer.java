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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.context.ServletContextAware;

import io.github.carlos_emr.CarlosProperties;

/**
 * Deploys bundled Rich Text Letter (RTL) eForm assets from the WAR to the
 * eForm images directory on application startup.
 *
 * <h3>Architecture Context</h3>
 * <p>The RTL eForm stores its HTML in the database ({@code eform.form_html} column),
 * but references external JavaScript and template files via {@code displayImage.do}
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

    /**
     * Assets to deploy. These filenames must match exactly what the RTL eForm's
     * form_html references via {@code displayImage.do?imagefile=<filename>}.
     */
    private static final String[] ASSETS = {
        "editControl2.js",
        "blank.rtl",
        "editor_help.html"
    };

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
     * <p>Exits early (with a warning log) if the directory is not configured
     * or doesn't exist on disk. This is non-fatal — the application still starts,
     * but the RTL editor will be broken until the directory is created and Tomcat restarted.</p>
     */
    @Override
    public void afterPropertiesSet() {
        String imageDir = CarlosProperties.getInstance().getEformImageDirectory();
        if (imageDir == null || imageDir.isBlank()) {
            logger.warn("eForm image directory not configured; skipping asset deployment");
            return;
        }

        File targetDir = new File(imageDir);
        if (!targetDir.isDirectory()) {
            logger.warn("eForm image directory does not exist: {}; skipping asset deployment", imageDir);
            return;
        }

        for (String asset : ASSETS) {
            deployAsset(asset, targetDir);
        }
    }

    /**
     * Copies a single asset from the WAR to the target directory if it doesn't already exist.
     *
     * @param filename  String the asset filename (e.g., "editControl2.js")
     * @param targetDir File the eForm images directory to deploy into
     */
    private void deployAsset(String filename, File targetDir) {
        File targetFile = new File(targetDir, filename);
        if (targetFile.exists()) {
            logger.debug("eForm asset already exists, skipping: {}", targetFile.getAbsolutePath());
            return;
        }

        String resourcePath = BUNDLED_ASSETS_PATH + filename;
        try (InputStream is = servletContext.getResourceAsStream(resourcePath)) {
            if (is == null) {
                logger.warn("Bundled eForm asset not found in WAR: {}", resourcePath);
                return;
            }
            Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("Deployed eForm asset: {} -> {}", resourcePath, targetFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to deploy eForm asset: {}", filename, e);
        }
    }
}
