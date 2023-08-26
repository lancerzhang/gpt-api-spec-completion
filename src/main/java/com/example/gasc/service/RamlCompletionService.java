package com.example.gasc.service;

import com.example.gasc.config.GptModel;
import com.example.gasc.model.openai.Message;
import com.example.gasc.model.openai.OpenAIResult;
import com.example.gasc.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Scope("prototype")
public class RamlCompletionService {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    @Autowired
    private OpenAIApiService openAIApiService;

    private String projectPath;
    private String examplesFilenames;
    @Autowired
    private GptModel gptModel;
    @Value("classpath:prompts/search_examples.txt")
    private Resource searchExamples;
    @Value("classpath:prompts/generate_schema.txt")
    private Resource generateSchema;

    public void configure(String projectPath) {
        this.projectPath = projectPath;
    }

    public void process() throws Exception {
//        CommandUtils.runMvnCompile(projectPath);

        // Define the path
        Path dir = Paths.get(projectPath, "src", "main", "api", "examples");
        examplesFilenames = FileUtil.getFilenames(dir);

        Map<Object, Object> filteredData = YamlUtil.filterRaml(projectPath);

        completeSpec(filteredData);

        Yaml yaml = new Yaml();
        try (FileWriter writer = new FileWriter(Paths.get(projectPath, "src", "main", "api", "filtered_api.yaml").toFile())) {
            yaml.dump(filteredData, writer);
        } catch (IOException e) {
            throw new RuntimeException("Error writing to filtered_api.raml", e);
        }
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

            String newPath = currentPath + key;  // Construct the new path for this level
            logger.debug("processing path: " + newPath);

            if (value instanceof Map) {
                // This recursive call ensures that all nested maps are processed.
                completeSpecHelper((Map<Object, Object>) value, newPath);

                // If current map has a "post" attribute, add the constructed path to its body's application/json
                if (((Map<Object, Object>) value).containsKey("post")) {
                    Map<Object, Object> postMap = (Map<Object, Object>) ((Map<Object, Object>) value).get("post");
                    Map<Object, Object> postResponseMap = (Map<Object, Object>) postMap.get("responses");
                    Map<Object, Object> okResponseMap = (Map<Object, Object>) postResponseMap.get(200);
                    Map<Object, Object> responseBodyMap = (Map<Object, Object>) okResponseMap.get("body");
                    if (postMap.containsKey("body")) {
                        Map<Object, Object> postBodyMap = (Map<Object, Object>) postMap.get("body");
                        String flowName = "post:" + newPath + ":mobile_api-config";
                        logger.debug("flowName: " + flowName);
                        List<String> dwlVars = XmlUtil.findDwl(flowName, projectPath);
                        logger.debug("dwlVars: " + dwlVars);

                        // Get Java classes from DWL files and add them to a list
                        List<String> javaClasses = new ArrayList<>();
                        for (String dwlVar : dwlVars) {
                            String dwlPath = dwlVar.split("=")[1];
                            String javaClass = DwlUtil.getJavaClassFromDwl(dwlPath, projectPath);
                            if (javaClass != null) {
                                javaClasses.add(javaClass);
                            }
                        }
                        logger.debug("javaClasses: " + javaClasses);

                        if (dwlVars.isEmpty()) {
                            logger.warn("Can't find any dwl for: " + flowName);
                            continue;
                        }

                        if (dwlVars.size() == javaClasses.size()) {
                            logger.info("All dwl file has class, can use program to generate schema.");
                            generateSchemaByJava(javaClasses, newPath, postBodyMap);
                        } else {
                            logger.info("Some java class is missing, start to use ChatGPT");
                            generateSchemasByGPT(dwlVars, newPath, postBodyMap, responseBodyMap);
                        }
                    }
                }
            }
        }
    }

    protected OpenAIResult getGptResponse(String task, String prompt) throws IOException {
        ArrayList<Message> messages = OpenAIApiService.createInitialMessages();
        OpenAIApiService.addUserMessages(messages, prompt);
        logger.debug(prompt);
        OpenAIResult result = openAIApiService.post(task, gptModel, messages);
        logger.debug(result.getContent());
        return result;
    }

    protected void generateSchemasByGPT(List<String> dwlVars, String apiPath, Map<Object, Object> postBodyMap, Map<Object, Object> responseBodyMap) throws Exception {
        List<String> codeBlocks = searchExamples(apiPath);

        if (codeBlocks.size() != 2) {
            logger.info("Expect two code block in openAI response but it's not.");
        } else {
            generateSchema(dwlVars, apiPath, codeBlocks, postBodyMap, responseBodyMap);
        }
    }

    protected void generateSchema(List<String> dwlVars, String apiPath, List<String> exampleCode, Map<Object, Object> postBodyMap, Map<Object, Object> responseBodyMap) throws Exception {
        String exampleRequestFilenames = exampleCode.get(0);
        String exampleRequestContent = FileUtil.getExamplesContent(projectPath, exampleRequestFilenames);
        String exampleResponseFilenames = exampleCode.get(1);
        String exampleResponseContent = FileUtil.getExamplesContent(projectPath, exampleResponseFilenames);
        String dwlContent = DwlUtil.getDwlContent(dwlVars, projectPath);
        String task = "generate_schema";
        String promptTemplate = loadTemplate(task);
        String prompt = String.format(promptTemplate, apiPath, dwlContent, exampleRequestContent, exampleResponseContent);
        OpenAIResult result = getGptResponse(task, prompt);
        List<String> codeBlocks = FileUtil.extractMarkdownCodeBlocks(result.getContent());
        if (codeBlocks.size() != 2) {
            logger.info("Expect two code block in openAI response but it's not.");
        } else {
            Map<String, String> requestMap = new HashMap<>();
            String requestSchemaName = JavaUtil.convertToCamelCase("post" + apiPath + "/RequestBody");
            String requestSchemaFileName = SchemaUtil.writeSchema(projectPath, requestSchemaName, codeBlocks.get(0));
            requestMap.put("schema", "!include schema/" + requestSchemaFileName);
            postBodyMap.put("application/json", requestMap);

            Map<String, String> responseMap = new HashMap<>();
            String responseSchemaName = JavaUtil.convertToCamelCase("post" + apiPath + "/ResponseBody");
            String responseSchemaFileName = SchemaUtil.writeSchema(projectPath, responseSchemaName, codeBlocks.get(1));
            responseMap.put("schema", "!include schema/" + responseSchemaFileName);
            postBodyMap.put("application/json", responseMap);
        }
    }

    protected List<String> searchExamples(String apiPath) throws IOException {
        String task = "search_examples";
        String promptTemplate = loadTemplate(task);
        String prompt = String.format(promptTemplate, apiPath, examplesFilenames);
        OpenAIResult result = getGptResponse(task, prompt);
        return FileUtil.extractMarkdownCodeBlocks(result.getContent());
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


    protected String loadTemplate(String templateType) throws IOException {
        Resource resourceToUse;
        switch (templateType) {
            case "search_examples":
                resourceToUse = searchExamples;
                break;
            case "generate_schema":
                resourceToUse = generateSchema;
                break;
            default:
                throw new IllegalArgumentException("Invalid template type: " + templateType);
        }

        byte[] bytes = FileCopyUtils.copyToByteArray(resourceToUse.getInputStream());
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
