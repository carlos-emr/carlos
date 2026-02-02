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
import org.hibernate.*;
import ca.openosp.openo.dashboard.handler.IndicatorTemplateXML.RangeType;
import ca.openosp.openo.dashboard.query.Column;
import ca.openosp.openo.dashboard.query.DrillDownAction;
import ca.openosp.openo.dashboard.query.Parameter;
import ca.openosp.openo.dashboard.query.RangeInterface;
import ca.openosp.openo.dashboard.query.RangeInterface.Limit;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import org.springframework.orm.hibernate5.support.HibernateDaoSupport;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public abstract class AbstractQueryHandler extends HibernateDaoSupport {

    private static Logger logger = MiscUtils.getLogger();

    @Autowired
    public void setSessionFactoryOverride(SessionFactory sessionFactory) {
        super.setSessionFactory(sessionFactory);
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

    public AbstractQueryHandler() {

    }

    protected List<?> execute() {
        if (getQuery().isEmpty()) {
            logger.error("Failed to execute query.");
            return null;
        }
        return execute(getQuery());
    }

    protected List<?> execute(String query) {

        setResultList(null);

        Transaction tx = null;
        try (Session session = getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            SQLQuery sqlQuery = session.createSQLQuery(query);
            List<?> results = sqlQuery.setResultTransformer(Criteria.ALIAS_TO_ENTITY_MAP).list();

            //TODO work on method to detect and exclude demographic files that are
            // defined in the securityInfoManager object.

            setResultList(results);
            tx.commit();

            return results;
        } catch (Exception e) {
            if (tx != null) {
                try {
                    tx.rollback();
                } catch (Exception rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
            }
            logger.error("Query execution failed");
            throw new RuntimeException("Error executing query", e);
        }
    }


    public List<Parameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<Parameter> parameters) {
        this.parameters = parameters;
    }

    public List<DrillDownAction> getActions() {
        return actions;
    }

    public void setActions(List<DrillDownAction> actions) {
        this.actions = actions;
    }

    public List<RangeInterface> getRanges() {
        return ranges;
    }

    public void setRanges(List<RangeInterface> ranges) {
        this.ranges = ranges;
    }

    public String getQuery() {
        if (query == null) {
            return "";
        }
        return query;
    }

    protected void setQuery(String query) {
        this.query = query;
    }

    public List<?> getResultList() {
        return resultList;
    }

    private void setResultList(List<?> resultList) {
        this.resultList = resultList;
    }

    public List<Column> getColumns() {
        return columns;
    }

    public void setColumns(List<Column> columns) {
        this.columns = columns;
    }

    protected LoggedInInfo getLoggedInInfo() {
        return loggedInInfo;
    }

    public void setLoggedInInfo(LoggedInInfo loggedInInfo) {
        this.loggedInInfo = loggedInInfo;
    }

    /**
     * Build a final query string with all the place-holders filled in.
     * <p>
     * Not Thread Safe
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
     * Set the parameter values into the given query string.
     * Searches the query string for a specific string pattern.
     * ie: "(\\$){1}(\\{){1}( )* [firstName] ( )*(\\}){1}"
     */
    public final String addParameters(List<Parameter> parameters, String query) {

        for (Parameter parameter : parameters) {
            query = addParameter(parameter, query);
        }

        return query;
    }

    protected String addParameter(Parameter parameter, String query) {

        String parameterId = parameter.getId();
        String parameterValue = parseParameterValue(parameter.getValue());

        // set default and predetermined parameter values here.
        parameterId = getPattern(parameterId);
        return patternReplace(parameterId, query, parameterValue);
    }

    /**
     * Set the Range values into the given query string.
     * Searches the query string for a specific string pattern.
     * ie: "(\\$){1}(\\{){1}( )* [firstName] ( )*(\\}){1}"
     */

    public final String addRanges(List<RangeInterface> ranges, String query) {

        for (RangeInterface range : ranges) {
            query = addRange(range, query);
        }

        return query;
    }

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
     * The entire select syntax will be rewritten if the column list is set.
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
     * Injects a variable pattern value into the predetermine string replacement pattern.
     * ie: "(\\$){1}(\\{){1}( )* [firstName] ( )*(\\}){1}"
     */
    private String getPattern(String patternValue) {
        return new String(PLACE_HOLDER_PATTERN.replace("##", patternValue.trim()));
    }

    /*
     * Replaces all given patterns in the given string with the given value.
     */
    private String patternReplace(String pattern, String query, String value) {
        logger.debug("Inserting pattern " + pattern + " with a value of " + value);
        return query.replaceAll(pattern, value);
    }

    /**
     * parses a parameter value array into a comma delimited string for use
     * in a SQL query.
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
     * Removes pesky colons, question marks, and comments from the
     * query string.
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
