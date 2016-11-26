/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.gpl.siddhi.extension.pmml;


import com.fuzzylite.term.Gaussian;
import org.dmg.pmml.*;
import org.jpmml.evaluator.*;
import org.jpmml.evaluator.OutputField;
import org.wso2.carbon.analytics.api.AnalyticsDataAPI;
import org.wso2.carbon.analytics.dataservice.commons.AnalyticsDataResponse;
import org.wso2.carbon.analytics.dataservice.commons.SearchResultEntry;
import org.wso2.carbon.analytics.dataservice.core.AnalyticsDataServiceUtils;
import org.wso2.carbon.analytics.datasource.commons.Record;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.gpl.siddhi.extension.pmml.algorithms.*;
import org.wso2.gpl.siddhi.extension.pmml.algorithms.Constant;
import org.wso2.siddhi.core.config.ExecutionPlanContext;
import org.wso2.siddhi.core.event.ComplexEventChunk;
import org.wso2.siddhi.core.event.stream.StreamEvent;
import org.wso2.siddhi.core.event.stream.StreamEventCloner;
import org.wso2.siddhi.core.event.stream.populater.ComplexEventPopulater;
import org.wso2.siddhi.core.exception.ExecutionPlanCreationException;
import org.wso2.siddhi.core.exception.ExecutionPlanRuntimeException;
import org.wso2.siddhi.core.executor.ConstantExpressionExecutor;
import org.wso2.siddhi.core.executor.ExpressionExecutor;
import org.wso2.siddhi.core.executor.VariableExpressionExecutor;
import org.wso2.siddhi.core.query.processor.Processor;
import org.wso2.siddhi.core.query.processor.stream.StreamProcessor;
import org.wso2.siddhi.query.api.definition.AbstractDefinition;
import org.wso2.siddhi.query.api.definition.Attribute;
import org.wso2.siddhi.query.api.exception.ExecutionPlanValidationException;
import org.xml.sax.InputSource;


import com.fuzzylite.Engine;
import com.fuzzylite.FuzzyLite;
import com.fuzzylite.Op;
import com.fuzzylite.rule.Rule;
import com.fuzzylite.rule.RuleBlock;
import com.fuzzylite.term.Triangle;
import com.fuzzylite.variable.InputVariable;
import com.fuzzylite.variable.OutputVariable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jpmml.model.ImportFilter;
import org.jpmml.model.JAXBUtil;

import java.io.*;
import java.util.*;

import javax.xml.transform.Source;

public class PmmlModelProcessor extends StreamProcessor {

    private static final Log logger = LogFactory.getLog(PmmlModelProcessor.class);

    final int INDEX_ALGO_META_PHONE = 2;

    public static final int INDEX_ALGO_SOUNDEX = 1;
    public static final int INDEX_ALGO_DOUBLE_META_PHONE = 3;
    public static final int INDEX_ALGO_MATCH_RATING = 5;
    public static final int MIN_INDEX_LENGTH = 6;
    private static CustomMatchingUtility customMatching;
    private  static FortuneCompaniesUtility fortuneUtil;

    private ArrayList<String[]> currentCustomer;

    private String pmmlDefinition;
    private String pmmlDefinition2;
    private String lookupTable;
    private boolean attributeSelectionAvailable;
    private Map<InputField, int[]> attributeIndexMap;           // <feature-name, [event-array-type][attribute-index]> pairs
    
    private List<InputField> inputFields;        // All the input fields defined in the pmml definition
    private List<OutputField> outputFields;// Output fields of the pmml definition
    private Evaluator singlePointEvaluator, windowEvaluator;
    private static TitleUtility titleUtil = new TitleUtility();
    private Engine engine = new Engine();


    private InputVariable singlePoint = new InputVariable();
    private InputVariable window = new InputVariable();
    private  OutputVariable probability = new OutputVariable();


    private static String [] columnsIncluded = { "Title", "Company", "Country", "IpAddress", "Activity date/time",
            "Link"};

    @Override
    protected void process(ComplexEventChunk<StreamEvent> streamEventChunk, Processor nextProcessor, StreamEventCloner streamEventCloner, ComplexEventPopulater complexEventPopulater) {

        StreamEvent event = streamEventChunk.getFirst();
        Object[] input = cleanse(event.getOutputData());
        Map<FieldName, FieldValue> inData = new HashMap<FieldName, FieldValue>();
        int[] outColumns = {4,5,10};

        int count = 0;
        for (Map.Entry<InputField, int[]> entry : attributeIndexMap.entrySet()) {
            FieldName featureName = entry.getKey().getName();
            int[] attributeIndexArray = entry.getValue();
            Object dataValue;

            if(featureName.getValue().equals("Link")){
                dataValue= input[10];
            }else if(featureName.getValue().equals("Title")){
                dataValue= input[5];
            }else{
                dataValue= input[4];
            }

            FieldValue inputFieldValue = entry.getKey().prepare(dataValue);
            inData.put(featureName, inputFieldValue);
        }

        if (!inData.isEmpty()) {
            try {
                Map<FieldName, ?> result = singlePointEvaluator.evaluate(inData);

                Object[] output = new Object[3];
                double[] probabilityVal = new double[3];

                int i = 0;

                for (FieldName fieldName : result.keySet()) {

                        output[i] = EvaluatorUtil.decode(result.get(fieldName));
                        i++;


                }
                probabilityVal[0] = (Double)output[2];

                String query = "index:\"" + input[0] + "\"";

                AnalyticsDataAPI analyticsDataAPI = (AnalyticsDataAPI) PrivilegedCarbonContext.getThreadLocalCarbonContext()
                        .getOSGiService(AnalyticsDataAPI.class,
                                null);
                int tenantId =  PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId();
                List<SearchResultEntry> resultEntries = analyticsDataAPI
                        .search(tenantId, lookupTable, query, 0, 2);

                List<String> ids = new ArrayList<String>();
                for (SearchResultEntry entry : resultEntries) {
                    ids.add(entry.getId());
                }
                AnalyticsDataResponse resp = analyticsDataAPI
                        .get(tenantId, lookupTable, 1, null, ids);

                List<Record> records = AnalyticsDataServiceUtils
                        .listRecords(analyticsDataAPI, resp);

                if(records != null ){
                    if(records.size() > 0){
                        List<InputField> inputFields2 = windowEvaluator.getActiveFields();
                        Map<FieldName, FieldValue> inData2 = new HashMap<FieldName, FieldValue>();

                        for (InputField fieldName : inputFields2) {
                            inData2.put(fieldName.getName(),fieldName.prepare(records.get(0).getValue(fieldName.getName().toString())));
                        }

                        Map<FieldName, ?> result2 = windowEvaluator.evaluate(inData2);

                        i=0;
                        Object[] output2 = new Object[3];
                        for (FieldName fieldName : result2.keySet()) {
                            output2[i] = EvaluatorUtil.decode(result2.get(fieldName));
                            i++;
                        }

                        probabilityVal[1] = (Double)output2[2];
                    }else{
                        probabilityVal[1] = 0.1;
                    }
                }else{
                    probabilityVal[1] = 0.1;
                }

                singlePoint.setInputValue(probabilityVal[0]);
                window.setInputValue(probabilityVal[1]);
                engine.process();

                Object prob = new Double(probability.getOutputValue());

                Object[] newArray = new Object[7];

                for(i=0;i<6;i++){
                    newArray[i] = input[i];
                }
                newArray[6] = prob;

                complexEventPopulater.populateComplexEvent(event, newArray);
                nextProcessor.process(streamEventChunk);
            } catch (Exception e) {
                log.error("Error while predicting", e);
                throw new ExecutionPlanRuntimeException("Error while predicting", e);
            }
        }
    }

    private Object[] concat(Object[]... arrays) {
        int length = 0;
        for (Object[] array : arrays) {
            length += array.length;
        }
        Object[] result = new String[length];
        int pos = 0;
        for (Object[] array : arrays) {
            for (Object element : array) {
                result[pos] = element;
                pos++;
            }
        }
        return result;
    }

    private Object[] cleanse(Object[]  data){

        int columnIndex ;
        int titleColumnIndex;
        int generatedColumnCount;
        int linkColumnIndex;

        int [] columnIncludedIndexes = new int[columnsIncluded.length];


        //Writing Header row for output files
        Object [] nextLine = data;

        //Add one more column for algorithm index to specified column array and write as header
        List<String> list = new LinkedList<String>(Arrays.asList(columnsIncluded));



        generatedColumnCount = 5;


        //Get the column index of given column name
        columnIndex = 4;
        titleColumnIndex = 3;
        linkColumnIndex = 9;


        columnIncludedIndexes[0] = 3;
        columnIncludedIndexes[1] = 4;
        columnIncludedIndexes[2] = 6;
        columnIncludedIndexes[3] = 7;
        columnIncludedIndexes[4] = 8;
        columnIncludedIndexes[5] = 9;



        CSVReader readerIgnore = null;
        try {
            readerIgnore = new CSVReader(
                    new InputStreamReader(new FileInputStream(
                           "./ignoreCompanies.csv"),
                            Constant.CSV_CHARACTER_FORMAT), Constant.CSV_SEPERATOR,
                    CSVReader.DEFAULT_QUOTE_CHARACTER, CSVReader.DEFAULT_QUOTE_CHARACTER);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        List<String[]> ignoreCustomers = null;
        try {
            ignoreCustomers = readerIgnore.readAll();
        } catch (IOException e) {
            e.printStackTrace();
        }



       int  indexAlgorithm = INDEX_ALGO_DOUBLE_META_PHONE;


        String[] outputLine = new String[columnIncludedIndexes.length + 5];




        //Check read line is number of required columns and indexing column value is not empty
            if (nextLine.length >= columnIncludedIndexes.length && !(((String)nextLine[columnIndex]).equals("")) &&
                    !isIgnore(ignoreCustomers, (String)nextLine[columnIndex])) {

                //Initialize output with specified columns plus one more column for algorithm index


                try {
                    //Set  algorithm Index for first column
                    switch (indexAlgorithm) {
                        case INDEX_ALGO_DOUBLE_META_PHONE :
                            outputLine[0] = customMatching.Convert((String)nextLine[columnIndex],
                                    INDEX_ALGO_DOUBLE_META_PHONE);
                            break;
                        case INDEX_ALGO_META_PHONE :
                            outputLine[0] = customMatching.Convert((String)nextLine[columnIndex],
                                    INDEX_ALGO_META_PHONE);
                            break;
                        default :
                            outputLine[0] = customMatching.Convert((String)nextLine[columnIndex],
                                   INDEX_ALGO_SOUNDEX);
                            break;
                    }


                    if (outputLine[0] != null) {
                        boolean isExistingCustomer;
                        String  [] result = isCustomer(currentCustomer, outputLine[0], (String)nextLine[columnIndex]);
                        boolean isValidIp = false;

                        if (result == null) {
                            isExistingCustomer = false;
                        }
                        else {
                            isExistingCustomer = true;

                            outputLine[0] = result[0];

                        }

                        outputLine[1] = Customer.booleanToString(isExistingCustomer);

//                        if(false) {
//                            isValidIp = validator.countryByIpAddressValidation(nextLine[ipColumnIndex],
//                                    nextLine[countryColumnIndex]);
//                        }

                        outputLine[2] = String.valueOf(isValidIp);
                        outputLine[4] = Customer.booleanToString(fortuneUtil.isFortuneCompany(outputLine[0]));

                        //Set specified columns for output
                        for (int i = generatedColumnCount;
                             i < columnIncludedIndexes.length + generatedColumnCount; i++) {
                            //Check include index is available on readLine
                            if (nextLine.length > columnIncludedIndexes[i - generatedColumnCount]) {
                                if (titleColumnIndex == columnIncludedIndexes[i - generatedColumnCount]) {
                                    outputLine[i] = titleUtil.titleClassifier((String)
                                            nextLine[columnIncludedIndexes[i - generatedColumnCount]]).toString();
                                }else if((linkColumnIndex == columnIncludedIndexes[i - generatedColumnCount])){

                                    if(!(nextLine[columnIncludedIndexes[i - generatedColumnCount]]).equals("")){

                                        String actionsType = ((String)nextLine[linkColumnIndex]).trim();
                                        if(actionsType.equals("")){
                                            outputLine[i] = "other";
                                        }else{
                                            if (actionsType.contains(Constant.KEY_WORD_DOWNLOADS)) {
                                                outputLine[i] = "download";
                                            }
                                            else  if (actionsType.contains(Constant.KEY_WORD_WHITE_PAPERS)) {
                                                outputLine[i] = "whitepapers";
                                            }
                                            else  if (actionsType.contains(Constant.KEY_WORD_TUTORIALS)) {
                                                outputLine[i] = "tutorials";
                                            }
                                            else  if (actionsType.contains(Constant.KEY_WORD_WORKSHOPS)) {
                                                outputLine[i] = "workshops";
                                            }
                                            else  if (actionsType.contains(Constant.KEY_WORD_CASE_STUDIES)) {
                                                outputLine[i] = "caseStudies";
                                            }
                                            else  if (actionsType.contains(Constant.KEY_WORD_PRODUCT_PAGES)) {
                                                outputLine[i] = "productPages";
                                            }
                                            else {
                                                outputLine[i] = "other";
                                            }
                                        }


                                    }else{
                                        outputLine[i] = "other";
                                    }
                                }
                                else {
                                    outputLine[i] = (String)nextLine[columnIncludedIndexes[i - generatedColumnCount]];
                                }
                            } else {
                                outputLine[i] = "";
                            }
                        }


                        if (isExistingCustomer) {
                            outputLine[3] = result[2];
                            outputLine[6] = result[1];
                        }



                    }

                } catch (IllegalArgumentException ex) {
                    //handles algorithm encode exceptions

                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        return outputLine;


    }


    private static String[] isCustomer(ArrayList<String[]> currentCustomers, String companyIndex, String companyName) {
        for (int i = 0 ; i < currentCustomers.size(); i++) {
            if (currentCustomers.get(i)[0].equals(companyIndex)) {
                return currentCustomers.get(i);
            }
            else if (companyName.toUpperCase().contains(currentCustomers.get(i)[1].toUpperCase())) {
                return currentCustomers.get(i);
            }
        }

        return null;
    }




    private   ArrayList<String[]> loadCurrentCustomers(CSVReader readerCustomers,
                                                             int indexAlgorithm, int indexColumn) throws  Exception {

        ArrayList<String[]> Customers = new ArrayList<String[]>();
        String [] nextLine;

        readerCustomers.readNext();

        while ((nextLine = readerCustomers.readNext()) != null) {
            try {

                String [] rowVal = new String[3];

                //Set  algorithm Index for first column
                switch (indexAlgorithm) {
                    case Constant.INDEX_ALGO_DOUBLE_META_PHONE:
                        rowVal[0] = customMatching.Convert(nextLine[indexColumn],
                                Constant.INDEX_ALGO_DOUBLE_META_PHONE);
                        break;
                    case Constant.INDEX_ALGO_META_PHONE:
                        rowVal[0] =customMatching.Convert(nextLine[indexColumn],
                                Constant.INDEX_ALGO_META_PHONE);
                        break;
                    default:
                        rowVal[0] = customMatching.Convert(nextLine[indexColumn],
                                Constant.INDEX_ALGO_SOUNDEX);
                        break;
                }

                rowVal[1] = nextLine[indexColumn].trim();
                rowVal[2] = nextLine[1].trim();

                Customers.add(rowVal);

            }
            catch (IllegalArgumentException ex) {
                //handles algorithm encode exceptions
                logger.error("Exception occurred when indexing" + ex.getMessage());
            }
        }
        return Customers;
    }

    @Override
    protected List<Attribute> init(AbstractDefinition inputDefinition, ExpressionExecutor[] attributeExpressionExecutors, ExecutionPlanContext executionPlanContext) {





        if(attributeExpressionExecutors.length == 0) {
            throw new ExecutionPlanValidationException("PMML model definition not available.");
        } else if(attributeExpressionExecutors.length == 1) {
            attributeSelectionAvailable = false;    // model-definition only
        } else {
            attributeSelectionAvailable = true;     // model-definition and stream-attributes list
        }

        // Check whether the first parameter in the expression is the pmml definition
        if(attributeExpressionExecutors[0] instanceof ConstantExpressionExecutor)  {
            Object constantObj = ((ConstantExpressionExecutor) attributeExpressionExecutors[0]).getValue();
            pmmlDefinition = (String) constantObj;
        } else {
            throw new ExecutionPlanValidationException("PMML model definition has not been set as the first parameter");
        }

        if(attributeExpressionExecutors[1] instanceof ConstantExpressionExecutor)  {
            Object constantObj = ((ConstantExpressionExecutor) attributeExpressionExecutors[1]).getValue();
            pmmlDefinition2 = (String) constantObj;
        } else {
            throw new ExecutionPlanValidationException("PMML model definition has not been set as the first parameter");
        }

        if(attributeExpressionExecutors[2] instanceof ConstantExpressionExecutor)  {
            Object constantObj = ((ConstantExpressionExecutor) attributeExpressionExecutors[2]).getValue();
            lookupTable = (String) constantObj;
        } else {
            throw new ExecutionPlanValidationException("PMML model definition has not been set as the first parameter");
        }
        
        // Unmarshal the definition and get an executable pmml model
        return generateOutputAttributes();
    }


    private static boolean isIgnore(List<String[]> ignoreCustomers, String currentValue) {
        for (int i = 0; i < ignoreCustomers.size(); i++) {
            if (currentValue.length() < 3 ||
                    ignoreCustomers.get(i)[0].trim().toUpperCase().equals(currentValue.trim().toUpperCase())) {
                return true;
            }
        }

        return  false;
    }

    /**
     * Generate the output attribute list
     * @return
     */
    private List<Attribute> generateOutputAttributes() {
        List<Attribute> outputAttributes = new ArrayList<Attribute>();

        Attribute.Type type = Attribute.Type.STRING;;
        outputAttributes.add(new Attribute("Index" , type));

        type = Attribute.Type.INT;
        outputAttributes.add(new Attribute("Is_Customer" , type));

        type = Attribute.Type.BOOL;
        outputAttributes.add(new Attribute("Is_valid_Country" , type));

        type = Attribute.Type.STRING;
        outputAttributes.add(new Attribute("Joined_Date" , type));

        type = Attribute.Type.INT;
        outputAttributes.add(new Attribute("Is_Fortune_500" , type));

        type = Attribute.Type.STRING;
        outputAttributes.add(new Attribute("ProcessedTitle" , type));

        type = Attribute.Type.DOUBLE;
        outputAttributes.add(new Attribute("probability" , type));


        return outputAttributes;
    }

    @Override
    public void start() {
        try {

            PMML pmmlModel = unmarshal(pmmlDefinition);
            PMML pmmlModel2 = unmarshal(pmmlDefinition2);
            // Get the different types of fields defined in the pmml model

            org.jpmml.evaluator.ModelEvaluatorFactory modelEvaluatorFactory = org.jpmml.evaluator.ModelEvaluatorFactory.newInstance();
            org.jpmml.evaluator.ModelEvaluator<?> modelEvaluator = modelEvaluatorFactory.newModelEvaluator(pmmlModel);
            singlePointEvaluator = modelEvaluator;

            org.jpmml.evaluator.ModelEvaluatorFactory modelEvaluatorFactory2 = org.jpmml.evaluator.ModelEvaluatorFactory.newInstance();
            org.jpmml.evaluator.ModelEvaluator<?> modelEvaluator2 = modelEvaluatorFactory2.newModelEvaluator(pmmlModel2);
            windowEvaluator = modelEvaluator2;


            inputFields = singlePointEvaluator.getActiveFields();
            outputFields = singlePointEvaluator.getOutputFields();

            CSVReader readerCustomers= null;

            customMatching = new CustomMatchingUtility();
            try {
                readerCustomers = new CSVReader(
                        new InputStreamReader(new FileInputStream("./customerBase.csv"),
                                Constant.CSV_CHARACTER_FORMAT), Constant.CSV_SEPERATOR, CSVReader.DEFAULT_QUOTE_CHARACTER,
                        CSVReader.DEFAULT_QUOTE_CHARACTER);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            try {
                currentCustomer = loadCurrentCustomers(readerCustomers,
                        Constant.INDEX_ALGO_DOUBLE_META_PHONE, 0);
            } catch (Exception e) {
                e.printStackTrace();
            }

            customMatching.loadCompanySuffixFromCsv("/home/sameera/Downloads/product-cep/modules/distribution/target/wso2cep-4.2.1-SNAPSHOT/company_suffix.csv");
            fortuneUtil = new FortuneCompaniesUtility(Constant.INDEX_ALGO_DOUBLE_META_PHONE);

            populateFeatureAttributeMapping();
            initFuzzy();
        } catch (Exception e) {
            log.error("Error while mapping attributes with pmml model features : " + pmmlDefinition, e);
            throw new ExecutionPlanCreationException("Error while mapping attributes with pmml model features : " + pmmlDefinition + "\n" + e.getMessage());
        }
    }

    private void initFuzzy(){

        engine.setName("lead-predictor");
        singlePoint.setName("singlePoint");
        singlePoint.setRange(0.000, 1.000);
        singlePoint.addTerm(new Triangle("LOW", 0.000, 0.250, 0.500));
        singlePoint.addTerm(new Triangle("MEDIUM", 0.250, 0.500, 0.750));
        singlePoint.addTerm(new Triangle("HIGH", 0.500, 0.750, 1.000));
        engine.addInputVariable(singlePoint);


        window.setName("window");
        window.setRange(0.000, 1.000);
        window.addTerm(new Gaussian("LOW",0,0.2));
        window.addTerm(new Gaussian("MEDIUM",0.5,0.2));
        window.addTerm(new Gaussian("HIGH",1,0.2));
        engine.addInputVariable(window);


        probability.setName("probability");
        probability.setRange(0.000, 1.000);
        probability.setDefaultValue(Double.NaN);
        probability.addTerm(new Gaussian("LOW",0,0.2));
        probability.addTerm(new Gaussian("MEDIUM",0.5,0.2));
        probability.addTerm(new Gaussian("HIGH",1,0.2));
        engine.addOutputVariable(probability);

        RuleBlock ruleBlock = new RuleBlock();
        ruleBlock.addRule(Rule.parse("if singlePoint is LOW and window is LOW then probability is LOW", engine));
        ruleBlock.addRule(Rule.parse("if singlePoint is LOW and window is MEDIUM then probability is HIGH", engine));
        ruleBlock.addRule(Rule.parse("if singlePoint is MEDIUM and window is LOW then probability is MEDIUM", engine));
        ruleBlock.addRule(Rule.parse("if singlePoint is MEDIUM and window is MEDIUM then probability is MEDIUM", engine));
        ruleBlock.addRule(Rule.parse("if singlePoint is HIGH and window is LOW then probability is MEDIUM", engine));
        ruleBlock.addRule(Rule.parse("if singlePoint is HIGH and window is MEDIUM then probability is HIGH", engine));
        ruleBlock.addRule(Rule.parse("if window is HIGH then probability is HIGH", engine));
        engine.addRuleBlock(ruleBlock);

        engine.configure("Minimum", "Maximum", "Minimum", "Maximum", "Centroid");

        StringBuilder status = new StringBuilder();
        if (!engine.isReady(status)) {
            throw new RuntimeException("Engine not ready. "
                    + "The following errors were encountered:\n" + status.toString());
        }


    }

    /**
     * Match the attribute index values of stream with feature names of the model
     * @throws Exception
     */
    private void populateFeatureAttributeMapping() throws Exception {

        attributeIndexMap = new HashMap<InputField, int[]>();
        HashMap<String, InputField> features = new HashMap<String, InputField>();

        for (InputField fieldName : inputFields) {
            features.put(fieldName.getName().toString(), fieldName);
        }



            String[] attributeNames = {"Link","Title","Is_Fortune_500"};
            int count = 0;
            for(String attributeName : attributeNames) {

                    int[] attributeIndexArray = new int[4];
                    attributeIndexArray[2] = 2; // get values from output data
                    attributeIndexArray[3] = count;//inputDefinition.getAttributePosition(attributeName);
                    attributeIndexMap.put(inputFields.get(count), attributeIndexArray);
                    count++;


        }
    }

    /**
     * TODO : move to a Util class (PmmlUtil)
     * Unmarshal the definition and get an executable pmml model.
     * 
     * @return  pmml model
     */
    private PMML unmarshal(String pmmlDefinition) {
        try {
            File pmmlFile = new File(pmmlDefinition);
            InputSource pmmlSource;
            Source source;
            // if the given is a file path, read the pmml definition from the file
            if (pmmlFile.isFile() && pmmlFile.canRead()) {
                pmmlSource = new InputSource(new FileInputStream(pmmlFile));
            } else {
                // else, read from the given definition
                pmmlSource = new InputSource(new StringReader(pmmlDefinition));
            }
            source = ImportFilter.apply(pmmlSource);
            return JAXBUtil.unmarshalPMML(source);
        } catch (Exception e) {
            logger.error("Failed to unmarshal the pmml definition: " + e.getMessage());
            throw new ExecutionPlanCreationException("Failed to unmarshal the pmml definition: " + pmmlDefinition + ". "
                    + e.getMessage(), e);
        }
    }
    
    @Override
    public void stop() {

    }

    @Override
    public Object[] currentState() {
        return new Object[0];
    }

    @Override
    public void restoreState(Object[] state) {

    }
}
