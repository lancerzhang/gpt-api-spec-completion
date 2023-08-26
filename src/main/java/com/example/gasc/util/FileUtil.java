package com.example.gasc.util;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public static List<String> extractMarkdownCodeBlocks(String markdown) {
        // This regular expression will match code blocks delimited by triple backticks
        Pattern pattern = Pattern.compile("```(.*?)```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(markdown);

        List<String> codeBlocks = new ArrayList<>();
        while (matcher.find()) {
            codeBlocks.add(matcher.group(1).trim());  // group(1) gives the content inside the backticks
        }

        return codeBlocks;
    }

    public static List<String> getJsonFilenames(String filenameStr) {
        List<String> paths = new ArrayList<>();

        // Split the string based on newline characters
        String[] filenames = filenameStr.split("\\\\n", -1);

        // For each file name, construct the desired file path
        for (String filename : filenames) {
            // Check if the filename is not empty to avoid constructing paths for blank lines
            if (!filename.isEmpty()) {
                paths.add(filename + ".json");
            }
        }

        return paths;
    }

    public static String getExamplesContent(String projectPath, String filenameStr) throws IOException {
        List<String> paths = getJsonFilenames(filenameStr);
        StringBuilder contentBuilder = new StringBuilder();

        for (String path : paths) {
            contentBuilder.append(path);
            contentBuilder.append("\n");
            String fullPath = projectPath + "/src/main/api/examples/" + path;
            contentBuilder.append(new String(Files.readAllBytes(Paths.get(fullPath))));
            contentBuilder.append("\n");  // Separate contents of each file with a newline
        }

        return contentBuilder.toString();
    }
}
