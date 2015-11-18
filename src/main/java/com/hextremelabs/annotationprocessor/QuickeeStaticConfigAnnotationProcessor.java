/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.hextremelabs.annotationprocessor;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author oladeji
 */
@SupportedAnnotationTypes("com.hextremelabs.quickee.configuration.StaticConfig")
public class QuickeeStaticConfigAnnotationProcessor extends AbstractProcessor {

  private static final String BLANK = "\"-\"";
  private static final StringBuilder outputBuilder = new StringBuilder();
  private static final Map<String, String> indexMap = new LinkedHashMap<>();
  private static boolean alreadyVisited = false;

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Messager messager = processingEnv.getMessager();
    messager.printMessage(Diagnostic.Kind.NOTE, "Indexing @StaticConfig annotation targets...");

    if (annotations == null || annotations.isEmpty()) {
      return true;
    }

    InputStream inputStream = null;
    PrintWriter writer = null;
    int a = 0;
    try {
      FileObject outputObject = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT,
          "com.hextremelabs.quickee.configuration", "static_config_injection_candidates", (Element) null);

      OutputStream outputStream = outputObject.openOutputStream();
      writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(outputStream, "UTF-8")));
      //writer.write(existingContent);
      if (!alreadyVisited) {
        outputBuilder.append("# @StaticConfig field injection targets.\n");
        outputBuilder.append("# <class> <field> <key> <tenant>\n");
        outputBuilder.append("# - (minus) represents a blank value and should be ignored.\n");
      }

      for (TypeElement te : annotations) {
        for (Element e : roundEnv.getElementsAnnotatedWith(te)) {
          String parent
              = ((QualifiedNameable) e.getEnclosingElement()).getQualifiedName().toString();
          String field = e.getSimpleName().toString();
          String qualifiedFieldName = parent + "." + field;
          messager.printMessage(Diagnostic.Kind.NOTE, "Processing: " + qualifiedFieldName);

          String key = BLANK;
          String tenant = BLANK;

          List<? extends AnnotationMirror> annotationMirrors = e.getAnnotationMirrors();
          for (AnnotationMirror mirror : annotationMirrors) {
            key = BLANK;
            tenant = BLANK;

            Map<? extends ExecutableElement, ? extends AnnotationValue> mirrorMap
                = mirror.getElementValues();
            for (ExecutableElement values : mirrorMap.keySet()) {
              String entryName = values.getSimpleName().toString();
              if ("key".equalsIgnoreCase(entryName)) {
                key = mirrorMap.get(values).toString();
              } else if ("tenant".equalsIgnoreCase(entryName)) {
                tenant = mirrorMap.get(values).toString();
              }
            }
          }

          String line = parent + " " + field + " " + strip(key) + " " + strip(tenant) + "\n";
          indexMap.put(qualifiedFieldName, line);
        }
      }

      for (String line : indexMap.values()) {
        outputBuilder.append(line);
      }

      messager.printMessage(Diagnostic.Kind.NOTE, "Current indexing batch complete.");
      messager.printMessage(Diagnostic.Kind.NOTE, "Writing index in archive...");
      writer.write(outputBuilder.toString());
    } catch (Exception exc) {
      messager.printMessage(Diagnostic.Kind.ERROR, "Unable to index injection candidates! "
          + exc.getMessage());
      messager.printMessage(Diagnostic.Kind.ERROR, exc.getClass().getName());
    } finally {
      if (writer != null) {
        writer.flush();
      }

      IOUtils.closeQuietly(writer);
      IOUtils.closeQuietly(inputStream);
    }

    alreadyVisited = true;
    return true;
  }

  private String strip(String input) {
    return input.substring(1, input.length() - 1);
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }
}
