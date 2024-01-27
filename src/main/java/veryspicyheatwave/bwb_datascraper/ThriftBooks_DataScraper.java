package veryspicyheatwave.bwb_datascraper;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

import static veryspicyheatwave.bwb_datascraper.WebRetryTool.*;

public class ThriftBooks_DataScraper
{
    //region Class-Wide Variables
    final static String BASE_URL = "https://www.thriftbooks.com";
    final static String FILTER_URL = "&b.pp=30&b.f.lang[]=40&b.pt=1&b.f.t[]=";
    final static String GECKO_DRIVER = "src/main/resources/geckodriver.exe";
    static Genre filterGenre;
    static PrimaryFilter filterPrimary;
    static long[] averageTimes;
    public static Logger logger = LoggerFactory.getLogger(ThriftBooks_DataScraper.class);
    public static Logger errorLogger = LoggerFactory.getLogger("errorLogger");
    //endregion


    public static void main(String[] args) throws IOException, ParseException
    {
        printBanner();
        Scanner keyboard = new Scanner(System.in);
        int firstPage, lastPage;

        do
        {
            firstPage = getFirstOrLastPage(keyboard, "first");
            if (firstPage == 0)
            {
                logger.info("User chose to exit program...");
                return;
            }
            lastPage = getFirstOrLastPage(keyboard, "last");
            if (lastPage < firstPage)
                System.out.println("**ERROR: Last page can't be smaller than first page!\t");
        }
        while (lastPage < firstPage);

        filterPrimary = getPrimaryFilter(keyboard);
        filterGenre = getBookGenre(keyboard);

        logger.info("BEGIN SESSION");
        logger.info("User selected catalog pages " + firstPage + " to " + lastPage + " filtering by the " + filterPrimary.getDisplayString() + " " + filterGenre.getDisplayString() + " books");

        keyboard.close();
        long startTime = System.currentTimeMillis();
        try
        {
            scrapeBookPages(getListOfBookURLs(firstPage, lastPage));
        }
        catch (ConnectException e)
        {
            errorLogger.error("CONNECTION FAILURE!", e);
        }
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;


        logger.info("Finished scraping catalog pages " + firstPage + " to " + lastPage + " filtering by the " + filterPrimary.getDisplayString() + " " + filterGenre.getDisplayString() + " books");
        logger.info("Full job complete in " + getFormattedDurationString((int)duration));
        logger.info("Average time " + getFormattedDurationString((int)getAverageTime(averageTimes)) + " per book");
        logger.info("END SESSION\n\n");
    }


    //region Primary WebScraper Methods
    static @NotNull ArrayList<String> getListOfBookURLs(int firstPage, int lastPage) throws IOException
    {
        WebDriver driver = getFFXDriver();
        ArrayList<String> listOfBookURLs = new ArrayList<>();
        logger.info("Created the webdriver object to scrape for the book URLs");
        Wait<WebDriver> wait = new WebDriverWait(driver, 4);
        int numberOfBooks = 0;

        for (int pageNo = firstPage; pageNo < lastPage + 1; pageNo++)
        {
            try
            {
                long currentPageStartTime = System.nanoTime();
                String pageURL = BASE_URL + filterPrimary.getFilterString() + pageNo + FILTER_URL + filterGenre.getFilterNo();

                performWebActionWithRetries(() -> {
                    driver.get("about:blank");
                    return new ArrayList<>();
                }, "load a blank page", 2);
                Thread.sleep(666);

                performWebActionWithRetries(() -> {
                    driver.get(pageURL);
                    return new ArrayList<>();
                }, "load the URL for catalog page " + pageNo, 3);
                Thread.sleep(2500);

                WebElement searchContainer = performWebActionWithRetries(() -> {
                    String xpathExpression = "/html/body/div[4]/div/div[2]/div/div[2]/div/div/div/div/div[2]/div[2]/div[1]/div/div[1]";
                    return listGetterXPath(driver, wait, xpathExpression, true);
                }, "collect catalog page " + pageNo + "'s search results", 4).get(0);

                List<WebElement> books = performWebActionWithRetries(() -> {
                    String tagName = "a";
                    return listGetterTagName(searchContainer, wait, tagName, false);
                }, "find the books in the search results container", 4);

                for (WebElement book : books)
                {
                    String bookURL = book.getAttribute("href");
                    listOfBookURLs.add(bookURL);
                }
                int booksRetrieved = listOfBookURLs.size() - numberOfBooks;
                numberOfBooks = listOfBookURLs.size();
                logger.info("Got a list of " + booksRetrieved + " book links from page " + pageNo);
                long currentPageEndTime = System.nanoTime();
                long duration = (currentPageEndTime - currentPageStartTime) / 1000000;
                logger.info(getFormattedDurationString((int) duration) + " spent scraping page " + pageNo);
            }
            catch (RuntimeException | InterruptedException e)
            {
                errorLogger.error("Exception while trying to access a book on catalog page " + pageNo);
                errorLogger.error(e.getMessage());
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                errorLogger.error(sw.toString());
                sw.close();
            }
        }

        logger.info("Successfully retrieved " + listOfBookURLs.size() + " book links from " + (lastPage - firstPage + 1) + " pages");
        driver.quit();
        logger.info("WebDriver instance successfully closed");
        return listOfBookURLs;
    }


    static void scrapeBookPages(@NotNull ArrayList<String> listOfBookURLs) throws IOException, ParseException
    {
        WebDriver driver = getFFXDriver();
        logger.info("Created the webdriver object to scrape the book URLs for book data");
        Wait<WebDriver> wait = new WebDriverWait(driver, 4);
        int bookSuccesses = 0, bookFailures = 0, bookDuplicates = 0, avgIndex = 0, lastGoodBook = 0, totalBooks = listOfBookURLs.size();
        averageTimes = new long[totalBooks];

        for (String bookURL : listOfBookURLs)
        {
            long currentBookStartTime = System.nanoTime();
            BookEntry book = new BookEntry();
            book.link = bookURL;
            try
            {
                if (sqlEntryIsDuplicate(bookURL))
                    throw new SQLException("Duplicate entry: ISBN code exists");

                WebDriver finalDriver = driver;
                performWebActionWithRetries(() -> {
                    finalDriver.get(bookURL);
                    return new ArrayList<>();
                    }, "load book number " + listOfBookURLs.indexOf(bookURL) + " book URL", 3);
                Thread.sleep(400);
                parseTitleAuthor(driver.getTitle(), book);

                WebDriver finalDriver1 = driver;
                WebElement pageContents = performWebActionWithRetries(() -> {
                    String xpathExpression = "//div[@class='Content']";
                    return listGetterXPath(finalDriver1, wait, xpathExpression, true);
                }, "build a list of all of the book page contents", 4).get(0);
                book.genre = parseGenreString(pageContents, wait);

                WebDriver finalDriver2 = driver;
                WebElement titleBlock = performWebActionWithRetries(() -> {
                    String xpathExpression = "//h1[@class='WorkMeta-title Alternative Alternative-title']";
                    return listGetterXPath(finalDriver2, wait, xpathExpression, true);
                }, "locate and parse the title block", 3).get(0);
                if (titleBlock.getText() != null)
                    book.title = titleBlock.getText();

                if (sqlEntryIsDuplicate(book.title.replace("'", "\\'"), book.author.replace("'", "\\'")))
                    throw new SQLException("Duplicate entry: title and author exist");

                WebDriver finalDriver3 = driver;
                WebElement image = performWebActionWithRetries(() -> {
                    String xpathExpression = "//img[@itemprop='image']";
                    return listGetterXPath(finalDriver3,wait,xpathExpression,true);
                }, "store the image link", 4).get(0);
                book.paperbackImageLink = image.getAttribute("src");

                parseButtonContainer(driver, wait, book);

                if (sqlEntryIsDuplicate(book.isbnCode))
                    throw new SQLException("Duplicate entry: ISBN code exists");

                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

                if (!(book.paperbackImageLink == null))
                    book.imageFile = downloadImageFromURL(book.paperbackImageLink,book.title);
                else
                    book.imageFile = "no image";

                String dataEntry = String.format("'%s','%s',%.2f,%.2f,'%s','%s','%s','%s',%d,'%s','%s'",
                        book.title.replace("'", "\\'"), book.author.replace("'", "\\'"), book.usedPrice, book.newPrice, book.genre.replace("'", "\\'"),
                        book.format, book.isbnCode, formatter.format(book.releaseDate),
                        book.pageLength, book.link, book.imageFile);

                insertBookIntoSQLdb(dataEntry);

                logger.info(String.format("Successfully added book number %,d titled \"%s\" to the SQL database", (1 + listOfBookURLs.indexOf(bookURL)), book.title));
                bookSuccesses++;
                lastGoodBook = 1 + listOfBookURLs.indexOf(bookURL);
            }
            catch (NoSuchSessionException e)
            {
                driver = getFFXDriver();
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                String exceptionMsg = "Exception while parsing the page for book number " +
                        (1 + listOfBookURLs.indexOf(bookURL)) + ": " + bookURL + "\n" +
                        e.getMessage() + "\n" + sw;
                errorLogger.error(exceptionMsg);
                errorLogger.info("Last good " + filterPrimary.getDisplayString() + " " + filterGenre.getDisplayString() + " book: " + lastGoodBook);
                logger.info("Last good " + filterPrimary.getDisplayString() + " " + filterGenre.getDisplayString() + " book: " + lastGoodBook);
                sw.close();
                bookFailures++;
            }
            catch (InterruptedException | RuntimeException | SQLException | ClassNotFoundException e)
            {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                String exceptionMsg = "Exception while parsing the page for book number " +
                        (1 + listOfBookURLs.indexOf(bookURL)) + ": " + bookURL + "\n" +
                        e.getMessage();// + "\n" + sw;
                sw.close();
                if (e.getMessage() != null && !(e.getMessage().contains("null")) && e.getMessage().contains("Duplicate entry"))
                {
                    logger.info(exceptionMsg);
                    bookDuplicates++;
                }
                else
                {
                    errorLogger.error(exceptionMsg);
                    bookFailures++;
                }
                errorLogger.info("Last good " + filterPrimary.getDisplayString() + " " + filterGenre.getDisplayString() + " book: " + lastGoodBook);
                logger.info("Last good " + filterPrimary.getDisplayString() + " " + filterGenre.getDisplayString() + " book: " + lastGoodBook);
            }
            finally
            {
                long currentBookEndTime = System.nanoTime();
                long duration = (currentBookEndTime - currentBookStartTime) / 1000000;
                averageTimes[avgIndex] = duration;
                avgIndex++;
                String logStr = String.format("%d / %d successes, %d / %d failures, %d / %d duplicate entries", bookSuccesses, totalBooks, bookFailures, totalBooks, bookDuplicates, totalBooks);
                logger.info(logStr);
                logger.info(getFormattedDurationString((int) duration) + " spent on scraping book " + (1 + listOfBookURLs.indexOf(bookURL)) + "\n\n");
            }
        }

        driver.quit();
        logger.info("WebDriver instance successfully closed");
        logger.info("Web scraping complete: " + bookSuccesses + " books successfully added");
        logger.info(bookFailures + " books could not be processed");
        logger.info(bookDuplicates + " books were duplicate entry attempts to the database");
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


    static void parseButtonContainer(@NotNull WebDriver driver, Wait<WebDriver> wait, BookEntry book) throws InterruptedException, StaleElementReferenceException, TimeoutException, ParseException {

       ArrayList<BookDetails> bookDeets = new ArrayList<>();
        WebElement buttonContainer = performWebActionWithRetries(() -> {
            String xpathExpression = "//div[@class='WorkSelector-row WorkSelector-td-height']";
            return listGetterXPath(driver, wait, xpathExpression, true);
        }, "locate the button container", 2).get(0);

        List<WebElement> buttons = performWebActionWithRetries(() -> {
            String tagName = "button";
            return listGetterTagName(buttonContainer, wait, tagName, false);
        }, "get the list of book binding buttons", 3);

        for (WebElement button : buttons)
        {
            BookDetails tempBookDeets = new BookDetails();
            List<WebElement> buttDetails = performWebActionWithRetries(() -> {
                String xpathExpression = "./child::div";
                return listGetterXPath(button,wait,xpathExpression,false);
            }, "get the button details for a given button", 3);

            for (WebElement buttDetail : buttDetails)
            {
                if (buttDetail.getText().toLowerCase().contains("paperback"))
                    tempBookDeets.buttonName = "Paperback";
                else if (buttDetail.getText().toLowerCase().contains("hardcover"))
                    tempBookDeets.buttonName = "Hardcover";
                else
                    continue;

                WebElement priceRangeElement = performWebActionWithRetries(() -> {
                    String xpathExpression = "./following-sibling::div[@class='']//span";
                    return listGetterXPath(buttDetail,wait,xpathExpression,true);
                }, "identify the " + tempBookDeets.buttonName + " price range", 3).get(0);
                if (priceRangeElement.getText().contains("$"))
                {
                    getNewAndUsedPrices(tempBookDeets, priceRangeElement.getText());
                }
                break;
            }

            performWebActionWithRetries(() -> {
                JavascriptExecutor js = (JavascriptExecutor)driver;
                js.executeScript("arguments[0].click();", button);
                return new ArrayList<>();
            }, "click the " + tempBookDeets.buttonName + " button", 3);

            WebElement table = performWebActionWithRetries(() -> {
                String xpathExpression = "//div[@class='WorkMeta-details is-collapsed']";
                return listGetterXPath(driver,wait,xpathExpression,true);
            }, "find the " + tempBookDeets.buttonName + " book data table", 3).get(0);

            performWebActionWithRetries(() -> {
                parseTableDetails(table, wait, tempBookDeets);
                return new ArrayList<>();
            }, "parse " + tempBookDeets.buttonName + " table details", 3);

            parseTableDetails(table, wait, tempBookDeets);

            bookDeets.add(tempBookDeets);
        }
        book.takeBookDetails(determineBestPriceSet(bookDeets));
    }


    static void parseTableDetails(@NotNull WebElement table, Wait<WebDriver> wait, BookDetails book) throws StaleElementReferenceException, TimeoutException
    {
        List<WebElement> elements = listGetterTagName(table,wait,"span",false);
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

            if (detail.getAttribute("itemprop") != null && book.genre == null && detail.getAttribute("itemprop").toLowerCase().contains("name"))
            {
                book.genre = detail.getText();
            }
        }
    }


    static String parseGenreString(@NotNull WebElement pageContents, Wait<WebDriver> wait) throws InterruptedException
    {
        List<WebElement> spans = performWebActionWithRetries(() -> {
            String tagName = "span";
            return listGetterTagName(pageContents,wait,tagName,false);
        }, "find the genre element at the top of the page", 3);

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
            errorLogger.error("Failed to parse date/time value from text string...");
        }
        return resp;
    }
    //endregion


    //region SQL Methods
    static void insertBookIntoSQLdb(String csvStr) throws FileNotFoundException, ClassNotFoundException, SQLException
    {
        Class.forName("com.mysql.cj.jdbc.Driver");
        String pwSQL = getSQLPassword();
        Connection con = null;
        try
        {
            String insertionQuery = "INSERT INTO books(title,author,used_price,new_price,genre,binding_type,isbn_code,release_date,page_length,bookURL,imageFile) VALUES";
            insertionQuery += "(" + csvStr + ");";

            con = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/thriftBooksDB", "root", pwSQL);
            Statement statement = con.createStatement();
            statement.execute(insertionQuery);
            con.close();
        } catch (SQLException sqlE)
        {
            assert con != null;
            con.close();
            throw sqlE;
        }
    }


    static boolean sqlEntryIsDuplicate(@NotNull String urlStr) throws FileNotFoundException, ClassNotFoundException, SQLException
    {
        String testStr;
        if(urlStr.length() > 13)
        {
            String[] splitStr = urlStr.split("#");
            if (!splitStr[splitStr.length - 1].contains("isbn="))
                return false;
            testStr = splitStr[splitStr.length - 1].replace("isbn=","");
        }
        else
        {
            testStr = urlStr;
        }

        Class.forName("com.mysql.cj.jdbc.Driver");
        String pwSQL = getSQLPassword();
        Connection con = null;
        try
        {
            String queryStr = String.format("SELECT * FROM books WHERE isbn_code = '%s';", testStr);

            con = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/thriftBooksDB", "root", pwSQL);

            Statement statement = con.createStatement();
            ResultSet results = statement.executeQuery(queryStr);

            int resCount = 0;
            while(results.next())
            {
                resCount++;
            }
            con.close();
            return resCount > 0;
        } catch (SQLException sqlE)
        {
            assert con != null;
            con.close();
            throw sqlE;
        }
    }


    static boolean sqlEntryIsDuplicate(@NotNull String title, @NotNull String author) throws FileNotFoundException, ClassNotFoundException, SQLException
    {
        Class.forName("com.mysql.cj.jdbc.Driver");
        String pwSQL = getSQLPassword();
        Connection con = null;
        try
        {
            String queryStr = String.format("SELECT * FROM books WHERE title = '%s' AND author = '%s';", title, author);

            con = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/thriftBooksDB", "root", pwSQL);

            Statement statement = con.createStatement();
            ResultSet results = statement.executeQuery(queryStr);

            int resCount = 0;
            while(results.next())
            {
                resCount++;
            }
            con.close();
            return resCount > 0;
        } catch (SQLException sqlE)
        {
            assert con != null;
            con.close();
            throw sqlE;
        }
    }


    static String getSQLPassword() throws FileNotFoundException
    {
        File pwFile = new File("pw.txt");
        Scanner pwFileScan = new Scanner(pwFile);
        String resp = pwFileScan.nextLine();
        pwFileScan.close();
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


    static @NotNull String downloadImageFromURL(@NotNull String imageURL, @NotNull String bookTitle)
    {
        String cleanBookTitle = bookTitle.replace(":"," -").replace("'","");
        if (imageURL.equalsIgnoreCase("null"))
        {
            errorLogger.error("No image URL was found");
            return "no image";
        }

        File fileOutputStr =  new File("images/" + cleanBookTitle + ".jpg");

        try (BufferedInputStream in = new BufferedInputStream(new URI(imageURL).toURL().openStream());
             FileOutputStream fileOutputStream = new FileOutputStream(fileOutputStr))
        {
            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1)
            {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        }
        catch (IOException | InvalidPathException | URISyntaxException e)
        {
            errorLogger.error("Failed to create image file: " + e.getMessage());
            return "no image";
        }

        return cleanBookTitle + ".jpg";
    }
    //endregion


    //region User Interaction Methods
    static int getFirstOrLastPage(Scanner keyboard, @NotNull String page)
    {
        String introStr, errorStr;
        int resp;
        if (page.equalsIgnoreCase("first"))
        {
            introStr = "Enter the first page you want to read, or enter 0 to exit: ";
            errorStr = "First";
        }
        else
        {
            introStr = "Enter the last page you want to read: ";
            errorStr = "Last";
        }
        do
        {
            System.out.println(introStr);
            resp = keyboard.nextInt();
            if (resp < 0)
                System.out.println("\t**ERROR: " + errorStr + " page number must be at least 1");
            else if (resp > 333)
                System.out.println("\t**ERROR: " + errorStr + " page number cannot be more than 333");
        }
        while (resp < 0 || resp > 333);

        keyboard.nextLine();
        return resp;
    }


    static PrimaryFilter getPrimaryFilter(Scanner keyboard)
    {
        System.out.println("Enter the number of which way you'd like the pages to be filtered: ");

        for (PrimaryFilter g : PrimaryFilter.values())
        {
            System.out.printf("[%d]: %s\n", g.ordinal(), g.getDisplayString());
        }
        int selection = keyboard.nextInt();

        while (selection < 0 || selection > PrimaryFilter.values().length)
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
    //endregion


    //region Misc Algorithms and Utility Methods
    static void getNewAndUsedPrices(@NotNull BookDetails respBook, @NotNull String priceString)
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


    static BookDetails determineBestPriceSet(@NotNull ArrayList<BookDetails> bookDeets) throws ParseException {
        boolean[] removeIndices = new boolean[bookDeets.size()];
        BookDetails tempBookDeets = bookDeets.get(0);

        for (int i = 0; i < bookDeets.size(); i++)
        {
            if (bookDeets.get(i).newPrice <= 0)
                removeIndices[i] = true;

            if (bookDeets.get(i).isbnCode.length() < 10)
                removeIndices[i] = true;

            if (bookDeets.get(i).pageLength < 5)
                removeIndices[i] = true;
        }

        for (int i = bookDeets.size() - 1; i >= 0; i--)
        {
            if (bookDeets.size() > 1)
            {
                if (removeIndices[i])
                {
                    bookDeets.remove(i);
                }
            }
        }

        if (bookDeets.size() == 1)
        {
            return bookDeets.get(0);
        }

        BookDetails returnBookDeets = new BookDetails();
        returnBookDeets.newPrice = 1000000000;
        returnBookDeets.usedPrice = 1000000000;

        for (int i = 0; i <= bookDeets.size() - 1; i++)
        {
            if (bookDeets.get(i).newPrice < returnBookDeets.newPrice)
            {
                returnBookDeets = bookDeets.get(i);
            }
        }

        if (returnBookDeets.usedPrice == 1000000000)
        {
            returnBookDeets = tempBookDeets;
        }
        return returnBookDeets;
    }


    static @NotNull WebDriver getFFXDriver()
    {
        System.setProperty("webdriver.gecko.driver",GECKO_DRIVER);
        FirefoxOptions ffxOptions = new FirefoxOptions();
        FirefoxProfile profile = new FirefoxProfile();
        profile.setPreference("browser.cache.disk.enable",false);
        profile.setPreference("browser.cache.memory.enable",false);
        profile.setPreference("browser.cache.offline.enable",false);
        profile.setPreference("network.http.use-cache",false);
        ffxOptions.setCapability(FirefoxDriver.PROFILE, profile);
        ffxOptions.setHeadless(true);
        return new FirefoxDriver(ffxOptions);
    }


    static @NotNull String getFormattedDurationString(int duration)
    {
        int millis = 0, seconds = 0, minutes = 0, hours = 0, days = 0;
        String timeString = "";

        if (duration > 1000)
        {
            seconds = (duration / 1000);
            millis = (duration % 1000);
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
        if (hours > 24)
        {
            days = hours / 24;
            hours = hours % 24;
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


    @Contract(pure = true)
    static long getAverageTime(long @NotNull [] averageTimes)
    {
        long sum = 0;
        for (long time : averageTimes)
        {
            sum += time;
        }
        return sum / averageTimes.length;

    }
    //endregion
}