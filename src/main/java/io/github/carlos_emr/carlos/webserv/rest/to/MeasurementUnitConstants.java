/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */
package io.github.carlos_emr.carlos.webserv.rest.to;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Measurement unit mappings extracted from E2E module
 */
public class MeasurementUnitConstants {
    
    public static final Map<String, String> measurementUnitMap;
    
    static {
        Map<String, String> map = new HashMap<String, String>();
        map.put("02", "%");
        map.put("02SA", "%");
        map.put("24UA", "mg/24h");
        map.put("24UR", "mg/24h");
        map.put("ACR", "mg/mmol");
        map.put("ALB", "g/l");
        map.put("ALT", "U/L");
        map.put("AST", "U/L");
        map.put("BG", "mmol/L");
        map.put("BILC", "mmol/L");
        map.put("BILT", "mmol/L");
        map.put("BILU", "mmol/L");
        map.put("BMI", "kg/m2");
        map.put("BP", "mmHg");
        map.put("BPD", "mmHg");
        map.put("BPS", "mmHg");
        map.put("BPTE", "");
        map.put("CK", "U/L");
        map.put("CLPL", "mmol/L");
        map.put("Cr", "umol/L");
        map.put("CRPH", "");
        map.put("CYSU", "nmol/L");
        map.put("DM", "");
        map.put("DMSM", "");
        map.put("DREX", "");
        map.put("DRNP", "");
        map.put("EDDD", "");
        map.put("EGFR", "mL/min/1.73m2");
        map.put("EPR", "");
        map.put("EYEE", "");
        map.put("FBPC", "");
        map.put("FEET", "");
        map.put("FEV1", "L");
        map.put("FEV1FVC", "%");
        map.put("FOBT", "");
        map.put("FTST", "");
        map.put("FVC", "L");
        map.put("GGT", "U/L");
        map.put("HBAI", "%");
        map.put("HBAL", "%");
        map.put("HC", "cm");
        map.put("HDL", "mmol/L");
        map.put("HR", "bpm");
        map.put("HT", "cm");
        map.put("iDia", "mmHg");
        map.put("iSys", "mmHg");
        map.put("K", "mmol/L");
        map.put("LDL", "mmol/L");
        map.put("LEO", "");
        map.put("LM", "");
        map.put("MACA", "");
        map.put("MI", "");
        map.put("Na", "mmol/L");
        map.put("NOSK", "cigarettes per day");
        map.put("NTR", "");
        map.put("O2", "%");
        map.put("P", "bpm");
        map.put("POSK", "");
        map.put("PREG", "");
        map.put("R", "breaths per minute");
        map.put("REBG", "");
        map.put("SCR", "umol/L");
        map.put("SEXF", "");
        map.put("SMBG", "");
        map.put("SMCD", "");
        map.put("SMK", "");
        map.put("TCHL", "mmol/L");
        map.put("TCHLDL", "");
        map.put("TG", "mmol/L");
        map.put("UA", "umol/L");
        map.put("WAIS", "cm");
        map.put("WT", "kg");
        measurementUnitMap = Collections.unmodifiableMap(map);
    }
    
    private MeasurementUnitConstants() {
        // Prevent instantiation
    }
}