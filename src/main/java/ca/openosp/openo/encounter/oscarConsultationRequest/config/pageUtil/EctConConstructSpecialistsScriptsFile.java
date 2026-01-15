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

package ca.openosp.openo.encounter.oscarConsultationRequest.config.pageUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Vector;

import org.apache.commons.text.StringEscapeUtils;

import ca.openosp.openo.commn.dao.ConsultationServiceDao;
import ca.openosp.openo.commn.dao.ServiceSpecialistsDao;
import ca.openosp.openo.commn.model.ConsultationServices;
import ca.openosp.openo.commn.model.ProfessionalSpecialist;
import ca.openosp.openo.commn.model.ServiceSpecialists;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.encounter.oscarConsultationRequest.config.data.EctConConfigurationJavascriptData;
import ca.openosp.openo.util.ConversionUtils;

public class EctConConstructSpecialistsScriptsFile {
    private ConsultationServiceDao consultationServiceDao = (ConsultationServiceDao) SpringUtils.getBean(ConsultationServiceDao.class);

    Vector<String> serviceId;
    Vector<String> serviceDesc;
    String javaScriptString;
    String retval;

    public EctConConstructSpecialistsScriptsFile() {
        retval = null;
    }

    public String makeFile() {
        serviceId = new Vector<String>();
        serviceDesc = new Vector<String>();
        File file = new File("oscarEncounter/consult.js");
        try {
            FileWriter fileWriter = new FileWriter(file);
            retval = "writing file too ".concat(String.valueOf(String.valueOf(file.getAbsolutePath())));
            fileWriter.write("function makeSpecialistslist(dec){\n");
            fileWriter.write(" if(dec=='1') \n");
            fileWriter.write("{K(-1,\"----Choose a Service-------\");D(-1,\"--------Choose a Specialist-----\");}\n");
            fileWriter.write("else\n");
            fileWriter.write("{K(-1,\"----All Services-------\");D(-1,\"--------All Specialists-----\");}\n");

            List<ConsultationServices> services = consultationServiceDao.findAll();
            for (ConsultationServices cs : services) {
                serviceId.add(String.valueOf(cs.getServiceId()));
                serviceDesc.add(cs.getServiceDesc());
            }

            ServiceSpecialistsDao dao = SpringUtils.getBean(ServiceSpecialistsDao.class);
            for (int i = 0; i < serviceId.size(); i++) {
                String servId = serviceId.elementAt(i);
                String servDesc = serviceDesc.elementAt(i);
                fileWriter.write("K(" + servId + ",\"" + servDesc + "\");\n");
                for (Object[] o : dao.findSpecialists(ConversionUtils.fromIntString(servId))) {
                    ServiceSpecialists ser = (ServiceSpecialists) o[0];
                    ProfessionalSpecialist pro = (ProfessionalSpecialist) o[1];

                    String name = pro.getLastName() + ", " + pro.getFirstName() + " " + pro.getProfessionalLetters();

                    String specId = "" + ser.getId().getSpecId();
                    String phone = pro.getPhoneNumber();
                    String address = pro.getStreetAddress();
                    String fax = pro.getFaxNumber();
                    fileWriter.write("D(" + servId + ",\"" + specId + "\",\"" + phone + "\",\"" + name + "\",\"" + fax + "\",\"" + address + "\");\n");
                }

                fileWriter.write("\n");
            }

            fileWriter.write("\n");
            fileWriter.write("}\n");
            fileWriter.close();
        } catch (IOException io) {
            MiscUtils.getLogger().debug(io.getMessage());
        }
        return retval;
    }

    public void makeString(Locale locale) {
        serviceId = new Vector<String>();
        serviceDesc = new Vector<String>();
        ResourceBundle props = ResourceBundle.getBundle("oscarResources", locale);
        StringBuilder stringBuffer = new StringBuilder();
        stringBuffer.append("function makeSpecialistslist(dec){\n");
        stringBuffer.append(" if(dec=='1') \n");
        stringBuffer.append("{K(-1,\"----" + props.getString("oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.optChooseServ") + "-------\");D(-1,\"--------" + props.getString("oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.optChooseSpec") + "-----\");}\n");
        stringBuffer.append("else\n");
        stringBuffer.append("{K(-1,\"----" + props.getString("oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.optAllServices") + "-------\");D(-1,\"--------" + props.getString("oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.optAllSpecs") + "-----\");}\n");

        List<ConsultationServices> services = consultationServiceDao.findActive();
        for (ConsultationServices cs : services) {
            serviceId.add(String.valueOf(cs.getServiceId()));
            serviceDesc.add(cs.getServiceDesc());
        }

        ServiceSpecialistsDao dao = SpringUtils.getBean(ServiceSpecialistsDao.class);
        for (int i = 0; i < serviceId.size(); i++) {
            String servId = serviceId.elementAt(i);
            String servDesc = serviceDesc.elementAt(i);
            stringBuffer.append(String.valueOf(String.valueOf((new StringBuilder("K(")).append(servId).append(",\"").append(servDesc).append("\");\n"))));

            for (Object[] o : dao.findSpecialists(ConversionUtils.fromIntString(servId))) {
                ServiceSpecialists ser = (ServiceSpecialists) o[0];
                ProfessionalSpecialist pro = (ProfessionalSpecialist) o[1];

                String name = pro.getLastName() + ", " + pro.getFirstName() + (pro.getProfessionalLetters() == null ? "" : " " + pro.getProfessionalLetters());
                name = this.escapeString(name);
                String specId = "" + ser.getId().getSpecId();
                String phone = pro.getPhoneNumber();
                String address = pro.getStreetAddress();
                address = this.escapeString(address);
                String fax = pro.getFaxNumber();
                stringBuffer.append("D(" + servId + ",\"" + specId + "\",\"" + phone + "\",\"" + name + "\",\"" + fax + "\",\"" + address + "\");\n");
            }

            stringBuffer.append("\n");

        }

        stringBuffer.append("\n");
        stringBuffer.append("}\n");
        javaScriptString = stringBuffer.toString();
        EctConConfigurationJavascriptData configurationJavascriptData = new EctConConfigurationJavascriptData();
        configurationJavascriptData.setJavascript(javaScriptString);
    }

    private String escapeString(String s) {
        s = StringEscapeUtils.escapeEcmaScript(s);
        return s;
    }
}
