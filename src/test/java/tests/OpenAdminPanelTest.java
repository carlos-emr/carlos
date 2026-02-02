package tests;

import org.testng.annotations.Test;
import org.testng.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.util.Set;

public class OpenAdminPanelTest extends BaseTest {

    /**
     * Helper method to login and open the admin panel.
     * Returns the admin window handle for further navigation.
     */
    private String openAdminPanel(WebDriver driver, WebDriverWait wait) {
        // Login
        login(driver, wait);
        Assert.assertTrue(isLoginSuccessful(driver));
        System.out.println("Login successful, the current URL is: " + driver.getCurrentUrl());

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

        return adminWindowHandle;
    }

    /**
     * Helper method to check for 500 error.
     */
    private void check500Error(WebDriver driver, String context) {
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                System.out.println("500 error encountered while " + context + ".");
                Assert.fail("500 error encountered while " + context + ".");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }
    }

    // Test the open admin panel successful scenario
    @Test
    public void openAdminPanelSuccessful() throws InterruptedException {
        WebDriver driver = createHeadlessDriver();
        WebDriverWait wait = createWait(driver);

        try {
            openAdminPanel(driver, wait);

            check500Error(driver, "opening administration panel");

            wait.until(ExpectedConditions.urlContains("/administration/"));
            Assert.assertTrue(driver.getCurrentUrl().contains("/administration/"));
            System.out.println("Administration panel opened successfully.");

            // Display the admin panel for 2 sec
            Thread.sleep(2000);
        } finally {
            driver.quit();
        }
    }

    // Test the open unlock account window successful scenario
    @Test
    public void testOpenUnlockAccountWindowSuccessful() throws InterruptedException {
        WebDriver driver = createHeadlessDriver();
        WebDriverWait wait = createWait(driver);

        try {
            openAdminPanel(driver, wait);

            // Click on the "Unlock Account" tab
            WebElement unlockAccountTab = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("div.well.quick-links a[rel$='unLock.jsp']")));
            unlockAccountTab.click();

            check500Error(driver, "opening unlock account window");

            Assert.assertTrue(driver.getCurrentUrl().contains("/administration/"));
            System.out.println("'Unlock Account' window opened successfully.");

            // Display the admin panel for 2 sec
            Thread.sleep(2000);
        } finally {
            driver.quit();
        }
    }

    // Test the open add providers window successful scenario
    @Test
    public void testOpenAddProviderWindowSuccessful() throws InterruptedException {
        WebDriver driver = createHeadlessDriver();
        WebDriverWait wait = createWait(driver);

        try {
            openAdminPanel(driver, wait);

            // Click on the "Add a Provider" tab
            WebElement addProviderTab = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("div.well.quick-links a[rel$='provideraddarecordhtm.jsp']")));
            addProviderTab.click();

            check500Error(driver, "opening add providers window");

            Assert.assertTrue(driver.getCurrentUrl().contains("/administration/"));
            System.out.println("'Add a Provider' window opened successfully.");

            // Display the admin panel for 2 sec
            Thread.sleep(2000);
        } finally {
            driver.quit();
        }
    }

    // Test the open add login record window successful scenario
    @Test
    public void testOpenAddLoginRecordWindowSuccessful() throws InterruptedException {
        WebDriver driver = createHeadlessDriver();
        WebDriverWait wait = createWait(driver);

        try {
            openAdminPanel(driver, wait);

            // Click on the "Add a Login Record" tab
            WebElement addLoginRecordTab = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("div.well.quick-links a[rel$='securityaddarecord.jsp']")));
            addLoginRecordTab.click();

            check500Error(driver, "opening add login record window");

            Assert.assertTrue(driver.getCurrentUrl().contains("/administration/"));
            System.out.println("'Add a Login Record' window opened successfully.");

            // Display the admin panel for 2 sec
            Thread.sleep(2000);
        } finally {
            driver.quit();
        }
    }

    // Test the open manage eforms window successful scenario
    @Test
    public void testOpenManageEformsWindowSuccessful() throws InterruptedException {
        WebDriver driver = createHeadlessDriver();
        WebDriverWait wait = createWait(driver);

        try {
            openAdminPanel(driver, wait);

            // Click on the "Manage eForms" tab
            WebElement manageEformsTab = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//div[@class='well quick-links']//a[contains(@href, '/eform/efmformmanager.jsp')]")));
            manageEformsTab.click();

            check500Error(driver, "opening manage eforms window");

            Assert.assertTrue(driver.getCurrentUrl().contains("/administration/"));
            System.out.println("'Manage eForms' window opened successfully.");

            // Display the admin panel for 2 sec
            Thread.sleep(2000);
        } finally {
            driver.quit();
        }
    }

    // Test the open schedule setting window successful scenario
    @Test
    public void testOpenScheduleSettingWindowSuccessful() throws InterruptedException {
        WebDriver driver = createHeadlessDriver();
        WebDriverWait wait = createWait(driver);

        try {
            openAdminPanel(driver, wait);

            // Click on the "Schedule Setting" tab
            WebElement scheduleSettingTab = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("div.well.quick-links a[rel$='scheduletemplatesetting.jsp']")));
            scheduleSettingTab.click();

            check500Error(driver, "opening schedule setting window");

            Assert.assertTrue(driver.getCurrentUrl().contains("/administration/"));
            System.out.println("'Schedule Setting' window opened successfully.");

            // Display the admin panel for 2 sec
            Thread.sleep(2000);
        } finally {
            driver.quit();
        }
    }

    // Test the open search groups window window successful scenario
    @Test
    public void testOpenSearchGroupsWindowSuccessful() throws InterruptedException {
        WebDriver driver = createHeadlessDriver();
        WebDriverWait wait = createWait(driver);

        try {
            openAdminPanel(driver, wait);

            // Click on the "Search/Edit/Delete Groups" tab
            WebElement searchGroupsTab = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("div.well.quick-links a[rel$='admindisplaymygroup.jsp']")));
            searchGroupsTab.click();

            check500Error(driver, "opening search groups window");

            Assert.assertTrue(driver.getCurrentUrl().contains("/administration/"));
            System.out.println("'Search/Edit/Delete Groups' window opened successfully.");

            // Display the admin panel for 2 sec
            Thread.sleep(2000);
        } finally {
            driver.quit();
        }
    }

    // Test the open insert template window successful scenario
    @Test
    public void testOpenInsertTemplateWindowSuccessful() throws InterruptedException {
        WebDriver driver = createHeadlessDriver();
        WebDriverWait wait = createWait(driver);

        try {
            openAdminPanel(driver, wait);

            // Click on the "Insert a Template" tab
            WebElement insertTemplateTab = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("div.well.quick-links a[rel$='providertemplate.jsp']")));
            insertTemplateTab.click();

            check500Error(driver, "opening insert template window");

            Assert.assertTrue(driver.getCurrentUrl().contains("/administration/"));
            System.out.println("'Insert a Template' window opened successfully.");

            // Display the admin panel for 2 sec
            Thread.sleep(2000);
        } finally {
            driver.quit();
        }
    }

    // Test the open assign role window successful scenario
    @Test
    public void testAssignRoleWindowSuccessful() throws InterruptedException {
        WebDriver driver = createHeadlessDriver();
        WebDriverWait wait = createWait(driver);

        try {
            openAdminPanel(driver, wait);

            // Click on the "Assign Role/Rights to Object" tab
            WebElement assignRoleTab = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("div.well.quick-links a[rel$='providerPrivilege.jsp']")));
            assignRoleTab.click();

            check500Error(driver, "opening assign role window");

            Assert.assertTrue(driver.getCurrentUrl().contains("/administration/"));
            System.out.println("'Assign Role/Rights to Object' window opened successfully.");

            // Display the admin panel for 2 sec
            Thread.sleep(2000);
        } finally {
            driver.quit();
        }
    }

}
