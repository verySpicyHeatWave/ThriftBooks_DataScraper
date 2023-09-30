package veryspicyheatwave.bwb_datascraper;

public enum PrimaryFilter
{
    MOST_POPULAR("/browse/?b.search=#b.s=mostPopular-desc&b.p=", "Most Popular"),
    BEST_SELLER("/browse/?b.search=#b.s=bestsellers-desc&b.p=", "Best Selling");

    private final String filterString;
    private final String displayString;

    PrimaryFilter(String filterString, String displayString)
    {
        this.filterString = filterString;
        this.displayString = displayString;
    }

    public String getFilterString()
    {
        return filterString;
    }
    public String getDisplayString()
    {
        return displayString;
    }
}
