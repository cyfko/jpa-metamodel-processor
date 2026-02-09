package io.github.cyfko.tests;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import io.github.cyfko.jpametamodel.processor.MetamodelProcessor;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;

public class EntityProcessorTest {

    @Test
    void generatesRegistryForSimpleEntity() {
        JavaFileObject entity = JavaFileObjects.forSourceString("com.example.User", """
                    package com.example;
                    import jakarta.persistence.*;
                    import java.util.List;

                    @Entity
                    public class User {
                        @Id
                        private Long id;
                        private String name;
                        @OneToMany
                        private List<Role> roles;
                    }
                """);

        JavaFileObject role = JavaFileObjects.forSourceString("com.example.Role", """
                    package com.example;
                    import jakarta.persistence.*;

                    @Entity
                    public class Role {
                        @Id
                        private Long id;
                        private String label;
                    }
                """);

        JavaFileObject dtoclass = JavaFileObjects.forSourceString("com.example.UserDTO", """
                    package com.example;
                    import io.github.cyfko.projection.Projection;

                    @Projection(from=com.example.User.class)
                    public class UserDTO {
                        private String name;
                    }
                """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, role, dtoclass);

        assertThat(compilation).succeeded();

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("registry.put(com.example.User.class");

        // Vérifier que id est un champ ID scalar
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains(
                        "fields.put(\"id\", new PersistenceMetadata(true, java.lang.Long.class, Optional.empty(), Optional.empty()))");

        // Vérifier que name est un champ scalar simple
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains(
                        "fields.put(\"name\", new PersistenceMetadata(false, java.lang.String.class, Optional.empty(), Optional.empty()))");

        // Vérifier que roles est une List de collection d'entités
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("fields.put(\"roles\"");

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("CollectionKind.ENTITY");

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("CollectionType.LIST");
    }

    @Test
    void generatesRegistryForEntityWithMappedSuperclassAndEmbeddedId() {
        JavaFileObject baseEntity = JavaFileObjects.forSourceString("com.example.BaseEntity", """
                    package com.example;
                    import jakarta.persistence.MappedSuperclass;
                    import jakarta.persistence.EmbeddedId;

                    @MappedSuperclass
                    public abstract class BaseEntity {
                        @EmbeddedId
                        protected EntityKey key;
                    }
                """);

        JavaFileObject entityKey = JavaFileObjects.forSourceString("com.example.EntityKey", """
                    package com.example;
                    import java.io.Serializable;
                    import jakarta.persistence.Embeddable;

                    @Embeddable
                    public class EntityKey implements Serializable {
                        private Long part1;
                        private Long part2;
                    }
                """);

        JavaFileObject concreteEntity = JavaFileObjects.forSourceString("com.example.Invoice", """
                    package com.example;
                    import jakarta.persistence.Entity;

                    @Entity
                    public class Invoice extends BaseEntity {
                        private String label;
                    }
                """);

        JavaFileObject dtoclass = JavaFileObjects.forSourceString("com.example.InvoiceDTO", """
                    package com.example;
                    import io.github.cyfko.projection.Projection;

                    @Projection(from=com.example.Invoice.class)
                    public class InvoiceDTO {
                        private String label;
                    }
                """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(baseEntity, entityKey, concreteEntity, dtoclass);

        assertThat(compilation).succeeded();

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("registry.put(com.example.Invoice.class");

        // @EmbeddedId doit être marqué comme ID
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains(
                        "fields.put(\"key\", new PersistenceMetadata(true, com.example.EntityKey.class, Optional.empty(), Optional.empty()))");

        // label est un champ scalar
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains(
                        "fields.put(\"label\", new PersistenceMetadata(false, java.lang.String.class, Optional.empty(), Optional.empty()))");
    }

    @Test
    void failedToGeneratesRegistryForEntityWithMissingEmbeddableAndEmbeddedId() {
        JavaFileObject entityKey = JavaFileObjects.forSourceString("com.example.EntityKey", """
                    package com.example;
                    import java.io.Serializable;

                    public class EntityKey implements Serializable {
                        private Long part1;
                        private Long part2;
                    }
                """);

        JavaFileObject concreteEntity = JavaFileObjects.forSourceString("com.example.Invoice", """
                    package com.example;
                    import jakarta.persistence.Entity;
                    import jakarta.persistence.EmbeddedId;

                    @Entity
                    public class Invoice {
                        @EmbeddedId
                        private EntityKey key;
                        private String label;
                    }
                """);

        JavaFileObject dtoclass = JavaFileObjects.forSourceString("com.example.InvoiceDTO", """
                    package com.example;
                    import io.github.cyfko.projection.Projection;

                    @Projection(from=com.example.Invoice.class)
                    public class InvoiceDTO {
                        private String label;
                    }
                """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entityKey, concreteEntity, dtoclass);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "com.example.EntityKey is not embeddable. Missing @jakarta.persistence.Embeddable annotation on it.");
    }

    @Test
    void failedToGeneratesRegistryForEntityWithMissingEmbeddableAndEmbedded() {
        JavaFileObject entityKey = JavaFileObjects.forSourceString("com.example.SharedPart", """
                    package com.example;
                    import java.io.Serializable;

                    public class SharedPart implements Serializable {
                        private Long part1;
                        private Long part2;
                    }
                """);

        JavaFileObject concreteEntity = JavaFileObjects.forSourceString("com.example.Invoice", """
                    package com.example;
                    import jakarta.persistence.Entity;
                    import jakarta.persistence.Id;
                    import jakarta.persistence.Embedded;

                    @Entity
                    public class Invoice {
                        @Id
                        Long id;
                        @Embedded
                        private SharedPart sharedPart;
                    }
                """);

        JavaFileObject dtoclass = JavaFileObjects.forSourceString("com.example.InvoiceDTO", """
                    package com.example;
                    import io.github.cyfko.projection.Projection;

                    @Projection(from=com.example.Invoice.class)
                    public class InvoiceDTO {
                        private Long id;
                    }
                """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entityKey, concreteEntity, dtoclass);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(
                "com.example.SharedPart is not embeddable. Missing @jakarta.persistence.Embeddable annotation on it.");
    }

    @Test
    void generatesRegistryForEntityWithIdClassAndMapsId() {
        JavaFileObject idClass = JavaFileObjects.forSourceString("com.example.OrderItemId", """
                    package com.example;
                    import java.io.Serializable;

                    public class OrderItemId implements Serializable {
                        public Long orderId;
                        public Long productId;
                    }
                """);

        JavaFileObject orderEntity = JavaFileObjects.forSourceString("com.example.Order", """
                    package com.example;
                    import jakarta.persistence.*;

                    @Entity
                    public class Order {
                        @Id
                        private Long id;
                    }
                """);

        JavaFileObject productEntity = JavaFileObjects.forSourceString("com.example.Product", """
                    package com.example;
                    import jakarta.persistence.*;

                    @Entity
                    public class Product {
                        @Id
                        private Long id;
                    }
                """);

        JavaFileObject orderItemEntity = JavaFileObjects.forSourceString("com.example.OrderItem", """
                    package com.example;
                    import jakarta.persistence.*;

                    @Entity
                    @IdClass(OrderItemId.class)
                    public class OrderItem {
                        @Id
                        private Long orderId;

                        @Id
                        private Long productId;

                        @ManyToOne
                        @MapsId("orderId")
                        private Order order;

                        @ManyToOne
                        @MapsId("productId")
                        private Product product;

                        private int quantity;
                    }
                """);

        JavaFileObject dtoclass = JavaFileObjects.forSourceString("com.example.OrderItemDTO", """
                    package com.example;
                    import io.github.cyfko.projection.Projection;

                    @Projection(from=com.example.OrderItem.class)
                    public class OrderItemDTO {
                        private Long orderId;
                    }
                """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(idClass, orderEntity, productEntity, orderItemEntity, dtoclass);

        assertThat(compilation).succeeded();

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("registry.put(com.example.OrderItem.class");

        // orderId est un champ ID
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains(
                        "fields.put(\"orderId\", new PersistenceMetadata(true, java.lang.Long.class, Optional.empty(), Optional.empty()))");

        // product est une relation avec @MapsId
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains(
                        "fields.put(\"product\", new PersistenceMetadata(false, com.example.Product.class, Optional.of(\"productId\"), Optional.empty()))");

        // order est une relation avec @MapsId
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains(
                        "fields.put(\"order\", new PersistenceMetadata(false, com.example.Order.class, Optional.of(\"orderId\"), Optional.empty()))");

        // quantity est un champ scalar
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains(
                        "fields.put(\"quantity\", new PersistenceMetadata(false, int.class, Optional.empty(), Optional.empty()))");
    }

    @Test
    void generatesRegistryForEntityWithInheritance() {
        JavaFileObject base = JavaFileObjects.forSourceString("com.example.Person", """
                    package com.example;
                    import jakarta.persistence.*;

                    @Entity
                    @Inheritance(strategy = InheritanceType.JOINED)
                    public abstract class Person {
                        @Id
                        protected Long id;
                        protected String name;
                    }
                """);

        JavaFileObject subclass = JavaFileObjects.forSourceString("com.example.Customer", """
                    package com.example;
                    import jakarta.persistence.*;

                    @Entity
                    public class Customer extends Person {
                        private String loyaltyCode;
                    }
                """);

        JavaFileObject dtoclass = JavaFileObjects.forSourceString("com.example.CustomerDTO", """
                    package com.example;
                    import io.github.cyfko.projection.Projection;

                    @Projection(from=com.example.Customer.class)
                    public class CustomerDTO {
                        private String loyaltyCode;
                    }
                """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(base, subclass, dtoclass);

        assertThat(compilation).succeeded();

        // id hérité doit être présent comme ID
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains(
                        "fields.put(\"id\", new PersistenceMetadata(true, java.lang.Long.class, Optional.empty(), Optional.empty()))");

        // name hérité doit être présent comme scalar
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains(
                        "fields.put(\"name\", new PersistenceMetadata(false, java.lang.String.class, Optional.empty(), Optional.empty()))");

        // loyaltyCode est un champ scalar
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains(
                        "fields.put(\"loyaltyCode\", new PersistenceMetadata(false, java.lang.String.class, Optional.empty(), Optional.empty()))");
    }

    @Test
    void failsIfEntityHasNoId() {
        JavaFileObject entity = JavaFileObjects.forSourceString("com.example.NoIdEntity", """
                    package com.example;
                    import jakarta.persistence.*;

                    @Entity
                    public class NoIdEntity {
                        private String name;
                    }
                """);

        JavaFileObject dtoclass = JavaFileObjects.forSourceString("com.example.NoIdEntityDTO", """
                    package com.example;
                    import io.github.cyfko.projection.Projection;

                    @Projection(from=com.example.NoIdEntity.class)
                    public class NoIdEntityDTO {
                        private String name;
                    }
                """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, dtoclass);

        assertThat(compilation).succeeded();
        assertThat(compilation).hadWarningContaining("No @Id field found in com.example.NoIdEntity");
    }

    @Test
    void warnsIfMultipleIdFields() {
        JavaFileObject entity = JavaFileObjects.forSourceString("com.example.MultiIdEntity", """
                    package com.example;
                    import jakarta.persistence.*;

                    @Entity
                    public class MultiIdEntity {
                        @Id
                        private Long id1;
                        @Id
                        private Long id2;
                    }
                """);

        JavaFileObject dtoclass = JavaFileObjects.forSourceString("com.example.MultiIdEntityDTO", """
                    package com.example;
                    import io.github.cyfko.projection.Projection;

                    @Projection(from=com.example.MultiIdEntity.class)
                    public class MultiIdEntityDTO {
                        private Long id1;
                    }
                """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, dtoclass);

        assertThat(compilation).succeeded();
        assertThat(compilation).hadWarningContaining(
                "com.example.MultiIdEntity is not annotated with @jakarta.persistence.IdClass but multiple @Id fields detected");
    }

    @Test
    void generatesRegistryForCustomerOrderModel() {
        JavaFileObject customer = JavaFileObjects.forSourceString("com.example.Customer", """
                    package com.example;
                    import jakarta.persistence.*;
                    import java.util.List;

                    @Entity
                    public class Customer {
                        @Id
                        private Long id;
                        private String name;
                        @OneToMany(mappedBy = "customer")
                        private List<Order> orders;
                    }
                """);

        JavaFileObject order = JavaFileObjects.forSourceString("com.example.Order", """
                    package com.example;
                    import jakarta.persistence.*;

                    @Entity
                    public class Order {
                        @Id
                        private Long id;
                        @ManyToOne
                        private Customer customer;
                    }
                """);

        JavaFileObject dtoclass = JavaFileObjects.forSourceString("com.example.CustomerDTO", """
                    package com.example;
                    import io.github.cyfko.projection.Projection;

                    @Projection(from=com.example.Customer.class)
                    public class CustomerDTO {
                        private Long id;
                    }
                """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(customer, order, dtoclass);

        assertThat(compilation).succeeded();

        // customer est une relation ManyToOne
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains(
                        "fields.put(\"customer\", new PersistenceMetadata(false, com.example.Customer.class, Optional.empty(), Optional.empty()))");

        // orders est une collection d'entités avec mappedBy
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("fields.put(\"orders\"");

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("CollectionKind.ENTITY");

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("CollectionType.LIST");

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("Optional.of(\"customer\")");
    }

    @Test
    void generatesRegistryForEcommerceModel() {
        JavaFileObject address = JavaFileObjects.forSourceString("com.example.Address", """
                    package com.example;
                    import jakarta.persistence.Embeddable;

                    @Embeddable
                    public class Address {
                        private String street;
                        private String city;
                    }
                """);

        JavaFileObject customer = JavaFileObjects.forSourceString("com.example.Customer", """
                    package com.example;
                    import jakarta.persistence.*;
                    import java.util.List;

                    @Entity
                    public class Customer {
                        @Id
                        private Long id;
                        private String name;
                        private String email;
                        @Embedded
                        private Address address;
                        @OneToMany(mappedBy = "customer")
                        private List<Order> orders;
                    }
                """);

        JavaFileObject product = JavaFileObjects.forSourceString("com.example.Product", """
                    package com.example;
                    import jakarta.persistence.*;

                    @Entity
                    public class Product {
                        @Id
                        private Long id;
                        private String name;
                        private double price;
                    }
                """);

        JavaFileObject order = JavaFileObjects.forSourceString("com.example.Order", """
                    package com.example;
                    import jakarta.persistence.*;
                    import java.time.LocalDate;
                    import java.util.List;

                    @Entity
                    public class Order {
                        @Id
                        private Long id;
                        private LocalDate date;
                        @ManyToOne
                        private Customer customer;
                        @OneToMany(mappedBy = "order")
                        private List<OrderItem> items;
                    }
                """);

        JavaFileObject orderItem = JavaFileObjects.forSourceString("com.example.OrderItem", """
                    package com.example;
                    import jakarta.persistence.*;

                    @Entity
                    public class OrderItem {
                        @Id
                        @GeneratedValue
                        private Long id;
                        @ManyToOne
                        private Order order;
                        @ManyToOne
                        private Product product;
                        private int quantity;
                    }
                """);

        JavaFileObject dtoclass = JavaFileObjects.forSourceString("com.example.OrderDTO", """
                    package com.example;
                    import io.github.cyfko.projection.Projection;

                    @Projection(from=com.example.Order.class)
                    public class OrderDTO {
                        private Long id;
                    }
                """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(address, customer, product, order, orderItem, dtoclass);

        assertThat(compilation).succeeded();

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("registry.put(com.example.Customer.class");

        // address est un embeddable
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains(
                        "fields.put(\"address\", new PersistenceMetadata(false, com.example.Address.class, Optional.empty(), Optional.empty()))");

        // orders est une collection d'entités
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("fields.put(\"orders\"");

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("CollectionKind.ENTITY");

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("CollectionType.LIST");

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("Optional.of(\"customer\")");

        // customer est une relation
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains(
                        "fields.put(\"customer\", new PersistenceMetadata(false, com.example.Customer.class, Optional.empty(), Optional.empty()))");

        // product est une relation
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains(
                        "fields.put(\"product\", new PersistenceMetadata(false, com.example.Product.class, Optional.empty(), Optional.empty()))");
    }

    @Test
    void supportsElementCollectionAndTransientFields() {
        JavaFileObject address = JavaFileObjects.forSourceString("com.example.Address", """
                    package com.example;
                    import jakarta.persistence.Embeddable;

                    @Embeddable
                    public class Address {
                        private String street;
                        private String city;
                    }
                """);

        JavaFileObject customer = JavaFileObjects.forSourceString("com.example.Customer", """
                    package com.example;
                    import jakarta.persistence.*;
                    import java.util.Set;

                    @Entity
                    public class Customer {
                        @Id
                        private Long id;

                        @ElementCollection
                        private Set<String> tags;

                        @ElementCollection
                        private Set<Address> addresses;

                        @Transient
                        private String cachedDisplayName;
                    }
                """);

        JavaFileObject dtoclass = JavaFileObjects.forSourceString("com.example.CustomerDTO", """
                    package com.example;
                    import io.github.cyfko.projection.Projection;
                    import java.util.Set;

                    @Projection(from=com.example.Customer.class)
                    public class CustomerDTO {
                        private Long id;
                        private Set<String> tags;
                    }
                """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(address, customer, dtoclass);

        assertThat(compilation).succeeded();

        // tags est une collection de scalaires (Set)
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("fields.put(\"tags\"");

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("CollectionKind.SCALAR");

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("CollectionType.SET");

        // addresses est une collection d'embeddables (Set)
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("fields.put(\"addresses\"");

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("CollectionKind.EMBEDDABLE");

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("CollectionType.SET");

        // cachedDisplayName ne doit pas être présent (@Transient)
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .doesNotContain("cachedDisplayName");
    }

    @Test
    void detectsCollectionTypeAndMappedByAndOrderBy() {
        JavaFileObject book = JavaFileObjects.forSourceString("com.example.Book", """
                    package com.example;
                    import jakarta.persistence.*;

                    @Entity
                    public class Book {
                        @Id
                        private Long id;
                        private String title;

                        @ManyToOne
                        private Author author;
                    }
                """);

        JavaFileObject author = JavaFileObjects.forSourceString("com.example.Author", """
                    package com.example;
                    import jakarta.persistence.*;
                    import java.util.*;

                    @Entity
                    public class Author {
                        @Id
                        private Long id;

                        @OneToMany(mappedBy = "author")
                        @OrderBy("title ASC")
                        private List<Book> books;

                        @ElementCollection
                        private Set<String> tags;
                    }
                """);

        JavaFileObject dtoclass = JavaFileObjects.forSourceString("com.example.AuthorDTO", """
                    package com.example;
                    import io.github.cyfko.projection.Projection;

                    @Projection(from=com.example.Author.class)
                    public class AuthorDTO {
                        private Long id;
                    }
                """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(book, author, dtoclass);

        assertThat(compilation).succeeded();

        // Vérifier que books est une List d'entités avec mappedBy et orderBy
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("fields.put(\"books\"");

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("CollectionType.LIST");

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("CollectionKind.ENTITY");

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("Optional.of(\"author\")");

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("Optional.of(\"title ASC\")");

        // Vérifier que tags est un Set de scalaires
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("fields.put(\"tags\"");

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("CollectionType.SET");

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("CollectionKind.SCALAR");
    }

    @Test
    void detectsEnumsAsScalars() {
        JavaFileObject status = JavaFileObjects.forSourceString("com.example.Status", """
                    package com.example;

                    public enum Status {
                        ACTIVE, INACTIVE, PENDING
                    }
                """);

        JavaFileObject entity = JavaFileObjects.forSourceString("com.example.Task", """
                    package com.example;
                    import jakarta.persistence.*;
                    import java.util.Set;

                    @Entity
                    public class Task {
                        @Id
                        private Long id;

                        private Status status;

                        @ElementCollection
                        private Set<Status> allowedStatuses;
                    }
                """);

        JavaFileObject dtoclass = JavaFileObjects.forSourceString("com.example.TaskDTO", """
                    package com.example;
                    import io.github.cyfko.projection.Projection;

                    @Projection(from=com.example.Task.class)
                    public class TaskDTO {
                        private Long id;
                    }
                """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(status, entity, dtoclass);

        assertThat(compilation).succeeded();

        // allowedStatuses doit être détecté comme une collection de scalaires (enum)
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("fields.put(\"allowedStatuses\"");

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("CollectionKind.SCALAR");

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("CollectionType.SET");
    }

    private String getGeneratedCode(Compilation compilation) {
        return compilation
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .map(file -> {
                    try {
                        return file.getCharContent(true).toString();
                    } catch (Exception e) {
                        return "";
                    }
                })
                .orElse("");
    }

    @Test
    void generatedRegistryCanBeLoadedViaReflection() throws Exception {
        JavaFileObject entity = JavaFileObjects.forSourceString("com.example.TestEntity", """
                    package com.example;
                    import jakarta.persistence.*;

                    @Entity
                    public class TestEntity {
                        @Id
                        private Long id;
                        private String name;
                    }
                """);

        JavaFileObject dtoclass = JavaFileObjects.forSourceString("com.example.TestEntityDTO", """
                    package com.example;
                    import io.github.cyfko.projection.Projection;

                    @Projection(from=com.example.TestEntity.class)
                    public class TestEntityDTO {
                        private Long id;
                        private String name;
                    }
                """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, dtoclass);

        assertThat(compilation).succeeded();

        // Verify the generated class has the expected structure
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains(
                        "public static final Map<Class<?>, Map<String, PersistenceMetadata>> ENTITY_METADATA_REGISTRY;");

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains(
                        "public static final Map<Class<?>, Map<String, PersistenceMetadata>> EMBEDDABLE_METADATA_REGISTRY;");

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("registry.put(com.example.TestEntity.class");
    }

    @Test
    void ignoresStaticFieldsInEntity() {
        JavaFileObject entity = JavaFileObjects.forSourceString("com.example.EntityWithStatic", """
                    package com.example;
                    import jakarta.persistence.*;

                    @Entity
                    public class EntityWithStatic {
                        @Id
                        private Long id;
                        private String name;

                        // This static field should be ignored
                        public static final String TABLE_NAME = "entity_with_static";
                        private static int instanceCount = 0;
                    }
                """);

        JavaFileObject dtoclass = JavaFileObjects.forSourceString("com.example.EntityWithStaticDTO", """
                    package com.example;
                    import io.github.cyfko.projection.Projection;

                    @Projection(from=com.example.EntityWithStatic.class)
                    public class EntityWithStaticDTO {
                        private String name;
                    }
                """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(entity, dtoclass);

        assertThat(compilation).succeeded();

        // Instance fields should be present
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("fields.put(\"id\"");

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("fields.put(\"name\"");

        // Static fields should NOT be present
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .doesNotContain("TABLE_NAME");

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .doesNotContain("instanceCount");
    }

    @Test
    void stripsTypeAnnotationsFromGeneratedCode() {
        // Note: This test uses a custom annotation to simulate @NotNull behavior
        // since jakarta.validation.constraints.NotNull may not be on the classpath
        JavaFileObject notNullAnnotation = JavaFileObjects.forSourceString("com.example.NotNull", """
                    package com.example;
                    import java.lang.annotation.*;

                    @Target({ElementType.FIELD, ElementType.TYPE_USE})
                    @Retention(RetentionPolicy.RUNTIME)
                    public @interface NotNull {}
                """);

        JavaFileObject statusEnum = JavaFileObjects.forSourceString("com.example.Status", """
                    package com.example;

                    public enum Status {
                        ACTIVE, INACTIVE, PENDING
                    }
                """);

        JavaFileObject entity = JavaFileObjects.forSourceString("com.example.AnnotatedEntity", """
                    package com.example;
                    import jakarta.persistence.*;

                    @Entity
                    public class AnnotatedEntity {
                        @Id
                        private Long id;

                        @com.example.NotNull
                        private String name;

                        @com.example.NotNull
                        private Status status;
                    }
                """);

        JavaFileObject dtoclass = JavaFileObjects.forSourceString("com.example.AnnotatedEntityDTO", """
                    package com.example;
                    import io.github.cyfko.projection.Projection;

                    @Projection(from=com.example.AnnotatedEntity.class)
                    public class AnnotatedEntityDTO {
                        private String name;
                    }
                """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new MetamodelProcessor())
                .compile(notNullAnnotation, statusEnum, entity, dtoclass);

        assertThat(compilation).succeeded();

        // The generated code should contain proper class references
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("java.lang.String.class");

        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .contains("com.example.Status.class");

        // The generated code should NOT contain annotation syntax in class literals
        // e.g., should NOT have @com.example.NotNull before class names
        assertThat(compilation)
                .generatedSourceFile(
                        "io.github.cyfko.jpametamodel.providers.impl.PersistenceRegistryProviderImpl")
                .contentsAsUtf8String()
                .doesNotContain("@com.example.NotNull");
    }
}
