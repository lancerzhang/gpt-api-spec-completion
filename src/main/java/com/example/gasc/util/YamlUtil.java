package com.example.gasc.util;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.Map;


public class YamlUtil {

    public static Map<Object, Object> filterRaml(String projectPath) {
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

        return filterYamlByAnother(dataYaml, filterYaml);
    }

    @SuppressWarnings("unchecked")
    public static Map<Object, Object> filterYamlByAnother(File dataFile, File filterFile) {
        Yaml yaml = new Yaml();
        Map<Object, Object> dataMap;
        Map<Object, Object> filterMap;
        try {
            dataMap = yaml.load(new FileInputStream(dataFile));
            filterMap = yaml.load(new FileInputStream(filterFile));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Error reading the YAML files", e);
        }

        filterData(dataMap, filterMap);
        return dataMap;
    }

    @SuppressWarnings("unchecked")
    private static void filterData(Map<Object, Object> data, Map<Object, Object> filter) {
        data.keySet().removeIf(key -> ((String) key).startsWith("/") && !filter.containsKey(key));
        for (Object key : data.keySet()) {
            Object dataValue = data.get(key);
            Object filterValue = filter.get(key);
            if (dataValue instanceof Map && filterValue instanceof Map) {
                filterData((Map<Object, Object>) dataValue, (Map<Object, Object>) filterValue);
            }
        }
    }
}