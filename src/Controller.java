import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.semanticweb.HermiT.Reasoner;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.util.InferredAxiomGenerator;
import org.semanticweb.owlapi.util.InferredOntologyGenerator;
import org.semanticweb.owlapi.util.InferredSubClassAxiomGenerator;


/**
 * Main Class where all the functionality of the application is defined 
 * 
 * @author Cristian Talavera
 */
public class Controller implements Initializable {

    /* Form Elements */
    /** Control Elements **/
    @FXML private Button btnSearch;
    @FXML private Button btnClear;
    @FXML private Button btnCancel;
    @FXML private TextField txtSearch;
    @FXML private Label lblNetwork;
    @FXML private ProgressIndicator progressIndicator;

    /** View Elements **/    
    @FXML private TableView<ResultItem> tableResults;
    @FXML private TableColumn<?, ?> colClass;
    @FXML private TableColumn<?, ?> colIndividual;
    @FXML private TableColumn<?, ?> colObjectProperty;
    @FXML private TableColumn<?, ?> colData;
    
    @FXML private TableView<HistoryItem> tableHistory;
    @FXML private TableColumn<?, ?> colSearch;
    @FXML private TableColumn<?, ?> colDate;
    @FXML private TableColumn<?, ?> colRows;
    @FXML private TableColumn<?, ?> colTime;
    
    /** Configuration Elements **/
    @FXML private TextField txtLimit;
    
    private final ToggleGroup searchGroup = new ToggleGroup();
    @FXML private RadioButton rbtnRegex;
    @FXML private RadioButton rbtnContains;
    @FXML private RadioButton rbtnExactly;

    private final ToggleGroup resourceGroup = new ToggleGroup();
    @FXML private RadioButton rbtnDBpedia;
    @FXML private RadioButton rbtnUniProt;
    @FXML private RadioButton rbtnWikiData;
    
    /* Program Variables */
    private final SimpleDateFormat dateFormat;
    private OWLReasoner reasoner;
    private OWLOntology localOnt;

    private int numRows = 0;
    private Instant startInstant;
    private Task asyncTask;
    
    public Controller() {
        this.dateFormat = new SimpleDateFormat("hh:mm:ss dd/MM/yyyy");
        try {
            // Load local ontology
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            OWLReasonerFactory reasonerFactory = new Reasoner.ReasonerFactory();

            File input = new File("localOntology.owl");
            File output = new File("localOntology[inferred].owl");
            
            System.out.println("["+dateFormat.format(new Date())+"]\tLoading local ontology");
            this.localOnt = manager.loadOntologyFromOntologyDocument(IRI.create(input.toURI()));
            this.reasoner = reasonerFactory.createNonBufferingReasoner(localOnt);

            // If not inferred before, infer local ontology and save it
            if (!output.exists()) {
                System.out.println("["+dateFormat.format(new Date())+"]\tInferring local ontology");
                this.reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
                List<InferredAxiomGenerator<? extends OWLAxiom>> gens = new ArrayList<>();
                gens.add(new InferredSubClassAxiomGenerator());

                InferredOntologyGenerator iog = new InferredOntologyGenerator(reasoner, gens);
                iog.fillOntology(manager, localOnt);

                System.out.println("[" + dateFormat.format(new Date()) + "]\tSaving inferred ontology");
                manager.saveOntology(localOnt, IRI.create(output.toURI()));
            } 
            // Load inferred ontology
            System.out.println("[" + dateFormat.format(new Date()) + "]\tLoading inferred ontology");
            manager = OWLManager.createOWLOntologyManager();
            this.localOnt = manager.loadOntologyFromOntologyDocument(IRI.create(output.toURI()));
        } catch (OWLOntologyCreationException | OWLOntologyStorageException ex) {
            showDialogError("Local ontology load/inference error", ex);
        }
    }
    
    /************************ Initialization Functions ************************/
    /**
     * Initializes graphical components of the application
     * 
     * @param url
     * @param rb
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        System.out.println("["+dateFormat.format(new Date())+"]\tInitializing components");
        progressIndicator.setVisible(false);
        
        rbtnRegex.setToggleGroup(searchGroup);
        rbtnRegex.setSelected(true);
        rbtnContains.setToggleGroup(searchGroup);
        rbtnExactly.setToggleGroup(searchGroup);
       
        rbtnDBpedia.setToggleGroup(resourceGroup);
        rbtnDBpedia.setSelected(true);
        rbtnUniProt.setToggleGroup(resourceGroup);
        rbtnWikiData.setToggleGroup(resourceGroup);
        
        colSearch.setCellValueFactory(new PropertyValueFactory<>("searchItem"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("searchDate"));
        colRows.setCellValueFactory(new PropertyValueFactory<>("numRows"));
        colTime.setCellValueFactory(new PropertyValueFactory<>("queryTime"));
        
        colClass.setCellValueFactory(new PropertyValueFactory<>("className"));
        colIndividual.setCellValueFactory(new PropertyValueFactory<>("individual"));
        colObjectProperty.setCellValueFactory(new PropertyValueFactory<>("property"));
        colData.setCellValueFactory(new PropertyValueFactory<>("data"));
        
        btnSearch.setOnAction((ActionEvent evt) -> {
            try {
                if (txtSearch.getText().isEmpty()) {
                    Alert alert = new Alert(AlertType.INFORMATION);
                    alert.setTitle("Information Dialog");
                    alert.setHeaderText(null);
                    alert.setContentText("Please, input a search term.");
                    alert.show();
                }
                String searchTerm = txtSearch.getText();
                Date searchDate = new Date();
                
                System.out.println("["+dateFormat.format(searchDate)+"]\tSearched term: " + searchTerm);
                
                startInstant = Instant.now();
                makeSearch(searchTerm);
            } catch (IOException | ParseException ex) {
                showDialogError("Searching error", ex);
            }
            progressIndicator.setVisible(false);
        });
        
        btnClear.setOnAction((ActionEvent evt) -> {
            tableResults.getItems().clear();
        });
        
        btnCancel.setOnAction((ActionEvent evt) -> {
            progressIndicator.setVisible(false);
            if (asyncTask != null) {
                asyncTask.cancel(true);
            }
        });
        
        txtSearch.setOnKeyPressed((evt) -> {
            if (evt.getCode() == KeyCode.ENTER) {
                btnSearch.fire();
            }
        });

        checkInternetConnection();
    }
    
    /************************ Search Functions ************************/
    /**
     * Make a local search of a specified term in the local ontology configured,
     * and a online search in the SPARQL endpoint configured
     * 
     * @param searchTerm
     * @throws Exception
     */
    private void makeSearch(String searchTerm) throws FileNotFoundException, IOException, ParseException {  
        numRows = 0;
        tableResults.getItems().clear();
        
        offlineSearch(searchTerm);
        tableResults.refresh();      
        
        if (lblNetwork.getText().equals("Online") && !txtSearch.getText().isEmpty()) {
            asyncTask = new Task() {
                @Override
                protected Object call() throws Exception {
                    progressIndicator.setVisible(true);
                    String endpoint = ((RadioButton) resourceGroup.getSelectedToggle()).getText();
                    switch (endpoint) {
                        case "DBpedia":
                            OnlineQuery.dbpedia(
                                    searchTerm,
                                    ((RadioButton) searchGroup.getSelectedToggle()).getText(),
                                    Integer.parseInt(txtLimit.getText())
                            );
                            break;
                        case "UniProt":
                            OnlineQuery.uniprot(
                                    searchTerm,
                                    ((RadioButton) searchGroup.getSelectedToggle()).getText(),
                                    Integer.parseInt(txtLimit.getText())
                            );
                            break;
                        case "WikiData":
                            OnlineQuery.wikidata(
                                    searchTerm,
                                    ((RadioButton) searchGroup.getSelectedToggle()).getText(),
                                    Integer.parseInt(txtLimit.getText())
                            );
                    }
                    return null;
                }
            };
            asyncTask.setOnSucceeded((evt) -> { onlineSearchFinished(searchTerm); });
            new Thread(asyncTask).start();
        } else {
            tableHistory.getItems().add(new HistoryItem(searchTerm, dateFormat.format(new Date()), numRows, Duration.between(startInstant, Instant.now()).getSeconds()));
        }
    }
    
    /**
     * Parses the resulting file of the query, adding the elements in it to the
     * table view. Saves an entry in the table history too, with the time of the query
     * and the number of rows
     * 
     * @param searchTerm 
     */
    private void onlineSearchFinished(String searchTerm) {
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonFile = (JSONObject) parser.parse(new FileReader("result.json"));
            JSONObject results = (JSONObject) jsonFile.get("results");
            JSONArray bindings = (JSONArray) results.get("bindings");
            
            for (Object object : bindings) {
                JSONObject element = (JSONObject) object;
                
                JSONObject individual = (JSONObject) element.get("Individual");
                String individualStr = (String) individual.get("value");
                
                JSONObject property = (JSONObject) element.get("Property");
                String propertyStr = (String) property.get("value");
                
                JSONObject value = (JSONObject) element.get("Value");
                String valueStr = (String) value.get("value");
                System.out.println("\t Online [individual: " + individualStr + ", property: " + propertyStr + ", value: " + valueStr+ "]");
                ++numRows;
                
                String rootClass = "owl:Thing"; // --> Root class for DBpedia and UniProt
                if (((RadioButton) resourceGroup.getSelectedToggle()).getText().equals("WikiData"))
                    rootClass = "wd:Q1";
                    
                tableResults.getItems().add(new ResultItem(rootClass, individualStr, propertyStr, valueStr));
            }
            new File("result.json").delete();
            
            long queryTime = Duration.between(startInstant, Instant.now()).getSeconds();
            tableHistory.getItems().add(new HistoryItem(searchTerm, dateFormat.format(new Date()), numRows, queryTime));
            System.out.println("["+dateFormat.format(new Date())+"]\tSearching of term: " + searchTerm + " finished in " + queryTime + " seconds");
            progressIndicator.setVisible(false);
        } catch (IOException | ParseException ex) {
            showDialogError("Parse/Read results file error", ex);
        }
    }
    
    /**
     * Search in the local ontology the specified term, making a normalization
     * to lowercase of the elements to be compared
     * 
     * @param searchTerm
     */
    private void offlineSearch(String searchTerm) {
        
        // Get all the classes of the ONTOLOGY and iterate on them
        for (OWLClass _class : localOnt.getClassesInSignature()) {
            String className = _class.getIRI().getFragment();

            NodeSet<OWLNamedIndividual> individualsNodeSet = reasoner.getInstances(_class, true);
            Set<OWLNamedIndividual> individuals = individualsNodeSet.getFlattened();

            if (className.toLowerCase().contains(searchTerm.toLowerCase())) { // --> CLASS search filter
                ++numRows;
                tableResults.getItems().add(new ResultItem(className, "", "", ""));
            }
            
            // Get all the individuals of the CLASS and iterate on them
            for (OWLNamedIndividual individual : individuals) {
                String indIRI = individual.toString();
                String individualName = indIRI.substring(indIRI.indexOf("#") + 1, indIRI.length() - 1);

                if (className.toLowerCase().contains(searchTerm.toLowerCase())
                        || individualName.toLowerCase().contains(searchTerm.toLowerCase())) { // --> INDIVIDUAL search filter
                    ++numRows;
                    System.out.println("\tOffline individual [individual: " + individualName + ", property: , value: ]");
                    tableResults.getItems().add(new ResultItem(className, individualName, "", ""));
                }
                
                // Get all the data properties of the INDIVIDUAL and iterate on them    
                for (OWLDataPropertyAssertionAxiom dataProperty : localOnt.getDataPropertyAssertionAxioms(individual)) {
                    String s1 = dataProperty.getProperty().toString();
                    String objectPropertyName = s1.substring(s1.indexOf("#") + 1, s1.length() - 1);

                    if (className.toLowerCase().contains(searchTerm.toLowerCase())
                            || individualName.toLowerCase().contains(searchTerm.toLowerCase())
                            || objectPropertyName.toLowerCase().contains(searchTerm.toLowerCase())
                            || dataProperty.getObject().getLiteral().toLowerCase().contains(searchTerm.toLowerCase())) { // --> DATA PROPERTIES search filter
                        ++numRows;
                        System.out.println("\tOffline data property [individual: " + individualName + ", property: " + objectPropertyName + ", value: " + dataProperty.getObject().getLiteral() + "]");
                        tableResults.getItems().add(new ResultItem(className, individualName, objectPropertyName, dataProperty.getObject().getLiteral()));
                    }
                }

                // Get all the object properties of the INDIVIDUAL and iterate on them 
                for (OWLObjectPropertyAssertionAxiom objectProperty : localOnt.getObjectPropertyAssertionAxioms(individual)) {
                    String s1 = objectProperty.getProperty().toString();
                    String s2 = objectProperty.getObject().toString();
                    String objectPropertyName = s1.substring(s1.indexOf("#") + 1, s1.length() - 1);
                    String objectPropertyValue = s2.substring(s2.indexOf("#") + 1, s2.length() - 1);

                    if (className.toLowerCase().contains(searchTerm.toLowerCase())
                            || individualName.toLowerCase().contains(searchTerm.toLowerCase())
                            || objectPropertyName.toLowerCase().contains(searchTerm.toLowerCase())
                            || objectPropertyValue.toLowerCase().contains(searchTerm.toLowerCase())) { // --> OBJECT PROPERTIES search filter
                        ++numRows;
                        System.out.println("\tOffline object property [individual: " + individualName + ", property: " + objectPropertyName + ", value: " + objectPropertyValue + "]");
                        tableResults.getItems().add(new ResultItem(className, individualName, objectPropertyName, objectPropertyValue));
                    }
                }
            }
        }
    }
    
    /************************ Auxiliar Functions ************************/
    /**
     * Make an HTTP request to dbpedia, uniprot and wikidata to check if online queries are disponible
     */
    private void checkInternetConnection() {
        try {
            new URL("http://dbpedia.org").openConnection().connect();
            new URL("https://uniprot.org").openConnection().connect();
            new URL("https://wikidata.org").openConnection().connect();
            lblNetwork.setText("Online");
        } catch (IOException ex) {
            lblNetwork.setText("Offline");
        }
    }
    
    /**
     * Show a dialog error, to notify the user that something went wrong
     * 
     * @param errorID String that identifies the generic type of the error
     * @param ex Exception from which the detail of the error is shown
     */
    public static void showDialogError(String errorID, Exception ex) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(errorID);
        alert.setHeaderText(null);
        alert.setContentText(ex.getMessage());
        alert.setResizable(true);
        alert.showAndWait();
    }
    
}
