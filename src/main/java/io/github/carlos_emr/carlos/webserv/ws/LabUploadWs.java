import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class LabUploadWs {
    private static final Log log = LogFactory.getLog(LabUploadWs.class);

    public void uploadLabResult(LabResult labResult) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_labresult", "w", null)) {
            throw new SecurityException("User does not have write privilege for lab result");
        }
        // existing code
    }

    public LabResult getLabResult(Integer labResultId) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_labresult", "r", null)) {
            throw new SecurityException("User does not have read privilege for lab result");
        }
        // existing code
    }
}