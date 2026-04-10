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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.utility;

/**
 * Constants for safe reflective class loading across CARLOS EMR.
 *
 * <p>Centralises the package-prefix constraint used wherever {@code Class.forName()} is called
 * with a class name that originates from configuration (properties file, database, or XML).
 * Using a single constant ensures a future package rename is a one-file change.</p>
 *
 * @since 2026-04-10
 */
public final class ReflectionConstants {

    private ReflectionConstants() {
        // utility class — no instances
    }

    /**
     * Allowed package prefix for all reflective class loading in CARLOS EMR.
     *
     * <p>Only classes whose fully-qualified name begins with this prefix may be
     * instantiated via reflection. This prefix check is a defence-in-depth measure
     * against arbitrary class loading (CWE-470) in case a configuration source
     * (properties file, database row, or XML document) is tampered with.</p>
     */
    public static final String CARLOS_PACKAGE_PREFIX = "io.github.carlos_emr.carlos.";

}
