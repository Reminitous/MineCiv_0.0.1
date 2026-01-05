package net.reminitous.mineciv.net;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.net.pkt.C2S_CreateCivPacket;
import net.reminitous.mineciv.net.pkt.S2C_OpenCreateCivScreenPacket;

public final class Network {

    private static final int PROTOCOL = 1;

    public static final SimpleChannel CH = ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(MineCiv.MOD_ID, "main"))
            .acceptedVersions((status, version) -> version == PROTOCOL)
            .networkProtocolVersion(PROTOCOL)
            .simpleChannel();


    private static int id = 0;

    public static void init() {
        CH.messageBuilder(S2C_OpenCreateCivScreenPacket.class, id++)
                .encoder(S2C_OpenCreateCivScreenPacket::encode)
                .decoder(S2C_OpenCreateCivScreenPacket::decode)
                .consumerMainThread(S2C_OpenCreateCivScreenPacket::handle)
                .add();

        CH.messageBuilder(C2S_CreateCivPacket.class, id++)
                .encoder(C2S_CreateCivPacket::encode)
                .decoder(C2S_CreateCivPacket::decode)
                .consumerMainThread(C2S_CreateCivPacket::handle)
                .add();
    }
}
