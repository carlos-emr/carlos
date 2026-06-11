import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class PrescriptionWs {
    private static final Log log = LogFactory.getLog(PrescriptionWs.class);

    public void createPrescription(Prescription prescription) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_prescription", "w", null)) {
            throw new SecurityException("User does not have write privilege for prescription");
        }
        // existing code
    }

    public void updatePrescription(Prescription prescription) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_prescription", "w", null)) {
            throw new SecurityException("User does not have write privilege for prescription");
        }
        // existing code
    }

    public Prescription getPrescription(Integer prescriptionId) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_prescription", "r", null)) {
            throw new SecurityException("User does not have read privilege for prescription");
        }
        // existing code
    }
}