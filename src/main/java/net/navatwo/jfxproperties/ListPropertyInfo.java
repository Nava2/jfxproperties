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

import com.google.common.reflect.Invokable;
import com.google.common.reflect.TypeToken;
import javafx.beans.property.ListProperty;
import javafx.beans.property.Property;
import javafx.beans.property.ReadOnlyListProperty;
import javafx.beans.property.ReadOnlyProperty;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Supports creating a property backed by a {@link List} value. If it is a {@link ListProperty} it can be accessed via
 * {@link #getProperty(Object)}.
 */
public class ListPropertyInfo<E> extends ObjectPropertyInfo<List<E>>
        implements CollectionPropertyInfo<E, List<E>> {

    private final TypeToken<E> elemType;

    ListPropertyInfo(TypeToken<?> base,
                     String name,
                     TypeToken<List<E>> propertyType,
                     TypeToken<E> elemType,
                     @Nullable Field field,
                     @Nullable Invokable<Object, List<E>> getterRef,
                     @Nullable Invokable<Object, Void> setterRef,
                     @Nullable Invokable<Object, ? extends ReadOnlyProperty<List<E>>> accessorRef) {
        super(base, name, propertyType, field, getterRef, setterRef, accessorRef);

        this.elemType = checkNotNull(elemType, "elemType == null");
    }

    @Override
    public Class<?> getPropertyClass() {
        return super.getPropertyClass();
    }

    @Override
    public TypeToken<List<E>> getPropertyType() {
        return super.getPropertyType();
    }

    @Override
    public TypeToken<E> getElementType() {
        return elemType;
    }

    public ListProperty<E> getProperty(Object instance) {
        checkGetValue(instance);

        checkState(getAccessorRef().isPresent(), "Can not get property if no accessor method is present.");

        @SuppressWarnings("unchecked")
        Invokable<Object, ? extends Property<? extends List<E>>> accessor =
                (Invokable<Object, ? extends Property<? extends List<E>>>) (getAccessorRef().get());

        TypeToken<? extends Property<? extends List<E>>> retType = accessor.getReturnType();
        checkState(retType.isSubtypeOf(ListProperty.class),
                "Property %s is not writeable property, it is %s", getName(), retType);

        // write available
        try {
            return (ListProperty<E>) accessor.invoke(instance);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Invalid method state", e);
        }
    }

    public ReadOnlyListProperty<E> getReadOnlyProperty(Object instance) {
        checkGetValue(instance);

        checkState(getAccessorRef().isPresent(),
                "Can not get read-only property if no accessor method is present.");

        Invokable<Object, ? extends ReadOnlyProperty<? extends List<E>>> accessor = getAccessorRef().get();

        TypeToken<? extends ReadOnlyProperty<? extends List<E>>> retType = accessor.getReturnType();
        checkState(retType.isSubtypeOf(ReadOnlyListProperty.class),
                "Property %s is not readable property, it is %s", getName(), retType);
        try {
            return (ReadOnlyListProperty<E>) accessor.invoke(instance);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Invalid method state", e);
        }
    }
}
