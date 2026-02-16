package io.github.cyfko.jpametamodel.processor;

import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;

public class Helper {

    /**
     * Generates the SPI file in META-INF/services.
     *
     * @param processingEnv     The processing environment
     * @param serviceInterface  The interface (e.g.
     *                          PersistenceRegistryProvider.class)
     * @param implementationFQN The fully qualified name of the generated
     *                          implementation class (e.g. "io.github...Impl")
     */
    public static void generateServiceProviderInfo(ProcessingEnvironment processingEnv,
            Class<?> serviceInterface,
            String implementationFQN) {
        Messager messager = processingEnv.getMessager();

        // 1. The file name MUST be the FQN of the interface (without .class)
        String resourcePath = "META-INF/services/" + serviceInterface.getName();

        messager.printMessage(Diagnostic.Kind.NOTE, "🛠️ Generating SPI file: " + resourcePath);

        try {
            Filer filer = processingEnv.getFiler();

            // 2. Forced creation (without prior check to support tests)
            FileObject serviceFile = filer.createResource(
                    StandardLocation.CLASS_OUTPUT,
                    "",
                    resourcePath);

            // 3. Write the implementation full name inside
            try (Writer writer = serviceFile.openWriter()) {
                writer.write(implementationFQN);
            }

            // Success log (with URI to check where it lands)
            messager.printMessage(Diagnostic.Kind.NOTE, "✅ SPI file generated at: " + serviceFile.toUri());

        } catch (FilerException e) {
            // File already created in this round -> Ignore
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate SPI file: " + e.getMessage());
        }
    }
}