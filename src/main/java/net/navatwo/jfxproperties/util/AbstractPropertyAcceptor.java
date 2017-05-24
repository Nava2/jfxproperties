package net.navatwo.jfxproperties.util;

import net.navatwo.jfxproperties.*;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Provides a no-implementation implementation of the {@link PropertyAcceptor}
 */
@ParametersAreNonnullByDefault
public class AbstractPropertyAcceptor implements PropertyAcceptor {

    @Override
    public void acceptInt(IntegerPropertyInfo pi) {

    }

    @Override
    public void acceptLong(LongPropertyInfo pi) {

    }

    @Override
    public void acceptDouble(DoublePropertyInfo pi) {

    }

    @Override
    public void acceptEnum(ObjectPropertyInfo<? extends Enum<?>> pi) {

    }

    @Override
    public void acceptObject(ObjectPropertyInfo<?> pi) {

    }

    @Override
    public void acceptList(ListPropertyInfo<?> pi) {

    }

    @Override
    public void acceptSet(SetPropertyInfo<?> pi) {

    }

    @Override
    public void acceptMap(final MapPropertyInfo<?, ?> pi) {

    }
}
