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

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import javafx.beans.property.*;

import javax.annotation.CheckReturnValue;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Utility functions for use with reflection
 */
public abstract class MoreReflection {

    private MoreReflection() {
        // no instantiate
    }

    /**
     * Unwraps a {@link Property} subtype and gets the underlying value type.
     *
     * @param propertyType Type of the property, used to extract the generic
     * @return Underlying type or null if not found
     */
    @CheckReturnValue
    public static TypeToken<?> unwrapPropertyType(TypeToken<?> propertyType) {
        checkNotNull(propertyType, "propertyType == null");
        Class<?> rawType = propertyType.getRawType();
        checkNotNull(rawType, "rawType == null");

        // used to clean up the if statements
        TypeToken<?> readOnlyPrim = new TypeToken<ReadOnlyProperty<Number>>(rawType) {
        };
        if (propertyType.isSubtypeOf(readOnlyPrim)) {
            if (propertyType.isSubtypeOf(ReadOnlyIntegerProperty.class)) {
                return TypeToken.of(int.class);
            } else if (propertyType.isSubtypeOf(ReadOnlyLongProperty.class)) {
                return TypeToken.of(long.class);
            } else if (propertyType.isSubtypeOf(ReadOnlyDoubleProperty.class)) {
                return TypeToken.of(double.class);
            } else {
                throw new IllegalArgumentException("Unknown primitive property: " + propertyType);
            }
        } else if (propertyType.isSubtypeOf(ReadOnlyStringProperty.class)) {
            return TypeToken.of(String.class);
        } else if (isReadOnlyProperty(propertyType)) {
            // This checks for ReadOnlyProperty, both of which we can handle by
            // Pulling out the first generic argument (thanks to Guava's TypeToken)
            TypeToken<?> param = propertyType.resolveType(ReadOnlyProperty.class.getTypeParameters()[0]);
            return param;
        } else if (isOptional(propertyType)) {
            TypeToken<?> param = propertyType.resolveType(Optional.class.getTypeParameters()[0]);
            return param;
        } else {
            return propertyType;
        }
    }

    /**
     * Checks the arguments passed for validity with the invoked method.
     *
     * @param method     Method invoked
     * @param objectType Type of the instance
     * @param valueType  Value returned
     * @param <O>        Object type
     * @param <R>        Returned value
     */
    // FIXME replace with TypeLiteral
    public static <O, R> void checkMethodTypes(Method method,
                                               Class<O> objectType,
                                               Class<R> valueType,
                                               Class<?>... parameters) {
        checkNotNull(method, "method is null");
        checkNotNull(objectType, "objectType == null");
        checkNotNull(valueType, "valueType == null");

        Class<?> declType = method.getDeclaringClass();
        checkArgument(declType.isAssignableFrom(objectType),
                "Can not call %s on object %s", declType, objectType);

        Class<?> retType = method.getReturnType();
        checkArgument(valueType.isAssignableFrom(retType),
                "Can not convert %s to value %s", retType, valueType);

        // Now check all the parameters
        boolean paramsCorrect = (method.getParameterCount() == parameters.length);

        Class<?>[] mparams = method.getParameterTypes();
        for (int i = 0; paramsCorrect && i < parameters.length; ++i) {
            paramsCorrect = mparams[i].isAssignableFrom(parameters[i]);
        }

        if (!paramsCorrect) {
            throw new IllegalArgumentException(
                    String.format("Invalid parameters for method %s, expected: %s, actual: %s",
                            method, Arrays.asList(method.getParameterTypes()), Arrays.asList(parameters)));
        }
    }

    public static boolean isReadOnlyProperty(Class<?> clazz) {
        return isReadOnlyProperty(TypeToken.of(clazz));
    }


    public static boolean isReadOnlyProperty(TypeToken<?> type) {
        return type.isSubtypeOf(ReadOnlyProperty.class);
    }

    public static boolean isProperty(Class<?> clazz) {
        return isProperty(TypeToken.of(clazz));
    }

    public static boolean isProperty(TypeToken<?> type) {
        return type.isSubtypeOf(Property.class);
    }

    public static boolean isOptional(Class<?> clazz) {
        return isOptional(TypeToken.of(clazz));
    }

    public static boolean isOptional(TypeToken<?> type) {
        return type.isSubtypeOf(Optional.class);
    }

    /**
     * Checks if the passed type is an observable collection of the correct type.
     *
     * @param propType
     * @return
     */
    public static boolean isCollection(TypeToken<?> propType) {
        return propType.isSubtypeOf(Map.class) || propType.isSubtypeOf(Collection.class);
    }

    private static <E> TypeToken<Collection<E>> collectionToken(TypeToken<E> elementType) {
        return new TypeToken<Collection<E>>() {
        }
                .where(new TypeParameter<E>() {
                }, elementType);
    }

    /**
     * Resolves the {@code index}th type parameter from the token.
     *
     * @param token
     * @param index
     * @return
     * @throws ArrayIndexOutOfBoundsException if the type parameter doesn't exist
     */
    public static TypeToken<?> resolveTypeParameter(TypeToken<?> token, int index) {
        return token.resolveType(token.getRawType().getTypeParameters()[index]);
    }

    /**
     * Checks if the passed type is an observable collection of the correct type.
     *
     * @param propType
     * @return
     */
    public static <K, V> boolean isMap(TypeToken<?> propType, TypeToken<K> keyType, TypeToken<V> valueType) {
        TypeToken<Map<K, V>> mapType = new TypeToken<Map<K, V>>() {
        }
                .where(new TypeParameter<K>() {
                }, keyType)
                .where(new TypeParameter<V>() {
                }, valueType);

        boolean result = propType.isSubtypeOf(mapType);
        if (result) {
            TypeToken<?> pKeyType = resolveTypeParameter(propType, 0);
            TypeToken<?> pValueType = resolveTypeParameter(propType, 1);

            result &= keyType.isSubtypeOf(pKeyType) && valueType.isSubtypeOf(pValueType);
        }

        return result;
    }

    /**
     * Checks if the passed type is a collection of the correct type.
     *
     * @param propType
     * @return
     */
    public static <E> boolean isCollection(TypeToken<?> propType, TypeToken<E> elementType) {

        TypeToken<Collection<E>> collType = collectionToken(elementType);

        if (propType.isSubtypeOf(collType)) {
            return true;
        } else if (propType.isSubtypeOf(Map.class)) {
            TypeToken<?> keyType = resolveTypeParameter(elementType, 0);
            TypeToken<?> valueType = resolveTypeParameter(elementType, 1);

            return isMap(propType, keyType, valueType);
        }

        return false;
    }
}
