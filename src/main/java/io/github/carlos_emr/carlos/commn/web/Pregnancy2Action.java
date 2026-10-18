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
package io.github.carlos_emr.carlos.commn.web;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.form.*;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.struts2.ActionSupport;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.util.LabelValueBean;

import javax.imageio.ImageIO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.List;
import java.util.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.function.Function;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import io.github.carlos_emr.carlos.encounter.pageUtil.EncounterPatientContext;

public class Pregnancy2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private EpisodeDao episodeDao = SpringUtils.getBean(EpisodeDao.class);
    private ObjectMapper objectMapper = new ObjectMapper();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private Integer demographicNo;

    // Allowlist of form class names valid for pregnancy-related AJAX form saves.
    // Prevents user-controlled input from loading arbitrary classes via FrmRecordFactory.
    private static final Set<String> ALLOWED_PREGNANCY_FORM_CLASSES =
            Set.of("BCAR", "BCAR2007", "BCAR2012", "BCAR2020");

    static String labReqVersion;

    static {
        labReqVersion = CarlosProperties.getInstance().getProperty("onare_labreqver", "07");
        if ("".equals(labReqVersion)) {
            labReqVersion = "10";
        }
    }

    private static final String CODING_SYSTEM = "SnomedCore";
    private static final Map<String, String> PREGNANCY_TYPES = Map.of(
            "72892002", "Normal", "47200007", "High Risk", "16356006", "Multiple", "34801009", "Ectopic");

    /** Only the episode lifecycle is exposed by the Pregnancy route. Legacy helpers below
     * are deliberately not dispatched: they require separate authorization reviews. */
    @Override
    public String execute() throws Exception {
        String method = request.getParameter("method");
        if (method == null || "list".equals(method)) return list();
        return switch (method) {
            case "new" -> newPregnancy();
            case "edit" -> editPregnancy();
            case "create" -> create();
            case "complete" -> complete();
            default -> reject(400, "Unknown pregnancy operation");
        };
    }

    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    public String getLatestFormIdByPregnancy() throws IOException {
        String episodeId = request.getParameter("episodeId");

        Integer formId = 0;
        if (episodeId != null) {
            try {
                formId = PregnancyFormsDao.getLatestFormIdByPregnancy(Integer.parseInt(episodeId));
            } catch (NumberFormatException e) {
                //empty
            }
        }
        ObjectNode json = objectMapper.valueToTree(new LabelValueBean("formId", String.valueOf(formId)));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().println(json);
        return null;
    }

    /** Displays a form; following or reloading the Add link never writes an episode. */
    public String newPregnancy() throws IOException {
        Integer patient = authorizePatient("r");
        if (patient == null) return NONE;
        String code = request.getParameter("code");
        if (!validType(code)) return reject(400, "Invalid pregnancy type");
        request.setAttribute("code", code);
        request.setAttribute("description", PREGNANCY_TYPES.get(code));
        request.setAttribute("startDate", LocalDate.now(ZoneId.systemDefault()).toString());
        return "form";
    }

    /** Opens only this patient's pregnancy, including its completed history. */
    public String editPregnancy() throws IOException {
        Integer patient = authorizePatient("r");
        if (patient == null) return NONE;
        Integer id = episodeId();
        if (id == null) return reject(400, "Invalid pregnancy identifier");
        Episode episode = episodeDao.find(id);
        if (!ownsPregnancy(episode, patient)) return reject(404, "Pregnancy not found");
        request.setAttribute("episode", episode);
        return "form";
    }

    /** POST-only creation, serialized with this patient's other pregnancy mutations. */
    public String create() throws IOException {
        if (!"POST".equals(request.getMethod())) return postRequired();
        Integer patient = authorizePatient("w");
        if (patient == null) return NONE;
        String code = request.getParameter("code");
        LocalDate start = calendarDate(request.getParameter("startDate"));
        String notes = request.getParameter("notes");
        if (!validType(code) || start == null || start.isAfter(LocalDate.now(ZoneId.systemDefault())) || !validNotes(notes)) {
            return invalidForm("Check the pregnancy type, start date and notes.");
        }
        Mutation result = inPatientTransaction(patient, em -> {
            if (!em.createQuery("SELECT e FROM Episode e WHERE e.demographicNo=:patient "
                            + "AND e.status='Current' AND e.codingSystem=:system AND e.code IN :codes", Episode.class)
                    .setParameter("patient", patient).setParameter("system", CODING_SYSTEM)
                    .setParameter("codes", PREGNANCY_TYPES.keySet()).setMaxResults(1).getResultList().isEmpty()) {
                return new Mutation(409, "A current pregnancy already exists.");
            }
            Episode episode = new Episode();
            episode.setDemographicNo(patient);
            episode.setCode(code);
            episode.setCodingSystem(CODING_SYSTEM);
            episode.setDescription(PREGNANCY_TYPES.get(code));
            episode.setStatus("Current");
            episode.setStartDate(asDate(start));
            episode.setNotes(notes == null ? "" : notes);
            episode.setLastUpdateUser(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo());
            em.persist(episode);
            em.flush();
            return new Mutation(200, null);
        });
        return finishMutation(result);
    }

    /** POST-only completion; invalid or stale submissions never alter managed fields. */
    public String complete() throws IOException {
        if (!"POST".equals(request.getMethod())) return postRequired();
        Integer patient = authorizePatient("w");
        if (patient == null) return NONE;
        Integer id = episodeId();
        if (id == null) return reject(400, "Invalid pregnancy identifier");
        LocalDate end = calendarDate(request.getParameter("endDate"));
        String notes = request.getParameter("notes");
        if (end == null || end.isAfter(LocalDate.now(ZoneId.systemDefault())) || !validNotes(notes)) {
            return invalidForm("Check the completion date and notes.");
        }
        Mutation result = inPatientTransaction(patient, em -> {
            Episode episode = em.find(Episode.class, id, LockModeType.PESSIMISTIC_WRITE);
            if (!ownsPregnancy(episode, patient)) return new Mutation(404, "Pregnancy not found");
            if (!"Current".equals(episode.getStatus())) return new Mutation(409, "This pregnancy is already complete.");
            if (episode.getStartDate() == null || end.isBefore(LocalDate.parse(episode.getStartDateStr()))) {
                return new Mutation(400, "Completion date cannot precede the start date.");
            }
            episode.setStatus("Complete");
            episode.setEndDate(asDate(end));
            episode.setNotes(notes == null ? "" : notes);
            episode.setLastUpdateUser(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo());
            em.flush();
            return new Mutation(200, null);
        });
        return finishMutation(result);
    }

    public String list() throws IOException {
        Integer patient = authorizePatient("r");
        if (patient == null) return NONE;
        List<Episode> episodes = new ArrayList<>(episodeDao.findCurrentByCodeTypeAndCodes(
                patient, CODING_SYSTEM, PREGNANCY_TYPES.keySet()));
        episodes.addAll(episodeDao.findCompletedByCodeTypeAndCodes(patient, CODING_SYSTEM, PREGNANCY_TYPES.keySet()));
        request.setAttribute("episodes", episodes);
        return "list";
    }

    private Integer authorizePatient(String right) throws IOException {
        int patient;
        try {
            patient = Integer.parseInt(EncounterPatientContext.requireDemographicNo(request));
        } catch (IllegalArgumentException invalid) {
            reject(400, "Invalid patient identifier");
            return null;
        }
        LoggedInInfo info = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(info, "_eChart", "r", patient)
                || !securityInfoManager.isAllowedAccessToPatientRecord(info, patient)) {
            throw new SecurityException("missing required sec object (_eChart)");
        }
        if (!securityInfoManager.hasPrivilege(info, "_pregnancy", right, patient)) {
            throw new SecurityException("missing required sec object (_pregnancy)");
        }
        demographicNo = patient;
        request.setAttribute("demographicNo", Integer.toString(patient));
        request.setAttribute("canWrite", securityInfoManager.hasPrivilege(info, "_pregnancy", "w", patient));
        return patient;
    }

    private boolean validType(String code) {
        String system = request.getParameter("codetype");
        return code != null && PREGNANCY_TYPES.containsKey(code) && (system == null || CODING_SYSTEM.equals(system));
    }

    private static boolean ownsPregnancy(Episode episode, int patient) {
        return episode != null && episode.getDemographicNo() == patient
                && CODING_SYSTEM.equals(episode.getCodingSystem()) && PREGNANCY_TYPES.containsKey(episode.getCode())
                && ("Current".equals(episode.getStatus()) || "Complete".equals(episode.getStatus()));
    }

    private Integer episodeId() {
        String value = request.getParameter("episodeId");
        try {
            if (value != null && value.matches("[0-9]{1,10}")) {
                int id = Integer.parseInt(value);
                if (id > 0) return id;
            }
        } catch (NumberFormatException ignored) {
            // A number outside the identifier range is still invalid.
        }
        return null;
    }

    private static LocalDate calendarDate(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return null;
        try {
            LocalDate date = LocalDate.parse(value);
            return date.getYear() >= 1000 ? date : null;
        }
        catch (DateTimeParseException invalid) { return null; }
    }

    private static Date asDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static boolean validNotes(String notes) { return notes == null || notes.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 65535; }

    private record Mutation(int status, String error) { }

    private Mutation inPatientTransaction(int patient, Function<EntityManager, Mutation> mutation) {
        return new TransactionTemplate(SpringUtils.getBean(PlatformTransactionManager.class)).execute(status -> {
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(SpringUtils.getBean(EntityManagerFactory.class));
            if (em == null) throw new IllegalStateException("Pregnancy transaction has no entity manager");
            // Lock an existing row even for the first pregnancy: locking an empty Episode
            // query cannot serialize concurrent creates. The same lock orders completion.
            // Loading Demographic invokes legacy property getters that can dirty null fields.
            // A scalar, parameterized row lock establishes ownership/existence without hydration.
            if (em.createNativeQuery("SELECT demographic_no FROM demographic WHERE demographic_no=:patient FOR UPDATE")
                    .setParameter("patient", patient).getResultList().isEmpty()) {
                return new Mutation(404, "Patient not found");
            }
            return mutation.apply(em);
        });
    }

    private String finishMutation(Mutation result) throws IOException {
        if (result.status() != 200) return reject(result.status(), result.error());
        return "saved";
    }

    /** Returns the authorized patient identifier used by the fixed post-save Struts redirect. */
    public Integer getDemographicNo() { return demographicNo; }

    private String invalidForm(String message) throws IOException { return reject(400, message); }
    private String postRequired() throws IOException {
        response.setHeader("Allow", "POST");
        return reject(405, "POST required");
    }
    private String reject(int status, String message) throws IOException {
        response.sendError(status, message);
        return NONE;
    }

    public String createGBSLabReq() throws SQLException {
        String demoNoParam = request.getParameter("demographicNo");
        if (demoNoParam == null || !demoNoParam.matches("\\d+")) {
            MiscUtils.getLogger().warn("Invalid non-numeric demographicNo in createGBSLabReq: {}", LogSafe.sanitize(demoNoParam)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            return null;
        }
        Integer demographicNo = Integer.parseInt(demoNoParam);
        String penicillin = request.getParameter("penicillin");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        if (labReqVersion.equals("07")) {
            FrmLabReq07Record lr = new FrmLabReq07Record();
            Properties p = lr.getFormRecord(loggedInInfo, demographicNo, 0);
            p = lr.getFormCustRecord(loggedInInfo, loggedInInfo.getCurrentFacility(), p, providerNo);
            if (penicillin != null && penicillin.equals("checked")) {
                p.setProperty("o_otherTests1", "Vaginal Anal GBS w/ sensitivities");
                p.setProperty("o_otherTests2", "pt allergic to penicillin");
            } else {
                p.setProperty("o_otherTests1", "Vaginal Anal GBS");
            }
            request.getSession().setAttribute("labReq07" + demographicNo, p); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep - demographicNo validated as numeric at method entry
        } else {
            FrmLabReq10Record lr = new FrmLabReq10Record();
            Properties p = lr.getFormRecord(loggedInInfo, demographicNo, 0);
            p = lr.getFormCustRecord(p, providerNo);
            if (penicillin != null && penicillin.equals("checked")) {
                p.setProperty("o_otherTests1", "Vaginal Anal GBS w/ sensitivities");
                p.setProperty("o_otherTests2", "pt allergic to penicillin");
            } else {
                p.setProperty("o_otherTests1", "Vaginal Anal GBS");
            }
            request.getSession().setAttribute("labReq10" + demographicNo, p); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep - demographicNo validated as numeric at method entry
        }

        return null;
    }

    public String createMCVLabReq() throws SQLException {
        String demoNoParam = request.getParameter("demographicNo");
        if (demoNoParam == null || !demoNoParam.matches("\\d+")) {
            MiscUtils.getLogger().warn("Invalid non-numeric demographicNo in createMCVLabReq: {}", LogSafe.sanitize(demoNoParam)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            return null;
        }
        Integer demographicNo = Integer.parseInt(demoNoParam);
        String ferritin = request.getParameter("ferritin");
        String hbElectrophoresis = request.getParameter("hb_electrophoresis");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        if (labReqVersion.equals("07")) {
            FrmLabReq07Record lr = new FrmLabReq07Record();
            Properties p = lr.getFormRecord(loggedInInfo, demographicNo, 0);
            p = lr.getFormCustRecord(loggedInInfo, loggedInInfo.getCurrentFacility(), p, providerNo);

            if (ferritin != null && ferritin.equals("checked")) {
                p.setProperty("b_ferritin", "checked=\"checked\"");
            }
            if (hbElectrophoresis != null && hbElectrophoresis.equals("checked")) {
                p.setProperty("o_otherTests1", "Hb Electrophoresis");
            }
            request.getSession().setAttribute("labReq07" + demographicNo, p); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep - demographicNo validated as numeric at method entry

        } else {
            FrmLabReq10Record lr = new FrmLabReq10Record();
            Properties p = lr.getFormRecord(loggedInInfo, demographicNo, 0);
            p = lr.getFormCustRecord(p, providerNo);

            if (ferritin != null && ferritin.equals("checked")) {
                p.setProperty("b_ferritin", "checked=\"checked\"");
            }
            if (hbElectrophoresis != null && hbElectrophoresis.equals("checked")) {
                p.setProperty("o_otherTests1", "Hb Electrophoresis");
            }
            request.getSession().setAttribute("labReq10" + demographicNo, p); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep - demographicNo validated as numeric at method entry

        }


        return null;
    }

    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    public String getAllergies() throws IOException {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_form", "r", null)) {
            throw new SecurityException("missing required sec object (_form)");
        }
        Integer demographicNo = Integer.parseInt(request.getParameter("demographicNo"));
        AllergyDao allergyDao = SpringUtils.getBean(AllergyDao.class);
        List<Allergy> allergies = allergyDao.findActiveAllergies(demographicNo);
        StringBuilder output = new StringBuilder();
        for (Allergy allergy : allergies) {
            output.append(allergy.getDescription());
            if (allergy.getReaction() != null) {
                output.append(":" + allergy.getReaction());
            }
            output.append("\n");
        }

        ObjectNode json = objectMapper.valueToTree(new LabelValueBean("allergies", output.toString().trim()));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().println(json); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- JSON API response with application/json content-type
        return null;
    }

    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    public String getMeds() throws IOException {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_form", "r", null)) {
            throw new SecurityException("missing required sec object (_form)");
        }
        Integer demographicNo = Integer.parseInt(request.getParameter("demographicNo"));
        DrugDao drugDao = SpringUtils.getBean(DrugDao.class);
        List<Drug> drugs = drugDao.findByDemographicId(demographicNo, false);
        StringBuilder output = new StringBuilder();
        for (Drug drug : drugs) {
            if (drug.isArchived() || drug.isDeleted() || drug.isDiscontinued() || drug.isExpired()) {
                continue;
            }
            if (drug.getBrandName() != null && drug.getBrandName().length() > 0) {
                if (output.length() > 0)
                    output.append(",");
                output.append(drug.getBrandName());
            } else if (drug.getCustomName() != null && drug.getCustomName().length() > 0) {
                if (output.length() > 0)
                    output.append(",");
                output.append(drug.getCustomName());
            } else {
                if (output.length() > 0)
                    output.append(",");
                output.append(drug.getSpecial());
            }
        }

        ObjectNode json = objectMapper.valueToTree(new LabelValueBean("meds", output.toString().trim()));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().println(json); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- JSON API response with application/json content-type
        return null;
    }

    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    public String saveFormAjax() throws IOException {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_form", "w", null)) {
            throw new SecurityException("missing required sec object (_form write)");
        }
        int newID = 0;
        FrmRecord rec = null;
        ObjectNode jsonObj = null;

        String formClass = request.getParameter("form_class");
        if (formClass == null || !ALLOWED_PREGNANCY_FORM_CLASSES.contains(formClass)) {
            MiscUtils.getLogger().warn("Invalid form class requested in pregnancy saveFormAjax: {}", LogSafe.sanitize(formClass)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            jsonObj = objectMapper.valueToTree(new LabelValueBean("result", "error"));
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().print(jsonObj.toString());
            return null;
        }

        try {
            FrmRecordFactory recorder = new FrmRecordFactory();
            rec = recorder.factory(formClass);
            Properties props = new Properties();

            boolean bMulPage = request.getParameter("c_lastVisited") != null ? true : false;
            String name;

            if (bMulPage) {
                String curPageNum = request.getParameter("c_lastVisited");
                String commonField = request.getParameter("commonField") != null ? request
                        .getParameter("commonField") : "&'";
                curPageNum = curPageNum.length() > 3 ? ("" + curPageNum.charAt(0)) : curPageNum;

                //copy an old record
                props = rec.getFormRecord(LoggedInInfo.getLoggedInInfoFromSession(request), Integer.parseInt(request.getParameter("demographic_no")), Integer
                        .parseInt(request.getParameter("formId")));

                //empty the current page
                Properties currentParam = new Properties();
                for (Enumeration varEnum = request.getParameterNames(); varEnum.hasMoreElements(); ) {
                    name = (String) varEnum.nextElement();
                    currentParam.setProperty(name, "");
                }
                for (Enumeration varEnum = props.propertyNames(); varEnum.hasMoreElements(); ) {
                    name = (String) varEnum.nextElement();
                    // kick off the current page elements, commonField on the current page
                    if (name.startsWith(curPageNum + "_")
                            || (name.startsWith(commonField) && currentParam.containsKey(name))) {
                        props.remove(name);
                    }
                }
            }

            //update the current record
            for (Enumeration varEnum = request.getParameterNames(); varEnum.hasMoreElements(); ) {
                name = (String) varEnum.nextElement();
                props.setProperty(name, request.getParameter(name));
            }

            props.setProperty("provider_no", (String) request.getSession().getAttribute("user")); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): reads authenticated provider from own session (set by Login2Action post-auth)
            newID = rec.saveFormRecord(props);
            String ip = request.getRemoteAddr();
            LogAction.addLog(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), LogConst.ADD, formClass,
                    "" + newID, ip, request.getParameter("demographic_no"));


            jsonObj = objectMapper.valueToTree(new LabelValueBean("result", String.valueOf(newID)));


        } catch (Exception ex) {
            MiscUtils.getLogger().error("error", ex);
            jsonObj = objectMapper.valueToTree(new LabelValueBean("result", "error"));

        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print(jsonObj.toString()); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- JSON API response with application/json content-type

        return null;
    }

    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    public String getMeasurementsAjax() throws IOException {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_form", "r", null)) {
            throw new SecurityException("missing required sec object (_form)");
        }
        String demographicNo = request.getParameter("demographicNo");
        String type = request.getParameter("type");

        MeasurementDao md = SpringUtils.getBean(MeasurementDao.class);
        List<Measurement> m = md.findByType(Integer.parseInt(demographicNo), type);

        ArrayNode json = objectMapper.valueToTree(m);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print(json.toString()); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- JSON API response with application/json content-type

        return null;
    }

    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    public String saveMeasurementAjax() throws IOException {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_form", "w", null)) {
            throw new SecurityException("missing required sec object (_form write)");
        }
        String demographicNo = request.getParameter("demographicNo");
        String type = request.getParameter("type");
        String value = request.getParameter("value");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        MeasurementDao md = SpringUtils.getBean(MeasurementDao.class);

        Measurement m = new Measurement();
        m.setAppointmentNo(0);
        m.setComments("");
        m.setDataField(value);
        m.setDateObserved(new Date());
        m.setDemographicId(Integer.parseInt(demographicNo));
        m.setMeasuringInstruction("");
        m.setProviderNo(providerNo);
        m.setType(type);

        md.persist(m);

        ObjectNode jsonObj = objectMapper.valueToTree(new LabelValueBean("result", "success"));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print(jsonObj);

        return null;
    }

    /*
     * Tests, and the LOINC code, I could find for it
     *
     * Hemoglobin (718-7)
     * HIV (public health - manually entered for now) (X50045) - hiv serology (GDML:HIV)
     * MCV (787-2)
     * ABO - GDML (4490) test name is "Blood Group" - includes RH. Textual though
     * RH (10331-7)
     * PAP Smear (GDML:GY04)
     * Antibody screen (8061-4)?? (GDML:4482)
     * Gonnorhea
     * Rubella (25514-1)?
     * Chlamydia - urine GDML:CHLD
     * HbsAG (5196-1) GDML:HB1
     * Urine C&S
     * VDRL (public health) (X100666)
     * Sickle Cell
     */
    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    public String getAR1Labs() throws IOException {
        String demographicNo = request.getParameter("demographicNo");

        ArrayNode json = objectMapper.createArrayNode();

        MeasurementDao md = SpringUtils.getBean(MeasurementDao.class);
        List<Measurement> m = md.findByType(Integer.parseInt(demographicNo), "HEMO");
        if (m.size() > 0) {
            json.add(objectMapper.valueToTree(m.get(0)));
        }
        m = md.findByType(Integer.parseInt(demographicNo), "MCV");
        if (m.size() > 0) {
            json.add(objectMapper.valueToTree(m.get(0)));
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print(json.toString());

        return null;
    }

    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    public String loadEformByName() {
        EFormDao eformDao = (EFormDao) SpringUtils.getBean(EFormDao.class);
        //Prenatal Screening (IPS) Credit Valley
        //Prenatal Screening - North York
        String demographicNo = request.getParameter("demographicNo");
        String name = request.getParameter("name");
        String apptNo = request.getParameter("appointmentNo");
        if (apptNo == null) {
            apptNo = "0";
        }
        EForm eform = eformDao.findByName(name);

        String url;
        if (eform != null) {
            url = "/eform/efmformadd_data?fid=" + eform.getId() + "&demographic_no=" + demographicNo + "&appointment=" + apptNo;
        } else {
            url = "/pregnancy/eform_not_found.jsp";
        }
        try {
            response.sendRedirect(url);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return NONE;
    }

    /*
     * Hb
Urine C&S
Repeat antibody screen
1 hour 50 gm glucose screen
     */
    public String createGCTLabReq() throws SQLException {
        String demoNoParam = request.getParameter("demographicNo");
        if (demoNoParam == null || !demoNoParam.matches("\\d+")) {
            MiscUtils.getLogger().warn("Invalid non-numeric demographicNo in createGCTLabReq: {}", LogSafe.sanitize(demoNoParam)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            return null;
        }
        Integer demographicNo = Integer.parseInt(demoNoParam);
        String hb = request.getParameter("hb");
        String urine = request.getParameter("urine");
        String antibody = request.getParameter("antibody");
        String glucose = request.getParameter("glucose");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();


        if (labReqVersion.equals("07")) {
            FrmLabReq07Record lr = new FrmLabReq07Record();
            Properties p = lr.getFormRecord(loggedInInfo, demographicNo, 0);
            p = lr.getFormCustRecord(loggedInInfo, loggedInInfo.getCurrentFacility(), p, providerNo);

            if (hb != null && hb.equals("checked")) {
                p.setProperty("h_cbc", "checked=\"checked\"");
            }
            if (urine != null && urine.equals("checked")) {
                p.setProperty("m_urine", "checked=\"checked\"");
            }
            if (antibody != null && antibody.equals("checked")) {
                p.setProperty("i_repeatPrenatalAntibodies", "checked=\"checked\"");
            }
            if (glucose != null && glucose.equals("checked")) {
                p.setProperty("o_otherTests1", "1 Hr 50gm GLUCOSE Screen");
            }

            request.getSession().setAttribute("labReq07" + demographicNo, p); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep - demographicNo validated as numeric at method entry
        } else {
            FrmLabReq10Record lr = new FrmLabReq10Record();
            Properties p = lr.getFormRecord(loggedInInfo, demographicNo, 0);
            p = lr.getFormCustRecord(p, providerNo);

            if (hb != null && hb.equals("checked")) {
                p.setProperty("h_cbc", "checked=\"checked\"");
            }
            if (urine != null && urine.equals("checked")) {
                p.setProperty("m_urine", "checked=\"checked\"");
            }
            if (antibody != null && antibody.equals("checked")) {
                p.setProperty("i_repeatPrenatalAntibodies", "checked=\"checked\"");
            }
            if (glucose != null && glucose.equals("checked")) {
                p.setProperty("o_otherTests1", "1 Hr 50gm GLUCOSE Screen");
            }

            request.getSession().setAttribute("labReq10" + demographicNo, p); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep - demographicNo validated as numeric at method entry
        }
        return null;
    }

    public String createGTTLabReq() throws SQLException {
        String demoNoParam = request.getParameter("demographicNo");
        if (demoNoParam == null || !demoNoParam.matches("\\d+")) {
            MiscUtils.getLogger().warn("Invalid non-numeric demographicNo in createGTTLabReq: {}", LogSafe.sanitize(demoNoParam)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            return null;
        }
        Integer demographicNo = Integer.parseInt(demoNoParam);
        String glucose = request.getParameter("glucose");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        if (labReqVersion.equals("07")) {
            FrmLabReq07Record lr = new FrmLabReq07Record();
            Properties p = lr.getFormRecord(loggedInInfo, demographicNo, 0);
            p = lr.getFormCustRecord(loggedInInfo, loggedInInfo.getCurrentFacility(), p, providerNo);

            if (glucose != null && glucose.equals("checked")) {
                p.setProperty("o_otherTests1", "2 Hr 75gm GLUCOSE Screen");
            }
            request.getSession().setAttribute("labReq07" + demographicNo, p); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep - demographicNo validated as numeric at method entry
        } else {
            FrmLabReq10Record lr = new FrmLabReq10Record();
            Properties p = lr.getFormRecord(loggedInInfo, demographicNo, 0);
            p = lr.getFormCustRecord(p, providerNo);

            if (glucose != null && glucose.equals("checked")) {
                p.setProperty("o_otherTests1", "2 Hr 75gm GLUCOSE Screen");
            }
            request.getSession().setAttribute("labReq10" + demographicNo, p); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep - demographicNo validated as numeric at method entry
        }
        return null;
    }

    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    public String getEformsByGroupAjax() throws IOException {
        String name = request.getParameter("name");
        EFormDao eformDao = (EFormDao) SpringUtils.getBean(EFormDao.class);
        EFormGroupDao eformGroupDao = (EFormGroupDao) SpringUtils.getBean(EFormGroupDao.class);
        List<LabelValueBean> results = new ArrayList<LabelValueBean>();

        List<EFormGroup> items = eformGroupDao.getByGroupName(name);
        for (EFormGroup item : items) {
            if (item.getFormId() > 0) {
                EForm eform = eformDao.find(item.getFormId());
                if (eform != null) {
                    LabelValueBean bean = new LabelValueBean(eform.getFormName(), String.valueOf(item.getFormId()));
                    results.add(bean);
                }
            }
        }

        ArrayNode jsonObj = objectMapper.valueToTree(results);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print(jsonObj);

        return null;
    }

    public static List<LabelValueBean> getEformsByGroup(String name) {
        EFormDao eformDao = (EFormDao) SpringUtils.getBean(EFormDao.class);
        EFormGroupDao eformGroupDao = (EFormGroupDao) SpringUtils.getBean(EFormGroupDao.class);
        List<LabelValueBean> results = new ArrayList<LabelValueBean>();

        List<EFormGroup> items = eformGroupDao.getByGroupName(name);
        for (EFormGroup item : items) {
            if (item.getFormId() > 0) {
                EForm eform = eformDao.find(item.getFormId());
                if (eform != null) {
                    LabelValueBean bean = new LabelValueBean(eform.getFormName(), String.valueOf(item.getFormId()));
                    results.add(bean);
                }
            }
        }

        return results;
    }


    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    public String getFundalImage() throws IOException {

        List<Point2D.Double> points = new ArrayList<Point2D.Double>();
        int index = 1;
        while (true) {
            String ga = request.getParameter("ga" + index);
            String cm = request.getParameter("fh" + index);
            if (ga == null || cm == null)
                break;

            String weeks = null;
            String offset = null;
            try {
                weeks = ga.substring(0, ga.indexOf("w"));
                offset = "0";
                if (ga.indexOf("+") != -1)
                    offset = ga.substring(ga.indexOf("+") + 1);
            } catch (Exception e) {
                MiscUtils.getLogger().warn("Error", e);
            }

            if (weeks != null && offset != null) {
                try {
                    double gaa = Integer.parseInt(weeks) + (Integer.parseInt(offset) / 7);
                    double cma = Double.parseDouble(cm);
                    Point2D.Double p = new Point2D.Double(gaa, cma);
                    points.add(p);
                } catch (NumberFormatException e) {
                    MiscUtils.getLogger().warn("Error", e);
                }
            }
            index++;
        }

        File file = PathValidationUtils.resolveTrustedPath(new File(request.getSession().getServletContext().getRealPath("/") + "WEB-INF/classes/oscar/form/prop/fundal_graph.png"));
        BufferedImage bufferedImage = ImageIO.read(file);
        Graphics2D g = bufferedImage.createGraphics();
        g.setColor(Color.black);
        g.setStroke(new BasicStroke(2));

        int xStart = 23;  //each week is 10.25pixels
        int yStart = 318; //each height is 10.6pixels
        int width = 7;
        int height = 7;
        int startingWeek = 20;
        int startingHeight = 15;

        Point2D.Double lastPoint = null;

        for (Point2D.Double p : points) {
            if (p.x < startingWeek || p.x > 42)
                continue;
            if (p.y < startingHeight || p.y > 45)
                continue;

            Rectangle r = new Rectangle((int) (xStart + (10.25 * (p.x - startingWeek)) - (width / 2)), (int) (yStart - (10.60 * (p.y - startingHeight)) - (height / 2)), width, height);
            g.fill(r);

            if (lastPoint != null) {
                g.drawLine((int) (xStart + (10.25 * (lastPoint.x - startingWeek))), (int) (yStart - (10.60 * (lastPoint.y - startingHeight))),
                        (int) (xStart + (10.25 * (p.x - startingWeek))), (int) (yStart - (10.60 * (p.y - startingHeight))));
            }
            lastPoint = p;
        }

        g.dispose();
        response.setContentType("image/png");
        OutputStream outputStream = response.getOutputStream();
        ImageIO.write(bufferedImage, "png", outputStream);
        outputStream.close();

        return null;
    }

    public String recordPrint() {
        String printLocation = request.getParameter("printLocation");
        String printMethod = request.getParameter("printMethod");
        String resourceName = request.getParameter("resourceName");
        String resourceId = request.getParameter("resourceId");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        PrintResourceLog item = new PrintResourceLog();
        item.setDateTime(new Date());
        item.setExternalLocation(printLocation);
        item.setExternalMethod(printMethod);
        item.setProviderNo(providerNo);
        item.setResourceId(resourceId);
        item.setResourceName(resourceName);

        PrintResourceLogDao dao = SpringUtils.getBean(PrintResourceLogDao.class);
        dao.persist(item);

        return null;
    }

    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    public String getPrintData() throws IOException {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_form", "r", null)) {
            throw new SecurityException("missing required sec object (_form)");
        }
        PrintResourceLogDao dao = SpringUtils.getBean(PrintResourceLogDao.class);
        ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
        String resourceName = request.getParameter("resourceName");
        String resourceId = request.getParameter("resourceId");
        List<PrintResourceLog> results = dao.findByResource(resourceName, resourceId);

        for (PrintResourceLog l : results) {
            l.setProviderName(providerDao.getProviderName(l.getProviderNo()));
        }
        ArrayNode json = objectMapper.valueToTree(results);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print(json.toString()); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- JSON API response with application/json content-type
        return null;
    }
}
