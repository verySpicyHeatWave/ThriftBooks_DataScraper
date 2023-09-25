/*
============================================================ TO-DO LIST ============================================================

1) ***DONE*** SOMETHING IS HOGGING MEMORY! Process slows down considerably after a while. Potentially fix with the following implementations:
    a) ***DONE*** Change the webdriver to run headless to try and preserve memory
    b) ***DONE*** Create a driver each time a page is scraped and then close the driver.
    c) ***DONE*** Try and find other memory leaks, maybe?
2) Start looking into pumping the data into a SQL database.
    a) Reference link: https://stackoverflow.com/questions/2839321/connect-java-to-a-mysql-database
3) Figure out how to automatically download the image files and either save them somewhere to reference later or embed them into the
        SQL database (is that even possible?)
4) ***DONE*** It would also be wise to write all of my debug lines to a log file. That way I can always troubleshoot and see where things might be
        going wrong down the road. I want to implement that.
5) Add the book description to the BookEntry objects and store that string in the database as well.
5) ***DONE*** OPTIONAL: Maybe I want to reformat the date before I send it off? The simple "Date.toString()" method has a really ugly format and even
        Excel, which is pretty good at recognizing dates even if there is no date, doesn't recognize it as a date.
6) ***DONE*** OPTIONAL: I wonder if I'll ever actually use the "bookList" variable that's in the main function. Maybe I ought to refactor to just cut
        that out. Since I'm writing to the DB line by line instead of just dumping at the end of everything, I may never use it.
7) ***DONE*** OPTIONAL: Minor addition, but adding the page number to the book details that go to the CSV could be helpful for debugging, as well, even
        though that data wouldn't be necessary for the final DB later on.

On occasion, run the scraper overnight and see how accurate my time estimate is. 2 minutes per page means 30 pages per hour. 120 pages should
        take about 4 hours and I should wind up with 3600 books in the CSV.

====================================================================================================================================
*/

package veryspicyheatwave.bwb_datascraper;

import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.*;

import org.jetbrains.annotations.NotNull;

// Selenium Dependencies
import org.openqa.selenium.*;
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
    final static String LOG_FILE = "C:/Users/smash/Desktop/Dad's Stuff/Coding/dataScrape_" + System.currentTimeMillis() +".log";
    static boolean loggingEvents = false;


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
        keyboard.nextLine();

        if (firstPage == 0)
        {
            System.out.println("Exiting program...");
            return;
        }

        String logEventsResponse;
        do
        {
            System.out.println("Would you like to log events to a file? [(y)es/(n)o]");
            logEventsResponse = keyboard.nextLine();
        }
        while (!logEventsResponse.equalsIgnoreCase("yes") && !logEventsResponse.equalsIgnoreCase("no") && !logEventsResponse.equalsIgnoreCase("y") && !logEventsResponse.equalsIgnoreCase("n"));

        if (logEventsResponse.toLowerCase().charAt(0) == 'y')
        {
            loggingEvents = true;
            eventLogEntry("Log file generated.");
        }

        writeLineToCSV("Title, Author, Used Price, New Price, Genre, Format, ISBN Code, Release Date," +
                        "Page Length, Language, ThriftBooks URL, Image Link 1, Image Link 2, Page Number");

        eventLogEntry("CSV file generated.");

        //WebDriver driver = getFFXDriver();
        //BypassWebroot(driver);
        //Thread.sleep(250);

        long startTime = System.nanoTime();

        scrapeBookPages(getListOfBookURLs(firstPage, lastPage));

        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1000000;
        printTimeFromNanoseconds((int)duration, false);
    }


    static void writeLineToCSV(String dataEntry)
    {
        try (FileWriter writer = new FileWriter(SAVE_FILE, true))
        {
            writer.write(dataEntry + "\n");
        }
        catch (IOException ex)
        {
            System.out.println("ERROR: Failed to write to CSV file. Oops!");
        }
    }


    static void eventLogEntry(String logEntry)
    {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        logEntry = formatter.format(LocalDateTime.now()) + ": " + logEntry;
        System.out.println(logEntry);
        if (loggingEvents)
        {
            try (FileWriter writer = new FileWriter(LOG_FILE, true))
            {
                writer.write(logEntry + "\n");
            }
            catch (IOException ex)
            {
                System.out.println("ERROR: Failed to write to log file. Oops!");
            }
        }
    }


    static @NotNull ArrayList<String> getListOfBookURLs(int firstPage, int lastPage) throws InterruptedException
    {
        WebDriver driver = getFFXDriver();
        eventLogEntry("Created the webdriver object to scrape for the book URLs.");
        ArrayList<String> listOfBookURLs = new ArrayList<>();
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

            WebElement searchContainer = driver.findElement(By.xpath("/html/body/div[4]/div/div[2]/div[2]/div[2]/div/div/div/div/div[2]/div[2]/div[1]/div/div[1]"));
            List<WebElement> books = searchContainer.findElements(By.tagName("a"));
            for (WebElement book : books)
            {
                String bookURL = book.getAttribute("href");
                listOfBookURLs.add(bookURL);
            }
            eventLogEntry("Got a list of book links from page " + pageNo);
        }

        Thread.sleep(250);
        eventLogEntry("retrieved " + listOfBookURLs.size() + " book links from " + (lastPage - firstPage + 1) + " pages.");

        driver.close();
        return listOfBookURLs;
    }


    static void scrapeBookPages(@NotNull ArrayList<String> listOfBookURLs) throws InterruptedException
    {
        WebDriver driver = getFFXDriver();
        eventLogEntry("Created the webdriver object to scrape the book URLs for book data.");
        Wait<WebDriver> wait = new WebDriverWait(driver, 2);
        for (String bookURL : listOfBookURLs)
        {
            long currentBookStartTime = System.nanoTime();
            try
            {
                BookEntry book = new BookEntry();
                book.pageNo = (listOfBookURLs.indexOf(bookURL) / 30) + 1;
                book.link = bookURL;
                driver.navigate().to(book.link);
                Thread.sleep(400);
                wait.until(d -> driver.findElement(By.xpath("//div[@class='WorkMeta-details is-collapsed']")).isDisplayed());

                String titleAuthor = driver.getTitle();
                titleAuthor = titleAuthor.replace(" book by ", "|");
                String[] titleAuthorSplit = titleAuthor.split("\\|");
                book.title = titleAuthorSplit[0];
                book.author = titleAuthorSplit[0];

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
                        tempPageLength = tempPageLength.replace(" Pages", "");
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

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/yyyy");

                String dataEntry = String.format("\"%s\",\"%s\",%.2f,%.2f,\"%s\",\"%s\",\"%s\",\"%s\",%d,\"%s\",\"%s\",\"%s\",\"%s\",%d",
                        book.title, book.author, book.usedPrice, book.newPrice, book.genre, book.format, book.isbnCode, formatter.format((TemporalAccessor) book.releaseDate),
                        book.pageLength, book.language, book.link, book.paperbackImageLink, book.massImageLink, book.pageNo);

                writeLineToCSV(dataEntry);

                eventLogEntry(String.format("Successfully added book number %,d titled \"%s\" to the file.\n", (1 + listOfBookURLs.indexOf(bookURL)), book.title));
            }
            catch (TimeoutException e)
            {
                eventLogEntry("Error: Selenium timed out when trying to access the page for book number " + listOfBookURLs.indexOf(bookURL) + ": " + bookURL );
                throw new TimeoutException(e);
            }
            catch (StaleElementReferenceException e)
            {
                eventLogEntry("Error: Selenium couldn't locate a particular page element for book number " + listOfBookURLs.indexOf(bookURL) + ": " + bookURL );
                throw new TimeoutException(e);
            }
            long currentBookEndTime = System.nanoTime();

            long duration = (currentBookEndTime - currentBookStartTime) / 1000000;
            printTimeFromNanoseconds((int)duration, true);
        }
        driver.close();
    }
    

    static void printTimeFromNanoseconds(int duration, boolean isBook)
    {
        int millis = 0, seconds = 0, minutes = 0, hours = 0, days = 0;
        String timeString = "";

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

        if (!isBook)
            timeString += "The scraping took ";
        if (days > 0)
        {
            timeString += String.format("%d days, ", days);
        }
        if (hours > 0)
        {
            timeString += String.format("%d hours, ", hours);
        }
        if (minutes > 0)
        {
            timeString += String.format("%d minutes ", minutes);
        }

        timeString += String.format("%d.%d seconds", seconds, millis);

        if (isBook)
            timeString += (" between the last book and this one.");
        else
            timeString += (".");

        eventLogEntry(timeString);
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
            eventLogEntry("Failed to parse date/time value from text string...");
        }
        return resp;
    }

    
    static @NotNull WebDriver getFFXDriver()
    {
        System.setProperty("webdriver.gecko.driver",GECKO_DRIVER_PATH);
        FirefoxOptions ffxOptions = new FirefoxOptions();
        FirefoxProfile profile = new FirefoxProfile();        
        ffxOptions.setCapability(FirefoxDriver.PROFILE, profile);
        ffxOptions.setHeadless(true);
        return new FirefoxDriver(ffxOptions);
    }


//    static void BypassWebroot(@NotNull WebDriver driver)
//    {
//        try
//        {
//            Thread.sleep(2000);
//            WebElement allowBTN = driver.findElement(By.id("allowButton"));
//            allowBTN.click();
//            System.out.println("Bypassed the Webroot filter page");
//        }
//        catch (InterruptedException ex)
//        {
//            Logger.getLogger(ThriftBooks_DataScraper.class.getName()).log(Level.SEVERE, null, ex);
//        }
//    }


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
    int pageNo;

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