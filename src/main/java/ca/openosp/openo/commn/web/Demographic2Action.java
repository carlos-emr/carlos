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
package ca.openosp.openo.commn.web;

import com.opensymphony.xwork2.ActionSupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.commons.io.IOUtils;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.commn.dao.DemographicArchiveDao;
import ca.openosp.openo.commn.dao.DemographicDao;
import ca.openosp.openo.commn.dao.DemographicExtArchiveDao;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.DemographicArchive;
import ca.openosp.openo.commn.model.DemographicExtArchive;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.form.JSONUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.List;

public class Demographic2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
    private DemographicArchiveDao demographicArchiveDao = SpringUtils.getBean(DemographicArchiveDao.class);
    private DemographicExtArchiveDao demographicExtArchiveDao = SpringUtils.getBean(DemographicExtArchiveDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String execute() throws Exception {
        String method = request.getParameter("method");
        if ("getSubdivisionCodes".equals(method)) {
            return getSubdivisionCodes();
        } else if ("getCountryAndProvinceCodes".equals(method)) {
            return getCountryAndProvinceCodes();
        } else if ("getAddressAndPhoneHistoryAsJson".equals(method)) {
            return getAddressAndPhoneHistoryAsJson();
        } else if ("checkForDuplicates".equals(method)) {
            return checkForDuplicates();
        }
        return getSubdivisionCodes();
    }

    public String getSubdivisionCodes()
            throws Exception {

        String selectedCountry = request.getParameter("country");

        ArrayNode results = objectMapper.createArrayNode();
        ObjectNode obj = null;

        try {
            InputStream in = this.getClass().getClassLoader()
                    .getResourceAsStream("iso-3166-2.json");
            String theString = IOUtils.toString(in, "UTF-8");
            obj = (ObjectNode) objectMapper.readTree(theString);
        } catch (Exception e) {
            MiscUtils.getLogger().warn("Warning", e);
        }

        if (obj != null) {
            if (selectedCountry == null) {
                Iterator<String> iter = obj.fieldNames();
                while (iter.hasNext()) {
                    String countryCode = iter.next();
                    String countryName = ((ObjectNode) obj.get(countryCode)).get("name").asText();
                    ObjectNode r = objectMapper.createObjectNode();
                    r.put("value", countryCode);
                    r.put("label", countryName);
                    results.add(r);
                }
            } else {
                ObjectNode country = (ObjectNode) obj.get(selectedCountry);
                ObjectNode divisions = (ObjectNode) country.get("divisions");
                Iterator<String> iter = divisions.fieldNames();
                while (iter.hasNext()) {
                    String divisionCode = iter.next();
                    String divisionName = divisions.get(divisionCode).asText();
                    ObjectNode r = objectMapper.createObjectNode();
                    r.put("value", divisionCode);
                    r.put("label", divisionName);
                    results.add(r);
                }
            }
        }

        response.getWriter().write(results.toString());

        return null;
    }

    public String getCountryAndProvinceCodes()
            throws Exception {

        String selectedCountry = request.getParameter("country");
        //	String selectedSubDivision = request.getParameter("subdividion");

        ArrayNode results = objectMapper.createArrayNode();

        ObjectNode obj = null;
        try {
            InputStream in = this.getClass().getClassLoader()
                    .getResourceAsStream("iso-3166-2.json");
            String theString = IOUtils.toString(in, "UTF-8");
            obj = (ObjectNode) objectMapper.readTree(theString);
        } catch (Exception e) {
            MiscUtils.getLogger().warn("Warning", e);
        }

        if (obj != null) {

            if (selectedCountry == null) {
                Iterator<String> iter = obj.fieldNames();
                while (iter.hasNext()) {
                    String countryCode = iter.next();
                    String countryName = ((ObjectNode) obj.get(countryCode)).get("name").asText();
                    ObjectNode r = objectMapper.createObjectNode();
                    r.put("value", countryCode);
                    r.put("label", countryName);
                    results.add(r);
                }
            } else if ("".equals(selectedCountry)) {

            } else {
                ObjectNode country = (ObjectNode) obj.get(selectedCountry);
                ObjectNode divisions = (ObjectNode) country.get("divisions");
                Iterator<String> iter = divisions.fieldNames();
                while (iter.hasNext()) {
                    String divisionCode = iter.next();
                    String divisionName = divisions.get(divisionCode).asText();
                    ObjectNode r = objectMapper.createObjectNode();
                    r.put("value", divisionCode);
                    r.put("label", divisionName);
                    results.add(r);
                }
            }
        }

        response.getWriter().write(results.toString());

        return null;
    }

    public String getAddressAndPhoneHistoryAsJson()
            throws Exception {

        String demographicNo = request.getParameter("demographicNo");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "r", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        if (demographicNo != null) {
            List<DemographicArchive> archives = demographicArchiveDao.findByDemographicNoChronologically(Integer.parseInt(demographicNo));

            String address;
            String city;
            String province;
            String postal;
            String phone;
            String phone2;
            String cell;
            String hPhoneExt;
            String wPhoneExt;

            List<DemographicHistoryItem> items = new ArrayList<DemographicHistoryItem>();

            for (DemographicArchive archive : archives) {

                address = "";
                city = "";
                province = "";
                postal = "";
                phone = "";
                phone2 = "";
                cell = "";
                hPhoneExt = "";
                wPhoneExt = "";

                List<DemographicExtArchive> exts = demographicExtArchiveDao.getDemographicExtArchiveByArchiveId(archive.getId());
                java.util.Map<String, DemographicExtArchive> extMap = new java.util.HashMap<String, DemographicExtArchive>();
                for (DemographicExtArchive ext : exts) {
                    extMap.put(ext.getKey(), ext);
                }

                if (!address.equals(archive.getAddress()) || !city.equals(archive.getCity())
                        || !province.equals(archive.getProvince()) || !postal.equals(archive.getPostal())) {

                    items.add(new DemographicHistoryItem(new String(archive.getAddress() + ", " + archive.getCity() + "," + archive.getProvince() + "," + archive.getPostal()), "address", archive.getLastUpdateDate()));
                    address = archive.getAddress();
                    city = archive.getCity();
                    province = archive.getProvince();
                    postal = archive.getPostal();
                }

                if (!phone.equals(archive.getPhone()) || (extMap.get("hPhoneExt") != null && !extMap.get("hPhoneExt").getValue().equals(hPhoneExt))) {
                    //new home phone
                    items.add(new DemographicHistoryItem(archive.getPhone() + (extMap.get("hPhoneExt") != null ? "x" + extMap.get("hPhoneExt").getValue() : ""), "phone", archive.getLastUpdateDate()));
                    phone = archive.getPhone();
                    hPhoneExt = extMap.get("hPhoneExt") != null ? extMap.get("hPhoneExt").getValue() : "";
                }

                if (!phone2.equals(archive.getPhone2()) || (extMap.get("wPhoneExt") != null && !extMap.get("wPhoneExt").getValue().equals(wPhoneExt))) {
                    //new work phone
                    items.add(new DemographicHistoryItem(archive.getPhone2() + (extMap.get("wPhoneExt") != null ? "x" + extMap.get("wPhoneExt").getValue() : ""), "phone2", archive.getLastUpdateDate()));
                    phone2 = archive.getPhone2();
                    wPhoneExt = extMap.get("wPhoneExt") != null ? extMap.get("wPhoneExt").getValue() : "";
                }

                if ((extMap.get("demo_cell") != null && extMap.get("demo_cell").getValue() != null && !extMap.get("demo_cell").getValue().equals(cell))) {
                    //new cell phone
                    items.add(new DemographicHistoryItem(extMap.get("demo_cell").getValue(), "cell", archive.getLastUpdateDate()));
                    cell = extMap.get("demo_cell").getValue();
                }

            }

            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(items);
            response.getWriter().print(json);
        }


        return null;
    }

    public String checkForDuplicates() {
        String firstName = request.getParameter("firstName");
        String lastName = request.getParameter("lastName");
        String yearOfBirth = request.getParameter("yearOfBirth");
        String monthOfBirth = request.getParameter("monthOfBirth");
        String dayOfBirth = request.getParameter("dayOfBirth");

        List<Demographic> duplicateList = demographicDao.getDemographicWithLastFirstDOBExact(lastName, firstName,
                yearOfBirth, monthOfBirth, dayOfBirth);

        Map<String, Object> result = new HashMap<>();
        result.put("hasDuplicates", !duplicateList.isEmpty());

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try {
            new ObjectMapper().writeValue(response.getWriter(), result);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error in checkForDuplicates", e);
        }

        return null;
    }
}
