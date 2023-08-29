package com.example.gasc.util;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DwlUtil {

    private static final String resourcePath = "/src/main/resources/";

    /**
     * Get the Java class name from a DWL file.
     *
     * @param dwlFileName The name of the DWL file.
     * @return The Java class name, or null if not found.
     * @throws IOException If there's an issue reading the file.
     */
    public static String getJavaClassFromDwl(String dwlFileName, String projectPath) throws IOException {
        String content = new String(Files.readAllBytes(FileUtil.getPath(projectPath + resourcePath + dwlFileName)));

        Pattern pattern = Pattern.compile("class\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Get the content of multiple DWL files.
     *
     * @param dwlVars     List of DWL variable definitions, e.g. "abc=dwl/setUserInfoReq.dwl".
     * @param projectPath Path to the project.
     * @return Concatenated content of the DWL files.
     * @throws IOException If there's an issue reading the files.
     */
    public static String getDwlContent(List<String> dwlVars, String projectPath) throws IOException {
        StringBuilder contentBuilder = new StringBuilder();

        for (String dwlVar : dwlVars) {
            String[] parts = dwlVar.split("=");
            String variableName = parts.length > 1 ? parts[0].trim() : null;
            String dwlFileName = parts.length > 1 ? parts[1].trim() : parts[0].trim();

            String fileContent = new String(Files.readAllBytes(FileUtil.getPath(projectPath + resourcePath + dwlFileName)));

            if (variableName != null) {
                contentBuilder.append("variableName=").append(variableName)
                        .append(", dwlFilename=").append(dwlFileName)
                        .append("\n");
            }
            contentBuilder.append(fileContent).append("\n");
        }

        return contentBuilder.toString();
    }

    public static String getDwlContent(String dwlFileStr, String projectPath) throws IOException {
        StringBuilder contentBuilder = new StringBuilder();
        String[] dwlFilenames = dwlFileStr.split("\\\\n", -1);
        for (String filename : dwlFilenames) {
            String fileContent = new String(Files.readAllBytes(FileUtil.getPath(projectPath + resourcePath + filename)));
            contentBuilder.append(fileContent).append("\n");
        }
        return contentBuilder.toString();
    }
}
