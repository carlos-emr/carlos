/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform.util;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilege;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilegePrimaryKey;
import io.github.carlos_emr.carlos.managers.CanadianVaccineCatalogueManager;
import io.github.carlos_emr.carlos.test.browser.SmokeDriverService;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.*;
import java.net.URI;
import java.nio.file.*;
import java.util.Date;
import java.util.List;
import org.apache.catalina.startup.Tomcat;
import org.apache.jasper.servlet.JasperInitializer;
import org.apache.jasper.servlet.JspServlet;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.hl7.fhir.dstu3.model.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

/** Actual production JSP compiled by Jasper, then parsed/executed by Chromium. No DHIR submission. */
@Tag("integration") @Tag("browser")
class DhirReviewJspBrowserIntegrationTest {
    private static final String PAYLOAD="日本語 & \" ' <img src=x onerror=\"window.__dhirInjected=1\"><script>window.__dhirInjected=1</script>";
    private static final String BUNDLE_ID="fixture\" autofocus onfocus=\"window.__dhirInjected=1";
    @TempDir Path directory;

    @Test void shouldRenderClinicalTextAndUuidAsData_inTheActualReviewJsp() throws Exception {
        try(var drivers=new SmokeDriverService()) {
            String service=drivers.endpointOrNull(); assumeTrue(service!=null,"Configure the browser driver for DHIR review qualification");
            String chromium=System.getenv("CARLOS_SMOKE_CHROMIUM"); assumeTrue(chromium!=null,"Configure Chromium for DHIR review qualification");
            var previous=SpringUtils.getBeanFactory(); var beans=new DefaultListableBeanFactory();
            beans.registerSingleton("demographicExtDao",mock(DemographicExtDao.class));
            beans.registerSingleton("canadianVaccineCatalogueManager",mock(CanadianVaccineCatalogueManager.class));
            beans.registerSingleton("cvcMappingDao",mock(CVCMappingDao.class));
            beans.registerSingleton("cvcImmunizationDao",mock(CVCImmunizationDao.class));
            beans.registerSingleton("providerDao",mock(ProviderDao.class));
            var privileges=mock(SecObjPrivilegeDao.class); var grant=new SecObjPrivilege();
            grant.setId(new SecObjPrivilegePrimaryKey("doctor","_prevention")); grant.setPrivilege("r");grant.setPriority(1);
            when(privileges.findByObjectNames(any())).thenReturn(List.of(grant)); beans.registerSingleton("secObjPrivilegeDao",privileges);
            ReflectionTestUtils.setField(SpringUtils.class,"beanFactory",beans);
            var tomcat=new Tomcat();
            try {
                Path web=Files.createDirectories(directory.resolve("web"));
                copy(Path.of("src/main/webapp/WEB-INF/jsp/prevention/review.jsp"),web.resolve("WEB-INF/jsp/prevention/review.jsp"));
                copy(Path.of("src/main/webapp/WEB-INF/security.tld"),web.resolve("WEB-INF/security.tld"));
                Path libs=Files.createDirectories(web.resolve("WEB-INF/lib"));
                for(Class<?> type:List.of(jakarta.servlet.jsp.jstl.core.Config.class,org.apache.taglibs.standard.tag.rt.fmt.SetBundleTag.class)) {
                    Path jar=Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());copy(jar,libs.resolve(jar.getFileName()));
                }
                tomcat.setBaseDir(directory.resolve("tomcat").toString());tomcat.setPort(0);tomcat.getConnector();tomcat.getHost().setAutoDeploy(false);
                var context=tomcat.addContext("/fixture",web.toString());context.setParentClassLoader(getClass().getClassLoader());
                var scanner=new StandardJarScanner();scanner.setScanClassPath(false);context.setJarScanner(scanner);
                context.addServletContainerInitializer(new JasperInitializer(),null);
                Tomcat.addServlet(context,"jsp",new JspServlet());context.addServletMappingDecoded("*.jsp","jsp");
                Tomcat.addServlet(context,"review",new HttpServlet() {
                    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response) throws java.io.IOException,jakarta.servlet.ServletException {
                        if(request.getRequestURI().endsWith("control")) {
                            response.setContentType("text/html;charset=UTF-8");response.getWriter().write("<script>window.__dhirInjected=1</script>");return;
                        }
                        var session=request.getSession();session.setAttribute("userrole","doctor");session.setAttribute("user","FAKE");
                        LoggedInInfo.setLoggedInInfoIntoSession(session,mock(LoggedInInfo.class));
                        request.setAttribute("bundle",bundle());request.setAttribute("demographicNo","42");request.setAttribute("preventionId",7);
                        request.getRequestDispatcher("/WEB-INF/jsp/prevention/review.jsp").forward(request,response);
                    }
                }); context.addServletMappingDecoded("/review","review");context.addServletMappingDecoded("/control","review");
                tomcat.start();String origin="http://127.0.0.1:"+tomcat.getConnector().getLocalPort();
                try(var browser=new RenderBrowser(URI.create(service),EFormBrowserPdfService.buildBrowserOptions(chromium,true,origin).asMap())) {
                    browser.get(origin+"/fixture/control");assertThat(browser.executeScript("return window.__dhirInjected")).isEqualTo(1L);
                    browser.get(origin+"/fixture/review");
                    assertThat(browser.executeScript("return document.title")).isEqualTo("CARLOS Prevention Review Screen");
                    assertThat((String)browser.executeScript("return document.body.innerText")).contains("DHIR Submission Review",PAYLOAD,"Retained clinician");
                    assertThat(browser.executeScript("return window.__dhirInjected || 0")).isEqualTo(0L);
                    assertThat(browser.executeScript("return document.querySelectorAll('img[onerror], [autofocus]').length")).isEqualTo(0L);
                    assertThat(browser.executeScript("return document.querySelector('input[name=uuid]').value")).isEqualTo(BUNDLE_ID);
                    assertThat(browser.executeScript("return document.characterSet")).isEqualTo("UTF-8");
                }
            } finally {try {tomcat.stop();tomcat.destroy();} finally {ReflectionTestUtils.setField(SpringUtils.class,"beanFactory",previous);}}
        }
    }
    private static void copy(Path from,Path to) throws Exception {Files.createDirectories(to.getParent());Files.copy(from,to,StandardCopyOption.REPLACE_EXISTING);}
    private static Bundle bundle() {
        var bundle=new Bundle();bundle.setId(BUNDLE_ID);
        var header=new MessageHeader();header.setAuthor(new Reference("Practitioner/1"));header.setSender(new Reference().setDisplay(PAYLOAD));
        header.getSource().setName(PAYLOAD).setSoftware(PAYLOAD).setVersion(PAYLOAD).setEndpoint("http://fixture.invalid");header.setTimestamp(new Date());
        var submitter=new Practitioner();submitter.setId("1");submitter.addName().setFamily("Retained clinician").addGiven("Test");
        submitter.addIdentifier().setSystem("https://ehealthontario.ca/API/FHIR/NamingSystem/ca-on-provider-oneid").setValue(PAYLOAD);
        var patient=new Patient();patient.setId("42");patient.addName().setFamily(PAYLOAD).addGiven("Test");
        patient.addIdentifier().setSystem("https://ehealthontario.ca/API/FHIR/NamingSystem/ca-on-patient-hcn").setValue(PAYLOAD);
        patient.setGender(Enumerations.AdministrativeGender.FEMALE);patient.setBirthDate(new Date(0));
        patient.addAddress().addLine(PAYLOAD).setCity(PAYLOAD).setState(PAYLOAD).setPostalCode(PAYLOAD);
        var performer=new Practitioner();performer.setId("2");performer.addName().setFamily(PAYLOAD).addGiven("Test");
        var immunization=new Immunization();immunization.setId("7");immunization.setDate(new Date());immunization.setPrimarySource(true);
        immunization.getVaccineCode().addCoding().setCode("fixture").setDisplay(PAYLOAD);immunization.setLotNumber(PAYLOAD);
        immunization.addPractitioner().setActor(new Reference("Practitioner/2"));
        for(Resource resource:List.of(header,submitter,patient,performer,immunization)) bundle.addEntry().setResource(resource);
        return bundle;
    }
}
