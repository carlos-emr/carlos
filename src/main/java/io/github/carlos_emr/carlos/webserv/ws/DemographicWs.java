import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class DemographicWs {
    private static final Log log = LogFactory.getLog(DemographicWs.class);

    public void createDemographic(Demographic demographic) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "w", null)) {
            throw new SecurityException("User does not have write privilege for demographic");
        }
        // existing code
    }

    public void updateDemographic(Demographic demographic) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "w", null)) {
            throw new SecurityException("User does not have write privilege for demographic");
        }
        // existing code
    }

    public Demographic getDemographic(Integer demographicId) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)) {
            throw new SecurityException("User does not have read privilege for demographic");
        }
        // existing code
    }

    public List<Demographic> getAllDemographics() {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)) {
            throw new SecurityException("User does not have read privilege for demographic");
        }
        // existing code
    }
}