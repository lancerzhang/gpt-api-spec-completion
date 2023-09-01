package com.example.gasc.service;

import com.example.gasc.model.openai.SearchMuleFlowResponse;
import com.example.gasc.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
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
        CommandUtils.runMvnCompile(projectPath);

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
                    generateSchema("get", apiPath, valueNode);
                }

                if (valueNode.containsKey("post")) {
                    generateSchema("post", apiPath, valueNode);
                }
            }
        }
    }

    protected void generateSchema(String httpMethod, String apiPath, Map<Object, Object> valueNode) throws Exception {
        Map<Object, Object> requestBodyMap = YamlUtil.getPathNode(valueNode, httpMethod, "body");
        Map<Object, Object> responseBodyMap = YamlUtil.getPathNode(valueNode, httpMethod, "responses", 200, "body");
        String flowName = httpMethod + ":" + apiPath + ":mobile_api-config";
        logger.debug("flowName: " + flowName);
        String muleFlowXmlContent = XmlUtil.searchMuleFlowXml(flowName, projectPath);
        SearchMuleFlowResponse codes = retryableAIService.searchMuleFlow(httpMethod, apiPath, muleFlowXmlContent);
        if (codes == null) {
            return;
        }
        String respJavaClassStr = codes.getRespJavaClasses();
        String reqJavaClassStr = codes.getReqJavaClasses();
        if ("N/A".equals(respJavaClassStr) || (httpMethod.equals("post") && "N/A".equals(reqJavaClassStr))) {
            retryableAIService.generateSchemaByGPT(projectPath, httpMethod, apiPath, codes, requestBodyMap, responseBodyMap);
        }

        if (!"N/A".equals(respJavaClassStr)) {
            generateSchemaByJava(httpMethod, "Response", apiPath, respJavaClassStr, responseBodyMap);
        }
        if ("post".equals(httpMethod) && !"N/A".equals(reqJavaClassStr)) {
            generateSchemaByJava(httpMethod, "Request", apiPath, reqJavaClassStr, requestBodyMap);
        }
    }

    protected void generateSchemaByJava(String httpMethod, String type, String apiPath, String reqJavaClassStr, Map<Object, Object> requestBodyMap) throws Exception {
        logger.info("start to use java to generate schema for " + httpMethod + ":" + apiPath);
        List<String> javaClasses = Utils.getContentItems(reqJavaClassStr);
        List<JsonNode> schemas = new ArrayList<>();
        for (String javaClass : javaClasses) {
            Class<?> clazz = JavaUtil.loadClassFromFile(javaClass, projectPath + "/target/classes/");
            JsonNode schema = JsonSchemaUtil.generateJsonSchemaNode(clazz);
            schemas.add(schema);
        }

        JsonNode schemaNode = JsonSchemaUtil.mergeAll(schemas);
        String newClassName = JavaUtil.convertToCamelCase(httpMethod + apiPath + "/" + type + "Body");
        ObjectMapper mapper = new ObjectMapper();
        String schemaStr = mapper.writeValueAsString(schemaNode);
        String schemaFileName = JsonSchemaUtil.writeSchema(projectPath, newClassName, schemaStr);

        Map<String, String> innerMap = new HashMap<>();
        innerMap.put("schema", "!include schema/" + schemaFileName);
        requestBodyMap.put("application/json", innerMap);
    }

}
