import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class DrugWs {
    private static final Log log = LogFactory.getLog(DrugWs.class);

    public void createDrug(Drug drug) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_drug", "w", null)) {
            throw new SecurityException("User does not have write privilege for drug");
        }
        // existing code
    }

    public void updateDrug(Drug drug) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_drug", "w", null)) {
            throw new SecurityException("User does not have write privilege for drug");
        }
        // existing code
    }

    public Drug getDrug(Integer drugId) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_drug", "r", null)) {
            throw new SecurityException("User does not have read privilege for drug");
        }
        // existing code
    }
}