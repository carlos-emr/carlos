package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.XmlObject;
import ca.openosp.openo.ar2005.YesNoNullType;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.PsychosocialType;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * Implementation of the PsychosocialType interface for AR2005 (Antenatal Record 2005) forms.
 *
 * <p>This class provides XML binding functionality for psychosocial assessment data collected
 * during prenatal care. It manages seven key psychosocial risk factors that may impact maternal
 * and fetal health outcomes:</p>
 * <ul>
 *   <li>Poor social support networks</li>
 *   <li>Relationship problems or domestic issues</li>
 *   <li>Emotional depression or mood disorders</li>
 *   <li>Substance abuse (alcohol, drugs, tobacco)</li>
 *   <li>Family violence or safety concerns</li>
 *   <li>Parenting concerns or lack of preparation</li>
 *   <li>Religious or cultural considerations</li>
 * </ul>
 *
 * <p>Each psychosocial factor is represented as a Yes/No/Null value, allowing healthcare providers
 * to document presence, absence, or non-assessment of each factor. This data is critical for:
 * identifying high-risk pregnancies, coordinating appropriate social services, and ensuring
 * comprehensive prenatal care that addresses both medical and psychosocial needs.</p>
 *
 * <p>This implementation uses Apache XMLBeans for XML serialization/deserialization, providing
 * thread-safe access to psychosocial assessment data through synchronized getter/setter methods.
 * All operations are backed by the XMLBeans store mechanism, ensuring data integrity and proper
 * XML schema validation according to the AR2005 specification.</p>
 *
 * @since 2026-01-24
 * @see ca.openosp.openo.ar2005.PsychosocialType
 * @see ca.openosp.openo.ar2005.YesNoNullType
 * @see org.apache.xmlbeans.impl.values.XmlComplexContentImpl
 */
public class PsychosocialTypeImpl extends XmlComplexContentImpl implements PsychosocialType
{
    private static final long serialVersionUID = 1L;
    private static final QName POORTSOCIALSUPPORT$0;
    private static final QName RELATIONSHIPPROBLEMS$2;
    private static final QName EMOTIONALDEPRESSION$4;
    private static final QName SUBSTANCEABUSE$6;
    private static final QName FAMILYVIOLENCE$8;
    private static final QName PARENTINGCONCERNS$10;
    private static final QName RELIGIOUSCULTURAL$12;

    /**
     * Constructs a new PsychosocialTypeImpl instance with the specified schema type.
     *
     * <p>This constructor initializes the XMLBeans complex content implementation with the
     * provided schema type, setting up the internal XML store for managing psychosocial
     * assessment data elements.</p>
     *
     * @param sType SchemaType the XML schema type definition for this psychosocial data element
     */
    public PsychosocialTypeImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Retrieves the poor social support assessment value.
     *
     * <p>This method returns whether the patient has been identified as having poor or inadequate
     * social support networks during pregnancy. Social support is a critical psychosocial factor
     * that can impact maternal mental health, pregnancy outcomes, and postpartum adjustment.</p>
     *
     * @return YesNoNullType the poor social support indicator (Yes/No/Null), or null if not set
     */
    public YesNoNullType getPoortSocialSupport() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(PsychosocialTypeImpl.POORTSOCIALSUPPORT$0, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the poor social support assessment value.
     *
     * <p>This method records whether the patient has been identified as having inadequate social
     * support networks. Healthcare providers use this assessment to identify patients who may
     * benefit from additional social services, support groups, or community resources.</p>
     *
     * @param poortSocialSupport YesNoNullType the poor social support indicator to set (Yes/No/Null)
     */
    public void setPoortSocialSupport(final YesNoNullType poortSocialSupport) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(PsychosocialTypeImpl.POORTSOCIALSUPPORT$0, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(PsychosocialTypeImpl.POORTSOCIALSUPPORT$0);
            }
            target.set((XmlObject)poortSocialSupport);
        }
    }

    /**
     * Creates and adds a new poor social support assessment element.
     *
     * <p>This method creates a new YesNoNullType element in the XML structure for recording
     * poor social support assessment. The returned object can be further configured with the
     * appropriate Yes/No/Null value.</p>
     *
     * @return YesNoNullType a new poor social support element ready for configuration
     */
    public YesNoNullType addNewPoortSocialSupport() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(PsychosocialTypeImpl.POORTSOCIALSUPPORT$0);
            return target;
        }
    }

    /**
     * Retrieves the relationship problems assessment value.
     *
     * <p>This method returns whether the patient has disclosed relationship problems, partner
     * conflict, or domestic issues during pregnancy. Relationship stress can significantly impact
     * maternal well-being and may indicate need for counseling or intervention services.</p>
     *
     * @return YesNoNullType the relationship problems indicator (Yes/No/Null), or null if not set
     */
    public YesNoNullType getRelationshipProblems() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(PsychosocialTypeImpl.RELATIONSHIPPROBLEMS$2, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the relationship problems assessment value.
     *
     * <p>This method records whether the patient has reported relationship difficulties or partner
     * conflict. Documentation of relationship problems helps care teams identify patients who may
     * benefit from couples counseling, conflict resolution resources, or safety planning.</p>
     *
     * @param relationshipProblems YesNoNullType the relationship problems indicator to set (Yes/No/Null)
     */
    public void setRelationshipProblems(final YesNoNullType relationshipProblems) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(PsychosocialTypeImpl.RELATIONSHIPPROBLEMS$2, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(PsychosocialTypeImpl.RELATIONSHIPPROBLEMS$2);
            }
            target.set((XmlObject)relationshipProblems);
        }
    }

    /**
     * Creates and adds a new relationship problems assessment element.
     *
     * <p>This method creates a new YesNoNullType element in the XML structure for recording
     * relationship problems assessment. The returned object can be further configured with the
     * appropriate Yes/No/Null value.</p>
     *
     * @return YesNoNullType a new relationship problems element ready for configuration
     */
    public YesNoNullType addNewRelationshipProblems() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(PsychosocialTypeImpl.RELATIONSHIPPROBLEMS$2);
            return target;
        }
    }

    /**
     * Retrieves the emotional depression assessment value.
     *
     * <p>This method returns whether the patient has been assessed for or diagnosed with emotional
     * depression or mood disorders during pregnancy. Perinatal depression is a significant health
     * concern that affects both maternal well-being and pregnancy outcomes, requiring early
     * identification and appropriate mental health intervention.</p>
     *
     * @return YesNoNullType the emotional depression indicator (Yes/No/Null), or null if not set
     */
    public YesNoNullType getEmotionalDepression() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(PsychosocialTypeImpl.EMOTIONALDEPRESSION$4, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the emotional depression assessment value.
     *
     * <p>This method records whether the patient has been screened for or diagnosed with emotional
     * depression or mood disorders. Documentation supports appropriate referral to mental health
     * services, monitoring for postpartum depression risk, and coordination of comprehensive care.</p>
     *
     * @param emotionalDepression YesNoNullType the emotional depression indicator to set (Yes/No/Null)
     */
    public void setEmotionalDepression(final YesNoNullType emotionalDepression) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(PsychosocialTypeImpl.EMOTIONALDEPRESSION$4, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(PsychosocialTypeImpl.EMOTIONALDEPRESSION$4);
            }
            target.set((XmlObject)emotionalDepression);
        }
    }

    /**
     * Creates and adds a new emotional depression assessment element.
     *
     * <p>This method creates a new YesNoNullType element in the XML structure for recording
     * emotional depression assessment. The returned object can be further configured with the
     * appropriate Yes/No/Null value.</p>
     *
     * @return YesNoNullType a new emotional depression element ready for configuration
     */
    public YesNoNullType addNewEmotionalDepression() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(PsychosocialTypeImpl.EMOTIONALDEPRESSION$4);
            return target;
        }
    }

    /**
     * Retrieves the substance abuse assessment value.
     *
     * <p>This method returns whether the patient has been identified as having substance abuse
     * issues, including alcohol, illicit drugs, or tobacco use during pregnancy. Substance use
     * during pregnancy is a critical risk factor that can significantly impact fetal development
     * and requires immediate intervention and support services.</p>
     *
     * @return YesNoNullType the substance abuse indicator (Yes/No/Null), or null if not set
     */
    public YesNoNullType getSubstanceAbuse() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(PsychosocialTypeImpl.SUBSTANCEABUSE$6, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the substance abuse assessment value.
     *
     * <p>This method records whether the patient has been screened for or disclosed substance use
     * including alcohol, drugs, or tobacco. Documentation enables appropriate referral to addiction
     * services, increased monitoring for fetal complications, and coordination of specialized care.</p>
     *
     * @param substanceAbuse YesNoNullType the substance abuse indicator to set (Yes/No/Null)
     */
    public void setSubstanceAbuse(final YesNoNullType substanceAbuse) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(PsychosocialTypeImpl.SUBSTANCEABUSE$6, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(PsychosocialTypeImpl.SUBSTANCEABUSE$6);
            }
            target.set((XmlObject)substanceAbuse);
        }
    }

    /**
     * Creates and adds a new substance abuse assessment element.
     *
     * <p>This method creates a new YesNoNullType element in the XML structure for recording
     * substance abuse assessment. The returned object can be further configured with the
     * appropriate Yes/No/Null value.</p>
     *
     * @return YesNoNullType a new substance abuse element ready for configuration
     */
    public YesNoNullType addNewSubstanceAbuse() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(PsychosocialTypeImpl.SUBSTANCEABUSE$6);
            return target;
        }
    }

    /**
     * Retrieves the family violence assessment value.
     *
     * <p>This method returns whether the patient has disclosed or been identified as experiencing
     * family violence, domestic abuse, or safety concerns. Family violence is a critical risk
     * factor during pregnancy that requires immediate safety assessment, intervention planning,
     * and coordination with protective services.</p>
     *
     * @return YesNoNullType the family violence indicator (Yes/No/Null), or null if not set
     */
    public YesNoNullType getFamilyViolence() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(PsychosocialTypeImpl.FAMILYVIOLENCE$8, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the family violence assessment value.
     *
     * <p>This method records whether the patient has reported experiencing family violence or
     * domestic abuse. Documentation is essential for safety planning, connecting patients with
     * protective services, and ensuring appropriate follow-up care in a safe environment.</p>
     *
     * @param familyViolence YesNoNullType the family violence indicator to set (Yes/No/Null)
     */
    public void setFamilyViolence(final YesNoNullType familyViolence) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(PsychosocialTypeImpl.FAMILYVIOLENCE$8, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(PsychosocialTypeImpl.FAMILYVIOLENCE$8);
            }
            target.set((XmlObject)familyViolence);
        }
    }

    /**
     * Creates and adds a new family violence assessment element.
     *
     * <p>This method creates a new YesNoNullType element in the XML structure for recording
     * family violence assessment. The returned object can be further configured with the
     * appropriate Yes/No/Null value.</p>
     *
     * @return YesNoNullType a new family violence element ready for configuration
     */
    public YesNoNullType addNewFamilyViolence() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(PsychosocialTypeImpl.FAMILYVIOLENCE$8);
            return target;
        }
    }

    /**
     * Retrieves the parenting concerns assessment value.
     *
     * <p>This method returns whether the patient has expressed concerns about parenting readiness,
     * parenting skills, or preparation for the transition to parenthood. Identifying parenting
     * concerns enables healthcare providers to offer prenatal education, parenting classes, and
     * supportive resources to promote positive parent-child relationships.</p>
     *
     * @return YesNoNullType the parenting concerns indicator (Yes/No/Null), or null if not set
     */
    public YesNoNullType getParentingConcerns() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(PsychosocialTypeImpl.PARENTINGCONCERNS$10, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the parenting concerns assessment value.
     *
     * <p>This method records whether the patient has reported concerns about parenting or lack of
     * preparation for childcare responsibilities. Documentation supports referral to prenatal
     * classes, parenting support groups, and educational resources to build parenting confidence.</p>
     *
     * @param parentingConcerns YesNoNullType the parenting concerns indicator to set (Yes/No/Null)
     */
    public void setParentingConcerns(final YesNoNullType parentingConcerns) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(PsychosocialTypeImpl.PARENTINGCONCERNS$10, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(PsychosocialTypeImpl.PARENTINGCONCERNS$10);
            }
            target.set((XmlObject)parentingConcerns);
        }
    }

    /**
     * Creates and adds a new parenting concerns assessment element.
     *
     * <p>This method creates a new YesNoNullType element in the XML structure for recording
     * parenting concerns assessment. The returned object can be further configured with the
     * appropriate Yes/No/Null value.</p>
     *
     * @return YesNoNullType a new parenting concerns element ready for configuration
     */
    public YesNoNullType addNewParentingConcerns() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(PsychosocialTypeImpl.PARENTINGCONCERNS$10);
            return target;
        }
    }

    /**
     * Retrieves the religious or cultural considerations assessment value.
     *
     * <p>This method returns whether the patient has identified religious or cultural factors that
     * may impact pregnancy care, birth preferences, or healthcare decision-making. Understanding
     * religious and cultural beliefs enables providers to deliver culturally competent,
     * patient-centered care that respects individual values and traditions.</p>
     *
     * @return YesNoNullType the religious/cultural considerations indicator (Yes/No/Null), or null if not set
     */
    public YesNoNullType getReligiousCultural() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(PsychosocialTypeImpl.RELIGIOUSCULTURAL$12, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the religious or cultural considerations assessment value.
     *
     * <p>This method records whether the patient has religious or cultural beliefs that should be
     * considered in care planning. Documentation ensures that care teams are aware of and can
     * accommodate religious practices, cultural traditions, and specific preferences during
     * pregnancy, labor, delivery, and postpartum care.</p>
     *
     * @param religiousCultural YesNoNullType the religious/cultural considerations indicator to set (Yes/No/Null)
     */
    public void setReligiousCultural(final YesNoNullType religiousCultural) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(PsychosocialTypeImpl.RELIGIOUSCULTURAL$12, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(PsychosocialTypeImpl.RELIGIOUSCULTURAL$12);
            }
            target.set((XmlObject)religiousCultural);
        }
    }

    /**
     * Creates and adds a new religious or cultural considerations assessment element.
     *
     * <p>This method creates a new YesNoNullType element in the XML structure for recording
     * religious or cultural considerations. The returned object can be further configured with
     * the appropriate Yes/No/Null value.</p>
     *
     * @return YesNoNullType a new religious/cultural considerations element ready for configuration
     */
    public YesNoNullType addNewReligiousCultural() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(PsychosocialTypeImpl.RELIGIOUSCULTURAL$12);
            return target;
        }
    }
    
    static {
        POORTSOCIALSUPPORT$0 = new QName("http://www.oscarmcmaster.org/AR2005", "poortSocialSupport");
        RELATIONSHIPPROBLEMS$2 = new QName("http://www.oscarmcmaster.org/AR2005", "relationshipProblems");
        EMOTIONALDEPRESSION$4 = new QName("http://www.oscarmcmaster.org/AR2005", "emotionalDepression");
        SUBSTANCEABUSE$6 = new QName("http://www.oscarmcmaster.org/AR2005", "substanceAbuse");
        FAMILYVIOLENCE$8 = new QName("http://www.oscarmcmaster.org/AR2005", "familyViolence");
        PARENTINGCONCERNS$10 = new QName("http://www.oscarmcmaster.org/AR2005", "parentingConcerns");
        RELIGIOUSCULTURAL$12 = new QName("http://www.oscarmcmaster.org/AR2005", "religiousCultural");
    }
}
