module io.github.cyfko.jpametamodel {
    requires com.google.auto.service;
    requires transitive io.github.cyfko.projection;
    requires java.compiler;

    exports io.github.cyfko.jpametamodel.api;
    exports io.github.cyfko.jpametamodel.providers;
    exports io.github.cyfko.jpametamodel.util;
    exports io.github.cyfko.jpametamodel;

    provides javax.annotation.processing.Processor with io.github.cyfko.jpametamodel.processor.MetamodelProcessor;
}