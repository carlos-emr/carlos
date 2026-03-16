package tests;

import org.testng.annotations.Test;
import org.testng.Assert;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.util.Set;
import java.util.List;

public class OpenEchartSubSectionsTest extends BaseTest {

    /**
     * Helper class to hold window handles during echart navigation.
     */
    private static class EchartContext {
        final WebDriver driver;
        final WebDriverWait wait;
        final String mainWindowHandle;
        final String demographicSearchWindowHandle;
        final String eChartWindowHandle;

        EchartContext(WebDriver driver, WebDriverWait wait, String mainWindowHandle,
                      String demographicSearchWindowHandle, String eChartWindowHandle) {
            this.driver = driver;
            this.wait = wait;
            this.mainWindowHandle = mainWindowHandle;
            this.demographicSearchWindowHandle = demographicSearchWindowHandle;
            this.eChartWindowHandle = eChartWindowHandle;
        }
    }

    /**
     * Helper method to navigate to echart for patient with demographic number 7.
     * Returns context with all window handles for further navigation.
     */
    private EchartContext navigateToEchartForPatient7() throws InterruptedException {
        WebDriver driver = createHeadlessDriver();
        WebDriverWait wait = createWait(driver);

        // Login
        login(driver, wait);
        Assert.assertTrue(isLoginSuccessful(driver));
        System.out.println("Login successful, the current URL is: " + driver.getCurrentUrl());

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful.");

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with demographic number 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) {
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        return new EchartContext(driver, wait, mainWindowHandle, demographicSearchWindowHandle, eChartWindowHandle);
    }

    /**
     * Helper method to click a tab in the echart and verify the new window opens correctly.
     */
    private void clickEchartTabAndVerify(EchartContext ctx, String linkText, String expectedUrlPattern,
                                          String tabName) throws InterruptedException {
        // Scroll to the top to reveal the tab
        ((JavascriptExecutor) ctx.driver).executeScript("window.scrollTo(0, 0);");

        WebElement tab = ctx.wait.until(ExpectedConditions.elementToBeClickable(By.linkText(linkText)));
        tab.click();

        // Wait for the new window to open
        ctx.wait.until(ExpectedConditions.numberOfWindowsToBe(4));
        Set<String> allWindowHandles = ctx.driver.getWindowHandles();
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(ctx.mainWindowHandle) &&
                !windowHandle.equals(ctx.demographicSearchWindowHandle) &&
                !windowHandle.equals(ctx.eChartWindowHandle)) {
                ctx.driver.switchTo().window(windowHandle);
                if (ctx.driver.getCurrentUrl().contains(expectedUrlPattern)) {
                    break;
                }
            }
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = ctx.driver.findElement(
                    By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening " + tabName + " window.");
                Assert.fail("500 error encountered while opening " + tabName + " window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            ctx.wait.until(ExpectedConditions.urlContains(expectedUrlPattern));
            Assert.assertTrue(ctx.driver.getCurrentUrl().contains(expectedUrlPattern));
            System.out.println(tabName + " window opened successfully.");
        }

        // Display the window for 2 sec
        Thread.sleep(2000);
    }

    // Test the open preventions window successful scenario
    @Test
    public void testOpenPreventionsWindowSuccessful() throws InterruptedException {
        EchartContext ctx = navigateToEchartForPatient7();
        try {
            clickEchartTabAndVerify(ctx, "Preventions", "oscarPrevention/index.jsp", "Preventions");
        } finally {
            ctx.driver.quit();
        }
    }

    // Test the open tickler window successful scenario
    @Test
    public void testOpenTicklerWindowSuccessful() throws InterruptedException {
        EchartContext ctx = navigateToEchartForPatient7();
        try {
            clickEchartTabAndVerify(ctx, "Tickler", "tickler/ticklerMain.jsp", "Tickler");
        } finally {
            ctx.driver.quit();
        }
    }

    // Test the open disease registry window successful scenario
    @Test
    public void testOpenDiseaseRegistryWindowSuccessful() throws InterruptedException {
        EchartContext ctx = navigateToEchartForPatient7();
        try {
            clickEchartTabAndVerify(ctx, "Disease Registry", "dxresearch/setupDxResearch.do", "Disease Registry");
        } finally {
            ctx.driver.quit();
        }
    }

    // Test the open forms window successful scenario
    @Test
    public void testOpenFormsWindowSuccessful() throws InterruptedException {
        EchartContext ctx = navigateToEchartForPatient7();
        try {
            clickEchartTabAndVerify(ctx, "Forms", "oscarEncounter/formlist.jsp", "Forms");
        } finally {
            ctx.driver.quit();
        }
    }

    // Test the open eforms window successful scenario
    @Test
    public void testOpenEformsWindowSuccessful() throws InterruptedException {
        EchartContext ctx = navigateToEchartForPatient7();
        try {
            clickEchartTabAndVerify(ctx, "eForms", "eform/efmpatientformlist.jsp", "eForms");
        } finally {
            ctx.driver.quit();
        }
    }

    // Test the open documents window successful scenario
    @Test
    public void testOpenDocumentsWindowSuccessful() throws InterruptedException {
        EchartContext ctx = navigateToEchartForPatient7();
        try {
            clickEchartTabAndVerify(ctx, "Documents", "documentManager/documentReport.jsp", "Documents");
        } finally {
            ctx.driver.quit();
        }
    }

    // Test the open lab result window successful scenario
    @Test
    public void testOpenLabResultWindowSuccessful() throws InterruptedException {
        EchartContext ctx = navigateToEchartForPatient7();
        try {
            clickEchartTabAndVerify(ctx, "Lab Result", "lab/DemographicLab.jsp", "Lab Result");
        } finally {
            ctx.driver.quit();
        }
    }

    // Test the open messenger window successful scenario
    @Test
    public void testOpenMessengerWindowSuccessful() throws InterruptedException {
        EchartContext ctx = navigateToEchartForPatient7();
        try {
            clickEchartTabAndVerify(ctx, "Messenger", "messenger/DisplayDemographicMessages.do", "Messenger");
        } finally {
            ctx.driver.quit();
        }
    }

    // Test the open measurements window successful scenario
    @Test
    public void testOpenMeasurementsWindowSuccessful() throws InterruptedException {
        EchartContext ctx = navigateToEchartForPatient7();
        try {
            clickEchartTabAndVerify(ctx, "Measurements", "oscarMeasurements/SetupHistoryIndex.do", "Measurements");
        } finally {
            ctx.driver.quit();
        }
    }

    // Test the open consultations window successful scenario
    @Test
    public void testOpenConsultationsWindowSuccessful() throws InterruptedException {
        EchartContext ctx = navigateToEchartForPatient7();
        try {
            clickEchartTabAndVerify(ctx, "Consultations",
                    "oscarConsultationRequest/DisplayDemographicConsultationRequests.jsp", "Consultations");
        } finally {
            ctx.driver.quit();
        }
    }

    // Test the open HRM documents window successful scenario
    @Test
    public void testOpenHRMDocumentsWindowSuccessful() throws InterruptedException {
        EchartContext ctx = navigateToEchartForPatient7();
        try {
            clickEchartTabAndVerify(ctx, "HRM Documents",
                    "hospitalReportManager/displayHRMDocList.jsp", "HRM Documents");
        } finally {
            ctx.driver.quit();
        }
    }

    // Test the open social history window successful scenario
    @Test
    public void testOpenSocialHistoryWindowSuccessful() throws InterruptedException {
        EchartContext ctx = navigateToEchartForPatient7();
        try {
            clickEchartTabAndVerify(ctx, "Social History", "oscar/CaseManagementEntry.do", "Social History");
        } finally {
            ctx.driver.quit();
        }
    }

    // Test the open medical history window successful scenario
    @Test
    public void testOpenMedicalHistoryWindowSuccessful() throws InterruptedException {
        EchartContext ctx = navigateToEchartForPatient7();
        try {
            clickEchartTabAndVerify(ctx, "Medical History", "oscar/CaseManagementEntry.do", "Medical History");
        } finally {
            ctx.driver.quit();
        }
    }

    // Test the open ongoing concerns window successful scenario
    @Test
    public void testOpenOngoingConcernsWindowSuccessful() throws InterruptedException {
        EchartContext ctx = navigateToEchartForPatient7();
        try {
            clickEchartTabAndVerify(ctx, "Ongoing Concerns", "oscar/CaseManagementEntry.do", "Ongoing Concerns");
        } finally {
            ctx.driver.quit();
        }
    }

    // Test the open reminders window successful scenario
    @Test
    public void testOpenRemindersWindowSuccessful() throws InterruptedException {
        EchartContext ctx = navigateToEchartForPatient7();
        try {
            clickEchartTabAndVerify(ctx, "Reminders", "oscar/CaseManagementEntry.do", "Reminders");
        } finally {
            ctx.driver.quit();
        }
    }

    // Test the open allergies window successful scenario
    @Test
    public void testOpenAllergiesWindowSuccessful() throws InterruptedException {
        EchartContext ctx = navigateToEchartForPatient7();
        try {
            clickEchartTabAndVerify(ctx, "Allergies", "oscarRx/showAllergy.do", "Allergies");
        } finally {
            ctx.driver.quit();
        }
    }

    // Test the open medications window successful scenario
    @Test
    public void testOpenMedicationsWindowSuccessful() throws InterruptedException {
        EchartContext ctx = navigateToEchartForPatient7();
        try {
            clickEchartTabAndVerify(ctx, "Medications", "oscarRx/choosePatient.do", "Medications");
        } finally {
            ctx.driver.quit();
        }
    }

    // Test the open other meds window successful scenario
    @Test
    public void testOpenOtherMedsWindowSuccessful() throws InterruptedException {
        EchartContext ctx = navigateToEchartForPatient7();
        try {
            clickEchartTabAndVerify(ctx, "Other Meds", "oscar/CaseManagementEntry.do", "Other Meds");
        } finally {
            ctx.driver.quit();
        }
    }

    // Test the open risk factors window successful scenario
    @Test
    public void testOpenRiskFactorsWindowSuccessful() throws InterruptedException {
        EchartContext ctx = navigateToEchartForPatient7();
        try {
            clickEchartTabAndVerify(ctx, "Risk Factors", "oscar/CaseManagementEntry.do", "Risk Factors");
        } finally {
            ctx.driver.quit();
        }
    }

    // Test the open family history window successful scenario
    @Test
    public void testOpenFamilyHistoryWindowSuccessful() throws InterruptedException {
        EchartContext ctx = navigateToEchartForPatient7();
        try {
            clickEchartTabAndVerify(ctx, "Family History", "oscar/CaseManagementEntry.do", "Family History");
        } finally {
            ctx.driver.quit();
        }
    }

    // Test the open unresolved issues page successful scenario
    @Test
    public void testOpenUnresolvedIssuesPageSuccessful() throws InterruptedException {
        EchartContext ctx = navigateToEchartForPatient7();
        try {
            clickEchartTabAndVerify(ctx, "Unresolved Issues", "oscar/CaseManagementView.do", "Unresolved Issues");
        } finally {
            ctx.driver.quit();
        }
    }

    // Test the open resolved issues page successful scenario
    @Test
    public void testOpenResolvedIssuesPageSuccessful() throws InterruptedException {
        EchartContext ctx = navigateToEchartForPatient7();
        try {
            clickEchartTabAndVerify(ctx, "Resolved Issues", "oscar/CaseManagementView.do", "Resolved Issues");
        } finally {
            ctx.driver.quit();
        }
    }

    // Test the open decision support alerts window successful scenario
    @Test
    public void testOpenDecisionSupportAlertsWindowSuccessful() throws InterruptedException {
        EchartContext ctx = navigateToEchartForPatient7();
        try {
            clickEchartTabAndVerify(ctx, "Decision Support Alerts",
                    "decisionSupport/guidelineAction.do", "Decision Support Alerts");
        } finally {
            ctx.driver.quit();
        }
    }

    // Test the open episodes window successful scenario
    @Test
    public void testOpenEpisodesWindowSuccessful() throws InterruptedException {
        EchartContext ctx = navigateToEchartForPatient7();
        try {
            clickEchartTabAndVerify(ctx, "Episodes", "oscar/Episode.do", "Episodes");
        } finally {
            ctx.driver.quit();
        }
    }

    // Test the open health care team window successful scenario
    @Test
    public void testOpenHealthCareTeamWindowSuccessful() throws InterruptedException {
        EchartContext ctx = navigateToEchartForPatient7();
        try {
            clickEchartTabAndVerify(ctx, "Health Care Team",
                    "demographic/displayHealthCareTeam.jsp", "Health Care Team");
        } finally {
            ctx.driver.quit();
        }
    }

}
