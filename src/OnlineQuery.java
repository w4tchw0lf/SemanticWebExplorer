import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.ResultSetFormatter;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Scanner;


/**
 * Class that contains all static methods used to query online SPARQL endpoints
 * 
 * @author Cristian Talavera
 */
// NOTE: I can't make use of federated queries to https endpoints due
// to an error in the Apache Jena library version that i'm using, so, for that reason, 
// i made that using HTTP requests.
public class OnlineQuery {

    /**
     * Query the DBpedia SPARQL endpoint: http://dbpedia.org/sparql
     * 
     * @param searchTerm String text to be searched
     * @param searchCondition String condition to create the search filter
     * @param rowLimit Integer that limits the number of rows to get
     */
    public static void dbpedia(String searchTerm, String searchCondition, int rowLimit) {
        // Initialize personalized filter pattern
        String searchFilter;
        switch (searchCondition) {
            case "RegEx":
                searchFilter = "FILTER REGEX (?Name, \"" + searchTerm + "\")\n";
                break;
            case "Contains Text":
                searchFilter = "FILTER CONTAINS (?Name, \"" + searchTerm + "\")\n";
                break;
            case "Exactly":
                searchTerm = searchTerm.replace(" ", "_");
                searchFilter = "FILTER (?Individual=dbr:" + searchTerm + ")\n";
                break;
            default:
                searchFilter = "FILTER CONTAINS (?Name, \"" + searchTerm + "\")\n";
        }

        // Create generic query and initialize ontology model
        OntModel model = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM_MICRO_RULE_INF);
        String dbpediaQuery
                = "     PREFIX owl: <http://www.w3.org/2002/07/owl#>"
                + "     PREFIX dbo:<http://dbpedia.org/ontology/>\n"
                + "     PREFIX dbr: <http://dbpedia.org/resource/>\n"
                + "     PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
                + "     PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
                + "     SELECT DISTINCT ?Individual ?Property ?Value\n"
                + "     WHERE {\n"
                + "         SERVICE <http://dbpedia.org/sparql> {\n"
                + "             ?Individual a owl:Thing .\n"
                + "             ?Individual rdfs:label ?Name . \n"
                + "             ?Individual ?Property ?Value .\n"
                + "             FILTER (?Property NOT IN (rdf:type,rdfs:label,rdfs:comment,dbo:abstract,owl:sameAs,dbo:wikiPageRevisionID,dbo:wikiPageID))\n"
                + "             " + searchFilter
                + "             FILTER langMatches(lang(?Name),'en')\n"
                + "         }\n"
                + "     } LIMIT " + rowLimit;

        // Execute the query and obtain results
        Query query = QueryFactory.create(dbpediaQuery);
        QueryExecution qe = QueryExecutionFactory.create(query, model);
        ResultSet results = qe.execSelect();

        // Save query results (file will be processed and deleted later)
        File file = new File("result.json");
        try (FileOutputStream fop = new FileOutputStream(file)) {
            if (!file.exists()) {
                file.createNewFile();
            }

            ResultSetFormatter.outputAsJSON(fop, results);
            fop.flush();
            fop.close();
        } catch (Exception ex) {
            Controller.showDialogError("Parse/Read results file error", ex);
        }
    }
    
    
    /**
     * Query the UniProt SPARQL endpoint: https://sparql.uniprot.org/sparql
     * 
     * @param searchTerm String text to be searched
     * @param searchCondition String condition to create the search filter
     * @param rowLimit Integer that limits the number of rows to get
     */
    public static void uniprot(String searchTerm, String searchCondition, int rowLimit) {
        try {
            // Initialize personalized filter pattern
            String searchFilter;
            switch (searchCondition) {
                case "RegEx":
                    searchFilter = "FILTER REGEX (?Name, \"" + searchTerm + "\")\n";
                    break;
                case "Contains Text":
                    searchFilter = "FILTER CONTAINS (?Name, \"" + searchTerm + "\")\n";
                    break;
                case "Exactly":
                    searchFilter = "FILTER (?Name=" + searchTerm + ")\n";
                    break;
                default:
                    searchFilter = "FILTER CONTAINS (?Name, \"" + searchTerm + "\")\n";
            }
            
            // Create generic query 
            String uniprotHTTPQuery 
                    = "SELECT DISTINCT ?Individual ?Property ?Value \n"
                    + "WHERE {\n"
                    + "     ?Individual a owl:Thing .\n"
                    + "     ?Individual rdfs:label ?Name .\n"
                    + "     ?Individual ?Property ?Value .\n"
                    + "     FILTER (?Property NOT IN (rdf:type,rdfs:label,rdfs:comment,owl:sameAs))\n"
                    + "     " + searchFilter
                    + "} LIMIT " + rowLimit;
            
            // Execute the query and obtain results
            URL url = new URL("https://sparql.uniprot.org/sparql?query="+URLEncoder.encode(uniprotHTTPQuery)+"&format=srj");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            String response = new Scanner(conn.getInputStream()).useDelimiter("\\A").next();
            
            // Save query results (file will be processed and deleted later)
            File file = new File("result.json");
            try (FileOutputStream fop = new FileOutputStream(file)) {
                fop.write(response.getBytes());
                fop.flush();
            }
        } catch (IOException ex) {
            Controller.showDialogError("Parse/Read results file error", ex);
        }
    }
    
    /**
     * Query the WikiData SPARQL endpoint: https://query.wikidata.org/sparql
     * 
     * @param searchTerm String text to be searched
     * @param searchCondition String condition to create the search filter
     * @param rowLimit Integer that limits the number of rows to get
     */
    public static void wikidata(String searchTerm, String searchCondition, int rowLimit) {
        try {
            // Initialize personalized filter pattern
            String searchFilter;
            switch (searchCondition) {
                case "RegEx":
                    searchFilter = "FILTER (REGEX (?Individual, \"" + searchTerm + "\") || REGEX (?Name2, \""+searchTerm +"\")) \n";
                    break;
                case "Contains Text":
                    searchFilter = "FILTER (CONTAINS (?Individual, \"" + searchTerm + "\") || CONTAINS (?Name2, \""+searchTerm +"\")) \n";
                    break;
                case "Exactly":
                    searchFilter = "FILTER ((?Individual=\"" + searchTerm + "\") || (?Name2=\""+searchTerm +"\")) \n";
                    break;
                default:
                    searchFilter = "FILTER (CONTAINS (?Individual, \"" + searchTerm + "\") || CONTAINS (?Name2, \""+searchTerm +"\")) \n";
            }
            
            // Create generic query 
            String wikidataHTTPQuery 
                    = "SELECT DISTINCT ?Individual ?Property ?Value WHERE {\n"
                    + "    ?Object ?Property ?Value .\n"
                    + "    ?Object rdfs:label ?Individual .\n"
                    + "    ?Object skos:altLabel ?Name2 .\n"
                    + "    FILTER langMatches(lang(?Individual),'en')\n"
                    + "    FILTER langMatches(lang(?Name2),'en')\n"
                    + "    " + searchFilter
                    + "    FILTER (?Property NOT IN (rdf:type,rdfs:label,rdfs:comment,owl:sameAs,schema:description,schema:version,schema:dateModified,wikibase:statements,\n"
                    + "                            skos:altLabel,wdt:P1843,wdt:P1417,wikibase:identifiers,wikibase:timestamp,wdt:P39))\n"
                    + "} LIMIT " + rowLimit;
            
            // Execute the query and obtain results (all headers sent are mandatory)
            URL url = new URL("https://query.wikidata.org/sparql");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Host", "query.wikidata.org");
            conn.setRequestProperty("Accept", "application/sparql-results+json");
            conn.setRequestProperty("Referer", "https://query.wikidata.org/");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(("query="+URLEncoder.encode(wikidataHTTPQuery)).getBytes());
                os.flush();
            }
            String response = new Scanner(conn.getInputStream()).useDelimiter("\\A").next();
            
            // Save query results (file will be processed and deleted later)
            File file = new File("result.json");
            try (FileOutputStream fop = new FileOutputStream(file)) {
                fop.write(response.getBytes());
                fop.flush();
            }
        } catch (IOException ex) {
            Controller.showDialogError("Parse/Read results file error", ex);
        }
    }
    
}
