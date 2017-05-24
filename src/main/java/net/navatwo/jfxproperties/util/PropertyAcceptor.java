package net.navatwo.jfxproperties.util;

import com.google.common.reflect.TypeToken;
import net.navatwo.jfxproperties.*;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Provides a visitor interface for working with {@link PropertyInfo} values.
 */
@ParametersAreNonnullByDefault
public interface PropertyAcceptor {

    void acceptInt(IntegerPropertyInfo pi);

    void acceptLong(LongPropertyInfo pi);

    void acceptDouble(DoublePropertyInfo pi);

    void acceptEnum(ObjectPropertyInfo<? extends Enum<?>> pi);

    void acceptObject(ObjectPropertyInfo<?> pi);

    void acceptList(ListPropertyInfo<?> pi);

    void acceptSet(SetPropertyInfo<?> pi);

    void acceptMap(MapPropertyInfo<?, ?> pi);

    /**
     * Performs the work to discern the underlying type of the {@link PropertyInfo} and calls the appropriate
     * {@code accept*} method on {@link PropertyAcceptor}.
     *
     * @param info     Non-{@code null} property info value
     * @param acceptor
     */
    static void accept(PropertyInfo<?> info, PropertyAcceptor acceptor) {
        checkNotNull(info, "info == null");
        checkNotNull(acceptor, "acceptor == null");

        TypeToken<?> type = info.getPropertyType();
        Class<?> rawType = type.unwrap().getRawType();
        if (rawType == int.class) {
            acceptor.acceptInt((IntegerPropertyInfo) info);
        } else if (rawType == long.class) {
            acceptor.acceptLong((LongPropertyInfo) info);
        } else if (rawType == double.class) {
            acceptor.acceptDouble((DoublePropertyInfo) info);
        } else if (type.isSubtypeOf(List.class)) {
            acceptor.acceptList((ListPropertyInfo<?>) info);
        } else if (type.isSubtypeOf(Set.class)) {
            acceptor.acceptSet((SetPropertyInfo<?>) info);
        } else if (type.isSubtypeOf(Map.class)) {
            acceptor.acceptMap((MapPropertyInfo<?, ?>) info);
        } else {
            acceptor.acceptObject((ObjectPropertyInfo<?>) info);
        }
    }
}
