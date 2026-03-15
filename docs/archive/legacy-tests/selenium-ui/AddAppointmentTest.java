package tests;

import org.testng.annotations.Test;
import org.testng.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.util.Set;

public class AddAppointmentTest extends BaseTest {

    // Test the add appointment successful scenario
    @Test
    public void testAddAppointmentSuccessful() {
        WebDriver driver = createHeadlessDriver();
        WebDriverWait wait = createWait(driver);

        try {
            // Login
            login(driver, wait);
            Assert.assertTrue(isLoginSuccessful(driver));

            // Click the "08:00" button to open the add appointment window
            WebElement appointmentButton = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("08:00")));
            appointmentButton.click();

            // Store the current window handle
            String mainWindowHandle = driver.getWindowHandle();

            // Switch to the add appointment window
            wait.until(ExpectedConditions.numberOfWindowsToBe(2));
            Set<String> allWindowHandles = driver.getWindowHandles();
            for (String windowHandle : allWindowHandles) {
                if (!windowHandle.equals(mainWindowHandle)) {
                    driver.switchTo().window(windowHandle);
                    break;
                }
            }

            // Ensure the URL of the add appointment window is correct
            wait.until(ExpectedConditions.urlContains("oscar/appointment/addappointment.jsp"));

            // Locate the search field, type patient's first name, and click the patient suggestion
            WebElement searchField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("keyword")));
            searchField.sendKeys("Alex");
            WebElement suggestion = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//li[contains(text(), 'ALEX, JOHN')]")
            ));
            suggestion.click();

            // Select reason
            Select reasonSelect = new Select(driver.findElement(By.name("reasonCode")));
            reasonSelect.selectByVisibleText("Testing");

            // Scroll to the bottom of the page
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("window.scrollTo(0, document.body.scrollHeight)");

            // Click the "Add Appointment" button
            WebElement addAppointmentButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("addButton")));
            addAppointmentButton.click();

            // Wait for the appointment window to close and return to main window
            wait.until(ExpectedConditions.numberOfWindowsToBe(1));
            driver.switchTo().window(mainWindowHandle);

            // Verify appointment was created (check for patient name in schedule)
            WebElement createdAppointment = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//a[contains(text(), 'ALEX, JOHN')]")
            ));
            Assert.assertNotNull(createdAppointment, "Appointment should appear in the schedule");
        } finally {
            driver.quit();
        }
    }
}
