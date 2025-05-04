//package net.Gabou.projectatmosphere.modules.temperature.spike.commands;
//
//import com.mojang.brigadier.CommandDispatcher;
//import com.mojang.brigadier.arguments.IntegerArgumentType;
//import net.Gabou.projectatmosphere.manager.AtmosphereManager;
//import net.Gabou.projectatmosphere.modules.temperature.TemperatureModule;
//import net.Gabou.projectatmosphere.modules.temperature.spike.SpikeManager;
//import net.minecraft.commands.CommandSourceStack;
//import net.minecraft.commands.Commands;
//import net.minecraft.commands.arguments.ResourceLocationArgument;
//import net.minecraft.network.chat.Component;
//import net.minecraft.resources.ResourceLocation;
//import net.minecraft.server.level.ServerLevel;
//
//import java.util.Random;
//import java.util.function.Supplier;
//
//public class SpikeCommands {
//    private static final Random RAND = new Random();
//
//    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
//        dispatcher.register(Commands.literal("temperature")
//                .then(Commands.literal("spike")
//                        // /temperature spike clear
//                        .then(Commands.literal("clear")
//                                .executes(ctx -> {
//                                    ServerLevel world = ctx.getSource().getPlayerOrException().serverLevel();
//                                    SpikeManager.clearSpikes(world);
//                                    ctx.getSource().sendSuccess(
//                                            () ->
//                                            Component.literal("All spikes cleared."), true);
//                                    return 1;
//                                })
//                        )
//                        // /temperature spike ongoing <biome>
//                        .then(Commands.literal("ongoing")
//                                .then(Commands.argument("biome", ResourceLocationArgument.id())
//                                        .executes(ctx -> {
//                                            ServerLevel world = ctx.getSource().getPlayerOrException().serverLevel();
//                                            ResourceLocation biome = ctx.getArgument("biome", ResourceLocation.class);
//                                            SpikeManager.applyOngoingSpike(world, biome);
//                                            ctx.getSource().sendSuccess(
//                                                    () -> Component.literal("Applied ongoing spike for biome " + biome), false);
//                                            return 1;
//                                        })
//                                )
//                        )
//                        // /temperature spike start [magnitude] [biome]
//                        .then(Commands.literal("start")
//                                .then(Commands.argument("magnitude", IntegerArgumentType.integer(1))
//                                        .then(Commands.argument("biome", ResourceLocationArgument.id())
//                                                .executes(ctx -> {
//                                                    ServerLevel world = ctx.getSource().getPlayerOrException().serverLevel();
//                                                    int mag = ctx.getArgument("magnitude", Integer.class);
//                                                    ResourceLocation biome = ctx.getArgument("biome", ResourceLocation.class);
//                                                    mod.startNewSpike(world, biome, mag);
//                                                    ctx.getSource().sendSuccess(
//                                                            ()->
//                                                            Component.literal("Started new spike of magnitude " + mag +
//                                                                    " for biome " + biome), true);
//                                                    return 1;
//                                                })
//                                        )
//                                )
//                                // overload: pick random magnitude if none given
//                                .executes(ctx -> {
//                                    ServerLevel world = ctx.getSource().getPlayerOrException().serverLevel();
//                                    int mag = 1 + RAND.nextInt(5); // e.g. random 1–5
//                                    ResourceLocation biome = ctx.getSource().getPlayerOrException().serverLevel().getBiome(ctx.getSource().getPlayerOrException()
//                                            .blockPosition()).unwrapKey().get().location();
//                                    SpikeManager.startNewSpike(world, biome, mag);
//                                    ctx.getSource().sendSuccess(
//                                            () ->
//                                            Component.literal("Started random spike of magnitude " + mag +
//                                                    " for your current biome"), true);
//                                    return 1;
//                                })
//                        )
//                )
//        );
//    }
//}
