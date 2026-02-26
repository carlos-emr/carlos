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
package io.github.carlos_emr.carlos.decisionSupport.prevention;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import org.apache.logging.log4j.Logger;
import org.kie.api.KieBase;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.DSCondition;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.RuleBaseCreator;

/**
 * Parses XML-based prevention guideline definitions and generates a Drools {@link KieBase}
 * containing programmatic DRL rules for clinical decision support.
 *
 * <h2>XML-to-DRL Conversion Pipeline</h2>
 * <p>This class implements the following transformation pipeline:</p>
 * <ol>
 *   <li><strong>XML Parsing</strong> - Reads prevention guideline XML (stored in the
 *       {@code resource_storage} database table) using JDOM2 SAX parsing. The XML contains
 *       {@code <recommendations>} elements, each with a {@code for} attribute naming the
 *       prevention type (e.g., "DTaP", "Flu", "Td").</li>
 *   <li><strong>Condition Extraction</strong> - Each {@code <recommendation>} contains
 *       {@code <condition>} elements with a {@code type} attribute that routes to a specific
 *       processor method. Processors translate the XML into {@link DSCondition} objects
 *       representing method calls, comparison operators, and threshold values.</li>
 *   <li><strong>DRL Generation</strong> - The {@link DSCondition} list is passed to
 *       {@link RuleBaseCreator#getRule(String, String, List, String)}, which generates DRL
 *       rule text with Drools eval() expressions. Each eval invokes a method on the
 *       {@link io.github.carlos_emr.carlos.prevention.Prevention Prevention} fact object
 *       (bound as {@code m}) and compares the result against the threshold.</li>
 *   <li><strong>KieBase Compilation</strong> - The generated DRL strings are compiled into
 *       a Drools {@link KieBase} via {@link RuleBaseCreator#getRuleBase(String, List)}.</li>
 * </ol>
 *
 * <h2>XML Structure</h2>
 * <p>The expected XML format is:</p>
 * <pre>{@code
 * <preventionSet>
 *   <recommendations for="DTaP">
 *     <recommendation message="oscarResources.key" reminder="oscarResources.key">
 *       <condition type="age" value="2m-72m" />
 *       <condition type="numberOfPreventions" param="DTaP-IPV" value="0" />
 *     </recommendation>
 *   </recommendations>
 * </preventionSet>
 * }</pre>
 *
 * <h2>Supported Condition Types</h2>
 * <p>The {@link #getConditions(Element, String)} method routes the following XML condition
 * types to their respective processors:</p>
 * <ul>
 *   <li>{@code age} - Patient age range in months ({@code m} suffix) or years ({@code y}
 *       suffix or no suffix)</li>
 *   <li>{@code numberOfPreventions} - Count of a specific prevention type administered</li>
 *   <li>{@code numberOfMonthsSinceLast} - Months elapsed since last prevention</li>
 *   <li>{@code numberOfDaysSinceLast} - Days elapsed since last prevention</li>
 *   <li>{@code numberOfAgeInMonthsSinceLastPreventionTypeGiven} - Patient age in months
 *       at the time the last prevention of a given type was administered</li>
 *   <li>{@code isNextDateSet} / {@code !isNextDateSet} - Whether a next date is scheduled</li>
 *   <li>{@code isPassedNextDate} / {@code !isPassedNextDate} - Whether the next date has
 *       passed</li>
 *   <li>{@code isPreventionNever} / {@code !isPreventionNever} - Whether prevention is
 *       marked "never"</li>
 *   <li>{@code isInelligible} / {@code !isInelligible} - Whether patient is ineligible
 *       (note: misspelling of "ineligible" preserved from original codebase for backward
 *       compatibility with existing XML guideline files)</li>
 *   <li>{@code isMale} / {@code isFemale} - Patient sex check (no param needed)</li>
 *   <li>{@code todayIsInDateRange} / {@code !todayIsInDateRange} - Calendar date range
 *       check</li>
 *   <li>{@code lastPreventionIsWithinRange} / {@code !lastPreventionIsWithinRange} - Whether
 *       last prevention date falls within a specified date range</li>
 * </ul>
 *
 * <h2>Consequence Messages</h2>
 * <p>Rule consequences use the {@code oscarResources} {@link ResourceBundle} to look up
 * localized message strings. The {@link #replaceKeys(String, String)} method substitutes
 * two placeholders in the resolved message text:</p>
 * <ul>
 *   <li>{@code $NUMMONTHS} - Replaced with a DRL expression that calls
 *       {@code m.getHowManyMonthsSinceLast("preventionType")} at rule firing time, allowing
 *       the message to display the dynamic month count</li>
 *   <li>{@code $PREVENTION_TYPE} - Replaced with the literal prevention type name
 *       (e.g., "DTaP", "Flu")</li>
 * </ul>
 *
 * <p>This class is invoked by
 * {@link io.github.carlos_emr.carlos.prevention.PreventionDSImpl PreventionDSImpl},
 * which loads the XML rule set from the database {@code resource_storage} table (or falls
 * back to a classpath resource) and passes the raw bytes to {@link #createRuleBase(byte[])}.</p>
 *
 * <p>Part of the Drools 2.0 to 7.74.1 migration. The legacy Drools 2.0 XML rule format
 * was replaced with programmatically generated DRL text while preserving the existing
 * XML guideline definition format consumed by this parser.</p>
 *
 * @since 2015-12-01
 */
public class DSPreventionDrools {
    private static final Logger logger = MiscUtils.getLogger();

    /**
     * Fully qualified class path for the
     * {@link io.github.carlos_emr.carlos.prevention.Prevention Prevention} fact object.
     *
     * <p>Used by {@link RuleBaseCreator} to generate the DRL {@code import} statement
     * and bind the fact object as variable {@code m} in rule conditions and consequences.</p>
     */
    public static final String preventionObjectClassPath = "io.github.carlos_emr.carlos.prevention.Prevention";

    /**
     * Parses an XML prevention guideline rule set and compiles it into a Drools {@link KieBase}.
     *
     * <p>This is the main entry point for the XML-to-DRL conversion pipeline. It performs
     * the following steps:</p>
     * <ol>
     *   <li>Parses the XML byte array into a JDOM2 document</li>
     *   <li>Iterates over each {@code <recommendations for="...">} element</li>
     *   <li>For each nested {@code <recommendation>}, extracts conditions via
     *       {@link #getConditions(Element, String)} and builds consequence strings from
     *       the {@code message} and {@code reminder} attributes</li>
     *   <li>Message and reminder attribute values are keys into the {@code oscarResources}
     *       ResourceBundle; the resolved text is embedded into the DRL consequence</li>
     *   <li>When {@code DEBUG.PREVENTION} is enabled, a {@code m.log()} call is prepended
     *       to each rule's consequence for diagnostic tracing</li>
     *   <li>Each rule is assigned a unique name in the format
     *       {@code "preventionType-sequenceNumber"} (e.g., "DTaP-0", "DTaP-1", "Flu-2")</li>
     *   <li>All generated DRL rule strings are compiled into a single KieBase named
     *       "preventions"</li>
     * </ol>
     *
     * @param ruleSet byte[] the raw XML bytes of the prevention guideline definitions,
     *                typically loaded from the {@code resource_storage} database table
     *                or a classpath resource file
     * @return KieBase the compiled Drools knowledge base containing all prevention rules,
     *         ready for stateless or stateful session creation
     * @throws Exception if XML parsing fails, if a ResourceBundle key is missing, or if
     *                   DRL compilation encounters syntax errors in the generated rules
     */
    public static KieBase createRuleBase(byte[] ruleSet) throws Exception {
        logger.debug(preventionObjectClassPath);
        RuleBaseCreator rbc = new RuleBaseCreator();
        ResourceBundle oscarResource = ResourceBundle.getBundle("oscarResources");

        SAXBuilder parser = new SAXBuilder();
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc = parser.build(new ByteArrayInputStream(ruleSet));
        Element root = doc.getRootElement();
        // Global counter across all recommendations to ensure unique rule names
        int count = 0;
        List<String> elementList = new ArrayList<String>();

        // Each <recommendations for="preventionType"> groups rules for one prevention type
        List<Element> recommendations = root.getChildren("recommendations");
        logger.debug("recommendations size :" + recommendations.size());

        for (Element recommendationsElements : recommendations) {
            // The "for" attribute identifies the prevention type (e.g., "DTaP", "Flu", "Td")
            String preventionType = recommendationsElements.getAttributeValue("for");
            // Escape for DRL string literal embedding (defense-in-depth; values come from trusted XML)
            String safePreventionType = preventionType.replace("\\", "\\\\").replace("\"", "\\\"");
            List<Element> recommendation = recommendationsElements.getChildren("recommendation");
            for (Element recommendationElement : recommendation) {
                // Rule name format: "preventionType-N" ensures uniqueness across all rules
                String ruleNumber = safePreventionType + "-" + count++;
                // "message" and "reminder" attributes are keys into oscarResources bundle
                String message = recommendationElement.getAttributeValue("message");
                String reminder = recommendationElement.getAttributeValue("reminder");
                // Parse all <condition> child elements into DSCondition objects
                List<DSCondition> dsConditions = getConditions(recommendationElement, safePreventionType);
                // Build the DRL "then" block: log + warning + reminder method calls on Prevention object
                StringBuilder consequence = new StringBuilder();
                if (OscarProperties.getInstance().getBooleanProperty("DEBUG.PREVENTION", "yes")) {
                    consequence.append("m.log(\"" + ruleNumber + "\"); ");
                }
                if (message != null) {
                    // Resolve the resource bundle key to localized text, escape before replaceKeys
                    // so $NUMMONTHS DRL expression fragments are not double-escaped
                    message = oscarResource.getString(message).replace("\\", "\\\\").replace("\"", "\\\"");
                    // Replace $NUMMONTHS and $PREVENTION_TYPE placeholders with DRL expressions
                    message = replaceKeys(message, safePreventionType);
                    // addWarning(preventionType, message) stores both the type and the message
                    consequence.append("m.addWarning(\"" + safePreventionType + "\",\"" + message + "\"); ");
                }
                if (reminder != null) {
                    reminder = oscarResource.getString(reminder).replace("\\", "\\\\").replace("\"", "\\\"");
                    reminder = replaceKeys(reminder, safePreventionType);
                    consequence.append("m.addReminder(\"" + reminder + "\"); ");
                }
                // Generate DRL rule text and add to the list for compilation
                elementList.add(rbc.getRule(ruleNumber, DSPreventionDrools.preventionObjectClassPath, dsConditions, consequence.toString()));
            }
        }
        // Compile all DRL rule strings into a single KieBase named "preventions"
        return rbc.getRuleBase("preventions", elementList);

    }

    /**
     * Substitutes placeholder tokens in a message string with DRL expressions or literal values.
     *
     * <p>Two placeholders are supported:</p>
     * <ul>
     *   <li>{@code $NUMMONTHS} - Replaced with a DRL string concatenation expression that calls
     *       {@code m.getHowManyMonthsSinceLast("preventionType")} at rule firing time. The
     *       replacement inserts string-closing and string-opening quotes around the method call
     *       so it becomes part of a DRL string concatenation: {@code "..."+m.getHowManyMonthsSinceLast("DTaP")+"..."}</li>
     *   <li>{@code $PREVENTION_TYPE} - Replaced with the literal prevention type name string</li>
     * </ul>
     *
     * @param txt String the message text (already resolved from the oscarResources bundle)
     *            that may contain {@code $NUMMONTHS} and/or {@code $PREVENTION_TYPE} placeholders
     * @param preventionType String the prevention type name (e.g., "DTaP", "Flu") used both
     *                       as the argument to {@code getHowManyMonthsSinceLast()} and as the
     *                       literal replacement for {@code $PREVENTION_TYPE}
     * @return String the message text with all placeholders replaced
     */
    private static String replaceKeys(String txt, String preventionType) {
        // Build the DRL expression fragment: closes the surrounding string literal, calls the
        // method, then re-opens the string literal. When embedded in the consequence string
        // "m.addWarning("DTaP","..."+m.getHowManyMonthsSinceLast("DTaP")+"...")", the result
        // is a valid DRL string concatenation that resolves the month count at rule firing time.
        String NUMMONTHS = "\"+m.getHowManyMonthsSinceLast(\"" + preventionType + "\")+\"";
        txt = txt.replaceAll("\\$NUMMONTHS", NUMMONTHS);
        txt = txt.replaceAll("\\$PREVENTION_TYPE", preventionType);
        return txt;
    }


    /**
     * Routes XML {@code <condition>} elements to the appropriate processor method based on
     * the {@code type} attribute value.
     *
     * <p>Each condition type maps to a specific method on the
     * {@link io.github.carlos_emr.carlos.prevention.Prevention Prevention} fact object.
     * The routing handles both positive and negated forms (prefixed with {@code !}).</p>
     *
     * <p>Condition types map to Prevention methods as follows:</p>
     * <ul>
     *   <li>{@code age} &rarr; {@code getAgeInMonths()} or {@code getAgeInYears()}</li>
     *   <li>{@code numberOfPreventions} &rarr; {@code getNumberOfPreventionType(param)}</li>
     *   <li>{@code numberOfMonthsSinceLast} &rarr; {@code getHowManyMonthsSinceLast(param)}</li>
     *   <li>{@code numberOfDaysSinceLast} &rarr; {@code getHowManyDaysSinceLast(param)}</li>
     *   <li>{@code numberOfAgeInMonthsSinceLastPreventionTypeGiven} &rarr;
     *       {@code getAgeInMonthsLastPreventionTypeGiven(param)}</li>
     *   <li>{@code isNextDateSet} &rarr; {@code isNextDateSet(param)}</li>
     *   <li>{@code isPassedNextDate} &rarr; {@code isPassedNextDate(param)}</li>
     *   <li>{@code isPreventionNever} &rarr; {@code isPreventionNever(param)}</li>
     *   <li>{@code isInelligible} &rarr; {@code isInelligible(param)}</li>
     *   <li>{@code isMale} / {@code isFemale} &rarr; {@code isMale()} / {@code isFemale()}</li>
     *   <li>{@code todayIsInDateRange} &rarr; {@code isTodayinDateRange(startDate, endDate)}</li>
     *   <li>{@code lastPreventionIsWithinRange} &rarr;
     *       {@code isLastPreventionWithinRange(param, startDate, endDate)}</li>
     * </ul>
     *
     * <p>For negated types (prefixed with {@code !}), the same method is called but the
     * condition adds {@code == false} to negate the boolean result.</p>
     *
     * <p>Unrecognized condition types are logged as errors but do not throw exceptions,
     * allowing partial rule processing to continue.</p>
     *
     * @param recommendationElement Element the {@code <recommendation>} XML element containing
     *                              zero or more {@code <condition>} child elements
     * @param preventionType String the prevention type name (e.g., "DTaP") used as the
     *                       default parameter when no {@code param} attribute is specified
     *                       on the condition element
     * @return List of {@link DSCondition} objects representing all parsed conditions for
     *         this recommendation, to be converted into DRL eval() expressions
     */
    private static List<DSCondition> getConditions(Element recommendationElement, String preventionType) {
        List<DSCondition> dsConditions = new ArrayList<DSCondition>();

        List<Element> conditions = recommendationElement.getChildren("condition");

        for (Element condition : conditions) {
            String type = condition.getAttributeValue("type");
            if ("age".equals(type)) { //<condition type="age" value="2m-72m" />  //int getAgeInMonths() //int getAgeInYears()
                processAgeElement(dsConditions, condition);
            } else if ("numberOfPreventions".equals(type)) { //<condition type="numberOfPreventions" param="DTaP-IPV" value="0" />
                processGenericNumberValues("getNumberOfPreventionType", dsConditions, condition, preventionType);
            } else if ("numberOfMonthsSinceLast".equals(type)) {
                processGenericNumberValues("getHowManyMonthsSinceLast", dsConditions, condition, preventionType);
            } else if ("numberOfDaysSinceLast".equals(type)) {
                processGenericNumberValues("getHowManyDaysSinceLast", dsConditions, condition, preventionType);
            } else if ("numberOfAgeInMonthsSinceLastPreventionTypeGiven".equals(type)) {
                processGenericNumberValues("getAgeInMonthsLastPreventionTypeGiven", dsConditions, condition, preventionType);
            } else if ("isNextDateSet".equals(type)) {
                processSimpleBooleanValue("isNextDateSet", dsConditions, condition, preventionType);
            } else if ("!isNextDateSet".equals(type)) {
                processFalseBooleanValue("isNextDateSet", dsConditions, condition, preventionType);
            } else if ("isPassedNextDate".equals(type)) {
                processSimpleBooleanValue("isPassedNextDate", dsConditions, condition, preventionType);
            } else if ("!isPassedNextDate".equals(type)) {
                processFalseBooleanValue("isPassedNextDate", dsConditions, condition, preventionType);
            } else if ("isPreventionNever".equals(type)) {
                processSimpleBooleanValue("isPreventionNever", dsConditions, condition, preventionType);
            } else if ("!isPreventionNever".equals(type)) {
                processFalseBooleanValue("isPreventionNever", dsConditions, condition, preventionType);
            } else if ("isInelligible".equals(type)) {
                processSimpleBooleanValue("isInelligible", dsConditions, condition, preventionType);
            } else if ("!isInelligible".equals(type)) {
                processFalseBooleanValue("isInelligible", dsConditions, condition, preventionType);
            } else if ("isMale".equals(type)) {
                // Sex checks pass null for paramDefaultIfNull because isMale()/isFemale()
                // are parameterless methods on Prevention
                processSimpleBooleanValue("isMale", dsConditions, condition, null);
            } else if ("isFemale".equals(type)) {
                processSimpleBooleanValue("isFemale", dsConditions, condition, null);
            } else if ("todayIsInDateRange".equals(type)) {  //boolean isTodayinDateRange(String startDate,String endDate)
                processTodayIsInDateRange(dsConditions, condition);
            } else if ("!todayIsInDateRange".equals(type)) {
                processTodayIsNotInDateRange(dsConditions, condition);
            } else if ("lastPreventionIsWithinRange".equals(type)) { //boolean isLastPreventionWithinRange(String preventionType, String startDate, String endDate)
                processLastPreventionIsWithinRange(dsConditions, condition);
            } else if ("!lastPreventionIsWithinRange".equals(type)) {
                processLastPreventionIsNotWithinRange(dsConditions, condition);
            } else {
                logger.error("Unrecognized condition type '{}' in prevention '{}'; condition will be SKIPPED, making the rule less restrictive", type, preventionType);
            }
        }
        return dsConditions;
    }

    /**
     * Processes a {@code lastPreventionIsWithinRange} condition, checking whether the most
     * recent prevention of a given type falls within a specified date range.
     *
     * <p>Expected XML format:
     * {@code <condition type="lastPreventionIsWithinRange" param="Flu" value="2024-09-01,2025-04-30" />}</p>
     *
     * <p>The {@code param} attribute specifies the prevention type to check, and the
     * {@code value} attribute contains two comma-separated dates (start and end). These are
     * assembled into a multi-argument parameter string for
     * {@link io.github.carlos_emr.carlos.prevention.Prevention#isLastPreventionWithinRange(String, String, String)}
     * by inserting DRL string-literal delimiters between them:
     * {@code param + '","' + startDate + '","' + endDate}.</p>
     *
     * <p>The resulting DSCondition uses empty comparison and value strings, which causes
     * RuleBaseCreator to generate a truthy eval (the boolean return value itself is the
     * condition).</p>
     *
     * @param dsConditions List of {@link DSCondition} objects to append the new condition to
     * @param condition Element the XML {@code <condition>} element with {@code param} and
     *                  {@code value} attributes
     */
    private static void processLastPreventionIsWithinRange(List<DSCondition> dsConditions, Element condition) {
        String param = condition.getAttributeValue("param");
        String value = condition.getAttributeValue("value");

        // Split "2024-09-01,2025-04-30" into [startDate, endDate]
        String[] values = value.split(",");
        // Build multi-argument param: preventionType","startDate","endDate
        param = param + "\",\"" + values[0] + "\",\"" + values[1];
        param = param.replaceAll(";", "");
        dsConditions.add(new DSCondition("isLastPreventionWithinRange", param, "", ""));

    }

    /**
     * Processes a negated {@code !lastPreventionIsWithinRange} condition, checking that the
     * most recent prevention does NOT fall within the specified date range.
     *
     * <p>Identical to {@link #processLastPreventionIsWithinRange(List, Element)} except the
     * generated DSCondition includes {@code == false} to negate the boolean result.</p>
     *
     * @param dsConditions List of {@link DSCondition} objects to append the new condition to
     * @param condition Element the XML {@code <condition>} element with {@code param} and
     *                  {@code value} attributes
     */
    private static void processLastPreventionIsNotWithinRange(List<DSCondition> dsConditions, Element condition) {
        String param = condition.getAttributeValue("param");
        String value = condition.getAttributeValue("value");

        String[] values = value.split(",");
        param = param + "\",\"" + values[0] + "\",\"" + values[1];
        param = param.replaceAll(";", "");
        dsConditions.add(new DSCondition("isLastPreventionWithinRange", param, "==", "false"));
    }

    /**
     * Processes a {@code todayIsInDateRange} condition, checking whether today's date falls
     * within a specified date range.
     *
     * <p>Expected XML format:
     * {@code <condition type="todayIsInDateRange" value="2024-09-01,2025-04-30" />}</p>
     *
     * <p>The {@code value} attribute contains two comma-separated dates. These are split and
     * assembled into a two-argument parameter string for
     * {@link io.github.carlos_emr.carlos.prevention.Prevention#isTodayinDateRange(String, String)}.
     * The generated DRL expression evaluates as: {@code m.isTodayinDateRange("startDate","endDate")}.</p>
     *
     * @param dsConditions List of {@link DSCondition} objects to append the new condition to
     * @param condition Element the XML {@code <condition>} element with a {@code value}
     *                  attribute containing comma-separated start and end dates
     */
    private static void processTodayIsInDateRange(List<DSCondition> dsConditions, Element condition) {
        String param = condition.getAttributeValue("value");
        // Split "2024-09-01,2025-04-30" on comma into [startDate, endDate]
        String[] params = param.split(",");
        // Build two-argument param: startDate","endDate
        param = params[0] + "\",\"" + params[1];
        param = param.replaceAll(";", "");
        dsConditions.add(new DSCondition("isTodayinDateRange", param, "", ""));
    }

    /**
     * Processes a negated {@code !todayIsInDateRange} condition, checking that today's date
     * does NOT fall within the specified date range.
     *
     * <p>Expected XML format:
     * {@code <condition type="!todayIsInDateRange" value="2024-09-01,2025-04-30" />}</p>
     *
     * <p>Identical to {@link #processTodayIsInDateRange(List, Element)} except the
     * generated DSCondition includes {@code == false} to negate the boolean result.</p>
     *
     * @param dsConditions List of {@link DSCondition} objects to append the new condition to
     * @param condition Element the XML {@code <condition>} element with a {@code value}
     *                  attribute containing comma-separated start and end dates
     */
    private static void processTodayIsNotInDateRange(List<DSCondition> dsConditions, Element condition) {
        String param = condition.getAttributeValue("value");
        // Split "2024-09-01,2025-04-30" on comma into [startDate, endDate]
        String[] params = param.split(",");
        // Build two-argument param: startDate","endDate
        param = params[0] + "\",\"" + params[1];
        param = param.replaceAll(";", "");
        dsConditions.add(new DSCondition("isTodayinDateRange", param, "==", "false"));
    }


    /**
     * Processes a boolean condition that expects a {@code true} result from the specified
     * Prevention method.
     *
     * <p>Used for condition types like {@code isNextDateSet}, {@code isPassedNextDate},
     * {@code isPreventionNever}, {@code isInelligible}, {@code isMale}, and {@code isFemale}.</p>
     *
     * <p>The generated DSCondition uses empty comparison and value strings. When
     * {@link RuleBaseCreator} renders this, the Drools eval expression simply checks the
     * boolean return value: {@code eval( m.isNextDateSet("DTaP") )}. Since no comparison
     * operator is specified, the truthy boolean result itself satisfies the condition.</p>
     *
     * <p>If the XML element has no {@code param} attribute and {@code paramDefaultIfNull}
     * is non-null, the default value is used. For sex checks ({@code isMale}/{@code isFemale}),
     * {@code paramDefaultIfNull} is null because those methods take no arguments.</p>
     *
     * @param method String the Prevention method name to call (e.g., "isNextDateSet",
     *               "isMale", "isInelligible")
     * @param dsConditions List of {@link DSCondition} objects to append the new condition to
     * @param condition Element the XML {@code <condition>} element, optionally containing
     *                  a {@code param} attribute
     * @param paramDefaultIfNull String the fallback parameter value (typically the prevention
     *                           type name) to use when the XML element has no {@code param}
     *                           attribute, or null if the method takes no arguments
     */
    private static void processSimpleBooleanValue(String method, List<DSCondition> dsConditions, Element condition, String paramDefaultIfNull) {
        String param = condition.getAttributeValue("param");
        if (param == null && paramDefaultIfNull != null) {
            param = paramDefaultIfNull;
        }
        // Empty comparison and value: the boolean return is evaluated directly as truthy
        dsConditions.add(new DSCondition(method, param, "", ""));
    }

    /**
     * Processes a negated boolean condition that expects a {@code false} result from the
     * specified Prevention method.
     *
     * <p>Used for negated condition types prefixed with {@code !} (e.g., {@code !isNextDateSet},
     * {@code !isPassedNextDate}, {@code !isPreventionNever}, {@code !isInelligible}).</p>
     *
     * <p>The generated DSCondition includes {@code == false} as the comparison, producing
     * a DRL eval expression like: {@code eval( m.isNextDateSet("DTaP") == false )}.</p>
     *
     * @param method String the Prevention method name to call (without the {@code !} prefix;
     *               e.g., "isNextDateSet" for condition type "!isNextDateSet")
     * @param dsConditions List of {@link DSCondition} objects to append the new condition to
     * @param condition Element the XML {@code <condition>} element, optionally containing
     *                  a {@code param} attribute
     * @param paramDefaultIfNull String the fallback parameter value (typically the prevention
     *                           type name) to use when the XML element has no {@code param}
     *                           attribute, or null if the method takes no arguments
     */
    private static void processFalseBooleanValue(String method, List<DSCondition> dsConditions, Element condition, String paramDefaultIfNull) {
        String param = condition.getAttributeValue("param");
        if (param == null && paramDefaultIfNull != null) {
            param = paramDefaultIfNull;
        }
        dsConditions.add(new DSCondition(method, param, "==", "false"));
    }

    /**
     * Parses a numeric value expression from the {@code value} attribute and creates one or
     * two {@link DSCondition} objects with the appropriate comparison operator(s).
     *
     * <p>Used for condition types: {@code numberOfPreventions}, {@code numberOfMonthsSinceLast},
     * {@code numberOfDaysSinceLast}, and {@code numberOfAgeInMonthsSinceLastPreventionTypeGiven}.
     * Each maps to a corresponding method on the Prevention fact object.</p>
     *
     * <h3>Supported value formats:</h3>
     * <ul>
     *   <li><strong>Between (range)</strong>: {@code "2-10"} &rarr; generates two conditions:
     *       {@code method(param) >= 2} AND {@code method(param) <= 10}. The hyphen is detected
     *       only when it is not at position 0 (to avoid confusion with negative numbers).</li>
     *   <li><strong>Greater than</strong>: {@code ">4"} or {@code "&gt;4"} (XML-escaped) &rarr;
     *       generates {@code method(param) >= 4}. Note: uses {@code >=} (greater-or-equal),
     *       not strict greater-than.</li>
     *   <li><strong>Less than</strong>: {@code "<4"} or {@code "&lt;4"} (XML-escaped) &rarr;
     *       generates {@code method(param) <= 4}. Note: uses {@code <=} (less-or-equal),
     *       not strict less-than.</li>
     *   <li><strong>Not equal</strong>: {@code "!=4"} &rarr; generates
     *       {@code method(param) != 4}</li>
     *   <li><strong>Exact match</strong>: {@code "4"} (plain integer) &rarr; generates
     *       {@code method(param) == 4}</li>
     * </ul>
     *
     * <p>The {@code param} attribute from the XML element specifies the prevention type
     * argument passed to the method. If absent, {@code paramDefaultIfNull} (typically the
     * enclosing {@code <recommendations for="...">} prevention type) is used as the default.</p>
     *
     * @param method String the Prevention method name to invoke (e.g.,
     *               "getNumberOfPreventionType", "getHowManyMonthsSinceLast")
     * @param dsConditions List of {@link DSCondition} objects to append the new condition(s) to
     * @param condition Element the XML {@code <condition>} element with a {@code value}
     *                  attribute containing the numeric expression to parse
     * @param paramDefaultIfNull String the fallback parameter value used when the XML element
     *                           has no {@code param} attribute
     */
    private static void processGenericNumberValues(String method, List<DSCondition> dsConditions, Element condition, String paramDefaultIfNull) {
        String param = condition.getAttributeValue("param");
        if (param == null && paramDefaultIfNull != null) {
            param = paramDefaultIfNull;
        }
        String toParse = condition.getAttributeValue("value");
        // Check for range/between format: hyphen present but not at position 0 (not a negative number)
        if (toParse.indexOf("-") != -1 && toParse.indexOf("-") != 0) { //between style
            String[] betweenVals = toParse.split("-");
            if (betweenVals.length == 2) {
                // Range creates two conditions: lower bound >= and upper bound <=
                dsConditions.add(new DSCondition(method, param, ">=", betweenVals[0]));
                dsConditions.add(new DSCondition(method, param, "<=", betweenVals[1]));
            }

        } else if (toParse.indexOf("&gt;") != -1 || toParse.indexOf(">") != -1) { // greater than style
            // Strip both XML-escaped and literal greater-than signs
            toParse = toParse.replaceFirst("&gt;", "");
            toParse = toParse.replaceFirst(">", "");
            int gt = Integer.parseInt(toParse);
            // Note: uses >= (greater-or-equal) rather than strict >
            dsConditions.add(new DSCondition(method, param, ">=", "" + gt));

        } else if (toParse.indexOf("&lt;") != -1 || toParse.indexOf("<") != -1) { // less than style
            // Strip both XML-escaped and literal less-than signs
            toParse = toParse.replaceFirst("&lt;", "");
            toParse = toParse.replaceFirst("<", "");

            int lt = Integer.parseInt(toParse);
            // Note: uses <= (less-or-equal) rather than strict <
            dsConditions.add(new DSCondition(method, param, "<=", "" + lt));
        } else if (toParse.indexOf("!=") != -1) { // not-equal style
            toParse = toParse.replaceFirst("!=", "");
            int eq = Integer.parseInt(toParse);
            dsConditions.add(new DSCondition(method, param, "!=", "" + eq));
        } else if (!toParse.isEmpty()) { // exact match style (plain integer value)
            int eq = Integer.parseInt(toParse);
            dsConditions.add(new DSCondition(method, param, "==", "" + eq));
        }

    }

    /**
     * Parses an age condition from the {@code value} attribute and creates one or two
     * {@link DSCondition} objects that compare against
     * {@link io.github.carlos_emr.carlos.prevention.Prevention#getAgeInMonths()} or
     * {@link io.github.carlos_emr.carlos.prevention.Prevention#getAgeInYears()}.
     *
     * <p>The unit suffix determines which age method is used:</p>
     * <ul>
     *   <li>{@code m} suffix &rarr; {@code getAgeInMonths()} (e.g., "2m", "72m")</li>
     *   <li>{@code y} suffix or no suffix &rarr; {@code getAgeInYears()} (e.g., "4y", "65")</li>
     * </ul>
     *
     * <h3>Supported value formats:</h3>
     * <ul>
     *   <li><strong>Between (range)</strong>: {@code "2m-72m"} &rarr; generates
     *       {@code getAgeInMonths() >= 2} AND {@code getAgeInMonths() <= 72}. Each half
     *       of the range is independently checked for the unit suffix, so mixed-unit ranges
     *       like {@code "2m-6"} (months-to-years) are supported.</li>
     *   <li><strong>Greater than</strong>: {@code ">4m"} or {@code "&gt;4m"} &rarr;
     *       {@code getAgeInMonths() >= 4}. Note: uses {@code >=} (greater-or-equal).</li>
     *   <li><strong>Less than</strong>: {@code "<65"} or {@code "&lt;65"} &rarr;
     *       {@code getAgeInYears() <= 65}. Note: uses {@code <=} (less-or-equal).</li>
     *   <li><strong>Not equal</strong>: {@code "!=12m"} &rarr;
     *       {@code getAgeInMonths() != 12}</li>
     *   <li><strong>Exact match</strong>: {@code "4m"} &rarr;
     *       {@code getAgeInMonths() == 4}. Checks for "m" suffix (months) or
     *       assumes years, stripping the suffix via {@code replaceAll}.</li>
     * </ul>
     *
     * <p>Age conditions use no {@code param} argument (empty string) because
     * {@code getAgeInMonths()} and {@code getAgeInYears()} are parameterless methods
     * on the Prevention object.</p>
     *
     * @param dsConditions List of {@link DSCondition} objects to append the new condition(s) to
     * @param condition Element the XML {@code <condition>} element with a {@code value}
     *                  attribute containing the age expression to parse.
     *                  Example formats: {@code "2m-72m"}, {@code ">4m"}, {@code "&lt;65"},
     *                  {@code "!=12m"}, {@code "4m"}
     */
    private static void processAgeElement(List<DSCondition> dsConditions, Element condition) {
        String valueToParse = condition.getAttributeValue("value");
        logger.debug("process Age Element :" + valueToParse);
        // Check for range/between format: hyphen present but not at position 0
        if (valueToParse.indexOf("-") != -1 && valueToParse.indexOf("-") != 0) { //between style
            String[] betweenVals = valueToParse.split("-");
            if (betweenVals.length == 2) {
                // Lower bound: check unit suffix independently from upper bound
                if (betweenVals[0].indexOf("m") != -1) {
                    // "m" suffix detected: use getAgeInMonths, strip the "m" for the numeric value
                    dsConditions.add(new DSCondition("getAgeInMonths", "", ">=", betweenVals[0].replaceAll("m", "")));
                } else { // assume years if no "m" suffix; strip optional "y" suffix
                    dsConditions.add(new DSCondition("getAgeInYears", "", ">=", betweenVals[0].replaceAll("y", "")));
                }
                // Upper bound: unit suffix checked independently
                if (betweenVals[1].indexOf("m") != -1) {
                    dsConditions.add(new DSCondition("getAgeInMonths", "", "<=", betweenVals[1].replaceAll("m", "")));
                } else { // assume years
                    dsConditions.add(new DSCondition("getAgeInYears", "", "<=", betweenVals[1].replaceAll("y", "")));
                }
            }
        } else if (valueToParse.indexOf("&gt;") != -1 || valueToParse.indexOf(">") != -1) { // greater than style
            // Strip both XML-escaped and literal greater-than signs
            valueToParse = valueToParse.replaceFirst("&gt;", "");
            valueToParse = valueToParse.replaceFirst(">", "");

            if (valueToParse.indexOf("m") != -1) {
                dsConditions.add(new DSCondition("getAgeInMonths", "", ">=", valueToParse.replaceAll("m", "")));
            } else { // assume years
                dsConditions.add(new DSCondition("getAgeInYears", "", ">=", valueToParse.replaceAll("y", "")));
            }
        } else if (valueToParse.indexOf("&lt;") != -1 || valueToParse.indexOf("<") != -1) { // less than style
            // Strip both XML-escaped and literal less-than signs
            valueToParse = valueToParse.replaceFirst("&lt;", "");
            valueToParse = valueToParse.replaceFirst("<", "");

            if (valueToParse.indexOf("m") != -1) {
                dsConditions.add(new DSCondition("getAgeInMonths", "", "<=", valueToParse.replaceAll("m", "")));
            } else { // assume years
                dsConditions.add(new DSCondition("getAgeInYears", "", "<=", valueToParse.replaceAll("y", "")));
            }

        } else if (valueToParse.indexOf("!=") != -1) { // not-equal style
            valueToParse = valueToParse.replaceFirst("!=", "");
            if (valueToParse.indexOf("m") != -1) {
                dsConditions.add(new DSCondition("getAgeInMonths", "", "!=", valueToParse.replaceAll("m", "")));
            } else { // assume years
                dsConditions.add(new DSCondition("getAgeInYears", "", "!=", valueToParse.replaceAll("y", "")));
            }

        } else if (!valueToParse.isEmpty()) { // exact match style (plain value)
            if (valueToParse.indexOf("m") != -1) {
                dsConditions.add(new DSCondition("getAgeInMonths", "", "==", valueToParse.replaceAll("m", "")));
            } else { // assume years
                dsConditions.add(new DSCondition("getAgeInYears", "", "==", valueToParse.replaceAll("y", "")));
            }
        }
    }
}
