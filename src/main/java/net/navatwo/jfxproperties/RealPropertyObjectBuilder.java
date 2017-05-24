/*
 * Copyright 2017 jfxproperties contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.navatwo.jfxproperties;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.TreeMultimap;
import com.google.common.reflect.Invokable;
import com.google.common.reflect.Parameter;
import com.google.common.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static net.navatwo.jfxproperties.PropertyObject.Builder.matchesPrefix;
import static net.navatwo.jfxproperties.PropertyObject.Builder.matchesSuffix;

/**
 * Does the actual building of the {@link PropertyObject.Builder} class for
 * {@link PropertyObject.Builder#build(TypeToken)}.
 */
@ParametersAreNonnullByDefault
final class RealPropertyObjectBuilder<T> {

    // init it immediately
    private final TypeToken<T> base;

    private static final Logger logger = LogManager.getLogger(RealPropertyObjectBuilder.class);

    private final Map<Class<?>, TypePropertiesCollector> allCollectors;

    private final Set<Class<?>> visited;

    private final TreeMultimap<String, String> propErrorsMap;

    private final PropertyObject.Builder buildParameters;

    private final Predicate<Class<?>> isTypeBadPredicate;

    RealPropertyObjectBuilder(TypeToken<T> base,
                              PropertyObject.Builder buildParameters) {
        this.base = base;
        this.buildParameters = checkNotNull(buildParameters, "buildParameters == null");
        this.allCollectors = new HashMap<>();

        this.propErrorsMap = TreeMultimap.create();

        this.visited = new HashSet<>();


        this.isTypeBadPredicate = token -> {
            if (token == null) {
                return true;
            }

            final String packageName = token.getPackage().getName();
            @SuppressWarnings("UnnecessaryLocalVariable") // its convenient for readability..
                    boolean result = packageName.startsWith("java.")
                    || packageName.startsWith("javax.")
                    || packageName.startsWith("javafx.")
                    || packageName.startsWith("sun.")
                    || packageName.startsWith("com.google.");

            return result;
        };
    }

    private final class TypePropertiesCollector {

        private final TypeToken<?> base;
        private final int hashCode;

        private final Set<String> ignoredProperties;

        private final Map<String, TypeToken<?>> propertyTypes;
//        private final Map<String, TypeToken<?>> elementTypes;

        private final Map<String, Field> fieldsMap;

        private final Map<String, EnumMap<PropertyObject.MethodType, Invokable<?, ?>>> methodsMap;

        TypePropertiesCollector(TypeToken<?> base) {
            this.base = base;
            this.hashCode = base.hashCode();

            this.ignoredProperties = new HashSet<>();
            this.propertyTypes = new HashMap<>();
            this.fieldsMap = new HashMap<>();
            this.methodsMap = new HashMap<>();
        }

        TypePropertiesCollector(PropertyObject<?> object) {
            this(object.getBaseType());


            // This could definitely be done via a small virtual inheritance scheme, but all that the difference is
            // this function. I'm not sure its worth the abstraction and invokeVirtual costs. - kb

            this.ignoredProperties.addAll(object.getIgnoredProperties());
            for (Map.Entry<String, ? extends PropertyInfo<?>> e : object.getLocalProperties().entrySet()) {
                String propName = e.getKey();
                PropertyInfo<?> po = e.getValue();

                this.propertyTypes.put(propName, po.getPropertyType());
                po.getFieldRef().ifPresent(field -> this.fieldsMap.put(propName, field));

                po.getGetterRef().ifPresent(getter ->
                        this.methodsMap.computeIfAbsent(propName, _p -> new EnumMap<>(PropertyObject.MethodType.class))
                                .put(PropertyObject.MethodType.GETTER, getter));

                po.getSetterRef().ifPresent(setter ->
                        this.methodsMap.computeIfAbsent(propName, _p -> new EnumMap<>(PropertyObject.MethodType.class))
                                .put(PropertyObject.MethodType.SETTER, setter));

                po.getAccessorRef().ifPresent(accessor ->
                        this.methodsMap.computeIfAbsent(propName, _p -> new EnumMap<>(PropertyObject.MethodType.class))
                                .put(PropertyObject.MethodType.ACCESSOR, accessor));
            }

            // Force this to be pre-visited
            visited.add(base.getRawType());
        }

        void collect(Deque<Class<?>> q) {
            Class<?> rawCurrent = this.base.getRawType();

            if (rawCurrent == Object.class) {
                return;
            }

            // Visit all of the interfaces implemented, check all of their methods
            // If there are any interfaces, go to those.
            Arrays.stream(rawCurrent.getInterfaces())
                    .forEach(q::addLast); // Visit all of the interfaces!

            // Visit the methods and fields of the current type
            searchFields();
            searchMethods();
            visited.add(base.getRawType());

            // Add the super class so they are traversed.
            Class rawSuperClazz = base.getRawType().getSuperclass();
            if (rawSuperClazz != null) {
                // can't figure out how to not have this be unchecked
                q.addLast(rawSuperClazz);
            }

            // Remove the keys that we found but should be ignored
            fieldsMap.keySet().stream()
                    .filter(ignoredProperties::contains)
                    .collect(Collectors.toList())
                    .forEach(fieldsMap::remove);

            methodsMap.keySet().stream()
                    .filter(ignoredProperties::contains)
                    .collect(Collectors.toList())
                    .forEach(methodsMap::remove);
        }

        TypeToken<?> getBase() {
            return base;
        }


        /**
         * Check all {@link Field Fields} in the class and use this information to extract type information.
         */
        // In the future, we may want to expose synthetic getter/setters via Fields. Though, I feel this is a bad
        // idea as it breaks encapsulation. - kb
        private void searchFields() {
            logger.traceEntry("searchFields()");
            Class<?> clazz = base.getRawType();
            visited.add(clazz);

            if (isTypeBadPredicate.test(clazz)) {
                logger.traceExit("Found invalid class, skipping: {}", base);
                return;
            }

            if (clazz.isInterface()) {
                logger.traceExit("Found interface, no fields to read, {}", base);
                return;
            }

            // Collect all of the fields, we will filter them later
            Arrays.stream(clazz.getDeclaredFields())
                    .filter(f -> !Modifier.isStatic(f.getModifiers())) // only get valid non-static methods
                    .forEach(field -> {
                        // Probably a property suffix
                        Optional<String> prefix = matchesPrefix(field.getName(), buildParameters.getFieldPrefixes());
                        if (!prefix.isPresent()) {
                            // No field prefix is present, thus there's nothing to do here
                            return;
                        }

                        // Now check the rest of the properties of the field
                        final String propName = PropertyObject.Builder.removePrefix(field.getName(), prefix.get());
                        checkState(!propName.isEmpty(), "Found invalid property %s", field);

                        // we have a property field probably!
                        if (isIgnored(field)) {
                            // ignoring the field ignores it _completely_
                            ignoredProperties.add(propName);
                        } else {
                            // Otherwise we need to save the config information
                            if (fieldsMap.containsKey(propName)) {
                                propertyError(propName, "Found multiple fields for name: "
                                        + field + ", " + fieldsMap.get(propName));
                            } else {
                                logger.debug("Found property {} field: {}", propName, field);
                                fieldsMap.put(propName, field);

                                if (!propertyTypes.containsKey(propName)) {
                                    TypeToken<?> fieldType = base.resolveType(field.getGenericType());
                                    propertyTypes.put(propName, MoreReflection.unwrapPropertyType(fieldType));
                                }
                            }
                        }
                    });
        }


        /**
         * Adds a method to the appropriate place in the {@link #methodsMap} field for the property name and handles
         * the {@link IgnoreProperty} behaviour for the {@link Method}. This method was extracted because the
         * behaviour is the same for all of the three different {@link PropertyObject.MethodType MethodTypes}.
         */
        private void handleMethod(PropertyObject.MethodType type, String propName, Invokable<?, ?> m) {
            EnumMap<PropertyObject.MethodType, Invokable<?, ?>> mmap = methodsMap.computeIfAbsent(propName, method -> new EnumMap<>(PropertyObject.MethodType.class));

            if (isIgnored(m)) {
                // This handles the promises outlined in @IgnoreProperty surrounding Method ignoring -- only the
                // current method annotated, not all.
                logger.trace("Ignoring property {} {} method from @IgnoreProperty on {}",
                        propName, type, m);
                mmap.put(type, null);
                return;
            }

            // Get the stored Method for the type. When an interface is implemented, we will find both the
            // abstract and non-abstract versions of the method.
            // If we have this happen, we store the non-abstract version as it's more likely the intended method
            // to be called later reducing any cost of virtual invocations (maybe?)
            Invokable<?, ?> stored = mmap.get(type);
            if (stored != null && !Objects.equals(stored, m)) {

                if (stored.isAbstract()) {
                    // we just replace it with the non-abstract one -- faster :D
                    mmap.put(type, m);
                    logger.trace("Found non-abstract {} for property {}, replaced {} -> {}",
                            type, propName, stored, m);
                } else {
                    propertyError(propName, "Multiple " + type + " found: " + m + " and " + stored);
                }

                logger.debug("Found inherited property {} accessor: {}", propName, m);

            } else if (!mmap.containsKey(type)) {
                // null => ignored
                // Not present => not found

                logger.debug("Found property {} {}: {}", propName, type, m);
                mmap.put(type, m);
            } else {
                logger.debug("Found ignored property {} {}", propName, type);
            }
        }

        /**
         * Checks for the presence of the {@link IgnoreProperty} annotation
         *
         * @param element Element to check for annotation
         * @return {@code true} if the annotation is present
         */
        boolean isIgnored(AnnotatedElement element) {
            return element.isAnnotationPresent(IgnoreProperty.class);
        }

        /**
         * Search for methods on the type
         */
        private void searchMethods() {
            logger.traceEntry("searchMethods()");

            Class<?> clazz = base.getRawType();

            if (isTypeBadPredicate.test(clazz)) { // Don't check Object
                logger.traceExit("Found invalid class, skipping");
                return;
            }

            // For each Method
            Arrays.stream(clazz.getMethods())
                    .map(base::method)
                    .filter(m -> m.getDeclaringClass() != Object.class)
                    .filter(m -> !m.isStatic() && !m.isSynthetic()) // only get valid methods
                    .forEach(m -> {
                        final String mName = m.getName();

                        final TypeToken<?> returnType = m.getReturnType();

                        PropertyObject.MethodType mType = PropertyObject.MethodType.NONE;

                        // Check for a property accessor
                        Optional<String> propSuffix = matchesSuffix(mName, buildParameters.getPropertySuffixes());
                        if (propSuffix.isPresent()) {
                            // Probably a property suffix
                            String propName = PropertyObject.Builder.removeSuffix(mName, propSuffix.get());
                            checkState(!propName.isEmpty(), "Found invalid accessor method %s", m);

                            // we have a property field possibly
                            if (MoreReflection.isReadOnlyProperty(returnType)) {
                                // DEFINITELY have a property
                                mType = PropertyObject.MethodType.ACCESSOR;

                                if (isIgnored(m)) {
                                    ignoredProperties.add(propName);
                                    logger.debug("Ignoring property entirely due to accessor annotation.");
                                } else {
                                    handleMethod(PropertyObject.MethodType.ACCESSOR, propName, m);

                                    propertyTypes.put(propName, MoreReflection.unwrapPropertyType(returnType));
                                }
                            }
                        }

                        // Now a bean-like getter
                        Optional<String> getterPrefix = matchesPrefix(mName, buildParameters.getGetterPrefixes());
                        if (mType == PropertyObject.MethodType.NONE
                                && getterPrefix.isPresent()
                                && !isIgnored(m)) {
                            // is a getter?
                            String propName = PropertyObject.Builder.removePrefix(mName, getterPrefix.get());
                            checkState(!propName.isEmpty(), "Found invalid getter method %s", m);

                            // Check the method signature to make sure its a "getter"
                            if (m.getParameters().isEmpty()
                                    && returnType.getRawType() != void.class) {
                                // have a getter for _something_
                                mType = PropertyObject.MethodType.GETTER;

                                handleMethod(PropertyObject.MethodType.GETTER, propName, m);

                                if (!propertyTypes.containsKey(propName)) {
                                    // getters have a possible return type
                                    propertyTypes.put(propName, MoreReflection.unwrapPropertyType(returnType));
                                }
                            }
                        }

                        // Now a bean-like setter
                        Optional<String> setterPrefix = matchesPrefix(mName, buildParameters.getSetterPrefixes());

                        if (mType == PropertyObject.MethodType.NONE
                                && setterPrefix.isPresent()
                                && !isIgnored(m)) {
                            // is a setterRef?
                            String propName = PropertyObject.Builder.removePrefix(mName, setterPrefix.get());
                            checkState(!propName.isEmpty(), "Found invalid setter method %s", m);

                            // Now check that the parameters are correct
                            List<Parameter> params = m.getParameters();
                            if (params.size() != 1) {
                                // found a method with more than one parameter or no parameters that starts with
                                // setterPrefix
                                logger.trace("Found setter method with invalid signature: " + m);
                            } else {
                                handleMethod(PropertyObject.MethodType.SETTER, propName, m);

                                if (!propertyTypes.containsKey(propName)) {
                                    // getters have a possible return type
                                    propertyTypes.put(propName, MoreReflection.unwrapPropertyType(params.get(0).getType()));
                                }
                            }
                        }
                    });

            logger.traceExit();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || TypePropertiesCollector.class != o.getClass()) {
                return false;
            }

            @SuppressWarnings("unchecked")
            TypePropertiesCollector that = (TypePropertiesCollector) o;
            return Objects.equals(base, that.getBase());
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private void buildHierarchyRec(Deque<TypePropertiesCollector> previous,
                                   Set<TypePropertiesCollector> visited,
                                   @Nullable TypePropertiesCollector current) {
        if (current == null
                || visited.contains(current)
                || isTypeBadPredicate.test(current.getBase().getRawType())) {
            return;
        }

        previous.push(current);

        TypeToken<?> base = current.getBase();
        Class<?> currentClazz = current.getBase().getRawType();

        // Visit all of the interfaces first to make sure they're loaded before the subclass
        Arrays.stream(currentClazz.getInterfaces())
                .filter(isTypeBadPredicate.negate())
                .map(i -> (TypeToken<?>) base.resolveType(i))
                .forEach(in -> buildHierarchyRec(previous, visited, allCollectors.get(in.getRawType())));

        // now the super classes!
        if (currentClazz.getSuperclass() != null) {
            buildHierarchyRec(previous, visited,
                    allCollectors.get(currentClazz.getSuperclass()));
        }
    }

    /**
     * Provides a breadth-first component to build up the tree of all of the {@link PropertyObject} found
     * in the {@link #base} type's hierarchy.
     *
     * @param cache Known {@link PropertyObject} instances to save lookup times
     * @return Mapping of classes to their associated {@link PropertyObject} value
     */
    ImmutableMap<Class<?>, PropertyObject<?>> build(
            ImmutableMap<Class<?>, PropertyObject<?>> cache) {
        logger.traceEntry("Builder.RealBuilder#build(cache: {} entries)", cache.size());

        // Utilize the cache by adding all the known entites to the #allCollectors field
        cache.forEach((type, propertyObject) ->
                allCollectors.put(type, new TypePropertiesCollector(propertyObject)));

        // Do a breadth-first search from the bottom of the class hierarchy
        // and add fields/methods as they're found. If a type has interfaces, we
        // check those, too!

        // "visit" action is done by creating a new TypePropertiesCollector to visit the class found.
        Deque<Class<?>> q = new ArrayDeque<>();

        q.addLast(base.getRawType());
        visited.add(Object.class); // do this so it never tries to keep going higher

        // Mark all of the known classes as visited
        visited.addAll(allCollectors.keySet());

        // Do a DFS via the TypePropertiesCollector#collect method
        while (!q.isEmpty()) {
            final Class<?> currentClazz = q.pop();
            final TypeToken<?> current = base.resolveType(currentClazz);

            if (isTypeBadPredicate.test(currentClazz)
                    || visited.contains(currentClazz)) {
                continue;
            }

            TypePropertiesCollector c = new TypePropertiesCollector(current);
            c.collect(q);
            allCollectors.put(currentClazz, c);
        }

        logger.trace("Finished breadth-first search to find all fields/methods");

        throwIfPropertyErrors();

        // Do a DFS to build up all of the Collectors
        Deque<TypePropertiesCollector> dfsPath = new ArrayDeque<>();
        buildHierarchyRec(dfsPath, new HashSet<>(), allCollectors.get(base.getRawType()));

        Map<Class<?>, PropertyObject<?>> builtObjects = new HashMap<>();
        while (!dfsPath.isEmpty()) {
            TypePropertiesCollector current = dfsPath.pop();

            TypeToken<?> type = current.getBase();

            // Get the super TypePropertiesCollectors
            List<? extends PropertyObject<?>> supers =
                    Stream.concat(Stream.of(type.getRawType().getSuperclass()),
                            Arrays.stream(type.getRawType().getInterfaces()))
                            .filter(Objects::nonNull)
                            .filter(isTypeBadPredicate.negate())
                            .map(t -> {
                                PropertyObject<?> po = builtObjects.get(t);
                                checkState(po != null,
                                        "PropertyObject not loaded before super requested, " +
                                                "super: %s, base: %s",
                                        t, type);

                                return po;
                            }).collect(Collectors.toList());

            @SuppressWarnings("unchecked") // I hate this, but its required :(
                    PropertyObject<?> po = (PropertyObject<?>) new PropertyObject(type,
                    supers,
                    current.ignoredProperties,
                    current.propertyTypes,
                    current.fieldsMap,
                    current.methodsMap);

            builtObjects.put(type.getRawType(), po);
        }


        // Now the maps have all been filled, we pass the values to the PropertyObject constructor to
        // let it decide how to format them.
        return logger.traceExit(ImmutableMap.copyOf(builtObjects));
    }

    /**
     * Adds an error message for the {@code propertyName}
     *
     * @param propertyName Name of the current property
     * @param message      Message to save
     */
    private void propertyError(String propertyName, String message) {
        propErrorsMap.put(propertyName, message);
        logger.error("[P: {}] {}", propertyName, message);
    }

    /**
     * Throws an {@link IllegalStateException} if there is any errors currently in the builder
     */
    private void throwIfPropertyErrors() throws IllegalStateException {
        if (!propErrorsMap.isEmpty()) {
            // found some errors :(
            StringBuilder bld = new StringBuilder();
            bld.append("Found property errors with class: ");
            bld.append(base.getRawType().getName());
            bld.append("\n");

            int index = 1;
            for (Map.Entry<String, Collection<String>> e : propErrorsMap.asMap().entrySet()) {
                bld.append('\t')
                        .append(index)
                        .append(")\t")
                        .append(e.getKey())
                        .append(" ->");

                boolean first = true;
                for (String error : e.getValue()) {
                    if (!first) {
                        bld.append("\t\t");
                    }

                    bld.append(error);
                    bld.append('\n');

                    first = false;
                }
            }

            throw new IllegalArgumentException(bld.toString());
        }
    }

}
