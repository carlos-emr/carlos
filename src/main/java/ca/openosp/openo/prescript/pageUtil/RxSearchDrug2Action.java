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


package ca.openosp.openo.prescript.pageUtil;

import ca.openosp.OscarProperties;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.prescript.data.RxDrugData;
import ca.openosp.openo.prescript.util.RxDrugRef;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;

public final class RxSearchDrug2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private RxDrugRef drugref;
    private static Logger logger = MiscUtils.getLogger();

    public RxSearchDrug2Action() {
        this.drugref = new RxDrugRef();
    }

    @Override
    public String execute()
            throws IOException, ServletException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_rx", "r", null)) {
            throw new RuntimeException("missing required sec object (_rx)");
        }

        String method = request.getParameter("method");
        if ("searchAllCategories".equals(method)) {
            return searchAllCategories();
        } else if ("searchBrandName".equals(method)) {
            return searchBrandName();
        } else if ("searchGenericName".equals(method)) {
            return searchGenericName();
        } else if ("searchActiveIngredient".equals(method)) {
            return searchActiveIngredient();
        } else if ("searchNaturalRemedy".equals(method)) {
            return searchNaturalRemedy();
        } else if ("jsonSearch".equals(method)) {
            return jsonSearch();
        } else if ("inactiveDate".equals(method)) {
            return getInactiveDate();
        }


        // Setup variables
        String genericSearch = this.getGenericSearch();
        //String searchString = reqForm.getSearchString();
        //String searchRoute = reqForm.getSearchRoute();
        if (searchRoute == null) searchRoute = "";

        RxDrugData drugData = new RxDrugData();

        RxDrugData.DrugSearch drugSearch = null;

        try {
            if (genericSearch != null) {
                drugSearch = drugData.listDrugFromElement(genericSearch);
            } else if (!searchRoute.equals("")) {
                drugSearch = drugData.listDrugByRoute(searchString, searchRoute);
            } else {
                drugSearch = drugData.listDrug(searchString);
            }
        } catch (Exception connEx) {
            MiscUtils.getLogger().error("Error", connEx);
        }
        request.setAttribute("drugSearch", drugSearch);
        request.setAttribute("demoNo", this.getDemographicNo());

        return SUCCESS;
    }

    public String searchAllCategories() {
        logger.debug("Calling searchAllCategories");
        Parameter.setParameters(request.getParameterMap());
        Vector<Hashtable<String, Object>> results = null;


        try {
            results = drugref.list_drug_element3(Parameter.SEARCH_STRING, wildCardRight(Parameter.WILDCARD));
            jsonify(results, response);
        } catch (IOException e) {
            logger.error("Exception while attempting to contact DrugRef", e);
            return "error";
        } catch (Exception e) {
            logger.error("Unknown Error", e);
            return "error";
        }

        return null;
    }

    @SuppressWarnings({"unused", "rawtypes", "unchecked"})
    public String searchBrandName() {
        logger.debug("Calling searchBrandName");
        Parameter.setParameters(request.getParameterMap());
        Vector catVec = new Vector();
        catVec.add(RxDrugRef.CAT_BRAND);
        Vector<Hashtable<String, Object>> results = drugref.list_search_element_select_categories(
                Parameter.SEARCH_STRING,
                catVec,
                wildCardRight(Parameter.WILDCARD));
        try {
            jsonify(results, response);
        } catch (IOException e) {
            logger.error("Exception creating JSON Object for " + results, e);
            return "error";
        }
        return null;
    }

    @SuppressWarnings({"unused", "rawtypes", "unchecked"})
    public String searchGenericName() {
        logger.debug("Calling searchGenericName");
        Parameter.setParameters(request.getParameterMap());

        Vector catVec = new Vector();
        catVec.add(RxDrugRef.CAT_AI_COMPOSITE_GENERIC);
        Vector<Hashtable<String, Object>> results = drugref.list_search_element_select_categories(
                Parameter.SEARCH_STRING,
                catVec,
                wildCardRight(Parameter.WILDCARD));
        try {
            jsonify(results, response);
        } catch (IOException e) {
            logger.error("Exception creating JSON Object for " + results, e);
            return "error";
        }
        return null;
    }

    @SuppressWarnings({"unused", "unchecked", "rawtypes"})
    public String searchActiveIngredient() {
        logger.debug("Calling searchActiveIngredient");
        Parameter.setParameters(request.getParameterMap());

        Vector catVec = new Vector();
        catVec.add(RxDrugRef.CAT_ACTIVE_INGREDIENT);
        Vector<Hashtable<String, Object>> results = drugref.list_search_element_select_categories(
                Parameter.SEARCH_STRING,
                catVec,
                wildCardRight(Parameter.WILDCARD));
        try {
            jsonify(results, response);
        } catch (IOException e) {
            logger.error("Exception creating JSON Object for " + results, e);
            return "error";
        }

        return null;
    }

    @SuppressWarnings({"unused", "unchecked", "rawtypes"})
    public String searchNaturalRemedy() {

        return null;
    }


    @SuppressWarnings({"unchecked", "unused"})
    public String jsonSearch() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_rx", "r", null)) {
            throw new RuntimeException("missing required sec object (_rx)");
        }

        String searchStr = request.getParameter("query");
        if (searchStr == null) {
            searchStr = request.getParameter("name");
        }
        String wildcardRightOnly = OscarProperties.getInstance().getProperty("rx.search_right_wildcard_only", "false");
        Vector<Hashtable<String, Object>> vec = null;

        try {
            vec = drugref.list_drug_element3(searchStr, wildCardRight(wildcardRightOnly));
            jsonify(vec, response);
        } catch (IOException e) {
            logger.error("Exception while attempting to contact DrugRef", e);
            return "error";
        } catch (Exception e) {
            logger.error("Unknown Error", e);
            return "error";
        }

        return null;
    }


    private String getInactiveDate () {
        String din = request.getParameter("din");
        String id = request.getParameter("id");

        try {
            RxDrugRef drugData = new RxDrugRef();
            Vector vec = drugData.getInactiveDate(din);
            vec.add(id);
            jsonify(vec, response);

        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
//        Hashtable d = new Hashtable();
//
//        d.put("id", id);
//        d.put("vec", vec);
//        mapper.writeValueAsString(d);
        return null;
    }

    /**
     * Utilty methods - should be split into a class if they get any bigger.
     */

    private static boolean wildCardRight(final String wildcard) {
        if (!StringUtils.isBlank(wildcard)) {
            return Boolean.valueOf(wildcard);
        }
        return Boolean.FALSE;
    }

    private static void jsonify(final Vector<Hashtable<String, Object>> data,
                                final HttpServletResponse response) throws IOException {

        Hashtable<String, Vector<Hashtable<String, Object>>> d = new Hashtable<String, Vector<Hashtable<String, Object>>>();
        d.put("results", data);
        response.setContentType("text/x-json");

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode jsonArray = (ObjectNode) mapper.valueToTree(d);
        Writer jsonWriter = response.getWriter();
        jsonWriter.write(jsonArray.toString());

        jsonWriter.flush();
        jsonWriter.close();

    }

    private static class Parameter {

        //public static String DRUG_STATUS;
        public static String WILDCARD;
        public static String SEARCH_STRING;

        private static void reset() {
            //DRUG_STATUS = "";
            WILDCARD = "";
            SEARCH_STRING = "";
        }

        public static void setParameters(Map<String, String[]> parameters) {
            reset();

    		if(parameters.containsKey("name")) {
    			Parameter.SEARCH_STRING = parameters.get("name")[0];
    		} else if(parameters.containsKey("query")) {
    			Parameter.SEARCH_STRING = parameters.get("query")[0];
    		} else if(parameters.containsKey("searchString")) {
    			Parameter.SEARCH_STRING = parameters.get("searchString")[0];
    		}

            if (parameters.containsKey("wildcard")) {
                Parameter.WILDCARD = parameters.get("wildcard")[0];
            }

        }

    }


    private String demographicNo = null;
    private String searchString = null;
    private String searchRoute = null;
    private String genericString = null;
    private String otcExcluded = null;
    private String ahfsString = null;


    public String getAction() {
        MiscUtils.getLogger().debug("Can i be deleted GETTER getAction RxSearchDrug2Form");
        return "";
    }

    public void getAction(String d) {
        MiscUtils.getLogger().debug("Can i be deleted SETTER getAction RxSearchDrug2Form");
    }

    public String getAhfsSearch() {
        return ahfsString;
    }

    public void setAhfsSearch(String str) {
        ahfsString = str;
    }

    public String getOtcExcluded() {
        if (otcExcluded == null) {
            otcExcluded = "0";
        }
        return otcExcluded;
    }

    public void setOtcExcluded(String str) {
        otcExcluded = str;
    }


    public String getDemographicNo() {
        return (this.demographicNo);
    }

    public void setDemographicNo(String demographicNo) {
        this.demographicNo = demographicNo;
    }

    public String getSearchString() {
        return (this.searchString);
    }

    public void setSearchString(String searchString) {
        this.searchString = searchString;
    }

    public String getSearchRoute() {
        return (this.searchRoute);
    }

    public void setSearchRoute(String searchRoute) {
        this.searchRoute = searchRoute;
    }

    public String getGenericSearch() {
        return genericString;
    }

    public void setGenericSearch(String str) {
        genericString = str;
    }
}
