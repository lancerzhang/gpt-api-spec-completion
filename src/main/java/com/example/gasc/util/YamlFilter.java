package com.example.gasc.util;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Map;


public class YamlFilter {
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