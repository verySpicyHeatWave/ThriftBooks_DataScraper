package veryspicyheatwave.bwb_datascraper;

enum Genre
{

    SCI_FI(15011, "Science Fiction"),
    FANTASY (13184, "Fantasy"),
    LITERATURE (13902, "Literary Fiction"),
    YOUNG_ADULT (15370, "Young Adult Fiction"),
    ROMANCE (14929, "Romance"),
    MYSTERY (14236, "Mystery"),
    HORROR (13564, "Horror"),
    CONTEMPORARY (12667, "Contemporary Fiction"),
    HUMOR (13599, "Humor and Entertainment"),
    COMICS (12596, "Graphic Novels"),
    CHILDREN (12466, "Children's Books"),
    HISTORY (13526, "History"),
    RELIGION (14858, "Religion and Spirituality"),
    PHILOSOPHY (14569, "Philosophy"),
    POLITICAL_SCIENCE (14641, "Political Science"),
    ECONOMICS (12996, "Economics"),
    BIOGRAPHICAL (12206, "Biographies"),
    BUSINESS (12319, "Business"),
    SELF_HELP (15049, "Self-Help Books"),
    LAW (13829, "Law Books"),
    ART (12045, "Art Books"),
    PROGRAMMING(12637, "Programming");

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