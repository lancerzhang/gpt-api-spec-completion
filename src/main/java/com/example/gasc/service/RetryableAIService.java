package com.example.gasc.service;

import com.example.gasc.config.GptModel;
import com.example.gasc.model.openai.Message;
import com.example.gasc.model.openai.OpenAIResult;
import com.example.gasc.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RetryableAIService {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    @Autowired
    private ResourceLoader resourceLoader;
    @Autowired
    private GptModel gptModel;
    @Autowired
    private OpenAIApiService openAIApiService;

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

    @Retryable(maxAttempts = 2, value = Exception.class)
    protected void generateSchemaByGPT(String projectPath, String httpMethod, String apiPath, String[] codeblocks, Map<Object, Object> postBodyMap, Map<Object, Object> responseBodyMap) throws Exception {
        logger.info("start to use ChatGPT to generate schema for " + httpMethod + ":" + apiPath);
        String respDwContent = codeblocks[0];
        String respDwlFile = codeblocks[1];
        String respDwlContent = DwlUtil.getDwlContent(respDwlFile, projectPath);
        respDwContent = respDwContent + "\n" + respDwlContent;
        String respJavaClasses = DwlUtil.extractClasses(respDwContent);
        String respJavaContent = JavaUtil.getSimpleJavaFileContents(respJavaClasses, projectPath);

        String reqDwContent = "";
        String reqJavaContent = "";

        if (httpMethod.equals("post")) {
            reqDwContent = codeblocks[3];
            String reqDwlFile = codeblocks[4];
            String reqDwlContent = DwlUtil.getDwlContent(reqDwlFile, projectPath);
            reqDwContent = reqDwContent + "\n" + reqDwlContent;
            String reqJavaClasses = DwlUtil.extractClasses(reqDwContent);
            reqJavaContent = JavaUtil.getSimpleJavaFileContents(reqJavaClasses, projectPath);
        }
        String task = "generate_" + httpMethod + "_schema";
        String promptTemplate = readClasspathFile("prompts/" + task + ".txt");
        String prompt = String.format(promptTemplate, apiPath, respDwContent, respJavaContent, reqDwContent, reqJavaContent);
        OpenAIResult result = getGptResponse(task, prompt);
        String[] schemaCode = Utils.splitReturnContent(result.getContent());

        String respJavaClassStr = codeblocks[2];
        if (respJavaClassStr.equals("N/A")) {
            // only use ChatGPT result when no returnClass found for response
            Map<String, String> responseMap = new HashMap<>();
            String responseSchemaName = JavaUtil.convertToCamelCase(httpMethod + apiPath + "/ResponseBody");
            String responseSchemaFileName = JsonSchemaUtil.writeSchema(projectPath, responseSchemaName, schemaCode[0]);
            responseMap.put("schema", "!include schema/" + responseSchemaFileName);
            responseBodyMap.put("application/json", responseMap);
        }

        if (httpMethod.equals("post") && codeblocks[5].equals("N/A")) {
            // only use ChatGPT result when no returnClass found for request
            Map<String, String> requestMap = new HashMap<>();
            String requestSchemaName = JavaUtil.convertToCamelCase(httpMethod + apiPath + "/RequestBody");
            String requestSchemaFileName = JsonSchemaUtil.writeSchema(projectPath, requestSchemaName, schemaCode[1]);
            requestMap.put("schema", "!include schema/" + requestSchemaFileName);
            postBodyMap.put("application/json", requestMap);
        }
    }

    @Retryable(maxAttempts = 2, value = Exception.class)
    protected String[] searchMuleFlow(String httpMethod, String apiPath, String muleFlowXmlContent) throws Exception {
        logger.info("start to search MuleFlow " + httpMethod + ":" + apiPath);
        String task = "search_" + httpMethod + "_muleFlow";
        String promptTemplate = readClasspathFile("prompts/" + task + ".txt");
        String prompt = String.format(promptTemplate, apiPath, muleFlowXmlContent);
        OpenAIResult result = getGptResponse(task, prompt);
        String[] codeblocks = Utils.splitReturnContent(result.getContent());
        if (httpMethod.equals("post") && codeblocks.length != 6) {
            throw new IllegalArgumentException("searchMuleFlow return must have exactly 6 items");
        } else if (httpMethod.equals("get") && codeblocks.length != 3) {
            throw new IllegalArgumentException("searchMuleFlow return must have exactly 3 items");
        }
        return codeblocks;
    }

    @Retryable(maxAttempts = 2, value = Exception.class)
    protected String[] searchExamples(String projectPath, String httpMethod, String apiPath, String examplesFilenames) throws Exception {
        logger.info("start to search examples " + httpMethod + ":" + apiPath);
        String task = "search_" + httpMethod + "_examples";
        String promptTemplate = readClasspathFile("prompts/" + task + ".txt");
        String prompt = String.format(promptTemplate, apiPath, examplesFilenames);
        OpenAIResult result = getGptResponse(task, prompt);
        String[] exampleFilenames = Utils.splitReturnContent(result.getContent());
        String exampleResponseContent = FileUtil.getExamplesContent(projectPath, exampleFilenames[0]);
        String exampleRequestContent = "";
        if (httpMethod.equals("post")) {
            exampleRequestContent = FileUtil.getExamplesContent(projectPath, exampleFilenames[1]);
        }
        return new String[]{exampleResponseContent, exampleRequestContent};
    }

    public String readClasspathFile(String filename) throws IOException {
        Resource resource = resourceLoader.getResource("classpath:" + filename);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
