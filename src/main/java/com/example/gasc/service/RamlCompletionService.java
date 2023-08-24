package com.example.gasc.service;

import org.raml.v2.api.RamlModelBuilder;
import org.raml.v2.api.RamlModelResult;

import java.io.File;
import java.nio.file.Paths;

public class RamlCompletionService {

    public void process(String projectPath) {
        // Locate the RAML file in the specified directory
        File ramlDirectory = Paths.get(projectPath, "src", "main", "api").toFile();

        File[] ramlFiles = ramlDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".raml"));

        if (ramlFiles == null || ramlFiles.length == 0) {
            throw new RuntimeException("No RAML file found in the specified directory.");
        }

        if (ramlFiles.length > 1) {
            throw new RuntimeException("More than one RAML file found. Please ensure only one RAML file is present.");
        }

        File ramlFile = ramlFiles[0];

        // Parse and process the RAML file
        RamlModelResult ramlModelResult = new RamlModelBuilder().buildApi(ramlFile);

        if (ramlModelResult.hasErrors()) {
            ramlModelResult.getValidationResults().forEach(validationResult ->
                    System.out.println(validationResult.getMessage())
            );
        } else {
            // Further processing on the RAML file
            // E.g., Enhance the RAML with missing specifications

            // Note: Add your specific RAML processing logic here.
        }
    }
}
