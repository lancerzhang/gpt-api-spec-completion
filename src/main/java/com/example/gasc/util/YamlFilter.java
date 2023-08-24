package com.example.gasc.util;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.Map;


public class YamlFilter {
    @SuppressWarnings("unchecked")
    public static Map<String, Object> filterYamlByAnother(File dataFile, File filterFile) {
        Yaml yaml = new Yaml();
        Map<String, Object> dataMap;
        Map<String, Object> filterMap;
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
    private static void filterData(Map<String, Object> data, Map<String, Object> filter) {
        data.keySet().removeIf(key -> key.startsWith("/") && !filter.containsKey(key));
        for (String key : data.keySet()) {
            Object dataValue = data.get(key);
            Object filterValue = filter.get(key);
            if (dataValue instanceof Map && filterValue instanceof Map) {
                filterData((Map<String, Object>) dataValue, (Map<String, Object>) filterValue);
            }
        }
    }
}