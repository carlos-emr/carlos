package tests;

import org.testng.annotations.Test;
import org.testng.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class LoginTest {
    
    // Test the login successful scenario
    @Test
    public void testLoginSuccessful() {

        // Open the login page
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

        // Close the browser
        driver.quit();

    }

    // Test the login failed scenario - invalid username
    @Test
    public void testLoginFailedWithInvalidUsername() {

        // Open the login page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver");
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the invalid username and password
        usernameField.sendKeys("oscardoctor");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login failed
        String currentUrl = driver.getCurrentUrl();
        Assert.assertFalse(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login failed with invalid username.");

        // Close the browser
        driver.quit();

    }

    // Test the login failed scenario - invalid password
    @Test
    public void testLoginFailedWithInvalidPassword() {

        // Open the login page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver");
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and invalid password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login failed
        String currentUrl = driver.getCurrentUrl();
        Assert.assertFalse(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login failed with invalid passowrd.");   

        // Close the browser
        driver.quit();

    }

    // Test the login failed scenario - invalid username and password
    @Test
    public void testLoginFailedWithInvalidUsernameAndPassword() {

        // Open the login page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver");
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the invalid username and invalid password
        usernameField.sendKeys("oscardoctor");
        passwordField.sendKeys("mac2002");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login failed
        String currentUrl = driver.getCurrentUrl();
        Assert.assertFalse(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login failed with invalid username and passowrd.");   

        // Close the browser
        driver.quit();

    }
    
}