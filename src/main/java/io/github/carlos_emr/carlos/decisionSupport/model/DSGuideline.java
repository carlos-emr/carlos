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


/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.decisionSupport.model;

import java.util.Date;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.AbstractModel;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Abstract JPA entity representing a clinical decision support guideline in the CARLOS EMR system.
 * <p>
 * DSGuideline is the base class for all clinical decision support guideline implementations.
 * It is persisted in the {@code dsGuidelines} database table using single-table inheritance
 * with the {@code engine} column as the discriminator (e.g., "drools" for the Drools-based
 * implementation). Each guideline contains XML-defined clinical conditions, consequences,
 * and parameters that are lazily parsed on first access.
 * </p>
 * <p>
 * The guideline lifecycle is:
 * </p>
 * <ol>
 *   <li>XML content is stored in the {@code xml} LOB column at creation time</li>
 *   <li>On first access to conditions, consequences, or parameters, the XML is parsed
 *       by {@link DSGuidelineFactory} into structured objects</li>
 *   <li>The {@link #evaluate(LoggedInInfo, String)} method (implemented by subclasses)
 *       evaluates the guideline against patient data and returns triggered consequences</li>
 * </ol>
 *
 * @since 2009-07-06
 * @see DSGuidelineFactory for XML parsing logic
 * @see io.github.carlos_emr.carlos.decisionSupport.model.impl.drools.DSGuidelineDrools for the Drools implementation
 * @see DSCondition for condition definitions
 * @see DSConsequence for consequence definitions
 */
@Entity
@Table(name = "dsGuidelines")
@DiscriminatorColumn(name = "engine", discriminatorType = DiscriminatorType.STRING, length = 60)
public abstract class DSGuideline extends AbstractModel<Integer> {

    private static Logger _log = MiscUtils.getLogger();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Integer id;

    @Column(length = 60, nullable = false)
    protected String uuid;

    @Column(length = 100, nullable = false)
    protected String title;

    @Column(nullable = true)
    protected Integer version;

    @Column(length = 60, nullable = false)
    protected String author;

    @Lob
    @Column(nullable = true)
    protected String xml;

    @Column(length = 60, nullable = false)
    protected String source;

    @Column(nullable = true)
    @Temporal(TemporalType.TIMESTAMP)
    protected Date dateStart;

    @Column(nullable = true)
    @Temporal(TemporalType.TIMESTAMP)
    protected Date dateDecomissioned;

    @Column(nullable = true)
    protected char status = 'A';


    //following are populated by parsing
    @Transient
    private List<DSParameter> parameters;
    @Transient
    private List<DSCondition> conditions;
    @Transient
    private List<DSConsequence> consequences;

    @Transient
    private boolean parsed = false;

    /**
     * Gets the title of this clinical guideline.
     *
     * @return String the guideline title used for display and identification
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the title of this clinical guideline.
     *
     * @param title String the guideline title
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Gets the list of clinical conditions for this guideline, parsing from XML if needed.
     * <p>
     * Triggers lazy parsing of the XML content on first access if the guideline
     * has not yet been parsed.
     * </p>
     *
     * @return List of DSCondition objects defining the guideline's evaluation criteria
     */
    public List<DSCondition> getConditions() {
        if (!parsed) this.tryParseFromXml();
        return conditions;
    }

    /**
     * Sets the list of clinical conditions for this guideline.
     *
     * @param conditions List of DSCondition objects defining evaluation criteria
     */
    public void setConditions(List<DSCondition> conditions) {
        this.conditions = conditions;
    }

    /**
     * Gets the list of consequences triggered when guideline conditions are met, parsing from XML if needed.
     * <p>
     * Triggers lazy parsing of the XML content on first access if the guideline
     * has not yet been parsed.
     * </p>
     *
     * @return List of DSConsequence objects defining the guideline's triggered actions
     */
    public List<DSConsequence> getConsequences() {
        if (!parsed) this.tryParseFromXml();
        return consequences;
    }

    /**
     * Sets the list of consequences for this guideline.
     *
     * @param consequences List of DSConsequence objects defining triggered actions
     */
    public void setConsequences(List<DSConsequence> consequences) {
        this.consequences = consequences;
    }

    /**
     * Evaluates this clinical guideline against a patient's data and returns applicable consequences.
     * <p>
     * This abstract method must be implemented by concrete guideline implementations to provide
     * the core evaluation logic. The method examines patient demographics, clinical history,
     * medications, and other relevant data against the guideline's conditions.
     * </p>
     *
     * @param loggedInInfo LoggedInInfo session information for the evaluating provider
     * @param demographicNo String patient identifier for data retrieval
     * @return List of DSConsequence objects representing triggered clinical recommendations or warnings, null if no conditions match
     * @throws DecisionSupportException if evaluation fails due to data access or logic errors
     * @see DSConsequence for consequence types and handling
     */
    public abstract List<DSConsequence> evaluate(LoggedInInfo loggedInInfo, String demographicNo) throws DecisionSupportException;

    /**
     * Evaluates this clinical guideline with provider-specific context and returns applicable consequences.
     *
     * @param loggedInInfo LoggedInInfo session information for the evaluating provider
     * @param demographicNo String patient identifier for data retrieval
     * @param providerNo String provider identifier for provider-specific evaluation context
     * @return List of DSConsequence objects representing triggered clinical recommendations or warnings, null if no conditions match
     * @throws DecisionSupportException if evaluation fails due to data access or logic errors
     */
    public abstract List<DSConsequence> evaluate(LoggedInInfo loggedInInfo, String demographicNo, String providerNo) throws DecisionSupportException;

    /**
     * Evaluates this clinical guideline with dynamic arguments and returns applicable consequences.
     * <p>
     * This method provides the most flexible evaluation interface, allowing dynamic arguments
     * to be passed for specialized guideline logic that requires runtime parameters.
     * </p>
     *
     * @param loggedInInfo LoggedInInfo session information for the evaluating provider
     * @param demographicNo String patient identifier for data retrieval
     * @param providerNo String provider identifier for provider-specific evaluation context
     * @param dynamicArgs List of Object parameters for specialized evaluation logic
     * @return List of DSConsequence objects representing triggered clinical recommendations or warnings, null if no conditions match
     * @throws DecisionSupportException if evaluation fails due to data access or logic errors
     */
    public abstract List<DSConsequence> evaluate(LoggedInInfo loggedInInfo, String demographicNo, String providerNo, List<Object> dynamicArgs) throws DecisionSupportException;

    /**
     * Evaluates this guideline and returns a simple boolean indicating if any conditions were met.
     * <p>
     * This is a convenience method that performs standard evaluation and returns true
     * if any consequences were generated, false otherwise.
     * </p>
     *
     * @param loggedInInfo LoggedInInfo session information for the evaluating provider
     * @param demographicNo String patient identifier for data retrieval
     * @return boolean true if the guideline conditions are met, false otherwise
     * @throws DecisionSupportException if evaluation fails due to data access or logic errors
     */
    public boolean evaluateBoolean(LoggedInInfo loggedInInfo, String demographicNo) throws DecisionSupportException {
        if (evaluate(loggedInInfo, demographicNo) == null) return false;
        return true;
    }

    private void tryParseFromXml() {
        try {
            this.parseFromXml();
        } catch (Exception e) {
            _log.error("Could not parse xml for guideline", e);
        }
    }

    /**
     * Parses the guideline's XML content into structured condition, consequence, and parameter objects.
     * <p>
     * Generally invoked automatically on first access to conditions or consequences.
     * Can be called explicitly to force re-parsing of the XML content.
     * </p>
     *
     * @throws DecisionSupportParseException if the XML content is malformed or contains invalid elements
     */
    public void parseFromXml() throws DecisionSupportParseException {
        DSGuidelineFactory factory = new DSGuidelineFactory();
        DSGuideline newGuideline = factory.createGuidelineFromXml(getXml());
        setParsed(true);
        //copy over
        this.title = newGuideline.getTitle();
        this.conditions = newGuideline.getConditions();
        this.consequences = newGuideline.getConsequences();
        this.parameters = newGuideline.getParameters();
    }

    /**
     * Checks whether this guideline's XML has been parsed into structured objects.
     *
     * @return boolean true if the XML has been parsed, false otherwise
     */
    public boolean isParsed() {
        return parsed;
    }

    /**
     * Sets the parsed status of this guideline.
     * <p>
     * Setting to true suppresses automatic XML parsing on condition/consequence access.
     * Used when manually configuring guideline objects for testing or preview.
     * </p>
     *
     * @param parsed boolean true to indicate parsing is complete
     */
    public void setParsed(boolean parsed) {
        this.parsed = parsed;
    }

    /**
     * Gets the unique database identifier for this guideline.
     *
     * @return Integer the auto-generated primary key
     */
    public Integer getId() {
        return id;
    }

    /**
     * Sets the unique database identifier for this guideline.
     *
     * @param id Integer the primary key value
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * Gets the universally unique identifier for this guideline.
     * <p>
     * The UUID is used for cross-system identification and provider-guideline mappings.
     * </p>
     *
     * @return String the guideline UUID (max 60 characters)
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * Sets the universally unique identifier for this guideline.
     *
     * @param uuid String the guideline UUID
     */
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    /**
     * Gets the version number of this guideline.
     *
     * @return Integer the guideline version, may be null for unversioned guidelines
     */
    public Integer getVersion() {
        return version;
    }

    /**
     * Sets the version number of this guideline.
     *
     * @param version Integer the guideline version
     */
    public void setVersion(Integer version) {
        this.version = version;
    }

    /**
     * Gets the author of this guideline.
     *
     * @return String the guideline author name (max 60 characters)
     */
    public String getAuthor() {
        return author;
    }

    /**
     * Sets the author of this guideline.
     *
     * @param author String the guideline author name
     */
    public void setAuthor(String author) {
        this.author = author;
    }

    /**
     * Gets the XML content defining this guideline's conditions, consequences, and parameters.
     *
     * @return String the raw XML guideline definition, may be null
     */
    public String getXml() {
        return xml;
    }

    /**
     * Sets the XML content defining this guideline's conditions, consequences, and parameters.
     *
     * @param xml String the raw XML guideline definition
     */
    public void setXml(String xml) {
        this.xml = xml;
    }

    /**
     * Gets the source identifier indicating where this guideline originated.
     *
     * @return String the guideline source (max 60 characters)
     */
    public String getSource() {
        return source;
    }

    /**
     * Sets the source identifier indicating where this guideline originated.
     *
     * @param source String the guideline source
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * Gets the date when this guideline became active.
     *
     * @return Date the activation timestamp, may be null
     */
    public Date getDateStart() {
        return dateStart;
    }

    /**
     * Sets the date when this guideline became active.
     *
     * @param dateStart Date the activation timestamp
     */
    public void setDateStart(Date dateStart) {
        this.dateStart = dateStart;
    }

    /**
     * Gets the date when this guideline was decommissioned.
     *
     * @return Date the decommissioning timestamp, may be null if still active
     */
    public Date getDateDecomissioned() {
        return dateDecomissioned;
    }

    /**
     * Sets the date when this guideline was decommissioned.
     *
     * @param dateDecomissioned Date the decommissioning timestamp
     */
    public void setDateDecomissioned(Date dateDecomissioned) {
        this.dateDecomissioned = dateDecomissioned;
    }

    /**
     * Gets the status of this guideline.
     * <p>
     * Default value is 'A' (active). Other values indicate decommissioned or draft states.
     * </p>
     *
     * @return char the guideline status character
     */
    public char getStatus() {
        return status;
    }

    /**
     * Sets the status of this guideline.
     *
     * @param status char the guideline status character ('A' for active)
     */
    public void setStatus(char status) {
        this.status = status;
    }

    /**
     * Gets the parameter definitions for this guideline.
     * <p>
     * Parameters define named Java class references that can be instantiated and
     * inserted into the rules engine during guideline evaluation.
     * </p>
     *
     * @return List of DSParameter objects, may be null if no parameters are defined
     */
    public List<DSParameter> getParameters() {
        return parameters;
    }

    /**
     * Sets the parameter definitions for this guideline.
     *
     * @param parameters List of DSParameter objects defining named class references
     */
    public void setParameters(List<DSParameter> parameters) {
        this.parameters = parameters;
    }


}
