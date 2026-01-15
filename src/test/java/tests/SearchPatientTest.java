package tests;

import org.testng.annotations.Test;
import org.testng.Assert;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.util.Set;
import java.util.List;

public class SearchPatientTest {

    // Test the open all echarts successful scenario
    @Test
    public void testOpenAllEchartsSuccessful() throws InterruptedException {

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

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Iterate through all pages and open each patient's e-chart
        boolean hasNextPage = true;
        while (hasNextPage) {
            // Locate the patient results table
            WebElement patientResultsTable = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("patientResults")));
            List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

            // Iterate over each row and open the e-chart
            for (WebElement row : rows) {
                List<WebElement> cells = row.findElements(By.tagName("td"));
                if (cells.size() > 0) {
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();

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
                    wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));

                    // Check for encountering error page
                    boolean isErrorMsg = false;
                    try {
                        WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'An Error has occurred in this application.')]"));
                        if (errorElement.isDisplayed()) {
                            isErrorMsg = true;
                            System.out.println("Error encountered while opening echart.");
                            Assert.fail("Error encountered while opening echart.");
                        }
                    } catch (NoSuchElementException e) {
                        System.out.println("No error encountered.");
                    }

                    // Check for encountering 500 error
                    boolean is500Error = false;
                    try {
                        WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
                        if (errorElement.isDisplayed()) {
                            is500Error = true;
                            System.out.println("500 error encountered while opening echart.");
                            Assert.fail("500 error encountered while opening echart.");
                        }
                    } catch (NoSuchElementException e) {
                        System.out.println("No 500 error encountered.");
                    }

                    if (!isErrorMsg && !is500Error) {
                        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
                        System.out.println("Echart opened successfully for patient.");
                    }

                    // Handle the popup asking to keep editing
                    try {
                        Alert alert = wait.until(ExpectedConditions.alertIsPresent());
                        alert.accept();
                    } catch (TimeoutException e) {
                        System.out.println("No alert present.");
                    }

                    // Display the eChart for 2 sec
                    Thread.sleep(2000);

                    // Close the eChart window and switch back to the demographic search window
                    driver.close();
                    driver.switchTo().window(demographicSearchWindowHandle);
                }
            }

            // Check if there is a next page
            List<WebElement> nextPageButtons = driver.findElements(By.xpath("//a[contains(text(), 'Next')]"));
            if (nextPageButtons.size() > 0) {
                nextPageButtons.get(0).click();
                wait.until(ExpectedConditions.stalenessOf(patientResultsTable));
            } else {
                hasNextPage = false;
            }
        }

        // Close the browser
        driver.quit();

    }

    // Test the open all master records successful scenario
    @Test
    public void testOpenAllMasterRecordsSuccessful() throws InterruptedException {

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

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        boolean hasNextPage = true;

        while (hasNextPage) {
            // Locate the patient results table
            WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
            List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

            // Iterate through each row to open the master record for each patient
            for (WebElement row : rows) {
                List<WebElement> cells = row.findElements(By.tagName("td"));
                if (cells.size() > 0) {
                    WebElement masterRecordButton = row.findElement(By.cssSelector("a[title='Master Demographic File']"));
                    masterRecordButton.click();

                    // Switch to the master record window
                    wait.until(ExpectedConditions.numberOfWindowsToBe(3));
                    allWindowHandles = driver.getWindowHandles();
                    String masterRecordWindowHandle = null;
                    for (String windowHandle : allWindowHandles) {
                        if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                            masterRecordWindowHandle = windowHandle;
                            driver.switchTo().window(windowHandle);
                            if (driver.getCurrentUrl().contains("search_detail")) {
                                break;
                            }
                        }
                    }
                    wait.until(ExpectedConditions.urlContains("search_detail"));

                    // Check for encountering 500 error
                    boolean is500Error = false;
                    try {
                        WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
                        if (errorElement.isDisplayed()) {
                            is500Error = true;
                            System.out.println("500 error encountered while opening master record.");
                            Assert.fail("500 error encountered while opening master record.");
                        }
                    } catch (NoSuchElementException e) {
                        System.out.println("No 500 error encountered.");
                    }

                    if (!is500Error) {
                        Assert.assertTrue(driver.getCurrentUrl().contains("search_detail"));
                        System.out.println("Master record opened successfully for patient.");
                    }

                    // Display the master record for 2 sec
                    Thread.sleep(2000);

                    // Click the "Exit Master Record" button
                    WebElement exitButton = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("cancelButton")));
                    exitButton.click();

                    // Switch back to the demographic search window
                    wait.until(ExpectedConditions.numberOfWindowsToBe(2));
                    driver.switchTo().window(demographicSearchWindowHandle);
                }
            }

            // Check if there's a "Next" button for pagination
            List<WebElement> nextButtons = driver.findElements(By.xpath("//a[contains(text(), 'Next')]"));
            if (nextButtons.size() > 0) {
                WebElement nextPageButton = nextButtons.get(0);
                nextPageButton.click();
                wait.until(ExpectedConditions.stalenessOf(patientResultsTable)); 
            } else {
                hasNextPage = false;
            }
        }

        // Close the browser
        driver.quit();
    
    }

    // Test the search patient by clicking "All" button successful scenario
    @Test
    public void testSearchPatientByAllButtonSuccessful() throws InterruptedException {

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

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while searching.");
                Assert.fail("500 error encountered while searching.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            Assert.assertTrue(driver.getCurrentUrl().contains("demographic/demographiccontrol.jsp"));
            System.out.println("Clicked the 'All' button to list most recent three patients.");
        }

        // Display the patients search result for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open create demographic window successful scenario
    @Test
    public void testOpenCreateDemographicWindowSuccessful() throws InterruptedException {

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

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening create demographic window.");
                Assert.fail("500 error encountered while opening create demographic window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            Assert.assertTrue(driver.getCurrentUrl().contains("demographic/demographicaddarecordhtm.jsp"));
            System.out.println("Create demographic window opened successfully, the current URL is: " + driver.getCurrentUrl());
        }

        // Display the create demographic window for 2 sec
        Thread.sleep(2000);
        
        // Close the browser
        driver.quit();

    }

}