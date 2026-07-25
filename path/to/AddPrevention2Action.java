# complete code
import java.util.regex.Pattern;

public class AddPrevention2Action {
    // ... (rest of the class remains the same)

    public void validate() {
        String demographicNo = request.getParameter("demographic_no");
        if (demographicNo == null || !demographicNo.matches("\\d+")) {
            result.add("Invalid or missing demographic_no");
        } else if (!demographicDao.clientExists(Integer.parseInt(demographicNo))) {
            result.add("Patient not found");
        }
        // ... (rest of the method remains the same)
    }
}