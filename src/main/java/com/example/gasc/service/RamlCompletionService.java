package com.example.gasc.service;

import com.example.gasc.util.YamlFilter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.raml.v2.api.RamlModelBuilder;
import org.raml.v2.api.RamlModelResult;
import org.raml.v2.api.model.v08.api.Api;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RamlCompletionService {

    public void process(String projectPath) {
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

        Map<String, Object> filteredData = YamlFilter.filterYamlByAnother(dataYaml, filterYaml);

        Yaml yaml = new Yaml();
        try (FileWriter writer = new FileWriter(new File(apiSpecDirectory, "filtered_api.raml"))) {
            yaml.dump(filteredData, writer);
        } catch (IOException e) {
            throw new RuntimeException("Error writing to filtered_api.raml", e);
        }
    }

}

