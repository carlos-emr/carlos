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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Service
public class DrugLookUpManager implements DrugLookUp {

    private static Logger logger = MiscUtils.getLogger();

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
                temp.setActive(!((Boolean) h.get("isInactive")));
                temp.setId((Integer) h.get("id"));
                temp.setCategory((Integer) h.get("category"));

                drugs.add(temp);

            }

        } catch (Exception e) {
            logger.error(e.getStackTrace());
            return null;
        }

        return drugs;

    }

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
            logger.error("fullSearch Error", e);
            return null;
        }

        return drugs;

    }

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


    public DrugSearchTo1 details(String id) throws Exception {

        RxDrugRef dr = new RxDrugRef();

        DrugSearchTo1 toReturn = new DrugSearchTo1();

        // TODO: This is not finished! Needs more work once drug ref gets better.

        // { name=STRING, atc=STRING, product=STRING, regional_identifier=STRING, components=VECTOR, drugForm=STRING }

        Hashtable drug = dr.getDrug2(id, true);

        this.extractAndPopulateDetails(toReturn, drug, id);

        return toReturn;

    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    protected void extractAndPopulateDetails(DrugSearchTo1 t, Hashtable h, String id) throws Exception {

        RxDrugRef dr = new RxDrugRef();

        t.setAtc((String) h.get("atc"));
        t.setRegionalId(Integer.parseInt((String) h.get("regional_identifier")));
        t.setForm((String) h.get("drugForm"));
        t.setName((String) h.get("product"));

        populateComponents(t, h.get("components"));

        // Generic Name : { name=STRING, category=INT, id=INT }
        Hashtable generic = dr.getGenericName(id);
        String gn = generic == null ? null : (String) generic.get("name");

        // ugh, this is gross...
        // drugref should return null if nothing is found.
        if (gn == null || "none found".equalsIgnoreCase(gn)) {

            // if there is no generic name found then we try the following in order:
            //  1) the active ingredient
            //  2) the name of the drug
            //
            // This solution is not ideal, drugref seems to do some weird things....

            t.setGenericName(t.getComponents().stream().map(DrugSearchTo1.DrugComponentTo1::getName)
                    .filter(name -> name != null && !name.isBlank()).findFirst().orElse(t.getName()));

        } else {
            t.setGenericName(gn);
        }

    }

    static void populateComponents(DrugSearchTo1 product, Object payload) {
        if (!(payload instanceof Iterable<?> components)) {
            product.setComponentDataComplete(false);
            return;
        }
        boolean complete = true;
        for (Object value : components) {
            var component = new DrugSearchTo1.DrugComponentTo1();
            if (value instanceof java.util.Map<?, ?> fields) {
                if (fields.get("name") instanceof String name) component.setName(name);
                if (fields.get("unit") instanceof String unit) component.setUnit(unit);
                Object strength = fields.get("strength");
                if (strength instanceof Number || strength instanceof String) {
                    String text = strength.toString();
                    try {
                        double amount = new java.math.BigDecimal(text.strip()).doubleValue();
                        if (Double.isFinite(amount)) component.setStrength(amount);
                    } catch (NumberFormatException ignored) {
                        // Retain the catalogue text for review. It is not a numeric dose.
                    }
                    if (component.getStrength() == null) component.setRawStrength(text);
                }
            }
            if (component.getStrength() == null || component.getName() == null || component.getName().isBlank()
                    || component.getUnit() == null || component.getUnit().isBlank()) {
                complete = false;
            }
            product.addComponent(component);
        }
        if (!complete || product.getComponents().isEmpty()) product.setComponentDataComplete(false);
    }
}
