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
     * Génère le fichier SPI dans META-INF/services.
     *
     * @param processingEnv L'environnement de compilation
     * @param serviceInterface L'interface (ex: PersistenceRegistryProvider.class)
     * @param implementationFQN Le nom complet de la classe d'implémentation générée (ex: "io.github...Impl")
     */
    public static void generateServiceProviderInfo(ProcessingEnvironment processingEnv,
                                                   Class<?> serviceInterface,
                                                   String implementationFQN) {
        Messager messager = processingEnv.getMessager();

        // 1. Le nom du fichier DOIT être le FQN de l'interface (sans .class)
        String resourcePath = "META-INF/services/" + serviceInterface.getName();

        messager.printMessage(Diagnostic.Kind.NOTE, "🛠️ Generating SPI file: " + resourcePath);

        try {
            Filer filer = processingEnv.getFiler();

            // 2. Création forcée (sans vérification préalable pour supporter les tests)
            FileObject serviceFile = filer.createResource(
                    StandardLocation.CLASS_OUTPUT,
                    "",
                    resourcePath
            );

            // 3. On écrit le nom complet de l'implémentation à l'intérieur
            try (Writer writer = serviceFile.openWriter()) {
                writer.write(implementationFQN);
            }

            // Log de succès (avec URI pour vérifier où il atterrit)
            messager.printMessage(Diagnostic.Kind.NOTE, "✅ SPI file generated at: " + serviceFile.toUri());

        } catch (FilerException e) {
            // Fichier déjà créé dans ce round -> On ignore
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate SPI file: " + e.getMessage());
        }
    }
}