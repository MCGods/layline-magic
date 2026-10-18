package com.example.laylines.event;

import com.example.laylines.api.LaylineGraphApi;
import com.example.laylines.network.LaylineNode;
import com.example.laylines.network.LaylineNode.AttributeType;
import com.example.laylines.saveddata.LaylinesData;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public class LaylineCommandHandler {

        @SubscribeEvent
        public void onRegisterCommands(RegisterCommandsEvent event) {
                var currentValueArgument = Commands.argument("currentValue", FloatArgumentType.floatArg(0.0F))
                                .executes(context -> setPool(
                                                context.getSource(),
                                                UuidArgument.getUuid(context, "uuid"),
                                                StringArgumentType.getString(context, "attribute"),
                                                FloatArgumentType.getFloat(context, "capacity"),
                                                FloatArgumentType.getFloat(context, "currentValue")));
                var capacityArgument = Commands.argument("capacity", FloatArgumentType.floatArg(0.0F))
                                .then(currentValueArgument);
                var attributeArgument = Commands.argument("attribute", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                Stream.of(AttributeType.values()).map(Enum::name), builder))
                                .then(capacityArgument);
                var uuidArgument = Commands.argument("uuid", UuidArgument.uuid())
                                .then(attributeArgument);

                event.getDispatcher().register(Commands.literal("laylines")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("pool")
                                                .then(Commands.literal("set")
                                                                .then(uuidArgument))));
        }

        private static int setPool(CommandSourceStack source, UUID id, String attributeName, float capacity,
                        float currentValue) {
                AttributeType type;
                try {
                        type = AttributeType.valueOf(attributeName.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                        source.sendFailure(Component.literal("Unknown layline attribute '" + attributeName
                                        + "'. Valid attributes: " + String.join(", ",
                                                        Stream.of(AttributeType.values()).map(Enum::name).toList())));
                        return 0;
                }

                if (!Float.isFinite(capacity) || !Float.isFinite(currentValue) || currentValue > capacity) {
                        source.sendFailure(
                                        Component.literal("Pool values must be finite, non-negative, and current value "
                                                        + "must not exceed capacity."));
                        return 0;
                }

                Optional<LaylineNode> nodeOpt = LaylineGraphApi.getNode(source.getLevel(), id);
                if (nodeOpt.isEmpty()) {
                        source.sendFailure(Component.literal("No layline found with UUID " + id + "."));
                        return 0;
                }

                LaylineNode node = nodeOpt.get();
                node.setCapacity(type, capacity);
                node.setCurrentValue(type, currentValue);
                LaylinesData.get(source.getServer()).markDirty();
                source.sendSuccess(() -> Component.literal("Set " + type.name() + " pool for layline " + id
                                + " to " + currentValue + "/" + capacity + "."), true);
                return 1;
        }
}
