/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

/**
 *
 */
package io.github.carlos_emr.carlos.lab.ca.all.pageUtil;

/**
 * Provides version-specific casting of HAPI ORU_R01 message objects to their
 * corresponding HL7 version classes (v2.2, v2.3, v2.5, v2.6). Used during lab
 * result parsing to access version-specific segment accessors.
 *
 * @since 2008-01-01
 */
public class ORUR01Manager {

	/*public static ca.uhn.hl7v2.model.v21.message.ORU_R01 getORUR01_21(Object obj) {
		return (ca.uhn.hl7v2.model.v21.message.ORU_R01) obj;
	}*/

    /**
     * Casts the given object to an HL7 v2.2 ORU_R01 message.
     *
     * @param obj Object the HAPI message object to cast
     * @return ca.uhn.hl7v2.model.v22.message.ORU_R01 the typed ORU_R01 message
     */
    public static ca.uhn.hl7v2.model.v22.message.ORU_R01 getORUR01_22(Object obj) {
        return (ca.uhn.hl7v2.model.v22.message.ORU_R01) obj;
    }

    /**
     * Casts the given object to an HL7 v2.3 ORU_R01 message.
     *
     * @param obj Object the HAPI message object to cast
     * @return ca.uhn.hl7v2.model.v23.message.ORU_R01 the typed ORU_R01 message
     */
    public static ca.uhn.hl7v2.model.v23.message.ORU_R01 getORUR01_23(Object obj) {
        return (ca.uhn.hl7v2.model.v23.message.ORU_R01) obj;
    }

	/*public static ca.uhn.hl7v2.model.v24.message.ORU_R01 getORUR01_24(Object obj) {
		return (ca.uhn.hl7v2.model.v24.message.ORU_R01) obj;
	}*/

    public static ca.uhn.hl7v2.model.v25.message.ORU_R01 getORUR01_25(Object obj) {
        return (ca.uhn.hl7v2.model.v25.message.ORU_R01) obj;
    }

    public static ca.uhn.hl7v2.model.v26.message.ORU_R01 getORUR01_26(Object obj) {
        return (ca.uhn.hl7v2.model.v26.message.ORU_R01) obj;
    }

    /**
     * Removes the dots between the version number and returns the pure integer representation of the version
     *
     * @param version the string representation of the lab version, usually 2.5
     * @return the integer representation of the version, without the decimal
     */
    public static int getVersion(String version) {

        String v2 = version.replaceAll("(\\n)*\\.(\\n)*", "");
        if (v2.length() > 0)
            return Integer.parseInt(v2);
        return 0;
    }
}
