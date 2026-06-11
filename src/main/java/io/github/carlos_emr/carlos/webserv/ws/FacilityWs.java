import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class FacilityWs {
    private static final Log log = LogFactory.getLog(FacilityWs.class);

    public void createFacility(Facility facility) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_facility", "w", null)) {
            throw new SecurityException("User does not have write privilege for facility");
        }
        // existing code
    }

    public void updateFacility(Facility facility) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_facility", "w", null)) {
            throw new SecurityException("User does not have write privilege for facility");
        }
        // existing code
    }

    public Facility getFacility(Integer facilityId) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_facility", "r", null)) {
            throw new SecurityException("User does not have read privilege for facility");
        }
        // existing code
    }
}