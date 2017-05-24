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
import com.google.common.reflect.Invokable;
import com.google.common.reflect.TypeToken;
import javafx.beans.property.Property;
import javafx.beans.property.ReadOnlyProperty;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Optional;

/**
 * Defines accessors and information for a {@link Property}. This interface is populated by the
 * {@link PropertyObject} class.
 */
public interface PropertyInfo<V> {

    /**
     * Used to denote how much access is available on a property
     */
    enum Mutability {
        Read, Write;

        public EnumSet<Mutability> readOnly() {
            return EnumSet.of(Read);
        }

        public EnumSet<Mutability> readWrite() {
            return EnumSet.of(Read, Write);
        }
    }

    /**
     * Name of the property
     *
     * @return name
     */
    String getName();

    /**
     * Get the type this property has access from. This does not imply the Property is defined in this class, only that
     * this current class can access that property.
     *
     * @return Type the property is accessible from
     */
    Class<?> getBaseClass();

    TypeToken<?> getBaseType();

    /**
     * Type of the value stored in the {@link Property}
     *
     * @return Type of the stored property
     */
    Class<?> getPropertyClass();

    TypeToken<?> getPropertyType();

    ImmutableList<? extends Annotation> getAnnotationsPrsent();

    boolean isAnnotationPresent(Class<? extends Annotation> annotationClass);


    /**
     * Get the mutability state of the Property.
     *
     * @return Mutability state
     * @see Mutability
     */
    ImmutableSet<Mutability> getMutability();

    /**
     * Get the reflective {@link Field} for the property.
     *
     * @return Present {@code field} if found and not ignored
     * @see IgnoreProperty
     */
    Optional<Field> getFieldRef();

    /**
     * Get the reflective {@link Method} for the getting the value of the property.
     *
     * @return Present {@code Method} if found and not ignored
     * @see IgnoreProperty
     */
    Optional<Invokable<Object, ? extends V>> getGetterRef();

    /**
     * Get the reflective {@link Method} for the setting the value of the property.
     *
     * @return Present {@code Method} if found and not ignored
     * @see IgnoreProperty
     */
    Optional<Invokable<Object, Void>> getSetterRef();

    /**
     * Get the reflective {@link Method} for the accessing the value of the property.
     *
     * @return Present {@code Method} if found and not ignored
     * @see IgnoreProperty
     */
    Optional<Invokable<Object, ? extends ReadOnlyProperty<V>>> getAccessorRef();

    /**
     * Using the passed instance, reads the value of the Property from the instance.
     *
     * @param instance Non-{@code null} instance accessed
     * @return Optional value that is returned
     * @throws IllegalStateException Thrown if any exception is thrown inside the accessor method. The actual reason
     *                               is wrapped in the {@link Throwable#getCause()}
     */
    Optional<V> getValue(Object instance) throws IllegalStateException;

    /**
     * Using the passed instance, reads the value of the Property from the instance but returns it in its raw, possibly
     * {@code null} form.
     *
     * @param instance Non-{@code null} instance accessed
     * @return Value from the getter, possibly {@code null}
     * @throws IllegalStateException Thrown if any exception is thrown inside the accessor method. The actual reason
     *                               is wrapped in the {@link Throwable#getCause()}
     */
    default @CheckForNull
    V getValueRaw(Object instance) throws IllegalStateException {
        return getValue(instance).orElse(null);
    }

    /**
     * Using the passed instance, sets the value of the Property from the instance.
     *
     * @param instance Non-{@code null} instance accessed
     * @throws IllegalStateException Thrown if any exception is thrown inside the accessor method. The actual reason
     *                               is wrapped in the {@link Throwable#getCause()}
     */
    void setValue(Object instance, @Nullable V value) throws IllegalStateException;


}
