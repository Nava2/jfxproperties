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

import com.google.common.reflect.TypeToken;

import java.util.Collection;

/**
 * Represents a {@link Collection}-based {@link PropertyInfo}.
 */
public interface CollectionPropertyInfo<V, C extends Collection<V>> extends PropertyInfo<C> {

    /**
     * Gets the type of the underlying element
     */
    @SuppressWarnings("unchecked")
    default TypeToken<V> getElementType() {
        return (TypeToken<V>) (getBaseType().resolveType(getPropertyType().getType()).resolveType(Collection.class.getTypeParameters()[0]));
    }

    /**
     * Gets the class of the {@code V} parameter.
     *
     * @return
     */
    default Class<?> getElementClass() {
        return getElementType().getRawType();
    }
}
