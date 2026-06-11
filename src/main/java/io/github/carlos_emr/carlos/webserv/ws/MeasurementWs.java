import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class MeasurementWs {
    private static final Log log = LogFactory.getLog(MeasurementWs.class);

    public void createMeasurement(Measurement measurement) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_measurement", "w", null)) {
            throw new SecurityException("User does not have write privilege for measurement");
        }
        // existing code
    }

    public void updateMeasurement(Measurement measurement) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_measurement", "w", null)) {
            throw new SecurityException("User does not have write privilege for measurement");
        }
        // existing code
    }

    public Measurement getMeasurement(Integer measurementId) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_measurement", "r", null)) {
            throw new SecurityException("User does not have read privilege for measurement");
        }
        // existing code
    }
}