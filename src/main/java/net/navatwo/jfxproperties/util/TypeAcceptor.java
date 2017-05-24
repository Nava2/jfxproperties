package net.navatwo.jfxproperties.util;

import com.google.common.reflect.TypeToken;
import javafx.collections.ObservableMap;

import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Provides a visitor interface for working with "untyped" objects
 */
@ParametersAreNonnullByDefault
public interface TypeAcceptor {

    void acceptInt();

    void acceptLong();

    void acceptDouble();

    void acceptObject(TypeToken<?> type);

    void acceptList(TypeToken<?> elementType);

    void acceptSet(TypeToken<?> elementType);

    void acceptMap(TypeToken<?> keyType, TypeToken<?> valueType);

    /**
     * Performs a "visit" for the type based on what the {@code type} parameter holds, calling the appropriate
     * {@code accept*} call.
     *
     * @param type     Type to breakdown and visit
     * @param acceptor Acceptor that will accept the calls.
     */
    static void accept(TypeToken<?> type, TypeAcceptor acceptor) {
        checkNotNull(type, "type == null");
        checkNotNull(acceptor, "acceptor == null");

        Class<?> rawType = type.unwrap().getRawType();
        if (rawType == int.class) {
            acceptor.acceptInt();
        } else if (rawType == long.class) {
            acceptor.acceptLong();
        } else if (rawType == double.class) {
            acceptor.acceptDouble();
        } else if (type.isSubtypeOf(List.class)) {
            TypeToken<?> elementType = type.resolveType(List.class.getTypeParameters()[0]);
            acceptor.acceptList(elementType);
        } else if (type.isSubtypeOf(Set.class)) {
            TypeToken<?> elementType = type.resolveType(Set.class.getTypeParameters()[0]);
            acceptor.acceptSet(elementType);
        } else if (type.isSubtypeOf(ObservableMap.class)) {
            Type[] params = Map.class.getTypeParameters();
            TypeToken<?> keyType = type.resolveType(params[0]);
            TypeToken<?> valueType = type.resolveType(params[1]);
            acceptor.acceptMap(keyType, valueType);
        } else {
            acceptor.acceptObject(type);
        }
    }
}
