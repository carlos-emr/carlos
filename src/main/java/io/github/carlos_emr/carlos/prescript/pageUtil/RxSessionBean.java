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


package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.prescript.data.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.OscarProperties;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class RxSessionBean implements java.io.Serializable {
    private static final Logger logger = MiscUtils.getLogger();

    private String providerNo = null;
    private int demographicNo = 0;
    private String view = "Active";

    private ArrayList<RxPrescriptionData.Prescription> stash = new ArrayList();
    // private ArrayList stash=new ArrayList();
    private HashMap<Integer, Long> favIdRandomIdMap = new HashMap<Integer, Long>();
    private int stashIndex = -1;
    private Hashtable allergyWarnings = new Hashtable();
    private Hashtable missingAllergyWarnings = new Hashtable();
    private Hashtable workingAllergyWarnings = new Hashtable();
    private ArrayList attributeNames = new ArrayList();
    private String interactingDrugList = ""; //contains hash tables, each hashtable has the a
    private CopyOnWriteArrayList reRxDrugIdList = new CopyOnWriteArrayList<>();
    private HashMap randomIdDrugIdPair = new HashMap();
    private List<HashMap<String, String>> listMedHistory = new ArrayList();

    public List<HashMap<String, String>> getListMedHistory() {
        return listMedHistory;
    }

    public void setListMedHistory(List<HashMap<String, String>> l) {
        listMedHistory = l;
    }

    public HashMap getRandomIdDrugIdPair() {
        return randomIdDrugIdPair;
    }

    public void setRandomIdDrugIdPair(HashMap hm) {
        randomIdDrugIdPair = hm;
    }

    public void addRandomIdDrugIdPair(long r, int d) {
        randomIdDrugIdPair.put(r, d);
    }

    public void addReRxDrugIdList(String s) {
        reRxDrugIdList.add(s);
    }

    public void setReRxDrugIdList(List<String> sList) {
        reRxDrugIdList = (CopyOnWriteArrayList) sList;
    }

    public CopyOnWriteArrayList<String> getReRxDrugIdList() {
        return reRxDrugIdList;
    }

    public void clearReRxDrugIdList() {
        reRxDrugIdList = new CopyOnWriteArrayList<>();
    }

    public String getInteractingDrugList() {
        return interactingDrugList;
    }

    public void setInteractingDrugList(String s) {
        interactingDrugList = s;
    }

    public String getProviderNo() {
        return this.providerNo;
    }

    public void setProviderNo(String RHS) {
        this.providerNo = RHS;
    }

    public String getView() {
        return view;
    }

    public void setView(String view) {
        this.view = view;
    }

    public int getDemographicNo() {
        return this.demographicNo;
    }

    public void setDemographicNo(int RHS) {
        this.demographicNo = RHS;
    }

    public ArrayList getAttributeNames() {
        return this.attributeNames;
    }

    public void setAttributeNames(ArrayList RHS) {
        this.attributeNames = RHS;
    }

    public void addAttributeName(String RHS) {
        this.attributeNames.add(RHS);
    }

    public void addAttributeName(String RHS, int index) {
        this.attributeNames.set(index, RHS);
    }

    //--------------------------------------------------------------------------

    public int getStashIndex() {
        return this.stashIndex;
    }

    public void setStashIndex(int RHS) {
        if (RHS < this.getStashSize()) {
            this.stashIndex = RHS;
        }
    }

    public int getStashSize() {
        return this.stash.size();
    }

    public int getIndexFromRx(int randomId) {
        int ret = -1;
        for (int i = 0; i < stash.size(); i++) {
            if (stash.get(i).getRandomId() == randomId) {
                ret = i;
                break;
            }
        }
        logger.debug("in getIndexFromRx=" + ret);
        return ret;
    }

    public RxPrescriptionData.Prescription[] getStash() {
        RxPrescriptionData.Prescription[] arr = {};

        arr = stash.toArray(arr);

        return arr;
    }

    public ArrayList<RxPrescriptionData.Prescription> getStashList() {
        return this.stash;
    }

    public RxPrescriptionData.Prescription getStashItem(int index) {
        return stash.get(index);
    }

    //return prescript from its random id
    public RxPrescriptionData.Prescription getStashItem2(int randomId) {
        RxPrescriptionData.Prescription psp = null;
        for (RxPrescriptionData.Prescription rx : stash) {
            if (rx.getRandomId() == randomId) {
                psp = rx;
            }
        }
        return psp;
    }

    public void setStashItem(int index, RxPrescriptionData.Prescription item) {
        //this.clearDAM();
        //this.clearDDI();
        stash.set(index, item);
    }

    public int addStashItem(LoggedInInfo loggedInInfo, RxPrescriptionData.Prescription item) {

        int ret = -1;

        int i;
        RxPrescriptionData.Prescription rx;

        //check to see if the item already exists
        //by checking for duplicate brandname and gcn seq no
        //if it exists, return it, else add it.
        for (i = 0; i < this.getStashSize(); i++) {
            rx = this.getStashItem(i);

            if (item.isCustom()) {
                if (rx.isCustom() && rx.getCustomName() != null && item.getCustomName() != null) {
                    if (rx.getCustomName().equals(item.getCustomName())) {
                        ret = i;
                        break;
                    }
                }
            } else {
                if (rx.getBrandName() != null && item.getBrandName() != null) {
                    if (rx.getBrandName().equals(item.getBrandName())
                            && rx.getGCN_SEQNO() == item.getGCN_SEQNO()) {
                        ret = i;
                        break;
                    }
                }
            }
        }

        if (ret > -1) {


            return ret;
        } else {
            stash.add(item);
            preloadInteractions();
            preloadAllergyWarnings(loggedInInfo, item.getAtcCode());


            return this.getStashSize() - 1;
        }

    }

    public void removeStashItem(int index) {
        //    this.clearDDI();
        //    this.clearDAM();
        stash.remove(index);
    }

    public void clearStash() {
        //    this.clearDDI();
        //    this.clearDAM();
        stash = new ArrayList();
    }

    public HashMap<Integer, Long> getFavIdRandomIdMaps() {
        return favIdRandomIdMap;
    }

    public void setFavIdRandomIdMap(HashMap<Integer, Long> stashedIds) {
        this.favIdRandomIdMap = stashedIds;
    }

    public void addNewRandomIdToMap(Integer newId, Long newRandomId) {
        this.favIdRandomIdMap.put(newId, newRandomId);
    }

    public Long getStashedFavId(Integer idToGet) {
        return this.favIdRandomIdMap.get(idToGet);
    }

    //--------------------------------------------------------------------------

    public boolean isValid() {
        if (this.demographicNo > 0
                && this.providerNo != null
                && this.providerNo.length() > 0) {
            return true;
        }
        return false;
    }

    private void preloadInteractions() {
        RxInteractionData interact = RxInteractionData.getInstance();
        interact.preloadInteraction(this.getAtcCodes());
    }

    public void clearAllergyWarnings() {
        allergyWarnings = null;
        allergyWarnings = new Hashtable();

        missingAllergyWarnings = null;
        missingAllergyWarnings = new Hashtable();
    }


    private void preloadAllergyWarnings(LoggedInInfo loggedInInfo, String atccode) {
        try {
            Allergy[] allergies = RxPatientData.getPatient(loggedInInfo, getDemographicNo()).getActiveAllergies();
            RxAllergyWarningWorker worker = new RxAllergyWarningWorker(this, atccode, allergies);
            addToWorkingAllergyWarnings(atccode, worker);
            worker.start();
        } catch (Exception e) {
            logger.error("Error for demographic " + getDemographicNo(), e);
        }
    }

    public void addAllergyWarnings(String atc, Allergy[] allergy) {
        if (atc != null && !atc.isEmpty()) {
            allergyWarnings.put(atc, allergy);
        }
    }

    public void addMissingAllergyWarnings(String atc, Allergy[] allergy) {
        if (atc != null && !atc.isEmpty()) {
            missingAllergyWarnings.put(atc, allergy);
        }
    }

    public void addToWorkingAllergyWarnings(String atc, RxAllergyWarningWorker worker) {
        if (atc != null && !atc.isEmpty()) {
            workingAllergyWarnings.put(atc, worker);
        }
    }

    public void removeFromWorkingAllergyWarnings(String atc) {
        if (atc != null && !atc.isEmpty()) {
            workingAllergyWarnings.remove(atc);
        }
    }


    public Allergy[] getAllergyWarnings(LoggedInInfo loggedInInfo, String atccode) {
        Allergy[] allergies = null;

        //Check to see if Allergy checking property is on and if atccode is not null and if atccode is not "" or "null"

        if (OscarProperties.getInstance().getBooleanProperty("RX_ALLERGY_CHECKING", "yes") && atccode != null && !atccode.equals("") && !atccode.equals("null")) {
            logger.debug("Checking allergy reaction : " + atccode);
            if (allergyWarnings.containsKey(atccode)) {

                allergies = (Allergy[]) allergyWarnings.get(atccode);
            } else if (workingAllergyWarnings.contains(atccode)) {

                RxAllergyWarningWorker worker = (RxAllergyWarningWorker) workingAllergyWarnings.get(atccode);
                if (worker != null) {
                    try {
                        worker.join();

                        // Finished
                    } catch (InterruptedException e) {
                        // Thread was interrupted

                        logger.error("Error", e);
                    }


                }
                allergies = (Allergy[]) allergyWarnings.get(atccode);

            } else {
                logger.debug("NEW ATC CODE for allergy");
                try {
                    RxDrugData drugData = new RxDrugData();
                    Allergy[] allAllergies = RxPatientData.getPatient(loggedInInfo, getDemographicNo()).getActiveAllergies();
                    List<Allergy> missing = new ArrayList<Allergy>();
                    allergies = drugData.getAllergyWarnings(atccode, allAllergies, missing);
                    if (allergies != null) {
                        addAllergyWarnings(atccode, allergies);
                        addMissingAllergyWarnings(atccode, missing.toArray(new Allergy[missing.size()]));
                    }
                } catch (Exception e) {
                    logger.error("Error", e);
                }
            }
        }
        return allergies;
    }


    public Vector getAtcCodes() {
        RxPrescriptionData rxData = new RxPrescriptionData();
        Vector atcCodes = rxData.getCurrentATCCodesByPatient(this.getDemographicNo());
        RxPrescriptionData.Prescription rx;
        for (int i = 0; i < this.getStashSize(); i++) {
            rx = this.getStashItem(i);
            atcCodes.add(rx.getAtcCode());
        }
        return atcCodes;
    }

    public List getRegionalIdentifier() {
        RxPrescriptionData rxData = new RxPrescriptionData();
        List regionalIdentifierCodes = rxData.getCurrentRegionalIdentifiersCodesByPatient(this.getDemographicNo());
        RxPrescriptionData.Prescription rx;
        for (int i = 0; i < this.getStashSize(); i++) {
            rx = this.getStashItem(i);
            regionalIdentifierCodes.add(rx.getRegionalIdentifier());
        }
        return regionalIdentifierCodes;
    }

    public RxDrugData.Interaction[] getInteractions() {
        RxDrugData.Interaction[] interactions = null;
        long start = System.currentTimeMillis();
        long start2 = 0;
        long end2 = 0;
        try {
            start2 = System.currentTimeMillis();
            RxPrescriptionData rxData = new RxPrescriptionData();

            RxInteractionData rxInteract = RxInteractionData.getInstance();
            Vector atcCodes = rxData.getCurrentATCCodesByPatient(this.getDemographicNo());

            logger.debug("atccode " + atcCodes);
            RxPrescriptionData.Prescription rx;
            for (int i = 0; i < this.getStashSize(); i++) {
                rx = this.getStashItem(i);
                if (rx.isValidAtcCode()) {
                    atcCodes.add(rx.getAtcCode());
                }
            }
            logger.debug("atccode 2" + atcCodes);
            if (atcCodes != null && atcCodes.size() > 1) {
                try {
                    interactions = rxInteract.getInteractions(atcCodes);
                    logger.debug("interactions " + interactions.length);
                    for (int i = 0; i < interactions.length; i++) {
                        logger.debug(interactions[i].affectingatc + " " + interactions[i].effect + " " + interactions[i].affectedatc);
                    }
                    Arrays.sort(interactions);
                } catch (Exception e) {
                    logger.error("Error", e);
                }
            }

            end2 = System.currentTimeMillis() - start2;
        } catch (Exception e2) {
        }
        long end = System.currentTimeMillis() - start;


        logger.debug("took " + end + "milliseconds vs " + end2);
        return interactions;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("providerNo", providerNo)
                .append("demographicNo", demographicNo)
                .append("view", view)
                .append("stash", stash)
                .append("favIdRandomIdMap", favIdRandomIdMap)
                .append("stashIndex", stashIndex)
                .append("allergyWarnings", allergyWarnings)
                .append("missingAllergyWarnings", missingAllergyWarnings)
                .append("workingAllergyWarnings", workingAllergyWarnings)
                .append("attributeNames", attributeNames)
                .append("interactingDrugList", interactingDrugList)
                .append("reRxDrugIdList", reRxDrugIdList)
                .append("randomIdDrugIdPair", randomIdDrugIdPair)
                .append("listMedHistory", listMedHistory)
                .toString();
    }
}
