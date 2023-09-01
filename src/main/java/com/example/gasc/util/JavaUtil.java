package com.example.gasc.util;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class JavaUtil {
    private static final String javaPath = "/src/main/java/";

    /**
     * Merges given Java classes into a new one.
     *
     * @param projectPath  Path to the root of the project.
     * @param classNames   A list of fully qualified class names to merge.
     * @param newClassName The simple name of the new class (without the package name).
     * @return The fully qualified name of the new class.
     * @throws IOException If there's an error reading or writing the files.
     */
    public static String mergeClasses(String projectPath, List<String> classNames, String newClassName) throws IOException {
        CompilationUnit newCU = new CompilationUnit();
        ClassOrInterfaceDeclaration newClass = newCU.addClass(newClassName);

        for (String className : classNames) {
            String classPath = getPathFromQualifiedName(projectPath, className);
            File file = new File(classPath);

            CompilationUnit cu = StaticJavaParser.parse(file);

            // Extract fields from current class, add to new class and generate setters/getters
            List<FieldDeclaration> fields = cu.findAll(FieldDeclaration.class);
            for (FieldDeclaration field : fields) {
                FieldDeclaration clonedField = field.clone();
                newClass.addMember(clonedField);
                generateSetterAndGetters(newClass, clonedField);
            }

            // Extract imports from current class and add to new CompilationUnit
            cu.getImports().forEach(importDeclaration -> {
                newCU.addImport(importDeclaration.getNameAsString());
            });
        }

        // Derive path and package for the new file based on the first class
        String newClassPath = getPathFromQualifiedName(projectPath, classNames.get(0));
        newClassPath = new File(newClassPath).getParent() + File.separator + newClassName + ".java";

        String packageName = classNames.get(0).substring(0, classNames.get(0).lastIndexOf('.'));
        newCU.setPackageDeclaration(packageName);

        Files.write(Paths.get(newClassPath), newCU.toString().getBytes());

        return packageName + "." + newClassName;
    }

    protected static void generateSetterAndGetters(ClassOrInterfaceDeclaration newClass, FieldDeclaration field) {
        String fieldName = field.getVariable(0).getNameAsString();
        String capitalizedField = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

        // Generate Getter
        newClass.addMethod("get" + capitalizedField)
                .setType(field.getElementType())
                .addModifier(com.github.javaparser.ast.Modifier.Keyword.PUBLIC)
                .setBody(new BlockStmt().addStatement("return " + fieldName + ";"));

        // Generate Setter
        newClass.addMethod("set" + capitalizedField)
                .setType("void")
                .addParameter(field.getElementType(), fieldName)
                .addModifier(com.github.javaparser.ast.Modifier.Keyword.PUBLIC)
                .setBody(new BlockStmt().addStatement("this." + fieldName + " = " + fieldName + ";"));
    }

    protected static String getPathFromQualifiedName(String projectPath, String qualifiedName) {
        return FileUtil.changeToSystemFileSeparator(projectPath + javaPath + qualifiedName.replace('.', File.separatorChar) + ".java");
    }

    public static String convertToCamelCase(String input) {
        String[] words = input.split("/");
        StringBuilder camelCaseBuilder = new StringBuilder();

        for (String word : words) {
            camelCaseBuilder.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
        }

        return camelCaseBuilder.toString();
    }

    public static Class<?> loadClassFromFile(String className, String pathToJarOrClassFiles) throws Exception {
        URL url = new File(pathToJarOrClassFiles).toURI().toURL();
        URLClassLoader classLoader = new URLClassLoader(new URL[]{url});
        return classLoader.loadClass(className);
    }

    /**
     * Get the content of all Java files based on the list of class names.
     *
     * @param javaClasses List of fully qualified class names.
     * @param projectPath Root path of the Maven project.
     * @return List of file contents.
     * @throws IOException If there's an issue accessing or reading the files.
     */
    public static List<String> getSimpleJavaFileContents(List<String> javaClasses, String projectPath) throws IOException {
        List<String> fileContents = new ArrayList<>();

        for (String className : javaClasses) {
            Path filePath = getJavaFilePath(projectPath, className);
            String content = new String(Files.readAllBytes(filePath));
            fileContents.add(content);
        }

        return fileContents;
    }

    public static String getSimpleJavaFileContents(String javaClassesStr, String projectPath) throws IOException {

        StringBuilder contentBuilder = new StringBuilder();
        String[] javaClasses = javaClassesStr.split("\\\\n", -1);
        for (String javaClass : javaClasses) {
            if (javaClass.isEmpty() || javaClass.equals("N/A")) {
                continue;
            }
            String filePath = getJavaFilePathStr(projectPath, javaClass);
            String[] splitArray = javaClass.split("\\.");  // Split the string using the dot as the delimiter
            String classNameA = splitArray[splitArray.length - 1];
            String content = extractFieldsIntoNewClass(filePath, classNameA);
            contentBuilder.append(content);
        }

        String result = contentBuilder.toString();
        if (result.isEmpty()) {
            result = "N/A";
        }
        return result;
    }

    /**
     * Convert a fully qualified class name to its corresponding file path.
     *
     * @param projectRoot Root path of the Maven project.
     * @param className   Fully qualified class name.
     * @return Path corresponding to the Java file.
     */
    protected static Path getJavaFilePath(String projectRoot, String className) {
        String relativePath = className.replace('.', '/') + ".java";
        return FileUtil.getPath(projectRoot + javaPath + relativePath);
    }

    protected static String getJavaFilePathStr(String projectRoot, String className) {
        String relativePath = className.replace('.', '/') + ".java";
        return FileUtil.changeToSystemFileSeparator(projectRoot + javaPath + relativePath);
    }

    public static String extractFieldsIntoNewClass(String javaFilePath, String newClassName) {
        String modifiedSourceCode = "";
        try {
            // Load a Java file
            FileInputStream in = new FileInputStream(javaFilePath);
            ParseResult<CompilationUnit> parseResult = new JavaParser().parse(in);

            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit originalCu = parseResult.getResult().get();

                // Create a new CompilationUnit (no package set)
                CompilationUnit newCu = new CompilationUnit();

                // Create a new class declaration
                newCu.addClass(newClassName);

                // Get the new class declaration
                TypeDeclaration<?> newClass = newCu.getClassByName(newClassName).orElse(null);

                // Iterate through all types in the original file (classes, enums, etc.)
                for (TypeDeclaration<?> type : originalCu.getTypes()) {
                    // Add all fields to the new class
                    type.getMembers().stream()
                            .filter(member -> member instanceof FieldDeclaration)
                            .forEach(field -> {
                                if (newClass != null) {
                                    newClass.addMember(field.clone());
                                }
                            });
                }

                // Convert the modified CompilationUnit back to a String
                modifiedSourceCode = newCu.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return modifiedSourceCode;
    }

}
