package tests;

import org.testng.annotations.Test;
import org.testng.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.util.Set;

public class OpenEchartTest extends BaseTest {

    // Test the login successful scenario
    @Test
    public void testOpenEchartSuccessful() {
        WebDriver driver = createHeadlessDriver();
        WebDriverWait wait = createWait(driver);

        try {
            // Login
            login(driver, wait);
            Assert.assertTrue(isLoginSuccessful(driver));
            System.out.println("Login successful, the current URL is: " + driver.getCurrentUrl());

            // Click on the 'Search' tab on the menu bar
            WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
            searchTab.click();
            System.out.println("Search demographic page open successful.");

            // Store the current window handle
            String mainWindowHandle = driver.getWindowHandle();

            // Switch to the search demographic window
            wait.until(ExpectedConditions.numberOfWindowsToBe(2));
            Set<String> allWindowHandles = driver.getWindowHandles();
            for (String windowHandle : allWindowHandles) {
                if (!windowHandle.equals(mainWindowHandle)) {
                    driver.switchTo().window(windowHandle);
                    break;
                }
            }

            // Ensure the URL of the search demographic window is correct
            wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));
            System.out.println("Location of the search demographic window is: " + driver.getCurrentUrl());

            // Enter "%" in the search bar and press Enter to get all patients
            WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
            searchBar.sendKeys("%");
            searchBar.sendKeys(Keys.RETURN);
            System.out.println("Patients are listed on page " + driver.getCurrentUrl());
        } finally {
            driver.quit();
        }
    }
}
