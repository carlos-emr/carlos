//CHECKSTYLE:OFF
/**
 * Improved version of EctProgram that defaults to OSCAR program ID
 * instead of always returning "0"
 */

package io.github.carlos_emr.carlos.encounter.data;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.http.HttpSession;
import java.util.List;

/**
 * Improved implementation that properly defaults to the OSCAR program
 * when no specific program is configured for a providers
 */
public class EctProgram_improved {
    private HttpSession se;
    private static String cachedOscarProgramId = null;

    /**
     * Creates a new instance of EctProgram
     */
    public EctProgram_improved(HttpSession se) {
        this.se = se;
    }

    /**
     * Gets the program ID for a providers.
     * 
     * This improved implementation:
     * 1. First checks if the providers has an assigned program
     * 2. Falls back to the OSCAR program if it exists
     * 3. Only returns "0" if no OSCAR program exists (backwards compatibility)
     * 
     * @param providerNo The providers number
     * @return The program ID as a string
     */
    public String getProgram(String providerNo) {
        try {
            // First check if providers has an assigned program
            ProgramProviderDAO programProviderDao = SpringUtils.getBean(ProgramProviderDAO.class);
            if (providerNo != null && !providerNo.trim().isEmpty()) {
                List<ProgramProvider> programProviders = programProviderDao.getProgramProviderByProviderNo(providerNo);
                if (programProviders != null && !programProviders.isEmpty()) {
                    // Return the first active program for this providers
                    for (ProgramProvider pp : programProviders) {
                        if (pp.getProgramId() != null) {
                            return String.valueOf(pp.getProgramId());
                        }
                    }
                }
            }
            
            // No providers-specific program found, default to OSCAR program
            return getOscarProgramId();
            
        } catch (Exception e) {
            // Log error and return OSCAR program ID or "0" as fallback
            MiscUtils.getLogger().error("Error getting program for providers " + providerNo, e);
            return getOscarProgramId();
        }
    }
    
    /**
     * Gets the OSCAR program ID, with caching for performance
     * @return The OSCAR program ID or "0" if not found
     */
    private String getOscarProgramId() {
        // Use cached value if available
        if (cachedOscarProgramId != null) {
            return cachedOscarProgramId;
        }
        
        try {
            ProgramDao programDao = SpringUtils.getBean(ProgramDao.class);
            Program oscarProgram = programDao.getProgramByName("OSCAR");
            
            if (oscarProgram != null && oscarProgram.getId() != null) {
                cachedOscarProgramId = String.valueOf(oscarProgram.getId());
                return cachedOscarProgramId;
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error getting OSCAR program", e);
        }
        
        // Fallback to "0" for backwards compatibility
        return "0";
    }
    
    /**
     * Clears the cached OSCAR program ID (useful if program is created/modified)
     */
    public static void clearCache() {
        cachedOscarProgramId = null;
    }

    public ApplicationContext getAppContext() {
        return WebApplicationContextUtils.getWebApplicationContext(
                se.getServletContext());
    }
}