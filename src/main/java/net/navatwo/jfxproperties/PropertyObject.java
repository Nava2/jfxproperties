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

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.*;
import com.google.common.primitives.Primitives;
import com.google.common.reflect.Invokable;
import com.google.common.reflect.TypeToken;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyProperty;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import jdk.nashorn.internal.ir.annotations.Immutable;
import net.navatwo.jfxproperties.util.PropertyAcceptor;
import net.navatwo.jfxproperties.util.TypeAcceptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.reflect.Field;
import java.util.*;

import static com.google.common.base.Preconditions.*;

/**
 * Gathers all of the properties on a {@link Class} hierarchy except those ignored by {@link IgnoreProperty} and
 * allows for access via {@link PropertyInfo} structures to read and modify the content of the properties.
 *
 * @see IgnoreProperty
 */
@ParametersAreNonnullByDefault
@Immutable
public class PropertyObject<T> {

    /**
     * Base class type
     */
    private final TypeToken<T> base;

    /**
     * Used for {@link #hashCode()} based off of the {@link #base} field.
     */
    private final int hashCode;

    private final Collection<? extends PropertyObject<? super T>> supers;

    private final ImmutableSortedSet<String> ignoredProperties;
    private final ImmutableSortedSet<String> allNames;
    private final ImmutableMap<String, ? extends PropertyInfo<?>> allProperties;


    private final ImmutableSortedSet<String> thisNames;
    private final ImmutableMap<String, ? extends PropertyInfo<?>> localProperties;

    /**
     * Initialize a {@link PropertyObject}.
     * <br/>
     * <p>
     * This should only be called from {@link Builder}.
     *
     * @param fields  Fields found in the hierarchy
     * @param methods Methods found in the hierarchy
     */
    @SuppressWarnings("unchecked")
    PropertyObject(TypeToken<T> base,
                   Collection<? extends PropertyObject<? super T>> supers,
                   Set<String> ignoredProperties,
                   Map<String, TypeToken<?>> types,
                   Map<String, Field> fields,
                   Map<String, EnumMap<MethodType, Invokable<T, ?>>> methods) {
        this.base = checkNotNull(base, "base == null");
        this.hashCode = base.hashCode();
        this.supers = checkNotNull(supers, "supers == null");

        // Collect all of the properties from the immediate super collectors, these will have been initialized with
        // their super ignored properties as well.
        ImmutableSortedSet.Builder<String> ignoredPropertiesBuilder = ImmutableSortedSet.naturalOrder();
        ignoredPropertiesBuilder.addAll(ignoredProperties);
        supers.stream()
                .flatMap(s -> s.getIgnoredProperties().stream())
                .forEach(ignoredPropertiesBuilder::add);
        this.ignoredProperties = ignoredPropertiesBuilder.build();


        // now we need to go through and create a mapping of property names to the accessing methods/fields
        // do a union on the keys this gives us all the property names that have been found
        ImmutableMap.Builder<String, PropertyInfo<?>> propertyMapBuilder = ImmutableMap.builder();
        Sets.union(methods.keySet(), fields.keySet()).stream()
                .filter(propName -> !this.ignoredProperties.contains(propName))
                .forEach(propName -> {
                    // Now build the appropriate PropertyInfo<> implementation dependant on the type of the Field.
                    // We can use the primitive versions when it will be faster for them to execute later.

                    TypeToken<?> propType = types.get(propName).unwrap();

                    EnumMap<MethodType, Invokable<T, ?>> mmap = methods.getOrDefault(propName, new EnumMap<>(MethodType.class));

                    Field field = fields.get(propName);
                    Invokable<T, ? extends ReadOnlyProperty<?>> accessor = (Invokable<T, ? extends ReadOnlyProperty<?>>) mmap.get(MethodType.ACCESSOR);
                    Invokable<T, ?> getter = mmap.get(MethodType.GETTER);
                    Invokable<T, Void> setter = (Invokable<T, Void>) mmap.get(MethodType.SETTER);

                    if (getter != null || setter != null || accessor != null) {

                        PropertyInfoExtractor piExtract = new PropertyInfoExtractor(propName, base, propType,
                                field, getter, setter, accessor);
                        TypeAcceptor.accept(propType, piExtract);

                        propertyMapBuilder.put(propName, piExtract.getPropertyInfo());
                    }
                });

        this.localProperties = propertyMapBuilder.build();
        this.thisNames = ImmutableSortedSet.copyOf(localProperties.keySet());

        supers.stream()
                .flatMap(s -> s.getProperties().entrySet().stream())
                .filter(e -> !this.thisNames.contains(e.getKey()))
                .filter(e -> !this.ignoredProperties.contains(e.getKey()))
                .forEach(e -> propertyMapBuilder.put(e.getKey(), e.getValue()));

        // Now create the immutable structures required and store them.
        allProperties = propertyMapBuilder.build();
        allNames = ImmutableSortedSet.copyOf(allProperties.keySet());
    }

    /**
     * Get the base class for this {@code PropertyObject}.
     *
     * @return Base class
     */
    public Class<T> getBaseClass() {
        return (Class<T>) base.getRawType();
    }


    /**
     * Get the base class for this {@code PropertyObject}.
     *
     * @return Base class
     */
    public TypeToken<T> getBaseType() {
        return base;
    }


    /**
     * Gets a sorted, immutable set of names of properties
     *
     * @return Non-{@code null}, immutable set of property names
     */
    public ImmutableSortedSet<String> getAllNames() {
        return allNames;
    }

    /**
     * Gets a {@link PropertyInfo} instance for the property {@code name}. This allows one to manipulate the
     * property value.
     *
     * @param name         Property name
     * @param propertyType Type of the property
     * @param <V>          Type of the property
     * @return Manipulation for property
     * @throws IllegalArgumentException if the property does not exist or the type expected is not a valid super class
     *                                  of the property's actual type.
     */
    @SuppressWarnings("unchecked")
    public <V> PropertyInfo<V> getProperty(String name, Class<V> propertyType) {
        final PropertyInfo<?> info = allProperties.get(name);
        checkArgument(info != null,
                "Property %s does not exist on type %s", name, base);
        checkArgument(info.getPropertyType().isSubtypeOf(propertyType),
                "propertyType %s is not assignable from property %s, which is %s",
                propertyType, name, info.getPropertyClass());

        return (PropertyInfo<V>) info;
    }

    /**
     * Gets an {@link IntegerPropertyInfo} instance for the property {@code name}. This allows one to manipulate the
     * property value.
     *
     * @param name Property name
     * @return Manipulation for property
     * @throws IllegalArgumentException if the property does not exist or the type expected is not a valid super class
     *                                  of the property's actual type.
     */
    @SuppressWarnings("unchecked")
    public IntegerPropertyInfo getIntProperty(String name) {

        final PropertyInfo<?> info = allProperties.get(name);
        checkArgument(info != null,
                "Property %s does not exist on type %s", name, base);
        checkState(Primitives.unwrap(info.getPropertyClass()) == int.class,
                "Property %s is not an int, it is %s", name, info.getPropertyClass());

        return (IntegerPropertyInfo) info;
    }

    /**
     * Gets an {@link LongPropertyInfo} instance for the property {@code name}. This allows one to manipulate the
     * property value.
     *
     * @param name Property name
     * @return Manipulation for property
     * @throws IllegalArgumentException if the property does not exist or the type expected is not a valid super class
     *                                  of the property's actual type.
     */
    @SuppressWarnings("unchecked")
    public LongPropertyInfo getLongProperty(String name) {
        final PropertyInfo<?> info = allProperties.get(name);
        checkArgument(info != null,
                "Property %s does not exist on type %s", name, base);
        checkState(Primitives.unwrap(info.getPropertyClass()) == long.class,
                "Property %s is not a long, it is %s", name, info.getPropertyClass());

        return (LongPropertyInfo) info;
    }

    /**
     * Gets an {@link DoublePropertyInfo} instance for the property {@code name}. This allows one to manipulate the
     * property value.
     *
     * @param name Property name
     * @return Manipulation for property
     * @throws IllegalArgumentException if the property does not exist or the type expected is not a valid super class
     *                                  of the property's actual type.
     */
    @SuppressWarnings("unchecked")
    public DoublePropertyInfo getDoubleProperty(String name) {
        final PropertyInfo<?> info = allProperties.get(name);
        checkArgument(info != null,
                "Property %s does not exist on type %s", name, base);
        checkState(Primitives.unwrap(info.getPropertyClass()) == double.class,
                "Property %s is not a double, it is %s", name, info.getPropertyClass());

        return (DoublePropertyInfo) info;
    }

    /**
     * Gets an {@link ListPropertyInfo} instance for the property {@code name}. This allows one to manipulate the
     * property value.
     *
     * @param name Property name
     * @return Manipulation for property
     * @throws IllegalArgumentException if the property does not exist or the type expected is not a valid super class
     *                                  of the property's actual type.
     */
    @SuppressWarnings("unchecked")
    public <V> ListPropertyInfo<V> getListProperty(String name, TypeToken<V> type) {
        final PropertyInfo<?> info = allProperties.get(name);
        checkArgument(info != null,
                "Property %s does not exist on type %s", name, base);
        checkState(info.getPropertyType().isSubtypeOf(ObservableList.class),
                "Property %s is not a List property, it is %s", name, info.getPropertyClass());

        return (ListPropertyInfo<V>) info;
    }

    /**
     * Gets an {@link ListPropertyInfo} instance for the property {@code name}. This allows one to manipulate the
     * property value.
     * <br/>
     * Delegate to {@link #getListProperty(String, TypeToken)}.
     *
     * @param name Property name
     * @return Manipulation for property
     * @throws IllegalArgumentException if the property does not exist or the type expected is not a valid super class
     *                                  of the property's actual type.
     * @see #getListProperty(String, TypeToken)
     */
    @SuppressWarnings("unchecked")
    public <V> ListPropertyInfo<V> getListProperty(String name, Class<V> type) {
        return getListProperty(name, TypeToken.of(type));
    }

    /**
     * Gets an {@link SetPropertyInfo} instance for the property {@code name}. This allows one to manipulate the
     * property value.
     *
     * @param name Property name
     * @return Manipulation for property
     * @throws IllegalArgumentException if the property does not exist or the type expected is not a valid super class
     *                                  of the property's actual type.
     */
    @SuppressWarnings("unchecked")
    public <V> SetPropertyInfo<V> getSetProperty(String name, TypeToken<V> type) {
        final PropertyInfo<?> info = allProperties.get(name);
        checkArgument(info != null,
                "Property %s does not exist on type %s", name, base);
        checkState(info.getPropertyType().isSubtypeOf(ObservableSet.class),
                "Property %s is not a Set property, it is %s", name, info.getPropertyClass());

        final SetPropertyInfo<V> setInfo = (SetPropertyInfo<V>) info;

        checkState(setInfo.getElementType().isSubtypeOf(type),
                "Invalid element type, actual {} property type is {}", name, info.getPropertyType());

        return setInfo;
    }

    public PropertyInfo<?> getProperty(String name, PropertyAcceptor acceptor) {
        checkNotNull(acceptor, "acceptor == null");

        PropertyInfo<?> info = allProperties.get(name);
        checkArgument(info != null, "Property %s does not exist on type %s", name, base);

        PropertyAcceptor.accept(info, acceptor);

        return info;
    }

    /**
     * Gets a mapping of all {@link PropertyInfo} values keyed by {@link PropertyInfo#getName()}.
     *
     * @return Non-{@code null}, possibly empty set of properties
     */
    public ImmutableMap<String, ? extends PropertyInfo<?>> getProperties() {
        return allProperties;
    }

    /**
     * Gets a mapping of all {@link PropertyInfo} values keyed by {@link PropertyInfo#getName()}.
     *
     * @return Non-{@code null}, possibly empty set of properties
     */
    public ImmutableMap<String, ? extends PropertyInfo<?>> getLocalProperties() {
        return localProperties;
    }

    /**
     * Gets the {@link PropertyInfo#getName() names} of the properties that were found but ignored.
     *
     * @return Non-{@code null}, but possibly empty set of names
     */
    public ImmutableSortedSet<String> getIgnoredProperties() {
        return ignoredProperties;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PropertyObject)) {
            return false;
        }
        final PropertyObject<?> that = (PropertyObject<?>) o;
        return Objects.equal(base, that.base);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .addValue(base)
                .toString();
    }

    /**
     * Defines the different method types known for building Property objects
     */
    enum MethodType {
        /**
         * Method that returns an instance of the value of a property
         */
        GETTER,

        /**
         * Method that sets the instance of the value of a property
         */
        SETTER,

        /**
         * Accesses a property directly
         */
        ACCESSOR,

        /**
         * Unknown method found
         */
        NONE
    }


    /**
     * Creates a new {@link Builder} instance via the convenience method.
     *
     * @return New instance
     * @see Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Implements a Builder for creating {@link PropertyObject} values. This class allows for adjusting
     * how fields/methods are reflected.
     */
    @SuppressWarnings("WeakerAccess")
    public static class Builder {

        private static final Logger logger = LogManager.getLogger(Builder.class);

        private final ImmutableSet<String> fieldPrefixes;
        static final ImmutableSet<String> DEFAULT_FIELD_PREFIXES = ImmutableSet.of("");

        private final ImmutableSet<String> propertySuffixes;
        static final ImmutableSet<String> DEFAULT_PROPERTY_SUFFIXES = ImmutableSet.of("Property");

        private final ImmutableSet<String> getterPrefixes;
        static final ImmutableSet<String> DEFAULT_GETTER_PREFIXES = ImmutableSet.of("get", "is");

        private final ImmutableSet<String> setterPrefixes;
        static final ImmutableSet<String> DEFAULT_SETTER_PREFIXES = ImmutableSet.of("set");

        private final ImmutableMap.Builder<TypeToken<?>, PropertyObject<?>> cacheBuilder;

        Builder() {
            this(Collections.emptyMap(),
                    // string info
                    ImmutableSet.of(), ImmutableSet.of(),
                    ImmutableSet.of(), ImmutableSet.of());
        }

        Builder(Map<TypeToken<?>, PropertyObject<?>> cache,
                Set<String> fieldPrefixes,
                Set<String> propertySuffixes,
                Set<String> getterPrefixes,
                Set<String> setterPrefixes) {
            this.cacheBuilder = ImmutableMap.builder();
            cacheBuilder.putAll(checkNotNull(cache, "cache == null"));

            // handle default prefix/suffixes

            if (propertySuffixes.isEmpty()) {
                this.propertySuffixes = DEFAULT_PROPERTY_SUFFIXES;
            } else {
                this.propertySuffixes = ImmutableSet.copyOf(propertySuffixes);
            }

            if (fieldPrefixes.isEmpty()) {
                this.fieldPrefixes = DEFAULT_FIELD_PREFIXES;
            } else {
                this.fieldPrefixes = ImmutableSet.copyOf(fieldPrefixes);
            }

            if (getterPrefixes.isEmpty()) {
                this.getterPrefixes = DEFAULT_GETTER_PREFIXES;
            } else {
                this.getterPrefixes = ImmutableSet.copyOf(getterPrefixes);
            }

            if (setterPrefixes.isEmpty()) {
                this.setterPrefixes = DEFAULT_SETTER_PREFIXES;
            } else {
                this.setterPrefixes = ImmutableSet.copyOf(setterPrefixes);
            }

        }

        /**
         * Using the stored values, this traverses the class hierarchy collecting and filtering
         * "properties."
         *
         * @return New {@code PropertyObject} instance
         */
        public <T> ImmutableMap<Class<?>, PropertyObject<?>> buildAll(TypeToken<T> type) {
            return buildAll(type, ImmutableMap.of());
        }


        /**
         * Using the stored values, this traverses the class hierarchy collecting and filtering
         * "properties."
         *
         * @param type  The base of the collection
         * @param cache Known classes used to speed up collection
         * @return New {@code PropertyObject} instance
         */
        public <T>
        ImmutableMap<Class<?>, PropertyObject<?>>
        buildAll(TypeToken<T> type, ImmutableMap<Class<?>, PropertyObject<?>> cache) {
            logger.traceEntry("PropertyObject.Builder#build(type: {}, cache: {} entries)", type, cache.size());

            return logger.traceExit(new RealPropertyObjectBuilder<>(type, this).build(cache));
        }

        /**
         * Using the stored values, this traverses the class hierarchy collecting and filtering
         * "properties."
         *
         * @return New {@code PropertyObject} instance
         */
        public <T> PropertyObject<T> build(TypeToken<T> type) {
            logger.traceEntry("PropertyObject.Builder#build({})", type);

            Map<Class<?>, PropertyObject<?>> instanceMap = buildAll(type);
            @SuppressWarnings("unchecked")
            PropertyObject<T> obj = (PropertyObject<T>) instanceMap.get(type.getRawType());
            checkState(obj != null,
                    "Could get PropertyObject for base type {}", type);
            return logger.traceExit(obj);
        }

        /**
         * Using the stored values, this traverses the class hierarchy collecting and filtering
         * "properties."
         *
         * @return New {@code PropertyObject} instance
         */
        public <T> PropertyObject<T> build(Class<T> clazz) {
            logger.traceEntry("PropertyObject.Builder#build({})", clazz);

            return logger.traceExit(build(TypeToken.of(clazz)));
        }

        /**
         * Removes the prefix string from the string if it is present.
         *
         * @param value  String modified
         * @param prefix Prefix removed
         * @return String with the prefix removed, or the original string
         */
        static String removePrefix(String value, String prefix) {
            if (!prefix.isEmpty()
                    && value.startsWith(prefix)
                    && prefix.length() != value.length()) { // method called "get" for example...
                return Character.toLowerCase(value.charAt(prefix.length())) + value.substring(prefix.length() + 1);
            } else {
                return value;
            }
        }

        /**
         * Checks if the passed value matches the prefix.
         *
         * @param value  Value to check
         * @param prefix Prefix removed
         * @return {@code suffix} if a prefix is found, {@link Optional#empty()} if not
         * @see #matchesPrefix(String, Iterable)
         */
        static Optional<String> matchesPrefix(String value, String... prefix) {
            return Arrays.stream(prefix)
                    .filter(pre -> value.length() > pre.length())
                    .filter(value::startsWith)
                    .findFirst();
        }

        /**
         * Checks if the passed value matches the prefix.
         *
         * @param value    Value to check
         * @param prefixes Prefix values to check
         * @return {@code suffix} if a prefix is found, {@link Optional#empty()} if not
         */
        static Optional<String> matchesPrefix(String value, Iterable<String> prefixes) {
            return Streams.stream(prefixes)
                    .filter(pre -> value.length() > pre.length())
                    .filter(value::startsWith)
                    .findFirst();
        }

        /**
         * Removes the suffix string from the string if it is present.
         *
         * @param value  String modified
         * @param suffix Suffix removed
         * @return String with the suffix removed, or the original string
         */
        static String removeSuffix(String value, String suffix) {
            if (!value.isEmpty()
                    && value.endsWith(suffix)
                    && suffix.length() != value.length()) {
                return value.substring(0, value.length() - suffix.length());
            }

            return value;
        }

        /**
         * Checks if the passed value matches the prefix.
         *
         * @param value  Value to check
         * @param suffix Suffix searched for
         * @return {@code suffix} if a suffix is found, {@link Optional#empty()} if not
         * @see #matchesSuffix(String, Iterable)
         */
        static Optional<String> matchesSuffix(String value, String... suffix) {
            return matchesSuffix(value, ImmutableList.copyOf(suffix));
        }

        /**
         * Checks if the passed value matches the prefix.
         *
         * @param value    Value to check
         * @param suffixes Suffixes to search for
         * @return {@code suffix} if a suffix is found, {@link Optional#empty()} if not
         */
        static Optional<String> matchesSuffix(String value, Iterable<String> suffixes) {
            return Streams.stream(suffixes)
                    .filter(suff -> value.length() > suff.length())
                    .filter(value::endsWith)
                    .findFirst();
        }


        /**
         * Get the prefix used to discern if something is a property cared about.
         * <br/>
         * Defaults to {@link #DEFAULT_FIELD_PREFIXES}
         *
         * @return Prefix searched for
         */
        ImmutableSet<String> getFieldPrefixes() {
            return fieldPrefixes;
        }

        /**
         * Sets the {@link #getFieldPrefixes() field prefix} to the passed value.
         *
         * @param fieldPrefixes New value for field prefix
         * @return New {@link Builder} instance
         * @see #getFieldPrefixes()
         * @see #setFieldPrefixes(Iterable)
         */
        public Builder setFieldPrefixes(final String... fieldPrefixes) {
            return setFieldPrefixes(ImmutableSet.copyOf(fieldPrefixes));
        }

        /**
         * Sets the {@link #getFieldPrefixes() field prefix} to the passed value.
         *
         * @param fieldPrefixes New value for field prefix
         * @return New {@link Builder} instance
         * @see #getFieldPrefixes()
         * @see #setFieldPrefixes(String[])
         */
        public Builder setFieldPrefixes(final Iterable<String> fieldPrefixes) {
            return new Builder(cacheBuilder.build(),
                    ImmutableSet.copyOf(fieldPrefixes), propertySuffixes,
                    getterPrefixes, setterPrefixes);
        }

        ImmutableSet<String> getPropertySuffixes() {
            return propertySuffixes;
        }

        /**
         * Sets the {@link #getPropertySuffixes() property suffix} to the passed value.
         *
         * @param propertySuffixes New value for property suffix
         * @return New {@link Builder} instance
         * @see #getPropertySuffixes()
         * @see #setPropertySuffixes(Iterable)
         */
        public Builder setPropertySuffixes(final String... propertySuffixes) {
            return setPropertySuffixes(ImmutableSet.copyOf(propertySuffixes));
        }

        /**
         * Sets the {@link #getPropertySuffixes() property suffix} to the passed value.
         *
         * @param propertySuffixes New value for property suffix
         * @return New {@link Builder} instance
         * @see #getPropertySuffixes()
         * @see #setPropertySuffixes(String...)
         */
        public Builder setPropertySuffixes(final Iterable<String> propertySuffixes) {
            return new Builder(cacheBuilder.build(),
                    fieldPrefixes, ImmutableSet.copyOf(propertySuffixes),
                    getterPrefixes, setterPrefixes);
        }

        /**
         * The prefix used when deciding if a method is a "getter".
         * <br/>
         * Defaults to: {@link #DEFAULT_GETTER_PREFIXES}
         *
         * @return Current getter prefix
         */
        ImmutableSet<String> getGetterPrefixes() {
            return getterPrefixes;
        }

        /**
         * Sets the {@link #getGetterPrefixes() getter prefix} to the passed value.
         *
         * @param getterPrefixes New value for getter prefix
         * @return New {@link Builder} instance
         * @see #getGetterPrefixes()
         */
        public Builder setGetterPrefixes(final String... getterPrefixes) {
            return setGetterPrefixes(ImmutableSet.copyOf(getterPrefixes));
        }

        /**
         * Sets the {@link #getGetterPrefixes() getter prefix} to the passed value.
         *
         * @param getterPrefixes New value for getter prefix
         * @return New {@link Builder} instance
         * @see #getGetterPrefixes()
         */
        public Builder setGetterPrefixes(final Iterable<String> getterPrefixes) {
            return new Builder(cacheBuilder.build(),
                    fieldPrefixes, propertySuffixes,
                    ImmutableSet.copyOf(getterPrefixes), setterPrefixes);
        }

        /**
         * The prefix used when deciding if a method is a "setter".
         * <br/>
         * Defaults to: {@link #DEFAULT_SETTER_PREFIXES}
         *
         * @return Current setter prefix
         */
        ImmutableSet<String> getSetterPrefixes() {
            return setterPrefixes;
        }

        /**
         * Sets the {@link #getGetterPrefixes() setter prefix} to the passed value.
         *
         * @param setterPrefixes New value for setter prefix
         * @return New {@link Builder} instance
         * @see #getSetterPrefixes()
         */
        public Builder setSetterPrefixes(final String... setterPrefixes) {
            return setSetterPrefixes(ImmutableSet.copyOf(setterPrefixes));
        }

        /**
         * Sets the {@link #getGetterPrefixes() setter prefix} to the passed value.
         *
         * @param setterPrefixes New value for setter prefix
         * @return New {@link Builder} instance
         * @see #getSetterPrefixes()
         */
        public Builder setSetterPrefixes(final Iterable<String> setterPrefixes) {
            return new Builder(cacheBuilder.build(),
                    fieldPrefixes, propertySuffixes,
                    getterPrefixes, ImmutableSet.copyOf(setterPrefixes));
        }

        /**
         * Adds all of the entries in the Map into the current cache entries.
         *
         * @param entries Entries to add into the known cache
         * @return <strong>Original instance, {@code this}.</strong>
         */
        public Builder withCacheEntries(final Map<TypeToken<?>, PropertyObject<?>> entries) {
            cacheBuilder.putAll(entries);
            return this;
        }
    }

    /**
     * Uses the type information to discern and pull out what type of {@link PropertyInfo} should be used as the
     * base for the state passed. This is stored in the {@link #getPropertyInfo()} return result
     */
    @SuppressWarnings("unchecked")
    private class PropertyInfoExtractor implements TypeAcceptor {
        private final TypeToken<T> base;
        private final String propName;
        private final Field field;
        private final Invokable<T, ?> getter;
        private final Invokable<T, Void> setter;
        private final Invokable<T, ? extends ReadOnlyProperty<?>> accessor;
        private final TypeToken<?> propType;

        private PropertyInfo<?> pi;

        PropertyInfoExtractor(String propName,
                              TypeToken<T> base,
                              TypeToken<?> propType,
                              @Nullable Field field,
                              @Nullable Invokable<T, ?> getter,
                              @Nullable Invokable<T, Void> setter,
                              @Nullable Invokable<T, ? extends ReadOnlyProperty<?>> accessor) {
            this.base = base;
            this.propName = propName;
            this.field = field;
            this.getter = getter;
            this.setter = setter;
            this.accessor = accessor;
            this.propType = propType;

            pi = null;
        }

        @Override
        public void acceptInt() {
            pi = new IntegerPropertyInfo(base, propName,
                    field,
                    (Invokable<Object, Integer>) getter,
                    (Invokable<Object, Void>) setter,
                    (Invokable<Object, ? extends ReadOnlyIntegerProperty>) accessor);
        }

        @Override
        public void acceptLong() {
            pi = new LongPropertyInfo(base, propName,
                    field,
                    (Invokable<Object, Long>) getter,
                    (Invokable<Object, Void>) setter,
                    (Invokable<Object, ? extends ReadOnlyLongProperty>) accessor);
        }

        @Override
        public void acceptDouble() {
            pi = new DoublePropertyInfo(base, propName,
                    field,
                    (Invokable<Object, Double>) getter,
                    (Invokable<Object, Void>) setter,
                    (Invokable<Object, ? extends ReadOnlyDoubleProperty>) accessor);
        }

        @Override
        public void acceptObject(TypeToken<?> type) {
            pi = new ObjectPropertyInfo<>(base, propName,
                    (TypeToken<Object>) propType,
                    field,
                    (Invokable<Object, Object>) getter,
                    (Invokable<Object, Void>) setter,
                    (Invokable<Object, ? extends ReadOnlyProperty<Object>>) accessor);
        }

        @Override
        public void acceptList(TypeToken<?> elementType) {
            pi = new ListPropertyInfo<>(base, propName,
                    (TypeToken<List<Object>>) propType,
                    (TypeToken<Object>) elementType,
                    field,
                    (Invokable<Object, List<Object>>) getter,
                    (Invokable<Object, Void>) setter,
                    (Invokable<Object, ? extends ReadOnlyProperty<List<Object>>>) accessor);
        }

        @Override
        public void acceptSet(TypeToken<?> elementType) {
            pi = new SetPropertyInfo<>(base, propName,
                    (TypeToken<Set<Object>>) propType,
                    (TypeToken<Object>) elementType,
                    field,
                    (Invokable<Object, Set<Object>>) getter,
                    (Invokable<Object, Void>) setter,
                    (Invokable<Object, ? extends ReadOnlyProperty<Set<Object>>>) accessor);
        }

        @Override
        public void acceptMap(TypeToken<?> keyType, TypeToken<?> valueType) {
            pi = new MapPropertyInfo<>(base, propName,
                    (TypeToken<Map<Object, Object>>) propType,
                    (TypeToken<Object>) keyType,
                    (TypeToken<Object>) valueType,
                    field,
                    (Invokable<Object, Map<Object, Object>>) getter,
                    (Invokable<Object, Void>) setter,
                    (Invokable<Object, ? extends ReadOnlyProperty<Map<Object, Object>>>) accessor);
        }

        /**
         * Gets the underlying {@link PropertyInfo} that was found.
         *
         * @return Found property info
         * @throws IllegalStateException if this has not been used with
         *                               {@link TypeAcceptor#accept(TypeToken, TypeAcceptor)} yet
         */
        PropertyInfo<?> getPropertyInfo() {
            checkState(pi != null,
                    "Must use this type with TypeAcceptor#accept() before calling this method");
            return pi;
        }
    }
}
