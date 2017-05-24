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

import com.github.npathai.hamcrestopt.OptionalMatchers;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.Invokable;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Test the abilities of the {@link PropertyObject.Builder} class.
 * <p>
 * If changing the {@link RealPropertyObjectBuilder}, the {@link StringTests} are likely a good set of tests to get
 * "green" before trying the others. After that, {@link FieldAccess}, {@link Interface} and {@link Inheritance} in
 * that order.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        PropertyObjectTest.StringTests.class,
        PropertyObjectTest.FieldAccess.class,
        PropertyObjectTest.Inheritance.class,
        PropertyObjectTest.Interface.class,
        PropertyObjectTest.IgnorePropertyTest.class
})
public class PropertyObjectTest {

    /**
     * Tests the prefixes and suffixes are correctly used and can be adjusted.
     */
    public static class StringTests {

        @SuppressWarnings({"WeakerAccess", "unused"})
        static class ChangeLookup {

            public static int INIT_FOO = 30;

            // defaults
            private IntegerProperty foo = new SimpleIntegerProperty(INIT_FOO);

            public int getFoo() {
                return foo.getValue();
            }

            public void setFoo(int set) {
                foo.setValue(set);
            }

            public IntegerProperty fooProperty() {
                return foo;
            }

            public static int INIT_B_FOO = 20;

            // b_
            private IntegerProperty b_foo = new SimpleIntegerProperty(INIT_B_FOO);

            public int b_foo() {
                return b_foo.getValue();
            }

            public void b_foo(int set) {
                b_foo.setValue(set);
            }

            public IntegerProperty foo_b() {
                return b_foo;
            }

            public boolean isBar() {
                return false;
            }

            public void setBar(boolean value) {

            }

            public BooleanProperty barProperty() {
                return null;
            }

        }


        /**
         * Convenience function that checks that a builder's properties are appropriately changed
         */
        static PropertyObject.Builder checkedPropSet(PropertyObject.Builder from,
                                                     PropertyObject.MethodType type,
                                                     String param) {
            final String desc;
            final BiFunction<PropertyObject.Builder, String, PropertyObject.Builder> setter;
            final Function<PropertyObject.Builder, ImmutableSet<String>> getter;
            switch (type) {
                case GETTER:
                    desc = "getter prefix";
                    setter = PropertyObject.Builder::setGetterPrefixes;
                    getter = PropertyObject.Builder::getGetterPrefixes;
                    break;

                case SETTER:
                    desc = "setter prefix";
                    setter = PropertyObject.Builder::setSetterPrefixes;
                    getter = PropertyObject.Builder::getSetterPrefixes;
                    break;
                case ACCESSOR:
                    desc = "property suffix";
                    setter = PropertyObject.Builder::setPropertySuffixes;
                    getter = PropertyObject.Builder::getPropertySuffixes;
                    break;

                case NONE:
                    desc = "field prefix";
                    setter = PropertyObject.Builder::setFieldPrefixes;
                    getter = PropertyObject.Builder::getFieldPrefixes;
                    break;

                default:
                    fail("Can not set parameter: " + type);
                    throw new RuntimeException(); // throws ^
            }

            ImmutableSet<String> originalValue = getter.apply(from);
            PropertyObject.Builder check = setter.apply(from, param);

            assertNotSame("Did not return new Builder instance after " + desc + " change",
                    check, from);
            Assert.assertEquals("Setting " + desc + " changed original builder",
                    originalValue, getter.apply(from));
            Assert.assertEquals("Setting " + desc + " did not change new builder value",
                    ImmutableSet.of(param), getter.apply(check));

            return check;
        }

        @Test
        public void removePrefix() {
            assertEquals("foo", PropertyObject.Builder.removePrefix("setFoo", "set"));
            assertEquals("nopeFoo", PropertyObject.Builder.removePrefix("nopeFoo", "set"));
            assertEquals("setFoo", PropertyObject.Builder.removePrefix("setFoo", ""));
        }

        @Test
        public void removeSuffix() {
            assertEquals("foo", PropertyObject.Builder.removeSuffix("fooProp", "Prop"));
            assertEquals("fooNope", PropertyObject.Builder.removeSuffix("fooNope", "Prop"));
            assertEquals("fooProp", PropertyObject.Builder.removeSuffix("fooProp", ""));
        }

        @Test
        public void names() throws Exception {
            // defaults
            PropertyObject.Builder d_builder = PropertyObject.builder();
            assertEquals("Default field prefix incorrect",
                    PropertyObject.Builder.DEFAULT_FIELD_PREFIXES, d_builder.getFieldPrefixes());
            assertEquals("Default getter prefix incorrect",
                    PropertyObject.Builder.DEFAULT_GETTER_PREFIXES, d_builder.getGetterPrefixes());
            assertEquals("Default setter prefix incorrect",
                    PropertyObject.Builder.DEFAULT_SETTER_PREFIXES, d_builder.getSetterPrefixes());
            assertEquals("Default property suffix incorrect",
                    PropertyObject.Builder.DEFAULT_PROPERTY_SUFFIXES, d_builder.getPropertySuffixes());

            PropertyObject<ChangeLookup> d_propertyObject = d_builder.build(ChangeLookup.class);

            // b_foo property should fail, there's no getter, setter or accessor!
            try {
                d_propertyObject.getProperty("b_foo", Integer.class);
            } catch (IllegalArgumentException iae) {
                assertTrue("Wrong exception thrown", iae.getMessage().contains("b_foo does not exist on type"));
            }


            // foo property
            PropertyInfo<Integer> d_info = d_propertyObject.getProperty("foo", int.class);
            checkPI(d_info, ChangeLookup.class.getDeclaredField("foo"),
                    ChangeLookup.class.getDeclaredMethod("getFoo"),
                    ChangeLookup.class.getDeclaredMethod("setFoo", int.class),
                    ChangeLookup.class.getDeclaredMethod("fooProperty"));

            // bar property
            PropertyInfo<Boolean> d_bar_info = d_propertyObject.getProperty("bar", boolean.class);
            checkPI(d_bar_info, null,
                    ChangeLookup.class.getDeclaredMethod("isBar"),
                    ChangeLookup.class.getDeclaredMethod("setBar", boolean.class),
                    ChangeLookup.class.getDeclaredMethod("barProperty"));

            // Now permute them
            PropertyObject.Builder b_builder = checkedPropSet(d_builder, PropertyObject.MethodType.GETTER, "b_");
            b_builder = checkedPropSet(b_builder, PropertyObject.MethodType.SETTER, "b_");
            b_builder = checkedPropSet(b_builder, PropertyObject.MethodType.ACCESSOR, "_b");
            b_builder = checkedPropSet(b_builder, PropertyObject.MethodType.NONE, "b_");

            PropertyObject<ChangeLookup> b_po = b_builder.build(ChangeLookup.class);
            PropertyInfo<Integer> b_info = b_po.getProperty("foo", int.class);
            checkPI(b_info, ChangeLookup.class.getDeclaredField("b_foo"),
                    ChangeLookup.class.getDeclaredMethod("b_foo"),
                    ChangeLookup.class.getDeclaredMethod("b_foo", int.class),
                    ChangeLookup.class.getDeclaredMethod("foo_b"));
        }


    }

    @SuppressWarnings("unchecked")
    static <F> void checkPI(PropertyInfo<F> info,
                            Field fieldRef,
                            Method getterRef,
                            Method setterRef,
                            Method accessorRef) {
        assertNotNull("PropertyInfo parameter is null", info);

        if (fieldRef == null) {
            assertThat(info.getFieldRef(), OptionalMatchers.isEmpty());
        } else {
            assertThat(info.getFieldRef(), OptionalMatchers.hasValue(fieldRef));
        }

        if (getterRef == null) {
            assertThat(info.getGetterRef(), OptionalMatchers.isEmpty());
        } else {
            assertThat(info.getGetterRef(),
                    OptionalMatchers.hasValue((Invokable<Object, ? extends F>) info.getBaseType().method(getterRef)));
        }

        if (setterRef == null) {
            assertThat(info.getSetterRef(), OptionalMatchers.isEmpty());
        } else {
            assertThat(info.getSetterRef(),
                    OptionalMatchers.hasValue((Invokable) info.getBaseType().method(setterRef)));
        }

        if (accessorRef == null) {
            assertThat(info.getAccessorRef(), OptionalMatchers.isEmpty());
        } else {
            assertThat(info.getAccessorRef(),
                    OptionalMatchers.hasValue((Invokable) info.getBaseType().method(accessorRef)));
        }
    }


    /**
     * Test how the {@link net.navatwo.jfxproperties.PropertyObject.Builder} behaves with
     * Fields in a class.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static class FieldAccess {

        private PropertyObject.Builder builder;
        private PropertyObject<OnlyFields> po;
        private OnlyFields instance;

        // Harness class for testing
        @SuppressWarnings({"unused", "WeakerAccess"})
        public static class OnlyFields {

            public static int INIT_FOO = 30;
            public static double INIT_PRIM = 4;

            // defaults
            private final IntegerProperty foo = new SimpleIntegerProperty(INIT_FOO);

            private final double primFinal = INIT_PRIM;

            public double getPrimFinal() {
                return primFinal;
            }

            private double primNoSetter = INIT_PRIM;

            public double getPrimNoSetter() {
                return primNoSetter;
            }

            private double primNoGetter = INIT_PRIM;

            public void setPrimNoGetter(final double primNoGetter) {
                this.primNoGetter = primNoGetter;
            }

            private double prim = INIT_PRIM;

            public double getPrim() {
                return prim;
            }

            public void setPrim(final double prim) {
                this.prim = prim;
            }

            static final UUID CHECK_UUID = UUID.randomUUID();

            private ObjectProperty<UUID> id = new SimpleObjectProperty<>(CHECK_UUID);

            public Optional<UUID> getId() {
                return Optional.ofNullable(id.get());
            }

            public void setId(UUID id) {
                this.id.set(id);
            }

            public ObjectProperty<UUID> idProperty() {
                return id;
            }

            static final ObservableList<IdentifiedObject> DEFAULT_ID_OBJS = FXCollections.observableArrayList();
            private ListProperty<IdentifiedObject> idObjs = new SimpleListProperty<>(this, "idObjs", DEFAULT_ID_OBJS);

            public ObservableList<IdentifiedObject> getIdObjs() {
                return idObjs;
            }

            public ListProperty<IdentifiedObject> idObjsProperty() {
                return idObjs;
            }

            static final ObservableList<UUID> DEFAULT_NO_EXPLICIT_GETTER = FXCollections.observableArrayList(UUID.randomUUID());
            private ListProperty<UUID> noExplicitGetter = new SimpleListProperty<>(this, "noExplicitGetter", DEFAULT_NO_EXPLICIT_GETTER);

            public ListProperty<UUID> noExplicitGetterProperty() {
                return noExplicitGetter;
            }

            public void setNoExplicitGetter(List<? extends UUID> id) {
                noExplicitGetter.clear();
                noExplicitGetter.addAll(id);
            }


        }


        @Before
        public void setUp() throws Exception {
            builder = new PropertyObject.Builder();
            po = builder.build(OnlyFields.class);
            instance = spy(OnlyFields.class);
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Test
        public void nonFinal() throws Exception {

            // Non-final field
            DoublePropertyInfo pi_prim = po.getDoubleProperty("prim");
            checkPI(pi_prim,
                    OnlyFields.class.getDeclaredField("prim"),
                    OnlyFields.class.getDeclaredMethod("getPrim"),
                    OnlyFields.class.getDeclaredMethod("setPrim", double.class),
                    null);

            assertThat("Could not get value via PropertyInfo#getValue(T)",
                    pi_prim.getValueRaw(instance), equalTo(OnlyFields.INIT_PRIM));
            verify(instance, times(1)).getPrim();

            pi_prim.setValue(instance, 40.0);
            verify(instance, times(1)).setPrim(40.0);

            assertThat("Could not get value via PropertyInfo#getValue(T)",
                    pi_prim.getValueRaw(instance), equalTo(40.0));
            verify(instance, times(2)).getPrim();
        }

        @Test
        public void noGetter() throws Exception {

            // Non-final field no-setter
            DoublePropertyInfo pi_primNoSetter = po.getDoubleProperty("primNoGetter");
            checkPI(pi_primNoSetter, OnlyFields.class.getDeclaredField("primNoGetter"),
                    null,
                    OnlyFields.class.getDeclaredMethod("setPrimNoGetter", double.class),
                    null);

            pi_primNoSetter.setValue(instance, 40.0);
            verify(instance, times(1)).setPrimNoGetter(40.0);

            try {
                pi_primNoSetter.getValue(instance);
                fail("Should have thrown IllegalStateException as no getter exists");
            } catch (IllegalStateException ise) {
                // expected
            }
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Test
        public void noSetter() throws Exception {

            // Non-final field no-setter
            PropertyInfo<Double> pi_primNoSetter = po.getProperty("primNoSetter", double.class);
            checkPI(pi_primNoSetter,
                    OnlyFields.class.getDeclaredField("primNoSetter"),
                    OnlyFields.class.getDeclaredMethod("getPrimNoSetter"),
                    null, null);

            assertEquals("Could not get value via PropertyInfo#getValue(T)",
                    OnlyFields.INIT_PRIM, pi_primNoSetter.getValueRaw(instance), 0.0);
            verify(instance, times(1)).getPrimNoSetter();

            try {
                pi_primNoSetter.setValue(instance, 40.0);
                fail("Should have thrown exception -- no setter present");
            } catch (IllegalStateException ise) {
                // expected
            }
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Test
        public void finalField() throws Exception {

            DoublePropertyInfo pi_prim_final = po.getDoubleProperty("primFinal");
            checkPI(pi_prim_final, OnlyFields.class.getDeclaredField("primFinal"),
                    OnlyFields.class.getDeclaredMethod("getPrimFinal"), null, null);

            // Final field

            assertThat("Could not get value via PropertyInfo#getValue(T)",
                    pi_prim_final.getValueRaw(instance), equalTo(OnlyFields.INIT_PRIM));
            verify(instance).getPrimFinal();
            try {
                pi_prim_final.setValue(instance, 40.0);
                fail("Should have thrown IllegalStateException due to trying to set a final field");
            } catch (IllegalStateException ise) {
                // expected
            }

            assertThat("Could not get value via PropertyInfo#getValue(T)",
                    pi_prim_final.getValueRaw(instance), equalTo(OnlyFields.INIT_PRIM));
            verify(instance, times(2)).getPrimFinal();
        }

        @Test
        public void optionalProperty() throws Exception {

            PropertyInfo<UUID> pi_uuid = po.getProperty("id", UUID.class);
            checkPI(pi_uuid, OnlyFields.class.getDeclaredField("id"),
                    OnlyFields.class.getDeclaredMethod("getId"),
                    OnlyFields.class.getDeclaredMethod("setId", UUID.class),
                    OnlyFields.class.getDeclaredMethod("idProperty"));

            // Final field
            assertThat(pi_uuid.getValue(instance), OptionalMatchers.hasValue(OnlyFields.CHECK_UUID));
            verify(instance).getId();

            UUID toSet = UUID.randomUUID();
            pi_uuid.setValue(instance, toSet);
            verify(instance).setId(toSet);

            assertThat(pi_uuid.getValue(instance), OptionalMatchers.hasValue(toSet));
            verify(instance, times(2)).getId();
        }

        @Test
        public void listProperty() throws Exception {

            ListPropertyInfo<IdentifiedObject> pi_idObjs = po.getListProperty("idObjs", IdentifiedObject.class);
            checkPI(pi_idObjs, OnlyFields.class.getDeclaredField("idObjs"),
                    OnlyFields.class.getDeclaredMethod("getIdObjs"),
                    null,
                    OnlyFields.class.getDeclaredMethod("idObjsProperty"));

            // Final field
            assertThat(pi_idObjs.getValue(instance), OptionalMatchers.hasValue(OnlyFields.DEFAULT_ID_OBJS));
            verify(instance).getIdObjs();

            IdentifiedObject item1 = () -> new ReadOnlyObjectWrapper<>(UUID.randomUUID());
            IdentifiedObject item2 = () -> new ReadOnlyObjectWrapper<>(UUID.randomUUID());

            pi_idObjs.setValue(instance, FXCollections.observableArrayList(item1, item2));
            verify(instance).idObjsProperty();

            assertThat(pi_idObjs.getValue(instance), OptionalMatchers.hasValue(Matchers.contains(item1, item2)));
            verify(instance, times(2)).getIdObjs();

            ListProperty<IdentifiedObject> actualProp = pi_idObjs.getProperty(instance);
            assertThat(actualProp, equalTo(instance.idObjs));
        }


        @Test
        public void noExplicitGetterListProperty() throws Exception {

            // String property
            ListPropertyInfo<UUID> pi = po.getListProperty("noExplicitGetter", UUID.class);
            checkPI(pi, OnlyFields.class.getDeclaredField("noExplicitGetter"),
                    null,
                    OnlyFields.class.getMethod("setNoExplicitGetter", List.class),
                    OnlyFields.class.getMethod("noExplicitGetterProperty"));

            ObservableList<UUID> newContent = FXCollections.observableArrayList(UUID.randomUUID());

            assertEquals("Could not get value via PropertyInfo#getValue(T)",
                    OnlyFields.DEFAULT_NO_EXPLICIT_GETTER, pi.getValueRaw(instance));
            verify(instance).noExplicitGetterProperty();

            pi.setValue(instance, newContent);
            verify(instance).setNoExplicitGetter(newContent);

            assertEquals("Could not get value via PropertyInfo#getValue(T)",
                    newContent, pi.getValueRaw(instance));
            verify(instance, times(2)).noExplicitGetterProperty();
        }
    }

    /**
     * Test how the Builder works with interfaces
     */
    public static class Interface {

        private PropertyObject.Builder builder;
        private PropertyObject<Interface.WithProperty> po;
        private Interface.WithProperty instance;

        /**
         * Interface defined with a single property
         */
        public interface WithProperty {

            ReadOnlyLongProperty fooProperty();

            long getFoo();

            void setFoo(long value);


            StringProperty barProperty();

            String getBar();

            void setBar(String bar);


            ReadOnlyStringProperty readOnlyNoGetterProperty();

            StringProperty noGetterOrSetterProperty();


            String getBean();

            void setBean(String bean);
        }


        @Before
        public void setUp() throws Exception {
            builder = PropertyObject.builder();
            po = builder.build(WithProperty.class);
            instance = mock(Interface.WithProperty.class);
        }


        @Test
        public void longProperty() throws Exception {

            // Long property
            LongPropertyInfo pi = po.getLongProperty("foo");
            checkPI(pi,
                    null,
                    Interface.WithProperty.class.getMethod("getFoo"),
                    Interface.WithProperty.class.getMethod("setFoo", long.class),
                    Interface.WithProperty.class.getMethod("fooProperty"));

            when(instance.getFoo()).thenReturn(20L);

            assertEquals("Could not get value via PropertyInfo#getValue(T)",
                    20L, (long) pi.getValueRaw(instance));
            verify(instance, times(1)).getFoo();

            pi.setValue(instance, 40L);
            verify(instance).setFoo(40L);
            verify(instance, never()).fooProperty();
        }

        @Test
        public void stringProperty() throws Exception {

            // String property
            PropertyInfo<String> pi = po.getProperty("bar", String.class);
            checkPI(pi,
                    null,
                    Interface.WithProperty.class.getMethod("getBar"),
                    Interface.WithProperty.class.getMethod("setBar", String.class),
                    Interface.WithProperty.class.getMethod("barProperty"));

            when(instance.getBar()).thenReturn("wat");

            assertEquals("Could not get value via PropertyInfo#getValue(T)",
                    "wat", pi.getValueRaw(instance));
            verify(instance).getBar();

            pi.setValue(instance, "different");
            verify(instance).setBar("different");
            verify(instance, never()).barProperty();
        }

        @Test
        public void readOnlyNoGetter() throws Exception {

            // String property
            PropertyInfo<String> pi = po.getProperty("readOnlyNoGetter", String.class);
            checkPI(pi,
                    null, null, null,
                    Interface.WithProperty.class.getMethod("readOnlyNoGetterProperty"));

            ReadOnlyStringProperty testProp = spy(new SimpleStringProperty(instance,
                    "readOnlyNoGetter", "wat"));
            when(instance.readOnlyNoGetterProperty()).thenReturn(testProp);

            assertEquals("Could not get value via PropertyInfo#getValue(T)",
                    "wat", pi.getValueRaw(instance));
            verify(instance).readOnlyNoGetterProperty();
            verify(testProp).getValue();

            try {
                pi.setValue(instance, "different");
                fail("Should have thrown IllegalStateException as the field is read-only");
            } catch (IllegalStateException iae) {
                // expected
            }

            verify(instance, times(1)).readOnlyNoGetterProperty();
        }

        @Test
        public void propertyNoGetterOrSetter() throws Exception {

            // String property
            PropertyInfo<String> pi = po.getProperty("noGetterOrSetter", String.class);
            checkPI(pi, null, null, null,
                    Interface.WithProperty.class.getMethod("noGetterOrSetterProperty"));

            StringProperty testProp = spy(new SimpleStringProperty(instance,
                    "noGetterOrSetter", "wat"));
            when(instance.noGetterOrSetterProperty()).thenReturn(testProp);

            assertEquals("Could not get value via PropertyInfo#getValue(T)",
                    "wat", pi.getValueRaw(instance));
            verify(instance).noGetterOrSetterProperty();
            verify(testProp).getValue();

            pi.setValue(instance, "different");
            assertEquals("Did not properly set property value", "different", testProp.getValue());
            verify(instance, times(2)).noGetterOrSetterProperty();
        }

        @Test
        public void beanNoProperty() throws Exception {

            // String property
            PropertyInfo<String> pi = po.getProperty("bean", String.class);
            checkPI(pi, null,
                    Interface.WithProperty.class.getMethod("getBean"),
                    Interface.WithProperty.class.getMethod("setBean", String.class),
                    null);

            when(instance.getBean()).thenReturn("wat");

            assertEquals("Could not get value via PropertyInfo#getValue(T)",
                    "wat", pi.getValueRaw(instance));
            verify(instance).getBean();

            pi.setValue(instance, "different");
            verify(instance).setBean("different");
        }
    }

    /**
     * Test how inheritance relationships are handled
     */
    public static class Inheritance {


        public interface FooProperty {
            ReadOnlyLongProperty fooProperty();

            long getFoo();

            void setFoo(long value);
        }


        public interface BarProperty {

            StringProperty barProperty();

            String getBar();

            void setBar(String bar);

        }

        public interface MultiInterfaceProperty extends Inheritance.FooProperty, Inheritance.BarProperty {
            DoubleProperty multiProperty();

            double getMulti();

            void setMulti(double bar);
        }

        public class ImplFooProperty implements Inheritance.FooProperty {

            LongProperty fooProp = new SimpleLongProperty();

            @Override
            public ReadOnlyLongProperty fooProperty() {
                return fooProp;
            }

            @Override
            public long getFoo() {
                return fooProp.getValue();
            }

            @Override
            public void setFoo(final long value) {
                fooProp.setValue(value);
            }
        }

        private <T extends Inheritance.FooProperty> void checkFooInterface(Class<T> clazz, LongPropertyInfo pi, T instance) throws Exception {
            checkPI(pi,
                    null,
                    clazz.getMethod("getFoo"),
                    clazz.getMethod("setFoo", long.class),
                    clazz.getMethod("fooProperty"));

            when(instance.getFoo()).thenReturn(20L);

            assertEquals("Could not getValue value via PropertyInfo#getValue(T)",
                    20L, pi.get(instance));
            verify(instance).getFoo();

            pi.set(instance, 40L);
            verify(instance).setFoo(40L);
            verify(instance, never()).fooProperty();
        }

        private <T extends BarProperty> void checkBarInterface(Class<T> clazz, PropertyInfo<String> pi, T instance) throws Exception {
            checkPI(pi,
                    null,
                    clazz.getMethod("getBar"),
                    clazz.getMethod("setBar", String.class),
                    clazz.getMethod("barProperty"));

            when(instance.getBar()).thenReturn("wat");

            assertEquals("Could not getValue value via PropertyInfo#getValue(T)",
                    "wat", pi.getValueRaw(instance));
            verify(instance).getBar();

            pi.setValue(instance, "different");
            verify(instance).setBar("different");
            verify(instance, never()).barProperty();
        }

        @Test
        public void multiInterface() throws Exception {

            PropertyObject.Builder builder = PropertyObject.builder();
            PropertyObject<MultiInterfaceProperty> po = builder.build(MultiInterfaceProperty.class);
            MultiInterfaceProperty instance = mock(MultiInterfaceProperty.class);

            // Long property
            LongPropertyInfo fooPi = po.getLongProperty("foo");
            checkFooInterface(MultiInterfaceProperty.class, fooPi, instance);

            PropertyInfo<String> barPi = po.getProperty("bar", String.class);
            checkBarInterface(MultiInterfaceProperty.class, barPi, instance);

            PropertyInfo<Double> miPi = po.getProperty("multi", double.class);
            checkPI(miPi,
                    null,
                    MultiInterfaceProperty.class.getMethod("getMulti"),
                    MultiInterfaceProperty.class.getMethod("setMulti", double.class),
                    MultiInterfaceProperty.class.getMethod("multiProperty"));

            when(instance.getMulti()).thenReturn(20.0);

            assertEquals("Could not getValue value via PropertyInfo#getValue(T)",
                    20.0, miPi.getValueRaw(instance), 0.0);
            verify(instance).getFoo();

            miPi.setValue(instance, 40.0);
            verify(instance).setMulti(40.0);
            verify(instance, never()).multiProperty();
        }

        @Test
        public void classSingleInterface() throws Exception {

            PropertyObject.Builder builder = PropertyObject.builder();
            PropertyObject<Inheritance.ImplFooProperty> po = builder.build(ImplFooProperty.class);
            Inheritance.ImplFooProperty instance = spy(new Inheritance.ImplFooProperty());

            // Long property
            LongPropertyInfo fooPi = po.getLongProperty("foo");
            checkFooInterface(Inheritance.ImplFooProperty.class, fooPi, instance);
        }

    }

    /**
     * Test how properties are ignored (including inheritance)
     */
    public static class IgnorePropertyTest {

        private PropertyObject.Builder builder;
        private PropertyObject<Base> po;
        private Base instance;

        /**
         * Interface defined with a single property
         */
        @SuppressWarnings({"unused", "WeakerAccess", "UnusedReturnValue", "SameParameterValue"})
        public class Base {

            @IgnoreProperty
            private StringProperty propertyIgnored = new SimpleStringProperty(this, "bar");

            public StringProperty propertyIgnoredProperty() {
                return propertyIgnored;
            }

            public String getPropertyIgnored() {
                return this.propertyIgnored.getValue();
            }

            public void setPropertyIgnored(String propertyIgnored) {
                this.propertyIgnored.setValue(propertyIgnored);
            }


            @SuppressWarnings("UnusedReturnValue")
            @IgnoreProperty
            public String getBeanIgnoreGetter() {
                return null;
            }

            public void setBeanIgnoreGetter(String bean) {

            }

            public String getBeanIgnoreSetter() {
                return null;
            }

            @SuppressWarnings("SameParameterValue")
            @IgnoreProperty
            public void setBeanIgnoreSetter(String bean) {

            }

        }

        @SuppressWarnings("unused")
        public interface IgnoreBarProperty extends IgnorePropertyTest.BarProperty {

            @IgnoreProperty
            @Override
            StringProperty ignoredProperty();

        }

        @SuppressWarnings("unused")
        public interface BarProperty {
            StringProperty ignoredProperty();

            String getIgnored();

            void setIgnored(String bar);

        }

        @SuppressWarnings("unused")
        public class IgnoreFromInterface implements IgnorePropertyTest.IgnoreBarProperty {
            @Override
            public StringProperty ignoredProperty() {
                return null;
            }

            @Override
            public String getIgnored() {
                return null;
            }

            @Override
            public void setIgnored(final String bar) {

            }
        }

        @SuppressWarnings("unused")
        public class IgnoreFromClass implements IgnorePropertyTest.BarProperty {

            @IgnoreProperty
            @Override
            public StringProperty ignoredProperty() {
                return null;
            }

            @Override
            public String getIgnored() {
                return null;
            }

            @Override
            public void setIgnored(final String bar) {

            }
        }

        @Before
        public void setUp() throws Exception {
            builder = new PropertyObject.Builder();
            po = builder.build(Base.class);
            instance = spy(new Base());
        }

        @Test(expected = IllegalArgumentException.class)
        public void ignorePropertyField() throws Exception {
            // String property
            po.getProperty("propertyIgnored", String.class);
        }

        @Test(expected = IllegalArgumentException.class)
        public void ignoreClassFromInterface() throws Exception {
            PropertyObject.Builder builder = PropertyObject.builder();
            PropertyObject<IgnoreFromInterface> po = builder.build(IgnoreFromInterface.class);

            po.getProperty("ignored", String.class);
        }

        @Test(expected = IllegalArgumentException.class)
        public void ignoreInterfaceFromClass() throws Exception {
            PropertyObject.Builder builder = PropertyObject.builder();
            PropertyObject<IgnorePropertyTest.IgnoreFromClass> po = builder.build(IgnoreFromClass.class);

            po.getProperty("ignored", String.class);
        }

        @Test
        public void beanIgnoreGetter() throws Exception {
            // String property
            PropertyInfo<String> pi = po.getProperty("beanIgnoreGetter", String.class);
            checkPI(pi, null, null,
                    IgnorePropertyTest.Base.class.getMethod("setBeanIgnoreGetter", String.class),
                    null);

            try {
                pi.getValue(instance);
                fail("Should throw exception as no getter is present.");
            } catch (IllegalStateException ise) {
                // expected
            }
            verify(instance, never()).getBeanIgnoreGetter();

            pi.setValue(instance, "different");
            verify(instance).setBeanIgnoreGetter("different");
        }

        @Test
        public void beanIgnoreSetter() throws Exception {
            // String property
            PropertyInfo<String> pi = po.getProperty("beanIgnoreSetter", String.class);
            checkPI(pi, null,
                    IgnorePropertyTest.Base.class.getMethod("getBeanIgnoreSetter"),
                    null,
                    null);

            when(instance.getBeanIgnoreSetter()).thenReturn("wat");

            assertEquals("Wrong value returned from getter",
                    "wat", pi.getValueRaw(instance));
            verify(instance).getBeanIgnoreSetter();

            try {
                pi.setValue(instance, "different");
                fail("Should throw exception as no setter is present.");
            } catch (IllegalStateException ise) {
                // expected
            }
            verify(instance, never()).setBeanIgnoreSetter("different");
        }

    }

}
