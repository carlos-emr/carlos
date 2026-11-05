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


package io.github.carlos_emr;


import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.util.UtilDict;

/** Legacy request/control configuration; clinical queries use typed DAOs. */
public class AppointmentMainBean {

    private UtilDict toFile = null;
    private UtilDict dbSQL = null;
    private UtilDict requestUtilDict = null;
    private String targetType = null;
    private boolean bDoConfigure = false;

    /**
     * Gets the target control-to-file mappings.
     * 
     * @return UtilDict containing the control-to-file mappings
     */
    public UtilDict getTargets() {
        return toFile;
    }

    /**
     * Gets the utility dictionary containing processed request parameters.
     * 
     * @return UtilDict with request parameter mappings
     */
    public UtilDict getRequestUtilDict() {
        return requestUtilDict;
    }

    /**
     * Checks if the bean has been configured.
     * 
     * @return true if configured, false otherwise
     */
    public boolean getBDoConfigure() {
        return bDoConfigure;
    }

    /**
     * Resets the configuration state to false.
     */
    public void setBDoConfigure() {
        bDoConfigure = false;
    }

    /**
     * Configures the bean with database operations only.
     * 
     * @param dbOperation 2D array of database operation definitions
     */
    public void doConfigure(String[][] dbOperation) {
        bDoConfigure = true;
        dbSQL = new UtilDict();
        dbSQL.setDef(dbOperation);
    }

    /**
     * Configures the bean with both database operations and control-to-file mappings.
     * 
     * @param dbOperation 2D array of database operation definitions
     * @param controlToFile 2D array of control-to-file mapping definitions
     */
    public void doConfigure(String[][] dbOperation, String[][] controlToFile) {
        bDoConfigure = true;
        toFile = new UtilDict();
        toFile.setDef(controlToFile);
        dbSQL = new UtilDict();
        dbSQL.setDef(dbOperation);

    }

    public void doCommand(HttpServletRequest request) {
        //wrap the request object into a Dict help object
        requestUtilDict = new UtilDict();
        requestUtilDict.setDef(request);
    }

    public String whereTo() {
        targetType = requestUtilDict.getDef("displaymode", "");
        return toFile.getDef(targetType, "");
    }

    public String whereTo(String displaymode) {
        targetType = requestUtilDict.getDef(displaymode, "");
        return toFile.getDef(targetType, "");
    }

    public String getString(Object o) {
        if (o == null) return "";
        return (String) o;
    }
}
