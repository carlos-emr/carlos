//CHECKSTYLE:OFF
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
 */
package ca.openosp.openo.commn;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import ca.openosp.openo.utility.MiscUtils;

public class ISO36612 {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static ISO36612 obj = null;

    Map<String, String> codeToHRStringMap = new HashMap<String, String>();


    public static ISO36612 getInstance() {
        if (obj == null) {
            obj = new ISO36612();
        }
        return obj;
    }

    public ISO36612() {
        InputStream in = null;
        ObjectNode topLevelObj = null;
        try {
            in = this.getClass().getClassLoader().getResourceAsStream("iso-3166-2.json");
            String theString = IOUtils.toString(in, "UTF-8");
            topLevelObj = (ObjectNode) objectMapper.readTree(theString);
        } catch (Exception e) {
            MiscUtils.getLogger().warn("Warning", e);
        } finally {
            IOUtils.closeQuietly(in);
        }

        try {
            Iterator<String> iter = topLevelObj.fieldNames();
            while (iter.hasNext()) {
                String countryCode = iter.next();
                ObjectNode country = (ObjectNode) topLevelObj.get(countryCode);
                String countryName = country.get("name").asText();
                ObjectNode divisions = (ObjectNode) country.get("divisions");
                Iterator<String> iter2 = divisions.fieldNames();
                while (iter2.hasNext()) {
                    String divisionCode = iter2.next();
                    String divisionName = divisions.get(divisionCode).asText();
                    codeToHRStringMap.put(divisionCode, divisionName + "," + countryName);
                    if (divisionCode.startsWith("CA-")) {
                        codeToHRStringMap.put(divisionCode.substring(3), divisionName + "," + countryName);
                    }
                }

            }
        } catch (Exception e) {
            MiscUtils.getLogger().warn("Warning", e);
        }

    }

    public String translateCodeToHumanReadableString(String code) {
        if (StringUtils.isEmpty(code)) {
            return null;
        }
        return codeToHRStringMap.get(code);
    }

    public static void main(String args[]) {
        MiscUtils.getLogger().info(ISO36612.getInstance().translateCodeToHumanReadableString("ON"));


    }
}
