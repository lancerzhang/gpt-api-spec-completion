package com.example.gasc.service;

import com.example.gasc.util.YamlFilter;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;

public class RamlCompletionService {

    public void process(String projectPath) {
        Map<Object, Object> filteredData = filterRaml(projectPath);

        completeSpec(filteredData);

        Yaml yaml = new Yaml();
        try (FileWriter writer = new FileWriter(Paths.get(projectPath, "src", "main", "api", "filtered_api.yaml").toFile())) {
            yaml.dump(filteredData, writer);
        } catch (IOException e) {
            throw new RuntimeException("Error writing to filtered_api.raml", e);
        }
    }

    private Map<Object, Object> filterRaml(String projectPath) {
        File apiSpecDirectory = Paths.get(projectPath, "src", "main", "api").toFile();
        File[] ramlFiles = apiSpecDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".raml"));
        if (ramlFiles == null || ramlFiles.length == 0) {
            throw new RuntimeException("No RAML file found in the specified directory.");
        }
        if (ramlFiles.length > 1) {
            throw new RuntimeException("More than one RAML file found. Please ensure only one RAML file is present.");
        }
        File dataYaml = ramlFiles[0];

        File filterYaml = new File(apiSpecDirectory, "endPointFilter.yaml");
        if (!filterYaml.exists()) {
            throw new RuntimeException("endPointFilter.yaml not found in the specified directory.");
        }

        return YamlFilter.filterYamlByAnother(dataYaml, filterYaml);
    }

    @SuppressWarnings("unchecked")
    private void completeSpec(Map<Object, Object> filteredData) {
        completeSpecHelper(filteredData, "");
    }

    @SuppressWarnings("unchecked")
    private void completeSpecHelper(Map<Object, Object> currentData, String currentPath) {
        for (Object objKey : currentData.keySet()) {
            if (!(objKey instanceof String)) {
                continue; // skip if key isn't a String
            }

            String key = (String) objKey;
            Object value = currentData.get(key);

            String newPath = currentPath + key;  // Construct the new path for this level

            if (value instanceof Map) {
                // This recursive call ensures that all nested maps are processed.
                completeSpecHelper((Map<Object, Object>) value, newPath);

                // If current map has a "post" attribute, add the constructed path to its body's application/json
                if (((Map<Object, Object>) value).containsKey("post")) {
                    Map<Object, Object> postMap = (Map<Object, Object>) ((Map<Object, Object>) value).get("post");

                    if (postMap.containsKey("body")) {
                        Map<Object, Object> bodyMap = (Map<Object, Object>) postMap.get("body");
                        String combinedPath = "post:" + newPath + ":mobile_api-config";
                        bodyMap.put("application/json", combinedPath);
                    }
                }
            }
        }
    }


}
