package net.tkg.RenaissanceLib;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.tkg.RenaissanceLib.attachment.AttachmentOverrides;
import net.tkg.RenaissanceLib.client.AttachmentAnimationManager;
import net.tkg.RenaissanceLib.client.ScopeShaderRegistry;
import net.tkg.RenaissanceLib.client.input.AttachmentWheelKey;
import net.tkg.RenaissanceLib.client.input.WeaponSelectKey;
import net.tkg.RenaissanceLib.network.NetworkHandler;
import net.tkg.RenaissanceLib.client.ShaderManager;
import org.slf4j.Logger;

@Mod(RenaissanceLibMod.MOD_ID)
public class RenaissanceLibMod {
    public static final String MOD_ID = "renaissance_lib";

    public static final Logger LOGGER = LogUtils.getLogger();

    public RenaissanceLibMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);
        modEventBus.addListener(this::addCreative);

        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, RenaissanceConfig.CLIENT_SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {

        event.enqueueWork(AttachmentOverrides::register);
        event.enqueueWork(NetworkHandler::register);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
        }

        @SubscribeEvent
        public static void onRegisterTooltipFactories(
                net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent event) {
            event.register(
                    net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelStatsTooltip.class,
                    net.tkg.RenaissanceLib.client.underbarrel.ClientUnderbarrelStatsTooltip::new);
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(AttachmentWheelKey.OPEN_WHEEL);
            event.register(WeaponSelectKey.SELECT_WEAPON);
        }

        @SubscribeEvent
        public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {

            event.registerReloadListener(new ScopeShaderRegistry());

            event.registerReloadListener(new SimplePreparableReloadListener<Void>() {
                @Override
                protected Void prepare(ResourceManager rm, ProfilerFiller p) {
                    return null;
                }

                @Override
                protected void apply(Void v, ResourceManager rm, ProfilerFiller p) {
                    ShaderManager.clearCache();
                    net.tkg.RenaissanceLib.client.refit.RefitBlur.close();

                    AttachmentAnimationManager.clearCache();
                }
            });
        }
    }
}
