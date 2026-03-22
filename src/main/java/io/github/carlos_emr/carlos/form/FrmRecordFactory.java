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


package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Factory for creating {@link FrmRecord} instances based on form name.
 *
 * <p>Uses reflection to instantiate the appropriate form record handler class
 * based on a naming convention: the form name is mapped to a class in the
 * {@code io.github.carlos_emr.carlos.form} package with the pattern
 * {@code Frm<FormName>Record}.</p>
 *
 * @see FrmRecord
 * @since 2026-03-17
 */
public class FrmRecordFactory {


    /**
     * Creates a FrmRecord instance for the specified form type.
     *
     * @param which String the form name (e.g., "Rourke2020", "BCAR2020", "LabReq")
     *              used to resolve the class {@code Frm<which>Record}
     * @return FrmRecord the instantiated form record handler, or {@code null} if
     *         the class cannot be found or instantiated
     */
    public FrmRecord factory(String which) {

        // Build the full class name from the form name (the 'which' parameter).
        String fullName = "io.github.carlos_emr.carlos.form.Frm" + which + "Record"; // keyword - form_name get reference to the class
        FrmRecord myClass = null;

        try {
            Class classDefinition = Class.forName(fullName);
            myClass = (FrmRecord) classDefinition.newInstance();
        } catch (InstantiationException e) {
            MiscUtils.getLogger().debug("debug", e);
        } catch (IllegalAccessException e) {
            MiscUtils.getLogger().debug("debug", e);
        } catch (ClassNotFoundException e) {
            MiscUtils.getLogger().debug("debug", e);
        }

        return myClass;
    }
}
