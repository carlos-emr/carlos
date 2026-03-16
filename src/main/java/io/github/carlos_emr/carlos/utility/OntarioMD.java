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


package io.github.carlos_emr.carlos.utility;

import java.io.InputStream;
import java.util.Hashtable;
import java.util.Iterator;

import org.apache.commons.collections4.OrderedMap;
import org.apache.commons.collections4.map.LinkedMap;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.filter.ElementFilter;
import org.jdom2.input.SAXBuilder;

import io.github.carlos_emr.OscarProperties;

/**
 * @author jaygallagher
 */
public class OntarioMD {


    static public boolean hasIncomingRequestor() {
        return OscarProperties.getInstance().hasProperty("ONTARIO_MD_INCOMINGREQUESTOR");
    }

    static public String getIncomingRequestor() {
        return OscarProperties.getInstance().getProperty("ONTARIO_MD_INCOMINGREQUESTOR");
    }

    //HACKED SOAP CALL.  THIS SHOULD BE REPLACED BUT IT'S SO SIMPLE
    public Hashtable loginToOntarioMD(String username, String password, String incomingRequestor) throws Exception {
        //public ArrayList soapHttpCall(int siteCode, String userId, String passwd,		String xml) throws Exception
        Hashtable h = null;
        HttpPost post = new HttpPost("https://www.ontariomd.ca/services/OMDAutomatedAuthentication");
        post.setHeader("SOAPAction", "");
        post.setHeader("Content-Type", "text/xml; charset=utf-8");

        String soapMsg = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><ns1:getSession xmlns:ns1=\"urn:OMDAutomatedAuthentication\"><username>" + username + "</username><password>" + password + "</password><incomingRequestor>" + incomingRequestor + "</incomingRequestor></ns1:getSession></soap:Body></soap:Envelope> ";

        StringEntity entity = new StringEntity(soapMsg, "UTF-8");
        post.setEntity(entity);

        try (CloseableHttpClient httpclient = HttpClients.createDefault();
            CloseableHttpResponse response = httpclient.execute(post)) {

            InputStream responseStream = response.getEntity().getContent();
            h = parseReturn(responseStream);

            EntityUtils.consume(response.getEntity());

        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        } finally {
            // Release current connection to the connection pool
            post.releaseConnection();
        }
        return h;
    }

    private Hashtable parseReturn(InputStream is) {
        Hashtable h = null;
        try {
            // Create secure SAXBuilder with XXE protection
            SAXBuilder parser = new SAXBuilder();

            // Security features to prevent XXE attacks
            setFeatureSafely(parser, "http://apache.org/xml/features/disallow-doctype-decl", true);
            setFeatureSafely(parser, "http://xml.org/sax/features/external-general-entities", false);
            setFeatureSafely(parser, "http://xml.org/sax/features/external-parameter-entities", false);
            setFeatureSafely(parser, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            
            try {
                parser.setExpandEntities(false);
            } catch (Exception ex) {
                MiscUtils.getLogger().error("Could not disable entity expansion: " + ex.getMessage());
            }
            Document doc = parser.build(is);
            Element root = doc.getRootElement();

            h = new Hashtable();

            String jsessionID = g(root.getDescendants(new ElementFilter("jsessionID")));
            String ptLoginToken = g(root.getDescendants(new ElementFilter("ptLoginToken")));
            String returnCode = g(root.getDescendants(new ElementFilter("returnCode")));

            h.put("returnCode", returnCode);
            h.put("ptLoginToken", ptLoginToken);
            h.put("jsessionID", jsessionID);

        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return h;
    }

    private void setFeatureSafely(SAXBuilder parser, String feature, boolean value) {
        try {
            parser.setFeature(feature, value);
        } catch (Exception ex) {
            MiscUtils.getLogger().warn("Could not set feature " + feature + ": " + ex.getMessage());
        }
    }

    private String g(Iterator iter) {
        String ret = null;
        if (iter.hasNext()) {
            Element ele = (Element) iter.next();
            ret = ele.getValue();
        }
        return ret;
    }

    public OrderedMap getDiseaseList() {
        OrderedMap map = new LinkedMap();
     /*
      Disease Community Keyword 
      */
        map.put("Acne", "Acne");
        map.put("AcuteBronchitis", "Acute Bronchitis");
        map.put("AddictionMedicine", "Addiction Medicine");
        map.put("Allergy", "Allergy");
        map.put("AlzheimersDisease", "Alzheimer's Disease");
        map.put("Andropause", "Andropause");
        map.put("AnxietyDisorders", "Anxiety Disorders");
        map.put("Appendicitis", "Appendicitis");
        map.put("Asthma", "Asthma");
        map.put("AtrialFibrillation", "Atrial Fibrillation");
        map.put("AvianFlu", "Avian Flu");
        map.put("BenignProstaticHyperplasia", "Benign Prostatic Hyperplasia");
        map.put("BipolarDisorder", "Bipolar Disorder");
        map.put("BreastCancer", "Breast Cancer");
        map.put("CHF", "CHF");
        map.put("COPD", "COPD");
        map.put("CVA", "CVA (Stroke)");

        map.put("Cholecystitis", "Cholecystitis");
        map.put("ChronicPain", "Chronic Pain");
        map.put("ColonCancer", "Colon Cancer");
        map.put("Contraception", "Contraception");
        map.put("CoronaryArteryDisease", "Coronary Artery Disease");
        map.put("Depression", "Depression");
        map.put("Diabetes", "Diabetes");
        map.put("Dyslipidemia", "Dyslipidemia");
        map.put("EatingDisorders", "Eating Disorders");
        map.put("ErectileDysfunction", "Erectile Dysfunction");
        map.put("GERD", "GERD");
        map.put("HIVandAIDS", "HIV and AIDS");
        map.put("Headache", "Headache");
        map.put("Hepatitis", "Hepatitis");
        map.put("Herpes", "Herpes");
        map.put("Hypertension", "Hypertension");
        map.put("Infertility", "Infertility");
        map.put("InflammatoryBowelDisease", "Inflammatory Bowel Disease");
        map.put("Influenza", "Influenza");
        map.put("IrritableBowelSyndrome", "Irritable Bowel Syndrome");
        map.put("LungCancer", "Lung Cancer");
        map.put("Menopause", "Menopause");
        map.put("Obesity", "Obesity");
        map.put("Osteoporosis", "Osteoporosis");
        map.put("OvarianCancer", "Ovarian Cancer");
        map.put("PalliativeCare", "Palliative Care");
        map.put("ParkinsonsDisease", "Parkinson's Disease");
        map.put("ProstateCancer", "Prostate Cancer");
        map.put("Psoriasis", "Psoriasis");
        map.put("RheumatoidArthritis", "Rheumatoid Arthritis/Autoimmune");
        map.put("SARS", "SARS");
        map.put("STIs", "STIs");
        map.put("Schizophrenia", "Schizophrenia");
        map.put("Sinusitis", "Sinusitis");
        map.put("SmokingCessation", "Smoking Cessation");
        map.put("ThyroidDisorders", "Thyroid Disorders");
        map.put("UrinaryIncontinence", "Urinary Incontinence");
        map.put("WestNileVirus", "West Nile Virus");
        return map;
    }

}
