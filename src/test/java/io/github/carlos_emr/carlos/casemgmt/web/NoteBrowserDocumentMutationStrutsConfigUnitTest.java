/**
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.casemgmt.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.apache.struts2.ActionContext;
import org.apache.struts2.ActionInvocation;
import org.apache.struts2.ActionProxy;
import org.apache.struts2.config.entities.ActionConfig;
import org.apache.struts2.config.entities.ResultConfig;
import org.apache.struts2.dispatcher.Dispatcher;
import org.apache.struts2.inject.Container;
import org.apache.struts2.ognl.ThreadAllowlist;
import org.apache.struts2.result.ServletActionRedirectResult;
import org.apache.struts2.util.ValueStack;
import org.apache.struts2.util.ValueStackFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("noteBrowser document mutation Struts config")
@Tag("unit")
@Tag("caseManagement")
class NoteBrowserDocumentMutationStrutsConfigUnitTest {

    private static final String CLINICAL_CONFIG =
            "src/main/webapp/WEB-INF/classes/struts-clinical.xml";

    private static final String[] MUTATION_ACTIONS = {
            "casemgmt/NoteBrowserDocumentDelete",
            "casemgmt/NoteBrowserDocumentUndelete",
            "casemgmt/NoteBrowserDocumentRefile"
    };

    @Test
    @DisplayName("should redirect mutations to fixed note browser action")
    void shouldRedirectMutations_toFixedNoteBrowserAction() throws Exception {
        Document doc = parseClinicalConfig();

        for (String actionName : MUTATION_ACTIONS) {
            Element success = result(findAction(doc, actionName), "success");

            assertThat(success.getAttribute("type")).isEqualTo("redirectAction");
            assertThat(param(success, "actionName")).isEqualTo("casemgmt/ViewNoteBrowser");
            assertThat(param(success, "demographic_no")).isEqualTo("${demographicNo}");
            assertThat(param(success, "chartContext")).isEqualTo("${chartContext}");
            assertThat(param(success, "view")).isEqualTo("${view}");
            assertThat(param(success, "viewstatus")).isEqualTo("${viewstatus}");
            assertThat(param(success, "sortorder")).isEqualTo("${sortorder}");
            assertThat(param(success, "errorMessage")).isEqualTo("${mutationErrorMessage}");
            assertThat(param(success, "suppressEmptyParameters")).isEqualTo("true");
        }
    }

    /** Executes the production XML result using Struts' real OGNL stack and redirect renderer. */
    static void executeRedirect(String operation, AbstractNoteBrowserDocumentMutation2Action action,
                                MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        NoteBrowserDocumentMutationStrutsConfigUnitTest config = new NoteBrowserDocumentMutationStrutsConfigUnitTest();
        String actionName = "casemgmt/NoteBrowserDocument" + operation;
        Element success = config.result(config.findAction(config.parseClinicalConfig(), actionName), "success");
        Map<String, String> params = new LinkedHashMap<>();
        NodeList entries = success.getElementsByTagName("param");
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            params.put(entry.getAttribute("name"), entry.getTextContent().trim());
        }
        ActionContext previous = ActionContext.getContext();
        Dispatcher previousDispatcher = Dispatcher.getInstance();
        Dispatcher dispatcher = new Dispatcher(request.getServletContext(), Map.of("config", "struts-default.xml"));
        dispatcher.init();
        try {
            Container container = dispatcher.getContainer();
            // The normal action invocation registers this hierarchy before evaluating its result.
            container.getInstance(ThreadAllowlist.class).allowClassHierarchy(action.getClass());
            ValueStack stack = container.getInstance(ValueStackFactory.class).createValueStack();
            stack.push(action);
            ActionContext context = stack.getActionContext().withContainer(container).withValueStack(stack)
                    .withServletRequest(request).withServletResponse(response).bind();
            ResultConfig resultConfig = new ResultConfig.Builder("success", ServletActionRedirectResult.class.getName())
                    .addParams(params).build();
            ActionConfig actionConfig = new ActionConfig.Builder("clinical", actionName, action.getClass().getName())
                    .addResultConfig(resultConfig).build();
            ActionProxy proxy = mock(ActionProxy.class);
            when(proxy.getConfig()).thenReturn(actionConfig);
            ActionInvocation invocation = mock(ActionInvocation.class);
            when(invocation.getProxy()).thenReturn(proxy);
            when(invocation.getInvocationContext()).thenReturn(context);
            when(invocation.getStack()).thenReturn(stack);
            when(invocation.getResultCode()).thenReturn("success");
            ServletActionRedirectResult redirect = new ServletActionRedirectResult("/", params.get("actionName"), null);
            container.inject(redirect);
            redirect.setSuppressEmptyParameters(Boolean.parseBoolean(params.get("suppressEmptyParameters")));
            redirect.execute(invocation);
        } finally {
            dispatcher.getContainer().getInstance(ThreadAllowlist.class).clearAllowlist();
            dispatcher.cleanup();
            if (previousDispatcher == null) Dispatcher.clearInstance();
            else Dispatcher.setInstance(previousDispatcher);
            if (previous == null) ActionContext.clear();
            else previous.bind();
        }
    }

    @Test
    @DisplayName("should reject non-POST methods with HTTP 405")
    void shouldRejectGetRequests_withHttpHeaderResult() throws Exception {
        Document doc = parseClinicalConfig();

        for (String actionName : MUTATION_ACTIONS) {
            Element methodNotAllowed = result(findAction(doc, actionName), "methodNotAllowed");

            assertThat(methodNotAllowed.getAttribute("type")).isEqualTo("httpheader");
            assertThat(param(methodNotAllowed, "status")).isEqualTo("405");
        }
    }

    private Element findAction(Document doc, String actionName) {
        NodeList actions = doc.getElementsByTagName("action");
        for (int i = 0; i < actions.getLength(); i++) {
            Element action = (Element) actions.item(i);
            if (actionName.equals(action.getAttribute("name"))) {
                return action;
            }
        }
        throw new AssertionError("Missing Struts action " + actionName);
    }

    private Element result(Element action, String resultName) {
        NodeList results = action.getElementsByTagName("result");
        for (int i = 0; i < results.getLength(); i++) {
            Element result = (Element) results.item(i);
            if (resultName.equals(result.getAttribute("name"))) {
                return result;
            }
        }
        throw new AssertionError("Missing " + resultName + " result for " + action.getAttribute("name"));
    }

    private String param(Element result, String name) {
        NodeList params = result.getElementsByTagName("param");
        for (int i = 0; i < params.getLength(); i++) {
            Element param = (Element) params.item(i);
            if (name.equals(param.getAttribute("name"))) {
                return param.getTextContent().trim();
            }
        }
        return null;
    }

    private Document parseClinicalConfig() throws Exception {
        DocumentBuilder db = newHardenedDocumentBuilder();
        try (InputStream in = new FileInputStream(CLINICAL_CONFIG)) {
            return db.parse(in);
        }
    }

    private DocumentBuilder newHardenedDocumentBuilder() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        dbf.setNamespaceAware(false);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        db.setEntityResolver((publicId, systemId) ->
                new InputSource(new java.io.StringReader("")));
        return db;
    }
}
