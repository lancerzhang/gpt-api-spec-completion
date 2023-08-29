package com.example.gasc.util;

import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FilenameFilter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public class XmlUtil {
    private static final String muleAppPath = "/src/main/app/";

    public static List<String> findDwl(String flowName, String projectPath) throws Exception {
        File muleAppDirectory = FileUtil.getPath(projectPath + muleAppPath).toFile();
        List<String> dwlPaths = new ArrayList<>();

        // Start the recursive search from the flowName provided
        searchDwl(flowName, muleAppDirectory, dwlPaths);

        return dwlPaths;
    }

    protected static void searchDwl(String flowName, File directory, List<String> dwlPaths) throws Exception {
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
            processNodeListDwl(flowList, flowName, directory, dwlPaths);
            processNodeListDwl(subFlowList, flowName, directory, dwlPaths);

        }
    }

    protected static void processNodeListDwl(NodeList flowList, String flowName, File directory, List<String> dwlPaths) throws Exception {
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
                            String variableName = dwSetPayloadElement.getAttribute("variableName");
                            dwlPaths.add(variableName + "=" + resourcePath.replace("classpath:", ""));
                        }
                    }
                }
            }
        }
    }

    public static String searchMuleFlowXml(String apiName, String projectPath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        String folderPath = FileUtil.changeToSystemFileSeparator(projectPath + muleAppPath);
        Document finalDocument = builder.newDocument();
        Element root = finalDocument.createElement("mule");
        finalDocument.appendChild(root);

        searchMuleFlowXmlHelper(apiName, folderPath, root, finalDocument, builder);

        // Convert Document to String
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        StringWriter stringWriter = new StringWriter();
        transformer.transform(new DOMSource(finalDocument), new StreamResult(stringWriter));

        return stringWriter.toString();
    }

    protected static void searchMuleFlowXmlHelper(String apiName, String folderPath, Element root, Document finalDocument, DocumentBuilder builder) {
        Element flow = findFlow(apiName, folderPath, builder);
        if (flow != null) {
            // Copy over namespace declarations from the mule node
            Element originalMuleNode = (Element) flow.getOwnerDocument().getDocumentElement();
            NamedNodeMap attributes = originalMuleNode.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Attr attr = (Attr) attributes.item(i);
                if (attr.getName().startsWith("xmlns:")) {
                    root.setAttribute(attr.getName(), attr.getValue());
                }
            }

            Node copiedFlow = finalDocument.importNode(flow, true);

            // Remove unwanted attributes before appending to root
            removeUnwantedAttributes((Element) copiedFlow);

            root.appendChild(copiedFlow);

            List<String> flowRefs = findFlowRefs(flow);
            for (String refName : flowRefs) {
                // Recursive call
                searchMuleFlowXmlHelper(refName, folderPath, root, finalDocument, builder);
            }
        }
    }


    protected static Element findFlow(String apiName, String folderPath, DocumentBuilder builder) {
        String[] tags = {"flow", "sub-flow"};
        for (String tag : tags) {
            File folder = new File(folderPath);
            File[] listOfFiles = folder.listFiles();
            if (listOfFiles != null) {
                for (File file : listOfFiles) {
                    if (file.isFile() && file.getName().endsWith(".xml")) {
                        try {
                            Document doc = builder.parse(file);
                            NodeList flows = doc.getElementsByTagName(tag);
                            for (int i = 0; i < flows.getLength(); i++) {
                                Element flow = (Element) flows.item(i);
                                if (flow.getAttribute("name").equals(apiName)) {
                                    return flow;
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        return null;
    }

    protected static List<String> findFlowRefs(Element element) {
        List<String> refs = new ArrayList<>();
        NodeList flowRefs = element.getElementsByTagName("flow-ref");
        for (int i = 0; i < flowRefs.getLength(); i++) {
            Node flowRef = flowRefs.item(i);
            refs.add(((Element) flowRef).getAttribute("name"));
        }
        return refs;
    }

    protected static void removeUnwantedAttributes(Element element) {
        String[] unwantedAttributes = {"logger", "exception-strategy", "object-to-string-transformer"};
        for (String unwanted : unwantedAttributes) {
            NodeList unwantedNodes = element.getElementsByTagName(unwanted);
            for (int i = unwantedNodes.getLength() - 1; i >= 0; i--) {
                Node unwantedNode = unwantedNodes.item(i);
                unwantedNode.getParentNode().removeChild(unwantedNode);
            }
        }
    }
}
