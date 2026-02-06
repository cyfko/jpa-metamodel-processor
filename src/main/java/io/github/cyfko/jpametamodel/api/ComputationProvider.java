package io.github.cyfko.jpametamodel.api;

import java.util.Objects;

public record ComputationProvider(Class<?> clazz, String bean) {
    public ComputationProvider {
        Objects.requireNonNull(clazz, "clazz cannot be null");
        Objects.requireNonNull(bean, "bean cannot be null");
    }
}
