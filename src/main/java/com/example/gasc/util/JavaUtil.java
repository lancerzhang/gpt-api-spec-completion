package com.example.gasc.util;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class JavaUtil {
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

            // Extract fields from current class and add to new class
            List<FieldDeclaration> fields = cu.findAll(FieldDeclaration.class);
            for (FieldDeclaration field : fields) {
                newClass.addMember(field.clone());
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


    private static String getPathFromQualifiedName(String projectPath, String qualifiedName) {
        return projectPath + File.separator + "src" + File.separator + "main" + File.separator + "java" + File.separator + qualifiedName.replace('.', File.separatorChar) + ".java";
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

}
