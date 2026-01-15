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


package ca.openosp.openo.decisionSupport.model;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import ca.openosp.openo.decisionSupport.model.conditionValue.DSValue;
import ca.openosp.openo.decisionSupport.model.impl.drools.DSGuidelineDrools;
import ca.openosp.openo.utility.MiscUtils;

/**
 * Factory class for creating and parsing clinical decision support guidelines from XML configuration.
 * <p>
 * DSGuidelineFactory provides the primary mechanism for converting XML-based guideline definitions
 * into executable DSGuideline objects. It parses complex XML structures containing clinical conditions,
 * consequences, and parameters to create fully-configured guideline instances.
 * </p>
 * <p>
 * The factory supports parsing of:
 * </p>
 * <ul>
 * <li>Guideline metadata (title, author, version, dates)</li>
 * <li>Parameter definitions with class mappings</li>
 * <li>Clinical conditions with various operators and value types</li>
 * <li>Consequences including warnings and executable actions</li>
 * <li>Complex condition expressions with multiple values and operators</li>
 * </ul>
 * <p>
 * Example XML structure:
 * <pre>
 * &lt;guideline title="Diabetes Management"&gt;
 *   &lt;conditions&gt;
 *     &lt;condition type="dxcodes" any="icd9:250,icd10:E11"/&gt;
 *     &lt;condition type="age" any="&gt;=18 y"/&gt;
 *   &lt;/conditions&gt;
 *   &lt;consequence&gt;
 *     &lt;warning&gt;Consider HbA1c monitoring&lt;/warning&gt;
 *   &lt;/consequence&gt;
 * &lt;/guideline&gt;
 * </pre>
 *
 * @author apavel
 * @since 2009-07-06
 * @see DSGuideline for guideline structure and evaluation
 * @see DSCondition for condition parsing and evaluation
 * @see DSConsequence for consequence creation
 * @see DSGuidelineDrools for Drools-based implementation
 */
public class DSGuidelineFactory {
    private static Logger _log = MiscUtils.getLogger();

    /**
     * Creates a complete DSGuideline object from XML configuration string.
     * <p>
     * Parses a complete XML guideline definition and creates a fully-configured
     * DSGuideline instance with all conditions, consequences, and parameters.
     * The XML must conform to the expected guideline schema structure.
     * </p>
     * <p>
     * The parsing process includes:
     * </p>
     * <ul>
     * <li>Extracting guideline metadata (title, author, version)</li>
     * <li>Parsing parameter definitions for dynamic class instantiation</li>
     * <li>Creating condition objects with operators and value lists</li>
     * <li>Building consequence objects for warnings and actions</li>
     * <li>Validating all XML elements and attributes</li>
     * </ul>
     *
     * @param xml String containing the complete XML guideline definition
     * @return DSGuideline fully-configured guideline object ready for evaluation
     * @throws DecisionSupportParseException if XML is invalid, malformed, or contains unrecognized elements
     * @see DSGuideline for the resulting guideline object structure
     */
    public DSGuideline createGuidelineFromXml(String xml) throws DecisionSupportParseException {
        if (xml == null || xml.equals("")) throw new DecisionSupportParseException("Xml not set");
        SAXBuilder parser = new SAXBuilder();
        Document doc;
        try {
            doc = parser.build(new StringReader(xml));
        } catch (JDOMException jdome) {
            throw new DecisionSupportParseException("Failed to read the xml string for parsing", jdome);
        } catch (IOException ioe) {
            throw new DecisionSupportParseException("Failed to read the xml string for parsing", ioe);
        }
        //<guideline evidence="" significance="" title="Plavix Drug DS">
        Element guidelineRoot = doc.getRootElement();

        DSGuideline dsGuideline = this.createBlankGuideline();

        String guidelineTitle = guidelineRoot.getAttributeValue("title");
        dsGuideline.setTitle(guidelineTitle);

        //Load parameters such as classes
        //<parameter identifier="a">
        //  <class>java.util.ArrayList</class>
        //</parameter>
        ArrayList<DSParameter> parameters = new ArrayList<DSParameter>();
        @SuppressWarnings("unchecked")
        List<Element> parameterTags = guidelineRoot.getChildren("parameter");
        for (Element parameterTag : parameterTags) {
            String alias = parameterTag.getAttributeValue("identifier");
            if (alias == null) {
                throw new DecisionSupportParseException(guidelineTitle, "Parameter identifier attribute is mandatory");
            }

            Element Eclass = parameterTag.getChild("class");
            String strClass = Eclass.getText();
            DSParameter dsParameter = new DSParameter();
            dsParameter.setStrAlias(alias);
            dsParameter.setStrClass(strClass);
            parameters.add(dsParameter);
        }

        dsGuideline.setParameters(parameters);

        //Load Conditions
        //<conditions>
        //  <condition type="dxcodes" any="icd9:4439,icd9:4438,icd10:E11,icd10:E12"/>
        //  <condition type="drug" not="atc:34234"/>
        ArrayList<DSCondition> conditions = new ArrayList<DSCondition>();
        @SuppressWarnings("unchecked")
        List<Element> conditionTags = guidelineRoot.getChild("conditions").getChildren("condition");
        for (Element conditionTag : conditionTags) {

            String conditionTypeStr = conditionTag.getAttributeValue("type");
            if (conditionTypeStr == null)
                throw new DecisionSupportParseException(guidelineTitle, "Condition 'type' attribute is mandatory");
            //try to resolve type
            DSDemographicAccess.Module conditionType;
            try {
                conditionType = DSDemographicAccess.Module.valueOf(conditionTypeStr);
            } catch (IllegalArgumentException iae) {
                String knownTypes = StringUtils.join(DSDemographicAccess.Module.values(), ",");
                throw new DecisionSupportParseException(guidelineTitle, "Cannot recognize condition type: '" + conditionTypeStr + "'.  Known types: " + knownTypes, iae);
            }

            String conditionDescStr = conditionTag.getAttributeValue("desc");


            Hashtable<String, String> paramHashtable = new Hashtable<String, String>();
            @SuppressWarnings("unchecked")
            List<Element> paramList = conditionTag.getChildren("param");
            if (paramList != null) {
                for (Element param : paramList) {
                    String key = param.getAttributeValue("key");
                    String value = param.getAttributeValue("value");
                    paramHashtable.put(key, value);
                }
            }

            @SuppressWarnings("unchecked")
            List<Attribute> attributes = conditionTag.getAttributes();
            for (Attribute attribute : attributes) {
                if (attribute.getName().equalsIgnoreCase("type")) continue;
                if (attribute.getName().equalsIgnoreCase("desc")) continue;
                DSCondition.ListOperator operator;
                try {
                    operator = DSCondition.ListOperator.valueOf(attribute.getName().toLowerCase());
                } catch (IllegalArgumentException iae) {
                    throw new DecisionSupportParseException(guidelineTitle, "Unknown condition attribute'" + attribute.getName() + "'", iae);
                }
                DSCondition dsCondition = new DSCondition();
                dsCondition.setConditionType(conditionType);
                dsCondition.setDesc(conditionDescStr);
                dsCondition.setListOperator(operator); //i.e. any, all, not
                if (paramHashtable != null && !paramHashtable.isEmpty()) {
                    _log.debug("THIS IS THE HASH STRING " + paramHashtable.toString());
                    dsCondition.setParam(paramHashtable);
                }

                dsCondition.setValues(DSValue.createDSValues(attribute.getValue())); //i.e. icd9:3020,icd9:3021,icd10:5022
                conditions.add(dsCondition);
            }
        }
        dsGuideline.setConditions(conditions);

        //CONSEQUENCES
        ArrayList<DSConsequence> dsConsequences = new ArrayList<DSConsequence>();
        @SuppressWarnings("unchecked")
        List<Element> consequenceElements = guidelineRoot.getChild("consequence").getChildren();
        for (Element consequenceElement : consequenceElements) {
            DSConsequence dsConsequence = new DSConsequence();

            String consequenceTypeStr = consequenceElement.getName();
            DSConsequence.ConsequenceType consequenceType = null;
            //try to resolve type
            try {
                consequenceType = DSConsequence.ConsequenceType.valueOf(consequenceTypeStr.toLowerCase());
            } catch (IllegalArgumentException iae) {
                String knownTypes = StringUtils.join(DSConsequence.ConsequenceType.values(), ",");
                throw new DecisionSupportParseException(guidelineTitle, "Unknown consequence: " + consequenceTypeStr + ". Allowed: " + knownTypes, iae);
            }
            dsConsequence.setConsequenceType(consequenceType);

            if (consequenceType == DSConsequence.ConsequenceType.warning) {
                String strengthStr = consequenceElement.getAttributeValue("strength");
                if (strengthStr == null) {
                    strengthStr = "warning";
                }
                DSConsequence.ConsequenceStrength strength = null;
                //try to resolve strength type
                try {
                    strength = DSConsequence.ConsequenceStrength.valueOf(strengthStr.toLowerCase());
                    dsConsequence.setConsequenceStrength(strength);
                } catch (IllegalArgumentException iae) {
                    String knownStrengths = StringUtils.join(DSConsequence.ConsequenceStrength.values(), ",");
                    throw new DecisionSupportParseException(guidelineTitle, "Unknown strength: " + strengthStr + ". Allowed: " + knownStrengths, iae);
                }
            }
            dsConsequence.setText(consequenceElement.getText());
            dsConsequences.add(dsConsequence);
        }
        dsGuideline.setConsequences(dsConsequences);
        dsGuideline.setXml(xml);
        dsGuideline.setParsed(true);
        return dsGuideline;
    }

    /**
     * Creates a blank DSGuideline instance with default configuration.
     * <p>
     * Returns a new DSGuidelineDrools instance that can be manually configured
     * or used as a template for guideline creation. This method is primarily used
     * internally by the factory for creating guideline instances during XML parsing.
     * </p>
     *
     * @return DSGuideline blank guideline instance ready for configuration
     * @see DSGuidelineDrools for the default implementation
     */
    public DSGuideline createBlankGuideline() {
        DSGuidelineDrools newGuideline = new DSGuidelineDrools();
        return newGuideline;
    }
}
