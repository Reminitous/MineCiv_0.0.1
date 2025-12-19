package net.reminitous.mineciv.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.network.packet.MonumentAddPlayerC2SPacket;
import net.reminitous.mineciv.network.packet.MonumentRemovePlayerC2SPacket;

public class ModMessages {
    private static SimpleChannel INSTANCE;
    private static int packetId = 0;

    private static int id() {
        return packetId++;
    }

    public static void register() {
        SimpleChannel net = NetworkRegistry.ChannelBuilder
                .named(ResourceLocation.fromNamespaceAndPath(MineCiv.MOD_ID, "messages"))
                .networkProtocolVersion(() -> "1.0")
                .clientAcceptedVersions(s -> true)
                .serverAcceptedVersions(s -> true)
                .simpleChannel();

        INSTANCE = net;

        net.messageBuilder(MonumentAddPlayerC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(MonumentAddPlayerC2SPacket::new)
                .encoder(MonumentAddPlayerC2SPacket::toBytes)
                .consumerMainThread(MonumentAddPlayerC2SPacket::handle)
                .add();

        net.messageBuilder(MonumentRemovePlayerC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(MonumentRemovePlayerC2SPacket::new)
                .encoder(MonumentRemovePlayerC2SPacket::toBytes)
                .consumerMainThread(MonumentRemovePlayerC2SPacket::handle)
                .add();
    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}