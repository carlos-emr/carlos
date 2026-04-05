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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.github.carlos_emr.carlos.utility.MiscUtils;


/**
 * Creates pdf graphic class passed to it with reflection.
 *
 * <p>Only classes explicitly registered in the {@link #ALLOWED_GRAPHICS} allowlist
 * may be instantiated.  Passing an unregistered class name returns {@code null}
 * and logs a warning, preventing unsafe reflection from config-file-controlled input.
 */
public class FrmGraphicFactory {

    /**
     * Allowlist mapping fully-qualified class names to their {@link Class} objects.
     * Only classes in this map can be created by {@link #create(String)}.
     */
    private static final Map<String, Class<? extends FrmPdfGraphic>> ALLOWED_GRAPHICS;

    static {
        Map<String, Class<? extends FrmPdfGraphic>> m = new HashMap<>();
        m.put("io.github.carlos_emr.carlos.form.graphic.FrmPdfGraphicAR", FrmPdfGraphicAR.class);
        m.put("io.github.carlos_emr.carlos.form.graphic.FrmPdfGraphicRourke", FrmPdfGraphicRourke.class);
        m.put("io.github.carlos_emr.carlos.form.graphic.FrmPdfGraphicGrowthChart", FrmPdfGraphicGrowthChart.class);
        ALLOWED_GRAPHICS = Collections.unmodifiableMap(m);
    }

    public static FrmPdfGraphic create(String name) {
        if (name == null) {
            return null;
        }
        Class<? extends FrmPdfGraphic> classDefinition = ALLOWED_GRAPHICS.get(name);
        if (classDefinition == null) {
            MiscUtils.getLogger().warn("FrmGraphicFactory: class '{}' is not on the allowlist — refusing to load", name);
            return null;
        }
        try {
            return classDefinition.getConstructor().newInstance();
        } catch (Exception e) {
            MiscUtils.getLogger().debug("Could not instantiate graphic class: " + name, e);
            return null;
        }
    }

}
