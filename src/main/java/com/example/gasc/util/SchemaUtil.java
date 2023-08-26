package com.example.gasc.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SchemaUtil {
    private static final Logger logger = LoggerFactory.getLogger(SchemaUtil.class);

    public static String generateJsonSchema(Class<?> clazz) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(mapper);
        JsonSchema schema = schemaGen.generateSchema(clazz);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
    }


    public static String writeSchema(String projectPath, String name, String content) throws Exception {
        String filename = name + ".jsd";
        Path schemaPath = Paths.get(projectPath, "src", "main", "api", "schema", filename);
        logger.info("writing schema: " + schemaPath);
        Files.write(schemaPath, content.getBytes());
        return filename;
    }
}
