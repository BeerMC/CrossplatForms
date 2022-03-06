package dev.projectg.crossplatforms.config.serializer;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * For use with {@link ValuedTypeSerializer}. Allows child classes to be successfully deserializes and serialized using a
 * String field representing the exact type to use when serializing.
 */
@ConfigSerializable
public abstract class ValuedType {

    public static final String KEY = "type";

    @Setting(KEY)
    protected String type = null;

    /**
     * Only to be used by Configurate.
     */
    protected ValuedType() {

    }

    protected ValuedType(@Nonnull String type) {
        this.type = Objects.requireNonNull(type);
    }

    public String identifier() {
        return type;
    }

    /**
     * Checks if the types of two different {@link ValuedType}'s are the same.
     */
    public boolean sameType(ValuedType other) {
        return Objects.equals(identifier(), other.identifier());
    }

    @Override
    public String toString() {
        return "ValuedType{" + "type='" + identifier() + '}';
    }
}
