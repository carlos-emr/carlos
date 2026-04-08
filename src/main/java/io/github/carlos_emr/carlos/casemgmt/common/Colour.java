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


/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package io.github.carlos_emr.carlos.casemgmt.common;

import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.CarlosProperties;

/**
 * @author jackson
 */
public class Colour {

    /**
     * Allowed package prefix for Colour subclass instantiation via reflection.
     *
     * <p>The class name is read from {@code ColourClass} in the server-side properties
     * file. This prefix check is a defence-in-depth measure to prevent arbitrary class
     * instantiation if the properties file is tampered with.</p>
     */
    private static final String ALLOWED_PACKAGE_PREFIX = "io.github.carlos_emr.carlos.";

    public static Colour getInstance() {
        Colour c = null;
        try {
            String colourClass = CarlosProperties.getInstance().getProperty("ColourClass", "io.github.carlos_emr.carlos.casemgmt.common.Colour");
            if (colourClass.length() > 0) {
                if (!colourClass.startsWith(ALLOWED_PACKAGE_PREFIX)) {
                    MiscUtils.getLogger().error("Rejected Colour class outside allowed package");
                    return new Colour();
                }
                c = (Colour) Class.forName(colourClass).newInstance();
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        if (c == null)
            return new Colour();
        return c;
    }

    public String prevention = "009999";
    public String tickler = "FF6600";
    public String disease = "5A5A5A";
    public String forms = "917611";
    public String eForms = "008000";
    public String documents = "476BB3";
    public String labs = "A0509C";
    public String hrmDocuments = "6699CC";
    public String messages = "7F462C";
    public String measurements = "344887";
    public String rx = "7D2252";
    public String allergy = "C85A17";
    public String omed = "306754";
    public String riskFactors = "993333";
    public String familyHistory = "006600";
    public String unresolvedIssues = "CC9900";
    public String resolvedIssues = "151B8D";
    public String socialHistory = "996633";
    public String medicalHistory = "996633";
    public String ongoingConcerns = "996633";
    public String reminders = "996633";
    public String invoices = "254117";
    public String consultation = "6C2DC7";
    public String episode = "045228";
    public String contacts = "045228";
    public String ticklerNotes = "FF6600";
    public String externalNotes = "005151";
    public String emailNotes = "345553";
}
