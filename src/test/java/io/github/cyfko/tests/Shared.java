package io.github.cyfko.tests;

import java.util.List;

public class Shared {
    public static List<String> compilerOptions = List.of(
            "--module-path", System.getProperty("jdk.module.path"), // Il faut récupérer le path manuellement
            "--add-modules", "io.github.cyfko.jpametamodel"
            );
}
