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
 * Deploys bundled eForm assets (editControl2.js, blank.rtl, editor_help.html)
 * to the eForm images directory on application startup.
 *
 * <p>Files are only copied if they do not already exist in the target directory.
 * This prevents overwriting clinic-customized versions. To force an update,
 * an administrator must manually delete the file from the eForm images directory.</p>
 *
 * <p>The {@code stamps.js} file is intentionally NOT auto-deployed because it
 * contains clinic-specific doctor signature mappings that administrators create.</p>
 *
 * @since 2026-03-22
 */
public class EFormAssetDeployer implements InitializingBean, ServletContextAware {

    private static final Logger logger = LogManager.getLogger(EFormAssetDeployer.class);

    private static final String BUNDLED_ASSETS_PATH = "/WEB-INF/eform-assets/";
    private static final String[] ASSETS = {
        "editControl2.js",
        "blank.rtl",
        "editor_help.html"
    };

    private jakarta.servlet.ServletContext servletContext;

    public void setServletContext(jakarta.servlet.ServletContext servletContext) {
        this.servletContext = servletContext;
    }

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
