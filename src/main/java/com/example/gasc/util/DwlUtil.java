package com.example.gasc.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DwlUtil {

    /**
     * Get the Java class name from a DWL file.
     *
     * @param dwlFileName The name of the DWL file.
     * @return The Java class name, or null if not found.
     * @throws IOException If there's an issue reading the file.
     */
    public static String getJavaClassFromDwl(String dwlFileName, String projectPath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(projectPath, "src", "main", "resources", dwlFileName)));

        Pattern pattern = Pattern.compile("class\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
