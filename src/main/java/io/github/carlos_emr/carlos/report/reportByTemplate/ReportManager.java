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
//This is the main utility object that:
//   -Makes all database transactions (except one in GenerateReport2Action)
//   -Loads/saves all the reports
//   -Saves/loads parameters
//   -Parses/saves all XML

package io.github.carlos_emr.carlos.report.reportByTemplate;

import java.util.ArrayList;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jdom2.Document;
import org.jdom2.Content;
import org.jdom2.JDOMException;
import org.jdom2.Element;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import org.jdom2.output.XMLOutputter;
import org.jdom2.output.Format;
import io.github.carlos_emr.carlos.commn.dao.ReportTemplatesDao;
import io.github.carlos_emr.carlos.commn.model.ReportTemplates;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.util.UtilXML;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Created on December 27, 2006, 10:54 AM
 *
 * @author apavel (Paul)
 */
public class ReportManager {

    private ReportTemplatesDao dao = SpringUtils.getBean(ReportTemplatesDao.class);

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Creates a new instance of reportManager
     */
    public ReportManager() {
    }

    public ArrayList<ReportObjectGeneric> getReportTemplatesNoParam() {
        ReportTemplatesDao dao = SpringUtils.getBean(ReportTemplatesDao.class);
        ArrayList<ReportObjectGeneric> reports = new ArrayList<ReportObjectGeneric>();
        for (ReportTemplates r : dao.findActive()) {
            ReportObjectGeneric curReport = new ReportObjectGeneric(r.getId().toString(), r.getTemplateTitle(), r.getTemplateDescription());
            reports.add(curReport);
        }
        return reports;
    }

    //gets the ReportObject without the parameters (don't always need parameters, no need to parse XML)
    public ReportObject getReportTemplateNoParam(String templateid) {
        ReportTemplatesDao dao = SpringUtils.getBean(ReportTemplatesDao.class);
        ReportTemplates rt = dao.find(ConversionUtils.fromIntString(templateid));

        ReportObjectGeneric curReport = new ReportObjectGeneric();
        if (rt == null) {
            return curReport;
        }

        String templatetitle = rt.getTemplateTitle();
        String templatedescription = rt.getTemplateDescription();
        curReport.setTemplateId(templateid);
        curReport.setTitle(templatetitle);
        curReport.setDescription(templatedescription);
        curReport.setSequence(rt.isSequence());
        curReport.setUuid(rt.getUuid());
        return curReport;
    }

    @SuppressWarnings("unchecked")
    public ReportObject getReportTemplate(String templateid) {
        ReportTemplatesDao dao = SpringUtils.getBean(ReportTemplatesDao.class);
        ReportTemplates rt = dao.find(ConversionUtils.fromIntString(templateid));
        if (rt == null) {
            return new ReportObjectGeneric(templateid, "Template Not Found");
        }

        try {
            String templatetitle = rt.getTemplateTitle();
            String templatedescription = rt.getTemplateDescription();
            String type = rt.getType() == null ? "" : rt.getType();
            String paramXML = rt.getTemplateXml();
            ArrayList<Parameter> params = new ArrayList<Parameter>();
            boolean sequence = rt.isSequence();

            if (!paramXML.equals("")) {
                Document doc = parseTemplateXml(paramXML);
                Element root = doc.getRootElement();

                List<Element> paramsXml = root.getChildren("param");
                for (int i = 0; i < paramsXml.size(); i++) {
                    Element param = paramsXml.get(i);
                    String paramid = param.getAttributeValue("id");
                    if (paramid == null) return new ReportObjectGeneric(templateid, "Error: Param id not found");
                    String paramtype = param.getAttributeValue("type");
                    if (paramtype == null)
                        return new ReportObjectGeneric(templateid, "Error: Param type not found on param '" + paramid + "'");
                    String paramdescription = param.getAttributeValue("description");
                    if (paramdescription == null)
                        return new ReportObjectGeneric(templateid, "Error: Param description not found on param '" + paramid + "'");
                    List<Element> choicesXml = param.getChildren("choice");
                    ArrayList<Choice> choices = new ArrayList<Choice>();
                    String priority = param.getAttributeValue("priority") != null ? param.getAttributeValue("priority") : "query";

                    String paramquery = param.getChildText("param-query"); //if retrieving choices from the DB
                    if (paramquery != null && !ReportTemplateSqlValidator.isAllowedStatement(paramquery)) {
                        // Opening a template runs its param-queries, so a stored statement that is not a
                        // plain SELECT (saved before save-time validation existed, or written straight to
                        // the table) is refused here rather than executed.
                        MiscUtils.getLogger().warn("Refused non-SELECT param-query in report template {}",
                                LogSafe.sanitize(templateid));
                        return new ReportObjectGeneric(templateid, "Error: The <param-query> of parameter '"
                                + paramid + "' is not an allowed SELECT statement");
                    }
                    if (paramquery != null) {
                        // Use parameterized query method even though no parameters are bound
                        // This eliminates the use of the unsafe runNativeQuery method
                        Map<String, Object> emptyParams = new HashMap<>();
                        for (Object[] o : dao.runParameterizedNativeQuery(paramquery, emptyParams)) {
                            String choiceid = "", choicetext = "";
                            if (o.length >= 1) {
                                choiceid = String.valueOf(o[0]);
                            }

                            if (o.length >= 2) {
                                choicetext = String.valueOf(o[1]);
                            }

                            if (choicetext == null) {
                                choicetext = choiceid;
                            }
                            Choice curchoice = new Choice(choiceid, choicetext);
                            choices.add(curchoice);
                        }
                    }
                    List<Choice> tmp = new ArrayList<Choice>();
                    for (int i2 = 0; i2 < choicesXml.size(); i2++) {
                        Element choice = choicesXml.get(i2);
                        String choiceid = choice.getAttributeValue("id");
                        String choicetext = choice.getTextTrim();
                        if (choiceid == null) choiceid = choicetext;
                        Choice curchoice = new Choice(choiceid, choicetext);
                        tmp.add(curchoice);
                    }

                    if ("choice".equals(priority)) {
                        choices.addAll(0, tmp);
                    } else {
                        choices.addAll(tmp);
                    }

                    Parameter curparam = new Parameter(paramid, paramtype, paramdescription, choices);
                    params.add(curparam);
                }
            }
            ReportObjectGeneric curreport = new ReportObjectGeneric(templateid, templatetitle, templatedescription, type, params);
            return curreport;

        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            return new ReportObjectGeneric(templateid, "Parameter Parsing Exception: check the configuration file. Cause: " + e.getCause());
        }
    }

    public String getSQL(String templateId) {
        ReportTemplatesDao dao = SpringUtils.getBean(ReportTemplatesDao.class);
        ReportTemplates rt = dao.find(ConversionUtils.fromIntString(templateId));
        if (rt == null) {
            return "";
        }
        return rt.getTemplateSql();
    }

    public String getTemplateXml(String templateid) {
        ReportTemplatesDao dao = SpringUtils.getBean(ReportTemplatesDao.class);
        ReportTemplates rt = dao.find(ConversionUtils.fromIntString(templateid));
        if (rt == null) {
            return "";
        }
        return rt.getTemplateXml();
    }

    public Document readXml(String xml) throws Exception {
        Document doc = parseTemplateXml(xml);
        if (doc.getRootElement().getName().equals("report")) {
            Element newRoot = new Element("report-list");
            Element oldRoot = doc.detachRootElement();
            newRoot.setContent(oldRoot);
            doc.removeContent();
            doc.setRootElement(newRoot);
        }
        return doc;
    }

    private static final java.util.regex.Pattern EMPTY_PARAMETER = java.util.regex.Pattern.compile(
            "<param(?=[\\s/])(?:[^<>\"']|\"[^\"]*\"|'[^']*')*/\\s*>");

    /** Restores empty parameter metadata while retaining the established legacy SQL escaping policy. */
    private static Document parseTemplateXml(String xml) throws JDOMException, IOException {
        Document doc = XmlUtils.createSecureSAXBuilder().build(new StringReader(UtilXML.escapeXML(xml)));
        Element root = doc.getRootElement();
        List<Element> reports = "report".equals(root.getName()) ? List.of(root)
                : root.getChildren().stream().filter(element -> "report".equals(element.getName())).toList();
        for (Element report : reports) {
            for (int index = 0; index < report.getContentSize(); index++) {
                Content content = report.getContent(index);
                if (content.getCType() != Content.CType.Text) {
                    continue;
                }
                String text = content.getValue();
                if (!text.contains("<param")) {
                    continue;
                }
                // Only report-level metadata is repaired; query text keeps its legacy interpretation.
                Element wrapper = new Element("report", report.getNamespace());
                for (var namespace : report.getNamespacesInScope()) {
                    wrapper.addNamespaceDeclaration(namespace);
                }
                String wrapperXml = new XMLOutputter(Format.getRawFormat().setExpandEmptyElements(true))
                        .outputString(wrapper);
                String closingTag = "</" + wrapper.getQualifiedName() + ">";
                // Recover only complete empty parameter metadata. Other report text may
                // legitimately contain literal or unmatched <param spellings.
                var matcher = EMPTY_PARAMETER.matcher(text);
                List<Content> restored = new ArrayList<>();
                int cursor = 0;
                while (matcher.find()) {
                    String fragmentXml = wrapperXml.replace(closingTag,
                            matcher.group().replace("&", "&amp;") + closingTag);
                    Element parameter;
                    try {
                        Document fragment = XmlUtils.createSecureSAXBuilder().build(new StringReader(fragmentXml));
                        parameter = fragment.getRootElement().getChildren().getFirst();
                    } catch (JDOMException malformedLiteral) {
                        continue;
                    }
                    if (parameter.getAttributeValue("id") == null || parameter.getAttributeValue("type") == null
                            || parameter.getAttributeValue("description") == null) {
                        continue;
                    }
                    if (matcher.start() > cursor) {
                        restored.add(new org.jdom2.Text(text.substring(cursor, matcher.start())));
                    }
                    restored.add(parameter.detach());
                    cursor = matcher.end();
                }
                if (cursor > 0) {
                    if (cursor < text.length()) {
                        restored.add(new org.jdom2.Text(text.substring(cursor)));
                    }
                    report.removeContent(index);
                    report.addContent(index, restored);
                    index += restored.size() - 1;
                }
            }
        }
        return doc;
    }

    //returns any error messages
    //templateId = null if adding a new template
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @SuppressWarnings("unchecked")
    public String addUpdateTemplate(String uuid, String templateId, Document templateXML, LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_report", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_report)");
        }
        try {
            Element rootElement = templateXML.getRootElement();
            List<Element> reports = rootElement.getChildren();
            // Refuse anything but SELECT before any <report> is stored (a document may carry
            // several, and each is persisted inside the loop below): the <query> runs when the
            // report is generated and every <param-query> runs as soon as the template is opened.
            for (Element report : reports) {
                String sqlRefusal = ReportTemplateSqlValidator.validateReport(report);
                if (sqlRefusal != null) {
                    return sqlRefusal;
                }
            }
            for (int i = 0; i < reports.size(); i++) {
                Element report = reports.get(i);
                //reading title
                String templateTitle = report.getAttributeValue("title");
                if (templateTitle == null) return "Error: Attribute 'title' missing in <report> tag";
                //reading description
                String templateDescription = report.getAttributeValue("description");
                if (templateDescription == null) return "Error: Attribute 'description' missing in <report> tag";
                //reading type
                String type = report.getChildTextTrim("type");
                if (type == null) {
                    type = "";
                }
                //reading sql
                String querysql = report.getChildText("query");
                if (type.equalsIgnoreCase(ReportFactory.SQL_TYPE) && (querysql == null || querysql.length() == 0))
                    return "Error: The sql query is missing in <report> tag";
                //reading active switch
                String active = report.getAttributeValue("active");

                boolean sequence;
                try {
                    sequence = Boolean.valueOf(report.getAttributeValue("sequence"));
                } catch (Exception e) {
                    sequence = false;
                }

                int activeint;
                try {
                    activeint = Integer.parseInt(active);
                } catch (Exception e) {
                    activeint = 1;
                }

                //processing XML for sql storage
                XMLOutputter templateout = new XMLOutputter();
                String templateXMLstr = templateout.outputString(report).trim();
                templateXMLstr = UtilXML.unescapeXML(templateXMLstr);

                ReportTemplates r = null;
                if (uuid != null && !uuid.trim().isEmpty()) {
                    r = dao.findByUuid(uuid);
                } else if (templateId != null) {
                    r = dao.find(Integer.parseInt(templateId));
                }

                if (r == null) {
                    r = new ReportTemplates();
                    r.setTemplateTitle(templateTitle);
                    r.setTemplateDescription(templateDescription);
                    r.setTemplateSql(querysql);
                    r.setTemplateXml(templateXMLstr);
                    r.setActive(activeint);
                    r.setType(type);
                    r.setSequence(sequence);
                    if (uuid != null) {
                        r.setUuid(uuid);
                    } else {
                        r.setUuid(UUID.randomUUID().toString());
                    }
                    dao.persist(r);
                    LogAction.addLogSynchronous(loggedInInfo, "ReportManager.addUpdateTemplate", "id=" + r.getId());
                } else {
                    r.setTemplateTitle(templateTitle);
                    r.setTemplateDescription(templateDescription);
                    r.setTemplateSql(querysql);
                    r.setTemplateXml(templateXMLstr);
                    r.setActive(activeint);
                    r.setType(type);
                    r.setSequence(sequence);
                    dao.merge(r);
                    LogAction.addLogSynchronous(loggedInInfo, "ReportManager.addUpdateTemplate", "id=" + r.getId());
                }

            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            return "Error parsing file: " + e.getCause();
        }
        return "Saved Successfully";
    }

    /**
     * Deletes one template.
     *
     * @param templateid numeric template id
     * @param loggedInInfo the acting user, who must hold {@code _report} write
     * @return {@code ""} on success, otherwise an {@code "Error:"} message
     * @throws SecurityException if the user lacks {@code _report} write
     */
    public String deleteTemplate(String templateid, LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_report", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_report)");
        }
        int id = ConversionUtils.fromIntString(templateid);
        ReportTemplates template = id > 0 ? dao.find(id) : null;
        if (template == null) {
            return "Error: Template not found";
        }
        dao.remove(template.getId());
        LogAction.addLogSynchronous(loggedInInfo, "ReportManager.deleteTemplate", "id=" + template.getId());
        return "";
    }

    public String addTemplate(String uuid, String templateXML, LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_report", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_report)");
        }
        try {
            Document templateXMLdoc = readXml(templateXML);
            return addUpdateTemplate(uuid, null, templateXMLdoc, loggedInInfo);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            return "Error parsing file: " + e.getCause();
        }
    }

    public String updateTemplate(String uuid, String templateId, String templateXML, LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_report", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_report)");
        }
        try {
            Document templateXMLdoc = readXml(templateXML);
            return addUpdateTemplate(uuid, templateId, templateXMLdoc, loggedInInfo);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            return "Error: Error parsing file: " + e.getCause();
        }
    }

}
