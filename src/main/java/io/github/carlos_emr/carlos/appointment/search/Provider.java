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
package io.github.carlos_emr.carlos.appointment.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AllowedAppointmentTypeTransfer;
import io.github.carlos_emr.carlos.webserv.rest.to.model.BookingProviderTransfer;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Represents a healthcare provider configured for online appointment booking.
 *
 * <p>Contains the provider number, allowed appointment types with schedule template codes,
 * per-appointment-type duration overrides, team member associations, filter definitions,
 * and messaging configuration. Providers can be deserialized from XML configuration
 * or from REST transfer objects.</p>
 *
 * @since 2026-03-17
 */
public class Provider {

    private static Logger logger = MiscUtils.getLogger();
    String messageUserId;
    String providerNo = null;
    @Transient
    String lastName;
    @Transient
    String firstName;
    Map<String, Character[]> appointmentTypes = null;
    Map<Long, Integer> appointmentDurations = null;
    List<Provider> teamMembers = null;
    List<FilterDefinition> filters = null;
    String role = null;

    private ArrayList<Map<String, String>> suggestedRelationships;

    /**
     * Returns the provider number.
     *
     * @return String the provider number
     */
    public String getProviderNo() {
        return providerNo;
    }

    /**
     * Returns the list of filter definitions applied to this provider's schedule.
     *
     * @return List&lt;FilterDefinition&gt; the filter definitions
     */
    public List<FilterDefinition> getFilter() {
        return filters;
    }

    /**
     * Returns the map of appointment type IDs to custom durations in minutes.
     *
     * @return Map&lt;Long, Integer&gt; appointment type ID to duration mapping
     */
    public Map<Long, Integer> getAppointmentDurations() {
        return appointmentDurations;
    }

    /**
     * Returns the map of appointment type IDs to allowed schedule template codes.
     *
     * @return Map&lt;String, Character[]&gt; appointment type ID to allowed code arrays
     */
    public Map<String, Character[]> getAppointmentTypes() {
        return appointmentTypes;
    }

    /**
     * Returns the list of team members associated with this provider.
     *
     * @return List&lt;Provider&gt; the team member providers
     */
    public List<Provider> getTeamMembers() {
        return teamMembers;
    }

    /**
     * Returns the messaging system user ID for this provider.
     *
     * @return String the message user ID
     */
    public String getMessageUserId() {
        return messageUserId;
    }

    /**
     * Returns the list of suggested patient-provider relationships for this provider.
     *
     * @return ArrayList&lt;Map&lt;String, String&gt;&gt; list of relationship attribute maps
     */
    public ArrayList<Map<String, String>> getSuggestedRelationships() {
        return suggestedRelationships;
    }

    /**
     * Parses all filter child nodes from the given XML node into a list of filter definitions.
     *
     * @param node Node the parent XML node containing filter child elements
     * @return List&lt;FilterDefinition&gt; the parsed filter definitions
     */
    public static List<FilterDefinition> getFilterArray(Node node) {
        List<Node> filterNodes = XmlUtils.getChildNodes(node, "filter");
        List<FilterDefinition> returnVal = new ArrayList<FilterDefinition>();
        for (Node n : filterNodes) {
            returnVal.add(FilterDefinition.fromXml(n));
        }
        return returnVal;
    }

    /**
     * Creates a {@code Provider} from an XML node including team members, filters,
     * allowed appointments, and suggested relationships.
     *
     * @param node Node the XML node representing this provider
     * @return Provider the parsed provider
     */
    public static Provider fromXml(Node node) {
        Provider provider = new Provider();
        provider.providerNo = XmlUtils.getAttributeValue(node, "providerNo");
        provider.role = XmlUtils.getAttributeValue(node, "role");
        List<Node> apptNodes = XmlUtils.getChildNodes(node, "allowed_appointment");
        provider.messageUserId = XmlUtils.getChildNodeTextContents(node, "messageUserId");

        provider.filters = getFilterArray(node);

        provider.appointmentTypes = getAllowedAppointments(apptNodes);
        provider.appointmentDurations = getAppointmentDurations(apptNodes);

        provider.teamMembers = new ArrayList<Provider>();
        if (XmlUtils.getChildNode(node, "team") != null) {
            List<Node> members = XmlUtils.getChildNodes(XmlUtils.getChildNode(node, "team"), "member");
            for (Node memberNode : members) {
                Provider providerMember = new Provider();
                providerMember.providerNo = XmlUtils.getAttributeValue(memberNode, "providerNo");
                providerMember.role = XmlUtils.getAttributeValue(memberNode, "role");
                List<Node> memberApptNodes = XmlUtils.getChildNodes(memberNode, "allowed_appointment");

                if (XmlUtils.getChildNodesTextContents(memberNode, "filter") != null && XmlUtils.getChildNodesTextContents(memberNode, "filter").size() > 0) {
                    providerMember.filters = getFilterArray(memberNode);
                } else {
                    providerMember.filters = getFilterArray(node);
                }

                providerMember.appointmentTypes = getAllowedAppointments(memberApptNodes);
                providerMember.appointmentDurations = getAppointmentDurations(memberApptNodes);
                provider.teamMembers.add(providerMember);
            }
        }

        provider.suggestedRelationships = new ArrayList<Map<String, String>>();
        List<Node> userForRelationshipNodes = XmlUtils.getChildNodes(node, "suggestedRelationship");
        for (Node userForRelationship : userForRelationshipNodes) {
            NamedNodeMap attributes = userForRelationship.getAttributes();
            Map<String, String> attMap = new HashMap<String, String>();
            if (attributes != null) {
                for (int i = 0; i < attributes.getLength(); i++) {
                    String attName = attributes.item(i).getNodeName();
                    String attValue = attributes.item(i).getNodeValue();
                    attMap.put(attName, attValue);
                }
                provider.suggestedRelationships.add(attMap);
            }
        }

        return provider;
    }

    private static Map<Long, Integer> getAppointmentDurations(List<Node> apptNodes) {
        Map<Long, Integer> map = new HashMap<Long, Integer>();
        for (Node allowedAppointment : apptNodes) {
            String apptId = null;
            String apptDuration = null;
            try {
                apptId = XmlUtils.getAttributeValue(allowedAppointment, "id");
                apptDuration = XmlUtils.getAttributeValue(allowedAppointment, "duration");
                if (apptId != null && apptDuration != null) {
                    map.put(Long.parseLong(apptId), Integer.parseInt(apptDuration));
                }
            } catch (Exception e) {
                logger.debug("Not a Integer  id " + apptId + " dur " + apptDuration, e);
            }
        }
        return map;
    }

    private static Map<String, Character[]> getAllowedAppointments(List<Node> apptNodes) {
        Map<String, Character[]> map = new HashMap<String, Character[]>();

        for (Node allowedAppointment : apptNodes) {
            String apptId = XmlUtils.getAttributeValue(allowedAppointment, "id");
            String apptCodesTemp = XmlUtils.getAttributeValue(allowedAppointment, "appointment_codes");
            String[] tempSplit = apptCodesTemp.split(",");

            Character[] allowableTimeCodes = new Character[tempSplit.length];
            int count = 0;
            for (String s : tempSplit) {
                s = StringUtils.trimToNull(s);
                if (s != null && s.length() > 0) {
                    allowableTimeCodes[count] = s.charAt(0);
                    count++;
                }
            }
            Arrays.sort(allowableTimeCodes);
            map.put(apptId, allowableTimeCodes);
        }
        return map;
    }

    /**
     * Returns the provider's last name.
     *
     * @return String the last name
     */
    public String getLastName() {
        return lastName;
    }

    /**
     * Sets the provider's last name.
     *
     * @param lastName String the last name
     */
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    /**
     * Returns the provider's first name.
     *
     * @return String the first name
     */
    public String getFirstName() {
        return firstName;
    }

    /**
     * Sets the provider's first name.
     *
     * @param firstName String the first name
     */
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    /**
     * Creates a {@code Provider} from a REST booking provider transfer object,
     * including team members and filter configuration from the transfer's flags.
     *
     * @param provider BookingProviderTransfer the transfer object
     * @return Provider the converted provider
     */
    public static Provider fromProvider(BookingProviderTransfer provider) {
        Provider returnProvider = new Provider();
        returnProvider.appointmentDurations = new HashMap<Long, Integer>();
        returnProvider.appointmentTypes = new HashMap<String, Character[]>();
        List<AllowedAppointmentTypeTransfer> allowedAppts = provider.getAppointmentTypes();
        for (AllowedAppointmentTypeTransfer allowedAppt : allowedAppts) {
            returnProvider.appointmentTypes.put("" + allowedAppt.getId(), allowedAppt.getCodes());
            if (allowedAppt.getDuration() > 0) {
                returnProvider.appointmentDurations.put(allowedAppt.getId(), allowedAppt.getDuration());
            }
        }

        returnProvider.messageUserId = provider.getMessageUserId();
        returnProvider.providerNo = provider.getProviderNo();
        returnProvider.teamMembers = new ArrayList<Provider>();
        if (provider.getTeamMembers() != null) {
            for (BookingProviderTransfer p : provider.getTeamMembers()) {
                returnProvider.teamMembers.add(Provider.fromProvider(p));
            }
        }
        returnProvider.filters = new ArrayList<FilterDefinition>();
        if (provider.isFilterFAF()) {
            FilterDefinition fd = new FilterDefinition();
            fd.setFilterClassName("io.github.carlos_emr.carlos.appointment.search.filters.FutureApptFilter");
            Map<String, String> params = new HashMap<String, String>();
            params.put("buffer", "" + provider.getFilterFAFbuffer());
            fd.setParams(params);
            returnProvider.filters.add(fd);
        }
        if (provider.isFilterEAF()) {
            FilterDefinition fd = new FilterDefinition();
            fd.setFilterClassName("io.github.carlos_emr.carlos.appointment.search.filters.ExistingAppointmentFilter");
            returnProvider.filters.add(fd);
        }
        if (provider.isFilterMUF()) {
            FilterDefinition fd = new FilterDefinition();
            fd.setFilterClassName("io.github.carlos_emr.carlos.appointment.search.filters.MultiUnitFilter");
            returnProvider.filters.add(fd);
        }
        if (provider.isFilterOAF()) {
            FilterDefinition fd = new FilterDefinition();
            fd.setFilterClassName("io.github.carlos_emr.carlos.appointment.search.filters.OpenAccessFilter");
            Map<String, String> params = new HashMap<String, String>();
            params.put("codes", "" + provider.getFilterOAFCodes());
            fd.setParams(params);
            returnProvider.filters.add(fd);
        }
        if (provider.isFilterSCTF()) {
            FilterDefinition fd = new FilterDefinition();
            fd.setFilterClassName("io.github.carlos_emr.carlos.appointment.search.filters.SufficientContiguousTimeFilter");
            returnProvider.filters.add(fd);
        }

        return returnProvider;
    }
}
