/*
============================================================ TO-DO LIST ============================================================

1) Run the scraper overnight and see how accurate my time estimate is. 2 minutes per page means 30 pages per hour. 120 pages should
        take about 4 hours and I should wind up with 3600 books in the CSV.
2) Start looking into pumping the data into a SQL database.
3) Figure out how to automatically download the image files and either save them somewhere to reference later or embed them into the
        SQL database (is that even possible?)
4) It would also be wise to write all of my debug lines to a log file. That way I can always troubleshoot and see where things might be
        going wrong down the road. I want to implement that.
5) OPTIONAL: Maybe I want to reformat the date before I send it off? The simple "Date.toString()" method has a really ugly format and even
        Excel, which is pretty good at recognizing dates even if there is no date, doesn't recognize it as a date.
6) OPTIONAL: I wonder if I'll ever actually use the "bookList" variable that's in the main function. Maybe I ought to refactor to just cut
        that out. Since I'm writing to the DB line by line instead of just dumping at the end of everything, I may never use it.
7) OPTIONAL: Minor addition, but adding the page number to the book details that go to the CSV could be helpful for debugging, as well, even
        though that data wouldn't be necessary for the final DB later on.

====================================================================================================================================
*/

package veryspicyheatwave.bwb_datascraper;

import java.io.FileWriter;
import java.io.IOException;
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
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;


public class ThriftBooks_DataScraper
{    
    final static String BASE_URL = "https://www.thriftbooks.com";
    final static String GECKO_DRIVER_PATH = "C:/Users/smash/Downloads/geckodriver-v0.33.0-win64/geckodriver.exe";
    final static String SAVE_FILE = "C:/Users/smash/Desktop/Dad's Stuff/Coding/dataScrape_" + System.currentTimeMillis() +".csv";


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

        writeLineToCSV("Title, Author, Used Price, New Price, Genre, Format, ISBN Code, Release Date," +
                        "Page Length, Language, ThriftBooks URL, Image Link 1, Image Link 2");

        System.out.println("Generated CSV file");

        WebDriver driver = getFFXDriver();        
        BypassWebroot(driver);
        Thread.sleep(250);

        long startTime = System.nanoTime();

        ArrayList<BookEntry> bookList = getBookList(driver, firstPage, lastPage);

        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1000000;
        printTimeFromNanoseconds((int)duration);
    }


    static void writeLineToCSV(String dataEntry)
    {
        FileWriter writer;
        try
        {
            writer = new FileWriter(ThriftBooks_DataScraper.SAVE_FILE, true);
            writer.write(dataEntry + "\n");
            writer.close();
        }
        catch (IOException ex)
        {
            System.out.println("Failed to write to CSV file. Oops!");
        }
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
        Wait<WebDriver> wait = new WebDriverWait(driver, 2);

        for (int pageNo = firstPage; pageNo < lastPage + 1; pageNo++)
        {
            driver.navigate().to("about:blank");
            Thread.sleep(500);
            String pageURL = BASE_URL + "/browse/#b.s=mostPopular-desc&b.p=" + pageNo + "&b.pp=30&b.oos";
            driver.navigate().to(pageURL);
            Thread.sleep(2000);
            wait.until(d -> driver.findElement(By.xpath("/html/body/div[4]/div/div[2]/div[2]/div[2]/div/div/div/div/div[2]/div[2]/div[1]/div/div[1]")).isDisplayed());
            Thread.sleep(500);

            WebElement searchContainer = driver.findElement(By.xpath(   "/html/body/div[4]/div/div[2]/div[2]/div[2]/div/div/div/div/div[2]/div[2]/div[1]/div/div[1]"));
            List<WebElement> books = searchContainer.findElements(By.tagName("a"));
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
            System.out.println("Got the list of books from page " + pageNo);
        }

        Thread.sleep(250);
        System.out.println("I got " + bookList.size() + " books!");

        return bookList;
    }


    static void scrapeBookPages(WebDriver driver, @NotNull ArrayList<BookEntry> bookList) throws InterruptedException
    {
        Wait<WebDriver> wait = new WebDriverWait(driver, 2);
        for (BookEntry book : bookList)
        {
            driver.navigate().to(book.link);
            Thread.sleep(400);
            wait.until(d -> driver.findElement(By.xpath("//div[@class='WorkMeta-details is-collapsed']")).isDisplayed());

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
                        WebElement priceRangeElement = buttDetail.findElement(By.xpath("./following-sibling::div[@class='']//span"));
                        if (priceRangeElement.getText().contains("$"))
                        {
                            getNewAndUsedPrices(book.paperbackPrices, priceRangeElement.getText());
                        }
                        button.click();
                        Thread.sleep(150);
                        WebElement image = driver.findElement(By.xpath("//img[@itemprop='image']"));
                        book.paperbackImageLink = image.getAttribute("src");

                        continue;
                    }

                    if (buttDetail.getText().equalsIgnoreCase("mass market paperback"))
                    {
                        button.click();
                        Thread.sleep(150);
                        WebElement image = driver.findElement(By.xpath("//img[@itemprop='image']"));
                        book.massImageLink = image.getAttribute("src");
                        if (book.usedPrice <= 0 || book.newPrice <= 0)
                        {
                            WebElement priceRangeElement = buttDetail.findElement(By.xpath("./following-sibling::div[@class='']//span"));
                            getNewAndUsedPrices(book.massMarketPrices, priceRangeElement.getText());
                        }
                        continue;
                    }

                    if (buttDetail.getText().equalsIgnoreCase("hardcover"))
                    {
                        WebElement priceRangeElement = buttDetail.findElement(By.xpath("./following-sibling::div[@class='']//span"));
                        if (priceRangeElement.getText().contains("$"))
                        {
                            getNewAndUsedPrices(book.hardcoverPrices, priceRangeElement.getText());
                        }
                    }
                }
            }

            determineBestPriceSet(book);

            String dataEntry = String.format("\"%s\",\"%s\",%.2f,%.2f,\"%s\",\"%s\",\"%s\",\"%s\",%d,\"%s\",\"%s\",\"%s\",\"%s\"",
                    book.title, book.author, book.usedPrice, book.newPrice, book.genre, book.format, book.isbnCode, book.releaseDate.toString(),
                    book.pageLength, book.language, book.link, book.paperbackImageLink, book.massImageLink);

            writeLineToCSV(dataEntry);

            System.out.println("Successfully added book number " + (1 + bookList.indexOf(book)) + " titled " + book.title + " to the file.");
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


    static void getNewAndUsedPrices(@NotNull PriceStructure respBook, @NotNull String priceString)
    {
        if (priceString.contains(" - "))
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
        else if (priceString.contains("$"))
        {
            priceString = priceString.trim();
            priceString = priceString.replace("$", "");
            respBook.newPrice = Double.parseDouble(priceString);
            respBook.usedPrice = 0;
        }
        else
        {
            respBook.usedPrice = 0;
            respBook.newPrice = 0;
        }
    }

    static void determineBestPriceSet(BookEntry book)
    {
        ArrayList<PriceStructure> priceLists = new ArrayList<>();
        boolean[] removeIndices = {false, false, false};
        priceLists.add(book.paperbackPrices);
        priceLists.add(book.massMarketPrices);
        priceLists.add(book.hardcoverPrices);

        for (int i = 0; i <= priceLists.size() - 1; i++)
        {
            if (priceLists.get(i).newPrice <= 0 || priceLists.get(i).newPrice - priceLists.get(i).usedPrice < 2)
            {
                removeIndices[i] = true;
            }
        }

        for (int i = priceLists.size() - 1; i >= 0; i--)
        {
            if (removeIndices[i])
            {
                priceLists.remove(i);
            }
        }

        if (priceLists.size() == 1)
        {
            book.newPrice = priceLists.get(0).newPrice;
            book.usedPrice = priceLists.get(0).usedPrice;
            book.format = priceLists.get(0).format;
            return;
        }

        PriceStructure returnBook = new PriceStructure("null");
        returnBook.newPrice = 1000000000;
        returnBook.usedPrice = 1000000000;

        for (int i = 0; i <= priceLists.size() - 1; i++)
        {
            if (priceLists.get(i).newPrice < returnBook.newPrice)
            {
                returnBook = priceLists.get(i);
            }
        }

        book.newPrice = returnBook.newPrice;
        book.usedPrice = returnBook.usedPrice;
        book.format = returnBook.format;
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
    String format;
    double newPrice;
    double usedPrice;
    String paperbackImageLink;
    String massImageLink;

    PriceStructure massMarketPrices = new PriceStructure("Paperback");
    PriceStructure paperbackPrices = new PriceStructure("Paperback");
    PriceStructure hardcoverPrices = new PriceStructure("Hardcover");


    BookEntry (){}
}




class PriceStructure
{
    double newPrice;
    double usedPrice;
    String format;


    PriceStructure (String format)
    {
        this.format = format;
    }
}