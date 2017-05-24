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

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkState;

/**
 * Implements a {@link PropertyInfo} instance wrapping Object values.
 */
public class ObjectPropertyInfo<V> extends PropertyInfoBase<V> {

    private final TypeToken<V> propertyType;
    private final Function<Object, Optional<V>> propertyGetter;
    private final BiConsumer<Object, V> propertySetter;

    @SuppressWarnings("unchecked")
    ObjectPropertyInfo(TypeToken<?> base,
                       String name,
                       TypeToken<V> propertyType,
                       @Nullable Field field,
                       @Nullable Invokable<Object, V> getterRef,
                       @Nullable Invokable<Object, Void> setterRef,
                       @Nullable Invokable<Object, ? extends ReadOnlyProperty<V>> accessorRef) {
        super(base, name, field, getterRef, setterRef, accessorRef);

        this.propertyType = propertyType;

        switch (FunctionLocation.calculateGetter(this)) {
            case Getter: {
                this.propertyGetter = getterFromMethod(getterRef);
            }
            break;
            case Accessor: {
                this.propertyGetter = getterFromPropMethod(accessorRef);
            }
            break;

            default: {
                this.propertyGetter = null;
            }
            break;
        }

        switch (FunctionLocation.calculateSetter(this)) {
            case Setter: {
                this.propertySetter = setterFromMethod(setterRef);
            }
            break;
            case Accessor: {
                this.propertySetter = setterFromPropMethod((Invokable<Object, ? extends Property<V>>) accessorRef);
            }
            break;

            default: {
                this.propertySetter = null;
            }
            break;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<?> getPropertyClass() {
        // return (Class<V>) propertyType.getRawType();
        return propertyType.getRawType();
    }


    @Override
    public TypeToken<V> getPropertyType() {
        return propertyType.unwrap();
    }

    private Function<Object, Optional<V>> getterFromPropMethod(Invokable<Object, ? extends ReadOnlyProperty<V>> handle) {
        return o -> {
            try {
                ReadOnlyProperty<V> property = handle.invoke(o);
                return Optional.ofNullable(property.getValue());
            } catch (InvocationTargetException | IllegalAccessException t) {
                throw new IllegalStateException(t);
            }
        };
    }

    private BiConsumer<Object, V> setterFromPropMethod(Invokable<Object, ? extends Property<V>> handle) {
        return (o, v) -> {
            try {
                Property<V> property = handle.invoke(o);
                property.setValue(v);
            } catch (InvocationTargetException | IllegalAccessException t) {
                throw new IllegalStateException(t);
            }
        };
    }

    private Function<Object, Optional<V>> getterFromMethod(Invokable<Object, ? extends V> getter) {
        checkState(getter != null, "Can not get getter without Getter Function");

        TypeToken<?> returnType = getter.getReturnType();

        if (MoreReflection.isOptional(returnType)) {
            return o -> {
                try {
                    @SuppressWarnings("unchecked")
                    Optional<V> out = (Optional<V>) getter.invoke(o);
                    return out;
                } catch (InvocationTargetException | IllegalAccessException t) {
                    throw new IllegalStateException(t);
                }
            };
        } else {
            // got a non-optional, do it a bit differently
            // We get the value and place it into the Optional instead
            return o -> {
                try {
                    return Optional.ofNullable(getter.invoke(o));
                } catch (InvocationTargetException | IllegalAccessException t) {
                    throw new IllegalStateException(t);
                }
            };
        }
    }

    private BiConsumer<Object, V> setterFromMethod(Invokable<Object, Void> handle) {
        return (o, v) -> {
            try {
                handle.invoke(o, v);
            } catch (InvocationTargetException | IllegalAccessException t) {
                throw new IllegalStateException(t);
            }
        };
    }

    @Override
    public Optional<V> getValue(Object instance) throws IllegalStateException {
        checkGetValue(instance);
        checkState(propertyGetter != null,
                "Property %s for %s is write-only, no getter available. " +
                        "Check that the appropriate property accessor or getter is exposed as public.",
                getName(), getBaseType());

        return propertyGetter.apply(instance);
    }

    @Override
    public void setValue(Object instance, @Nullable V value) throws IllegalStateException {
        checkSetValueParams(instance, value);
        checkState(propertySetter != null,
                "Property %s for %s is read-only, no setter available. " +
                        "Check that the appropriate writable property accessor or setter is exposed as public.",
                getName(), getBaseType());

        propertySetter.accept(instance, value);
    }

}
