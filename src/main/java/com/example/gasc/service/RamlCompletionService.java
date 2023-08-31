package com.example.gasc.service;

import com.example.gasc.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Scope("prototype")
public class RamlCompletionService {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final String specPath = "/src/main/api/";
    private final String exampleFolder = "examples/";
    private String projectPath;
    private String examplesFilenames;
    @Autowired
    private RetryableAIService retryableAIService;


    public void configure(String projectPath) {
        this.projectPath = projectPath;
    }

    public void process() throws Exception {
        Instant startTime = Instant.now();

        // Define the path
        Path dir = FileUtil.getPath(projectPath + specPath + exampleFolder);
        examplesFilenames = FileUtil.getFilenames(dir);

        Map<Object, Object> filteredData = YamlUtil.filterRaml(projectPath);

        completeSpec(filteredData);

        Yaml yaml = new Yaml();
        try (FileWriter writer = new FileWriter(FileUtil.getPath(projectPath + specPath + "filtered_api.yaml").toFile())) {
            yaml.dump(filteredData, writer);
        } catch (IOException e) {
            throw new RuntimeException("Error writing to filtered_api.raml", e);
        }
        Instant endTime = Instant.now();
        logger.info("Job duration (seconds): " + Duration.between(startTime, endTime).getSeconds());
    }


    protected void completeSpec(Map<Object, Object> filteredData) throws Exception {
        completeSpecHelper(filteredData, "");
    }

    protected void completeSpecHelper(Map<Object, Object> currentData, String currentPath) throws Exception {
        for (Object objKey : currentData.keySet()) {
            if (!(objKey instanceof String)) {
                continue; // skip if key isn't a String
            }

            String key = (String) objKey;
            Object value = currentData.get(key);

            String apiPath = currentPath + key;  // Construct the new path for this level
            logger.debug("processing path: " + apiPath);

            if (value instanceof Map) {
                Map<Object, Object> valueNode = (Map<Object, Object>) value;
                // This recursive call ensures that all nested maps are processed.
                completeSpecHelper(valueNode, apiPath);

                if (valueNode.containsKey("get")) {
                    prepareSchemaGeneration("get", apiPath, valueNode);
                }

                if (valueNode.containsKey("post")) {
                    prepareSchemaGeneration("post", apiPath, valueNode);
                }
            }
        }
    }


    protected void prepareSchemaGeneration(String methodName, String apiPath, Map<Object, Object> valueNode) throws Exception {
        Map<Object, Object> requestBodyMap = YamlUtil.getPathNode(valueNode, methodName, "body");
        Map<Object, Object> responseBodyMap = YamlUtil.getPathNode(valueNode, methodName, "responses", 200, "body");
        String flowName = methodName + ":" + apiPath + ":mobile_api-config";
        logger.debug("flowName: " + flowName);
        String muleFlowXmlContent = XmlUtil.searchMuleFlowXml(flowName, projectPath);

        String[] codeblocks = retryableAIService.searchMuleFlow(projectPath, methodName, apiPath, muleFlowXmlContent);

        String[] exampleFilenames = retryableAIService.searchExamples(projectPath, methodName, apiPath, examplesFilenames);

        retryableAIService.generateSchema(projectPath, methodName, apiPath, codeblocks, exampleFilenames, requestBodyMap, responseBodyMap);

    }


    protected void generateSchemaByJava(List<String> javaClasses, String apiPath, Map<Object, Object> postBodyMap) throws Exception {
        String requestBodyClass = "";
        if (javaClasses.size() > 1) {
            String newClassName = JavaUtil.convertToCamelCase("post" + apiPath + "/RequestBody");
            requestBodyClass = JavaUtil.mergeClasses(projectPath, javaClasses, newClassName);
        } else if (javaClasses.size() == 1) {
            requestBodyClass = javaClasses.get(0);
        }
        logger.debug("requestBodyClass: " + requestBodyClass);

        if (!requestBodyClass.isEmpty()) {

            Class<?> clazz = JavaUtil.loadClassFromFile(requestBodyClass, projectPath + "/target/classes/");
            String schema = SchemaUtil.generateJsonSchema(clazz);

            // Save schema to file
            String schemaFileName = SchemaUtil.writeSchema(projectPath, clazz.getSimpleName(), schema);

            Map<String, String> innerMap = new HashMap<>();
            innerMap.put("schema", "!include schema/" + schemaFileName);

            postBodyMap.put("application/json", innerMap);
        }
    }


}
