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
import javafx.beans.property.Property;
import javafx.beans.property.ReadOnlyProperty;
import javafx.beans.property.ReadOnlySetProperty;
import javafx.beans.property.SetProperty;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Supports creating a property backed by a {@link Set} value. If it is a {@link SetProperty} it can be accessed via
 * {@link #getProperty(Object)}.
 */
public class SetPropertyInfo<V> extends ObjectPropertyInfo<Set<V>>
        implements CollectionPropertyInfo<V, Set<V>> {

    private final TypeToken<V> elemType;

    @SuppressWarnings("unchecked")
    SetPropertyInfo(TypeToken<?> base, String name,
                    TypeToken<Set<V>> propertyType,
                    TypeToken<V> elemType,
                    @Nullable Field field,
                    @Nullable Invokable<Object, Set<V>> getterRef,
                    @Nullable Invokable<Object, Void> setterRef,
                    @Nullable Invokable<Object, ? extends ReadOnlyProperty<Set<V>>> accessorRef) {
        super(base, name, propertyType, field, getterRef, setterRef, accessorRef);

        this.elemType = checkNotNull(elemType, "elemType == null");
    }

    @Override
    public Class<?> getPropertyClass() {
        return super.getPropertyClass();
    }

    @Override
    public TypeToken<Set<V>> getPropertyType() {
        return super.getPropertyType();
    }

    @Override
    public TypeToken<V> getElementType() {
        return elemType;
    }

    public SetProperty<V> getProperty(Object instance) {
        checkGetValue(instance);

        checkState(getAccessorRef().isPresent(), "Can not get property if no accessor method is present.");
        Invokable<Object, ? extends Property<? extends Set<V>>> accessor =
                (Invokable<Object, ? extends Property<? extends Set<V>>>) getAccessorRef().get();

        TypeToken<? extends Property<? extends Set<V>>> retType = accessor.getReturnType();
        checkState(retType.isSubtypeOf(new TypeToken<SetProperty<V>>(getPropertyClass()) {
                }),
                "Property %s is not writeable property, it is %s", getName(), retType);

        // write available
        try {
            return (SetProperty<V>) accessor.invoke(instance);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Invalid method state", e);
        }
    }

    public ReadOnlySetProperty<V> getReadOnlyProperty(Object instance) {
        checkGetValue(instance);

        checkState(getAccessorRef().isPresent(),
                "Can not get read-only property if no accessor method is present.");

        Invokable<Object, ? extends ReadOnlyProperty<? extends Set<V>>> accessor = getAccessorRef().get();

        TypeToken<? extends ReadOnlyProperty<? extends Set<V>>> retType = accessor.getReturnType();
        checkState(retType.isSubtypeOf(new TypeToken<ReadOnlySetProperty<V>>(getPropertyClass()) {
                }),
                "Property %s is not readable property, it is %s", getName(), retType);

        try {
            return (ReadOnlySetProperty<V>) accessor.invoke(instance);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Invalid method state", e);
        }
    }
}
