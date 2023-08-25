package com.example.gasc.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class XmlUtil {

    public static List<String> findDwl(String flowName, String projectPath) throws Exception {
        File muleAppDirectory = Paths.get(projectPath, "src", "main", "app").toFile();
        List<String> dwlPaths = new ArrayList<>();

        // Start the recursive search from the flowName provided
        searchDwl(flowName, muleAppDirectory, dwlPaths);

        return dwlPaths;
    }

    private static void searchDwl(String flowName, File directory, List<String> dwlPaths) throws Exception {
        // Find all XML files in the directory
        File[] xmlFiles = directory.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".xml");
            }
        });

        if (xmlFiles == null) return;

        for (File xmlFile : xmlFiles) {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            // Search for the <flow> element with the specified name
            NodeList flowList = doc.getElementsByTagName("flow");
            NodeList subFlowList = doc.getElementsByTagName("sub-flow");
            processNodeList(flowList, flowName, directory, dwlPaths);
            processNodeList(subFlowList, flowName, directory, dwlPaths);

        }
    }

    private static void processNodeList(NodeList flowList, String flowName, File directory, List<String> dwlPaths) throws Exception {
        for (int i = 0; i < flowList.getLength(); i++) {
            Node flowNode = flowList.item(i);
            if (flowNode.getNodeType() == Node.ELEMENT_NODE) {
                Element flowElement = (Element) flowNode;
                if (flowName.equals(flowElement.getAttribute("name"))) {
                    // If flow-ref is found, recursively search for the referred flow
                    NodeList flowRefList = flowElement.getElementsByTagName("flow-ref");
                    for (int j = 0; j < flowRefList.getLength(); j++) {
                        Element flowRefElement = (Element) flowRefList.item(j);
                        searchDwl(flowRefElement.getAttribute("name"), directory, dwlPaths);
                    }

                    // If dw:set-payload is found, collect DWL paths
                    NodeList dwSetPayloadList = flowElement.getElementsByTagName("dw:set-payload");
                    for (int k = 0; k < dwSetPayloadList.getLength(); k++) {
                        Element dwSetPayloadElement = (Element) dwSetPayloadList.item(k);
                        String resourcePath = dwSetPayloadElement.getAttribute("resource");
                        if (resourcePath != null && resourcePath.startsWith("classpath:")) {
                            dwlPaths.add(resourcePath.replace("classpath:", ""));
                        }
                    }
                }
            }
        }
    }

}
