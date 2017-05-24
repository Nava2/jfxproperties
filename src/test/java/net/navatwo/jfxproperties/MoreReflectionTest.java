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
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static net.navatwo.jfxproperties.MoreReflection.*;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.*;

/**
 * Tests the methods on {@link
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        MoreReflectionTest.CheckMethodType.class,
        MoreReflectionTest.UnwrapProperty.class,
        MoreReflectionTest.IsCollection.class
})
public class MoreReflectionTest {


    /**
     * Tests Ref
     */
    public static class CheckMethodType {

        private Method getter;
        private Method setter;
        private Method accessor;
        private Method poly;

        @Before
        public void setUp() throws Exception {
            getter = SimplePropertyClass.class.getMethod("getFoo");
            setter = SimplePropertyClass.class.getMethod("setFoo", int.class);
            accessor = SimplePropertyClass.class.getMethod("fooProperty");
            poly = ChildSimplePropertyClass.class.getMethod("testPoly", SimplePropertyClass.class);
        }

        @Test
        public void valid() throws Exception {
            // Getter
            checkMethodTypes(getter, SimplePropertyClass.class, int.class);

            // Setter
            checkMethodTypes(setter, SimplePropertyClass.class, void.class, int.class);

            // direct reference
            checkMethodTypes(accessor, SimplePropertyClass.class, IntegerProperty.class);
            // polymorphic check on return reference
            checkMethodTypes(accessor, SimplePropertyClass.class, ReadOnlyProperty.class);

            // polymorphic check on instance reference
            checkMethodTypes(accessor, ChildSimplePropertyClass.class, ReadOnlyProperty.class);

            // Check we can replace the parameter which children classes
            checkMethodTypes(poly, ChildSimplePropertyClass.class, SimplePropertyClass.class, SimplePropertyClass.class);
            checkMethodTypes(poly, ChildSimplePropertyClass.class, SimplePropertyClass.class, ChildSimplePropertyClass.class);
        }

        @Test(expected = IllegalArgumentException.class)
        public void noChildClassForReturnType() throws Exception {
            checkMethodTypes(poly, ChildSimplePropertyClass.class, ChildSimplePropertyClass.class, SimplePropertyClass.class);
        }

        @Test(expected = IllegalArgumentException.class)
        public void noSuperClassForInstanceType() throws Exception {
            checkMethodTypes(poly, SimplePropertyClass.class, SimplePropertyClass.class, SimplePropertyClass.class);
        }

        @Test(expected = IllegalArgumentException.class)
        public void noSuperClassForParameterType() throws Exception {
            checkMethodTypes(poly, ChildSimplePropertyClass.class, SimplePropertyClass.class, Object.class);
        }

    }

    /**
     * {@link Matcher} to simplify working with the {@link TypeToken#getRawType()} value
     */
    static Matcher<TypeToken<?>> hasRawType(Class<?> value) {
        return new TypeSafeMatcher<TypeToken<?>>() {

            @Override
            protected void describeMismatchSafely(TypeToken<?> item, Description mismatchDescription) {
                mismatchDescription.appendText("was ")
                        .appendValue(item.getRawType());
            }

            @Override
            protected boolean matchesSafely(TypeToken<?> item) {
                return item.getRawType() == value;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(" has raw type ")
                        .appendValue(value);
            }
        };
    }

    /**
     * Tests Ref
     */
    public static class UnwrapProperty {

        private TypeToken<AllProperties<UUID>> base = new TypeToken<AllProperties<UUID>>() {
        };

        @SuppressWarnings("unchecked")
        private <T> void checkProperty(String property, Class<T> expected) throws Exception {
            checkProperty(property, TypeToken.of(expected));
        }

        @SuppressWarnings("unchecked")
        private <T> void checkProperty(String property, TypeToken<T> expected) throws Exception {
            Field rw = AllProperties.class.getDeclaredField(property);

            TypeToken<? extends T> rwType = (TypeToken<? extends T>) base.resolveType(rw.getGenericType());
            TypeToken<?> unwrappedRwType = unwrapPropertyType(rwType);
            assertThat("Read-write property could not be extracted.",
                    unwrappedRwType, equalTo(expected));

            try {
                final String readOnlyProperty = "readOnly" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
                Field ro = AllProperties.class.getDeclaredField(readOnlyProperty);

                TypeToken<? extends T> roType = (TypeToken<? extends T>) base.resolveType(ro.getGenericType());
                TypeToken<?> unwrappedRoType = unwrapPropertyType(roType);
                assertThat("Read-only property could not be extracted.",
                        unwrappedRoType, equalTo(expected));
            } catch (NoSuchFieldException nsfe) {
                // acceptable here, may not be a "read-only" version
            }
        }

        @Test
        public void integerProperty() throws Exception {
            checkProperty("integerProperty", int.class);
        }

        @Test
        public void longProperty() throws Exception {
            checkProperty("longProperty", long.class);
        }

        @Test
        public void doubleProperty() throws Exception {
            checkProperty("doubleProperty", double.class);
        }

        @Test
        public void stringProperty() throws Exception {
            checkProperty("stringProperty", String.class);
        }

        @Test
        public void objectProperty() throws Exception {
            checkProperty("objectProperty", UUID.class);
        }

        @Test
        public void nestedProperty() throws Exception {
            checkProperty("listProperty", new TypeToken<ObservableList<UUID>>(getClass()) {
            });
        }

        @Test
        public void enumSetProperty() throws Exception {

            checkProperty("enumSetProperty", new TypeToken<EnumSet<AllProperties.TestEnum>>(getClass()) {
            });
        }

    }

    public static class IsCollection {

        TypeToken<ObservableSet<String>> stringObsSet = new TypeToken<ObservableSet<String>>(getClass()) {
        };
        TypeToken<Set<String>> stringSet = new TypeToken<Set<String>>(getClass()) {
        };
        TypeToken<ObservableList<String>> stringObsList = new TypeToken<ObservableList<String>>(getClass()) {
        };
        TypeToken<List<String>> stringList = new TypeToken<List<String>>(getClass()) {
        };
        TypeToken<ObservableMap<String, Long>> stringLongObsMap = new TypeToken<ObservableMap<String, Long>>(getClass()) {
        };

        @Test
        public void valid() {
            assertTrue("Incorrect result in Set",
                    isCollection(stringObsSet, TypeToken.of(String.class)));
            assertTrue("Incorrect result for Set",
                    isCollection(stringSet, TypeToken.of(String.class)));

            assertTrue("Incorrect result for List",
                    isCollection(stringObsList, TypeToken.of(String.class)));
            assertTrue("Incorrect result for List",
                    isCollection(stringList, TypeToken.of(String.class)));

            assertTrue("Incorrect result for Map",
                    isCollection(stringLongObsMap, new TypeToken<Map.Entry<String, Long>>() {
                    }));
        }

        @Test
        public void incorrectCollTypes() {
            assertFalse("Succeeded with wrong element type",
                    isCollection(stringObsList, TypeToken.of(Integer.class)));
        }

        @Test
        public void incorrectMapTypes() {
            assertFalse("Incorrect result for map with wrong key type",
                    isCollection(stringLongObsMap, new TypeToken<Map.Entry<Integer, Long>>() {
                    }));
            assertFalse("Incorrect result for map with wrong value type",
                    isCollection(stringLongObsMap, new TypeToken<Map.Entry<String, Integer>>() {
                    }));
        }

    }

    @SuppressWarnings("unused")
    static class AllProperties<T> {

        private IntegerProperty integerProperty;
        private ReadOnlyIntegerProperty readOnlyIntegerProperty;

        private DoubleProperty doubleProperty;
        private ReadOnlyDoubleProperty readOnlyDoubleProperty;

        private LongProperty longProperty;
        private ReadOnlyLongProperty readOnlyLongProperty;

        private StringProperty stringProperty;
        private ReadOnlyStringProperty readOnlyStringProperty;

        private ObjectProperty<T> objectProperty;
        private ReadOnlyObjectProperty<T> readOnlyObjectProperty;

        private ListProperty<T> listProperty;
        private ReadOnlyListProperty<T> readOnlyListProperty;

        public enum TestEnum {
            Foo,
            Bar
        }

        private EnumSet<TestEnum> enumSetProperty;
    }

    /**
     * Defines a class with a single read-write property
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    static class SimplePropertyClass {

        private IntegerProperty foo;

        public int getFoo() {
            return foo.get();
        }

        public void setFoo(final int foo) {
            this.foo.set(foo);
        }

        public IntegerProperty fooProperty() {
            return foo;
        }
    }

    /**
     * Defines a hierarchy with {@link SimplePropertyClass}
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    static class ChildSimplePropertyClass extends SimplePropertyClass {

        // dummy method that checks that the return type is properly checked
        // AND the parameter
        public SimplePropertyClass testPoly(SimplePropertyClass value) {
            return null;
        }

    }
}