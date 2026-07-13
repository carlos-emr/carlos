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

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * Client-side interface to the DrugRef XML-RPC web service for drug database lookups.
 *
 * <p>Provides methods for ATC code resolution, drug-drug interaction checking, allergy
 * warnings, DIN-based drug lookups, and drug reference database health verification.
 * Web service calls are dispatched through {@link #callWebservice} (swallows all errors,
 * returns null on failure) or {@link #callWebserviceLite} (propagates non-zero fault
 * codes as exceptions).</p>
 *
 * <p>The DrugRef server URL is configured via the {@code drugref_url} property in
 * {@link io.github.carlos_emr.CarlosProperties}.</p>
 *
 * @since 2003-09-19
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

    /**
     * Creates a new RxDrugRef instance using the DrugRef URL from application properties.
     * The URL is read from the {@code drugref_url} key in {@link CarlosProperties}.
     *
     * @since 2003-09-19
     */
    public RxDrugRef() {
        server_url = CarlosProperties.getInstance().getProperty("drugref_url");
    }

    /**
     * Creates a new RxDrugRef instance using an explicitly provided server URL.
     * Used for testing and for overriding the configured URL at runtime.
     *
     * @param url String the full URL of the DrugRef XML-RPC server
     * @since 2026-02-26
     */
    public RxDrugRef(String url) {
        server_url = url;
    }

    /**
     * Returns the DrugRef server URL in use by this instance.
     *
     * @return String the configured DrugRef XML-RPC server URL, or null if not set
     * @since 2026-02-26
     */
    public String getDrugRefURL() {
        return server_url;
    }

    /**
     * Looks up a drug record by its Drug Identification Number (DIN).
     *
     * @param DIN     String the Drug Identification Number to look up
     * @param boolVal Boolean whether to include additional product details in the response
     * @return Hashtable&lt;String, Object&gt; drug data keyed by field name, or null if not found
     * @throws Exception if the DrugRef service is unavailable or returns a non-zero fault
     * @since 2026-02-26
     */
    public Hashtable<String, Object> getDrugByDIN(String DIN, Boolean boolVal) throws Exception {
        Vector params = new Vector();
        params.addElement(DIN);
        params.addElement(boolVal);
        Vector<Hashtable<String, Object>> vec = (Vector<Hashtable<String, Object>>) callWebserviceLite("get_drug_by_DIN", params);
        if (vec == null || vec.isEmpty()) {
            return null;
        }
        return vec.get(0);
    }

    /**
     * Returns all matching ATC codes for a given drug name fragment.
     * Search is case-insensitive. Returns a list of {@code {'code': '...', 'text': '...'}} dicts,
     * or {@code [{'code':'0', 'text':'None found'}]} when no results match.
     *
     * @param drug String the drug name or partial name to search for
     * @return Vector of Hashtables each containing {@code code} and {@code text} entries,
     *         or null if the service is unavailable
     * @since 2026-02-26
     */
    public Vector atc(String drug) {
        Vector params = new Vector();
        params.addElement(drug);
        return (Vector) callWebservice("atc", params);
    }

    /**
     * Returns all ATC codes associated with a given Drug Identification Number.
     * Search is case-insensitive.
     *
     * @param din String the Drug Identification Number to resolve to ATC codes
     * @return Vector of ATC code strings, or null if the service is unavailable
     * @since 2026-02-26
     */
    public Vector atcFromDIN(String din) {
        Vector params = new Vector();
        params.addElement(din);
        return (Vector) callWebservice("get_atcs_by_din", params);
    }

    /**
     * Returns all matching ATC codes for a given brand name fragment.
     * Search is case-insensitive. Returns a list of {@code {'code': '...', 'text': '...'}} dicts,
     * or {@code [{'code':'0', 'text':'None found'}]} when no results match.
     *
     * @param drug String the brand name or partial brand name to search for
     * @return Vector of Hashtables each containing {@code code} and {@code text} entries,
     *         or null if the service is unavailable
     * @since 2026-02-26
     */
    public Vector atcFromBrand(String drug) {
        Vector params = new Vector();
        params.addElement(drug);
        return (Vector) callWebservice("atcFromBrand", params);
    }

    /**
     * Returns the English name of a drug matching the given ATC code prefix.
     * Returns a list of {@code {'code': '...', 'text': '...'}} dicts,
     * or {@code [{'code':'0', 'text':'None found'}]} when no results match.
     *
     * @param code String the ATC code or code prefix to look up
     * @return Vector of Hashtables each containing {@code code} and {@code text} entries,
     *         or null if the service is unavailable
     * @since 2026-02-26
     */
    public Vector atc2text(String code) {
        Vector params = new Vector();
        params.addElement(code);
        return (Vector) callWebservice("atc2text", params);
    }

    /**
     * Returns drug-drug interactions for the given list of ATC codes at the default
     * minimum significance level of 1.
     *
     * @param atclist Vector of String ATC codes representing the current drug regimen
     * @return Vector of interaction result Hashtables, or null if the service is unavailable
     * @since 2026-02-26
     */
    public Vector interaction(Vector atclist) {
        return interaction(atclist, 1);
    }

    /**
     * Returns drug-drug interactions for the given list of ATC codes above a minimum
     * significance threshold.
     *
     * @param atclist              Vector of String ATC codes representing the current drug regimen
     * @param minimum_significance int interactions with significance below this value are excluded
     * @return Vector of interaction result Hashtables, or null if the service is unavailable
     * @since 2026-02-26
     */
    public Vector interaction(Vector atclist, int minimum_significance) {
        Vector params = new Vector();
        params.addElement(atclist);
        params.addElement(Integer.valueOf(minimum_significance));
        return (Vector) callWebservice("interaction", params);
    }

    /**
     * Returns drug-drug interactions using regional drug identifiers instead of ATC codes.
     *
     * @param regionalIdentifierList List of String regional drug identifiers for the drug regimen
     * @param minimum_significance   int interactions with significance below this value are excluded
     * @return Vector of interaction result Hashtables, or null if the service is unavailable
     * @since 2026-02-26
     */
    public Vector interactionByRegionalIdentifier(List regionalIdentifierList, int minimum_significance) {
        Vector params = new Vector();
        params.addElement(new Vector(regionalIdentifierList));
        params.addElement(Integer.valueOf(minimum_significance));
        return (Vector) callWebservice("interaction_by_regional_identifier", params);
    }

    /**
     * Returns full drug record data for the given primary key.
     *
     * @param pKey    String the DrugRef primary key identifying the drug record
     * @param boolVal Boolean whether to include additional product details in the response
     * @return Hashtable of drug field names to values, or null if not found
     * @throws Exception if the DrugRef service is unavailable or returns a non-zero fault
     * @since 2026-02-26
     */
    public Hashtable getDrug(String pKey, Boolean boolVal) throws Exception {
        Vector params = new Vector();
        params.addElement(pKey);
        params.addElement(boolVal);
        Vector vec = (Vector) callWebserviceLite("get_drug", params);
        if (vec == null || vec.isEmpty()) {
            return null;
        }
        return (Hashtable) vec.get(0);
    }

    /**
     * Returns extended drug record data (version 2 API) for the given primary key.
     *
     * @param pKey    String the DrugRef primary key identifying the drug record
     * @param boolVal Boolean whether to include additional product details in the response
     * @return Hashtable of drug field names to values, or null if not found
     * @throws Exception if the DrugRef service is unavailable or returns a non-zero fault
     * @since 2026-02-26
     */
    public Hashtable getDrug2(String pKey, Boolean boolVal) throws Exception {
        Vector params = new Vector();
        params.addElement(pKey);
        params.addElement(boolVal);
        Vector vec = (Vector) callWebserviceLite("get_drug_2", params);
        if (vec == null || vec.isEmpty()) {
            return null;
        }
        return (Hashtable) vec.get(0);
    }

    /**
     * Returns the dosage form data for the given drug primary key.
     *
     * @param pKey String the DrugRef primary key identifying the drug record
     * @return Hashtable of form field names to values, or null if not found
     * @throws Exception if the DrugRef service is unavailable or returns a non-zero fault
     * @since 2026-02-26
     */
    public Hashtable getDrugForm(String pKey) throws Exception {
        Vector params = new Vector();
        params.addElement(pKey);
        Vector vec = (Vector) callWebserviceLite("get_form", params);
        if (vec == null || vec.isEmpty()) {
            return null;
        }
        return (Hashtable) vec.get(0);
    }

    /**
     * Submits a suggested alias (alternative name) for a drug to the DrugRef service.
     *
     * @param alias        String the suggested alias for the drug
     * @param aliasComment String a comment or rationale for the suggested alias
     * @param id           String the DrugRef identifier for the drug
     * @param name         String the drug name associated with the suggestion
     * @param provider     String the provider submitting the suggestion
     * @return Vector of response values from the DrugRef service
     * @throws Exception if the DrugRef service is unavailable or returns a non-zero fault
     * @since 2026-02-26
     */
    public Vector suggestAlias(String alias, String aliasComment, String id, String name, String provider) throws Exception {
        Vector params = new Vector();
        params.addElement(alias);
        params.addElement(aliasComment);
        params.addElement(id);
        params.addElement(name);
        params.addElement(provider);
        return (Vector) callWebserviceLite("suggestAlias", params);
    }

    /**
     * Returns the generic name record for the given drug primary key.
     *
     * @param pKey String the DrugRef primary key identifying the drug record
     * @return Hashtable of generic name field names to values, or null if not found
     * @throws Exception if the DrugRef service is unavailable or returns a non-zero fault
     * @since 2026-02-26
     */
    public Hashtable getGenericName(String pKey) throws Exception {
        Vector params = new Vector();
        params.addElement(pKey);
        Vector vec = (Vector) callWebserviceLite("get_generic_name", params);
        if (vec == null || vec.isEmpty()) {
            return null;
        }
        return (Hashtable) vec.get(0);
    }

    /**
     * Returns a list of ATC codes for the given drug name, with drug names stripped from results.
     * Calls the {@code atc} function on the DrugRef backend and returns only the codes.
     *
     * @param drug String the drug name or partial name to resolve to ATC codes
     * @return Vector of ATC code strings, or null if the service is unavailable
     * @since 2026-02-26
     */
    public Vector drug2atclist(String drug) {
        Vector params = new Vector();
        params.addElement(drug);
        return (Vector) callWebservice("drug2atclist", params);
    }

    /**
     * Returns a list of ATC codes for a list of drug names.
     *
     * @param druglist Vector of String drug names to resolve to ATC codes
     * @return Vector of ATC code strings, or null if the service is unavailable
     * @since 2026-02-26
     */
    public Vector druglist2atclist(Vector druglist) {
        Vector params = new Vector();
        params.addElement(druglist);
        return (Vector) callWebservice("druglist2atclist", params);
    }

    /**
     * Returns drug-drug interactions for a list of drug names above a minimum significance threshold.
     *
     * @param druglist             Vector of String drug names representing the current drug regimen
     * @param minimum_significance int interactions with significance below this value are excluded
     * @return Vector of interaction result Hashtables, or null if the service is unavailable
     * @since 2026-02-26
     */
    public Vector interaction_by_drugnames(Vector druglist, int minimum_significance) {
        Vector params = new Vector();
        params.addElement(druglist);
        params.addElement(Integer.valueOf(minimum_significance));
        return (Vector) callWebservice("interaction_by_drugnames", params);
    }

    /**
     * Returns all matching drug search elements (names, IDs, categories) for the given
     * search string. Search is case-insensitive.
     *
     * @param searchStr String the drug name or partial name to search for
     * @return Vector of search result Hashtables, each containing name, id, and category fields
     * @throws Exception if the DrugRef service is unavailable or returns a non-zero fault
     * @since 2026-02-26
     */
    public Vector list_drug_element(String searchStr) throws Exception {
        Vector params = new Vector();
        params.addElement(searchStr);
        return (Vector) callWebserviceLite("list_search_element", params);
    }

    /**
     * Verifies connectivity to the drug reference service and retrieves system metadata.
     *
     * <p>Performs a health check by calling multiple web service endpoints to gather
     * information about the connected drug database system. Retrieves the last database update
     * timestamp, identifies the drug database type, and obtains the current database version.</p>
     *
     * @return Map&lt;String, String&gt; containing drug reference metadata with the following keys:
     *         {@code "lastUpdate"} — ISO timestamp of the most recent database update,
     *         {@code "drugDatabase"} — identifier of the drug database system in use,
     *         {@code "version"} — version number of the drug reference database
     * @throws Exception if the drug reference service is unavailable or any web service call fails
     * @since 2026-01-21
     */
    public Map<String, String> verify() throws Exception {
        Vector params = new Vector();
        String lastUpdateTime = getLastUpdateTime();
        Object identifyResult = callWebserviceLite("identify", params);
        if (identifyResult == null) {
            throw new Exception("DrugRef: 'identify' returned no result for server " + server_url);
        }
        String drugDatabase = identifyResult.toString();
        Object versionResult = callWebserviceLite("version", params);
        if (versionResult == null) {
            throw new Exception("DrugRef: 'version' returned no result for server " + server_url);
        }
        String version = versionResult.toString();
        Map<String, String> verify = new HashMap<>();
        verify.put("lastUpdate", lastUpdateTime);
        verify.put("drugDatabase", drugDatabase);
        verify.put("version", version);
        return verify;
    }

    /**
     * Triggers a database update on the DrugRef server and returns the result message.
     *
     * @return String the server's response message describing the update result
     * @throws Exception if the DrugRef service is unavailable or returns a non-zero fault
     * @since 2026-02-26
     */
    public String updateDB() throws Exception {
        Vector params = new Vector();
        return (String) callWebserviceLite("updateDB", params);
    }

    /**
     * Returns the timestamp of the most recent drug database update on the DrugRef server.
     *
     * @return String ISO timestamp of the last database update
     * @throws Exception if the DrugRef service is unavailable or returns a non-zero fault
     * @since 2026-02-26
     */
    public String getLastUpdateTime() throws Exception {
        Vector params = new Vector();
        return (String) callWebserviceLite("getLastUpdateTime", params);
    }

    /**
     * Returns all matching drug search elements using the version 2 search API.
     * Search is case-insensitive.
     *
     * @param searchStr String the drug name or partial name to search for
     * @return Vector of search result Hashtables, each containing name, id, and category fields
     * @throws Exception if the DrugRef service is unavailable or returns a non-zero fault
     * @since 2026-02-26
     */
    public Vector list_drug_element2(String searchStr) throws Exception {
        Vector params = new Vector();
        params.addElement(searchStr);
        return (Vector) callWebserviceLite("list_search_element2", params);
    }

    /**
     * Returns all matching drug search elements using the version 3 search API.
     * Supports an optional right-wildcard-only mode for prefix-only matching.
     *
     * @param searchStr         String the drug name or partial name to search for
     * @param rightWildcardOnly boolean if true, only right-side wildcard matching is applied
     *                          (prefix search); if false, both-side wildcard matching is used
     * @return Vector of search result Hashtables, each containing name, id, and category fields
     * @throws Exception if the DrugRef service is unavailable or returns a non-zero fault
     * @since 2026-02-26
     */
    public Vector list_drug_element3(String searchStr, boolean rightWildcardOnly) throws Exception {
        Vector params = new Vector();
        params.addElement(searchStr);
        String method = rightWildcardOnly ? "list_search_element3_right" : "list_search_element3";
        Vector vec = (Vector) callWebserviceLite(method, params);
        if (vec == null) {
            return new Vector<>();
        }
        return vec;
    }

    /**
     * Returns all matching drug search elements filtered by route of administration.
     * Search is case-insensitive.
     *
     * @param searchStr   String the drug name or partial name to search for
     * @param searchRoute String the route of administration to filter results by (e.g., "oral")
     * @return Vector of search result Hashtables, each containing name, id, and category fields
     * @throws Exception if the DrugRef service is unavailable or returns a non-zero fault
     * @since 2026-02-26
     */
    public Vector list_drug_element_route(String searchStr, String searchRoute) throws Exception {
        Vector params = new Vector();
        params.addElement(searchStr);
        params.addElement(searchRoute);
        return (Vector) callWebserviceLite("list_search_element_route", params);
    }

    /**
     * Returns all brand name products associated with the given DrugRef element ID.
     *
     * @param drugRefId String the DrugRef element identifier
     * @return Vector of brand product Hashtables, each containing name and product details
     * @throws Exception if the DrugRef service is unavailable or returns a non-zero fault
     * @since 2026-02-26
     */
    public Vector list_brands_from_element(String drugRefId) throws Exception {
        Vector params = new Vector();
        params.addElement(drugRefId);
        return (Vector) callWebserviceLite("list_brands_from_element", params);
    }

    /**
     * Returns the drug information page data for the given DrugRef ID.
     *
     * @param drugRefId String the DrugRef identifier for the drug
     * @return Vector of drug information page entries, or null if the service is unavailable
     * @since 2026-02-26
     */
    public Vector getDrugInfoPage(String drugRefId) {
        Vector params = new Vector();
        params.addElement(drugRefId);
        return (Vector) callWebservice("getDrugInfoPage", params);
    }

    /**
     * Returns all matching drug search elements filtered by the given category list,
     * using both-side wildcard matching.
     *
     * @param searchStr String the drug name or partial name to search for
     * @param catVec    Vector of Integer category codes to restrict results to
     * @return Vector of search result Hashtables, or null if the service is unavailable
     * @since 2026-02-26
     */
    public Vector list_search_element_select_categories(String searchStr, Vector catVec) {
        return list_search_element_select_categories(searchStr, catVec, false);
    }

    /**
     * Returns all matching drug search elements filtered by the given category list,
     * with optional right-wildcard-only matching mode.
     *
     * @param searchStr         String the drug name or partial name to search for
     * @param catVec            Vector of Integer category codes to restrict results to
     * @param wildcardRightOnly boolean if true, only right-side wildcard matching is applied;
     *                          if false, both-side wildcard matching is used
     * @return Vector of search result Hashtables, or null if the service is unavailable
     * @since 2026-02-26
     */
    public Vector list_search_element_select_categories(String searchStr, Vector catVec, boolean wildcardRightOnly) {
        Vector params = new Vector();
        params.addElement(searchStr);
        params.addElement(catVec);
        if (wildcardRightOnly) {
            return (Vector) callWebservice("list_search_element_select_categories_right", params);
        } else {
            return (Vector) callWebservice("list_search_element_select_categories", params);
        }
    }

    /**
     * Returns all drugs belonging to the given drug class categories.
     *
     * @param classVec Vector of drug class identifiers to look up
     * @return Vector of drug Hashtables for the specified classes, or null if unavailable
     * @since 2026-02-26
     */
    public Vector list_drug_class(Vector classVec) {
        Vector params = new Vector();
        params.addElement(classVec);
        return (Vector) callWebservice("list_drug_class", params);
    }

    /**
     * Returns all drugs sharing the same active ingredient as the given DrugRef ID.
     *
     * @param drugRefId String the DrugRef identifier for the reference drug
     * @return Vector of Hashtables for drugs with the same active ingredient, or null if unavailable
     * @since 2026-02-26
     */
    public Vector getAISameByDrugCode(String drugRefId) {
        Vector params = new Vector();
        params.addElement(drugRefId);
        return (Vector) callWebservice("getAISameByDrugCode", params);
    }

    /**
     * Returns the dosage form records associated with the given drug code.
     *
     * @param drugCode String the DrugRef drug code to look up forms for
     * @return Vector of form Hashtables, or null if the service is unavailable
     * @since 2026-02-26
     */
    public Vector getFormFromDrugCode(String drugCode) {
        Vector params = new Vector();
        params.addElement(drugCode);
        return (Vector) callWebservice("getFormFromDrugCode", params);
    }

    /**
     * Returns all distinct dosage forms available in the drug reference database.
     *
     * @return Vector of String form names, or null if the service is unavailable
     * @since 2026-02-26
     */
    public Vector getDistinctForms() {
        Vector params = new Vector();
        return (Vector) callWebservice("getDistinctForms", params);
    }

    /**
     * Returns the routes of administration associated with the given drug code.
     *
     * @param drugCode String the DrugRef drug code to look up routes for
     * @return Vector of route Hashtables, or null if the service is unavailable
     * @since 2026-02-26
     */
    public Vector getRouteFromDrugCode(String drugCode) {
        Vector params = new Vector();
        params.addElement(drugCode);
        return (Vector) callWebservice("getRouteFromDrugCode", params);
    }

    /**
     * Returns available strengths for the given drug code.
     *
     * @param drugCode String the DrugRef drug code to look up strengths for
     * @return Vector of strength Hashtables, or null if the service is unavailable
     * @since 2026-02-26
     */
    public Vector getStrengths(String drugCode) {
        Vector params = new Vector();
        params.addElement(drugCode);
        return (Vector) callWebservice("getStrengths", params);
    }

    /**
     * Returns product data for the given DrugRef ID.
     *
     * @param drugRefId String the DrugRef identifier for the drug product
     * @return Vector of product data Hashtables, or null if the service is unavailable
     * @since 2026-02-26
     */
    public Vector getProductData(String drugRefId) {
        Vector params = new Vector();
        params.addElement(drugRefId);
        return (Vector) callWebservice("getProductData", params);
    }

    /**
     * Returns the generic name string for the given DrugRef element ID.
     *
     * @param drugRefId String the DrugRef element identifier
     * @return String the generic name, or null if not found or service is unavailable
     * @since 2026-02-26
     */
    public String getGenericNamefromId(String drugRefId) {
        Vector params = new Vector();
        params.addElement(drugRefId);
        Vector vec = (Vector) callWebservice("getGenericNamefromId", params);
        if (vec == null || vec.isEmpty()) {
            return null;
        }
        return vec.get(0).toString();
    }

    /**
     * Calls the DrugRef XML-RPC service, swallowing all errors and returning null on failure.
     * Used by read-only query methods where returning null on failure is preferable to
     * propagating an exception (e.g., interaction checks, ATC lookups, drug form queries).
     *
     * @param procedureName String the XML-RPC method name to call on the DrugRef server
     * @param params        Vector of typed parameters to pass to the remote method
     * @return Object the deserialized response value, or null if any error occurs
     */
    private Object callWebservice(String procedureName, Vector params) {
        MiscUtils.getLogger().debug("#CALLDRUGREF-" + procedureName);
        Object object = null;
        try {
            SimpleXmlRpcClient server = new SimpleXmlRpcClient(server_url);
            object = server.execute(procedureName, params);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.error("DrugRef: call interrupted for procedure '{}' on {}", procedureName, server_url, exception);
        } catch (XmlRpcFaultException exception) {
            logger.error("DrugRef: XML-RPC fault code={} calling '{}' on {}", exception.code, procedureName, server_url, exception);
        } catch (Exception exception) {
            logger.error("DrugRef: failed to call '{}' on {}", procedureName, server_url, exception);
        }
        return object;
    }

    /**
     * Calls the DrugRef XML-RPC service, propagating non-zero fault codes as exceptions.
     * A fault code of 0 is treated as a "no result" condition — the event is logged at
     * WARN level and null is returned. {@link XmlRpcFaultException} with a non-zero code
     * is re-thrown directly; all other exceptions (network, parse, timeout) are logged
     * as "call failed" and wrapped in a plain {@link Exception}.
     *
     * @param procedureName String the XML-RPC method name to call on the DrugRef server
     * @param params        Vector of typed parameters to pass to the remote method
     * @return Object the deserialized response value, or null if fault code 0 is returned
     * @throws XmlRpcFaultException if the server returns a non-zero XML-RPC fault code
     * @throws Exception            if a network, parse, or timeout error occurs
     */
    private Object callWebserviceLite(String procedureName, Vector params) throws Exception {
        Object object = null;
        try {
            SimpleXmlRpcClient server = new SimpleXmlRpcClient(server_url);
            object = server.execute(procedureName, params);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new Exception("DrugRef: call interrupted for procedure '" + procedureName + "'", exception);
        } catch (XmlRpcFaultException exception) {
            if (exception.code == 0) {
                // Fault code 0 means "no result found" — log at warn level and return null
                logger.warn("DrugRef: no result (fault code 0) for procedure '{}'", procedureName);
            } else {
                logger.error("DrugRef: XML-RPC fault code={} calling '{}' on {}", exception.code, procedureName, server_url, exception);
                throw exception;
            }
        } catch (Exception exception) {
            logger.error("DrugRef: call failed for procedure '{}' on {}", procedureName, server_url, exception);
            throw new Exception("DrugRef: call failed for '" + procedureName + "'", exception);
        }
        return object;
    }

    /**
     * Removes all null entries from the given Vector in-place.
     * Used to sanitize drug parameter lists before sending to the DrugRef service,
     * since XML-RPC arrays do not support null elements.
     *
     * @param v Vector to remove null entries from; no-op if null or already contains no nulls
     * @since 2026-02-26
     */
    public static void removeNullFromVector(Vector v) {
        while (v != null && v.contains(null)) {
            v.remove(null);
        }
    }

    /**
     * Returns drug-drug interactions for the given list of drugs using ATC-based lookup.
     * Null entries are removed from the input list before the service call. The response
     * may be a Vector of interaction records or a Hashtable keyed by interaction source
     * (e.g., "Holbrook Drug Interactions").
     *
     * @param drugs Vector of drug identifiers (ATC codes or drug names) to check for interactions
     * @return Vector of interaction result Hashtables; empty Vector if no interactions found
     * @throws Exception if the DrugRef service is unavailable or returns a non-zero fault
     * @since 2026-02-26
     */
    public Vector getInteractions(Vector drugs) throws Exception {
        removeNullFromVector(drugs);
        Vector params = new Vector();
        params.addElement("interactions_byATC");
        params.addElement(drugs);
        Vector vec = new Vector();
        Object obj = callWebserviceLite("fetch", params);
        if (obj instanceof Vector) {
            vec = (Vector) obj;
        } else if (obj instanceof Hashtable) {
            // Some DrugRef implementations return interactions keyed by source name
            Object holbrook = ((Hashtable) obj).get("Holbrook Drug Interactions");
            if (holbrook instanceof Vector) {
                vec = (Vector) holbrook;
            }
            // Log only key names — not values, which may contain PHI (medication context).
            // Avoid calling .getClass() on values that may be null.
            if (logger.isDebugEnabled()) {
                logger.debug("DrugRef interaction sources returned: {}", ((Hashtable<?, ?>) obj).keySet());
            }
        }
        return vec;
    }

    /**
     * Returns allergy warning records for the given drug against the given allergy list.
     *
     * @param drugs     String the drug name or identifier to check for allergy warnings
     * @param allergies Vector of allergy identifiers (e.g., ATC codes or allergen names) to check against
     * @return Vector of allergy warning Hashtables, or null if the service is unavailable
     * @throws Exception if the DrugRef service is unavailable or returns a non-zero fault
     * @since 2026-02-26
     */
    public Vector getAlergyWarnings(String drugs, Vector allergies) throws Exception {
        Vector params = new Vector();
        params.addElement(drugs);
        params.addElement(allergies);
        return (Vector) callWebserviceLite("get_allergy_warnings", params);
    }

    /**
     * Returns allergy class records for the given list of allergen identifiers.
     *
     * @param allergies Vector of allergy identifiers to resolve to allergy classes
     * @return Vector of allergy class Hashtables, or null if the service is unavailable
     * @throws Exception if the DrugRef service is unavailable or returns a non-zero fault
     * @since 2026-02-26
     */
    public Vector getAllergyClasses(Vector allergies) throws Exception {
        Vector params = new Vector();
        params.addElement(allergies);
        return (Vector) callWebserviceLite("get_allergy_classes", params);
    }

    /**
     * Returns the inactive date record for the given Drug Identification Number.
     * An inactive date indicates when the drug was or will be withdrawn from the market.
     *
     * @param din String the Drug Identification Number to look up the inactive date for
     * @return Vector containing the inactive date record, or null if the service is unavailable
     * @throws Exception if the DrugRef service is unavailable or returns a non-zero fault
     * @since 2026-02-26
     */
    public Vector getInactiveDate(String din) throws Exception {
        Vector params = new Vector();
        params.addElement(din);
        return (Vector) callWebserviceLite("get_inactive_date", params);
    }
}
