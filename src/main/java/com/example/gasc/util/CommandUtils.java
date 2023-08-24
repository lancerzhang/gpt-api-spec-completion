package com.example.gasc.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class CommandUtils {
    private static final Logger logger = LoggerFactory.getLogger(CommandUtils.class);
    private static final String mvnFileName = System.getProperties().getProperty("os.name").toLowerCase().contains("windows") ? "mvn.cmd" : "mvn";

    public static void runMvnCompile(String projectPath) throws InterruptedException, IOException {
        String command = mvnFileName + " compile";
        logger.info("Start to run command: " + command);
        Process process = Runtime.getRuntime().exec(command, null, new File(projectPath));
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            logger.debug(line);
        }
        process.waitFor();
        logger.info("Finished running command: " + command);
    }

    public static void runGitClone(String githubRepo, String branch, String targetDirectory)
            throws InterruptedException, IOException {
        String command = "git clone -b " + branch + " " + githubRepo + " " + targetDirectory;
        logger.info("Start to run command: " + command);

        Process process = Runtime.getRuntime().exec(command, null, new File(targetDirectory).getParentFile());
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            logger.debug(line);
        }
        process.waitFor();
        logger.info("Finished running command: " + command);
    }
}
