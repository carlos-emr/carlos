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
package io.github.carlos_emr.carlos.dashboard.factory;

import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.dashboard.display.beans.GraphPlot;
import io.github.carlos_emr.carlos.dashboard.display.beans.IndicatorBean;
import io.github.carlos_emr.carlos.dashboard.handler.IndicatorQueryHandler;
import io.github.carlos_emr.carlos.dashboard.handler.IndicatorTemplateXML;
import io.github.carlos_emr.carlos.dashboard.query.Parameter;
import io.github.carlos_emr.carlos.dashboard.query.RangeInterface;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Factory for constructing {@link IndicatorBean} objects from parsed {@link IndicatorTemplateXML}.
 *
 * <p>Builds the indicator query by applying parameters and ranges, executes it via
 * {@link IndicatorQueryHandler}, and populates the resulting bean with graph plot data
 * in multiple formats (JSON, string arrays) for client-side rendering.</p>
 *
 * @since 2026-03-17
 */
public class IndicatorBeanFactory {

    private static Logger logger = MiscUtils.getLogger();
    private IndicatorQueryHandler indicatorQueryHandler = SpringUtils.getBean(IndicatorQueryHandler.class);

    private IndicatorTemplateXML indicatorTemplateXML;
    private IndicatorBean indicatorBean;
    private List<Parameter> parameters;
    private List<RangeInterface> ranges;
    private String indicatorQuery;

    /**
     * Constructs the factory and immediately builds the indicator bean by parsing
     * the template XML, executing the query, and populating graph data.
     *
     * @param indicatorTemplateXML IndicatorTemplateXML the parsed indicator template
     */
    public IndicatorBeanFactory(IndicatorTemplateXML indicatorTemplateXML) {

        logger.info("Thread " + Thread.currentThread().getName() + "[" + Thread.currentThread().getId()
                + "] Building Indicator ID: " + indicatorTemplateXML.getId() + " - " + indicatorTemplateXML.getName());

        setIndicatorTemplateXML(indicatorTemplateXML);

        this.parameters = getIndicatorTemplateXML().getIndicatorParameters();
        this.ranges = getIndicatorTemplateXML().getIndicatorRanges();

        setIndicatorQuery(getIndicatorTemplateXML().getIndicatorQuery());
        setIndicatorBean(new IndicatorBean());
    }

    /**
     * Returns the parsed indicator template XML.
     *
     * @return IndicatorTemplateXML the parsed template
     */
    public IndicatorTemplateXML getIndicatorTemplateXML() {
        return indicatorTemplateXML;
    }

    private void setIndicatorTemplateXML(IndicatorTemplateXML indicatorTemplateXML) {
        this.indicatorTemplateXML = indicatorTemplateXML;
    }

    /**
     * Returns the fully constructed indicator display bean with query results and graph data.
     *
     * @return IndicatorBean the constructed indicator bean
     */
    public IndicatorBean getIndicatorBean() {
        return indicatorBean;
    }

    /**
     * Returns the query handler used for executing indicator queries.
     *
     * @return IndicatorQueryHandler the indicator query handler
     */
    public IndicatorQueryHandler getIndicatorQueryHandler() {
        return indicatorQueryHandler;
    }

    /**
     * Returns the final processed indicator query string with parameters and ranges applied.
     *
     * @return String the processed SQL query string
     */
    public String getIndicatorQuery() {
        return indicatorQuery;
    }

    private void setIndicatorQuery(final String indicatorQuery) {

        String queryString = new String(indicatorQuery);

        queryString = getIndicatorQueryHandler().filterQueryString(queryString);

        if (parameters != null) {
            queryString = getIndicatorQueryHandler().addParameters(parameters, queryString);
        }

        if (ranges != null) {
            queryString = getIndicatorQueryHandler().addRanges(ranges, queryString);
        }

        this.indicatorQuery = queryString;
    }

    private void setIndicatorBean(IndicatorBean indicatorBean) {

        copyToBean(indicatorBean, getIndicatorTemplateXML());

        List<?> queryResultList = getIndicatorQueryHandler().execute(this.indicatorQuery);

        if (queryResultList != null) {

            logger.info("Setting Indicator query results for Indicator bean " + indicatorBean.getId());

            indicatorBean.setOriginalJsonPlots(IndicatorQueryHandler.createOriginalGraphPlots(queryResultList));
            List<GraphPlot[]> graphPlots = IndicatorQueryHandler.createGraphPlots(queryResultList);
            indicatorBean.setQueryResult(queryResultList);
            indicatorBean.setQueryString(this.indicatorQuery);
            indicatorBean.setParameters(this.parameters);
            indicatorBean.setRanges(this.ranges);
            indicatorBean.setGraphPlots(graphPlots);
            indicatorBean.setJsonPlots(IndicatorQueryHandler.plotsToJson(graphPlots));
            indicatorBean.setJsonTooltips(IndicatorQueryHandler.plotsToJsonTooltips(graphPlots));
            indicatorBean.setStringArrayPlots(IndicatorQueryHandler.plotsToStringArray(graphPlots));
            indicatorBean.setStringArrayTooltips(IndicatorQueryHandler.plotsToTooltipsStringArray(graphPlots));

            logger.debug("Indicator Bean: " + indicatorBean.toString());

        } else {
            logger.warn(" The query results and-or the Indicator Query handler were null for Indicator ID: "
                    + indicatorBean.getId() + " Was this expected?");
        }

        this.indicatorBean = indicatorBean;
    }

    private static void copyToBean(IndicatorBean indicatorBean, IndicatorTemplateXML indicatorTemplateXML) {

        Integer indicatorId = indicatorTemplateXML.getId();

        logger.info("Thread " + Thread.currentThread().getName() + "[" + Thread.currentThread().getId()
                + "] Setting Indicator Bean heading info for ID " + indicatorId);

        indicatorBean.setId(indicatorId);
        indicatorBean.setCategory(indicatorTemplateXML.getCategory());
        indicatorBean.setDefinition(indicatorTemplateXML.getDefinition());
        indicatorBean.setFramework(indicatorTemplateXML.getFramework());
        indicatorBean.setFrameworkVersion(indicatorTemplateXML.getFrameworkVersion());
        indicatorBean.setName(indicatorTemplateXML.getName());
        indicatorBean.setNotes(indicatorTemplateXML.getNotes());
        indicatorBean.setSubCategory(indicatorTemplateXML.getSubCategory());
        indicatorBean.setXmlTemplate(indicatorTemplateXML.getTemplate());

    }

}
