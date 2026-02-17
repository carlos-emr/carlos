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
 * Default Spring implementation of the {@link PreventionDS} prevention decision support service.
 *
 * <p>This bean loads Drools Rule Language (DRL) prevention/immunization rules into a compiled
 * {@link KieBase} at application startup via {@link PostConstruct @PostConstruct}, and provides
 * rule evaluation against patient {@link Prevention} fact objects using stateful
 * {@link KieSession} instances.</p>
 *
 * <h3>Three-Tier Rule Loading Priority</h3>
 * <p>The rule base is loaded from the first available source, checked in the following order:</p>
 * <ol>
 *   <li><strong>Filesystem / classpath property path (Priority 1)</strong> --
 *       If the {@code PREVENTION_FILE} property is set in
 *       {@link OscarProperties}, the value is interpreted as either:
 *       <ul>
 *         <li>An absolute filesystem path to a {@code .drl} file, loaded via
 *             {@link DroolsHelper#loadFromInputStream(java.io.InputStream)}</li>
 *         <li>A {@code classpath:} prefixed resource path (e.g.,
 *             {@code classpath:/oscar/oscarPrevention/prevention.drl}), loaded via
 *             {@link DroolsHelper#loadFromUrl(java.net.URL)}</li>
 *       </ul>
 *   </li>
 *   <li><strong>Database ResourceStorage (Priority 2)</strong> --
 *       If no filesystem rule file is found, queries the
 *       {@link ResourceStorageDao#findActive(String)} for an active
 *       {@link ResourceStorage#PREVENTION_RULES} record. The stored XML byte content is
 *       parsed and converted to DRL via
 *       {@link DSPreventionDrools#createRuleBase(byte[])}.</li>
 *   <li><strong>Classpath fallback (Priority 3)</strong> --
 *       If neither of the above sources provides rules, falls back to the bundled
 *       {@code /oscar/oscarPrevention/prevention.drl} resource on the classpath, loaded via
 *       {@link DroolsHelper#loadFromUrl(java.net.URL)}.</li>
 * </ol>
 *
 * <h3>Thread Safety</h3>
 * <p>The {@link #kieBase} is a shared static field. The {@link KieBase} itself is thread-safe
 * and designed for concurrent access. Each call to {@link #getMessages(Prevention)} creates
 * a new short-lived {@link KieSession} that is disposed of in a {@code finally} block,
 * ensuring no session state leaks between invocations.</p>
 *
 * <h3>Migration Note</h3>
 * <p>This class was migrated from the legacy Drools 2.0 API ({@code RuleBase},
 * {@code WorkingMemory}) to the modern KIE API (Drools 7.74.1) as part of the
 * CARLOS EMR Drools migration. The three-tier loading strategy and rule evaluation
 * semantics are preserved from the original implementation.</p>
 *
 * @since 2001-2002 (McMaster University); migrated to KIE API 2026-01-06
 * @see PreventionDS
 * @see Prevention
 * @see DroolsHelper
 * @see DSPreventionDrools
 * @see ResourceStorageDao
 * @see org.kie.api.KieBase
 * @see org.kie.api.runtime.KieSession
 */
@Component
public class PreventionDSImpl implements PreventionDS {
    private static Logger log = MiscUtils.getLogger();

    /**
     * Flag indicating whether the rule base has been loaded (regardless of success or failure).
     * Set to {@code true} at the end of {@link #loadRuleBase()}.
     */
    static boolean loaded = false;

    /**
     * The compiled Drools knowledge base containing the prevention/immunization rules.
     * Shared across all instances (static) and thread-safe for creating new
     * {@link KieSession} instances. Initialized by {@link #loadRuleBase()} at startup.
     */
    static KieBase kieBase = null;

    /** DAO for querying the {@code ResourceStorage} table for database-stored prevention rules. */
    @Autowired
    private ResourceStorageDao resourceStorageDao; // = SpringUtils.getBean(ResourceStorageDao.class);


    /**
     * Default no-argument constructor.
     *
     * <p>Rule loading is deferred to the {@link #loadRuleBase()} method, which is
     * invoked automatically by Spring via {@link PostConstruct @PostConstruct}
     * after dependency injection is complete.</p>
     */
    public PreventionDSImpl() {
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link #loadRuleBase()} to re-execute the three-tier loading
     * strategy. The existing {@link #kieBase} is replaced with the newly compiled
     * rule base upon success.</p>
     */
    public void reloadRuleBase() {
        loadRuleBase();
    }

    /**
     * Loads the prevention Drools rule base using the three-tier priority strategy.
     *
     * <p>Invoked automatically by Spring's {@link PostConstruct @PostConstruct} lifecycle
     * callback after dependency injection is complete. Can also be triggered manually via
     * {@link #reloadRuleBase()} for runtime rule updates.</p>
     *
     * <h4>Loading Priority</h4>
     * <ol>
     *   <li><strong>PREVENTION_FILE property</strong> -- Checks
     *       {@link OscarProperties} for the {@code PREVENTION_FILE} property.
     *       If set, attempts to load from the filesystem path first, then
     *       interprets {@code classpath:} prefixed values as classpath resources.</li>
     *   <li><strong>ResourceStorage database</strong> -- Queries
     *       {@link ResourceStorageDao#findActive(String)} for active
     *       {@link ResourceStorage#PREVENTION_RULES PREVENTION_RULES} records.
     *       The XML byte content is converted to DRL via
     *       {@link DSPreventionDrools#createRuleBase(byte[])}.</li>
     *   <li><strong>Classpath fallback</strong> -- Loads the bundled
     *       {@code /oscar/oscarPrevention/prevention.drl} from the classpath.</li>
     * </ol>
     *
     * <p>Sets the {@link #loaded} flag to {@code true} upon completion, regardless of
     * whether loading succeeded or failed. Errors at each tier are logged and do not
     * prevent attempting subsequent tiers.</p>
     */
    @PostConstruct
    private void loadRuleBase() {
        try {
            boolean fileFound = false;
            String preventionPath = OscarProperties.getInstance().getProperty("PREVENTION_FILE");

            // Priority 1: Load from filesystem path or classpath: property
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

                // Handle classpath: prefix for PREVENTION_FILE property value
                if (!fileFound && preventionPath.startsWith("classpath:")) {
                    URL url = PreventionDS.class.getResource(preventionPath.substring(10));
                    log.debug("loading from URL " + url.getFile());
                    kieBase = DroolsHelper.loadFromUrl(url);
                }
            }

            // Priority 2: Load from ResourceStorage database table
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


            // Priority 3: Classpath fallback to bundled prevention.drl
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


    /**
     * {@inheritDoc}
     *
     * <p>Creates a new stateful {@link KieSession} from the cached {@link #kieBase},
     * inserts the given {@link Prevention} fact, and fires all matching rules. The Drools
     * rules evaluate the patient's demographics and immunization history and add warnings
     * and reminders directly to the {@code Prevention} object via its
     * {@link Prevention#addWarning(String, String)} and {@link Prevention#addReminder(String)}
     * methods.</p>
     *
     * <h4>KieSession Lifecycle</h4>
     * <ol>
     *   <li>A new {@link KieSession} is created from the shared {@link #kieBase}</li>
     *   <li>The {@link Prevention} object is inserted as a fact into working memory</li>
     *   <li>{@link KieSession#fireAllRules()} evaluates all matching prevention rules</li>
     *   <li>The session is disposed in a {@code finally} block to release resources</li>
     * </ol>
     *
     * @param p Prevention the patient prevention fact object containing demographics and
     *          immunization history; must not be {@code null}
     * @return Prevention the same object, now enriched with warnings and reminders
     * @throws Exception if rule evaluation fails; wraps the root cause with
     *                   {@code "ERROR: Drools "} message prefix
     */
    public Prevention getMessages(Prevention p) throws Exception {
        KieSession kieSession = null;
        try {
            // Create a new stateful session for this evaluation
            kieSession = kieBase.newKieSession();

            // Insert the Prevention fact into working memory
            kieSession.insert(p);

            // Fire all matching rules; rules modify the Prevention object directly
            kieSession.fireAllRules();
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            throw new Exception("ERROR: Drools ", e);
        } finally {
            // Dispose the session to release resources and prevent memory leaks
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
