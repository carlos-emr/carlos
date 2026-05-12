/**
 * Copyright (c) 2013-2015. Department of Computer Science, University of Victoria. All Rights Reserved.
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
 * Department of Computer Science
 * LeadLab
 * University of Victoria
 * Victoria, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.managers;


import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DrugSearchTo1;
import org.springframework.stereotype.Service;
import io.github.carlos_emr.carlos.prescript.util.RxDrugRef;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

/**
 * Service implementation for looking up drugs against the internal or external drug reference database (RxDrugRef).
 * This manager provides methods to search for drugs by name, retrieve detailed drug information
 * including components and generic names, and handle legacy XML-RPC conversions from DrugRef.
 *
 * @since 2026-05-05
 */
@Service
public class DrugLookUpManager implements DrugLookUp {

    private static Logger logger = MiscUtils.getLogger();

    /**
     * Performs a standard search for drugs by a partial name or query string.
     * Maps the legacy DrugRef Hashtable output into structured {@link DrugSearchTo1} DTOs.
     * 
     * @param s The search string (e.g., partial brand or generic name).
     * @return List of matching drug DTOs.
     * @since 2026-05-05
     */
    public List<DrugSearchTo1> search(String s) {

        RxDrugRef dr = new RxDrugRef();

        List<DrugSearchTo1> drugs = new ArrayList<DrugSearchTo1>();

        try {

            // has structure: {isInactive=BOOLEAN, name=STRING, category=INT, id=INT},


            // This isn't the best approach to managing results from the RxDrugRef
            // we should have a hashtable type.
            // Would require refactor to the RxDrugRef class.
            Vector<Hashtable> v = (Vector<Hashtable>) dr.list_drug_element3(s, false);

            DrugSearchTo1 temp;

            for (Hashtable h : v) {

                temp = new DrugSearchTo1();
                temp.setName((String) h.get("name"));
                if (h.containsKey("isInactive")) {
                    temp.setActive(!((Boolean) h.get("isInactive")));
                } else {
                    temp.setActive(true);
                }
                temp.setId((Integer) h.get("id"));
                temp.setCategory((Integer) h.get("category"));

                drugs.add(temp);

            }

        } catch (Exception e) {
            logger.error("search Error", e);
            return null;
        }

        return drugs;

    }

    /**
     * Performs a full, potentially less-filtered search for drugs by a partial name.
     * Useful for comprehensive drug lookups.
     * 
     * @param s The search string.
     * @return List of matching drug DTOs.
     * @since 2026-05-05
     */
    public List<DrugSearchTo1> fullSearch(String s) {

        RxDrugRef dr = new RxDrugRef();

        List<DrugSearchTo1> drugs = new ArrayList<DrugSearchTo1>();

        try {

            // has structure: {isInactive=BOOLEAN, name=STRING, category=INT, id=INT},


            // This isn't the best approach to managing results from the RxDrugRef
            // we should have a hashtable type.
            // Would require refactor to the RxDrugRef class.
            Vector<Hashtable> v = (Vector<Hashtable>) dr.list_drug_element(s);

            DrugSearchTo1 temp;

            for (Hashtable h : v) {

                temp = new DrugSearchTo1();
                temp.setName((String) h.get("name"));
                if (h.containsKey("isInactive")) {
                    temp.setActive(!((Boolean) h.get("isInactive")));
                }
                temp.setId((Integer) h.get("id"));
                temp.setCategory((Integer) h.get("category"));

                drugs.add(temp);

            }

        } catch (Exception e) {
            logger.error("searchByElement Error", e);
            return null;
        }

        return drugs;

    }

    /**
     * Searches for drugs based specifically on active ingredients or element names.
     * 
     * @param s The active ingredient or element name to search for.
     * @return List of brand drugs containing that element.
     * @since 2026-05-05
     */
    @Override
    public List<DrugSearchTo1> searchByElement(String s) {
        RxDrugRef dr = new RxDrugRef();

        List<DrugSearchTo1> drugs = new ArrayList<DrugSearchTo1>();

        try {

            // has structure: {isInactive=BOOLEAN, name=STRING, category=INT, id=INT},


            // This isn't the best approach to managing results from the RxDrugRef
            // we should have a hashtable type.
            // Would require refactor to the RxDrugRef class.
            Vector<Hashtable> v = (Vector<Hashtable>) dr.list_brands_from_element(s);

            DrugSearchTo1 temp;

            for (Hashtable h : v) {

                temp = new DrugSearchTo1();
                temp.setName((String) h.get("name"));
                if (h.containsKey("isInactive")) {
                    temp.setActive(!((Boolean) h.get("isInactive")));
                }
                temp.setId((Integer) h.get("id"));
                temp.setCategory((Integer) h.get("category"));

                drugs.add(temp);

            }

        } catch (Exception e) {
            logger.error("fullSearch Error", e);
            return null;
        }

        return drugs;

    }

    /**
     * Retrieves detailed information about a specific drug, including its ATC code, form,
     * generic name, and chemical components/strengths.
     * 
     * @param id The drug identifier from DrugRef.
     * @return A populated {@link DrugSearchTo1} containing the details.
     * @throws Exception if the lookup fails.
     * @since 2026-05-05
     */
    public DrugSearchTo1 details(String id) throws Exception {

        RxDrugRef dr = new RxDrugRef();

        DrugSearchTo1 toReturn = new DrugSearchTo1();

        // TODO: This is not finished! Needs more work once drug ref gets better.

        // { name=STRING, atc=STRING, product=STRING, regional_identifier=STRING, components=VECTOR, drugForm=STRING }

        Hashtable drug = dr.getDrug2(id, true);

        this.extractAndPopulateDetails(toReturn, drug, id);

        return toReturn;

    }

    /**
     * Internal helper to map the nested Hashtable structure from DrugRef into the DTO.
     * Resolves edge cases where generic names are missing by falling back to active ingredients.
     * 
     * @param t The target DTO to populate.
     * @param h The source Hashtable from DrugRef.
     * @param id The drug identifier.
     * @throws Exception if mapping fails.
     */
    protected void extractAndPopulateDetails(DrugSearchTo1 t, Hashtable h, String id) throws Exception {

        RxDrugRef dr = new RxDrugRef();

        t.setAtc((String) h.get("atc"));
        t.setRegionalId(Integer.parseInt((String) h.get("regional_identifier")));
        t.setForm((String) h.get("drugForm"));
        t.setName((String) h.get("product"));

        // Component: { name=STRING, strength=INT, unit=STRING }
        Vector<Hashtable<String, Object>> components = (Vector<Hashtable<String, Object>>) h.get("components");

        DrugSearchTo1.DrugComponentTo1 comp;

        for (Hashtable<String, Object> o : components) {
            comp = new DrugSearchTo1.DrugComponentTo1();
            comp.setName((String) o.get("name"));
            comp.setStrength((Double) o.get("strength"));
            comp.setUnit((String) o.get("unit"));
            t.addComponent(comp);
        }

        // Generic Name : { name=STRING, category=INT, id=INT }
        Hashtable generic = dr.getGenericName(id);
        String gn = (String) generic.get("name");

        // ugh, this is gross...
        // drugref should return null if nothing is found.
        if (gn.toLowerCase().equals("none found")) {

            // if there is no generic name found then we try the following in order:
            //  1) the active ingredient
            //  2) the name of the drug
            //
            // This solution is not ideal, drugref seems to do some weird things....

            if (t.getComponents().size() >= 1) {
                t.setGenericName(t.getComponents().get(0).getName());
            } else {
                t.setGenericName(t.getName());
            }

        } else {
            t.setGenericName(gn);
        }

    }


}
