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
import javafx.beans.property.LongProperty;
import javafx.beans.property.ReadOnlyLongProperty;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.function.ObjLongConsumer;
import java.util.function.ToLongFunction;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Implements a {@link PropertyInfo} instance wrapping long values.
 */
public class LongPropertyInfo extends PropertyInfoBase<Number> {

    private final ToLongFunction<Object> propertyGetter;
    private final ObjLongConsumer<Object> propertySetter;

    @SuppressWarnings("unchecked")
    LongPropertyInfo(TypeToken<?> base,
                     String name,
                     @Nullable Field fieldRef,
                     @Nullable Invokable<Object, Long> getterRef,
                     @Nullable Invokable<Object, Void> setterRef,
                     @Nullable Invokable<Object, ? extends ReadOnlyLongProperty> accessorRef) {
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
                this.propertySetter = setterFromPropMethod((Invokable<Object, LongProperty>) accessorRef);
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
        return Long.class;
    }


    @Override
    public TypeToken<?> getPropertyType() {
        return TypeToken.of(long.class);
    }

    private ToLongFunction<Object> getterFromPropMethod(Invokable<Object, ? extends ReadOnlyLongProperty> handle) {
        return o -> {
            try {
                ReadOnlyLongProperty property = handle.invoke(o);
                return property.getValue();
            } catch (InvocationTargetException | IllegalAccessException t) {
                throw new IllegalStateException(t);
            }
        };
    }

    private ObjLongConsumer<Object> setterFromPropMethod(Invokable<Object, ? extends LongProperty> handle) {
        return (o, v) -> {
            try {
                LongProperty property = handle.invoke(o);
                property.setValue(v);
            } catch (InvocationTargetException | IllegalAccessException t) {
                throw new IllegalStateException(t);
            }
        };
    }

    private ToLongFunction<Object> getterFromMethod(Invokable<Object, Long> handle) {
        return o -> {
            try {
                return handle.invoke(o);
            } catch (InvocationTargetException | IllegalAccessException t) {
                throw new IllegalStateException(t);
            }
        };
    }

    private ObjLongConsumer<Object> setterFromMethod(Invokable<Object, Void> handle) {
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

        return Optional.of(propertyGetter.applyAsLong(instance));
    }

    public long get(Object instance) {
        checkGetValue(instance);

        return propertyGetter.applyAsLong(instance);
    }

    @Override
    public void setValue(Object instance, @Nullable Number value) throws IllegalStateException {
        checkSetValueParams(instance, value);

        checkNotNull(value, "value == null");
        propertySetter.accept(instance, value.longValue());
    }

    @Override
    protected void checkSetValueParams(final Object instance, @Nullable final Number value) throws IllegalStateException {
        super.checkSetValueParams(instance, value);
        checkState(propertySetter != null, "Property is read-only, no setter.");
    }

    public void set(Object instance, long value) {
        checkNotNull(instance, "instance == null");
        checkState(propertySetter != null, "Property is read-only, no setter.");

        propertySetter.accept(instance, value);
    }
}
