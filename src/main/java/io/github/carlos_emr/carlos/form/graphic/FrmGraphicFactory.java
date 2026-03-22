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

import io.github.carlos_emr.carlos.utility.MiscUtils;


/**
 * Factory for creating {@link FrmPdfGraphic} instances using reflection.
 *
 * <p>Instantiates the appropriate PDF growth chart graphic renderer based on the
 * fully qualified class name. Used by the form printing subsystem to generate
 * growth chart overlays on PDF forms.</p>
 *
 * @see FrmPdfGraphic
 * @since 2026-03-17
 */
public class FrmGraphicFactory {

    /**
     * Creates a FrmPdfGraphic instance for the specified class name.
     *
     * @param name String the fully qualified class name of the graphic renderer
     * @return FrmPdfGraphic the instantiated graphic renderer, or {@code null}
     *         if the class cannot be found or instantiated
     */
    public static FrmPdfGraphic create(String name) {
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
