/**
 *
 * @author Cristian Talavera
 */
public class HistoryItem {
    
    private final String searchItem;
    private final String searchDate;
    private final int numRows;
    private final long queryTime;

    public HistoryItem(String searchItem, String searchDate, int numRows, long queryTime) {
        this.searchItem = searchItem;
        this.searchDate = searchDate;
        this.numRows = numRows;
        this.queryTime = queryTime;
    }

    public String getSearchItem() {
        return searchItem;
    }

    public String getSearchDate() {
        return searchDate;
    }

    public int getNumRows() {
        return numRows;
    }

    public long getQueryTime() {
        return queryTime;
    }
    
}
