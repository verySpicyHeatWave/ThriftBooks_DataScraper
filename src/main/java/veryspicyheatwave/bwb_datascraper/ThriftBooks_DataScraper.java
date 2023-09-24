/*
============================================================ TO-DO LIST ============================================================

1) ***DONE***Get rid of the "priceRange" string and instead have a string each for "New" and "Used" priced pulled off of each book's page.
2) ***DONE*** Refactor the code a little bit--the ScrapePages method could be broken down into at least these two parts:
    a) ***DONE*** A method for scraping the full catalog pages for the basic book data
    b) ***DONE*** A method for scraping each book page for the more specific book data
3) Start thinking about how to spit this out into a real SQL database, or at the very least into a goddamn .csv file.
4) ***SCRAP*** Clean up the BookEntry class with some getters and setters. Yeah, it's a lot, but if I'm doing OOP then that's the right
        thing to do. This ain't a struct (wish it was though). **EDIT: This is a waste of time and code. I'm treating this
        thing like a struct and that's just the way it is.
5) ***DONE*** Put numerical values into numerical data types and use those instead of the String that we extract from the browser
    a) ***DONE*** New and Used price variables should be doubles
    b) ***DONE*** ReleaseDate string should be some sort of "date and time" data type (gotta look into that)
    c) ***DONE*** Page length should be an int
6) Add the image link to the BookEntry class as a String, and then figure out a way to download the image and include it
        the database. I think that's possible? I want the book images.
7) ***DONE*** Just learned a spicy new trick for how to find elements by their xpath and to sift through their child elements. I need to
        refactor the code to use this everywhere, I think it will save a lot of time since right now I'm just searching through
        every element on the page for what I want. I have the ability to narrow that down. DO IT.
8) ***DONE*** Put in a timer so that the duration of the scraping can be measured.
9) OPTIONAL: Consider doing away with the "Thread.sleep" calls littered throughout and figure out a more majestic way to
        wait for shit to happen with Selenium driver methods. It's a bit, err, brute force, right?
10) ***DONE*** OPTIONAL: Since the full scraping might take a while, maybe it'd be wise to create a CLI input that prompts for the start
        page and the number of pages. That way, I could run this bad boy for six hours at a time and just build a totally
        massive database over the course of a week just running Selenium at night, scraping away. Maybe?

====================================================================================================================================
*/


/*
    BCOBB NOTE:     The following dependencies aren't being used. I'm keeping the copied here for now but I'll
                    probably delete them unless I need them later.

    import org.openqa.selenium.interactions.Actions;
    import org.openqa.selenium.JavascriptExecutor;
    import org.openqa.selenium.StaleElementReferenceException;
    import org.openqa.selenium.support.ui.ExpectedConditions;
    import org.openqa.selenium.support.ui.WebDriverWait;

    BCOBB NOTE:     The following little block of code was used to click the "Deny" button on the pop-up banner.
                    I don't think I need this anymore, but I'm keeping it just in case I do.

    driver.findElement(By.cssSelector("button[class=' osano-cm-deny osano-cm-buttons__button osano-cm-button osano-cm-button--type_deny '")).click();
    Thread.sleep(500);     
    System.out.println("Clicked the \"Deny\" button on the pop-up banner!");        
    WebDriverWait wait = new WebDriverWait(driver, 10);        
*/

package veryspicyheatwave.bwb_datascraper;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jetbrains.annotations.NotNull;

// Selenium Dependencies
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;


public class ThriftBooks_DataScraper
{    
    final static String BASE_URL = "https://www.thriftbooks.com";
    final static String GECKO_DRIVER_PATH = "C:/Users/smash/Downloads/geckodriver-v0.33.0-win64/geckodriver.exe";


    public static void main(String[] args) throws InterruptedException
    {
        Scanner keyboard = new Scanner(System.in);
        int firstPage;
        int lastPage;

        printBanner();

        do
        {
            System.out.println("Enter the first page you want to read, or enter 0 to exit: ");
            firstPage = keyboard.nextInt();
            if (firstPage < 0)
                System.out.println("\t**ERROR: First page number must be at least 1");
            System.out.println("Enter the last page you want to read, or enter 0 to exit: ");
            lastPage = keyboard.nextInt();
            if (lastPage < 0)
                System.out.println("\t**ERROR: Last page number must be at least 1");
            if (lastPage < firstPage)
                System.out.println("\t**ERROR: Last page number must be greater than first page number");
        }
        while (firstPage < 0 || lastPage < 0 || lastPage < firstPage);

        if (firstPage == 0)
        {
            System.out.println("Exiting program...");
            return;
        }

        WebDriver driver = getFFXDriver();        
        BypassWebroot(driver);        
        Thread.sleep(1000);

        long startTime = System.nanoTime();

        ArrayList<BookEntry> bookList = getBookList(driver, firstPage, lastPage);

        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1000000;
        printTimeFromNanoseconds((int)duration);
    }

    static @NotNull ArrayList<BookEntry> getBookList(WebDriver driver, int firstPage, int lastPage) throws InterruptedException
    {
        ArrayList<BookEntry> tempBookList = scrapeCatalogPages(driver, firstPage, lastPage);
        scrapeBookPages(driver, tempBookList);

        return tempBookList;
    }

    static @NotNull ArrayList<BookEntry> scrapeCatalogPages(WebDriver driver, int firstPage, int lastPage) throws InterruptedException
    {
        ArrayList<BookEntry> bookList = new ArrayList<>();

        for (int pageNo = firstPage; pageNo < lastPage + 1; pageNo++)
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

        Thread.sleep(250);
        System.out.println("I got " + bookList.size() + " books!");

        return bookList;
    }

    static void scrapeBookPages(WebDriver driver, @NotNull ArrayList<BookEntry> bookList) throws InterruptedException
    {
        for (BookEntry book : bookList)
        {
            driver.navigate().to(book.link);
            Thread.sleep(750);

            WebElement table = driver.findElement(By.xpath("//div[@class='WorkMeta-details is-collapsed']"));
            List<WebElement> details = table.findElements(By.tagName("span"));
            int index = -1;

            WebElement pageContents = driver.findElement(By.xpath("//div[@class='Content']"));
            List<WebElement> spans = pageContents.findElements(By.tagName("span"));
            for (WebElement span : spans)
            {
                if (span.getAttribute("itemprop") != null && span.getAttribute("itemprop").toLowerCase().contains("name"))
                {
                    book.genre = span.getText();
                    break;
                }
            }

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
                    book.releaseDate = parseDateFromStr(details.get(index + 1).getText());
                    continue;
                }
                if (detail.getText().toLowerCase().contains("length"))
                {
                    String tempPageLength = details.get(index + 1).getText();
                    tempPageLength = tempPageLength.replace(" Pages","");
                    book.pageLength = Integer.parseInt(tempPageLength);
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

            WebElement buttonContainer = driver.findElement(By.xpath("//div[@class='WorkSelector-rowContainer']"));


            List<WebElement> buttons = buttonContainer.findElements(By.tagName("button"));
            for (WebElement button : buttons)
            {
                List<WebElement> buttDetails = button.findElements(By.xpath("./child::div"));
                for (WebElement buttDetail : buttDetails)
                {
                    if (buttDetail.getText().equalsIgnoreCase("paperback"))
                    {
                        System.out.println("Found the paperback button!");
                        WebElement priceRangeElement = buttDetail.findElement(By.xpath("./following-sibling::div[@class='']//span"));
                        getNewAndUsedPrices(book, priceRangeElement.getText());

                        continue;
                    }

                    if (buttDetail.getText().toLowerCase().contains("market"))
                    {
                        button.click();
                        Thread.sleep(150);
                    }
                }
            }

            WebElement image = driver.findElement(By.xpath("//img[@itemprop='image']"));
            book.imageLink = image.getAttribute("src");

            //BCOBB: Delete this block of print methods once I get this data spit into some sort of file.
            System.out.println(book.title);
            System.out.println(book.author);
            System.out.printf("$%.2f\n", book.usedPrice);
            System.out.printf("$%.2f\n", book.newPrice);
            System.out.println(book.link);
            System.out.println(book.isbnCode);
            System.out.println(book.releaseDate);
            System.out.println(book.pageLength);
            System.out.println(book.language);
            System.out.println(book.genre);
            System.out.println(book.imageLink);
            System.out.println();
        }
    }
    

    static void printTimeFromNanoseconds(int duration)
    {
        int millis = 0, seconds = 0, minutes = 0, hours = 0, days = 0;

        if (duration > 1000)
        {
            millis = (duration % 1000);
            seconds = (duration / 1000);
        }

        if (seconds > 60)
        {
            minutes = seconds / 60;
            seconds = seconds % 60;
        }
        if (minutes > 60)
        {
            hours = minutes / 60;
            minutes = minutes % 60;
        }
        if (duration > 24)
        {
            days = hours / 24;
            hours = hours / 24;
        }

        System.out.print("The scraping took ");
        if (days > 0)
        {
            System.out.print(days + " days, ");
        }
        if (hours > 0)
        {
            System.out.print(hours + " hours, ");
        }
        if (minutes > 0)
        {
            System.out.print(minutes + " minutes and ");
        }

        System.out.print(seconds + "." + millis + " seconds. \n");
    }


    static void getNewAndUsedPrices(@NotNull BookEntry respBook, @NotNull String priceString)
    {
        String[] parsedPriceString = priceString.split("-");
        for (int i = 0; i < parsedPriceString.length; i++)
        {
            parsedPriceString[i] = parsedPriceString[i].trim();
            parsedPriceString[i] = parsedPriceString[i].replace("$", "");
        }
        respBook.usedPrice = Double.parseDouble(parsedPriceString[0]);
        respBook.newPrice = Double.parseDouble(parsedPriceString[1]);
    }


    static Date parseDateFromStr(String dateStr)
    {
        SimpleDateFormat format = new SimpleDateFormat("MMMM yyyy");

        Date resp = new Date();
        try
        {
            resp = null;
            resp = format.parse(dateStr);
        }
        catch (ParseException ex)
        {
            System.out.println("Failed to parse date string...");
        }

        return resp;
    }

    
    static @NotNull WebDriver getFFXDriver()
    {
        System.setProperty("webdriver.gecko.driver",GECKO_DRIVER_PATH);
        FirefoxOptions ffxOptions = new FirefoxOptions();
        FirefoxProfile profile = new FirefoxProfile();        
        ffxOptions.setCapability(FirefoxDriver.PROFILE, profile);
        WebDriver driver = new FirefoxDriver();
        System.out.println("Created the new web driver");
        return driver;
    }
    
    static void BypassWebroot(@NotNull WebDriver driver)
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

    static void printBanner()
    {
        // Hey, I didn't know you could do a text block like that, that's pretty nice! Thanks, Java!
        System.out.print("""
                $$$$$$$$$$$$$$$$$$$$$$$$$$$$$$'               `$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$ \s
                $$$$$$$$$$$$$$$$$$$$$$$$$$$$'                   `$$$$$$$$$$$$$$$$$$$$$$$$$$$$
                $$$'`$$$$$$$$$$$$$'`$$$$$$!                       !$$$$$$'`$$$$$$$$$$$$$'`$$$
                $$$$  $$$$$$$$$$$  $$$$$$$                         $$$$$$$  $$$$$$$$$$$  $$$$
                $$$$. `$' \\' \\$`  $$$$$$$!                         !$$$$$$$  '$/ `/ `$' .$$$$
                $$$$$. !\\  i  i .$$$$$$$$                           $$$$$$$$. i  i  /! .$$$$$
                $$$$$$   `--`--.$$$$$$$$$                           $$$$$$$$$.--'--'   $$$$$$
                $$$$$$L        `$$$$$^^$$                           $$^^$$$$$'        J$$$$$$
                $$$$$$$.   .'   ""~   $$$    $.                 .$  $$$   ~""   `.   .$$$$$$$
                $$$$$$$$.  ;      .e$$$$$!    $$.             .$$  !$$$$$e,      ;  .$$$$$$$$
                $$$$$$$$$   `.$$$$$$$$$$$$     $$$.         .$$$   $$$$$$$$$$$$.'   $$$$$$$$$
                $$$$$$$$    .$$$$$$$$$$$$$!     $$`$$$$$$$$'$$    !$$$$$$$$$$$$$.    $$$$$$$$
                $JT&yd$     $$$$$$$$$$$$$$$$.    $    $$    $   .$$$$$$$$$$$$$$$$     $by&TL$
                                                 $    $$    $
                                                 $.   $$   .$
                                                 `$        $'
                                                  `$$$$$$$$'

                """);
        System.out.println("B. Cobb's Selenium Web scraper for Thrift Books");
        System.out.println("The super fun but slightly useless learning project!\n\n");
    }
}


class BookEntry
{
    String title;
    String author;
    String link;
    String isbnCode;
    Date releaseDate;
    int pageLength;
    String language;
    String genre;
    double newPrice;
    double usedPrice;
    String imageLink;
    
    
    BookEntry (){}
}