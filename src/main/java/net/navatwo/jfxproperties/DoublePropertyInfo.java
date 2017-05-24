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
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ToDoubleFunction;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Implements a {@link PropertyInfo} instance wrapping double values.
 */
public class DoublePropertyInfo extends PropertyInfoBase<Number> {

    private final ToDoubleFunction<Object> propertyGetter;
    private final ObjDoubleConsumer<Object> propertySetter;

    @SuppressWarnings("unchecked")
    DoublePropertyInfo(TypeToken<?> base,
                       String name,
                       @Nullable Field fieldRef,
                       @Nullable Invokable<Object, Double> getterRef,
                       @Nullable Invokable<Object, Void> setterRef,
                       @Nullable Invokable<Object, ? extends ReadOnlyDoubleProperty> accessorRef) {
        super(base, name, fieldRef, getterRef, setterRef, accessorRef);

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
                this.propertySetter = setterFromPropMethod((Invokable<Object, DoubleProperty>) accessorRef);
            }
            break;

            default: {
                this.propertySetter = null;
            }
            break;
        }
    }

    @Override
    public Class<?> getPropertyClass() {
        return Double.class;
    }

    @Override
    public TypeToken<?> getPropertyType() {
        return TypeToken.of(double.class);
    }

    private ToDoubleFunction<Object> getterFromPropMethod(Invokable<Object, ? extends ReadOnlyDoubleProperty> handle) {
        return o -> {
            try {
                ReadOnlyDoubleProperty property = handle.invoke(o);
                return property.getValue();
            } catch (InvocationTargetException | IllegalAccessException t) {
                throw new IllegalStateException(t);
            }
        };
    }

    private ObjDoubleConsumer<Object> setterFromPropMethod(Invokable<Object, ? extends DoubleProperty> handle) {
        return (o, v) -> {
            try {
                DoubleProperty property = handle.invoke(o);
                property.setValue(v);
            } catch (InvocationTargetException | IllegalAccessException t) {
                throw new IllegalStateException(t);
            }
        };
    }

    private ToDoubleFunction<Object> getterFromMethod(Invokable<Object, Double> handle) {
        return o -> {
            try {
                return handle.invoke(o);
            } catch (InvocationTargetException | IllegalAccessException t) {
                throw new IllegalStateException(t);
            }
        };
    }

    private ObjDoubleConsumer<Object> setterFromMethod(Invokable<Object, Void> handle) {
        return (o, v) -> {
            try {
                handle.invoke(o, v);
            } catch (InvocationTargetException | IllegalAccessException t) {
                throw new IllegalStateException(t);
            }
        };
    }

    @Override
    protected void checkGetValue(final Object instance) {
        super.checkGetValue(instance);
        checkState(propertyGetter != null, "Property is write-only, no getter.");
    }

    @Override
    public Optional<Number> getValue(Object instance) throws IllegalStateException {
        checkGetValue(instance);

        return Optional.of(propertyGetter.applyAsDouble(instance));
    }

    public double get(Object instance) {
        checkGetValue(instance);

        return propertyGetter.applyAsDouble(instance);
    }

    @Override
    public void setValue(Object instance, @Nullable Number value) throws IllegalStateException {
        checkSetValueParams(instance, value);

        checkNotNull(value, "value == null");
        propertySetter.accept(instance, value.doubleValue());
    }

    @Override
    protected void checkSetValueParams(final Object instance, @Nullable final Number value) throws IllegalStateException {
        super.checkSetValueParams(instance, value);
        checkState(propertySetter != null, "Property is read-only, no setter.");
    }

    public void set(Object instance, double value) {
        checkNotNull(instance, "instance == null");
        checkState(propertySetter != null, "Property is read-only, no setter.");

        propertySetter.accept(instance, value);
    }
}
