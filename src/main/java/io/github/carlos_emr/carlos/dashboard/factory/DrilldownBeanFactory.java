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

import org.springframework.beans.BeanUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.IndicatorTemplate;
import io.github.carlos_emr.carlos.dashboard.display.beans.DrilldownBean;
import io.github.carlos_emr.carlos.dashboard.handler.DrilldownQueryHandler;
import io.github.carlos_emr.carlos.dashboard.handler.IndicatorTemplateHandler;
import io.github.carlos_emr.carlos.dashboard.handler.IndicatorTemplateXML;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Factory for constructing {@link DrilldownBean} objects from an {@link IndicatorTemplate}.
 *
 * <p>Parses the indicator template XML, configures the {@link DrilldownQueryHandler} with
 * parameters, columns, ranges, and actions, then executes the drilldown query and
 * populates the resulting bean with tabular data.</p>
 *
 * @since 2026-03-17
 */
public class DrilldownBeanFactory {

    private static Logger logger = MiscUtils.getLogger();
    private IndicatorTemplate indicatorTemplate;
    private IndicatorTemplateXML indicatorTemplateXML;
    private IndicatorTemplateHandler indicatorTemplateHandler;
    private DrilldownBean drilldownBean;
    private DrilldownQueryHandler drilldownQueryHandler = SpringUtils.getBean(DrilldownQueryHandler.class);

    /**
     * Constructs a drilldown bean for the given indicator template without provider filtering.
     *
     * @param loggedInInfo LoggedInInfo the logged-in user context
     * @param indicatorTemplate IndicatorTemplate the template to drill down into
     */
    public DrilldownBeanFactory(LoggedInInfo loggedInInfo, IndicatorTemplate indicatorTemplate) {
        this(loggedInInfo, indicatorTemplate, null, null);
    }

    /**
     * Constructs a drilldown bean for the given indicator template, optionally
     * scoped to a specific provider and metric label.
     *
     * @param loggedInInfo LoggedInInfo the logged-in user context
     * @param indicatorTemplate IndicatorTemplate the template to drill down into
     * @param providerNo String the provider number to filter by, or {@code null} for all
     * @param metricLabel String the metric label for shared metric filtering, or {@code null}
     */
    public DrilldownBeanFactory(LoggedInInfo loggedInInfo, IndicatorTemplate indicatorTemplate, String providerNo, String metricLabel) {

        logger.info("Building Drilldown Bean for Indicator ID: " + indicatorTemplate.getId());

        setIndicatorTemplate(indicatorTemplate);
        String indicatorTemplateXML = getIndicatorTemplate().getTemplate();
        setIndicatorTemplateHandler(new IndicatorTemplateHandler(loggedInInfo, indicatorTemplateXML.getBytes()));
        IndicatorTemplateXML indicatorTemplateXmlObj = getIndicatorTemplateHandler().getIndicatorTemplateXML();
        indicatorTemplateXmlObj.setProviderNo(providerNo);
        indicatorTemplateXmlObj.setSharedMetricLabel(metricLabel);
        setIndicatorTemplateXML(indicatorTemplateXmlObj);

        drilldownQueryHandler.setLoggedInInfo(loggedInInfo);
        drilldownQueryHandler.setParameters(getIndicatorTemplateXML().getDrilldownParameters(metricLabel));
        drilldownQueryHandler.setColumns(getIndicatorTemplateXML().getDrilldownDisplayColumns());
        drilldownQueryHandler.setRanges(getIndicatorTemplateXML().getDrilldownRanges());
        drilldownQueryHandler.setActions(getIndicatorTemplateXML().getDrilldownActions());

        setDrilldownBean(new DrilldownBean());
    }

    /**
     * Returns the indicator template used to build this drilldown.
     *
     * @return IndicatorTemplate the source indicator template
     */
    public IndicatorTemplate getIndicatorTemplate() {
        return indicatorTemplate;
    }

    private void setIndicatorTemplate(IndicatorTemplate indicatorTemplate) {
        this.indicatorTemplate = indicatorTemplate;
    }

    /**
     * Returns the parsed XML representation of the indicator template.
     *
     * @return IndicatorTemplateXML the parsed template XML
     */
    public IndicatorTemplateXML getIndicatorTemplateXML() {
        return indicatorTemplateXML;
    }

    private void setIndicatorTemplateXML(IndicatorTemplateXML indicatorTemplateXML) {
        this.indicatorTemplateXML = indicatorTemplateXML;
    }

    /**
     * Returns the handler used to parse the indicator template XML.
     *
     * @return IndicatorTemplateHandler the template handler
     */
    public IndicatorTemplateHandler getIndicatorTemplateHandler() {
        return indicatorTemplateHandler;
    }

    private void setIndicatorTemplateHandler(IndicatorTemplateHandler indicatorTemplateHandler) {
        this.indicatorTemplateHandler = indicatorTemplateHandler;
    }

    /**
     * Returns the query handler used to execute the drilldown query.
     *
     * @return DrilldownQueryHandler the drilldown query handler
     */
    public DrilldownQueryHandler getDrilldownQueryHandler() {
        return drilldownQueryHandler;
    }

    /**
     * Returns the fully constructed drilldown display bean with query results and table data.
     *
     * @return DrilldownBean the constructed drilldown bean
     */
    public DrilldownBean getDrilldownBean() {
        return drilldownBean;
    }

    private void setDrilldownBean(DrilldownBean drilldownBean) {
        // copy what is available in the entity bean
        try {
            BeanUtils.copyProperties(getIndicatorTemplate(), drilldownBean);
        } catch (Exception e) {
            logger.error("Error while copying IndicatorTemplate entity id " + getIndicatorTemplate().getId(), e);
        }

        List<?> queryResultList = null;

        if (getDrilldownQueryHandler() != null) {
            getDrilldownQueryHandler().setQuery(getIndicatorTemplateXML().getDrilldownQuery());
            queryResultList = getDrilldownQueryHandler().execute();
        }

        if (queryResultList != null) {

            drilldownBean.setQueryResult(queryResultList);
            drilldownBean.setQueryString(getDrilldownQueryHandler().getQuery());
            drilldownBean.setDisplayColumns(getDrilldownQueryHandler().getColumns());
            drilldownBean.setParameters(getDrilldownQueryHandler().getParameters());
            drilldownBean.setRanges(getDrilldownQueryHandler().getRanges());
            drilldownBean.setActions(getDrilldownQueryHandler().getActions());
            drilldownBean.setTable(getDrilldownQueryHandler().getTable());

        } else {
            logger.warn(" The query results and-or the Indicator Query handler were null for Drilldown Indicator ID: "
                    + drilldownBean.getId() + " Was this expected?");
        }

        this.drilldownBean = drilldownBean;
    }

}
