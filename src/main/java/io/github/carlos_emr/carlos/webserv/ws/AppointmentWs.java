import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class AppointmentWs {
    private static final Log log = LogFactory.getLog(AppointmentWs.class);

    public void createAppointment(Appointment appointment) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "w", null)) {
            throw new SecurityException("User does not have write privilege for appointment");
        }
        // existing code
    }

    public void updateAppointment(Appointment appointment) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "w", null)) {
            throw new SecurityException("User does not have write privilege for appointment");
        }
        // existing code
    }

    public Appointment getAppointment(Integer appointmentId) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "r", null)) {
            throw new SecurityException("User does not have read privilege for appointment");
        }
        // existing code
    }
}