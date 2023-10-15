package veryspicyheatwave.bwb_datascraper;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

class BookDetails
{
    String isbnCode;
    Date releaseDate;
    int pageLength;
    double newPrice;
    double usedPrice;
    String buttonName;
    String genre;


    BookDetails() throws ParseException
    {
        this.releaseDate = new SimpleDateFormat("MM-yyyy").parse("01-0001");
    }
}