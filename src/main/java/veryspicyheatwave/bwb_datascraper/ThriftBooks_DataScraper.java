//region Import Statements
package veryspicyheatwave.bwb_datascraper;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Date;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;

import static veryspicyheatwave.bwb_datascraper.WebRetryTool.*;
//endregion

public class ThriftBooks_DataScraper
{
    //region Class-Wide Variables
    final static String BASE_URL = "https://www.thriftbooks.com";
    final static String FILTER_URL = "&b.pp=30&b.f.lang[]=40&b.pt=1&b.f.t[]=";
    final static String GECKO_DRIVER = "geckodriver.exe";
    final static String SAVE_FILE = "dataScrape_" + System.currentTimeMillis() +".csv";
    final static String LOG_FILE = "dataScrape_" + System.currentTimeMillis() +".log";
    final static String FAIL_FILE = "dataScrape_" + System.currentTimeMillis() +"_FAILS.csv";
    static boolean loggingEvents = false;
    static Genre filterGenre;
    static PrimaryFilter filterPrimary;
    //endregion


    public static void main(String[] args) throws IOException, ParseException
    {
        printBanner();
        Scanner keyboard = new Scanner(System.in);

        int firstPage;
        int lastPage;

        do
        {
            firstPage = getFirstOrLastPage(keyboard, "first");
            if (firstPage == 0)
            {
                eventLogEntry("User chose to exit program...");
                return;
            }
            lastPage = getFirstOrLastPage(keyboard, "last");
            if (lastPage < firstPage)
                System.out.println("**ERROR: Last page can't be smaller than first page!\t");
        }
        while (lastPage < firstPage);

        filterPrimary = getPrimaryFilter(keyboard);
        filterGenre = getBookGenre(keyboard);

        eventLogEntry("User selected catalog pages " + firstPage + " to " + lastPage + " filtering by the " + filterPrimary.getDisplayString() + " " + filterGenre.getDisplayString() + " books");

        loggingEvents = askIfUserWantsToLog(keyboard);
        if (loggingEvents)
            eventLogEntry("Log file generated");

        writeLineToCSV("Title, Author, Used Price, New Price, Genre, Format, ISBN Code, Release Date," +
                        "Page Length, Language, ThriftBooks URL, Image Link 1, Image Link 2");

        eventLogEntry("CSV file generated");
        keyboard.close();

        long startTime = System.nanoTime();

        scrapeBookPages(getListOfBookURLs(firstPage, lastPage));

        File failFile = new File(FAIL_FILE);
        if (failFile.exists())
        {
            eventLogEntry("Retrying failed books");
            scrapeBookPages(getListOfFailedURLs(failFile));
        }

        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1000000;
        eventLogEntry("Task complete in " + printDurationFromNanoseconds((int)duration));
    }


    //region Primary WebScraper Methods
    static @NotNull ArrayList<String> getListOfBookURLs(int firstPage, int lastPage) throws IOException
    {
        WebDriver driver = getFFXDriver();
        ArrayList<String> listOfBookURLs = new ArrayList<>();
        eventLogEntry("Created the webdriver object to scrape for the book URLs");
        Wait<WebDriver> wait = new WebDriverWait(driver, 4);
        int numberOfBooks = 0;

        for (int pageNo = firstPage; pageNo < lastPage + 1; pageNo++)
        {
            try
            {
                long currentPageStartTime = System.nanoTime();
                String pageURL = BASE_URL + filterPrimary.getFilterString() + pageNo + FILTER_URL + filterGenre.getFilterNo();

                performWebAction(() -> driver.get("about:blank"), "load a blank page");
                Thread.sleep(666);

                performWebAction(() -> driver.get(pageURL), "load the URL for catalog page " + pageNo);
                Thread.sleep(2000);

                WebElement searchContainer = getWebElement(() -> {
                    String xpathExpression = "/html/body/div[4]/div/div[2]/div/div[2]/div/div/div/div/div[2]/div[2]/div[1]/div/div[1]";
                    wait.until(d -> driver.findElement(By.xpath(xpathExpression)).isDisplayed());
                    return driver.findElement(By.xpath(xpathExpression));
                }, "page " + pageNo + " search results");

                List<WebElement> books = getListOfWebElements(() -> searchContainer.findElements(By.tagName("a")), "list of books");

                for (WebElement book : books)
                {
                    String bookURL = book.getAttribute("href");
                    listOfBookURLs.add(bookURL);
                }
                int booksRetrieved = listOfBookURLs.size() - numberOfBooks;
                numberOfBooks = listOfBookURLs.size();
                eventLogEntry("Got a list of " + booksRetrieved + " book links from page " + pageNo);
                long currentPageEndTime = System.nanoTime();
                long duration = (currentPageEndTime - currentPageStartTime) / 1000000;
                eventLogEntry(printDurationFromNanoseconds((int) duration) + " spent scraping page " + pageNo);
            }
            catch (RuntimeException | InterruptedException e)
            {
                eventLogEntry("Error: Exception while trying to access a book on catalog page " + pageNo);
                eventLogEntry(e.getMessage());
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                eventLogEntry(sw.toString());
                sw.close();
            }
        }

        eventLogEntry("Successfully retrieved " + listOfBookURLs.size() + " book links from " + (lastPage - firstPage + 1) + " pages");
        driver.quit();
        eventLogEntry("WebDriver instance successfully closed");
        return listOfBookURLs;
    }



    static void scrapeBookPages(@NotNull ArrayList<String> listOfBookURLs) throws IOException, ParseException
    {
        WebDriver driver = getFFXDriver();
        eventLogEntry("Created the webdriver object to scrape the book URLs for book data");
        Wait<WebDriver> wait = new WebDriverWait(driver, 4);
        int bookSuccesses = 0, bookFailures = 0;

        for (String bookURL : listOfBookURLs)
        {
            long currentBookStartTime = System.nanoTime();
            BookEntry book = new BookEntry();
            book.link = bookURL;
            try
            {
                performWebAction(() -> driver.get(bookURL), "load book number " + listOfBookURLs.indexOf(bookURL) + " book URL");
                Thread.sleep(400);
                parseTitleAuthor(driver.getTitle(), book);

                WebElement pageContents = getWebElement(() -> {
                    String xpathExpression = "//div[@class='Content']";
                    wait.until(d -> driver.findElement(By.xpath(xpathExpression)).isDisplayed());
                    return driver.findElement(By.xpath(xpathExpression));
                }, "book page contents");
                book.genre = parseGenreString(pageContents);

                WebElement titleBlock = getWebElement(() -> {
                    String xpathExpression = "//h1[@class='WorkMeta-title Alternative Alternative-title']";
                    wait.until(d -> driver.findElement(By.xpath(xpathExpression)).isDisplayed());
                    return driver.findElement(By.xpath(xpathExpression));
                }, "title block");
                if (titleBlock.getText() != null)
                    book.title = titleBlock.getText();

                parseButtonContainer(driver, wait, book);

                downloadImageFromURL(book.paperbackImageLink,book.title,"1");
                downloadImageFromURL(book.massImageLink,book.title,"2");

                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

                String dataEntry = String.format("'%s','%s',%.2f,%.2f,'%s','%s','%s','%s',%d,'%s','%s','%s','%s'",
                        book.title.replace("'", "\\'"), book.author, book.usedPrice, book.newPrice, book.genre.replace("'", "\\'"),
                        book.format, book.isbnCode, formatter.format(book.releaseDate),
                        book.pageLength, book.language, book.link, book.paperbackImageLink, book.massImageLink);

                writeLineToCSV(dataEntry);
                if (!insertBookIntoSQLdb(dataEntry, 1 + listOfBookURLs.indexOf(bookURL)))
                {
                    writeFailuresToCSV(bookURL);
                }

                eventLogEntry(String.format("Successfully added book number %,d titled \"%s\" to the CSV file", (1 + listOfBookURLs.indexOf(bookURL)), book.title));
                bookSuccesses++;
            }
            catch (InterruptedException | RuntimeException | SQLException | ClassNotFoundException e)
            {
                eventLogEntry("Error: Exception while parsing the page for book number " + (1 + listOfBookURLs.indexOf(bookURL)) + ": " + bookURL);
                eventLogEntry(e.getMessage());
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                eventLogEntry(sw.toString());
                sw.close();
                writeFailuresToCSV(bookURL);
                bookFailures++;
            }
            finally
            {
                long currentBookEndTime = System.nanoTime();
                long duration = (currentBookEndTime - currentBookStartTime) / 1000000;
                eventLogEntry(printDurationFromNanoseconds((int) duration) + " spent on scraping book " + (1 + listOfBookURLs.indexOf(bookURL)));
            }
        }

        driver.quit();
        eventLogEntry("WebDriver instance successfully closed");
        eventLogEntry("Web scraping complete: " + bookSuccesses + " books successfully added");
        eventLogEntry(bookFailures + " books could not be processed");
    }
    //endregion


    //region WebElement Parsers
    static void parseTitleAuthor(String titleAuthor, @NotNull BookEntry book)
    {
        titleAuthor = titleAuthor.replace(" book by ", "|");
        String[] titleAuthorSplit = titleAuthor.split("\\|");
        book.title = titleAuthorSplit[0];
        if (titleAuthorSplit.length > 1)
            book.author = titleAuthorSplit[1];
        else
            book.author = "Who??";
    }


    static void parseButtonContainer(@NotNull WebDriver driver, Wait<WebDriver> wait, BookEntry book) throws InterruptedException, StaleElementReferenceException, TimeoutException
    {
        WebElement buttonContainer = getWebElement(() -> {
            String xpathExpression = "//div[@class='WorkSelector-row WorkSelector-td-height']";
            wait.until(d -> driver.findElement(By.xpath(xpathExpression)).isDisplayed());
            return driver.findElement(By.xpath(xpathExpression));
        }, "button container");

        List<WebElement> buttons = getListOfWebElements(() -> buttonContainer.findElements(By.tagName("button")), "list of buttons");

        for (WebElement button : buttons)
        {
            List<WebElement> buttDetails = getListOfWebElements(() -> button.findElements(By.xpath("./child::div")), "button details");
            for (WebElement buttDetail : buttDetails)
            {
                if (buttDetail.getText().equalsIgnoreCase("paperback"))
                {
                    performWebAction(() -> {
                        JavascriptExecutor js = (JavascriptExecutor)driver;
                        js.executeScript("arguments[0].click();", button);
                    }, "click a paperback button");

                    WebElement priceRangeElement = getWebElement(() -> {
                        String xpathExpression = "./following-sibling::div[@class='']//span";
                        wait.until(d -> buttDetail.findElement(By.xpath(xpathExpression)).isDisplayed());
                        return buttDetail.findElement(By.xpath(xpathExpression));
                    }, "price range");

                    if (priceRangeElement.getText().contains("$"))
                    {
                        getNewAndUsedPrices(book.paperbackPrices, priceRangeElement.getText());
                    }

                    WebElement image = getWebElement(() -> {
                        String xpathExpression = "//img[@itemprop='image']";
                        wait.until(d -> driver.findElement(By.xpath(xpathExpression)).isDisplayed());
                        return driver.findElement(By.xpath(xpathExpression));
                    }, "paperback image link");

                    book.paperbackImageLink = image.getAttribute("src");

                   WebElement table = getWebElement(() -> {
                        String xpathExpression = "//div[@class='WorkMeta-details is-collapsed']";
                        wait.until(d -> driver.findElement(By.xpath(xpathExpression)).isDisplayed());
                        return driver.findElement(By.xpath(xpathExpression));
                    }, "book data table");

                    parseTableDetails(table, book);
                    continue;
                }

                if (buttDetail.getText().equalsIgnoreCase("mass market paperback"))
                {
                    performWebAction(() -> {
                        JavascriptExecutor js = (JavascriptExecutor)driver;
                        js.executeScript("arguments[0].click();", button);
                    }, "click a hardcover button");

                    WebElement image = getWebElement(() -> {
                        String xpathExpression = "//img[@itemprop='image']";
                        wait.until(d -> driver.findElement(By.xpath(xpathExpression)).isDisplayed());
                        return driver.findElement(By.xpath(xpathExpression));
                    }, "mass market paperback image link");

                    book.massImageLink = image.getAttribute("src");
                    if (book.usedPrice <= 0 || book.newPrice <= 0)
                    {
                        WebElement priceRangeElement = getWebElement(() -> {
                            String xpathExpression = "./following-sibling::div[@class='']//span";
                            wait.until(d -> buttDetail.findElement(By.xpath(xpathExpression)).isDisplayed());
                            return buttDetail.findElement(By.xpath(xpathExpression));
                        }, "price range");

                        getNewAndUsedPrices(book.massMarketPrices, priceRangeElement.getText());
                    }
                    continue;
                }

                if (buttDetail.getText().equalsIgnoreCase("hardcover"))
                {
                    WebElement priceRangeElement = getWebElement(() -> {
                        String xpathExpression = "./following-sibling::div[@class='']//span";
                        wait.until(d -> buttDetail.findElement(By.xpath(xpathExpression)).isDisplayed());
                        return buttDetail.findElement(By.xpath(xpathExpression));
                    }, "price range");
                    if (priceRangeElement.getText().contains("$"))
                    {
                        getNewAndUsedPrices(book.hardcoverPrices, priceRangeElement.getText());
                    }
                }
            }
        }
        determineBestPriceSet(book);
    }


    static void parseTableDetails(@NotNull WebElement table, BookEntry book) throws StaleElementReferenceException, TimeoutException, InterruptedException
    {
        List<WebElement> elements = getListOfWebElements(() -> table.findElements(By.tagName("span")), "table details");
        int index = -1;

        for (WebElement detail : elements)
        {
            index++;
            if (detail.getText().toLowerCase().contains("isbn"))
            {
                if (elements.get(index + 1).getText().length() < 13)
                {
                    book.isbnCode = elements.get(index + 1).getText();
                    continue;
                }
            }

            if (detail.getText().toLowerCase().contains("release"))
            {
                book.releaseDate = parseDateFromStr(elements.get(index + 1).getText());
                continue;
            }

            if (detail.getText().toLowerCase().contains("length"))
            {
                String tempPageLength = elements.get(index + 1).getText();
                tempPageLength = tempPageLength.replace(" Pages", "");
                if (Integer.parseInt(tempPageLength) > 10)
                {
                    book.pageLength = Integer.parseInt(tempPageLength);
                    continue;
                }
            }

            if (detail.getText().toLowerCase().contains("language"))
            {
                book.language = elements.get(index + 1).getText();
                continue;
            }

            if (detail.getAttribute("itemprop") != null && book.genre == null && detail.getAttribute("itemprop").toLowerCase().contains("name"))
            {
                book.genre = detail.getText();
            }
        }
    }


    static String parseGenreString(@NotNull WebElement pageContents) throws InterruptedException
    {
        List<WebElement> spans = getListOfWebElements(() -> pageContents.findElements(By.tagName("span")), "genre element");

        for (WebElement span : spans)
        {
            if (span.getAttribute("itemprop") != null && span.getAttribute("itemprop").toLowerCase().contains("name"))
            {
                return span.getText();
            }
        }
        return filterGenre.getDisplayString();
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
    //endregion


    //region File I/O and Print Methods
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


    static void writeFailuresToCSV(String dataEntry)
    {
        try (FileWriter writer = new FileWriter(FAIL_FILE, true))
        {
            writer.write(dataEntry + "\n");
        }
        catch (IOException ex)
        {
            System.out.println("ERROR: Failed to write to CSV file. Oops!");
        }
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
        logEntry = formatter.format(LocalDateTime.now()) + ":\t" + logEntry;
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


    static boolean insertBookIntoSQLdb(String csvStr, int bookNo) throws FileNotFoundException, ClassNotFoundException, SQLException
    {
        Class.forName("com.mysql.cj.jdbc.Driver");
        String pwSQL = getSQLPassword();
        boolean resp = false;

        Connection con = null;
        try
        {
            String insertionQuery = "INSERT INTO books(title,author,used_price,new_price,genre,binding_type,isbn_code,release_date,page_length,language,bookURL,bookImageURL1,bookImageURL2) VALUES";
            insertionQuery += "(" + csvStr + ");";

            con = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/thriftBooksDB", "root", pwSQL);
            Statement statement = con.createStatement();
            statement.execute(insertionQuery);
            eventLogEntry("Book " + bookNo + ": Successfully added to SQL table");
            resp = true;
        } catch (SQLException sqlE)
        {
            eventLogEntry("Book " + bookNo + ": " + sqlE.getMessage());
            if (sqlE.getMessage().contains("Duplicate entry"))
                resp = true;
        } finally
        {
            assert con != null;
            con.close();
        }
        return resp;
    }


    static void downloadImageFromURL(String imageURL, String bookTitle, String imageNumber)
    {
        if (imageURL.equalsIgnoreCase("null"))
        {
            eventLogEntry("No image URL exists");
            return;
        }

        Path fileOutputPath = Paths.get("images/" + bookTitle);
        File fileOutputStr =  new File(fileOutputPath + "/" + imageNumber + ".jpg");
        if (!Files.exists(fileOutputPath))
            new File(String.valueOf(fileOutputPath)).mkdirs();

        try (BufferedInputStream in = new BufferedInputStream(new URL(imageURL).openStream());
                FileOutputStream fileOutputStream = new FileOutputStream(fileOutputStr))
        {
            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1)
            {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
            eventLogEntry("Created new image file: " + fileOutputStr);
        }
        catch (IOException e)
        {
            eventLogEntry("Error while creating image file: " + e.getMessage());
        }
    }


    static @NotNull ArrayList<String> getListOfFailedURLs(File failFile) throws FileNotFoundException
    {
        Scanner failFileScan = new Scanner(failFile);
        ArrayList<String> failURLs = new ArrayList<>(){};

        while (failFileScan.hasNext())
        {
            failURLs.add(failFileScan.nextLine());
        }
        failFileScan.close();
        if (failFile.delete())
        {
            eventLogEntry("Deleted first fail log");
        }
        return failURLs;
    }


    static String getSQLPassword() throws FileNotFoundException
    {
        File pwFile = new File("pw.txt");
        Scanner pwFileScan = new Scanner(pwFile);
        String resp = pwFileScan.nextLine();
        pwFileScan.close();
        return resp;
    }



    static @NotNull String printDurationFromNanoseconds(int duration)
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

        return timeString;
    }
    //endregion


    //region User Interaction Methods
    static int getFirstOrLastPage(Scanner keyboard, @NotNull String page)
    {
        int resp;
        if (page.equalsIgnoreCase("first"))
        {
            do
            {
                System.out.println("Enter the first page you want to read, or enter 0 to exit: ");
                resp = keyboard.nextInt();
                if (resp < 0)
                    System.out.println("\t**ERROR: First page number must be at least 1");
            }
            while (resp < 0);
        }
        else if (page.equalsIgnoreCase("last"))
        {
            do
            {
                System.out.println("Enter the last page you want to read: ");
                resp = keyboard.nextInt();
                if (resp < 0)
                    System.out.println("\t**ERROR: Last page number must be at least 1");
            }
            while (resp < 0);
        }
        else
        {
            resp = 1;
        }
        keyboard.nextLine();
        return resp;
    }


    static boolean askIfUserWantsToLog(@NotNull Scanner keyboard)
    {
        String logEventsResponse;
        do
        {
            System.out.println("Would you like to log events to a file? [(y)es/(n)o]");
            logEventsResponse = keyboard.nextLine();
        }
        while (!logEventsResponse.equalsIgnoreCase("yes") && !logEventsResponse.equalsIgnoreCase("no") && !logEventsResponse.equalsIgnoreCase("y") && !logEventsResponse.equalsIgnoreCase("n"));

        return logEventsResponse.toLowerCase().charAt(0) == 'y';
    }
    //endregion


    //region Misc Utility Methods
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


    static void determineBestPriceSet(@NotNull BookEntry book)
    {
        ArrayList<PriceStructure> priceLists = new ArrayList<>();
        boolean[] removeIndices = {false, false, false};
        priceLists.add(book.paperbackPrices);
        priceLists.add(book.massMarketPrices);
        priceLists.add(book.hardcoverPrices);

        for (int i = 0; i <= priceLists.size() - 1; i++)
        {
            if (priceLists.get(i).newPrice <= 0)
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

        PriceStructure returnBook = new PriceStructure("Undefined", "Undefined");
        returnBook.newPrice = 1000000000;
        returnBook.usedPrice = 1000000000;

        for (int i = 0; i <= priceLists.size() - 1; i++)
        {
            if (priceLists.get(i).newPrice < returnBook.newPrice)
            {
                returnBook = priceLists.get(i);
            }
        }

        if (returnBook.usedPrice == 1000000000)
        {
            returnBook.usedPrice = 0;
            returnBook.newPrice = 0;
        }
        book.newPrice = returnBook.newPrice;
        book.usedPrice = returnBook.usedPrice;
        book.format = returnBook.format;
        book.buttonName = returnBook.buttonName;
    }


    static PrimaryFilter getPrimaryFilter(Scanner keyboard)
    {
        System.out.println("Enter the number of which way you'd like the pages to be filtered: ");

        for (PrimaryFilter g : PrimaryFilter.values())
        {
            System.out.printf("[%d]: %s\n", g.ordinal(), g.getDisplayString());
        }
        int selection = keyboard.nextInt();

        while (selection < 0 || selection > Genre.values().length)
        {
            System.out.println("Invalid selection. Try again. ");
            selection = keyboard.nextInt();
        }
        keyboard.nextLine();
        return PrimaryFilter.values()[selection];
    }


    static Genre getBookGenre(Scanner keyboard)
    {
        System.out.println("Enter the number of which genre would you like to get: ");

        for (Genre g : Genre.values())
        {
            System.out.printf("[%d]: %s\n", g.ordinal(), g.getDisplayString());
        }
        int selection = keyboard.nextInt();

        while (selection < 0 || selection > Genre.values().length)
        {
            System.out.println("Invalid selection. Try again. ");
            selection = keyboard.nextInt();
        }
        keyboard.nextLine();
        return Genre.values()[selection];
    }

    
    static @NotNull WebDriver getFFXDriver()
    {
        System.setProperty("webdriver.gecko.driver",GECKO_DRIVER);
        FirefoxOptions ffxOptions = new FirefoxOptions();
        FirefoxProfile profile = new FirefoxProfile();        
        ffxOptions.setCapability(FirefoxDriver.PROFILE, profile);
        ffxOptions.setHeadless(true);
        return new FirefoxDriver(ffxOptions);
    }
    //endregion
}