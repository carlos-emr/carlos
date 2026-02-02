package ca.openosp.openo.ar2005.impl;

import ca.openosp.openo.ar2005.SignatureType;
import ca.openosp.openo.ar2005.DiscussionTopicsType;
import ca.openosp.openo.ar2005.AdditionalLabInvestigationsType;
import ca.openosp.openo.ar2005.UltrasoundType;
import ca.openosp.openo.ar2005.SubsequentVisitItemType;
import ca.openosp.openo.ar2005.RecommendedImmunoprophylaxisType;
import org.apache.xmlbeans.XmlObject;
import java.util.List;
import java.util.ArrayList;
import ca.openosp.openo.ar2005.RiskFactorItemType;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.AR2;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * Implementation class for the AR2 (Antenatal Record 2005) medical form.
 *
 * This class provides the XMLBeans implementation for managing antenatal care records,
 * which are critical healthcare documents used during pregnancy care in Canadian healthcare
 * settings. The AR2005 form captures comprehensive pregnancy-related information including
 * risk factors, recommended immunizations, subsequent visit tracking, ultrasound results,
 * additional laboratory investigations, discussion topics with patients, and healthcare
 * provider signatures.
 *
 * This implementation extends {@link XmlComplexContentImpl} to leverage Apache XMLBeans
 * framework for XML serialization and deserialization of antenatal record data. It provides
 * thread-safe access to all form elements through synchronized methods that interact with
 * the underlying XML store.
 *
 * The class manages seven primary data categories:
 * <ul>
 *   <li>Risk Factor Lists - Array of pregnancy-related risk factors</li>
 *   <li>Recommended Immunoprophylaxis - Vaccination recommendations for pregnancy</li>
 *   <li>Subsequent Visit Lists - Array of follow-up visit records</li>
 *   <li>Ultrasound - Array of ultrasound examination results</li>
 *   <li>Additional Lab Investigations - Supplementary laboratory test information</li>
 *   <li>Discussion Topics - Patient education and counseling topics covered</li>
 *   <li>Signatures - Healthcare provider signature information for audit compliance</li>
 * </ul>
 *
 * All data access methods are synchronized to ensure thread-safety when multiple
 * healthcare providers may be accessing or updating the same antenatal record.
 *
 * @see AR2
 * @see RiskFactorItemType
 * @see RecommendedImmunoprophylaxisType
 * @see SubsequentVisitItemType
 * @see UltrasoundType
 * @see AdditionalLabInvestigationsType
 * @see DiscussionTopicsType
 * @see SignatureType
 * @since 2026-01-24
 */
public class AR2Impl extends XmlComplexContentImpl implements AR2
{
    private static final long serialVersionUID = 1L;
    private static final QName RISKFACTORLIST$0;
    private static final QName RECOMMENDEDIMMUNOPROPHYLAXIS$2;
    private static final QName SUBSEQUENTVISITLIST$4;
    private static final QName ULTRASOUND$6;
    private static final QName ADDITIONALLABINVESTIGATIONS$8;
    private static final QName DISCUSSIONTOPICS$10;
    private static final QName SIGNATURES$12;

    /**
     * Constructs a new AR2Impl instance with the specified schema type.
     *
     * This constructor initializes the XMLBeans implementation for the AR2005
     * antenatal record form by delegating to the parent XmlComplexContentImpl
     * constructor with the provided schema type definition.
     *
     * @param sType SchemaType the XMLBeans schema type definition for this AR2 document
     */
    public AR2Impl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Retrieves all risk factor items from the antenatal record.
     *
     * This method returns an array of all pregnancy-related risk factors documented
     * in the AR2005 form. Risk factors may include medical history, current pregnancy
     * complications, genetic factors, or other clinical concerns relevant to pregnancy care.
     *
     * The method is thread-safe and uses synchronized access to the underlying XML store.
     *
     * @return RiskFactorItemType[] array of risk factor items, or empty array if none exist
     */
    public RiskFactorItemType[] getRiskFactorListArray() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            final List targetList = new ArrayList();
            this.get_store().find_all_element_users(AR2Impl.RISKFACTORLIST$0, targetList);
            final RiskFactorItemType[] result = new RiskFactorItemType[targetList.size()];
            targetList.toArray(result);
            return result;
        }
    }

    /**
     * Retrieves a specific risk factor item by index from the antenatal record.
     *
     * This method provides indexed access to individual risk factor items in the
     * pregnancy risk assessment list.
     *
     * @param i int the zero-based index of the risk factor item to retrieve
     * @return RiskFactorItemType the risk factor item at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public RiskFactorItemType getRiskFactorListArray(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            RiskFactorItemType target = null;
            target = (RiskFactorItemType)this.get_store().find_element_user(AR2Impl.RISKFACTORLIST$0, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            return target;
        }
    }

    /**
     * Returns the number of risk factor items in the antenatal record.
     *
     * This method provides a count of all documented pregnancy risk factors,
     * which is useful for iteration and validation purposes.
     *
     * @return int the count of risk factor items
     */
    public int sizeOfRiskFactorListArray() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            return this.get_store().count_elements(AR2Impl.RISKFACTORLIST$0);
        }
    }

    /**
     * Replaces all risk factor items with a new array of risk factors.
     *
     * This method completely replaces the existing risk factor list with the
     * provided array, removing all previous risk factor entries.
     *
     * @param riskFactorListArray RiskFactorItemType[] the new array of risk factor items
     */
    public void setRiskFactorListArray(final RiskFactorItemType[] riskFactorListArray) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.arraySetterHelper((XmlObject[])riskFactorListArray, AR2Impl.RISKFACTORLIST$0);
        }
    }

    /**
     * Updates a specific risk factor item at the given index.
     *
     * This method replaces an existing risk factor item at the specified position
     * with a new risk factor item, maintaining the order of other items.
     *
     * @param i int the zero-based index of the risk factor item to update
     * @param riskFactorList RiskFactorItemType the new risk factor item to set at the index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public void setRiskFactorListArray(final int i, final RiskFactorItemType riskFactorList) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            RiskFactorItemType target = null;
            target = (RiskFactorItemType)this.get_store().find_element_user(AR2Impl.RISKFACTORLIST$0, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            target.set((XmlObject)riskFactorList);
        }
    }

    /**
     * Inserts a new risk factor item at the specified index.
     *
     * This method creates and inserts a new risk factor item at the given position,
     * shifting subsequent items to higher indices. The new item is initialized but
     * empty and must be populated by the caller.
     *
     * @param i int the zero-based index at which to insert the new risk factor item
     * @return RiskFactorItemType the newly created risk factor item ready for population
     */
    public RiskFactorItemType insertNewRiskFactorList(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            RiskFactorItemType target = null;
            target = (RiskFactorItemType)this.get_store().insert_element_user(AR2Impl.RISKFACTORLIST$0, i);
            return target;
        }
    }

    /**
     * Adds a new risk factor item to the end of the risk factor list.
     *
     * This method appends a new risk factor item after all existing items in the
     * antenatal record. The new item is initialized but empty and must be populated
     * by the caller with specific risk factor details.
     *
     * @return RiskFactorItemType the newly created risk factor item ready for population
     */
    public RiskFactorItemType addNewRiskFactorList() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            RiskFactorItemType target = null;
            target = (RiskFactorItemType)this.get_store().add_element_user(AR2Impl.RISKFACTORLIST$0);
            return target;
        }
    }

    /**
     * Removes a risk factor item at the specified index.
     *
     * This method deletes the risk factor item at the given position from the
     * antenatal record, shifting subsequent items to lower indices.
     *
     * @param i int the zero-based index of the risk factor item to remove
     */
    public void removeRiskFactorList(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.get_store().remove_element(AR2Impl.RISKFACTORLIST$0, i);
        }
    }

    /**
     * Retrieves the recommended immunoprophylaxis information for the pregnancy.
     *
     * This method returns the vaccination and immunization recommendations documented
     * for the pregnant patient, including recommendations for vaccines such as influenza,
     * Tdap (tetanus, diphtheria, pertussis), and other pregnancy-appropriate immunizations
     * as per Canadian healthcare guidelines.
     *
     * @return RecommendedImmunoprophylaxisType the immunoprophylaxis recommendations, or null if not set
     */
    public RecommendedImmunoprophylaxisType getRecommendedImmunoprophylaxis() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            RecommendedImmunoprophylaxisType target = null;
            target = (RecommendedImmunoprophylaxisType)this.get_store().find_element_user(AR2Impl.RECOMMENDEDIMMUNOPROPHYLAXIS$2, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the recommended immunoprophylaxis information for the pregnancy.
     *
     * This method updates or creates the immunization recommendations section of the
     * antenatal record with new vaccination guidance for the pregnant patient.
     *
     * @param recommendedImmunoprophylaxis RecommendedImmunoprophylaxisType the immunoprophylaxis recommendations to set
     */
    public void setRecommendedImmunoprophylaxis(final RecommendedImmunoprophylaxisType recommendedImmunoprophylaxis) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            RecommendedImmunoprophylaxisType target = null;
            target = (RecommendedImmunoprophylaxisType)this.get_store().find_element_user(AR2Impl.RECOMMENDEDIMMUNOPROPHYLAXIS$2, 0);
            if (target == null) {
                target = (RecommendedImmunoprophylaxisType)this.get_store().add_element_user(AR2Impl.RECOMMENDEDIMMUNOPROPHYLAXIS$2);
            }
            target.set((XmlObject)recommendedImmunoprophylaxis);
        }
    }

    /**
     * Creates and adds a new recommended immunoprophylaxis element to the antenatal record.
     *
     * This method initializes a new immunoprophylaxis recommendations section that can
     * be populated with pregnancy-specific vaccination guidance.
     *
     * @return RecommendedImmunoprophylaxisType the newly created immunoprophylaxis element ready for population
     */
    public RecommendedImmunoprophylaxisType addNewRecommendedImmunoprophylaxis() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            RecommendedImmunoprophylaxisType target = null;
            target = (RecommendedImmunoprophylaxisType)this.get_store().add_element_user(AR2Impl.RECOMMENDEDIMMUNOPROPHYLAXIS$2);
            return target;
        }
    }

    /**
     * Retrieves all subsequent visit records from the antenatal care plan.
     *
     * This method returns an array of all follow-up prenatal visits documented in
     * the AR2005 form. Each visit entry typically includes date, gestational age,
     * vital signs, fetal assessment, and clinical notes from that visit.
     *
     * @return SubsequentVisitItemType[] array of subsequent visit items, or empty array if none exist
     */
    public SubsequentVisitItemType[] getSubsequentVisitListArray() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            final List targetList = new ArrayList();
            this.get_store().find_all_element_users(AR2Impl.SUBSEQUENTVISITLIST$4, targetList);
            final SubsequentVisitItemType[] result = new SubsequentVisitItemType[targetList.size()];
            targetList.toArray(result);
            return result;
        }
    }

    /**
     * Retrieves a specific subsequent visit record by index.
     *
     * This method provides indexed access to individual prenatal follow-up visit
     * records in chronological order.
     *
     * @param i int the zero-based index of the subsequent visit item to retrieve
     * @return SubsequentVisitItemType the subsequent visit item at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public SubsequentVisitItemType getSubsequentVisitListArray(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SubsequentVisitItemType target = null;
            target = (SubsequentVisitItemType)this.get_store().find_element_user(AR2Impl.SUBSEQUENTVISITLIST$4, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            return target;
        }
    }

    /**
     * Returns the number of subsequent visit records in the antenatal care plan.
     *
     * This method provides a count of all documented prenatal follow-up visits,
     * which is useful for tracking the frequency of antenatal care.
     *
     * @return int the count of subsequent visit items
     */
    public int sizeOfSubsequentVisitListArray() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            return this.get_store().count_elements(AR2Impl.SUBSEQUENTVISITLIST$4);
        }
    }

    /**
     * Replaces all subsequent visit records with a new array of visit entries.
     *
     * This method completely replaces the existing subsequent visit list with the
     * provided array, removing all previous visit records.
     *
     * @param subsequentVisitListArray SubsequentVisitItemType[] the new array of subsequent visit items
     */
    public void setSubsequentVisitListArray(final SubsequentVisitItemType[] subsequentVisitListArray) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.arraySetterHelper((XmlObject[])subsequentVisitListArray, AR2Impl.SUBSEQUENTVISITLIST$4);
        }
    }

    /**
     * Updates a specific subsequent visit record at the given index.
     *
     * This method replaces an existing prenatal visit record at the specified position
     * with updated visit information.
     *
     * @param i int the zero-based index of the subsequent visit item to update
     * @param subsequentVisitList SubsequentVisitItemType the new subsequent visit item to set at the index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public void setSubsequentVisitListArray(final int i, final SubsequentVisitItemType subsequentVisitList) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SubsequentVisitItemType target = null;
            target = (SubsequentVisitItemType)this.get_store().find_element_user(AR2Impl.SUBSEQUENTVISITLIST$4, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            target.set((XmlObject)subsequentVisitList);
        }
    }

    /**
     * Inserts a new subsequent visit record at the specified index.
     *
     * This method creates and inserts a new prenatal visit record at the given position,
     * shifting subsequent items to higher indices.
     *
     * @param i int the zero-based index at which to insert the new subsequent visit item
     * @return SubsequentVisitItemType the newly created subsequent visit item ready for population
     */
    public SubsequentVisitItemType insertNewSubsequentVisitList(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SubsequentVisitItemType target = null;
            target = (SubsequentVisitItemType)this.get_store().insert_element_user(AR2Impl.SUBSEQUENTVISITLIST$4, i);
            return target;
        }
    }

    /**
     * Adds a new subsequent visit record to the end of the visit list.
     *
     * This method appends a new prenatal visit record after all existing visits,
     * typically used when documenting a new follow-up appointment.
     *
     * @return SubsequentVisitItemType the newly created subsequent visit item ready for population
     */
    public SubsequentVisitItemType addNewSubsequentVisitList() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SubsequentVisitItemType target = null;
            target = (SubsequentVisitItemType)this.get_store().add_element_user(AR2Impl.SUBSEQUENTVISITLIST$4);
            return target;
        }
    }

    /**
     * Removes a subsequent visit record at the specified index.
     *
     * This method deletes the prenatal visit record at the given position,
     * shifting subsequent items to lower indices.
     *
     * @param i int the zero-based index of the subsequent visit item to remove
     */
    public void removeSubsequentVisitList(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.get_store().remove_element(AR2Impl.SUBSEQUENTVISITLIST$4, i);
        }
    }

    /**
     * Retrieves all ultrasound examination records from the antenatal record.
     *
     * This method returns an array of all prenatal ultrasound results documented
     * in the AR2005 form. Ultrasound records typically include dating scans,
     * anatomy surveys, growth assessments, and other diagnostic imaging performed
     * during pregnancy.
     *
     * @return UltrasoundType[] array of ultrasound items, or empty array if none exist
     */
    public UltrasoundType[] getUltrasoundArray() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            final List targetList = new ArrayList();
            this.get_store().find_all_element_users(AR2Impl.ULTRASOUND$6, targetList);
            final UltrasoundType[] result = new UltrasoundType[targetList.size()];
            targetList.toArray(result);
            return result;
        }
    }

    /**
     * Retrieves a specific ultrasound examination record by index.
     *
     * This method provides indexed access to individual prenatal ultrasound
     * examination results.
     *
     * @param i int the zero-based index of the ultrasound item to retrieve
     * @return UltrasoundType the ultrasound item at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public UltrasoundType getUltrasoundArray(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            UltrasoundType target = null;
            target = (UltrasoundType)this.get_store().find_element_user(AR2Impl.ULTRASOUND$6, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            return target;
        }
    }

    /**
     * Returns the number of ultrasound examination records in the antenatal record.
     *
     * This method provides a count of all documented prenatal ultrasound examinations.
     *
     * @return int the count of ultrasound items
     */
    public int sizeOfUltrasoundArray() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            return this.get_store().count_elements(AR2Impl.ULTRASOUND$6);
        }
    }

    /**
     * Replaces all ultrasound examination records with a new array of results.
     *
     * This method completely replaces the existing ultrasound list with the
     * provided array, removing all previous ultrasound records.
     *
     * @param ultrasoundArray UltrasoundType[] the new array of ultrasound items
     */
    public void setUltrasoundArray(final UltrasoundType[] ultrasoundArray) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.arraySetterHelper((XmlObject[])ultrasoundArray, AR2Impl.ULTRASOUND$6);
        }
    }

    /**
     * Updates a specific ultrasound examination record at the given index.
     *
     * This method replaces an existing ultrasound record at the specified position
     * with updated examination results.
     *
     * @param i int the zero-based index of the ultrasound item to update
     * @param ultrasound UltrasoundType the new ultrasound item to set at the index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public void setUltrasoundArray(final int i, final UltrasoundType ultrasound) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            UltrasoundType target = null;
            target = (UltrasoundType)this.get_store().find_element_user(AR2Impl.ULTRASOUND$6, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            target.set((XmlObject)ultrasound);
        }
    }

    /**
     * Inserts a new ultrasound examination record at the specified index.
     *
     * This method creates and inserts a new ultrasound record at the given position,
     * shifting subsequent items to higher indices.
     *
     * @param i int the zero-based index at which to insert the new ultrasound item
     * @return UltrasoundType the newly created ultrasound item ready for population
     */
    public UltrasoundType insertNewUltrasound(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            UltrasoundType target = null;
            target = (UltrasoundType)this.get_store().insert_element_user(AR2Impl.ULTRASOUND$6, i);
            return target;
        }
    }

    /**
     * Adds a new ultrasound examination record to the end of the ultrasound list.
     *
     * This method appends a new ultrasound record after all existing examinations,
     * typically used when documenting a new prenatal ultrasound scan.
     *
     * @return UltrasoundType the newly created ultrasound item ready for population
     */
    public UltrasoundType addNewUltrasound() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            UltrasoundType target = null;
            target = (UltrasoundType)this.get_store().add_element_user(AR2Impl.ULTRASOUND$6);
            return target;
        }
    }

    /**
     * Removes an ultrasound examination record at the specified index.
     *
     * This method deletes the ultrasound record at the given position,
     * shifting subsequent items to lower indices.
     *
     * @param i int the zero-based index of the ultrasound item to remove
     */
    public void removeUltrasound(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.get_store().remove_element(AR2Impl.ULTRASOUND$6, i);
        }
    }

    /**
     * Retrieves the additional laboratory investigations information from the antenatal record.
     *
     * This method returns supplementary laboratory test information beyond the standard
     * prenatal screening panel. Additional investigations may include specialized tests
     * ordered based on specific maternal risk factors, abnormal findings, or clinical
     * indications during pregnancy care.
     *
     * @return AdditionalLabInvestigationsType the additional lab investigations, or null if not set
     */
    public AdditionalLabInvestigationsType getAdditionalLabInvestigations() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            AdditionalLabInvestigationsType target = null;
            target = (AdditionalLabInvestigationsType)this.get_store().find_element_user(AR2Impl.ADDITIONALLABINVESTIGATIONS$8, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the additional laboratory investigations information for the antenatal record.
     *
     * This method updates or creates the supplementary lab investigations section
     * with new test information ordered for this pregnancy.
     *
     * @param additionalLabInvestigations AdditionalLabInvestigationsType the additional lab investigations to set
     */
    public void setAdditionalLabInvestigations(final AdditionalLabInvestigationsType additionalLabInvestigations) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            AdditionalLabInvestigationsType target = null;
            target = (AdditionalLabInvestigationsType)this.get_store().find_element_user(AR2Impl.ADDITIONALLABINVESTIGATIONS$8, 0);
            if (target == null) {
                target = (AdditionalLabInvestigationsType)this.get_store().add_element_user(AR2Impl.ADDITIONALLABINVESTIGATIONS$8);
            }
            target.set((XmlObject)additionalLabInvestigations);
        }
    }

    /**
     * Creates and adds a new additional laboratory investigations element to the antenatal record.
     *
     * This method initializes a new supplementary lab investigations section that can
     * be populated with specialized test orders and results.
     *
     * @return AdditionalLabInvestigationsType the newly created additional lab investigations element ready for population
     */
    public AdditionalLabInvestigationsType addNewAdditionalLabInvestigations() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            AdditionalLabInvestigationsType target = null;
            target = (AdditionalLabInvestigationsType)this.get_store().add_element_user(AR2Impl.ADDITIONALLABINVESTIGATIONS$8);
            return target;
        }
    }

    /**
     * Retrieves the discussion topics information from the antenatal record.
     *
     * This method returns the patient education and counseling topics that have been
     * discussed during prenatal care. Topics typically include nutrition, exercise,
     * warning signs, labor preparation, breastfeeding, postpartum care, and other
     * important pregnancy-related subjects for patient education and informed consent.
     *
     * @return DiscussionTopicsType the discussion topics, or null if not set
     */
    public DiscussionTopicsType getDiscussionTopics() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            DiscussionTopicsType target = null;
            target = (DiscussionTopicsType)this.get_store().find_element_user(AR2Impl.DISCUSSIONTOPICS$10, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the discussion topics information for the antenatal record.
     *
     * This method updates or creates the patient education section with new
     * discussion topics covered during prenatal counseling sessions.
     *
     * @param discussionTopics DiscussionTopicsType the discussion topics to set
     */
    public void setDiscussionTopics(final DiscussionTopicsType discussionTopics) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            DiscussionTopicsType target = null;
            target = (DiscussionTopicsType)this.get_store().find_element_user(AR2Impl.DISCUSSIONTOPICS$10, 0);
            if (target == null) {
                target = (DiscussionTopicsType)this.get_store().add_element_user(AR2Impl.DISCUSSIONTOPICS$10);
            }
            target.set((XmlObject)discussionTopics);
        }
    }

    /**
     * Creates and adds a new discussion topics element to the antenatal record.
     *
     * This method initializes a new patient education section that can be
     * populated with topics covered during prenatal counseling.
     *
     * @return DiscussionTopicsType the newly created discussion topics element ready for population
     */
    public DiscussionTopicsType addNewDiscussionTopics() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            DiscussionTopicsType target = null;
            target = (DiscussionTopicsType)this.get_store().add_element_user(AR2Impl.DISCUSSIONTOPICS$10);
            return target;
        }
    }

    /**
     * Retrieves the signature information from the antenatal record.
     *
     * This method returns the healthcare provider signature data documenting the
     * clinicians who have contributed to the antenatal care. Signatures are essential
     * for audit trails, regulatory compliance, and medicolegal documentation in
     * Canadian healthcare settings. The signature section typically includes provider
     * identification, credentials, and attestation of care provided.
     *
     * @return SignatureType the signature information, or null if not set
     */
    public SignatureType getSignatures() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SignatureType target = null;
            target = (SignatureType)this.get_store().find_element_user(AR2Impl.SIGNATURES$12, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the signature information for the antenatal record.
     *
     * This method updates or creates the provider signature section with
     * authentication and attestation information for regulatory compliance
     * and audit trail purposes.
     *
     * @param signatures SignatureType the signature information to set
     */
    public void setSignatures(final SignatureType signatures) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SignatureType target = null;
            target = (SignatureType)this.get_store().find_element_user(AR2Impl.SIGNATURES$12, 0);
            if (target == null) {
                target = (SignatureType)this.get_store().add_element_user(AR2Impl.SIGNATURES$12);
            }
            target.set((XmlObject)signatures);
        }
    }

    /**
     * Creates and adds a new signature element to the antenatal record.
     *
     * This method initializes a new provider signature section that can be
     * populated with healthcare provider authentication and attestation details
     * for compliance and audit purposes.
     *
     * @return SignatureType the newly created signature element ready for population
     */
    public SignatureType addNewSignatures() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SignatureType target = null;
            target = (SignatureType)this.get_store().add_element_user(AR2Impl.SIGNATURES$12);
            return target;
        }
    }
    
    static {
        RISKFACTORLIST$0 = new QName("http://www.oscarmcmaster.org/AR2005", "riskFactorList");
        RECOMMENDEDIMMUNOPROPHYLAXIS$2 = new QName("http://www.oscarmcmaster.org/AR2005", "recommendedImmunoprophylaxis");
        SUBSEQUENTVISITLIST$4 = new QName("http://www.oscarmcmaster.org/AR2005", "subsequentVisitList");
        ULTRASOUND$6 = new QName("http://www.oscarmcmaster.org/AR2005", "ultrasound");
        ADDITIONALLABINVESTIGATIONS$8 = new QName("http://www.oscarmcmaster.org/AR2005", "additionalLabInvestigations");
        DISCUSSIONTOPICS$10 = new QName("http://www.oscarmcmaster.org/AR2005", "discussionTopics");
        SIGNATURES$12 = new QName("http://www.oscarmcmaster.org/AR2005", "signatures");
    }
}
