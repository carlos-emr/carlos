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
package io.github.carlos_emr.carlos.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import jakarta.persistence.Query;

/**
 * Extension of {@link QueryAppender} that adds named parameter support for JPQL queries.
 * Allows building parameterized WHERE clauses by associating parameter names and values
 * with AND/OR conditions, and then binding them to a {@link jakarta.persistence.Query}.
 *
 * @since 2001-01-01
 */
public class ParamAppender extends QueryAppender {

    private Map<String, Object> params = new HashMap<String, Object>();

    public ParamAppender() {
        super();
    }

    public ParamAppender(String baseQuery) {
        super(baseQuery);
    }

    /**
     * Appends a clause with a logical OR operator and registers a named parameter.
     * If the parameter value is null, the clause is not appended.
     *
     * @param clause String the WHERE sub-clause containing a named parameter placeholder
     * @param paramName String the parameter name used in the clause
     * @param paramValue Object the parameter value, or null to skip this clause
     */
    public void or(String clause, String paramName, Object paramValue) {
        if (paramValue == null) {
            return;
        }
        or(clause);
        addParam(paramName, paramValue);
    }

    /**
     * Appends a clause with a logical AND operator and registers a named parameter.
     * If the parameter value is null, the clause is not appended.
     *
     * @param clause String the WHERE sub-clause containing a named parameter placeholder
     * @param paramName String the parameter name used in the clause
     * @param paramValue Object the parameter value, or null to skip this clause
     */
    public void and(String clause, String paramName, Object paramValue) {
        if (paramValue == null) {
            return;
        }
        and(clause);
        addParam(paramName, paramValue);
    }

    /**
     * Registers a named parameter with its value for later binding.
     *
     * @param paramName String the parameter name
     * @param paramValue Object the parameter value
     * @return Object the previous value associated with the parameter name, or null
     */
    public Object addParam(String paramName, Object paramValue) {
        return getParams().put(paramName, paramValue);
    }

    public Map<String, Object> getParams() {
        return params;
    }

    protected void setParams(Map<String, Object> params) {
        this.params = params;
    }

    /**
     * Binds all registered parameters to the given JPA {@link Query}.
     *
     * @param query Query the JPA query to bind parameters to
     * @return Query the same query instance with parameters set
     */
    public Query setParams(Query query) {
        for (Entry<String, Object> param : getParams().entrySet()) {
            query.setParameter(param.getKey(), param.getValue());
        }
        return query;
    }

    public void and(ParamAppender appender) {
        super.and(appender);
        mergeParams(appender);
    }

    public void or(ParamAppender appender) {
        super.or(appender);
        mergeParams(appender);
    }

    /**
     * Adds all parameters from the specified appended to this appender
     *
     * @param paramAppender Parameter appender to merge parameters from
     * @return Returns this instance.
     */
    public ParamAppender mergeParams(ParamAppender paramAppender) {
        getParams().putAll(paramAppender.getParams());
        return this;
    }
}
