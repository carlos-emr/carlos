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
package io.github.carlos_emr.carlos.appointment.search;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Defines a time slot filter used in the appointment search pipeline.
 *
 * <p>Each filter definition holds a fully qualified class name of an
 * {@link io.github.carlos_emr.carlos.appointment.search.filters.AvailableTimeSlotFilter}
 * implementation along with a parameter map parsed from XML configuration attributes.
 * Filters are applied sequentially to narrow down available appointment time slots.</p>
 *
 * @since 2026-03-17
 */
public class FilterDefinition {
    private static Logger logger = MiscUtils.getLogger();

    String filterClassName = null;
    Map<String, String> params = new HashMap<String, String>();


    /**
     * Returns the fully qualified class name of the filter implementation.
     *
     * @return String the filter class name
     */
    public String getFilterClassName() {
        return filterClassName;
    }

    /**
     * Sets the fully qualified class name of the filter implementation.
     *
     * @param filterClassName String the filter class name
     */
    public void setFilterClassName(String filterClassName) {
        this.filterClassName = filterClassName;
    }

    /**
     * Returns the configuration parameters for this filter.
     *
     * @return Map&lt;String, String&gt; the parameter map
     */
    public Map<String, String> getParams() {
        return params;
    }

    /**
     * Sets the configuration parameters for this filter.
     *
     * @param params Map&lt;String, String&gt; the parameter map
     */
    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    /**
     * Creates a {@code FilterDefinition} from an XML node.
     *
     * <p>The node's text content is used as the filter class name. All XML attributes
     * on the node are extracted as key-value parameter pairs.</p>
     *
     * @param node Node the XML node containing the filter definition
     * @return FilterDefinition the parsed filter definition
     */
    public static FilterDefinition fromXml(Node node) {
        FilterDefinition filterDefinition = new FilterDefinition();
        filterDefinition.setFilterClassName(node.getTextContent());
        logger.debug("FilterDefinition " + filterDefinition.getFilterClassName());

        NamedNodeMap attributes = node.getAttributes();

        if (attributes != null) {
            filterDefinition.params = new HashMap<String, String>();
            for (int i = 0; i < attributes.getLength(); i++) {
                filterDefinition.params.put(attributes.item(i).getNodeName(), attributes.item(i).getNodeValue());
                logger.debug(filterDefinition.getFilterClassName() + "--" + attributes.item(i).getNodeName() + "--" + attributes.item(i).getNodeValue());
            }
        }
        return filterDefinition;
    }

}
