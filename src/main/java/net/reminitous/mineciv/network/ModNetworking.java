package net.reminitous.mineciv.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.PayloadChannel;
import net.minecraftforge.network.NetworkDirection;
import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.network.packet.MonumentAddPlayerC2SPayload;

public class ModNetworking {

    public static final PayloadChannel CHANNEL = Channel
            .builder()
            .name(new ResourceLocation(MineCiv.MOD_ID, "main"))
            .networkProtocolVersion(1)
            .acceptedVersions(Channel.VersionTest.exact(1))
            .simple();

    public static void register() {
        CHANNEL.register(
                MonumentAddPlayerC2SPayload.TYPE,
                MonumentAddPlayerC2SPayload.STREAM_CODEC,
                MonumentAddPlayerC2SPayload::handle,
                NetworkDirection.PLAY_TO_SERVER
        );
    }
}
