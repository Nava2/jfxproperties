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

import javafx.beans.property.ReadOnlyProperty;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used to mark a property that should be ignored. This annotation can be applied in multiple different locations.
 * <p>
 * <ol>
 * <li>On {@code fields}: if used on a {@code field}, this will stop all properties with the same name as the field
 * from being processed. For example, for the following code segment, the property "ignored" is ignored at the field
 * level so it's methods will not be gathered and no property named "ignored" will exist even though the accessor
 * methods exist.
 * <pre><code>
 * class Base {
 * \@IgnoreProperty
 * private StringProperty ignored = new SimpleStringProperty(this, "bar");
 *
 * public StringProperty ignoredProperty() {
 * return ignored;
 * }
 *
 * public String getIgnored() {
 * return this.ignored.getValue();
 * }
 *
 * public void setIgnored(String ignored) {
 * this.ignored.setValue(ignored);
 * }
 * }
 * </code></pre>
 * </li>
 * <p>
 * <li> On {@code Method}s: this is a more granular level than the field usage. This will ignore specific methods
 * from being collected. For example, in the following segment, we can ignore the {@code void setPartial(String)} method
 * effectively making the "ignored" property a "read-only" property.
 * <pre><code>
 * class Base {
 * private StringProperty partial = new SimpleStringProperty(this, "bar");
 *
 * public StringProperty partialProperty() {
 * return partial;
 * }
 *
 * public String getPartial() {
 * return this.partial.getValue();
 * }
 *
 * \@IgnoreProperty
 * public void setPartial(String partial) {
 * this.partial.setValue(partial);
 * }
 * }
 * </code></pre>
 * <strong>Note:</strong> to make a "write-only" property, one would need to ignore both the {@code getPartial()}
 * and the {@code partialProperty()} methods as if a getter does not exist, but the {@link ReadOnlyProperty}
 * is accessible via a {@code public} method, a synthetic getter is created that will call {@link ReadOnlyProperty#getValue()}
 * </li>
 * </ol>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface IgnoreProperty {

}
