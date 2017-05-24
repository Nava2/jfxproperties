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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.primitives.Primitives;
import com.google.common.reflect.Invokable;
import com.google.common.reflect.TypeToken;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyProperty;
import net.navatwo.jfxproperties.util.Gullectors;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static net.navatwo.jfxproperties.MoreReflection.isProperty;

/**
 * Abstract class to simplify implementations of the {@link PropertyInfo} type.
 */
abstract class PropertyInfoBase<V> implements PropertyInfo<V> {

    private final TypeToken<?> base;
    private final String name;
    private final Field fieldRef;
    private final Invokable<Object, ? extends V> getterRef;
    private final Invokable<Object, Void> setterRef;
    private final Invokable<Object, ? extends ReadOnlyProperty<V>> accessorRef;
    private final ImmutableSet<PropertyInfo.Mutability> mutability;
    private final ImmutableList<Annotation> annotations;
    private final ImmutableSet<Class<? extends Annotation>> annotationClasses;

    PropertyInfoBase(TypeToken<?> base,
                     String name,
                     @Nullable Field field,
                     @Nullable Invokable<Object, ? extends V> getterRef,
                     @Nullable Invokable<Object, Void> setterRef,
                     @Nullable Invokable<Object, ? extends ReadOnlyProperty<V>> accessorRef) {
        this.base = checkNotNull(base, "base == null");

        this.fieldRef = field;
        this.getterRef = getterRef;
        this.setterRef = setterRef;
        this.accessorRef = accessorRef;

        this.name = checkNotNull(name, "name == null");

        checkBase(field, getterRef, setterRef, accessorRef);

        this.annotations = Stream.of(field, getterRef, setterRef, accessorRef)
                .filter(Objects::nonNull)
                .map(AnnotatedElement::getAnnotations)
                .flatMap(Arrays::stream)
                .collect(Gullectors.toImmutableList());
        this.annotationClasses = annotations.stream()
                .map(Object::getClass)
                .map(c -> (Class<? extends Annotation>) c)
                .collect(Gullectors.toImmutableSet());

        EnumSet<Mutability> mutability = EnumSet.noneOf(Mutability.class);

        if (accessorRef != null) {
            // now we can check if it's read-write based on the type of the property
            TypeToken<?> rType = accessorRef.getReturnType();
            mutability.add(Mutability.Read);

            if (isProperty(rType.getRawType())) {
                mutability.add(Mutability.Write);
            }
        }

        if (getterRef != null) {
            mutability.add(Mutability.Read);
        }

        if (setterRef != null) {
            mutability.add(Mutability.Write);
        }

        this.mutability = Sets.immutableEnumSet(mutability);
    }

    private void checkBase(Member... members) {
        Class<?> baseClass = this.base.getRawType();
        for (Member m : members) {
            if (m != null) {
                checkArgument(m.getDeclaringClass().isAssignableFrom(baseClass),
                        "Invalid member passed (%s) for base (%s)", m, baseClass);
            }
        }
    }

    public TypeToken<?> getBaseType() {
        return this.base;
    }

    @Override
    public ImmutableList<? extends Annotation> getAnnotationsPrsent() {
        return annotations;
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return annotationClasses.contains(annotationClass);
    }

    /**
     * Used to ease separate the calculation of what property to use when.
     */
    enum FunctionLocation {
        Field,
        Getter,
        Setter,
        Accessor,
        None;

        static FunctionLocation calculateGetter(PropertyInfoBase<?> from) {
            Set<Mutability> mutability = from.getMutability();

            if (mutability.contains(Mutability.Read)) {
                if (from.getGetterRef().isPresent()) {
                    return Getter;
                } else if (from.getAccessorRef().isPresent()) {
                    return Accessor;
                } // else None
            }

            return None;
        }

        static FunctionLocation calculateSetter(PropertyInfoBase<?> from) {
            Set<Mutability> mutability = from.getMutability();

            if (mutability.contains(Mutability.Write)) {
                if (from.getSetterRef().isPresent()) {
                    return Setter;
                } else if (from.getAccessorRef().isPresent()) {
                    return Accessor;
                } // else None
            }

            return None;
        }
    }

    @Override
    public ImmutableSet<PropertyInfo.Mutability> getMutability() {
        return mutability;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Class<?> getBaseClass() {
        return this.base.getRawType();
    }

    @Override
    public Optional<Field> getFieldRef() {
        return Optional.ofNullable(fieldRef);
    }

    @Override
    public Optional<Invokable<Object, ? extends V>> getGetterRef() {
        return Optional.ofNullable(getterRef);
    }

    @Override
    public Optional<Invokable<Object, Void>> getSetterRef() {
        return Optional.ofNullable(setterRef);
    }

    @Override
    public Optional<Invokable<Object, ? extends ReadOnlyProperty<V>>> getAccessorRef() {
        return Optional.ofNullable(accessorRef);
    }

    protected void checkGetValue(Object instance) {
        checkNotNull(instance, "instance == null");
    }

    protected void checkSetValueParams(Object instance, @Nullable V value) throws IllegalStateException {
        checkNotNull(instance, "instance == null");
        if (value != null && getPropertyClass() != null) {
            checkArgument(getPropertyType().unwrap().getRawType().isAssignableFrom(Primitives.unwrap(value.getClass())),
                    "Can not assign property value %s to %s", value, getPropertyClass());
        }
    }

    /**
     * Uses the stored information to create an accessing function to a {@link ReadOnlyProperty} instance on a type
     * {@code T} returning a {@link ReadOnlyProperty} with value type {@code V}. If the property does not have a
     * property accessor, an attempt to create one via the bean "getter" method that returns a
     * {@link ReadOnlyObjectWrapper} will be created.
     *
     * @return Present function if possible, if not able to create a synthetic property, it will
     * return {@link Optional#empty()}
     */
    @SuppressWarnings("unchecked")
    public Function<Object, ? extends ReadOnlyProperty<V>> getPropertyReadOnlyAccessor() {
        return (Object instance) -> new ReadOnlyObjectWrapper<>(instance, getName(), getValueRaw(instance));
    }

    @Override
    public abstract Class<?> getPropertyClass();

    @Override
    public abstract Optional<V> getValue(final Object instance) throws IllegalStateException;

    @Override
    public abstract void setValue(final Object instance, @Nullable final V value) throws IllegalStateException;

    @Override
    public String toString() {
        return "Property@" + getBaseClass() + "{" + name + ": " + getPropertyClass() + ", " + getMutability() + "}";
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PropertyInfoBase)) {
            return false;
        }
        final PropertyInfoBase<?> that = (PropertyInfoBase<?>) o;
        return Objects.equals(base, that.base) &&
                Objects.equals(name, that.name) &&
                Objects.equals(mutability, that.mutability);
    }

    @Override
    public int hashCode() {
        return Objects.hash(base, name, mutability);
    }
}
