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
    protected void generateSchema(String projectPath, String methodName, String apiPath, String[] codeblocks, Map<Object, Object> postBodyMap, Map<Object, Object> responseBodyMap) throws Exception {
        logger.info("start to search generate Schema " + methodName + ":" + apiPath);
        String respDwContentStr = codeblocks[0];
        String respJavaContents = codeblocks[1];
        String reqDwContent = "";
        String reqJavaContents = "";
        if (methodName.equals("post")) {
            reqDwContent = codeblocks[2];
            reqJavaContents = codeblocks[3];
        }
        String task = "generate_" + methodName + "_schema";
        String promptTemplate = readClasspathFile("prompts/" + task + ".txt");
        String prompt = String.format(promptTemplate, apiPath, respDwContentStr, respJavaContents, reqDwContent, reqJavaContents);
        OpenAIResult result = getGptResponse(task, prompt);
        String[] schemaCode = Utils.splitReturnContent(result.getContent());

        Map<String, String> responseMap = new HashMap<>();
        String responseSchemaName = JavaUtil.convertToCamelCase(methodName + apiPath + "/ResponseBody");
        String responseSchemaFileName = JsonSchemaUtil.writeSchema(projectPath, responseSchemaName, schemaCode[0]);
        responseMap.put("schema", "!include schema/" + responseSchemaFileName);
        responseBodyMap.put("application/json", responseMap);

        if (methodName.equals("post")) {
            Map<String, String> requestMap = new HashMap<>();
            String requestSchemaName = JavaUtil.convertToCamelCase(methodName + apiPath + "/RequestBody");
            String requestSchemaFileName = JsonSchemaUtil.writeSchema(projectPath, requestSchemaName, schemaCode[1]);
            requestMap.put("schema", "!include schema/" + requestSchemaFileName);
            postBodyMap.put("application/json", requestMap);
        }
    }

    @Retryable(maxAttempts = 2, value = Exception.class)
    protected String[] searchMuleFlow(String projectPath, String methodName, String apiPath, String muleFlowXmlContent) throws Exception {
        logger.info("start to search MuleFlow " + methodName + ":" + apiPath);
        String task = "search_" + methodName + "_muleFlow";
        String promptTemplate = readClasspathFile("prompts/" + task + ".txt");
        String prompt = String.format(promptTemplate, apiPath, muleFlowXmlContent);
        OpenAIResult result = getGptResponse(task, prompt);
        String[] codeblocks = Utils.splitReturnContent(result.getContent());
        String respDwContentStr = codeblocks[0];
        String respDwlFileStr = codeblocks[1];
        String respDwlContent = DwlUtil.getDwlContent(respDwlFileStr, projectPath);
        respDwContentStr = respDwContentStr + "\n" + respDwlContent;

        String reqDwContent = "";
        String requestJavaStr = "";

        if (methodName.equals("post")) {
            reqDwContent = codeblocks[3];
            String reqDwlFileStr = codeblocks[4];
            String reqDwlContent = DwlUtil.getDwlContent(reqDwlFileStr, projectPath);
            reqDwContent = reqDwContent + reqDwlContent;
            requestJavaStr = codeblocks[5];
        }
        return new String[]{respDwContentStr, codeblocks[2], reqDwContent, requestJavaStr};
    }

    @Retryable(maxAttempts = 2, value = Exception.class)
    protected String[] searchExamples(String projectPath, String methodName, String apiPath, String examplesFilenames) throws Exception {
        logger.info("start to search examples " + methodName + ":" + apiPath);
        String task = "search_" + methodName + "_examples";
        String promptTemplate = readClasspathFile("prompts/" + task + ".txt");
        String prompt = String.format(promptTemplate, apiPath, examplesFilenames);
        OpenAIResult result = getGptResponse(task, prompt);
        String[] exampleFilenames = Utils.splitReturnContent(result.getContent());
        String exampleResponseContent = FileUtil.getExamplesContent(projectPath, exampleFilenames[0]);
        String exampleRequestContent = "";
        if (methodName.equals("post")) {
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
