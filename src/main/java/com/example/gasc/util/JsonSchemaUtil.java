package com.example.gasc.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class JsonSchemaUtil {
    private static final String specPath = "/src/main/api/";
    private static final String schemaFolder = "schema/";
    private static final Logger logger = LoggerFactory.getLogger(JsonSchemaUtil.class);

    public static String generateJsonSchema(Class<?> clazz) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(mapper);
        JsonNode mergedSchema = generateSchemaRecursively(clazz, schemaGen, mapper);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mergedSchema);
    }

    public static JsonNode generateJsonSchemaNode(Class<?> clazz) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(mapper);
        return generateSchemaRecursively(clazz, schemaGen, mapper);
    }

    protected static JsonNode generateSchemaRecursively(Class<?> clazz, JsonSchemaGenerator schemaGen, ObjectMapper mapper) throws Exception {
        if (clazz == null || Object.class.equals(clazz)) {
            return mapper.createObjectNode();
        }

        JsonSchema schema = schemaGen.generateSchema(clazz);
        JsonNode schemaNode = mapper.valueToTree(schema);

        Class<?> parentClass = clazz.getSuperclass();
        JsonNode parentSchemaNode = generateSchemaRecursively(parentClass, schemaGen, mapper);

        return mergeJsonNodes(schemaNode, parentSchemaNode);
    }

    protected static JsonNode mergeJsonNodes(JsonNode mainNode, JsonNode updateNode) {
        // Assumes that mainNode and updateNode are ObjectNodes
        if (mainNode.isObject() && updateNode.isObject()) {
            updateNode.fieldNames().forEachRemaining(fieldName -> {
                JsonNode jsonNode = mainNode.get(fieldName);
                // If field doesn't exist or is an object node
                if (jsonNode == null || jsonNode.isNull()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) mainNode).replace(fieldName, updateNode.get(fieldName));
                } else if (jsonNode.isObject()) {
                    mergeJsonNodes(jsonNode, updateNode.get(fieldName));
                } else {
                    if (updateNode.get(fieldName).isMissingNode() || updateNode.get(fieldName).isNull()) {
                        // If the node is missing or null in the updateNode, ignore it
                    } else {
                        // Replace field in main node
                        ((com.fasterxml.jackson.databind.node.ObjectNode) mainNode).replace(fieldName, updateNode.get(fieldName));
                    }
                }
            });
        }
        return mainNode;
    }

    public static String writeSchema(String projectPath, String name, String content) throws Exception {
        String filename = name + ".jsd";
        Path schemaPath = FileUtil.getPath(projectPath + specPath + schemaFolder + filename);
        logger.info("writing schema: " + schemaPath);
        Files.write(schemaPath, content.getBytes());
        return filename;
    }

    public static JsonNode mergeAll(List<JsonNode> jsonNodes) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode merged = mapper.createObjectNode();  // Create an empty ObjectNode

        for (JsonNode jsonNode : jsonNodes) {
            // Assumes that jsonNode is an ObjectNode
            if (jsonNode.isObject()) {
                merged.setAll((ObjectNode) jsonNode);
            }
        }

        return merged;
    }
}
