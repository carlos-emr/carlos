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
package io.github.carlos_emr.carlos.match.client;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Data transfer object holding a waitlisted client's attributes for vacancy matching.
 *
 * <p>Contains the client (demographic) identifier, the associated waitlist intake form
 * identifier, and a map of attribute key-value pairs extracted from the intake form.
 * These attributes are compared against {@link VacancyTemplateData} criteria during
 * the matching process.</p>
 *
 * @see VacancyData
 * @see Matcher
 * @since 2026-03-17
 */
public class ClientData {
    private int clientId;
    private int formId;
    private Map<String, String> clientData = new HashMap<String, String>();

    /**
     * Returns the client (demographic) identifier.
     *
     * @return int the client identifier
     */
    public int getClientId() {
        return clientId;
    }

    /**
     * Sets the client (demographic) identifier.
     *
     * @param clientId int the client identifier to set
     */
    public void setClientId(int clientId) {
        this.clientId = clientId;
    }

    /**
     * Returns the waitlist intake form identifier.
     *
     * @return int the form identifier
     */
    public int getFormId() {
        return formId;
    }

    /**
     * Sets the waitlist intake form identifier.
     *
     * @param formId int the form identifier to set
     */
    public void setFormId(int formId) {
        this.formId = formId;
    }

    /**
     * Returns the map of client attribute key-value pairs from the intake form.
     *
     * @return Map of String attribute names to String values
     */
    public Map<String, String> getClientData() {
        return clientData;
    }

    @Override
    public String toString() {
        final int maxLen = 20;
        return "ClientData [clientId="
                + clientId
                + ", formId="
                + formId
                + ", clientData="
                + (clientData != null ? toString(clientData.entrySet(), maxLen)
                : null) + "]";
    }

    private String toString(Collection<?> collection, int maxLen) {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        int i = 0;
        for (Iterator<?> iterator = collection.iterator(); iterator.hasNext()
                && i < maxLen; i++) {
            if (i > 0)
                builder.append(", ");
            builder.append(iterator.next());
        }
        builder.append("]");
        return builder.toString();
    }

}
