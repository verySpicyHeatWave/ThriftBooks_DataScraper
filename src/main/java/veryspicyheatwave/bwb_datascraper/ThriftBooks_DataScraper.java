/*
============================================================ TO-DO LIST ============================================================

1) Get rid of the "priceRange" string and instead have a string each for "New" and "Used" priced pulled off of each book's page.
2) Refactor the code a little bit--the ScrapePages method could be broken down into at least these two parts:
    a) A method for scraping the full catalog pages for the basic book data
    b) A method for scraping each book page for the more specific book data
3) Start thinking about how to spit this out into a real SQL database, or at the very least into a goddamn .csv file.
4) Clean up the BookEntry class with some getters and setters. Yeah, it's a lot, but if I'm doing OOP then that's the right
        thing to do. This ain't a fucking struct (wish it was though).
5) OPTIONAL: Consider doing away with the "Thread.sleep" calls littered throughout and figure out a more majestic way to
        wait for shit to happen with Selenium driver methods. It's a bit, err, brute force, right?

====================================================================================================================================
*/


/*  
    BCOBB NOTE:     The following little block of code was used to click the "Deny" button on the pop-up banner.
                    I don't think I need this anymore but I'm keeping it just in case I do.

    driver.findElement(By.cssSelector("button[class=' osano-cm-deny osano-cm-buttons__button osano-cm-button osano-cm-button--type_deny '")).click();
    Thread.sleep(500);     
    System.out.println("Clicked the \"Deny\" button on the pop-up banner!");        
    WebDriverWait wait = new WebDriverWait(driver, 10);        
*/

package veryspicyheatwave.bwb_datascraper;

import java.util.ArrayList;
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
    final static String BASE_URL = "https://www.thriftbooks.com";    
    final static String GECKO_DRIVER_PATH = "C:/Users/smash/Downloads/geckodriver-v0.33.0-win64/geckodriver.exe";
    
    public static void main(String[] args) throws InterruptedException
    {
        WebDriver driver = getFFXDriver();        
        BypassWebroot(driver);        
        
        try
        {
            Thread.sleep(1000);
        }
        catch (InterruptedException ex)
        {
            Logger.getLogger(ThriftBooks_DataScraper.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        ScrapePages(driver, 2);
    }
    
    static WebDriver getFFXDriver()
    {
        System.setProperty("webdriver.gecko.driver",GECKO_DRIVER_PATH);
        FirefoxOptions ffxOptions = new FirefoxOptions();
        FirefoxProfile profile = new FirefoxProfile();        
        ffxOptions.setCapability(FirefoxDriver.PROFILE, profile);
        WebDriver driver = new FirefoxDriver();
        System.out.println("Created the new web driver");
        return driver;
    }
    
    static void BypassWebroot(WebDriver driver)
    {
        try
        {
            Thread.sleep(2000);
            WebElement allowBTN = driver.findElement(By.id("allowButton"));
            allowBTN.click();
            System.out.println("Bypassed the Webroot filter page");
        }
        catch (InterruptedException ex)
        {
            Logger.getLogger(ThriftBooks_DataScraper.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    static void ScrapePages(WebDriver driver, int numberOfPages) throws InterruptedException    
    {
        ArrayList<BookEntry> bookList = new ArrayList<>();
        
        for (int pageNo = 1; pageNo < numberOfPages + 1; pageNo++)
        {
            
            driver.navigate().to("about:blank");
            Thread.sleep(500);
            String pageURL = BASE_URL + "/browse/#b.s=mostPopular-desc&b.p=" + pageNo + "&b.pp=30&b.oos";
            driver.navigate().to(pageURL);
            Thread.sleep(1500);

            
            List<WebElement> books = driver.findElements(By.tagName("a"));
            System.out.println("Got the list of books from page " + pageNo);
            for (WebElement book : books)
            {
                BookEntry tempBook = new BookEntry();
                boolean isABook = false;
                
                List<WebElement> details = book.findElements(By.tagName("p"));
                int counter = 1;
                for (WebElement detail : details)
                {
                    if (detail.getText().contains("from:"))
                    {
                        break;
                    }
                    switch (counter)
                    {   // Rule switch? Great suggestion, IDE!
                        case 1 -> tempBook.title = detail.getText();
                        case 2 -> tempBook.author = detail.getText();
                        case 3 -> tempBook.priceRange = detail.getText();
                    }
                    counter++;
                    isABook = true;
                }
                
                if (isABook)
                {
                    tempBook.link = book.getAttribute("href");
                    bookList.add(tempBook);
                }                
            }
        }
        
        for (BookEntry book : bookList)
        {
            driver.navigate().to(book.link);
            Thread.sleep(750);
            
            List<WebElement> details = driver.findElements(By.tagName("span"));
            int index = -1;
            
            for (WebElement detail : details)
            {
                index++;
                if (detail.getText().toLowerCase().contains("isbn"))
                {
                    book.isbnCode = details.get(index + 1).getText();
                    continue;
                }
                if (detail.getText().toLowerCase().contains("release"))
                {
                    book.releaseDate = details.get(index + 1).getText();
                    continue;
                }
                if (detail.getText().toLowerCase().contains("length"))
                {
                    book.pageLength = details.get(index + 1).getText();
                    continue;
                }
                if (detail.getText().toLowerCase().contains("language"))
                {
                    book.language = details.get(index + 1).getText();
                    continue;
                }
                if (detail.getAttribute("itemprop") != null && book.genre == null && detail.getAttribute("itemprop").toLowerCase().contains("name"))
                {
                    book.genre = detail.getText();
                }
            }
            
            //BCOBB: Delete this once I get this data spit into some sort of file.
            System.out.println(book.title);
            System.out.println(book.author);
            System.out.println(book.priceRange);
            System.out.println(book.link);
            System.out.println(book.isbnCode);
            System.out.println(book.releaseDate);
            System.out.println(book.pageLength);
            System.out.println(book.language);
            System.out.println(book.genre);
            System.out.println("");
            
            Thread.sleep(250);
        }
        System.out.println("I got " + bookList.size() + " books!");
    }
}


class BookEntry
{
    String title;
    String author;
    String priceRange;
    String link;
    String isbnCode;
    String releaseDate;
    String pageLength;
    String language;
    String genre;
    
    
    BookEntry (){}    
    
    
    BookEntry (String title, String author, String link)
    {
        this.title = title;
        this.author = author;
        this.link = link;
    }    
    
    BookEntry (String title, String author, String priceRange, String link)
    {
        this.title = title;
        this.author = author;
        this.priceRange = priceRange;
        this.link = link;
    }    
}