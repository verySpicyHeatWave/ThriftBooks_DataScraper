package veryspicyheatwave.bwb_datascraper;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

// Selenium Dependencies
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;


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
    
    static void ScrapePages(WebDriver driver, int numberOfPages) throws InterruptedException    
    {        
        
        //Navigate the webpage to the URL in question 
        driver.navigate().to(BASE_URL + "/browse");
        Thread.sleep(500);
        
        //Click the DENY button for cookies to get the banner out of the way
        driver.findElement(By.cssSelector("button[class=' osano-cm-deny osano-cm-buttons__button osano-cm-button osano-cm-button--type_deny '")).click();
        Thread.sleep(500);     
        
        WebDriverWait wait = new WebDriverWait(driver, 10);
        
            
        
        for (int i = 1; i < numberOfPages + 1; i++)
        {
        Thread.sleep(500);
            /*             
            PSEUDOCODE:
                For each page
                    Get a list of all of the book entries <a class="SearchResultGridItem undefined" ...>
                    For each book
                        Get the title <p class="SearchResultGridItem-title">
                        Get the author <p class="SearchResultGridItem-author">
                        Get the price range <p class="SearchResultGridItem-price">
                        Get the link <a class attribute href="<LINK>"....
                        Navigate to the link
                        Wait half a second (necessary??)
                        Get some other crap I haven't really determined yet
                            --Book Image Link?
                            --Book Genre?
                            --Book Review Rating?
                            --New book price
                            --Used book price
                        Navigate back
                        Wait half a second (necessary??)                
            */

            
            List<WebElement> books = driver.findElements(By.tagName("a"));
            for (WebElement book : books)
            {
                boolean isABook = false;
                
                List<WebElement> details = book.findElements(By.tagName("p"));                
                for (WebElement detail : details)
                {
                    if (detail.getText().contains("from:"))
                    {
                        break;
                    }
                    System.out.println(detail.getText());
                    isABook = true;
                }
                
                if (isABook)
                {
                    System.out.println(book.getAttribute("href"));
                    System.out.println("");
                }
            }
            
            
            // Click the "Next Page" button
            WebElement nextPageButton = driver.findElement(By.cssSelector("button[class='Pagination-link is-right is-link'"));
            if (nextPageButton != null)
            {
                wait.until(ExpectedConditions.visibilityOf(nextPageButton));
                nextPageButton.sendKeys("don't accept these keys");
                try
                {
                    nextPageButton.click();                    
                }
                catch (StaleElementReferenceException SERE)
                {
                    SERE.printStackTrace();
                    continue;
                }
            }
        }
    }
    
    public static void main(String[] args) throws InterruptedException
    {   
        //Set up the FirefoxDriver object
        WebDriver driver = getFFXDriver();        
        BypassWebroot(driver);        
        
        
        //Wait a little bit before moving on
        try
        {
            Thread.sleep(1500);
        }
        catch (InterruptedException ex)
        {
            Logger.getLogger(ThriftBooks_DataScraper.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        ScrapePages(driver, 2);

    }
}
