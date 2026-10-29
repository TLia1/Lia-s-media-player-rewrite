package com.lia.mediaplayer.platform.mc;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.ui.command.ShowCommand;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * The client command tree, for a dispatcher of either loader's source type.
 *
 * <p>NeoForge hands out a dispatcher of {@code CommandSourceStack} and Fabric one of its own
 * source; they share no useful ancestor, and the commands need nothing from the source but a
 * way to report a failure. So the source stays a type variable and each loader passes that one
 * thing.
 */
public final class McCommands {

    private McCommands() {}

    /**
     * {@code /show <type> <url> [newPlayer]}. The link is the rest of the line, so it needs no
     * quotes; a trailing {@code true} or {@code false} is the {@code newPlayer} flag.
     */
    public static <S> LiteralArgumentBuilder<S> show(
            ShowCommand command, BiConsumer<CommandContext<S>, Component> fail) {
        return LiteralArgumentBuilder.<S>literal("show")
                .then(RequiredArgumentBuilder.<S, String>argument("type", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(ShowCommand.types(), builder))
                        .then(RequiredArgumentBuilder.<S, String>argument("url", StringArgumentType.greedyString())
                                .executes(context -> {
                                    Optional<Text> refused = command.run(
                                            StringArgumentType.getString(context, "type"),
                                            StringArgumentType.getString(context, "url"));
                                    refused.ifPresent(reason -> fail.accept(context, McText.toComponent(reason)));
                                    return refused.isPresent() ? 0 : 1;
                                })));
    }
}
