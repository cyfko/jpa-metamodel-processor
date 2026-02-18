package io.github.cyfko.jpametamodel.processor;

import com.google.auto.service.AutoService;
import io.github.cyfko.projection.Projection;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.HashSet;
import java.util.Set;

/**
 * Main annotation processor that orchestrates entity and projection processing.
 * This is the only processor registered via @AutoService.
 * 
 * <p>
 * Processing happens in two phases:
 * <ol>
 * <li><strong>Phase 1:</strong> Process @Entity annotations via
 * {@link EntityProcessor}</li>
 * <li><strong>Phase 2:</strong> Process @Projection annotations via
 * {@link ProjectionProcessor}</li>
 * </ol>
 * </p>
 * 
 * <p>
 * This ensures that entity metadata is available when validating projection
 * mappings.
 * </p>
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("io.github.cyfko.projection.Projection")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class MetamodelProcessor extends AbstractProcessor {

    private Types typeUtils;
    private Elements elementUtils;

    private EntityProcessor entityProcessor;
    private ProjectionProcessor projectionProcessor;
    private boolean entitiesProcessed = false;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.elementUtils = processingEnv.getElementUtils();
        this.typeUtils = processingEnv.getTypeUtils();

        // Initialize delegate processors
        this.entityProcessor = new EntityProcessor(processingEnv);
        this.projectionProcessor = new ProjectionProcessor(processingEnv, entityProcessor);

        log("🚀 Projection Metamodel Processor initialized");
    }

    /**
     * Processes annotations in the current round.
     * <p>
     * Orchestrates the processing phases:
     * 1. Collects referenced entities from Projections.
     * 2. Processes entities (Phase 1).
     * 3. Processes projections (Phase 2).
     * 4. Verifies type compatibility (Phase 3).
     * 5. Generates code (Final Phase).
     * </p>
     *
     * @param annotations the annotation types requested to be processed
     * @param roundEnv    environment for information about the current and prior
     *                    round
     * @return true if the annotations are claimed by this processor, false
     *         otherwise
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        log("=== PROCESS ROUND START ===");
        Messager messager = processingEnv.getMessager();

        // ==================== Phase 0 : Collecte des entités référencées
        // ====================
        Set<String> referencedEntities = new HashSet<>();
        Set<TypeElement> projectionDtos = new HashSet<>();
        for (Element element : roundEnv.getElementsAnnotatedWith(Projection.class)) {
            if (element.getKind() != ElementKind.INTERFACE) {
                messager.printError("@Projection element must be an interface", element);
            }

            TypeElement dtoClass = (TypeElement) element;
            projectionDtos.add(dtoClass);

            AnnotationProcessorUtils.processExplicitFields(
                    dtoClass,
                    Projection.class.getCanonicalName(),
                    (params) -> referencedEntities.add((String) params.get("from")),
                    null);
        }

        // ==================== Phase 1 : Traitement des entités nécessaires
        // ====================
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

        // ==================== Phase 2 : Traitement des projections
        // ====================
        if (entitiesProcessed && !projectionDtos.isEmpty()) {
            log("═══════════════════════════════════════════════════");
            log("  Phase 2: Projection Processing");
            log("═══════════════════════════════════════════════════");
            projectionProcessor.setReferencedProjection(projectionDtos);
            projectionProcessor.processProjections();
        }

        // ==================== Phase 3 : Post-vérification de compatibilité des types
        // ====================
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
        return false; // Permettre la coexistence avec d'autres processeurs d'annotations traitant
                      // @Projection
    }

    /**
     * Verifies compatibility of projected types for a given DTO.
     * 
     * @param dtoClass the DTO type element
     * @param messager messager for error reporting
     * @param visited  set of visited types to avoid infinite recursion
     */
    private void verifyProjectionTypeCompatibility(TypeElement dtoClass, Messager messager, Set<String> visited) {
        if (!visited.add(dtoClass.getQualifiedName().toString()))
            return; // éviter récursion infinie
        // Récupérer la métadonnée de projection
        var projectionMeta = projectionProcessor.getRegistry().get(dtoClass.getQualifiedName().toString());
        if (projectionMeta == null)
            return;

        String entityClassName = projectionMeta.entityClass();
        var entityFields = entityProcessor.getRegistry().get(entityClassName);
        if (entityFields == null)
            return;

        for (var mapping : projectionMeta.directMappings()) {
            String dtoField = mapping.dtoField();
            String dtoFieldType = mapping.dtoFieldType();

            projectionProcessor.validateEntityFieldPath(entityClassName, mapping.entityField(), entityFieldType -> {

                // Vérification récursive si le champ projeté est lui-même une projection
                TypeElement dtoFieldTypeElement = elementUtils.getTypeElement(dtoFieldType);
                if (dtoFieldTypeElement != null && projectionProcessor.getRegistry()
                        .containsKey(dtoFieldTypeElement.getQualifiedName().toString())) {
                    // Champ DTO = projection imbriquée, vérifier récursivement
                    verifyProjectionTypeCompatibility(dtoFieldTypeElement, messager, visited);
                } else {
                    // Champ simple : vérifier assignabilité stricte
                    TypeMirror dtoType = dtoFieldTypeElement != null ? dtoFieldTypeElement.asType() : null;
                    TypeElement entityFieldTypeElement = (TypeElement) typeUtils.asElement(entityFieldType);
                    TypeMirror entityType = entityFieldTypeElement != null ? entityFieldTypeElement.asType() : null;
                    if (dtoType != null && entityType != null && !typeUtils.isSameType(dtoType, entityType)) {
                        messager.printError("Projected field has a type mismatch '" + dtoField + "' : DTO=" + dtoFieldType
                                        + ", Entity=" + entityFieldType,
                                dtoClass);
                    }
                }
            });
        }
    }

    /**
     * Logs an informational message with a standard prefix.
     *
     * @param message the message to log
     */
    private void log(String message) {
        processingEnv.getMessager().printNote("[ProjectionProcessor] " + message);
    }
}