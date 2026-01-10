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
package ca.openosp.openo.dashboard.handler;

import java.util.List;

import org.apache.logging.log4j.Logger;
import org.hibernate.Criteria;
import org.hibernate.SQLQuery;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ca.openosp.openo.dashboard.handler.IndicatorTemplateXML.RangeType;
import ca.openosp.openo.dashboard.query.Column;
import ca.openosp.openo.dashboard.query.DrillDownAction;
import ca.openosp.openo.dashboard.query.Parameter;
import ca.openosp.openo.dashboard.query.RangeInterface;
import ca.openosp.openo.dashboard.query.RangeInterface.Limit;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;

/**
 * Abstract base class for dashboard query handlers in OpenO EMR.
 * <p>
 * This class provides core functionality for building and executing SQL queries
 * used in the dashboard indicators, drilldowns, and exports. It manages query
 * parameterization, range filtering, and column selection through a template-based
 * approach using placeholder patterns.
 * </p>
 * 
 * <h2>Query Building</h2>
 * <p>
 * Queries are built by replacing placeholder patterns in template strings with
 * actual values from parameters, ranges, and columns. The placeholder pattern is:
 * <code>${ parameter_id }</code>
 * </p>
 * 
 * <h2>Subclassing</h2>
 * <p>
 * Subclasses should override the {@code execute()} method to provide specialized
 * query execution and result processing logic. Common subclasses include:
 * </p>
 * <ul>
 *   <li>{@link IndicatorQueryHandler} - Handles indicator queries and graph plot generation</li>
 *   <li>{@link DrilldownQueryHandler} - Handles drilldown queries and table display</li>
 *   <li>{@link ExportQueryHandler} - Handles data export queries and CSV generation</li>
 * </ul>
 * 
 * <h2>Thread Safety</h2>
 * <p>
 * This class is NOT thread-safe. Each thread should use its own instance of the handler.
 * The query building methods modify internal state and should not be called concurrently.
 * </p>
 * 
 * @see IndicatorQueryHandler
 * @see DrilldownQueryHandler
 * @see ExportQueryHandler
 */
@Service
public abstract class AbstractQueryHandler {

    private static Logger logger = MiscUtils.getLogger();
    
    @Autowired
    private SessionFactory sessionFactory;
    
    /**
     * Gets the current Hibernate session from the session factory.
     * 
     * @return the current Hibernate session
     */
    protected Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    private static final String PLACE_HOLDER_PATTERN = "(\\$){1}(\\{){1}( )*##( )*(\\}){1}";
    private static final String COMMENT_BLOCK_PATTERN = "/\\*(?:.|[\\n\\r])*?\\*/";

    private List<Parameter> parameters;
    private List<RangeInterface> ranges;
    private List<DrillDownAction> actions;
    private String query;
    private List<?> resultList;
    private List<Column> columns;
    private LoggedInInfo loggedInInfo;

    /**
     * Default constructor.
     */
    public AbstractQueryHandler() {
        // default
    }

    /**
     * Executes the query that has been set on this handler.
     * 
     * @return the query results as a list of maps, or null if the query is empty
     */
    protected List<?> execute() {
        if (getQuery().isEmpty()) {
            logger.error("Failed to execute query.");
            return null;
        }
        return execute(getQuery());
    }

    /**
     * Executes the given SQL query and returns the results.
     * <p>
     * The query is executed using a native SQL query with results transformed
     * to a map representation (alias to entity map). Results are logged and
     * stored internally for later retrieval.
     * </p>
     * 
     * @param query the SQL query string to execute
     * @return the query results as a list of maps
     */
    protected List<?> execute(String query) {

        setResultList(null);

        Session session = getSession();
        SQLQuery sqlQuery = session.createSQLQuery(query);
        List<?> results = sqlQuery.setResultTransformer(Criteria.ALIAS_TO_ENTITY_MAP).list();

        logger.info("Thread " + Thread.currentThread().getName() + "[" + Thread.currentThread().getId()
                + "] Query results " + results);

        //TODO work on method to detect and exclude demographic files that are
        // defined in the securityInfoManager object.

        setResultList(results);
        session.close();

        return results;
    }


    /**
     * Gets the list of parameters for this query handler.
     * 
     * @return the list of parameters
     */
    public List<Parameter> getParameters() {
        return parameters;
    }

    /**
     * Sets the parameters for this query handler.
     * 
     * @param parameters the list of parameters to set
     */
    public void setParameters(List<Parameter> parameters) {
        this.parameters = parameters;
    }

    /**
     * Gets the list of drill-down actions for this query handler.
     * 
     * @return the list of drill-down actions
     */
    public List<DrillDownAction> getActions() {
        return actions;
    }

    /**
     * Sets the drill-down actions for this query handler.
     * 
     * @param actions the list of drill-down actions to set
     */
    public void setActions(List<DrillDownAction> actions) {
        this.actions = actions;
    }

    /**
     * Gets the list of range filters for this query handler.
     * 
     * @return the list of range filters
     */
    public List<RangeInterface> getRanges() {
        return ranges;
    }

    /**
     * Sets the range filters for this query handler.
     * 
     * @param ranges the list of range filters to set
     */
    public void setRanges(List<RangeInterface> ranges) {
        this.ranges = ranges;
    }

    /**
     * Gets the current query string.
     * 
     * @return the query string, or an empty string if not set
     */
    public String getQuery() {
        if (query == null) {
            return "";
        }
        return query;
    }

    /**
     * Sets the query string for this handler.
     * 
     * @param query the SQL query string to set
     */
    protected void setQuery(String query) {
        this.query = query;
    }

    /**
     * Gets the results from the last query execution.
     * 
     * @return the list of query results
     */
    public List<?> getResultList() {
        return resultList;
    }

    /**
     * Sets the result list from query execution.
     * 
     * @param resultList the list of results to set
     */
    private void setResultList(List<?> resultList) {
        this.resultList = resultList;
    }

    /**
     * Gets the list of columns for this query handler.
     * 
     * @return the list of columns
     */
    public List<Column> getColumns() {
        return columns;
    }

    /**
     * Sets the columns for this query handler.
     * 
     * @param columns the list of columns to set
     */
    public void setColumns(List<Column> columns) {
        this.columns = columns;
    }

    /**
     * Gets the logged-in user information for this query handler.
     * 
     * @return the logged-in user information
     */
    protected LoggedInInfo getLoggedInInfo() {
        return loggedInInfo;
    }

    /**
     * Sets the logged-in user information for this query handler.
     * 
     * @param loggedInInfo the logged-in user information to set
     */
    public void setLoggedInInfo(LoggedInInfo loggedInInfo) {
        this.loggedInInfo = loggedInInfo;
    }

    /**
     * Builds a final query string with all placeholders filled in.
     * <p>
     * This method processes the query template in the following order:
     * </p>
     * <ol>
     *   <li>Filters the query string (removes comments and special characters)</li>
     *   <li>Adds columns (rewrites SELECT statement if columns are specified)</li>
     *   <li>Adds parameters (replaces parameter placeholders with values)</li>
     *   <li>Adds ranges (replaces range placeholders with values)</li>
     * </ol>
     * <p>
     * <b>Note:</b> This method is NOT thread-safe. Each thread should use its own
     * instance of the handler.
     * </p>
     * 
     * @param query the query template string
     * @return the final query string with all placeholders replaced
     */
    protected final String buildQuery(final String query) {

        String queryString = new String(query);

        queryString = filterQueryString(queryString);

        // columns should always be first. Columns can contain parameters
        // and ranges.
        if (getColumns() != null) {
            queryString = addColumns(queryString);
        }

        if (getParameters() != null) {
            queryString = addParameters(getParameters(), queryString);
        }

        if (getRanges() != null) {
            queryString = addRanges(getRanges(), queryString);
        }

        return queryString;
    }

    /**
     * Sets the parameter values into the given query string.
     * <p>
     * Searches the query string for placeholder patterns matching each parameter ID.
     * The pattern format is: {@code ${ parameter_id }}
     * </p>
     * 
     * @param parameters the list of parameters to insert
     * @param query the query string with placeholders
     * @return the query string with parameter values inserted
     */
    public final String addParameters(List<Parameter> parameters, String query) {

        for (Parameter parameter : parameters) {
            query = addParameter(parameter, query);
        }

        return query;
    }

    /**
     * Adds a single parameter to the query string.
     * 
     * @param parameter the parameter to insert
     * @param query the query string with placeholders
     * @return the query string with the parameter value inserted
     */
    protected String addParameter(Parameter parameter, String query) {

        String parameterId = parameter.getId();
        String parameterValue = parseParameterValue(parameter.getValue());

        // set default and predetermined parameter values here.
        parameterId = getPattern(parameterId);
        return patternReplace(parameterId, query, parameterValue);
    }

    /**
     * Sets the range values into the given query string.
     * <p>
     * Searches the query string for placeholder patterns matching each range ID.
     * Range placeholders are prefixed with either "lowerLimit." or "upperLimit."
     * depending on the range type.
     * </p>
     * 
     * @param ranges the list of ranges to insert
     * @param query the query string with placeholders
     * @return the query string with range values inserted
     */
    public final String addRanges(List<RangeInterface> ranges, String query) {

        for (RangeInterface range : ranges) {
            query = addRange(range, query);
        }

        return query;
    }

    /**
     * Adds a single range to the query string.
     * 
     * @param range the range to insert
     * @param query the query string with placeholders
     * @return the query string with the range value inserted
     */
    protected String addRange(RangeInterface range, String query) {

        String addPattern = range.getId().trim();

        if (Limit.RangeLowerLimit.name().equals(range.getClass().getSimpleName())) {
            addPattern = RangeType.lowerLimit.name() + "\\." + addPattern;
        } else {
            addPattern = RangeType.upperLimit.name() + "\\." + addPattern;
        }

        String pattern = getPattern(addPattern);
        return patternReplace(pattern, query, range.getValue());
    }

    /**
     * Rewrites the entire SELECT statement if the column list is set.
     * <p>
     * When columns are specified, this method:
     * </p>
     * <ol>
     *   <li>Builds a new SELECT clause using column names and titles</li>
     *   <li>Removes the existing SELECT clause from the query</li>
     *   <li>Prepends the new SELECT clause to the FROM clause</li>
     * </ol>
     * 
     * @param queryString the original query string
     * @return the query string with the SELECT statement rewritten
     */
    protected String addColumns(String queryString) {

        StringBuilder select = new StringBuilder("SELECT ");
        int from = 0;

        for (Column column : getColumns()) {

            select.append(column.getName());
            select.append(" AS ");
            select.append("'").append(column.getTitle()).append("',");
        }

        select.deleteCharAt(select.length() - 1);
        select.append(" ");

        logger.debug("Replacing current select statement with " + select.toString());

        from = queryString.indexOf("FROM");

        if (from < 0) {
            from = queryString.indexOf("from");
        }

        if (from < 0) {
            from = queryString.indexOf("From");
        }

        if (from < 0) {
            logger.warn("Syntax error with the MySQL FROM statement. Syntax permitted is FROM, from or From ");
        }

        // remove the current select statement
        queryString = queryString.substring(from, queryString.length());
        queryString = select.toString() + queryString;

        logger.debug("Final query with columns " + queryString);

        return queryString;

    }


    /**
     * Injects a variable pattern value into the predefined string replacement pattern.
     * <p>
     * Example pattern result: {@code (\$){1}(\{){1}( )* firstName ( )*(\}){1}}
     * </p>
     * 
     * @param patternValue the parameter or range ID to inject into the pattern
     * @return the complete regex pattern for matching placeholders
     */
    private String getPattern(String patternValue) {
        return new String(PLACE_HOLDER_PATTERN.replace("##", patternValue.trim()));
    }

    /**
     * Replaces all given patterns in the query string with the given value.
     * 
     * @param pattern the regex pattern to match
     * @param query the query string to search
     * @param value the value to replace matched patterns with
     * @return the query string with patterns replaced
     */
    private String patternReplace(String pattern, String query, String value) {
        logger.debug("Inserting pattern " + pattern + " with a value of " + value);
        return query.replaceAll(pattern, value);
    }

    /**
     * Parses a parameter value array into a comma-delimited string for use
     * in a SQL query.
     * <p>
     * For single values, returns the trimmed value.
     * For multiple values, returns a parenthesized, comma-separated list suitable
     * for SQL IN clauses: {@code ('value1','value2','value3')}
     * </p>
     * 
     * @param values the array of parameter values
     * @return the formatted parameter value string
     */
    private static String parseParameterValue(String[] values) {
        String value = "";

        if (values.length > 1) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("(");
            for (int i = 0; i < values.length; i++) {
                stringBuilder.append("'").append(values[i]).append("',");
            }
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
            stringBuilder.append(")");
            value = stringBuilder.toString();
        } else {
            value = values[0].trim();
        }

        return value;
    }

    /**
     * Filters the query string by removing comments and special characters.
     * <p>
     * This method performs the following operations:
     * </p>
     * <ul>
     *   <li>Removes block comments (/* ... */)</li>
     *   <li>Removes lines starting with -- or #</li>
     *   <li>Removes question marks and colons</li>
     *   <li>Removes empty lines</li>
     * </ul>
     * 
     * @param queryString the raw query string
     * @return the filtered query string
     */
    protected String filterQueryString(String queryString) {

        // Remove comment blocks
        String query = queryString.replaceAll(COMMENT_BLOCK_PATTERN, "");
        String[] lines = query.split("\\n");
        StringBuilder stringBuilder = new StringBuilder("");

        for (String line : lines) {

            line = line.trim();

            if (!line.startsWith("--") && !line.isEmpty() && !line.startsWith("#")) {

                line = line.replaceAll("\\?", "");
                line = line.replaceAll("\\:", "");

                logger.debug("Query line: " + line);

                stringBuilder.append(line);
                stringBuilder.append(" ");
            }
        }
        stringBuilder.deleteCharAt(stringBuilder.length() - 1);

        return stringBuilder.toString();
    }

}
