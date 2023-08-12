package veryspicyheatwave.bwb_datascraper;

import java.util.logging.Level;
import java.util.logging.Logger;

// Selenium Dependencies
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;

public class ThriftBooks_DataScraper
{    
    static String BASE_URL = "https://www.thriftbooks.com";    
    static String GECKO_DRIVER_PATH = "C:/Users/smash/Downloads/geckodriver-v0.33.0-win64/geckodriver.exe";
    
    static WebDriver getFFXDriver()
    {
        System.setProperty("webdriver.gecko.driver",GECKO_DRIVER_PATH);        
        FirefoxOptions ffxOptions = new FirefoxOptions();
        FirefoxProfile profile = new FirefoxProfile();        
        ffxOptions.setCapability(FirefoxDriver.PROFILE, profile);
        WebDriver driver = new FirefoxDriver();
        return driver;
    }
    
    static void BypassWebroot(WebDriver driver)
    {
        
        //Click the "Allow" button on my WebRoot blocker
        try
        {
            Thread.sleep(2000);
            WebElement allowBTN = driver.findElement(By.id("allowButton"));
            allowBTN.click();
        }
        catch (InterruptedException ex)
        {
            Logger.getLogger(ThriftBooks_DataScraper.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    static void ScrapePages(WebDriver driver, int numberOfPages)    
    {        
        //Navigate the webpage to the URL in question 
        driver.navigate().to(BASE_URL + "/browse");
        driver.manage().window().maximize();
        
        for (int i = 1; i < numberOfPages + 1; i++)
        {
            try
            {
                String urlToScrape = "https://www.thriftbooks.com/browse/#b.s=mostPopular-desc&b.p=" + i + "&b.pp=30&b.oos";
                
                driver.navigate().to(urlToScrape);
                Thread.sleep(1500);
            }
            catch (InterruptedException ex)
            {
                Logger.getLogger(ThriftBooks_DataScraper.class.getName()).log(Level.SEVERE, null, ex);
            }
            
        }
    }
    
    public static void main(String[] args)
    {   
        //Set up the FirefoxDriver object
        WebDriver driver = getFFXDriver();        
        BypassWebroot(driver);
        ScrapePages(driver, 2);       

    }
}
