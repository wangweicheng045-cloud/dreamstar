package dev.dreamstar;

import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Dreamstar.ID)
public final class DebugCommands {
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dreamstar")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("scroll").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    ItemStack scroll = new ItemStack(ItemRegistry.SCROLL.get());
                    ISpellContainer.createScrollContainer(Dreamstar.DREAM_STAR.get(), 1, scroll);
                    if (!player.addItem(scroll)) player.drop(scroll, false);
                    return 1;
                }))
                .then(Commands.literal("preview").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    var domain = Dreamstar.DOMAIN.get().create(player.level());
                    if (domain == null) return 0;
                    domain.setPos(player.position());
                    domain.initialize(Dreamstar.DOMAIN_RADIUS);
                    player.level().addFreshEntity(domain);
                    context.getSource().sendSuccess(() -> Component.translatable("command.dreamstar.preview"), false);
                    return 1;
                })));
    }
}
