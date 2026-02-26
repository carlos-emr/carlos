/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
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
 * Originally written for the Department of Family Medicine, McMaster University.
 * Now maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 *
 * Modifications by CARLOS Contributors, 2026.
 */


package io.github.carlos_emr.carlos.prescript.util;
/*
 * DrugRef.java
 *
 * Created on September 19, 2003, 2:16 PM
 */


import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * @author Jay
 */
public class RxDrugRef {


    // DRUG CATEGORIES FOR THE DRUG REF SEARCH TABLE.
    public static int CAT_BRAND = 13;
    public static int CAT_COMPOSITE_GENERIC = 12;
    public static int CAT_GENERIC = 11;
    public static int CAT_ATC = 8;
    public static int CAT_AHFS = 10;
    public static int CAT_ACTIVE_INGREDIENT = 14;
    public static int CAT_AI_COMPOSITE_GENERIC = 19;
    public static int CAT_AI_GENERIC = 18;

    private static final Logger logger = MiscUtils.getLogger();

    private String server_url = null;
    //"http://localhost:8080/drugref2/DrugrefService";
    // "http://www.hherb.com:8001";
    //"http://24.141.82.168:8001";
    //"http://192.168.42.3:8001";

    /**
     * Creates a new instance of DrugRef
     */
    public RxDrugRef() {
        server_url = OscarProperties.getInstance().getProperty("drugref_url");
        //server_url = System.getProperty("drugref_url");

    }

    public RxDrugRef(String url) {
        server_url = url;
    }

    public String getDrugRefURL() {
        return server_url;
    }

    public Hashtable<String, Object> getDrugByDIN(String DIN, Boolean boolVal) throws Exception {
        Vector params = new Vector();
        params.addElement(DIN);
        params.addElement(boolVal);
        Vector<Hashtable<String, Object>> vec = (Vector<Hashtable<String, Object>>) callWebserviceLite("get_drug_by_DIN", params);
        Hashtable<String, Object> returnVal = vec.get(0);
        return returnVal;
    }

    /**
     * returns all matching ATC codes for a given (fraction of) a drug name.
     * Search is case insensitive
     * query = "select code, text from atc where text like '%s%%'"
     * <p>
     * <p>
     * [{'code':'0', 'text':'None found'}]
     */
    public Vector atc(String drug) {
        Vector params = new Vector();
        params.addElement(drug);
        Vector vec = (Vector) callWebservice("atc", params);
        return vec;
    }

    /**
     * returns all matching ATC codes for a given Drug Identification Number.
     * Search is case insensitive
     */
    public Vector atcFromDIN(String din) {
        Vector params = new Vector();
        params.addElement(din);
        Vector vec = (Vector) callWebservice("get_atcs_by_din", params);
        return vec;
    }


    /**
     * returns all matching ATC codes for a given (fraction of) a drug brand name.
     * Search is case insensitive
     * query = "select atc.atccode, pm.brandname from link_product_manufacturer pm, product p, generic_drug_name g, link_drug_atc atc
     * where pm.id_product = p.id and p.id_drug = g.id_drug  and g.id_drug = atc.id_drug and pm.brandname like  '%s%%'"
     * <p>
     * [{'code':'0', 'text':'None found'}]
     */
    public Vector atcFromBrand(String drug) {
        Vector params = new Vector();
        params.addElement(drug);
        Vector vec = (Vector) callWebservice("atcFromBrand", params);
        return vec;
    }


    /**
     * returns the English name of a drug that matches the stated ATC code
     * query = "select code, text from atc where code like '%s%%'"
     * <p>
     * return [{'code':'0', 'text':'None found'}]
     */
    public Vector atc2text(String code) {
        Vector params = new Vector();
        params.addElement(code);
        Vector vec = (Vector) callWebservice("atc2text", params);

        return vec;
    }

    public Vector interaction(Vector atclist) {
        return interaction(atclist, 1);
    }

    /**
     * returns a list of drug-drug interactions as list of "dicts"
     * atclist : list of ATC codes
     * minimum_significance: interactions below the stated significance level will be ignored
     * <p>
     * query = "select drug, effect, affected_drug, significance, evidence, reference from simple_interactions where drug = '%s' and affected_drug = '%s' and significance >= %d" %
     */
    public Vector interaction(Vector atclist, int minimum_significance) {
        Vector params = new Vector();
        params.addElement(atclist);
        params.addElement(Integer.valueOf(minimum_significance));
        Vector vec = (Vector) callWebservice("interaction", params);
        return vec;
    }

    public Vector interactionByRegionalIdentifier(List regionalIdentifierList, int minimum_significance) {
        Vector params = new Vector();
        params.addElement(new Vector(regionalIdentifierList));
        params.addElement(Integer.valueOf(minimum_significance));
        Vector vec = (Vector) callWebservice("interaction_by_regional_identifier", params);
        return vec;
    }


    public Hashtable getDrug(String pKey, Boolean boolVal) throws Exception {
        Vector params = new Vector();
        params.addElement(pKey);
        params.addElement(boolVal);
        Vector vec = (Vector) callWebserviceLite("get_drug", params);
        Hashtable returnVal = (Hashtable) vec.get(0);
        return returnVal;
    }

    public Hashtable getDrug2(String pKey, Boolean boolVal) throws Exception {
        Vector params = new Vector();
        MiscUtils.getLogger().debug("Adding to params for get_drug_2 :" + pKey + " - " + boolVal);
        params.addElement(pKey);
        params.addElement(boolVal);
        Vector vec = (Vector) callWebserviceLite("get_drug_2", params);
        Hashtable returnVal = (Hashtable) vec.get(0);
        return returnVal;
    }

    public Hashtable getDrugForm(String pKey) throws Exception {
        Vector params = new Vector();
        params.addElement(pKey);
        Vector vec = (Vector) callWebserviceLite("get_form", params);
        //if (vec == null || vec.isEmpty()){
        //    return null;
        //}
        Hashtable returnVal = (Hashtable) vec.get(0);
        return returnVal;
    }


    public Vector suggestAlias(String alias, String aliasComment, String id, String name, String provider) throws Exception {
        Vector params = new Vector();
        params.addElement(alias);
        params.addElement(aliasComment);
        params.addElement(id);
        params.addElement(name);
        params.addElement(provider);
        Vector vec = (Vector) callWebserviceLite("suggestAlias", params);
        return vec;
    }


    public Hashtable getGenericName(String pKey) throws Exception {
        Vector params = new Vector();
        params.addElement(pKey);

        Vector vec = (Vector) callWebserviceLite("get_generic_name", params);
        //  if (vec == null || vec.isEmpty()){

        //     return null;
        //  }
        Hashtable returnVal = (Hashtable) vec.get(0);
        return returnVal;
    }

    /**
     * Returns a list of atc codes without the drug
     * uses function atc on the back end and strips the name
     */
    public Vector drug2atclist(String drug) {
        Vector params = new Vector();
        params.addElement(drug);
        Vector vec = (Vector) callWebservice("drug2atclist", params);
        return vec;
    }

    public Vector druglist2atclist(Vector druglist) {
        Vector params = new Vector();
        params.addElement(druglist);
        Vector vec = (Vector) callWebservice("druglist2atclist", params);
        return vec;
    }


    public Vector interaction_by_drugnames(Vector druglist, int minimum_significance) {
        Vector params = new Vector();
        params.addElement(druglist);
        params.addElement(Integer.valueOf(minimum_significance));
        Vector vec = (Vector) callWebservice("interaction_by_drugnames", params);
        return vec;
    }

    /**
     * returns all matching search element names, ids and categoeis for the given searchString
     * Search is case insensitive
     */
    public Vector list_drug_element(String searchStr) throws Exception {
        Vector params = new Vector();
        params.addElement(searchStr);
        Vector vec = (Vector) callWebserviceLite("list_search_element", params);
        return vec;
    }

    /**
     * Verifies connectivity to the drug reference service and retrieves system metadata.
     * <p>
     * This method performs a health check by calling multiple web service endpoints to gather
     * information about the connected drug database system. It retrieves the last database update
     * timestamp, identifies the drug database type, and obtains the current database version.
     *
     * @return Map&lt;String, String&gt; containing drug reference metadata with the following keys:
     *         <ul>
     *         <li>"lastUpdate" - ISO timestamp of the most recent database update</li>
     *         <li>"drugDatabase" - String identifier of the drug database system in use</li>
     *         <li>"version" - String version number of the drug reference database</li>
     *         </ul>
     * @throws Exception if the drug reference service is unavailable or if any web service call fails
     * @since 2026-01-21
     */
    public Map<String, String> verify() throws Exception {
        Vector params = new Vector();
        String lastUpdateTime = getLastUpdateTime();
        String drugDatabase = callWebserviceLite("identify", params).toString();
        String version = callWebserviceLite("version", params).toString();
        Map<String, String> verify = new HashMap<>();
        verify.put("lastUpdate", lastUpdateTime);
        verify.put("drugDatabase", drugDatabase);
        verify.put("version", version);
        return verify;
    }

    public String updateDB() throws Exception {
        Vector params = new Vector();
        return (String) callWebserviceLite("updateDB", params);

    }

    public String getLastUpdateTime() throws Exception {
        Vector params = new Vector();
        String s = (String) callWebserviceLite("getLastUpdateTime", params);
        return s;
    }

    public Vector list_drug_element2(String searchStr) throws Exception {
        Vector params = new Vector();
        params.addElement(searchStr);
        Vector vec = (Vector) callWebserviceLite("list_search_element2", params);
        return vec;
    }

    public Vector list_drug_element3(String searchStr, boolean rightWildcardOnly) throws Exception {
        Vector params = new Vector();
        params.addElement(searchStr);
        Vector<Hashtable> vec = null;
        if (rightWildcardOnly) {
            vec = (Vector) callWebserviceLite("list_search_element3_right", params);
        } else {
            vec = (Vector) callWebserviceLite("list_search_element3", params);
        }
        return vec;
    }

    /**
     * returns all matching search element names, ids and categoeis for the given searchString
     * Search is limited by the given searchForm, and is case insensitive
     */
    public Vector list_drug_element_route(String searchStr, String searchRoute) throws Exception {
        Vector params = new Vector();
        params.addElement(searchStr);
        params.addElement(searchRoute);
        Vector vec = (Vector) callWebserviceLite("list_search_element_route", params);
        return vec;
    }

    public Vector list_brands_from_element(String drugRefId) throws Exception {
        Vector params = new Vector();
        params.addElement(drugRefId);
        Vector vec = (Vector) callWebserviceLite("list_brands_from_element", params);
        return vec;
    }

    public Vector getDrugInfoPage(String drugRefId) {
        Vector params = new Vector();
        params.addElement(drugRefId);
        Vector vec = (Vector) callWebservice("getDrugInfoPage", params);
        return vec;
    }

    public Vector list_search_element_select_categories(String searchStr, Vector catVec) {
        return list_search_element_select_categories(searchStr, catVec, false);
    }

    public Vector list_search_element_select_categories(String searchStr, Vector catVec, boolean wildcardRightOnly) {
        Vector params = new Vector();
        params.addElement(searchStr);
        params.addElement(catVec);
        Vector vec = null;
        if (wildcardRightOnly) {
            vec = (Vector) callWebservice("list_search_element_select_categories_right", params);
        } else {
            vec = (Vector) callWebservice("list_search_element_select_categories", params);
        }
        return vec;
    }


    public Vector list_drug_class(Vector classVec) {
        Vector params = new Vector();
        params.addElement(classVec);
        Vector vec = (Vector) callWebservice("list_drug_class", params);
        return vec;
    }


    public Vector getAISameByDrugCode(String drugRefId) {
        Vector params = new Vector();
        params.addElement(drugRefId);
        Vector vec = (Vector) callWebservice("getAISameByDrugCode", params);
        return vec;
    }

    public Vector getFormFromDrugCode(String drugCode) {
        Vector params = new Vector();
        params.addElement(drugCode);
        Vector vec = (Vector) callWebservice("getFormFromDrugCode", params);
        return vec;
    }


    public Vector getDistinctForms() {
        Vector params = new Vector();
        Vector vec = (Vector) callWebservice("getDistinctForms", params);
        return vec;
    }

    public Vector getRouteFromDrugCode(String drugCode) {
        Vector params = new Vector();
        params.addElement(drugCode);
        Vector vec = (Vector) callWebservice("getRouteFromDrugCode", params);
        return vec;
    }

    public Vector getStrengths(String drugCode) {
        Vector params = new Vector();
        params.addElement(drugCode);
        Vector vec = (Vector) callWebservice("getStrengths", params);
        return vec;
    }


    public Vector getProductData(String drugRefId) {
        Vector params = new Vector();
        params.addElement(drugRefId);
        Vector vec = (Vector) callWebservice("getProductData", params);
        return vec;
    }


    public String getGenericNamefromId(String drugRefId) {
        Vector params = new Vector();
        params.addElement(drugRefId);
        Vector vec = (Vector) callWebservice("getGenericNamefromId", params);
        return vec.get(0).toString();
    }

    /**
     * Calls the DrugRef XML-RPC service, swallowing all errors and returning null on failure.
     * Used by methods where a missing result is acceptable (e.g., search results).
     */
    private Object callWebservice(String procedureName, Vector params) {
        MiscUtils.getLogger().debug("#CALLDRUGREF-" + procedureName);
        Object object = null;
        try {
            SimpleXmlRpcClient server = new SimpleXmlRpcClient(server_url);
            object = server.execute(procedureName, params);
        } catch (XmlRpcFaultException exception) {
            logger.error("JavaClient: XML-RPC Fault #" + exception.code, exception);
        } catch (Exception exception) {
            logger.error("JavaClient: ", exception);
        }
        return object;
    }

    /**
     * Calls the DrugRef XML-RPC service, propagating non-zero fault codes as exceptions.
     * A fault code of 0 is treated as a "no result" condition and silently returns null,
     * while all other errors are re-thrown to the caller.
     */
    private Object callWebserviceLite(String procedureName, Vector params) throws Exception {
        Object object = null;
        try {
            SimpleXmlRpcClient server = new SimpleXmlRpcClient(server_url);
            object = server.execute(procedureName, params);
        } catch (Exception exception) {
            // Fault code 0 means "no result found" — log and return null
            if (exception instanceof XmlRpcFaultException && ((XmlRpcFaultException) exception).code == 0) {
                logger.error("JavaClient: XML-RPC Fault. NoResultException thrown for procedure: {} with parameters {}", procedureName, params);
            } else {
                logger.error("JavaClient: XML-RPC Fault ", exception);
                throw new Exception("JavaClient: XML-RPC Fault", exception);
            }
        }
        return object;
    }


    public static void removeNullFromVector(Vector v) {
        while (v != null && v.contains(null)) {
            v.remove(null);
        }
    }

    public Vector getInteractions(Vector drugs) throws Exception {
        removeNullFromVector(drugs);
        Vector params = new Vector();
        params.addElement("interactions_byATC");
        params.addElement(drugs);
        //Vector vec = (Vector) callWebserviceLite("get",params);
        Vector vec = new Vector();
        Object obj = callWebserviceLite("fetch", params);
        if (obj instanceof Vector) {
            vec = (Vector) obj;
        } else if (obj instanceof Hashtable) {
            Object holbrook = ((Hashtable) obj).get("Holbrook Drug Interactions");
            if (holbrook instanceof Vector) {
                vec = (Vector) holbrook;
            }
            Enumeration e = ((Hashtable) obj).keys();
            while (e.hasMoreElements()) {
                String s = (String) e.nextElement();
                MiscUtils.getLogger().debug(s + " " + ((Hashtable) obj).get(s) + " " + ((Hashtable) obj).get(s).getClass().getName());
            }
        }

        return vec;
    }

    public Vector getAlergyWarnings(String drugs, Vector allergies) throws Exception {
        Vector params = new Vector();
        params.addElement(drugs);
        params.addElement(allergies);
        Vector vec = (Vector) callWebserviceLite("get_allergy_warnings", params);
        return vec;
    }

    public Vector getAllergyClasses(Vector allergies) throws Exception {
        Vector params = new Vector();
        params.addElement(allergies);
        Vector vec = (Vector) callWebserviceLite("get_allergy_classes", params);
        return vec;
    }


    public Vector getInactiveDate(String din) throws Exception {
        Vector params = new Vector();
        params.addElement(din);
        Vector vec = (Vector) callWebserviceLite("get_inactive_date", params);
        return vec;
    }
}
