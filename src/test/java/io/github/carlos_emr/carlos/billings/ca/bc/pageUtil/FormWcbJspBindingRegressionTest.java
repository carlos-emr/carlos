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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import io.github.carlos_emr.carlos.entities.WCB;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.SafeEncode;

/**
 * formwcb.jsp, the BC WCB form page: what every field shows (issue #4864).
 *
 * <p>The page's fields lost their bean bindings when it left Struts 1's form tags, so a new form opened blank
 * and a saved form showed nothing. The table below lists every field of the page's WCBForm form. Each test
 * renders the JSP source of a field's controls with a {@link WCBForm}, evaluating the scriptlet expressions the
 * page uses, and parses the result as a browser would (jsoup). An expression of any other shape fails the test
 * rather than being skipped.</p>
 *
 * <p>The FAKE values carry quotes and markup, so the same renders show that each value comes back exactly as
 * saved and creates no element or attribute of its own. Last, each field's posted value must reach its
 * column through the save ({@link WCBAction22Action}).</p>
 */
@Tag("unit")
@Tag("billing")
@DisplayName("formwcb.jsp field bindings")
class FormWcbJspBindingRegressionTest extends CarlosUnitTestBase {

    private static final Path FORM_WCB_JSP = Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/formwcb.jsp");

    /** Text that would break out of an attribute, a textarea or a JavaScript string if written unencoded. */
    private static final String HOSTILE = "O'Brien \"></textarea><script>alert(1)</script><img src=x onerror=alert(2)>";
    private static final String HOSTILE_JS = "Ltd'); alert(3); //\"></a><img src=x onerror=alert(4)>";

    private enum Kind { TEXT, TEXTAREA, RADIO, CHECKBOX, SELECT }

    private record Field(String name, Kind kind, List<String> values) {
        String property() {
            return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static Field text(String name) {
        return new Field(name, Kind.TEXT, List.of());
    }

    private static Field textarea(String name) {
        return new Field(name, Kind.TEXTAREA, List.of());
    }

    private static Field radio(String name, String... values) {
        return new Field(name, Kind.RADIO, List.of(values));
    }

    /**
     * Every field of the WCBForm form, in page order: 31 text and hidden inputs, a checkbox, 10 radio groups,
     * 3 textareas and a select.
     */
    private static final List<Field> FIELDS = List.of(
            text("w_servicelocation"),
            new Field("formNeeded", Kind.CHECKBOX, List.of("1")),
            radio("w_reporttype", "F", "C"),
            text("w_wcbno"), text("w_empname"), text("w_opaddress"), text("w_opcity"), text("w_emparea"),
            text("w_empphone"), text("w_lname"), text("w_fname"), text("w_mname"), text("w_dob"), text("w_gender"),
            text("w_phn"), text("w_area"), text("w_phone"), text("w_address"), text("w_city"), text("w_postal"),
            radio("w_rphysician", "Y", "N"),
            radio("w_duration", "1", "2", "9"),
            text("w_ftreatment"),
            textarea("w_problem"),
            text("w_servicedate"), text("w_doi"), text("w_diagnosis"), text("w_feeitem"), text("w_extrafeeitem"),
            text("w_icd9"), text("w_bp"),
            new Field("w_side", Kind.SELECT, List.of("L", "R", "B")),
            text("w_noi"),
            radio("w_work", "Y", "N"),
            text("w_workdate"),
            textarea("w_clinicinfo"),
            radio("w_capability", "Y", "N"),
            textarea("w_capreason"),
            radio("w_estimate", "0", "1", "2", "3", "9"),
            radio("w_rehab", "Y", "N"),
            radio("w_rehabtype", "C", "O"),
            radio("w_wcbadvisor", "Y", "N"),
            text("w_estimatedate"),
            radio("w_tofollow", "Y", "N"),
            text("w_payeeno"), text("w_pracno"));

    /** The form's own parameters and buttons, which are not WCBForm fields. */
    private static final Set<String> PAGE_PARAMETERS = Set.of("method", "billRegion", "billForm", "appointment_no",
            "demographic_name", "demographic_no", "providerview", "user_no", "status", "apptProvider_no", "bNewForm",
            "fromBilling", "wcbFormId", "appointment_date", "doValidate", "save", "saveAndClose", "saveandbill");

    /** The entity's date columns, which WCBForm shows as yyyy-MM-dd. */
    private static final Set<String> DATE_FIELDS = Set.of("w_dob", "w_servicedate", "w_doi", "w_workdate",
            "w_estimatedate");

    private static final String FORM_ACTION = "action=\"${pageContext.request.contextPath}/billing/CA/BC/formwcb\"";
    private static final Pattern EXPRESSION = Pattern.compile("<%=(.*?)%>", Pattern.DOTALL);
    private static final Pattern SCRIPTLET = Pattern.compile("<%(?![=@!-]).*?%>", Pattern.DOTALL);
    private static final Pattern PLACEHOLDER = Pattern.compile("\u0001(\\d+)\u0002");
    private static final Pattern CONTROL = Pattern.compile("<(input|textarea|select)\\b[^>]*>");
    private static final Pattern NAME = Pattern.compile("\\bname=\"([^\"]*)\"");
    private static final Pattern VALUE = Pattern.compile("\\bvalue=\"([^\"]*)\"");
    private static final Pattern ID = Pattern.compile("\\bid=\"([^\"]*)\"");
    private static final Pattern BOUND_VALUE =
            Pattern.compile("SafeEncode\\.(forHtmlAttribute|forHtmlContent)\\(bound\\.(get\\w+)\\(\\)\\)");
    private static final Pattern BOUND_CHECKED = Pattern.compile("checkedIf\\(bound\\.(get\\w+)\\(\\), \"([^\"]*)\"\\)");
    private static final String FORM_NEEDED_CHECKED =
            "checkedIf(\"true\".equals(bound.getFormNeeded()) ? \"1\" : bound.getFormNeeded(), \"1\")";
    private static final String CHECKED_IF = """
            private static String checkedIf(String actual, String value) {
                    return value.equals(actual) ? " checked=\\"checked\\"" : "";
                }""";
    private static final String SIDE_OPTION = """
            <option value="${carlos:forHtmlAttribute(injury.sidetype)}"<c:if test="${injury.sidetype eq wcbSide}"> \
            selected="selected"</c:if>>
                                        ${carlos:forHtmlContent(injury.sidedesc)}
                                </option>""";
    private static final Pattern JS_STRING = Pattern.compile("'((?:[^'\\\\]|\\\\.)*)'");

    private static String jsp;
    /** formwcb.jsp with each {@code <%= %>} expression replaced by a numbered placeholder. */
    private static String masked;
    private static final List<String> EXPRESSIONS = new ArrayList<>();
    /** The JSP source of each named control in the WCBForm form, by name, in page order. */
    private static final Map<String, List<String>> CONTROLS = new LinkedHashMap<>();

    @BeforeAll
    static void readPage() throws IOException {
        jsp = Files.readString(FORM_WCB_JSP, StandardCharsets.UTF_8);
        Matcher expression = EXPRESSION.matcher(jsp);
        StringBuilder out = new StringBuilder();
        while (expression.find()) {
            expression.appendReplacement(out, "\u0001" + EXPRESSIONS.size() + "\u0002");
            EXPRESSIONS.add(expression.group(1).trim());
        }
        expression.appendTail(out);
        masked = out.toString();

        int action = masked.indexOf(FORM_ACTION);
        String form = masked.substring(masked.lastIndexOf("<form ", action), masked.indexOf("</form>", action));
        Matcher control = CONTROL.matcher(form);
        while (control.find()) {
            String tag = control.group(1);
            int end = tag.equals("input") ? control.end() : form.indexOf("</" + tag + ">", control.end()) + tag.length() + 3;
            Matcher name = NAME.matcher(control.group());
            if (name.find()) {
                CONTROLS.computeIfAbsent(name.group(1), k -> new ArrayList<>()).add(form.substring(control.start(), end));
            }
        }
    }

    static Stream<Field> fields() {
        return FIELDS.stream();
    }

    @Test
    @DisplayName("should list every field of the WCBForm form in the binding table, with its radio values")
    void shouldListEveryFormField_inTheBindingTable() {
        Set<String> pageFields = new LinkedHashSet<>(CONTROLS.keySet());
        pageFields.removeAll(PAGE_PARAMETERS);

        assertThat(pageFields).containsExactlyElementsOf(FIELDS.stream().map(Field::name).toList());
        for (Field field : FIELDS) {
            if (field.kind() == Kind.RADIO) {
                assertThat(CONTROLS.get(field.name()).stream().map(FormWcbJspBindingRegressionTest::valueAttribute))
                        .as("the %s radio values", field.name())
                        .containsExactlyElementsOf(field.values());
            }
        }
        assertThat(jsp).as("the checked-state helper the radio and checkbox bindings use").contains(CHECKED_IF);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fields")
    @DisplayName("should label each of the field's controls")
    void shouldLabelEachControl_ofEachField(Field field) {
        for (String control : CONTROLS.get(field.name())) {
            if (control.contains("type=\"hidden\"")) {
                continue; // nothing to label: the user never sees it
            }
            Matcher id = ID.matcher(control);
            assertThat(id.find()).as("a %s control has an id", field.name()).isTrue();
            assertThat(jsp).as("the label for %s", id.group(1)).contains("<label for=\"" + id.group(1) + "\"");
        }
    }

    @Test
    @DisplayName("should give each control of the form its own id")
    void shouldGiveEachControlItsOwnId_inTheForm() {
        List<String> ids = CONTROLS.values().stream().flatMap(List::stream).map(ID::matcher)
                .filter(Matcher::find).map(id -> id.group(1)).toList();
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("should name the form WCBForm, which the page's lookups and scripts address it by")
    void shouldNameTheForm_forItsLookupsAndScripts() {
        assertThat(jsp).contains("<form name=\"WCBForm\" " + FORM_ACTION)
                .contains("popFeeItemList('WCBForm','w_feeitem')")
                .contains("window.WCBForm.w_workdate.value = '';");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fields")
    @DisplayName("should bind each field to its own WCBForm property")
    void shouldBindEachField_toItsOwnProperty(Field field) throws Exception {
        Set<String> getters = new LinkedHashSet<>();
        for (String control : CONTROLS.get(field.name())) {
            Matcher placeholder = PLACEHOLDER.matcher(control);
            while (placeholder.find()) {
                String expression = EXPRESSIONS.get(Integer.parseInt(placeholder.group(1)));
                Matcher getter = Pattern.compile("bound\\.(get\\w+)\\(\\)").matcher(expression);
                while (getter.find()) {
                    getters.add(getter.group(1));
                }
            }
        }
        if (field.kind() == Kind.SELECT) {
            assertThat(CONTROLS.get(field.name()).get(0)).contains(SIDE_OPTION);
            assertThat(jsp).contains("pageContext.setAttribute(\"wcbSide\", bound.getW_side());");
            getters.add("getW_side");
        }

        assertThat(getters).as("the WCBForm getters %s's controls read", field.name())
                .containsExactly("get" + field.property());
        assertThat(WCBForm.class.getMethod("get" + field.property()).getReturnType()).isEqualTo(String.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fields")
    @DisplayName("should show a new form's value in each field, inert")
    void shouldShowNewFormValue_inEachField(Field field) throws Exception {
        Method setter = WCBForm.class.getMethod("set" + field.property(), String.class);
        switch (field.kind()) {
            case TEXT, TEXTAREA -> {
                WCBForm form = new WCBForm();
                String value = "FAKE " + field.name() + " " + HOSTILE;
                setter.invoke(form, value);
                assertShows(field, form, value);
            }
            case RADIO -> {
                for (String value : field.values()) {
                    WCBForm form = new WCBForm();
                    setter.invoke(form, value);
                    assertThat(checkedValues(field, form)).as("%s=%s", field.name(), value).containsExactly(value);
                }
                WCBForm blank = new WCBForm();
                setter.invoke(blank, "");
                assertThat(checkedValues(field, blank)).as("%s blank", field.name()).isEmpty();
                // A new form starts from WCBForm's own defaults (Physician's First Report, regular physician, ...).
                String preset = property(new WCBForm(), "get" + field.property());
                assertThat(checkedValues(field, new WCBForm())).as("%s default", field.name())
                        .isEqualTo(preset.isEmpty() ? List.of() : List.of(preset));
            }
            case CHECKBOX -> {
                // A new form starts at "1"; a saved one comes back as "true" or "false" (WCBForm.setWCBForms).
                for (String value : List.of("1", "true", "0", "false", "")) {
                    WCBForm form = new WCBForm();
                    setter.invoke(form, value);
                    assertThat(checkedValues(field, form)).as("formNeeded=%s", value)
                            .isEqualTo("1".equals(value) || "true".equals(value) ? List.of("1") : List.of());
                }
            }
            case SELECT -> {
                for (String value : field.values()) {
                    WCBForm form = new WCBForm();
                    setter.invoke(form, value);
                    assertThat(selectedSide(form)).as("w_side=%s", value).containsExactly(value);
                }
                WCBForm blank = new WCBForm();
                setter.invoke(blank, "");
                assertThat(selectedSide(blank)).as("w_side blank").isEmpty();
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fields")
    @DisplayName("should show a saved form's value in each field, inert")
    void shouldShowSavedValue_inEachField(Field field) throws Exception {
        Method setter = setterOf(field.property());
        Class<?> type = setter.getParameterTypes()[0];
        if (DATE_FIELDS.contains(field.name())) {
            assertThat(type).isEqualTo(Date.class);
            WCB saved = new WCB();
            setter.invoke(saved, Date.from(LocalDate.of(2026, Month.MARCH, 14).atStartOfDay(ZoneId.systemDefault()).toInstant()));
            assertShows(field, open(saved), "2026-03-14");
            return;
        }
        switch (field.kind()) {
            case TEXT, TEXTAREA -> {
                WCB saved = new WCB();
                String value = "FAKE saved " + field.name() + " " + HOSTILE;
                setter.invoke(saved, value);
                assertShows(field, open(saved), value);
            }
            case RADIO -> {
                for (String value : field.values()) {
                    WCB saved = new WCB();
                    setter.invoke(saved, type == int.class ? (Object) Integer.parseInt(value) : value);
                    assertThat(checkedValues(field, open(saved))).as("saved %s=%s", field.name(), value)
                            .containsExactly(value);
                }
                // An unchosen duration is stored as NULL, which reads back as 0: no radio checked.
                assertThat(checkedValues(field, open(new WCB()))).as("saved %s unset", field.name()).isEmpty();
            }
            case CHECKBOX -> {
                for (int value : new int[] {1, 0}) {
                    WCB saved = new WCB();
                    setter.invoke(saved, value);
                    assertThat(checkedValues(field, open(saved))).as("saved formNeeded=%d", value)
                            .isEqualTo(value == 1 ? List.of("1") : List.of());
                }
            }
            case SELECT -> {
                for (String value : field.values()) {
                    WCB saved = new WCB();
                    setter.invoke(saved, value);
                    assertThat(selectedSide(open(saved))).as("saved w_side=%s", value).containsExactly(value);
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fields")
    @DisplayName("should save what each field posts to its column")
    void shouldSavePostedValue_forEachField(Field field) throws Exception {
        createAndRegisterMock(SecurityInfoManager.class);
        Method posted = WCBAction22Action.class.getMethod("set" + field.property(), String.class);
        assertThat(posted.isAnnotationPresent(StrutsParameter.class))
                .as("the save takes %s from the request", field.name()).isTrue();
        Method column = WCB.class.getMethod("get" + field.property());

        List<String> values = switch (field.kind()) {
            case RADIO, SELECT -> field.values();
            case CHECKBOX -> List.of("1");
            default -> List.of(DATE_FIELDS.contains(field.name()) ? "2026-03-14" : "FAKE " + field.name() + " " + HOSTILE);
        };
        try (MockedStatic<ServletActionContext> _ = mockStatic(ServletActionContext.class)) {
            for (String value : values) {
                WCBAction22Action save = new WCBAction22Action();
                save.setDemographic_no("42");
                if (!field.name().equals("w_duration")) {
                    save.setW_duration("1");
                }
                posted.invoke(save, value);

                Object saved = column.invoke(save.getWCB());

                Object expected = saved instanceof Date ? value : column.getReturnType() == int.class
                        ? (Object) Integer.parseInt(value) : value;
                Object actual = saved instanceof Date date
                        ? Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                        : saved;
                assertThat(actual).as("%s=%s saved", field.name(), value).isEqualTo(expected);
            }
            if (field.kind() == Kind.CHECKBOX) {
                // A clear box posts nothing.
                WCBAction22Action save = new WCBAction22Action();
                save.setDemographic_no("42");
                save.setW_duration("1");
                assertThat(column.invoke(save.getWCB())).as("formNeeded clear, saved").isEqualTo(0);
            }
        }
    }

    @Test
    @DisplayName("should encode the page's other written values: errors, the bill's hidden fields and the provider name")
    void shouldEncodeOtherWrittenValues_inTheForm() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("error", "FAKE error " + HOSTILE);
        values.put("form.getW_lname() + \" \" + form.getW_fname()", "FAKE-Lname " + HOSTILE);
        values.put("form.getDemographic()", "42" + HOSTILE);
        values.put("form.getProviderNo()", "97001" + HOSTILE);
        values.put("(String) session.getAttribute(\"user\")", "97002" + HOSTILE);
        values.put("form.getW_pracname()", "FAKE-Provider " + HOSTILE);

        Document errors = render(between("<% for (String error : actionErrors) { %>", "<% } %>"), values);
        assertInert(errors);
        assertThat(errors.select("li").text()).isEqualTo(values.get("error"));

        Document hidden = render(between("<input type=\"hidden\" name=\"demographic_name\"",
                "<input type=\"hidden\" name=\"bNewForm\""), values);
        assertInert(hidden);
        assertThat(hidden.select("input[name=demographic_name]").val()).isEqualTo(values.get(
                "form.getW_lname() + \" \" + form.getW_fname()"));
        assertThat(hidden.select("input[name=demographic_no]").val()).isEqualTo(values.get("form.getDemographic()"));
        assertThat(hidden.select("input[name=providerview]").val()).isEqualTo(values.get("form.getProviderNo()"));
        assertThat(hidden.select("input[name=apptProvider_no]").val()).isEqualTo(values.get("form.getProviderNo()"));
        assertThat(hidden.select("input[name=user_no]").val()).isEqualTo(values.get("(String) session.getAttribute(\"user\")"));

        int pracName = masked.indexOf("name=\"w_pracno\"");
        Document provider = render(masked.substring(masked.indexOf("(", pracName), masked.indexOf(")\n", pracName) + 1),
                values);
        assertInert(provider);
        assertThat(provider.body().text()).isEqualTo("(" + values.get("form.getW_pracname()") + ")");
    }

    @Test
    @DisplayName("should keep earlier claims' and employers' values inert in their pick lists and script calls")
    void shouldKeepPickListValuesInert_inLinksAndCalls() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("claim.getClaimNumber()", "FAKE-claim " + HOSTILE_JS);
        for (String part : List.of("w_empname", "w_opaddress", "w_opcity", "w_emparea", "w_empphone")) {
            values.put("emp." + part, "FAKE " + part + " " + HOSTILE_JS);
        }
        for (String part : List.of("w_icd9", "w_bp", "w_side", "w_noi", "w_doi")) {
            values.put("claim." + part, "FAKE " + part + " " + HOSTILE_JS);
        }

        Document claims = render("<table>" + between("<% for (int i = 0; i < claims.size(); i++) {", "<%} %>")
                + "</table>", values);
        assertInert(claims);
        Element claim = claims.selectFirst("a[onclick^=setClaim]");
        assertThat(claim).isNotNull();
        assertThat(jsArguments(claim.attr("onclick"), "setClaim", "hidepic('claimLayer');return false;"))
                .containsExactly(values.get("claim.getClaimNumber()"), values.get("emp.w_empname"),
                        values.get("emp.w_opaddress"), values.get("emp.w_opcity"), values.get("emp.w_emparea"),
                        values.get("emp.w_empphone"), values.get("claim.w_icd9"), values.get("claim.w_bp"),
                        values.get("claim.w_side"), values.get("claim.w_noi"), values.get("claim.w_doi"));
        assertThat(claim.text()).isEqualTo(values.get("claim.getClaimNumber()"));

        Document employers = render("<table>" + between("<% for (int i = 0; i < emps.size(); i++) {", "<%}%>")
                + "</table>", values);
        assertInert(employers);
        Element employer = employers.selectFirst("a[onclick^=setEmployer]");
        assertThat(employer).isNotNull();
        assertThat(jsArguments(employer.attr("onclick"), "setEmployer", "hidepic('employerLayer');return false;"))
                .containsExactly(values.get("emp.w_empname"), values.get("emp.w_opaddress"), values.get("emp.w_opcity"),
                        values.get("emp.w_emparea"), values.get("emp.w_empphone"));
        assertThat(employer.text()).isEqualTo(values.get("emp.w_empname"));
        assertThat(employers.select("td.wcblayerTitle").eachText()).containsExactly(values.get("emp.w_empname"),
                values.get("emp.w_opaddress"), values.get("emp.w_opcity"),
                values.get("emp.w_emparea") + "-" + values.get("emp.w_empphone"));
    }

    // ---- rendering ----

    /** Checks the field's control shows {@code expected} and that the value made no element or attribute of its own. */
    private static void assertShows(Field field, WCBForm form, String expected) {
        Document page = renderField(field, form);
        assertInert(page);
        Element control = page.selectFirst("[name=" + field.name() + "]");
        assertThat(control).as("the %s control", field.name()).isNotNull();
        assertThat(control.val()).as("the %s value", field.name()).isEqualTo(expected);

        Element blank = renderField(field, new WCBForm()).selectFirst("[name=" + field.name() + "]");
        assertThat(control.attributes().asList().stream().map(Attribute::getKey).toList())
                .as("the %s attributes", field.name())
                .isEqualTo(blank.attributes().asList().stream().map(Attribute::getKey).toList());
    }

    private static void assertInert(Document page) {
        assertThat(page.select("script, img")).as("elements made by a value").isEmpty();
        assertThat(page.getAllElements().stream().flatMap(e -> e.attributes().asList().stream())
                .map(a -> a.getKey().toLowerCase()).filter(k -> k.startsWith("on") && !k.equals("onclick")
                        && !k.equals("onkeyup") && !k.equals("onkeypress")))
                .as("event attributes made by a value").isEmpty();
    }

    private static List<String> checkedValues(Field field, WCBForm form) {
        return renderField(field, form).select("input[name=" + field.name() + "]").stream()
                .filter(input -> input.hasAttr("checked")).map(input -> input.attr("value")).toList();
    }

    /** Renders the page's Side select over three FAKE locations, evaluating the option's EL and its c:if. */
    private static List<String> selectedSide(WCBForm form) {
        String select = CONTROLS.get("w_side").get(0);
        String loop = "<c:forEach var=\"injury\" items=\"${injuryLocations}\">";
        String template = select.substring(select.indexOf(loop) + loop.length(), select.indexOf("</c:forEach>"));
        String options = Stream.of("L", "R", "B").map(side -> template
                .replace("${carlos:forHtmlAttribute(injury.sidetype)}", SafeEncode.forHtmlAttribute(side))
                .replace("<c:if test=\"${injury.sidetype eq wcbSide}\"> selected=\"selected\"</c:if>",
                        side.equals(form.getW_side()) ? " selected=\"selected\"" : "")
                .replace("${carlos:forHtmlContent(injury.sidedesc)}", SafeEncode.forHtmlContent("FAKE side " + side)))
                .collect(Collectors.joining());
        assertThat(options).as("EL or JSTL in the Side option this test does not know").doesNotContain("${", "<c:");
        String rendered = select.substring(0, select.indexOf('>') + 1) + options + "</select>";
        return Jsoup.parseBodyFragment(rendered).select("option").stream()
                .filter(option -> option.hasAttr("selected")).map(option -> option.attr("value")).toList();
    }

    private static Document renderField(Field field, WCBForm bound) {
        return Jsoup.parseBodyFragment(substitute(String.join("\n", CONTROLS.get(field.name())),
                expression -> evaluateBound(expression, bound)));
    }

    /** Evaluates the expression shapes the page's field bindings use, against {@code bound}. */
    private static String evaluateBound(String expression, WCBForm bound) {
        Matcher value = BOUND_VALUE.matcher(expression);
        if (value.matches()) {
            String property = property(bound, value.group(2));
            return value.group(1).equals("forHtmlAttribute") ? SafeEncode.forHtmlAttribute(property)
                    : SafeEncode.forHtmlContent(property);
        }
        Matcher checked = BOUND_CHECKED.matcher(expression);
        if (checked.matches()) {
            return checkedIf(property(bound, checked.group(1)), checked.group(2));
        }
        if (expression.equals(FORM_NEEDED_CHECKED)) {
            String formNeeded = bound.getFormNeeded();
            return checkedIf("true".equals(formNeeded) ? "1" : formNeeded, "1");
        }
        throw new AssertionError("a field binding this test does not know: <%= " + expression + " %>");
    }

    /** The page's checked-state helper; {@link #shouldListEveryFormField_inTheBindingTable} pins its source. */
    private static String checkedIf(String actual, String value) {
        return value.equals(actual) ? " checked=\"checked\"" : "";
    }

    /** Renders a stretch of the page with its scriptlets dropped and each encoded expression taken from {@code values}. */
    private static Document render(String source, Map<String, String> values) {
        String html = substitute(SCRIPTLET.matcher(source).replaceAll(""), expression -> {
            Matcher encoded = Pattern.compile("SafeEncode\\.(forHtmlAttribute|forHtmlContent|forJavaScriptAttribute)\\((.*)\\)")
                    .matcher(expression);
            if (!encoded.matches() || !values.containsKey(encoded.group(2))) {
                throw new AssertionError("an unencoded or unknown written value: <%= " + expression + " %>");
            }
            String value = values.get(encoded.group(2));
            return switch (encoded.group(1)) {
                case "forHtmlAttribute" -> SafeEncode.forHtmlAttribute(value);
                case "forHtmlContent" -> SafeEncode.forHtmlContent(value);
                default -> SafeEncode.forJavaScriptAttribute(value);
            };
        });
        return Jsoup.parseBodyFragment(html);
    }

    private static String substitute(String source, Function<String, String> evaluate) {
        Matcher placeholder = PLACEHOLDER.matcher(source);
        StringBuilder out = new StringBuilder();
        while (placeholder.find()) {
            String expression = EXPRESSIONS.get(Integer.parseInt(placeholder.group(1)));
            placeholder.appendReplacement(out, Matcher.quoteReplacement(evaluate.apply(expression)));
        }
        placeholder.appendTail(out);
        return out.toString();
    }

    /** The masked page from {@code start} up to (not including) the first {@code end} after it. */
    private static String between(String start, String end) {
        int from = masked.indexOf(start);
        assertThat(from).as("formwcb.jsp contains %s", start).isNotNegative();
        int to = masked.indexOf(end, from + start.length());
        assertThat(to).as("formwcb.jsp contains %s after %s", end, start).isPositive();
        return masked.substring(from, to);
    }

    /**
     * The decoded string arguments of a {@code name('…','…');suffix} handler. Fails if anything but string literals
     * sits between the parentheses, which is what an unescaped quote in a value would cause.
     */
    private static List<String> jsArguments(String handler, String name, String suffix) {
        assertThat(handler).startsWith(name + "(").endsWith(");" + suffix);
        String arguments = handler.substring(name.length() + 1, handler.length() - suffix.length() - 2);
        List<String> decoded = new ArrayList<>();
        Matcher literal = JS_STRING.matcher(arguments);
        StringBuilder rest = new StringBuilder();
        while (literal.find()) {
            decoded.add(decodeJs(literal.group(1)));
            literal.appendReplacement(rest, "");
        }
        literal.appendTail(rest);
        assertThat(rest.toString().replace(",", "")).as("code outside the string arguments").isEmpty();
        return decoded;
    }

    private static String decodeJs(String literal) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < literal.length(); i++) {
            char c = literal.charAt(i);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char next = literal.charAt(++i);
            switch (next) {
                case 'x' -> {
                    out.append((char) Integer.parseInt(literal.substring(i + 1, i + 3), 16));
                    i += 2;
                }
                case 'u' -> {
                    out.append((char) Integer.parseInt(literal.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                default -> out.append(next);
            }
        }
        return out.toString();
    }

    private static String valueAttribute(String control) {
        Matcher value = VALUE.matcher(control);
        return value.find() ? value.group(1) : null;
    }

    private static String property(WCBForm form, String getter) {
        try {
            return (String) WCBForm.class.getMethod(getter).invoke(form);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("WCBForm has no " + getter + "()", e);
        }
    }

    private static Method setterOf(String property) {
        return Stream.of(WCB.class.getMethods()).filter(m -> m.getName().equals("set" + property)
                && m.getParameterCount() == 1).findFirst()
                .orElseThrow(() -> new AssertionError("WCB has no set" + property + "(…)"));
    }

    private static WCBForm open(WCB saved) {
        WCBForm form = new WCBForm();
        form.setWCBForms(saved);
        return form;
    }
}
