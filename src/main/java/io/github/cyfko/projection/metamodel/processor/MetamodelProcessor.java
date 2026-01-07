package io.github.cyfko.projection.metamodel.processor;

import com.google.auto.service.AutoService;
import io.github.cyfko.projection.Projection;
import io.github.cyfko.projection.metamodel.util.AnnotationProcessorUtils;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.HashSet;
import java.util.Set;

/**
 * Main annotation processor that orchestrates entity and projection processing.
 * This is the only processor registered via @AutoService.
 * 
 * <p>Processing happens in two phases:
 * <ol>
 *   <li><strong>Phase 1:</strong> Process @Entity annotations via {@link EntityProcessor}</li>
 *   <li><strong>Phase 2:</strong> Process @Projection annotations via {@link ProjectionProcessor}</li>
 * </ol>
 * </p>
 * 
 * <p>This ensures that entity metadata is available when validating projection mappings.</p>
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("io.github.cyfko.projection.Projection")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class MetamodelProcessor extends AbstractProcessor {

    private EntityProcessor entityProcessor;
    private ProjectionProcessor projectionProcessor;
    private boolean entitiesProcessed = false;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        
        // Initialize delegate processors
        this.entityProcessor = new EntityProcessor(processingEnv);
        this.projectionProcessor = new ProjectionProcessor(processingEnv, entityProcessor);
        
        log("🚀 Projection Metamodel Processor initialized");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        log("=== PROCESS ROUND START ===");
        Messager messager = processingEnv.getMessager();

        // ==================== Phase 0 : Collecte des entités référencées ====================
        Set<String> referencedEntities = new HashSet<>();
        Set<TypeElement> projectionDtos = new HashSet<>();
        for (Element element : roundEnv.getElementsAnnotatedWith(Projection.class)) {
            if (element.getKind() != ElementKind.CLASS) continue;

            TypeElement dtoClass = (TypeElement) element;
            projectionDtos.add(dtoClass);

            AnnotationProcessorUtils.processExplicitFields(
                    dtoClass,
                    Projection.class.getCanonicalName(),
                    (params) -> referencedEntities.add((String) params.get("entity")),
                    null
            );
        }

        // ==================== Phase 1 : Traitement des entités nécessaires ====================
        if (!entitiesProcessed) {
            if (!referencedEntities.isEmpty()) {
                log("═══════════════════════════════════════════════════");
                log("  Phase 1: Entity Metadata Extraction");
                log("═══════════════════════════════════════════════════");

                entityProcessor.setReferencedEntities(referencedEntities);
                entityProcessor.processEntities();
                entitiesProcessed = true;
            }
        }

        // ==================== Phase 2 : Traitement des projections ====================
        if (entitiesProcessed && !projectionDtos.isEmpty()) {
            log("═══════════════════════════════════════════════════");
            log("  Phase 2: Projection Processing");
            log("═══════════════════════════════════════════════════");
            projectionProcessor.setReferencedProjection(projectionDtos);
            projectionProcessor.processProjections();
        }

        // ==================== Phase 3 : Post-vérification de compatibilité des types ====================
        if (entitiesProcessed && !projectionDtos.isEmpty()) {
            for (TypeElement dtoClass : projectionDtos) {
                verifyProjectionTypeCompatibility(dtoClass, messager, new HashSet<>());
            }
        }

        // ==================== Génération des registres ====================
        if (roundEnv.processingOver()) {
            log("═══════════════════════════════════════════════════");
            log("  Final Phase: Code Generation");
            log("═══════════════════════════════════════════════════");

            // Generate entity registry
            if (!entityProcessor.getRegistry().isEmpty()) {
                entityProcessor.generateProviderImpl();
            }

            // Generate projection registry
            if (!projectionProcessor.getRegistry().isEmpty()) {
                projectionProcessor.generateProviderImpl();
            }

            log("═══════════════════════════════════════════════════");
            log("  ✅ Processing Complete");
            log("     Entities: " + entityProcessor.getRegistry().size());
            log("     Projections: " + projectionProcessor.getRegistry().size());
            log("═══════════════════════════════════════════════════");
        }

        log("=== PROCESS ROUND END ===");
        return false; // Permettre la coexistence avec d'autres processeurs d'annotations traitant @Projection
    }

    /**
     * Vérifie la compatibilité des types projetés pour un DTO donné.
     * @param dtoClass le TypeElement du DTO
     * @param messager pour les messages d'erreur
     * @param visited  pour éviter les boucles sur projections imbriquées
     */
    private void verifyProjectionTypeCompatibility(TypeElement dtoClass, Messager messager, Set<String> visited) {
        if (!visited.add(dtoClass.getQualifiedName().toString())) return; // éviter récursion infinie
        // Récupérer la métadonnée de projection
        var projectionMeta = projectionProcessor.getRegistry().get(dtoClass.getQualifiedName().toString());
        if (projectionMeta == null) return;

        String entityClassName = projectionMeta.entityClass();
        var entityFields = entityProcessor.getRegistry().get(entityClassName);
        if (entityFields == null) return;

        for (var mapping : projectionMeta.directMappings()) {
            String dtoField = mapping.dtoField();
            String dtoFieldType = mapping.dtoFieldType();

            projectionProcessor.validateEntityFieldPath(entityClassName, mapping.entityField(), entityFieldType -> {

                // Vérification récursive si le champ projeté est lui-même une projection
                TypeElement dtoFieldTypeElement = processingEnv.getElementUtils().getTypeElement(dtoFieldType);
                if (dtoFieldTypeElement != null && projectionProcessor.getRegistry().containsKey(dtoFieldTypeElement.getQualifiedName().toString())) {
                    // Champ DTO = projection imbriquée, vérifier récursivement
                    verifyProjectionTypeCompatibility(dtoFieldTypeElement, messager, visited);
                } else {
                    // Champ simple : vérifier assignabilité stricte
                    Types types = processingEnv.getTypeUtils();
                    TypeMirror dtoType = dtoFieldTypeElement != null ? dtoFieldTypeElement.asType() : null;
                    TypeElement entityFieldTypeElement = processingEnv.getElementUtils().getTypeElement(entityFieldType);
                    TypeMirror entityType = entityFieldTypeElement != null ? entityFieldTypeElement.asType() : null;
                    if (dtoType != null && entityType != null && !types.isSameType(dtoType, entityType)) {
                        messager.printMessage(Diagnostic.Kind.ERROR,
                                "Projected field has a type mismatch '" + dtoField + "' : DTO=" + dtoFieldType + ", Entity=" + entityFieldType,
                                dtoClass);
                    }
                }
            });
        }
    }

    private void log(String message) {
        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.NOTE,
                "[ProjectionProcessor] " + message
        );
    }
}