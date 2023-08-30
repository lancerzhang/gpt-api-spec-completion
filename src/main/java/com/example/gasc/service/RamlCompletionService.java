package com.example.gasc.service;

import com.example.gasc.config.GptModel;
import com.example.gasc.model.openai.Message;
import com.example.gasc.model.openai.OpenAIResult;
import com.example.gasc.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Scope("prototype")
public class RamlCompletionService {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final String specPath = "/src/main/api/";
    private final String exampleFolder = "examples/";
    @Autowired
    private OpenAIApiService openAIApiService;
    @Autowired
    private ResourceLoader resourceLoader;
    private String projectPath;
    private String examplesFilenames;
    @Autowired
    private GptModel gptModel;

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

    protected OpenAIResult getGptResponse(String task, String prompt) throws IOException {
        ArrayList<Message> messages = OpenAIApiService.createInitialMessages();
        OpenAIApiService.addUserMessages(messages, prompt);
        logger.debug("Sending prompt to ChatGPT. ");
        logger.debug(prompt);
        OpenAIResult result = openAIApiService.post(task, gptModel, messages);
        logger.debug("Got return from ChatGPT.");
        logger.debug(result.getContent());
        return result;
    }

    protected void prepareSchemaGeneration(String methodName, String apiPath, Map<Object, Object> valueNode) throws Exception {
        Map<Object, Object> requestBodyMap = YamlUtil.getPathNode(valueNode, methodName, "body");
        Map<Object, Object> responseBodyMap = YamlUtil.getPathNode(valueNode, methodName, "responses", 200, "body");
        String flowName = methodName + ":" + apiPath + ":mobile_api-config";
        logger.debug("flowName: " + flowName);
        String muleFlowXmlContent = XmlUtil.searchMuleFlowXml(flowName, projectPath);

        String[] codeblocks = searchMuleFlow(methodName, apiPath, muleFlowXmlContent);

        String[] exampleFilenames = searchExamples(methodName, apiPath);

        generateSchema(methodName, apiPath, codeblocks, exampleFilenames, requestBodyMap, responseBodyMap);

    }

    protected void generateSchema(String methodName, String apiPath, String[] codeblocks, String[] exampleFilenames, Map<Object, Object> postBodyMap, Map<Object, Object> responseBodyMap) throws Exception {
        String exampleResponseContent = FileUtil.getExamplesContent(projectPath, exampleFilenames[0]);
        String respDwContentStr = codeblocks[0];
        String respDwlFileStr = codeblocks[1];
        String respJavaClassesStr = codeblocks[2];
        String respDwlContent = DwlUtil.getDwlContent(respDwlFileStr, projectPath);
        respDwContentStr = respDwContentStr + "\n" + respDwlContent;
        String respJavaContents = JavaUtil.getSimpleJavaFileContents(respJavaClassesStr, projectPath);

        String exampleRequestContent = "";
        String reqDwContent = "";
        String reqJavaContents = "";

        if (methodName.equals("post")) {
            exampleRequestContent = FileUtil.getExamplesContent(projectPath, exampleFilenames[1]);
            reqDwContent = codeblocks[3];
            String reqDwlFileStr = codeblocks[4];
            String reqJavaClassesStr = codeblocks[5];
            String reqDwlContent = DwlUtil.getDwlContent(reqDwlFileStr, projectPath);
            reqDwContent = reqDwContent + reqDwlContent;
            reqJavaContents = JavaUtil.getSimpleJavaFileContents(reqJavaClassesStr, projectPath);
        }

        String task = "generate_" + methodName + "_schema";
        String promptTemplate = readClasspathFile("prompts/" + task + ".txt");
        String prompt = String.format(promptTemplate, apiPath, respDwContentStr, respJavaContents, exampleResponseContent, reqDwContent, reqJavaContents, exampleRequestContent);
        OpenAIResult result = getGptResponse(task, prompt);
        String[] schemaCode = FileUtil.splitReturnContent(result.getContent());

        Map<String, String> responseMap = new HashMap<>();
        String responseSchemaName = JavaUtil.convertToCamelCase(methodName + apiPath + "/ResponseBody");
        String responseSchemaFileName = SchemaUtil.writeSchema(projectPath, responseSchemaName, schemaCode[0]);
        responseMap.put("schema", "!include schema/" + responseSchemaFileName);
        responseBodyMap.put("application/json", responseMap);

        if (methodName.equals("post")) {
            Map<String, String> requestMap = new HashMap<>();
            String requestSchemaName = JavaUtil.convertToCamelCase(methodName + apiPath + "/RequestBody");
            String requestSchemaFileName = SchemaUtil.writeSchema(projectPath, requestSchemaName, schemaCode[1]);
            requestMap.put("schema", "!include schema/" + requestSchemaFileName);
            postBodyMap.put("application/json", requestMap);
        }
    }

    protected String[] searchMuleFlow(String methodName, String apiPath, String muleFlowXmlContent) throws IOException {
        String task = "search_" + methodName + "_muleFlow";
        String promptTemplate = readClasspathFile("prompts/" + task + ".txt");
        String prompt = String.format(promptTemplate, apiPath, muleFlowXmlContent);
        OpenAIResult result = getGptResponse(task, prompt);
        return FileUtil.splitReturnContent(result.getContent());
    }

    protected String[] searchExamples(String methodName, String apiPath) throws IOException {
        String task = "search_" + methodName + "_examples";
        String promptTemplate = readClasspathFile("prompts/" + task + ".txt");
        String prompt = String.format(promptTemplate, apiPath, examplesFilenames);
        OpenAIResult result = getGptResponse(task, prompt);
        return FileUtil.splitReturnContent(result.getContent());
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

    public String readClasspathFile(String filename) throws IOException {
        Resource resource = resourceLoader.getResource("classpath:" + filename);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
