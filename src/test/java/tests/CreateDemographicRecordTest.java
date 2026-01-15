package tests;

import org.testng.annotations.Test;
import org.testng.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.util.Set;

public class CreateDemographicRecordTest {

    // Test the create demographic record successful scenario
    @Test
    public void createDemographicRecordSuccessful() throws InterruptedException {

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

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
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

        // Click the "All" button to get the patients listed
        WebElement allButton = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//input[@type='button' and @value='All']")));
        allButton.click();

        // Click the "Create Demographic" button to open the create new demographic record window
        WebElement createDemographicButton = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".createNew a[title]")));
        createDemographicButton.click();
        Assert.assertTrue(driver.getCurrentUrl().contains("demographic/demographicaddarecordhtm.jsp"));
        System.out.println("Create demographic window opened successfully, the current URL is: " + driver.getCurrentUrl());

        // Fill out the form fields to create a new demographic record
        WebElement firstNameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("first_name")));
        WebElement lastNameField = driver.findElement(By.name("last_name"));
        WebElement dobYearField = driver.findElement(By.name("year_of_birth"));

        // Select dropdown options
        Select titleSelect = new Select(driver.findElement(By.name("title")));
        titleSelect.selectByVisibleText("Ms");
        Select genderSelect = new Select(driver.findElement(By.name("sex")));
        genderSelect.selectByVisibleText("Female");

        firstNameField.sendKeys("Sophia");
        lastNameField.sendKeys("White");
        dobYearField.sendKeys("1999");

        // Submit the form to create the demographic record
        WebElement saveButton = driver.findElement(By.cssSelector("input[type='submit'][value='Add Record']"));
        saveButton.click();

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while saving master record.");
                Assert.fail("500 error encountered while saving master record.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            Assert.assertTrue(driver.getCurrentUrl().contains("demographic/demographicaddarecord.jsp"));
            System.out.println("Demographic record created successfully.");
        }

        // Display the confirmation page for 2 sec
        Thread.sleep(2000);

        // Locate and click the "Go to Record" button
        WebElement goToRecordButton = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@href, 'demographiccontrol.jsp?demographic_no=')]")));
        goToRecordButton.click();

        // Check for encountering 500 error
        is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while returning to master record.");
                Assert.fail("500 error encountered while returning to master record.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            Assert.assertTrue(driver.getCurrentUrl().contains("demographic/demographiccontrol.jsp"));
            System.out.println("Navigated to the demographic record successfully.");
        }

        // Display the master record for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }
}
