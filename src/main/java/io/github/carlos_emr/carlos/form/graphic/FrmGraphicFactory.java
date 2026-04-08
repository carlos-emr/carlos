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


package io.github.carlos_emr.carlos.form.graphic;

import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import java.util.Set;


/**
 * Creates pdf graphic class passed to it with reflection
 */
public class FrmGraphicFactory {

    /**
     * Whitelist of allowed {@link FrmPdfGraphic} implementation class names.
     *
     * <p>The class name originates from {@code __className} entries in form configuration
     * files. Only classes in this set may be instantiated via reflection to prevent
     * arbitrary class loading (CWE-470).</p>
     */
    private static final Set<String> ALLOWED_GRAPHIC_CLASSES = Set.of(
            "io.github.carlos_emr.carlos.form.graphic.FrmPdfGraphicAR",
            "io.github.carlos_emr.carlos.form.graphic.FrmPdfGraphicGrowthChart",
            "io.github.carlos_emr.carlos.form.graphic.FrmPdfGraphicRourke"
    );

    public static FrmPdfGraphic create(String name) {
        if (name == null || !ALLOWED_GRAPHIC_CLASSES.contains(name)) {
            MiscUtils.getLogger().warn("Rejected disallowed graphic class name: {}",
                    LogSanitizer.sanitize(name));
            return null;
        }
        FrmPdfGraphic pdfGraph = null;
        try {
            Class classDefinition = Class.forName(name);
            pdfGraph = (FrmPdfGraphic) classDefinition.newInstance();
        } catch (InstantiationException e) {
            MiscUtils.getLogger().debug("debug", e);
        } catch (IllegalAccessException e) {
            MiscUtils.getLogger().debug("debug", e);
        } catch (ClassNotFoundException e) {
            MiscUtils.getLogger().debug("debug", e);
        }

        return pdfGraph;
    }

}
