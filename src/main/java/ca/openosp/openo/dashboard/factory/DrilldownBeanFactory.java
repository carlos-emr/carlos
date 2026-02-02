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
package ca.openosp.openo.dashboard.factory;

import java.util.List;

import org.springframework.beans.BeanUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.model.IndicatorTemplate;
import ca.openosp.openo.dashboard.display.beans.DrilldownBean;
import ca.openosp.openo.dashboard.handler.DrilldownQueryHandler;
import ca.openosp.openo.dashboard.handler.IndicatorTemplateHandler;
import ca.openosp.openo.dashboard.handler.IndicatorTemplateXML;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

public class DrilldownBeanFactory {

    private static Logger logger = MiscUtils.getLogger();
    private IndicatorTemplate indicatorTemplate;
    private IndicatorTemplateXML indicatorTemplateXML;
    private IndicatorTemplateHandler indicatorTemplateHandler;
    private DrilldownBean drilldownBean;
    private DrilldownQueryHandler drilldownQueryHandler = SpringUtils.getBean(DrilldownQueryHandler.class);

    public DrilldownBeanFactory(LoggedInInfo loggedInInfo, IndicatorTemplate indicatorTemplate) {
        this(loggedInInfo, indicatorTemplate, null, null);
    }

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

    public IndicatorTemplate getIndicatorTemplate() {
        return indicatorTemplate;
    }

    private void setIndicatorTemplate(IndicatorTemplate indicatorTemplate) {
        this.indicatorTemplate = indicatorTemplate;
    }

    public IndicatorTemplateXML getIndicatorTemplateXML() {
        return indicatorTemplateXML;
    }

    private void setIndicatorTemplateXML(IndicatorTemplateXML indicatorTemplateXML) {
        this.indicatorTemplateXML = indicatorTemplateXML;
    }

    public IndicatorTemplateHandler getIndicatorTemplateHandler() {
        return indicatorTemplateHandler;
    }

    private void setIndicatorTemplateHandler(IndicatorTemplateHandler indicatorTemplateHandler) {
        this.indicatorTemplateHandler = indicatorTemplateHandler;
    }

    public DrilldownQueryHandler getDrilldownQueryHandler() {
        return drilldownQueryHandler;
    }

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
