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


/*
 * RxManagePharmacy2Action.java
 *
 * Created on September 29, 2004, 3:20 PM
 */

package ca.openosp.openo.prescript.pageUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;


import ca.openosp.openo.commn.model.PharmacyInfo;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;

import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.log.LogConst;
import ca.openosp.openo.prescript.data.RxPharmacyData;
import ca.openosp.openo.util.StringUtils;

/**
 * @author Jay Gallagher & Jackson Bi
 */
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public final class RxManagePharmacy2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final ObjectMapper objectMapper = new ObjectMapper();


    public String execute() throws IOException, ServletException {
        String method = request.getParameter("method");
        if ("delete".equals(method)) {
            return delete();
        } else if ("unlink".equals(method)) {
            return unlink();
        } else if ("getPharmacyFromDemographic".equals(method)) {
            return getPharmacyFromDemographic();
        } else if ("setPreferred".equals(method)) {
            return setPreferred();
        } else if ("add".equals(method)) {
            return add();
        } else if ("save".equals(method)) {
            return save();
        } else if ("search".equals(method)) {
            return search();
        } else if ("searchCity".equals(method)) {
            return searchCity();
        } else if ("getPharmacyInfo".equals(method)) {
            return getPharmacyInfo();
        } else if ("getTotalDemographicsPreferedToPharmacy".equals(method)) {
            return getTotalDemographicsPreferedToPharmacy();
        }

        String actionType = this.getPharmacyAction();
        RxPharmacyData pharmacy = new RxPharmacyData();

        if (actionType.equals("Add")) {
            pharmacy.addPharmacy(this.getName(), this.getAddress(), this.getCity(), this.getProvince(), this.getPostalCode(), this.getPhone1(), this.getPhone2(), this.getFax(), this.getEmail(), this.getServiceLocationIdentifier(), this.getNotes());
        } else if (actionType.equals("Edit")) {
            pharmacy.updatePharmacy(this.getID(), this.getName(), this.getAddress(), this.getCity(), this.getProvince(), this.getPostalCode(), this.getPhone1(), this.getPhone2(), this.getFax(), this.getEmail(), this.getServiceLocationIdentifier(), this.getNotes());
        } else if (actionType.equals("Delete")) {
            pharmacy.deletePharmacy(this.getID());
        }

        return SUCCESS;
    }

    public String delete() throws IOException {


        String retVal = "{\"success\":true}";
        try {
            String pharmacyId = request.getParameter("pharmacyId");

            RxPharmacyData pharmacy = new RxPharmacyData();
            pharmacy.deletePharmacy(pharmacyId);

            LoggedInInfo loggedInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

            LogAction.addLog(loggedInfo.getLoggedInProviderNo(), LogConst.DELETE, LogConst.CON_PHARMACY, pharmacyId);
        } catch (Exception e) {
            MiscUtils.getLogger().error("CANNOT DELETE PHARMACY ", e);
            retVal = "{\"success\":false}";
        }

        response.setContentType("text/x-json");
        ObjectNode jsonObject = (ObjectNode) objectMapper.readTree(retVal);
        response.getWriter().write(jsonObject.toString());

        return null;
    }

    public String unlink() {

        try {
            String pharmId = request.getParameter("pharmacyId");
            String demographicNo = request.getParameter("demographicNo");

            ObjectMapper mapper = new ObjectMapper();
            RxPharmacyData pharmacy = new RxPharmacyData();

            pharmacy.unlinkPharmacy(pharmId, demographicNo);

            response.setContentType("text/x-json");
            String retVal = "{\"id\":\"" + pharmId + "\"}";
            ObjectNode jsonObject = (ObjectNode) objectMapper.readTree(retVal);
            response.getWriter().write(jsonObject.toString());
        } catch (Exception e) {
            MiscUtils.getLogger().error("CANNOT UNLINK PHARMACY", e);
        }

        return null;
    }

    public String getPharmacyFromDemographic() throws IOException {

        String demographicNo = request.getParameter("demographicNo");

        if (demographicNo == null || demographicNo.isEmpty() || !demographicNo.matches("\\d+")) {
            return null;
	}

        RxPharmacyData pharmacyData = new RxPharmacyData();
        List<PharmacyInfo> pharmacyList;
        pharmacyList = pharmacyData.getPharmacyFromDemographic(demographicNo);

        response.setContentType("text/x-json");
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(response.getWriter(), pharmacyList);

        return null;
    }

    public String setPreferred() {
        RxPharmacyData pharmacy = new RxPharmacyData();
        try {
            PharmacyInfo pharmacyInfo = pharmacy.addPharmacyToDemographic(request.getParameter("pharmId"), request.getParameter("demographicNo"), request.getParameter("preferredOrder"));
            ObjectMapper mapper = new ObjectMapper();
            response.setContentType("text/x-json");
            mapper.writeValue(response.getWriter(), pharmacyInfo);
        } catch (Exception e) {
            MiscUtils.getLogger().error("ERROR SETTING PREFERRED ORDER", e);
        }

        return null;
    }

    public String add() {
        RxPharmacyData pharmacy = new RxPharmacyData();

        String status = "{\"success\":true}";

        try {
            pharmacy.addPharmacy(request.getParameter("pharmacyName"), request.getParameter("pharmacyAddress"), request.getParameter("pharmacyCity"),
                    request.getParameter("pharmacyProvince"), request.getParameter("pharmacyPostalCode"), request.getParameter("pharmacyPhone1"), request.getParameter("pharmacyPhone2"),
                    request.getParameter("pharmacyFax"), request.getParameter("pharmacyEmail"), request.getParameter("pharmacyServiceLocationId"), request.getParameter("pharmacyNotes"));
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error Updating Pharmacy " + request.getParameter("pharmacyId"), e);
            status = "{\"success\":false}";
        }

        ObjectNode jsonObject = objectMapper.createObjectNode();

        try {
            jsonObject = (ObjectNode) objectMapper.readTree(status);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Cannot parse status json", e);
        }

        try {
            response.setContentType("text/x-json");
            response.getWriter().write(jsonObject.toString());
        } catch (IOException e) {
            MiscUtils.getLogger().error("Cannot write response", e);
        }

        return null;
    }

    public String save() {


        RxPharmacyData pharmacy = new RxPharmacyData();
        PharmacyInfo pharmacyInfo = new PharmacyInfo();
        pharmacyInfo.setId(Integer.parseInt(request.getParameter("pharmacyId")));
        pharmacyInfo.setName(request.getParameter("pharmacyName"));
        pharmacyInfo.setAddress(request.getParameter("pharmacyAddress"));
        pharmacyInfo.setCity(request.getParameter("pharmacyCity"));
        pharmacyInfo.setProvince(request.getParameter("pharmacyProvince"));
        pharmacyInfo.setPostalCode(request.getParameter("pharmacyPostalCode"));
        pharmacyInfo.setPhone1(request.getParameter("pharmacyPhone1"));
        pharmacyInfo.setPhone2(request.getParameter("pharmacyPhone2"));
        pharmacyInfo.setFax(request.getParameter("pharmacyFax"));
        pharmacyInfo.setEmail(request.getParameter("pharmacyEmail"));
        pharmacyInfo.setServiceLocationIdentifier(request.getParameter("pharmacyServiceLocationId"));
        pharmacyInfo.setNotes(request.getParameter("pharmacyNotes"));

        try {
            pharmacy.updatePharmacy(request.getParameter("pharmacyId"), request.getParameter("pharmacyName"), request.getParameter("pharmacyAddress"), request.getParameter("pharmacyCity"),
                    request.getParameter("pharmacyProvince"), request.getParameter("pharmacyPostalCode"), request.getParameter("pharmacyPhone1"), request.getParameter("pharmacyPhone2"),
                    request.getParameter("pharmacyFax"), request.getParameter("pharmacyEmail"), request.getParameter("pharmacyServiceLocationId"), request.getParameter("pharmacyNotes"));
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error Updating Pharmacy " + request.getParameter("pharmacyId"), e);
            return null;
        }

        try {
            response.setContentType("text/x-json");
            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(response.getWriter(), pharmacyInfo);

        } catch (IOException e) {
            MiscUtils.getLogger().error("Error writing response", e);
        }

        return null;
    }

    public String search() {

        String searchStr = request.getParameter("term");

        RxPharmacyData pharmacy = new RxPharmacyData();

        List<PharmacyInfo> pharmacyList = pharmacy.searchPharmacy(searchStr);

        response.setContentType("text/x-json");
        ObjectMapper mapper = new ObjectMapper();

        try {
            mapper.writeValue(response.getWriter(), pharmacyList);
        } catch (IOException e) {
            MiscUtils.getLogger().error("ERROR WRITING RESPONSE ", e);
        }

        return null;

    }

    public String searchCity() {

        String searchStr = request.getParameter("term");

        RxPharmacyData pharmacy = new RxPharmacyData();

        response.setContentType("text/x-json");
        ObjectMapper mapper = new ObjectMapper();

        List<String> cityList = pharmacy.searchPharmacyCity(searchStr);

        try {
            mapper.writeValue(response.getWriter(), cityList);
        } catch (IOException e) {
            MiscUtils.getLogger().error("ERROR WRITING RESPONSE ", e);
        }

        return null;
    }

    public String getPharmacyInfo() throws IOException {
        String pharmacyId = request.getParameter("pharmacyId");
        MiscUtils.getLogger().debug("pharmacyId=" + pharmacyId);
        if (pharmacyId == null) return null;
        RxPharmacyData pharmacyData = new RxPharmacyData();
        PharmacyInfo pharmacy = pharmacyData.getPharmacy(pharmacyId);
        HashMap<String, String> hm = new HashMap<String, String>();
        if (pharmacy != null) {
            hm.put("address", pharmacy.getAddress());
            hm.put("city", pharmacy.getCity());
            hm.put("email", pharmacy.getEmail());
            hm.put("fax", pharmacy.getFax());
            hm.put("name", pharmacy.getName());
            hm.put("phone1", pharmacy.getPhone1());
            hm.put("phone2", pharmacy.getPhone2());
            hm.put("postalCode", pharmacy.getPostalCode());
            hm.put("province", pharmacy.getProvince());
            hm.put("serviceLocationIdentifier", pharmacy.getServiceLocationIdentifier());
            hm.put("notes", pharmacy.getNotes());
            ObjectNode jsonObject = objectMapper.valueToTree(hm);

            response.setContentType("application/json");

            response.getOutputStream().write(jsonObject.toString().getBytes());
        }
        return null;
    }

    public String getTotalDemographicsPreferedToPharmacy() throws IOException {
        String pharmacyId = StringUtils.isNullOrEmpty(request.getParameter("pharmacyId")) ? "0" : request.getParameter("pharmacyId");
        RxPharmacyData pharmacyData = new RxPharmacyData();
        Long totalDemographics = pharmacyData.getTotalDemographicsPreferedToPharmacyByPharmacyId(pharmacyId);
        ObjectNode jsonObject = objectMapper.createObjectNode();
        jsonObject.put("totalDemographics", totalDemographics);
        response.getOutputStream().write(jsonObject.toString().getBytes());
        return null;
    }

    /**
     * Creates a new instance of RxManagePharmacy2Action
     */
    public RxManagePharmacy2Action() {
    }

    String pharmacyAction = null;
    String ID = null;
    String name = null;
    String address = null;
    String city = null;
    String province = null;
    String postalCode = null;
    String phone1 = null;
    String phone2 = null;
    String fax = null;
    String email = null;
    String serviceLocationIdentifier = null;
    String notes = null;


    /**
     * Getter for property pharmacyAction.
     *
     * @return Value of property pharmacyAction.
     */
    public String getPharmacyAction() {
        return pharmacyAction;
    }

    /**
     * Setter for property pharmacyAction.
     *
     * @param pharmacyAction New value of property pharmacyAction.
     */
    public void setPharmacyAction(String pharmacyAction) {
        this.pharmacyAction = pharmacyAction;
    }

    /**
     * Getter for property ID.
     *
     * @return Value of property ID.
     */
    public String getID() {
        return ID;
    }

    /**
     * Setter for property ID.
     *
     * @param ID New value of property ID.
     */
    public void setID(String ID) {
        this.ID = ID;
    }

    /**
     * Getter for property name.
     *
     * @return Value of property name.
     */
    public String getName() {
        return name;
    }

    /**
     * Setter for property name.
     *
     * @param name New value of property name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Getter for property address.
     *
     * @return Value of property address.
     */
    public String getAddress() {
        return address;
    }

    /**
     * Setter for property address.
     *
     * @param address New value of property address.
     */
    public void setAddress(String address) {
        this.address = address;
    }

    /**
     * Getter for property city.
     *
     * @return Value of property city.
     */
    public String getCity() {
        return city;
    }

    /**
     * Setter for property city.
     *
     * @param city New value of property city.
     */
    public void setCity(String city) {
        this.city = city;
    }

    /**
     * Getter for property province.
     *
     * @return Value of property province.
     */
    public String getProvince() {
        return province;
    }

    /**
     * Setter for property province.
     *
     * @param province New value of property province.
     */
    public void setProvince(String province) {
        this.province = province;
    }

    /**
     * Getter for property postalCode.
     *
     * @return Value of property postalCode.
     */
    public String getPostalCode() {
        return postalCode;
    }

    /**
     * Setter for property postalCode.
     *
     * @param postalCode New value of property postalCode.
     */
    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    /**
     * Getter for property phone1.
     *
     * @return Value of property phone1.
     */
    public String getPhone1() {
        return phone1;
    }

    /**
     * Setter for property phone1.
     *
     * @param phone1 New value of property phone1.
     */
    public void setPhone1(String phone1) {
        this.phone1 = phone1;
    }

    /**
     * Getter for property phone2.
     *
     * @return Value of property phone2.
     */
    public String getPhone2() {
        return phone2;
    }

    /**
     * Setter for property phone2.
     *
     * @param phone2 New value of property phone2.
     */
    public void setPhone2(String phone2) {
        this.phone2 = phone2;
    }

    /**
     * Getter for property fax.
     *
     * @return Value of property fax.
     */
    public String getFax() {
        return fax;
    }

    /**
     * Setter for property fax.
     *
     * @param fax New value of property fax.
     */
    public void setFax(String fax) {
        this.fax = fax;
    }

    /**
     * Getter for property email.
     *
     * @return Value of property email.
     */
    public String getEmail() {
        return email;
    }

    /**
     * Setter for property email.
     *
     * @param email New value of property email.
     */
    public void setEmail(String email) {
        this.email = email;
    }


    /**
     * Getter for service location identifier.
     *
     * @return Value of service location identifier.
     */
    public String getServiceLocationIdentifier() {
        return serviceLocationIdentifier;
    }

    /**
     * Setter for Service Location Identifier
     *
     * @param serviceLocationIdentifier New value
     */
    public void setServiceLocationIdentifier(String serviceLocationIdentifier) {
        this.serviceLocationIdentifier = serviceLocationIdentifier;
    }

    /**
     * Getter for property notes.
     *
     * @return Value of property notes.
     */
    public String getNotes() {
        return notes;
    }

    /**
     * Setter for property notes.
     *
     * @param notes New value of property notes.
     */
    public void setNotes(String notes) {
        this.notes = notes;
    }

}
