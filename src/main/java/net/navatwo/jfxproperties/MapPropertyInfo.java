package net.navatwo.jfxproperties;

import com.google.common.reflect.Invokable;
import com.google.common.reflect.TypeToken;
import javafx.beans.property.MapProperty;
import javafx.beans.property.Property;
import javafx.beans.property.ReadOnlyMapProperty;
import javafx.beans.property.ReadOnlyProperty;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Represents an {@link Map} property
 */
public class MapPropertyInfo<K, V> extends ObjectPropertyInfo<Map<K, V>> {

    private final TypeToken<K> keyType;
    private final TypeToken<V> elemType;

    @SuppressWarnings("unchecked")
    MapPropertyInfo(TypeToken<?> base,
                    String name,
                    TypeToken<Map<K, V>> propertyType,
                    TypeToken<K> keyType,
                    TypeToken<V> elemType,
                    @Nullable Field field,
                    @Nullable Invokable<Object, Map<K, V>> getterRef,
                    @Nullable Invokable<Object, Void> setterRef,
                    @Nullable Invokable<Object, ? extends ReadOnlyProperty<Map<K, V>>> accessorRef) {
        super(base, name, propertyType, field, getterRef, setterRef, accessorRef);

        this.keyType = checkNotNull(keyType, "keyType == null");
        this.elemType = checkNotNull(elemType, "elemType == null");
    }

    @Override
    public Class<?> getPropertyClass() {
        return super.getPropertyClass();
    }

    @Override
    public TypeToken<Map<K, V>> getPropertyType() {
        return super.getPropertyType();
    }

    public TypeToken<K> getKeyType() {
        return keyType;
    }

    public Class<?> getKeyClass() {
        return keyType.getRawType();
    }

    public TypeToken<V> getElemType() {
        return elemType;
    }


    public Class<?> getElemClass() {
        return elemType.getRawType();
    }


    public MapProperty<K, V> getProperty(Object instance) {
        checkGetValue(instance);

        checkState(getAccessorRef().isPresent(),
                "Can not get property %s %s if no accessor method is present.",
                getName(), getBaseType());

        @SuppressWarnings("unchecked")
        Invokable<Object, ? extends Property<? extends Map<K, V>>> accessor =
                (Invokable<Object, ? extends Property<? extends Map<K, V>>>) (getAccessorRef().get());

        TypeToken<? extends Property<? extends Map<K, V>>> retType = accessor.getReturnType();
        checkState(retType.isSubtypeOf(new TypeToken<MapProperty<K, V>>(getPropertyClass()) {
                }),
                "Property %s %s on %s is read-only, make sure proper accessor or setters are available.",
                getName(), retType, getBaseType());

        // write available
        try {
            return (MapProperty<K, V>) accessor.invoke(instance);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Invalid method state", e);
        }
    }

    public ReadOnlyMapProperty<K, V> getReadOnlyProperty(Object instance) {
        checkGetValue(instance);

        checkState(getAccessorRef().isPresent(),
                "Can not get read-only property %s on %s if no accessor method is present.",
                getName(), getBaseType());

        @SuppressWarnings("unchecked")
        Invokable<Object, ? extends ReadOnlyProperty<? extends Map<K, V>>> accessor = getAccessorRef().get();

        TypeToken<? extends ReadOnlyProperty<? extends Map<K, V>>> retType = accessor.getReturnType();
        checkState(retType.isSubtypeOf(new TypeToken<ReadOnlyMapProperty<K, V>>(getPropertyClass()) {
                }),
                "Property %s %s on %s is not a property, make sure proper accessor or setters are available.",
                getName(), retType, getBaseType());
        try {
            return (ReadOnlyMapProperty<K, V>) accessor.invoke(instance);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Invalid method state", e);
        }
    }
}
