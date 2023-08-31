package com.example.gasc.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FileUtil {

    public static String getFilenames(Path dir) throws Exception {
        List<String> fileNamesWithoutExtensions = new ArrayList<>();

        // Use DirectoryStream to iterate over files in the directory
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) {
                    // Get the filename without the extension and save to the list
                    String fileNameWithoutExtension = removeFileExtension(entry.getFileName().toString());
                    fileNamesWithoutExtensions.add(fileNameWithoutExtension);
                }
            }
        }
        return String.join("\n", fileNamesWithoutExtensions);
    }

    protected static String removeFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }

    public static List<String> getJsonFilenames(String filenameStr) {
        List<String> paths = new ArrayList<>();

        // Split the string based on newline characters
        String[] filenames = filenameStr.split("\\\\n", -1);

        // For each file name, construct the desired file path
        for (String filename : filenames) {
            // Check if the filename is not empty to avoid constructing paths for blank lines
            if (!filename.isEmpty() && !filename.equals("N/A")) {
                paths.add(filename + ".json");
            }
        }

        return paths;
    }

    public static String getExamplesContent(String projectPath, String filenameStr) throws IOException {
        List<String> paths = getJsonFilenames(filenameStr);
        StringBuilder contentBuilder = new StringBuilder();

        for (String filename : paths) {
            if (filename != null && !filename.equals("N/A")) {
                if (!filename.endsWith(".json")) {
                    filename = filename + ".json";
                }
                contentBuilder.append(filename);
                contentBuilder.append("\n");
                String fullPath = projectPath + "/src/main/api/examples/" + filename;
                contentBuilder.append(new String(Files.readAllBytes(Paths.get(fullPath))));
                contentBuilder.append("\n");  // Separate contents of each file with a newline
            }
        }

        String result = contentBuilder.toString();
        if (result.isEmpty()) {
            result = "N/A";
        }
        return result;
    }

    public static String changeToSystemFileSeparator(String input) {
        return input.replace("/", File.separator);
    }

    public static Path getPath(String linuxPath) {
        return Paths.get(changeToSystemFileSeparator(linuxPath));
    }

}
