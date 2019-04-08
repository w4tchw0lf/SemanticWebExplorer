/**
 *
 * @author Cristian Talavera
 */
public class ResultItem {
    
    private final String className;
    private final String individual;
    private final String property;
    private final String data;

    public ResultItem(String className, String individual, String property, String data) {
        this.className = className;
        this.individual = individual;
        this.property = property;
        this.data = data;
    }

    public String getClassName() {
        return className;
    }

    public String getIndividual() {
        return individual;
    }

    public String getProperty() {
        return property;
    }

    public String getData() {
        return data;
    }
    
}
