package veryspicyheatwave.bwb_datascraper;

class PriceStructure
{
    double newPrice;
    double usedPrice;
    String format;
    String buttonName;


    PriceStructure (String format, String buttonName)
    {
        this.format = format;
        this.buttonName = buttonName;
    }
}