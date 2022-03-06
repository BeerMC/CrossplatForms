package dev.projectg.crossplatforms.config.keyedserializer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dev.projectg.crossplatforms.config.serializer.KeyedTypeSerializer;
import dev.projectg.crossplatforms.utils.ConfigurateUtils;
import dev.projectg.crossplatforms.utils.FileUtils;
import io.leangen.geantyref.TypeToken;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class KeyedTypeSerializerTest {

    private final KeyedTypeSerializer<Message> messageSerializer = new KeyedTypeSerializer<>();
    private final YamlConfigurationLoader loader;

    @TempDir
    private static File directory;

    public KeyedTypeSerializerTest() throws IOException {
        messageSerializer.registerSimpleType("message", String.class, SingleMessage::new);
        messageSerializer.registerType("messages", MultiMessage.class);

        File config = FileUtils.fileOrCopiedFromResource(new File(directory, "KeyedTypeConfig.yml"));
        YamlConfigurationLoader.Builder loaderBuilder = ConfigurateUtils.loaderBuilder(config);
        loaderBuilder.defaultOptions(opts -> (opts.serializers(builder -> builder.registerExact(new TypeToken<>() {}, messageSerializer))));
        loader = loaderBuilder.build();
    }

    @Test
    public void testDeserialize() throws ConfigurateException {
        ConfigurationNode actions = loader.load().node("actions");
        Assertions.assertFalse(actions.virtual());
        Assertions.assertTrue(actions.isMap());

        Map<String, Message> actualMessages = actions.get(new TypeToken<>() {});

        SingleMessage single = new SingleMessage("[WARN] Hello");
        MultiMessage list = new MultiMessage("[INFO]", ImmutableList.of("One", "Two", "Three"));
        Map<String, Message> expectedMessages = ImmutableMap.of(SingleMessage.IDENTIFIER, single, MultiMessage.IDENTIFIER, list);

        Assertions.assertEquals(expectedMessages, actualMessages);
    }

    @Test
    public void testSerialize() throws ConfigurateException {
        ConfigurationNode actions = loader.load().node("actions");
        ConfigurationNode copy = actions.copy();

        Map<String, Message> actualMessages = actions.get(new TypeToken<>() {});
        Objects.requireNonNull(actualMessages);
        copy.set(new TypeToken<>() {}, actualMessages);
        Assertions.assertEquals(actions, copy);

        Map<String, Message> modifiedMessages = new HashMap<>(actualMessages);
        modifiedMessages.put(SingleMessage.IDENTIFIER, new SingleMessage("greetings"));
        copy.set(new TypeToken<>() {}, modifiedMessages);
        Assertions.assertNotEquals(actions, copy);
    }
}
