package tests;

import org.testng.annotations.Test;
import org.testng.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.util.Set;
import java.util.List;

public class AddAppointmentTest {
    
    // Test the add appointment successful scenario
    @Test
    public void testAddAppointmentSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click the "08:00" button to open the add appointment window
        WebElement appointmentButton = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("08:00")));
        appointmentButton.click();

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the add appointment window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String addAppointmentWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                addAppointmentWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the add appointment window is correct
        wait.until(ExpectedConditions.urlContains("oscar/appointment/addappointment.jsp"));

        // Locate the search field, type patient's first name, and click the patient suggestion
        WebElement searchField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("keyword")));
        searchField.sendKeys("Alex");
        Thread.sleep(1000); 
        List<WebElement> suggestions = driver.findElements(By.tagName("li")); 
        for (WebElement suggestion : suggestions) {
            if (suggestion.getText().contains("ALEX, JOHN")) {
                suggestion.click();
                break;
            }
        }

        // Select reason
        Select reasonSelect = new Select(driver.findElement(By.name("reasonCode")));
        reasonSelect.selectByVisibleText("Testing");

        // Scroll to the bottom of the page
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("window.scrollTo(0, document.body.scrollHeight)");

        // Click the "Add Appointment" button
        WebElement addAppointmentButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("addButton")));
        addAppointmentButton.click();

        // Display the main page for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }
}
