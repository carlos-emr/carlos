/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * Originally written for the Department of Family Medicine, McMaster University.
 * Now maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 *
 * Modifications by Magenta Health in 2024.
 * Modifications by CARLOS Contributors, 2026.
 */


package io.github.carlos_emr.carlos.prevention;

//import java.io.ByteArrayInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;

import javax.annotation.PostConstruct;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import io.github.carlos_emr.carlos.drools.DroolsHelper;
import io.github.carlos_emr.carlos.commn.dao.ResourceStorageDao;
import io.github.carlos_emr.carlos.commn.model.ResourceStorage;
import io.github.carlos_emr.carlos.decisionSupport.prevention.DSPreventionDrools;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.carlos_emr.OscarProperties;

/**
 * @author Jay Gallagher
 */
@Component
public class PreventionDSImpl implements PreventionDS {
    private static Logger log = MiscUtils.getLogger();
    static boolean loaded = false;
    static KieBase kieBase = null;

    @Autowired
    private ResourceStorageDao resourceStorageDao; // = SpringUtils.getBean(ResourceStorageDao.class);


    public PreventionDSImpl() {
    }

    public void reloadRuleBase() {
        loadRuleBase();
    }

    @PostConstruct
    private void loadRuleBase() {
        try {
            boolean fileFound = false;
            String preventionPath = OscarProperties.getInstance().getProperty("PREVENTION_FILE");

            if (preventionPath != null) {
                File file = new File(OscarProperties.getInstance().getProperty("PREVENTION_FILE"));
                if (file.isFile() || file.canRead()) {
                    log.debug("Loading from file " + file.getName());

                    FileInputStream fis = new FileInputStream(file);
                    try {
                        kieBase = DroolsHelper.loadFromInputStream(fis);
                    } catch (Exception e) {
                        MiscUtils.getLogger().error("Error loading preventions", e);
                    } finally {
                        IOUtils.closeQuietly(fis);
                    }

                    fileFound = true;
                }

                if (!fileFound && preventionPath.startsWith("classpath:")) {
                    URL url = PreventionDS.class.getResource(preventionPath.substring(10));
                    log.debug("loading from URL " + url.getFile());
                    kieBase = DroolsHelper.loadFromUrl(url);
                }
            }

            if (!fileFound) {
                ResourceStorage resourceStorage = resourceStorageDao.findActive(ResourceStorage.PREVENTION_RULES);
                if (resourceStorage != null) {
                    try {
                        kieBase = DSPreventionDrools.createRuleBase(resourceStorage.getFileContents());
                        log.info("Loading prevention rule base from " + resourceStorage.getResourceName());
                        fileFound = true;
                    } catch (Exception resourceError) {
                        log.error("ERROR LOADING from resource Storage", resourceError);
                    }
                }


                // check if table has new preventions DRL
            }


            if (!fileFound) {
                URL url = PreventionDS.class.getResource("/oscar/oscarPrevention/prevention.drl");  //TODO: change this so it is configurable;
                log.debug("loading from URL " + url.getFile());
                kieBase = DroolsHelper.loadFromUrl(url);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        loaded = true;
    }


    public Prevention getMessages(Prevention p) throws Exception {
        KieSession kieSession = null;
        try {
            kieSession = kieBase.newKieSession();
            kieSession.insert(p);
            kieSession.fireAllRules();
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            throw new Exception("ERROR: Drools ", e);
        } finally {
            if (kieSession != null) {
                kieSession.dispose();
            }
        }
        return p;
    }


    ///
//         URL url = Prevs.class.getResource( "prevention.drl" );
//      log.debug(url.getFile());
//      
//      //RuleBase ruleBase = RuleBaseBuilder.buildFromUrl( url );
//      RuleBase ruleBase = RuleBaseLoader.loadFromUrl( url );
//      
//      
//      
//      Prevention[] p = new Prevention[4];
//      
//      p[0] = new Prevention("BOB","M", new GregorianCalendar(1998, Calendar.DECEMBER, 12).getTime()); //name, num imm, sex, age in months
//      p[1] = new Prevention("RITA","F", new GregorianCalendar(2005, Calendar.JANUARY, 1).getTime()); 
//      p[2] = new Prevention("GEORGE","M", new GregorianCalendar(2004, Calendar.APRIL, 25).getTime()); 
//      p[3] = new Prevention("Tyler","M", new GregorianCalendar(2005, Calendar.MARCH, 23).getTime()); 
//            
//      
//      PreventionItem pi = new PreventionItem("DTaP IPV", new GregorianCalendar(2005, Calendar.MARCH, 1).getTime());
//      p[1].addPreventionItem(pi, "DTaP IPV");
//      log.debug("should be 1 "+p[1].getNumberOfPreventionType("DTaP IPV"));
//      
//      log.debug("should be ?? "+p[1].getHowManyMonthsSinceLast("DTaP IPV"));
//      
//      for ( int k = 0; k < p.length; k++){
//         long start = System.currentTimeMillis();
//         WorkingMemory workingMemory = ruleBase.newWorkingMemory();
//
//         workingMemory.assertObject(p[k]);
//
//         workingMemory.fireAllRules();
//         long end = System.currentTimeMillis();
//         
//         ArrayList alist = p[k].getWarnings();
//         log.debug(p[k].getName()+" "+"size:"+alist.size()+ " at months : "+p[k].getAgeInMonths() + " time :"+(end-start));
//
//         for (int i = 0; i < alist.size(); i++){         
//            String s = (String) alist.get(i);
//            log.debug(s);
//         }
//        
//      }


    ////

}
