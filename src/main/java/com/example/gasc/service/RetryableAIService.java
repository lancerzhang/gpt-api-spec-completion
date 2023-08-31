package com.example.gasc.service;

import com.example.gasc.config.GptModel;
import com.example.gasc.model.openai.Message;
import com.example.gasc.model.openai.OpenAIResult;
import com.example.gasc.util.DwlUtil;
import com.example.gasc.util.FileUtil;
import com.example.gasc.util.JavaUtil;
import com.example.gasc.util.SchemaUtil;
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

    @Retryable(maxAttempts = 2, value = IOException.class)
    protected void generateSchema(String projectPath, String methodName, String apiPath, String[] codeblocks, String[] exampleFilenames, Map<Object, Object> postBodyMap, Map<Object, Object> responseBodyMap) throws Exception {
        logger.info("start to search generate Schema " + methodName + ":" + apiPath);
        String respDwContentStr = codeblocks[0];
        String respJavaContents = codeblocks[1];
        String exampleResponseContent = exampleFilenames[0];
        String reqDwContent = "";
        String reqJavaContents = "";
        String exampleRequestContent = "";
        if (methodName.equals("post")) {
            reqDwContent = codeblocks[2];
            reqJavaContents = codeblocks[3];
            exampleRequestContent = exampleFilenames[1];
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

    @Retryable(maxAttempts = 2, value = IOException.class)
    protected String[] searchMuleFlow(String projectPath, String methodName, String apiPath, String muleFlowXmlContent) throws IOException {
        logger.info("start to search MuleFlow " + methodName + ":" + apiPath);
        String task = "search_" + methodName + "_muleFlow";
        String promptTemplate = readClasspathFile("prompts/" + task + ".txt");
        String prompt = String.format(promptTemplate, apiPath, muleFlowXmlContent);
        OpenAIResult result = getGptResponse(task, prompt);
        String[] codeblocks = FileUtil.splitReturnContent(result.getContent());
        String respDwContentStr = codeblocks[0];
        String respDwlFileStr = codeblocks[1];
        String respJavaClassesStr = codeblocks[2];
        String respDwlContent = DwlUtil.getDwlContent(respDwlFileStr, projectPath);
        respDwContentStr = respDwContentStr + "\n" + respDwlContent;
        String respJavaContents = JavaUtil.getSimpleJavaFileContents(respJavaClassesStr, projectPath);

        String reqDwContent = "";
        String reqJavaContents = "";

        if (methodName.equals("post")) {
            reqDwContent = codeblocks[3];
            String reqDwlFileStr = codeblocks[4];
            String reqJavaClassesStr = codeblocks[5];
            String reqDwlContent = DwlUtil.getDwlContent(reqDwlFileStr, projectPath);
            reqDwContent = reqDwContent + reqDwlContent;
            reqJavaContents = JavaUtil.getSimpleJavaFileContents(reqJavaClassesStr, projectPath);
        }
        return new String[]{respDwContentStr, respJavaContents, reqDwContent, reqJavaContents};
    }

    @Retryable(maxAttempts = 2, value = IOException.class)
    protected String[] searchExamples(String projectPath, String methodName, String apiPath, String examplesFilenames) throws IOException {
        logger.info("start to search examples " + methodName + ":" + apiPath);
        String task = "search_" + methodName + "_examples";
        String promptTemplate = readClasspathFile("prompts/" + task + ".txt");
        String prompt = String.format(promptTemplate, apiPath, examplesFilenames);
        OpenAIResult result = getGptResponse(task, prompt);
        String[] exampleFilenames = FileUtil.splitReturnContent(result.getContent());
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
