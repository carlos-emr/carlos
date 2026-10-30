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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.engine;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * The one check that decides whether a local file may be read for a render: is it inside one of the
 * directories the render was given?
 *
 * <p>Paths are compared by canonical form, so symbolic links and {@code ..} segments cannot lead out
 * of an allowed directory, and the check fails closed (a path that cannot be resolved is outside).
 * It never logs a path: a file name can identify a patient, and
 * {@code PathValidationUtils.validateExistingPath}, which makes the same comparison, logs both full
 * paths at ERROR for every directory that does not match, which this module's logging rule
 * forbids.</p>
 *
 * @since 2026-10-29
 */
public final class AllowedDirectories {

    private static final Logger logger = MiscUtils.getLogger();

    private AllowedDirectories() {
    }

    /**
     * Whether a file lies inside one of the directories.
     *
     * @param directories the allowed directories
     * @param target the file or directory to check
     * @return {@code true} if the canonical form of {@code target} is one of the directories or below it
     */
    public static boolean contains(List<Path> directories, Path target) {
        Objects.requireNonNull(directories, "directories");
        Objects.requireNonNull(target, "target");
        try {
            String canonicalTarget = target.toFile().getCanonicalPath();
            for (Path allowed : directories) {
                String canonicalAllowed = allowed.toFile().getCanonicalPath();
                if (canonicalTarget.equals(canonicalAllowed)
                        || canonicalTarget.startsWith(canonicalAllowed + File.separator)) {
                    return true;
                }
            }
        } catch (IOException | RuntimeException e) {
            logger.warn("Could not check a path against the allowed directories; the resource is blocked ({})",
                    e.getClass().getSimpleName());
        }
        return false;
    }
}
