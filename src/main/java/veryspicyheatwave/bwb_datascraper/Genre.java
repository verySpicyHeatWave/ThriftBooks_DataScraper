package veryspicyheatwave.bwb_datascraper;

enum Genre
{
    LITERATURE (13902, "Literary Fiction"),
    HISTORY (13526, "History"),
    RELIGION (14858, "Religion and Spirituality"),
    CHILDREN (12466, "Children's Books"),
    BIOGRAPHICAL (12206, "Biographies"),
    BUSINESS (12319, "Business"),
    SELF_HELP (15049, "Self-Help Books"),
    POLITICAL_SCIENCE (14641, "Political Science"),
    PHILOSOPHY (14569, "Philosophy"),
    ECONOMICS (12996, "Economics"),
    LAW (13829, "Law Books"),
    SCI_FI(15011, "Science Fiction"),
    FANTASY (13184, "Fantasy"),
    COMICS (12596, "Graphic Novels"),
    YOUNG_ADULT (15370, "Young Adult Fiction"),
    ART (12045, "Art Books"),
    HORROR (13564, "Horror"),
    HUMOR (13599, "Humor and Entertainment"),
    PROGRAMMING(12637, "Programming"),
    ROMANCE (14929, "Romance"),
    MYSTERY (14236, "Mystery"),
    CONTEMPORARY (12667, "Contemporary Fiction");

    private final int filterNo;
    private final String displayString;

    Genre(int filterNo, String displayString)
    {
        this.filterNo = filterNo;
        this.displayString = displayString;
    }

    public String getDisplayString()
    {
        return displayString;
    }

    public int getFilterNo()
    {
        return filterNo;
    }
}