/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@DisplayName("Encounter issue panel regressions")
@Tag("unit")
@Tag("fast")
@Tag("encounter")
class EncounterIssuePanelRegressionUnitTest extends CarlosUnitTestBase {

    private static final Path STRUTS_ENCOUNTER =
            resolveProjectPath(Path.of("src/main/webapp/WEB-INF/classes/struts-encounter.xml"));
    private static final Path LEFT_NAVBAR_JSP = resolveProjectPath(
            Path.of("src/main/webapp/WEB-INF/jsp/encounter/LeftNavBarDisplay.jsp"));

    private CaseManagementManager caseManagementManager;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletRequest request;
    private EctSessionBean encounterSession;

    @BeforeEach
    void setUp() {
        caseManagementManager = mock(CaseManagementManager.class);
        registerMock(CaseManagementManager.class, caseManagementManager);
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));

        request = new MockHttpServletRequest();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);

        encounterSession = new EctSessionBean();
        encounterSession.demographicNo = "123";

        CaseManagementIssue unresolved = issue(101L, 1001L, "Unresolved issue", false);
        CaseManagementIssue resolved = issue(202L, 2002L, "Resolved issue", true);
        List<CaseManagementIssue> issues = List.of(unresolved, resolved);
        when(caseManagementManager.getIssues(123)).thenReturn(issues);
        when(caseManagementManager.filterIssues(any(), anyString(), anyList(), any()))
                .thenReturn(issues);
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("unresolved heading should be static while issue entries remain filters")
    void shouldConfigureUnresolvedHeadingAsStatic_whileEntriesRemainFilters() {
        EctDisplayIssues2Action action = spy(new EctDisplayIssues2Action());
        action.setCaseManagementManager(caseManagementManager);
        doReturn("Unresolved Issues").when(action).getText(anyString());
        NavBarDisplayDAO dao = new NavBarDisplayDAO();

        assertThat(action.getInfo(encounterSession, request, dao)).isTrue();
        assertThat(dao.hasInteractiveLeftHeading()).isFalse();
        assertThat(dao.getLeftURL()).isEmpty();
        assertThat(dao.numItems()).isEqualTo(1);
        assertThat(dao.getItem(0).getURL())
                .isEqualTo("setIssueCheckbox('101');return filter(false);");
    }

    @Test
    @DisplayName("resolved heading should be static while issue entries remain filters")
    void shouldConfigureResolvedHeadingAsStatic_whileEntriesRemainFilters() {
        EctDisplayResolvedIssues2Action action = spy(new EctDisplayResolvedIssues2Action());
        action.setCaseManagementManager(caseManagementManager);
        doReturn("Resolved Issues").when(action).getText(anyString());
        NavBarDisplayDAO dao = new NavBarDisplayDAO();

        assertThat(action.getInfo(encounterSession, request, dao)).isTrue();
        assertThat(dao.hasInteractiveLeftHeading()).isFalse();
        assertThat(dao.getLeftURL()).isEmpty();
        assertThat(dao.numItems()).isEqualTo(1);
        assertThat(dao.getItem(0).getURL())
                .isEqualTo("setIssueCheckbox('202');return filter(false);");
    }

    @Test
    @DisplayName("navbar heading should be interactive only when an action exists")
    void shouldReportInteractiveHeading_onlyWhenActionExists() {
        NavBarDisplayDAO dao = new NavBarDisplayDAO();
        assertThat(dao.hasInteractiveLeftHeading()).isFalse();

        dao.setLeftURL("doSomething();");
        assertThat(dao.hasInteractiveLeftHeading()).isTrue();

        NavBarDisplayDAO popupDao = new NavBarDisplayDAO();
        popupDao.setLeftPopup(600, 400, "details", "/details");
        assertThat(popupDao.hasInteractiveLeftHeading()).isTrue();
    }

    @Test
    @DisplayName("resolved issue commands should route only to the resolved endpoint")
    void shouldRouteResolvedIssueCommands_toResolvedEndpoint() throws Exception {
        // Trigger EctDisplayAction's static Actions map initialization.
        new EctDisplayIssues2Action();
        Map<String, String> actions = EctDisplayAction.getActions();
        assertThat(actions)
                .containsEntry("unresolvedIssues", "/encounter/displayIssues")
                .containsEntry("resolvedIssues", "/encounter/displayResolvedIssues");

        Document config = parseXml(STRUTS_ENCOUNTER);
        NodeList actionElements = config.getElementsByTagName("action");
        int resolvedResultCount = 0;
        for (int i = 0; i < actionElements.getLength(); i++) {
            Element actionElement = (Element) actionElements.item(i);
            String resolvedResult = resultPath(actionElement, "resolvedIssues");
            if (resolvedResult == null) {
                continue;
            }
            resolvedResultCount++;
            assertThat(resolvedResult).isEqualTo("/encounter/displayResolvedIssues");
            assertThat(resultPath(actionElement, "unresolvedIssues"))
                    .isEqualTo("/encounter/displayIssues");
        }
        assertThat(resolvedResultCount).isEqualTo(20);
    }

    @Test
    @DisplayName("navbar JSP should render a static heading only when the DAO reports no interactive action")
    void shouldRenderHeadingBasedOnInteractivity_inLeftNavBarJsp() throws Exception {
        String jsp = Files.readString(LEFT_NAVBAR_JSP);

        assertThat(jsp)
                .contains("if (dao.hasInteractiveLeftHeading()) {")
                .contains("<h3><carlos:encode value='<%= dao.getLeftHeading() %>' context=\"html\"/></h3>")
                .contains("popupPage(<%=leftCfg.width()%>,<%=leftCfg.height()%>,")
                .contains("<h3 onclick=\"<carlos:encode value='<%= dao.getLeftURL() + \"; return false;\" %>' "
                        + "context=\"javaScriptAttribute\"/>\">");
    }

    private String resultPath(Element action, String resultName) {
        NodeList results = action.getElementsByTagName("result");
        for (int i = 0; i < results.getLength(); i++) {
            Element result = (Element) results.item(i);
            if (resultName.equals(result.getAttribute("name"))) {
                return result.getTextContent().trim();
            }
        }
        return null;
    }

    private Document parseXml(Path configPath) throws Exception {
        DocumentBuilder db = newHardenedDocumentBuilder();
        try (InputStream in = Files.newInputStream(configPath)) {
            return db.parse(in);
        }
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty("basedir", System.getProperty("user.dir")))
                .toAbsolutePath()
                .normalize();
        for (int checkedParents = 0; current != null && checkedParents < 6; checkedParents++) {
            Path candidate = current.resolve(relativePath).normalize();
            if (Files.isRegularFile(candidate) || Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate " + relativePath + " from "
                + System.getProperty("basedir", System.getProperty("user.dir")));
    }

    private DocumentBuilder newHardenedDocumentBuilder() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        dbf.setNamespaceAware(false);
        // Defense-in-depth XML hardening — inputs are trusted local config
        // files, but pinning secure-processing + disabling external entities
        // keeps the test robust across JAXP implementations.
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        db.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        return db;
    }

    private static CaseManagementIssue issue(long id, long issueId, String description, boolean resolved) {
        Issue definition = new Issue();
        definition.setCode("custom-" + issueId);
        definition.setDescription(description);

        CaseManagementIssue issue = new CaseManagementIssue();
        issue.setId(id);
        issue.setIssue_id(issueId);
        issue.setIssue(definition);
        issue.setResolved(resolved);
        return issue;
    }
}
