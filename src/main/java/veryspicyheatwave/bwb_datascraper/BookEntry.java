package veryspicyheatwave.bwb_datascraper;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

class BookEntry
{
    String title;
    String author;
    String isbnCode;
    Date releaseDate;
    int pageLength;
    String genre;
    String format;
    double newPrice;
    double usedPrice;
    String link;
    String paperbackImageLink;
    String imageFile;
    String buttonName;


    BookEntry () throws ParseException
    {
        this.releaseDate = new SimpleDateFormat("MM-yyyy").parse("01-0001");
    }

    void takeBookDetails(BookDetails bookDeets)
    {
        this.isbnCode = bookDeets.isbnCode;
        this.releaseDate = bookDeets.releaseDate;
        this.pageLength = bookDeets.pageLength;
        this.format = bookDeets.buttonName;
        this.newPrice = bookDeets.newPrice;
        this.usedPrice = bookDeets.usedPrice;
        this.buttonName = bookDeets.buttonName;
    }
}