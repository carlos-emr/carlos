package tests;

import org.testng.annotations.Test;
import org.testng.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.util.Set;

public class OpenAdminPanelTest {
    
    // Test the open admin panel successful scenario
    @Test
    public void openAdminPanelSuccessful() throws InterruptedException {

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

        // Click on the 'Administration' tab on the menu bar
        WebElement adminTab = wait.until(ExpectedConditions.elementToBeClickable(By.id("admin-panel")));
        adminTab.click();

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Wait for the new window to open and switch to it
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String adminWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                adminWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening administration panel.");
                Assert.fail("500 error encountered while opening administration panel.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("/administration/"));
            Assert.assertTrue(driver.getCurrentUrl().contains("/administration/"));
            System.out.println("Administration panel opened successfully.");
        }

        // Display the admin panel for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open unlock account window successful scenario
    @Test
    public void testOpenUnlockAccountWindowSuccessful() throws InterruptedException {

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

        // Click on the 'Administration' tab on the menu bar
        WebElement adminTab = wait.until(ExpectedConditions.elementToBeClickable(By.id("admin-panel")));
        adminTab.click();

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Wait for the new window to open and switch to it
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String adminWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                adminWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Click on the "Unlock Account" tab
        WebElement unlockAccountTab = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("div.well.quick-links a[rel$='unLock.jsp']")));
        unlockAccountTab.click();

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening unlock account window.");
                Assert.fail("500 error encountered while opening unlock account window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            Assert.assertTrue(driver.getCurrentUrl().contains("/administration/"));
            System.out.println("'Unlock Account' window opened successfully.");
        }
        
        // Display the admin panel for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open add providers window successful scenario
    @Test
    public void testOpenAddProviderWindowSuccessful() throws InterruptedException {

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

        // Click on the 'Administration' tab on the menu bar
        WebElement adminTab = wait.until(ExpectedConditions.elementToBeClickable(By.id("admin-panel")));
        adminTab.click();

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Wait for the new window to open and switch to it
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String adminWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                adminWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Click on the "Add a Provider" tab
        WebElement addProviderTab = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("div.well.quick-links a[rel$='provideraddarecordhtm.jsp']")));
        addProviderTab.click();

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening add providers window.");
                Assert.fail("500 error encountered while opening add providers window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            Assert.assertTrue(driver.getCurrentUrl().contains("/administration/"));
            System.out.println("'Add a Provider' window opened successfully.");
        }
        
        // Display the admin panel for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open add login record window successful scenario
    @Test
    public void testOpenAddLoginRecordWindowSuccessful() throws InterruptedException {

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

        // Click on the 'Administration' tab on the menu bar
        WebElement adminTab = wait.until(ExpectedConditions.elementToBeClickable(By.id("admin-panel")));
        adminTab.click();

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Wait for the new window to open and switch to it
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String adminWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                adminWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Click on the "Add a Login Record" tab
        WebElement addLoginRecordTab = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("div.well.quick-links a[rel$='securityaddarecord.jsp']")));
        addLoginRecordTab.click();

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening add login record window.");
                Assert.fail("500 error encountered while opening add login record window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            Assert.assertTrue(driver.getCurrentUrl().contains("/administration/"));
            System.out.println("'Add a Login Record' window opened successfully.");
        }
        
        // Display the admin panel for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open manage eforms window successful scenario
    @Test
    public void testOpenManageEformsWindowSuccessful() throws InterruptedException {

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

        // Click on the 'Administration' tab on the menu bar
        WebElement adminTab = wait.until(ExpectedConditions.elementToBeClickable(By.id("admin-panel")));
        adminTab.click();

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Wait for the new window to open and switch to it
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String adminWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                adminWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Click on the "Manage eForms" tab
        WebElement manageEformsTab = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//div[@class='well quick-links']//a[contains(@href, '/eform/efmformmanager.jsp')]")));
        manageEformsTab.click();

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening manage eforms window.");
                Assert.fail("500 error encountered while opening manage eforms window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            Assert.assertTrue(driver.getCurrentUrl().contains("/administration/"));
            System.out.println("'Manage eForms' window opened successfully.");
        }
        
        // Display the admin panel for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open schedule setting window successful scenario
    @Test
    public void testOpenScheduleSettingWindowSuccessful() throws InterruptedException {

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

        // Click on the 'Administration' tab on the menu bar
        WebElement adminTab = wait.until(ExpectedConditions.elementToBeClickable(By.id("admin-panel")));
        adminTab.click();

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Wait for the new window to open and switch to it
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String adminWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                adminWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Click on the "Schedule Setting" tab
        WebElement scheduleSettingTab = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("div.well.quick-links a[rel$='scheduletemplatesetting.jsp']")));
        scheduleSettingTab.click();

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening schedule setting window.");
                Assert.fail("500 error encountered while opening schedule setting window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            Assert.assertTrue(driver.getCurrentUrl().contains("/administration/"));
            System.out.println("'Schedule Setting' window opened successfully.");
        }
        
        // Display the admin panel for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open search groups window window successful scenario
    @Test
    public void testOpenSearchGroupsWindowSuccessful() throws InterruptedException {

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

        // Click on the 'Administration' tab on the menu bar
        WebElement adminTab = wait.until(ExpectedConditions.elementToBeClickable(By.id("admin-panel")));
        adminTab.click();

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Wait for the new window to open and switch to it
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String adminWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                adminWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Click on the "Search/Edit/Delete Groups" tab
        WebElement searchGroupsTab = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("div.well.quick-links a[rel$='admindisplaymygroup.jsp']")));
        searchGroupsTab.click();

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening search groups window.");
                Assert.fail("500 error encountered while opening search groups window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            Assert.assertTrue(driver.getCurrentUrl().contains("/administration/"));
            System.out.println("'Search/Edit/Delete Groups' window opened successfully.");
        }
        
        // Display the admin panel for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }
    
    // Test the open insert template window successful scenario
    @Test
    public void testOpenInsertTemplateWindowSuccessful() throws InterruptedException {

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

        // Click on the 'Administration' tab on the menu bar
        WebElement adminTab = wait.until(ExpectedConditions.elementToBeClickable(By.id("admin-panel")));
        adminTab.click();

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Wait for the new window to open and switch to it
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String adminWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                adminWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Click on the "Insert a Template" tab
        WebElement insertTemplateTab = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("div.well.quick-links a[rel$='providertemplate.jsp']")));
        insertTemplateTab.click();

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening insert template window.");
                Assert.fail("500 error encountered while opening insert template window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            Assert.assertTrue(driver.getCurrentUrl().contains("/administration/"));
            System.out.println("'Insert a Template' window opened successfully.");
        }
        
        // Display the admin panel for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open assign role window successful scenario
    @Test
    public void testAssignRoleWindowSuccessful() throws InterruptedException {

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

        // Click on the 'Administration' tab on the menu bar
        WebElement adminTab = wait.until(ExpectedConditions.elementToBeClickable(By.id("admin-panel")));
        adminTab.click();

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Wait for the new window to open and switch to it
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String adminWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                adminWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Click on the "Assign Role/Rights to Object" tab
        WebElement assignRoleTab = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("div.well.quick-links a[rel$='providerPrivilege.jsp']")));
        assignRoleTab.click();

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening assign role window.");
                Assert.fail("500 error encountered while opening assign role window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            Assert.assertTrue(driver.getCurrentUrl().contains("/administration/"));
            System.out.println("'Assign Role/Rights to Object' window opened successfully.");
        }
        
        // Display the admin panel for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

}
