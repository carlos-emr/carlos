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


package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.prescript.data.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.CarlosProperties;

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

    /**
     * Adds a source drug id to the ReRx list; {@code saveDrug()} archives a listed source when its
     * replacement is saved. Callers that stage a copy use {@code RxRePrescribe2Action.recordReRxSource},
     * which adds an id once.
     *
     * @param s the source drug id
     */
    public void addReRxDrugIdList(String s) {
        reRxDrugIdList.add(s);
    }

    public void setReRxDrugIdList(List<String> sList) {
        reRxDrugIdList = (CopyOnWriteArrayList) sList;
    }

    public CopyOnWriteArrayList<String> getReRxDrugIdList() {
        return reRxDrugIdList;
    }

    /**
     * Empties the ReRx list (after a completed save or an explicit reset).
     */
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

    //--------------------------------------------------------------------------

    /**
     * The selected staged item (the cursor), or -1 when none is selected. Always within the
     * stash: a cursor left past the end by a removal that bypassed {@link #removeStashItem}
     * (for example an iterator over {@link #getStashList()}) is pulled back to the last item.
     */
    public int getStashIndex() {
        if (this.stashIndex >= this.stash.size()) {
            this.stashIndex = this.stash.size() - 1;
        }
        return this.stashIndex;
    }

    /**
     * Moves the cursor. Only -1 (nothing selected) or an index of a staged item is accepted; any
     * other value (negative, past the end) is ignored, so a bad index from a request can never
     * point the cursor at nothing and later make a write fail or touch the wrong item.
     */
    public void setStashIndex(int RHS) {
        if (RHS >= -1 && RHS < this.getStashSize()) {
            this.stashIndex = RHS;
        }
    }

    /**
     * The staged item the cursor selects, or {@code null} when nothing (valid) is selected.
     */
    public RxPrescriptionData.Prescription getCurrentStashItem() {
        int index = getStashIndex();
        return index >= 0 ? this.stash.get(index) : null;
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

    /**
     * The staged prescription carrying this random id, or {@code null} when the stash
     * does not hold one.
     *
     * <p>What a miss means is the caller's business, and callers differ: the
     * previous-instructions lookup renders its empty modal, {@code saveCustomName} logs
     * and abandons the rename, {@code normalDrugSetCustom} skips its conversion. This
     * method reports the miss and nothing more.</p>
     *
     * <p>The null ELEMENT check inside the scan is defensive rather than a reproduced
     * defect: no caller puts a null in the stash today. It is here because a
     * NullPointerException raised while scanning would escape as a 500 rather than as
     * the null this method contracts to return — and on the AJAX history lookup, whose
     * modal only opens from the success callback, a 500 is a control that does nothing
     * at all.</p>
     */
    public RxPrescriptionData.Prescription getStashItem2(int randomId) {
        RxPrescriptionData.Prescription psp = null;
        for (RxPrescriptionData.Prescription rx : stash) {
            if (rx != null && rx.getRandomId() == randomId) {
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

    /**
     * Stages a prescription for this bean's patient. An item equal to one already staged (same drug, by
     * value) is not added twice; the returned index is the item's position either way.
     *
     * @param loggedInInfo the logged-in provider, for allergy and interaction checks
     * @param item         the prescription to stage
     * @return the item's index in the stash
     */
    public int addStashItem(LoggedInInfo loggedInInfo, RxPrescriptionData.Prescription item) {
        // Two windows of the same patient share this bean. Insertion runs under the bean's
        // monitor, the same one RxStashIds allocates keys under, so a key two concurrent staging
        // requests both saw as free cannot end up on two cards (#3908).
        synchronized (this) {
            return addStashItemLocked(loggedInInfo, item);
        }
    }

    private int addStashItemLocked(LoggedInInfo loggedInInfo, RxPrescriptionData.Prescription item) {
        // A card for the same drug is not staged twice: return the existing card's position.
        int existing = indexOfSameCard(item);
        if (existing > -1) {
            return existing;
        }
        // A staged card is identified by its key when it is closed, edited or saved, so a key
        // another card already carries (a draw that raced with another window) is replaced
        // before the card is added; the caller renders the card from this same object.
        if (RxStashIds.inUseByAnother(this, item, item.getRandomId())) {
            item.setRandomId(RxStashIds.nextUniqueLocked(this, RxStashIds.DEFAULT_BOUND));
        }
        stash.add(item);
        preloadInteractions();
        preloadAllergyWarnings(loggedInInfo, item.getAtcCode());
        return this.getStashSize() - 1;
    }

    /** The position of the staged card for the same drug as {@code item}, or -1 when there is none. */
    private int indexOfSameCard(RxPrescriptionData.Prescription item) {
        for (int i = 0; i < this.getStashSize(); i++) {
            if (isSameCard(this.getStashItem(i), item)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Custom cards are the same card when their custom names match; catalogue cards when brand
     * name and GCN sequence number match. GCN_SEQNO is a String: {@code ==} compared references, so
     * two stash entries for the same drug built from different requests never matched.
     */
    private static boolean isSameCard(RxPrescriptionData.Prescription rx, RxPrescriptionData.Prescription item) {
        if (item.isCustom()) {
            return rx.isCustom() && rx.getCustomName() != null && rx.getCustomName().equals(item.getCustomName());
        }
        return rx.getBrandName() != null && rx.getBrandName().equals(item.getBrandName())
                && Objects.equals(rx.getGCN_SEQNO(), item.getGCN_SEQNO());
    }

    /**
     * A stash key no staged card carries, drawn under this bean's monitor: the monitor
     * {@link #addStashItem} inserts under, so a key two concurrent staging requests both saw as
     * free cannot end up on two cards (#3908). {@link RxStashIds#nextUnique} delegates here.
     *
     * @param bound the largest key, see {@link RxStashIds#DEFAULT_BOUND}
     * @return an unused key
     */
    public synchronized long nextUniqueStashKey(int bound) {
        return RxStashIds.nextUniqueLocked(this, bound);
    }

    /**
     * {@link RxStashIds#acceptOrNext}: the client's proposed key when it is well formed and unused,
     * else a fresh unique key, decided under this bean's monitor.
     *
     * @param clientKey the proposed key, may be {@code null} or malformed
     * @param bound     the range for a fresh key
     * @return a key no other staged card uses
     */
    public synchronized long acceptOrNextStashKey(String clientKey, int bound) {
        return RxStashIds.acceptOrNextLocked(this, clientKey, bound);
    }

    /**
     * Removes one staged item and keeps the cursor on the same item where it still exists: a
     * removal before the cursor shifts it down with the list, and a removal of the selected (last)
     * item leaves it on the new last item. An index outside the stash is ignored.
     */
    public void removeStashItem(int index) {
        //    this.clearDDI();
        //    this.clearDAM();
        if (index < 0 || index >= stash.size()) {
            return;
        }
        stash.remove(index);
        if (index < stashIndex) {
            stashIndex--;
        } else if (stashIndex >= stash.size()) {
            stashIndex = stash.size() - 1;
        }
    }

    /**
     * Discards every staged card and resets the cursor to -1.
     */
    public void clearStash() {
        //    this.clearDDI();
        //    this.clearDAM();
        stash = new ArrayList();
        stashIndex = -1;
    }

    /**
     * Drops stash items that a completed save already persisted, keeping unsaved drafts and the
     * selected draft's position.
     *
     * <p>A patient's bean is reused when Rx is reopened ({@link RxSessionBeanResolver#activate}),
     * so without this a prescription saved just before the window was closed would reappear
     * staged and could be saved twice. {@code drugId} is 0 until {@code Prescription.Save}
     * writes the row, so {@code drugId > 0} means "already saved". {@code script_no} is not a
     * safe signal: a staged re-prescription copies its source drug's script number.</p>
     *
     * @since 2026-09-24
     */
    public void removePersistedStashItems() {
        RxPrescriptionData.Prescription selected =
                (stashIndex >= 0 && stashIndex < stash.size()) ? stash.get(stashIndex) : null;
        stash.removeIf(rx -> rx != null && rx.getDrugId() > 0);
        if (selected != null && stash.contains(selected)) {
            stashIndex = stash.indexOf(selected);
        } else {
            stashIndex = stash.size() - 1;
        }
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
            // The demographic number is a PHI-correlating identifier; log only the failure.
            logger.error("Allergy warning check failed ({})", e.getClass().getSimpleName());
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

        if (CarlosProperties.getInstance().getBooleanProperty("RX_ALLERGY_CHECKING", "yes") && atccode != null && !atccode.equals("") && !atccode.equals("null")) {
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

            RxPrescriptionData.Prescription rx;
            for (int i = 0; i < this.getStashSize(); i++) {
                rx = this.getStashItem(i);
                if (rx.isValidAtcCode()) {
                    atcCodes.add(rx.getAtcCode());
                }
            }
            // Counts only: the patient's ATC codes and interactions describe their medications.
            logger.debug("Interaction check over {} ATC code(s)", atcCodes == null ? 0 : atcCodes.size());
            if (atcCodes != null && atcCodes.size() > 1) {
                try {
                    interactions = rxInteract.getInteractions(atcCodes);
                    logger.debug("interactions {}", interactions.length);
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
                .append("interactingDrugList", interactingDrugList)
                .append("reRxDrugIdList", reRxDrugIdList)
                .append("randomIdDrugIdPair", randomIdDrugIdPair)
                .append("listMedHistory", listMedHistory)
                .toString();
    }
}
