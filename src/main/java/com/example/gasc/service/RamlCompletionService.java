package com.example.gasc.service;

import com.example.gasc.util.DwlUtil;
import com.example.gasc.util.XmlUtil;
import com.example.gasc.util.YamlUtil;
import org.yaml.snakeyaml.Yaml;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RamlCompletionService {

    private String projectPath;

    public void configure(String projectPath) {
        this.projectPath = projectPath;
    }

    public void process() throws Exception {
        Map<Object, Object> filteredData = YamlUtil.filterRaml(projectPath);

        completeSpec(filteredData);

        Yaml yaml = new Yaml();
        try (FileWriter writer = new FileWriter(Paths.get(projectPath, "src", "main", "api", "filtered_api.yaml").toFile())) {
            yaml.dump(filteredData, writer);
        } catch (IOException e) {
            throw new RuntimeException("Error writing to filtered_api.raml", e);
        }
    }


    @SuppressWarnings("unchecked")
    private void completeSpec(Map<Object, Object> filteredData) throws Exception {
        completeSpecHelper(filteredData, "");
    }

    @SuppressWarnings("unchecked")
    private void completeSpecHelper(Map<Object, Object> currentData, String currentPath) throws Exception {
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
                        String flowName = "post:" + newPath + ":mobile_api-config";
                        List<String> dwlPaths = XmlUtil.findDwl(flowName, projectPath);

                        // Get Java classes from DWL files and add them to a list
                        List<String> javaClasses = new ArrayList<>();
                        for (String dwlPath : dwlPaths) {
                            String javaClass = DwlUtil.getJavaClassFromDwl(dwlPath, projectPath);
                            if (javaClass != null) {
                                javaClasses.add(javaClass);
                            }
                        }

                        // For this example, I'm adding the list of Java classes to the body map.
                        // Adjust this according to your actual requirement.
                        bodyMap.put("application/json", javaClasses);
                    }
                }
            }
        }
    }


}
