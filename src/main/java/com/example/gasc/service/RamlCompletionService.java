package com.example.gasc.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.raml.v2.api.RamlModelBuilder;
import org.raml.v2.api.RamlModelResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

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

        List<String> endpointFilters = getEndpointFilters(projectPath);
        processRamlEndpoints(ramlFile.getAbsolutePath(), endpointFilters);

    }

    private List<String> getEndpointFilters(String projectPath) {
        File filterFile = Paths.get(projectPath, "src", "main", "api", "endPointFilter.json").toFile();

        if (!filterFile.exists()) {
            return Collections.emptyList();  // return an empty list if file doesn't exist
        }

        try {
            // Using Jackson to parse the JSON file
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(filterFile, new TypeReference<List<String>>() {
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to read endpoint filter file.", e);
        }
    }

    public void processRamlEndpoints(String ramlPath, List<String> filters) {
        RamlModelResult ramlModelResult = new RamlModelBuilder().buildApi(ramlPath);

        if (ramlModelResult.hasErrors()) {
            ramlModelResult.getValidationResults().forEach(validationResult ->
                    System.out.println(validationResult.getMessage())
            );
            return;
        }

        if (ramlModelResult.isVersion08()) {
            ramlModelResult.getApiV08().resources().forEach(resource -> {
                printEndpointsForRaml08(resource, filters);
            });
        } else {
            ramlModelResult.getApiV10().resources().forEach(resource -> {
                printEndpointsForRaml10(resource, filters);
            });
        }
    }

    private void printEndpointsForRaml10(org.raml.v2.api.model.v10.resources.Resource resource, List<String> filters) {
        String fullPath = getFullPath(resource);
        if (filters.isEmpty() || filters.contains(fullPath)) {
            resource.methods().forEach(method -> {
                System.out.println(method.method() + " " + fullPath);
            });
        }
        resource.resources().forEach(subResource -> printEndpointsForRaml10(subResource, filters));
    }

    private void printEndpointsForRaml08(org.raml.v2.api.model.v08.resources.Resource resource, List<String> filters) {
        String fullPath = getFullPath(resource);
        if (filters.isEmpty() || filters.contains(fullPath)) {
            resource.methods().forEach(method -> {
                System.out.println(method.method() + " " + fullPath);
            });
        }
        resource.resources().forEach(subResource -> printEndpointsForRaml08(subResource, filters));
    }

    private String getFullPath(org.raml.v2.api.model.v08.resources.Resource resource) {
        if (resource.parentResource() != null) {
            return getFullPath(resource.parentResource()) + resource.relativeUri().value();
        }
        return resource.relativeUri().value();
    }

    private String getFullPath(org.raml.v2.api.model.v10.resources.Resource resource) {
        if (resource.parentResource() != null) {
            return getFullPath(resource.parentResource()) + resource.relativeUri().value();
        }
        return resource.relativeUri().value();
    }


}
