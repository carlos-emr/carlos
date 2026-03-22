package io.github.carlos_emr.carlos.appointment.search;
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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AppointmentTypeTransfer;
import io.github.carlos_emr.carlos.webserv.rest.to.model.BookingProviderTransfer;
import io.github.carlos_emr.carlos.webserv.rest.to.model.BookingScheduleTemplateCodeTransfer;
import io.github.carlos_emr.carlos.webserv.rest.to.model.SearchConfigTo1;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


/**
 * Central configuration for the online appointment booking search engine.
 *
 * <p>Holds all settings needed to search for available appointment slots, including
 * provider definitions, appointment types, schedule template code durations, time slot
 * filters, encryption keys for secure booking references, timezone, and pagination limits.
 * Configurations can be serialized to/from XML documents and converted from REST transfer objects.</p>
 *
 * @since 2026-03-17
 */
public class SearchConfig {
    protected static Logger logger = MiscUtils.getLogger();

    private int daysToSearchAheadLimit = 10; //Number of days it searches before giving up. ie search for the next 60 days before giving up
    private int numberOfAppointmentOptionsToReturn = 10; //Number of appts that seems like it gives a reasonable choice.
    private SecretKey secretKey = null;


    private List<AppointmentType> appointmentTypes = null;

    private Map<Character, Integer> appointmentCodeDurations = null;
    private Map<String, Provider> providers = null;
    private List<Provider> bookingProviders = null; //needed for www service delete me
    private String timezone = "America/Toronto";
    private int defaultAppointmentCount = 1;
    private List<FilterDefinition> filters = null;
    private String appointmentLocation = null;
    private String title;

    /**
     * Returns the providers and their allowed schedule codes for a given appointment type,
     * including the specified provider and any of their team members.
     *
     * @param demographicNo Integer the patient demographic number (currently unused)
     * @param appointmentTypeId Long the appointment type to look up
     * @param providerNo String the primary provider number (MRP)
     * @return Map&lt;Provider, Character[]&gt; map of providers to their allowed schedule codes
     */
    public Map<Provider, Character[]> getProvidersForAppointmentType(Integer demographicNo, Long appointmentTypeId, String providerNo) {

        Map<Provider, Character[]> map = new HashMap<Provider, Character[]>();
        Provider provider = providers.get(providerNo);
        Character[] codes = provider.appointmentTypes.get("" + appointmentTypeId);

        if (codes != null && codes.length > 0) {
            map.put(provider, codes);
        }

        for (Provider teamMember : provider.getTeamMembers()) {
            Character[] codes2 = teamMember.appointmentTypes.get("" + appointmentTypeId);
            if (codes2 != null && codes2.length > 0) {
                map.put(teamMember, codes2);
            }
        }
        return map;
    }

    //Delete me
    public void moveBooking2Provider() {
        if (bookingProviders != null) {
            for (Provider provider : bookingProviders) {
                providers.put(provider.getProviderNo(), provider);
            }
        }

    }

    /**
     * Returns the display title for this search configuration.
     *
     * @return String the configuration title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the maximum number of days to search ahead for available appointments.
     *
     * @return int the search-ahead day limit
     */
    public int getDaysToSearchAheadLimit() {
        return daysToSearchAheadLimit;
    }

    /**
     * Sets the maximum number of days to search ahead for available appointments.
     *
     * @param daysToSearchAheadLimit int the search-ahead day limit
     */
    public void setDaysToSearchAheadLimit(int daysToSearchAheadLimit) {
        this.daysToSearchAheadLimit = daysToSearchAheadLimit;
    }

    /**
     * Returns the number of appointment options to return per search result page.
     *
     * @return int the number of options to return
     */
    public int getNumberOfAppointmentOptionsToReturn() {
        return numberOfAppointmentOptionsToReturn;
    }

    /**
     * Sets the number of appointment options to return per search result page.
     *
     * @param numberOfAppointmentOptionsToReturn int the number of options to return
     */
    public void setNumberOfAppointmentOptionsToReturn(int numberOfAppointmentOptionsToReturn) {
        this.numberOfAppointmentOptionsToReturn = numberOfAppointmentOptionsToReturn;
    }


    /**
     * Returns the appointment duration in minutes, checking the MRP's own settings
     * and their team members' overrides before falling back to the default code duration.
     *
     * @param mrp String the Most Responsible Provider number
     * @param provider String the provider number to resolve the duration for
     * @param appointmentId Long the appointment type ID
     * @param code Character the schedule template code
     * @return Integer the duration in minutes
     */
    public Integer getAppointmentDuration(String mrp, String provider, Long appointmentId, Character code) {
        Integer appointmentDuration = appointmentCodeDurations.get(code);
        if (mrp != null && mrp.equals(provider)) {
            appointmentDuration = getAppointmentDuration(provider, appointmentId, code);
        } else {
            if (providers.get(mrp) != null) {
                Provider p = providers.get(mrp);
                for (Provider teamMember : p.getTeamMembers()) {
                    if (provider.equals(teamMember.getProviderNo()) && teamMember.appointmentDurations.get(appointmentId) != null) {
                        appointmentDuration = teamMember.getAppointmentDurations().get(appointmentId);
                    }
                }
            }
        }
        return appointmentDuration;
    }

    /**
     * Returns the appointment duration for a single provider, checking provider-specific
     * overrides before falling back to the default code duration.
     *
     * <p>Note: This method does not check team member overrides.</p>
     *
     * @param provider String the provider number
     * @param appointmentId Long the appointment type ID
     * @param code Character the schedule template code
     * @return Integer the duration in minutes
     */
    public Integer getAppointmentDuration(String provider, Long appointmentId, Character code) {
        Integer appointmentDuration = appointmentCodeDurations.get(code);
        if (providers.get(provider) != null && providers.get(provider).getAppointmentDurations() != null && providers.get(provider).getAppointmentDurations().get(appointmentId) != null) {
            appointmentDuration = providers.get(provider).getAppointmentDurations().get(appointmentId);
        }
        return appointmentDuration;
    }

    /**
     * Looks up a provider by the MRP's provider number, checking the MRP first and
     * then their team members.
     *
     * @param mrp String the Most Responsible Provider number
     * @param providerNo String the provider number to find
     * @return Provider the matching provider, or {@code null} if not found
     */
    public Provider getProvider(String mrp, String providerNo) {
        if (mrp != null && providers.get(mrp) != null) {
            Provider p = providers.get(mrp);
            if (p.getProviderNo().equals(providerNo)) {
                return p;
            }
            //If the mrp isn't what is being requested, check their team
            for (Provider teamMember : p.getTeamMembers()) {
                if (teamMember.getProviderNo().equals(providerNo)) {
                    return teamMember;
                }
            }
        }
        return null;
    }

    /**
     * Returns the global filter definitions applied to appointment searches.
     *
     * @return List&lt;FilterDefinition&gt; the filter definitions, or {@code null} if none
     */
    public List<FilterDefinition> getFilter() {
        return filters;
    }


    /**
     * Returns the advance booking buffer in minutes from the FutureApptFilter configuration.
     *
     * @return Integer the number of minutes of advance buffer, or {@code null} if not configured
     */
    public Integer getNumberOfMinutesAdvance() {
        if (filters != null) {
            for (FilterDefinition fd : filters) {
                if ("io.github.carlos_emr.carlos.appointment.search.filters.FutureApptFilter".equals(fd.getFilterClassName())) {
                    String str = fd.getParams().get("buffer");
                    try {
                        return Integer.parseInt(str);
                    } catch (Exception e) {
                        logger.error("buffer attribute should be an integer:" + str, e);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Sets the advance booking buffer in minutes on the FutureApptFilter configuration.
     *
     * @param minutes Integer the number of minutes of advance buffer
     */
    public void setNumberOfMinutesAdvance(Integer minutes) {
        if (filters != null) {
            for (FilterDefinition fd : filters) {
                if ("io.github.carlos_emr.carlos.appointment.search.filters.FutureApptFilter".equals(fd.getFilterClassName())) {
                    Map<String, String> params = new HashMap<String, String>();
                    params.put("buffer", "" + minutes);
                    fd.setParams(params);
                }
            }
        }
    }

    /**
     * Returns the set of all configured provider numbers.
     *
     * @return Set&lt;String&gt; the provider numbers
     */
    public Set<String> getProviderNo() {
        return providers.keySet();
    }

    /**
     * Returns the sorted array of open access schedule template codes from the OpenAccessFilter.
     *
     * @return Character[] the sorted open access codes, or {@code null} if not configured
     */
    public Character[] getOpenAccessCodes() {
        if (filters != null) {
            for (FilterDefinition fd : filters) {
                if ("io.github.carlos_emr.carlos.appointment.search.filters.OpenAccessFilter".equals(fd.getFilterClassName())) {
                    String str = fd.getParams().get("codes");
                    try {
                        Character[] retval = getCharArray(str);
                        Arrays.sort(retval);
                        return retval;
                    } catch (Exception e) {
                        logger.error("buffer attribute should be an integer:" + str, e);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Sets the open access schedule template codes on the OpenAccessFilter.
     *
     * @param openAccessCodes Character[] the open access codes
     */
    public void setOpenAccessCodes(Character[] openAccessCodes) {
        if (filters != null) {
            for (FilterDefinition fd : filters) {
                if ("io.github.carlos_emr.carlos.appointment.search.filters.OpenAccessFilter".equals(fd.getFilterClassName())) {
                    Map<String, String> params = new HashMap<String, String>();
                    params.put("codes", StringUtils.join(openAccessCodes, ","));
                    fd.setParams(params);
                }
            }
        }
    }

    /////

    /**
     * Serializes this search configuration to an XML {@code Document}.
     *
     * @param clinic SearchConfig the configuration to serialize
     * @return Document the XML document representation
     * @throws Exception if XML creation fails
     */
    public static Document toDocument(SearchConfig clinic) throws Exception {

        Document doc = XmlUtils.newDocument("clinic");
        //doc.getDocumentElement().setAttribute("name",clinic.getName());

        XmlUtils.appendChildToRoot(doc, "defaultAppointmentCount", Integer.toString(clinic.getDefaultAppointmentCount()));

        XmlUtils.appendChildToRoot(doc, "timezone", clinic.getTimezone());
        XmlUtils.appendChildToRoot(doc, "title", clinic.getTitle());


        XmlUtils.appendChildToRoot(doc, "daysToSearchAheadLimit", "" + clinic.daysToSearchAheadLimit);
        XmlUtils.appendChildToRoot(doc, "numberOfAppointmentOptionsToReturn", "" + clinic.numberOfAppointmentOptionsToReturn);
        addChildIfNotNull(doc, "appointment_location", clinic.appointmentLocation);


        if (clinic.getAppointmentTypes() != null) {
            for (AppointmentType aType : clinic.getAppointmentTypes()) {
                Element appointmentType = doc.createElement("appointment_type");
                appointmentType.setAttribute("id", "" + aType.getId());
                appointmentType.setAttribute("name", aType.getName());
                appointmentType.setAttribute("mappingOscarApptType", "" + aType.getMappingOscarApptType());
                doc.getFirstChild().appendChild(appointmentType);
            }
        }

        logger.error("toDocument " + clinic.appointmentCodeDurations);
        if (clinic.appointmentCodeDurations != null) {
            for (Map.Entry<Character, Integer> ent : clinic.appointmentCodeDurations.entrySet()) {
                Element appointmentCode = doc.createElement("appointment_code");
                appointmentCode.setAttribute("code", "" + ent.getKey());
                appointmentCode.setAttribute("duration", "" + ent.getValue());
                doc.getFirstChild().appendChild(appointmentCode);
            }
        }

        if (clinic.getFilter() != null && clinic.getFilter().size() > 0) {
            Element filters = doc.createElement("filters");
            for (FilterDefinition fd : clinic.filters) {
                Element filter = doc.createElement("filter");
                filter.setTextContent(fd.getFilterClassName());
                for (Map.Entry<String, String> fdParams : fd.getParams().entrySet()) {
                    filter.setAttribute(fdParams.getKey(), fdParams.getValue());
                }
                filters.appendChild(filter);
            }
            doc.getFirstChild().appendChild(filters);
        }

        if (clinic.providers != null) {
            for (Map.Entry<String, Provider> ent : clinic.providers.entrySet()) {
                Element allowedProvider = doc.createElement("allowedProvider");
                allowedProvider.setAttribute("providerNo", ent.getKey());
                Provider p = ent.getValue();
                for (Map.Entry<String, Character[]> apptTypeEnt : p.appointmentTypes.entrySet()) {
                    Element allowedAppt = doc.createElement("allowed_appointment");
                    allowedAppt.setAttribute("id", apptTypeEnt.getKey());
                    allowedAppt.setAttribute("appointment_codes", StringUtils.join(apptTypeEnt.getValue(), ","));
                    Integer duration = p.getAppointmentDurations().get(Long.parseLong(apptTypeEnt.getKey()));
                    if (duration != null) {
                        allowedAppt.setAttribute("duration", "" + duration);
                    }
                    allowedProvider.appendChild(allowedAppt);

                }

                for (FilterDefinition fd : p.filters) {
                    Element filter = doc.createElement("filter");
                    filter.setTextContent(fd.getFilterClassName());
                    for (Map.Entry<String, String> fdParams : fd.getParams().entrySet()) {
                        filter.setAttribute(fdParams.getKey(), fdParams.getValue());
                    }
                    allowedProvider.appendChild(filter);
                }

                if (p.getTeamMembers() != null && p.getTeamMembers().size() > 0) {
                    Element team = doc.createElement("team");
                    for (Provider m : p.getTeamMembers()) {
                        Element teamProvider = doc.createElement("member");
                        teamProvider.setAttribute("providerNo", m.getProviderNo());
                        allowedAppt(doc, teamProvider, m.appointmentTypes.entrySet(), m.appointmentDurations);
                        team.appendChild(teamProvider);
                    }
                    allowedProvider.appendChild(team);
                }

                doc.getFirstChild().appendChild(allowedProvider);
            }
        }

        return doc;
    }


    /**
     * Deserializes a search configuration from an XML {@code Document}.
     *
     * @param doc Document the XML document to parse
     * @return SearchConfig the deserialized configuration
     * @throws Exception if XML parsing fails
     */
    public static SearchConfig fromDocument(Document doc) throws Exception {
        SearchConfig returnClinic = new SearchConfig();

        Node rootNode = doc.getFirstChild();
        //returnClinic.name = XmlUtils.getAttributeValue(rootNode, "name");
        returnClinic.timezone = XmlUtils.getChildNodeTextContents(rootNode, "timezone");
        returnClinic.appointmentLocation = XmlUtils.getChildNodeTextContents(rootNode, "appointment_location");
        returnClinic.title = XmlUtils.getChildNodeTextContents(rootNode, "title");

        String daysToSearchAheadLimitStr = XmlUtils.getChildNodeTextContents(rootNode, "daysToSearchAheadLimit");
        if (daysToSearchAheadLimitStr != null) {
            try {
                returnClinic.daysToSearchAheadLimit = Integer.parseInt(daysToSearchAheadLimitStr);
            } catch (Exception daysToSearchAheadLimitException) {
                logger.error("Error parsing daysToSearchAheadLimit" + daysToSearchAheadLimitStr, daysToSearchAheadLimitException);
            }
        }

        String numberOfAppointmentOptionsToReturnStr = XmlUtils.getChildNodeTextContents(rootNode, "numberOfAppointmentOptionsToReturn");
        if (numberOfAppointmentOptionsToReturnStr != null) {
            try {
                returnClinic.numberOfAppointmentOptionsToReturn = Integer.parseInt(numberOfAppointmentOptionsToReturnStr);
            } catch (Exception numberOfAppointmentOptionsToReturnException) {
                logger.error("Error parsing numberOfAppointmentOptionsToReturn" + numberOfAppointmentOptionsToReturnStr, numberOfAppointmentOptionsToReturnException);
            }
        }


        String defaultAppointmentCount = XmlUtils.getChildNodeTextContents(rootNode, "defaultAppointmentCount");
        if (defaultAppointmentCount != null) {
            try {
                returnClinic.defaultAppointmentCount = Integer.parseInt(defaultAppointmentCount);
            } catch (Exception defaultAppointmentCountException) {
                logger.error("Error parsing defaultAppointmentCount " + defaultAppointmentCount, defaultAppointmentCountException);
            }
        }


        returnClinic.appointmentTypes = new ArrayList<AppointmentType>();
        List<Node> apptNodes = XmlUtils.getChildNodes(rootNode, "appointment_type");
        for (Node apptNode : apptNodes) {
            returnClinic.appointmentTypes.add(AppointmentType.fromXml2(apptNode));
        }


        Node filterNode = XmlUtils.getChildNode(rootNode, "filters");
        if (filterNode != null) {
            returnClinic.filters = Provider.getFilterArray(filterNode);
        }

        returnClinic.appointmentCodeDurations = new HashMap<Character, Integer>();
        List<Node> apptCodeNodes = XmlUtils.getChildNodes(rootNode, "appointment_code");
        for (Node apptCodeNode : apptCodeNodes) {
            Integer duration = Integer.parseInt(XmlUtils.getAttributeValue(apptCodeNode, "duration"));
            String s = XmlUtils.getAttributeValue(apptCodeNode, "code");
            s = StringUtils.trimToNull(s);
            if (s != null && s.length() > 0) {
                returnClinic.appointmentCodeDurations.put(s.charAt(0), duration);
                String openAccessStr = XmlUtils.getAttributeValue(apptCodeNode, "openaccess");
                if (openAccessStr != null && "true".equals(openAccessStr)) {
                    getCharArray(openAccessStr);
                }
            }
        }

        returnClinic.providers = new HashMap<String, Provider>();
        List<Node> providerNodes = XmlUtils.getChildNodes(rootNode, "allowedProvider");
        for (Node providerNode : providerNodes) {
            Provider provider = Provider.fromXml(providerNode);
            returnClinic.providers.put(provider.getProviderNo(), provider);
        }
        //returnClinic.secretKey = EncryptionUtils.generateEncryptionKey();

        return returnClinic;
    }

    private static Character[] getCharArray(String toSplit) {
        String[] splitStr = toSplit.split(",");
        Character[] retval = new Character[splitStr.length];
        int i = 0;
        for (String c : splitStr) {
            retval[i] = c.charAt(0);
            i++;
        }
        return retval;
    }

    private static void allowedAppt(Document doc, Element elementToAppend, Set<Map.Entry<String, Character[]>> allowedApptSet, Map<Long, Integer> extDurations) {
        for (Map.Entry<String, Character[]> apptTypeEnt : allowedApptSet) {
            Element allowedAppt = doc.createElement("allowed_appointment");
            allowedAppt.setAttribute("id", apptTypeEnt.getKey());
            allowedAppt.setAttribute("appointment_codes", StringUtils.join(apptTypeEnt.getValue(), ","));
            Integer duration = extDurations.get(Long.parseLong(apptTypeEnt.getKey()));
            if (duration != null) {
                allowedAppt.setAttribute("duration", "" + duration);
            }
            elementToAppend.appendChild(allowedAppt);
        }
    }

    private static void addChildIfNotNull(Document doc, String nodeName, String textValue) {
        if (textValue != null) {
            XmlUtils.appendChildToRoot(doc, nodeName, textValue);
        }
    }
    /////


    /**
     * Creates a new {@code SearchConfig} from a REST transfer object, falling back to
     * the old configuration for fields that cannot be parsed.
     *
     * @param clinicTransfer SearchConfigTo1 the transfer object with updated settings
     * @param oldClinic SearchConfig the previous configuration used as fallback
     * @return SearchConfig the new configuration
     */
    public static SearchConfig fromTransfer(SearchConfigTo1 clinicTransfer, SearchConfig oldClinic) {
        logger.debug("clinicTransfer:" + clinicTransfer);
        SearchConfig returnClinic = new SearchConfig();
        returnClinic.timezone = clinicTransfer.getTimezone();
        returnClinic.daysToSearchAheadLimit = clinicTransfer.getDaysToSearchAheadLimit();
        returnClinic.numberOfAppointmentOptionsToReturn = clinicTransfer.getNumberOfAppointmentOptionsToReturn();
        returnClinic.appointmentLocation = clinicTransfer.getAppointmentLocation();
        returnClinic.filters = new ArrayList<FilterDefinition>();
        returnClinic.defaultAppointmentCount = clinicTransfer.getDefaultAppointmentCount();
        returnClinic.title = clinicTransfer.getTitle();

        if (clinicTransfer.getNumberOfMinutesAdvance() != null && clinicTransfer.getNumberOfMinutesAdvance() > 0) {
            FilterDefinition fd = new FilterDefinition();
            fd.setFilterClassName("io.github.carlos_emr.carlos.appointment.search.filters.FutureApptFilter");
            Map<String, String> params = new HashMap<String, String>();
            params.put("buffer", "" + clinicTransfer.getNumberOfMinutesAdvance());
            fd.setParams(params);
            returnClinic.filters.add(fd);
        }

        try {
            List<AppointmentTypeTransfer> bookingAppointmentTypes = clinicTransfer.getBookingAppointmentTypes();
            returnClinic.appointmentTypes = new ArrayList<AppointmentType>();
            for (AppointmentTypeTransfer apptNode : bookingAppointmentTypes) {
                logger.debug("adding appt type");
                returnClinic.appointmentTypes.add(AppointmentType.fromAppointmentTypeTransfer(apptNode));
            }
        } catch (Exception e) {
            logger.error("error processing appointmentTypes");
            returnClinic.appointmentTypes = oldClinic.appointmentTypes;
        }

        returnClinic.providers = new HashMap<String, Provider>();
        List<BookingProviderTransfer> bookingProviders = clinicTransfer.getBookingProviders();
        for (BookingProviderTransfer bp : bookingProviders) {
            Provider provider = Provider.fromProvider(bp);
            returnClinic.providers.put(provider.getProviderNo(), provider);
        }

        returnClinic.appointmentCodeDurations = clinicTransfer.getAppointmentCodeDurations();
        List<BookingScheduleTemplateCodeTransfer> bookingCodeList = clinicTransfer.getApptCodes();
		/* Check if this is not needed... it seems the above codes receives the hashmap instead of creating it.
		  if(bookingCodeList != null) {
			returnClinic.appointmentCodeDurations = new HashMap<Character,Integer>();
			List<Character> openAccessList = new ArrayList<Character>();
			for(BookingScheduleTemplateCodeTransfer code:bookingCodeList){
				if(code.isOnlineBooking()){
					logger.info("Adding Code"+code.getCode()+ " with dur "+code.getDuration());
					returnClinic.appointmentCodeDurations.put(code.getCode(), Integer.parseInt(code.getDuration()));
				}else{
					logger.info("Not Adding Code"+code.getCode() );
					
				}
				if(code.isOpenAccess()){
					openAccessList.add(code.getCode());
				}
			}
			
			if(openAccessList.size() > 0){
				FilterDefinition fd = new FilterDefinition();
				fd.setFilterClassName("io.github.carlos_emr.carlos.appointment.search.filters.OpenAccessFilter");
				returnClinic.filters.add(fd);
			}
			returnClinic.setOpenAccessCodes(openAccessList.toArray(new Character[openAccessList.size()]));
		} else {
			logger.debug("booking code List size is null just going with what was there ");
			returnClinic.appointmentCodeDurations = oldClinic.appointmentCodeDurations;
		}
		*/

        if (clinicTransfer.getOpenAccessList() != null && clinicTransfer.getOpenAccessList().size() > 0) {
            FilterDefinition fd = new FilterDefinition();
            fd.setFilterClassName("io.github.carlos_emr.carlos.appointment.search.filters.OpenAccessFilter");
            returnClinic.filters.add(fd);
            returnClinic.setOpenAccessCodes(clinicTransfer.getOpenAccessList().toArray(new Character[clinicTransfer.getOpenAccessList().size()]));
        }


        FilterDefinition fdExistingApp = new FilterDefinition();
        fdExistingApp.setFilterClassName("io.github.carlos_emr.carlos.appointment.search.filters.ExistingAppointmentFilter");
        returnClinic.filters.add(fdExistingApp);

        FilterDefinition fdMultiUnit = new FilterDefinition();
        fdMultiUnit.setFilterClassName("io.github.carlos_emr.carlos.appointment.search.filters.MultiUnitFilter");
        returnClinic.filters.add(fdMultiUnit);


        return returnClinic;
    }


    public List<AppointmentType> getAppointmentTypes() {
        return appointmentTypes;
    }


    public void setAppointmentTypes(List<AppointmentType> appointmentTypes) {
        this.appointmentTypes = appointmentTypes;
    }


    public Map<Character, Integer> getAppointmentCodeDurations() {
        return appointmentCodeDurations;
    }


    public void setAppointmentCodeDurations(Map<Character, Integer> appointmentCodeDurations) {
        this.appointmentCodeDurations = appointmentCodeDurations;
    }


    public Map<String, Provider> getProviders() {
        return providers;
    }


    public void setProviders(Map<String, Provider> providers) {
        this.providers = providers;
    }


    public String getTimezone() {
        return timezone;
    }


    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }


    public int getDefaultAppointmentCount() {
        return defaultAppointmentCount;
    }


    public void setDefaultAppointmentCount(int defaultAppointmentCount) {
        this.defaultAppointmentCount = defaultAppointmentCount;
    }


    public List<FilterDefinition> getFilters() {
        return filters;
    }


    public void setFilters(List<FilterDefinition> filters) {
        this.filters = filters;
    }


    public String getAppointmentLocation() {
        return appointmentLocation;
    }


    public void setAppointmentLocation(String appointmentLocation) {
        this.appointmentLocation = appointmentLocation;
    }


    public List<Provider> getBookingProviders() {
        return bookingProviders;
    }


    public void setBookingProviders(List<Provider> bookingProviders) {
        this.bookingProviders = bookingProviders;
    }

    /**
     * Generates a new AES-256 secret key for encrypting booking references.
     *
     * @throws Exception if key generation fails
     */
    public void genSecKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);

        secretKey = keyGenerator.generateKey();
    }

    /**
     * Encrypts the given string using AES with the configured secret key.
     *
     * @param toEncyrpt String the plaintext to encrypt
     * @return String the Base64-encoded encrypted string, or the original if no key is set
     * @throws Exception if encryption fails
     */
    public String encrypt(String toEncyrpt) throws Exception {
        if (secretKey == null) return toEncyrpt;
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        byte[] unencryptedByteArray = toEncyrpt.getBytes("UTF8");
        byte[] encryptedBytes = cipher.doFinal(unencryptedByteArray);
        byte[] encodedBytes = Base64.encodeBase64(encryptedBytes);

        return new String(encodedBytes);
    }

    /**
     * Decrypts the given Base64-encoded AES-encrypted string.
     *
     * @param toDecrypt String the Base64-encoded encrypted string
     * @return String the decrypted plaintext, or the original if no key is set
     * @throws Exception if decryption fails
     */
    public String decrypt(String toDecrypt) throws Exception {
        if (secretKey == null) return (toDecrypt);

        byte[] encryptedData = Base64.decodeBase64(toDecrypt.getBytes());
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] unencryptedByteArray = cipher.doFinal(encryptedData);
        return new String(unencryptedByteArray, "UTF8");
    }

    /**
     * Creates a {@code BookingType} by encrypting the appointment type ID and provider number
     * into a secure identifier.
     *
     * @param appointmentType AppointmentType the appointment type
     * @param providerNo String the provider number
     * @return BookingType the booking type with encrypted ID
     */
    public BookingType getBookingType(AppointmentType appointmentType, String providerNo) {
        BookingType bookingType = new BookingType();

        bookingType.setName(appointmentType.getName());
        String combinedString = appointmentType.getId() + ":" + providerNo;
        try {
            bookingType.setId(encrypt(combinedString));
        } catch (Exception e) {
            bookingType.setId("" + appointmentType.getId());
        }
        return bookingType;
    }

    /**
     * Decrypts a booking type ID string and extracts the appointment type ID.
     *
     * @param encryptedStr String the encrypted booking type ID
     * @return Long the appointment type ID, or {@code null} if decryption fails
     */
    public Long getAppointmentTypeId(String encryptedStr) {
        try {
            String combinedString = decrypt(encryptedStr);
            String[] combined = combinedString.split(":");
            return Long.parseLong(combined[0]);
        } catch (Exception e) {
            logger.error("error getting appointment ID", e);
        }
        return null;
    }

    /**
     * Encrypts a time slot into a secure booking reference string containing the
     * date, provider, appointment type, schedule code, timezone, and demographic number.
     *
     * @param toEncyrpt TimeSlot the time slot to encrypt
     * @return String the encrypted booking reference
     * @throws Exception if encryption fails
     */
    public String encrypt(TimeSlot toEncyrpt) throws Exception {

        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmssZ");
        formatter.setTimeZone(toEncyrpt.getAvailableApptTime().getTimeZone());
        String combinedString = formatter.format(toEncyrpt.getAvailableApptTime().getTime()) + ":" + toEncyrpt.getProviderNo() + ":" + toEncyrpt.getAppointmentType() + ":" + toEncyrpt.getCode() + ":" + toEncyrpt.getAvailableApptTime().getTimeZone().getID() + ":" + toEncyrpt.getDemographicNo();
        return encrypt(combinedString);
    }

    /**
     * Decrypts an encrypted booking reference string back into a {@code TimeSlot}.
     *
     * @param toDecrypt String the encrypted booking reference
     * @return TimeSlot the reconstructed time slot
     * @throws Exception if decryption or parsing fails
     */
    public TimeSlot decryptTimeSlot(String toDecrypt) throws Exception {
        TimeSlot timeslot = new TimeSlot();

        String combinedString = decrypt(toDecrypt);
        String[] combined = combinedString.split(":");

        DateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmssZ");
        Date date = formatter.parse(combined[0]);
        Calendar cal = new GregorianCalendar();
        cal.setTime(date);
        cal.setTimeZone(TimeZone.getTimeZone(combined[4]));

        String providerId = combined[1];
        Long appointmentTypeId = Long.parseLong(combined[2]);

        timeslot.setAvailableApptTime(cal);
        timeslot.setProviderNo(providerId);
        timeslot.setAppointmentType(appointmentTypeId);
        if (combined[3].length() > 0) {
            Character code = combined[3].charAt(0);
            timeslot.setCode(code);
        }
        try {
            timeslot.setDemographicNo(Integer.parseInt(combined[5]));
        } catch (Exception e) {
            logger.error("Error parsing demo", e);
        }
        return timeslot;

    }

    /**
     * Looks up an appointment type by its ID.
     *
     * @param appointmentTypeId Long the appointment type ID to find
     * @return AppointmentType the matching appointment type, or {@code null} if not found
     */
    public AppointmentType getAppointmentType(Long appointmentTypeId) {
        for (AppointmentType appointmentType : appointmentTypes) {
            if (appointmentType.getId() == appointmentTypeId.longValue()) {
                return appointmentType;
            }
        }
        return null;
    }

}
