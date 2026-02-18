# Changelog

All notable changes to this project will be documented in this file.

---

## [1.1.0] — since v1.0.3

### Features
- Added support for allowing a computing method of a computed field to have parameter mismatches if compatible 
(autoboxing/type promotion context).
- Added support for autoboxing/unboxing as well as type promotion when resolving compute method signatures.

### Documentation
- Enhanced Javadoc across the API.

### Refactoring
- Switched to a new paradigm using only `@Projection` on interfaces.

---

## [v1.0.3] — 2026-02-10

### Features
- Added a new method utility to look up method signatures at compile time.

### Fixes
- Fixed `@Method` annotation to use the `value` attribute for the method name instead of `method`.

---

## [v1.0.2] — 2026-02-07

### Refactoring
- Separated API definition from the annotation processor, which is now defined solely by the current project.

---

## [v1.0.1] — 2026-02-07

### Fixes
- Removed the need for `@AutoService` on generated provider implementation classes.
- Added `use` statements for expected generated services within `module-info.java`.

---

## [v1.0.0] — 2026-02-07

Initial release.
