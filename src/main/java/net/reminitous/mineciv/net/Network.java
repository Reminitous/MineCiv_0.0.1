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

        CH.messageBuilder(net.reminitous.mineciv.net.pkt.S2C_OpenManageCivScreenPacket.class, id++)
                .encoder(net.reminitous.mineciv.net.pkt.S2C_OpenManageCivScreenPacket::encode)
                .decoder(net.reminitous.mineciv.net.pkt.S2C_OpenManageCivScreenPacket::decode)
                .consumerMainThread(net.reminitous.mineciv.net.pkt.S2C_OpenManageCivScreenPacket::handle)
                .add();

        CH.messageBuilder(net.reminitous.mineciv.net.pkt.C2S_RenameCivPacket.class, id++)
                .encoder(net.reminitous.mineciv.net.pkt.C2S_RenameCivPacket::encode)
                .decoder(net.reminitous.mineciv.net.pkt.C2S_RenameCivPacket::decode)
                .consumerMainThread(net.reminitous.mineciv.net.pkt.C2S_RenameCivPacket::handle)
                .add();

        CH.messageBuilder(net.reminitous.mineciv.net.pkt.C2S_KickMemberPacket.class, id++)
                .encoder(net.reminitous.mineciv.net.pkt.C2S_KickMemberPacket::encode)
                .decoder(net.reminitous.mineciv.net.pkt.C2S_KickMemberPacket::decode)
                .consumerMainThread(net.reminitous.mineciv.net.pkt.C2S_KickMemberPacket::handle)
                .add();

        CH.messageBuilder(net.reminitous.mineciv.net.pkt.S2C_OpenInvitePopupPacket.class, id++)
                .encoder(net.reminitous.mineciv.net.pkt.S2C_OpenInvitePopupPacket::encode)
                .decoder(net.reminitous.mineciv.net.pkt.S2C_OpenInvitePopupPacket::decode)
                .consumerMainThread(net.reminitous.mineciv.net.pkt.S2C_OpenInvitePopupPacket::handle)
                .add();

        CH.messageBuilder(net.reminitous.mineciv.net.pkt.C2S_InvitePlayerPacket.class, id++)
                .encoder(net.reminitous.mineciv.net.pkt.C2S_InvitePlayerPacket::encode)
                .decoder(net.reminitous.mineciv.net.pkt.C2S_InvitePlayerPacket::decode)
                .consumerMainThread(net.reminitous.mineciv.net.pkt.C2S_InvitePlayerPacket::handle)
                .add();

        CH.messageBuilder(net.reminitous.mineciv.net.pkt.C2S_RespondInvitePacket.class, id++)
                .encoder(net.reminitous.mineciv.net.pkt.C2S_RespondInvitePacket::encode)
                .decoder(net.reminitous.mineciv.net.pkt.C2S_RespondInvitePacket::decode)
                .consumerMainThread(net.reminitous.mineciv.net.pkt.C2S_RespondInvitePacket::handle)
                .add();

        CH.messageBuilder(net.reminitous.mineciv.net.pkt.C2S_RequestManageCivDataPacket.class, id++)
                .encoder(net.reminitous.mineciv.net.pkt.C2S_RequestManageCivDataPacket::encode)
                .decoder(net.reminitous.mineciv.net.pkt.C2S_RequestManageCivDataPacket::decode)
                .consumerMainThread(net.reminitous.mineciv.net.pkt.C2S_RequestManageCivDataPacket::handle)
                .add();

        CH.messageBuilder(net.reminitous.mineciv.net.pkt.C2S_CancelInvitePacket.class, id++)
                .encoder(net.reminitous.mineciv.net.pkt.C2S_CancelInvitePacket::encode)
                .decoder(net.reminitous.mineciv.net.pkt.C2S_CancelInvitePacket::decode)
                .consumerMainThread(net.reminitous.mineciv.net.pkt.C2S_CancelInvitePacket::handle)
                .add();

        CH.messageBuilder(net.reminitous.mineciv.net.pkt.C2S_LeaveCivPacket.class, id++)
                .encoder(net.reminitous.mineciv.net.pkt.C2S_LeaveCivPacket::encode)
                .decoder(net.reminitous.mineciv.net.pkt.C2S_LeaveCivPacket::decode)
                .consumerMainThread(net.reminitous.mineciv.net.pkt.C2S_LeaveCivPacket::handle)
                .add();

        CH.messageBuilder(net.reminitous.mineciv.net.pkt.C2S_DisbandCivPacket.class, id++)
                .encoder(net.reminitous.mineciv.net.pkt.C2S_DisbandCivPacket::encode)
                .decoder(net.reminitous.mineciv.net.pkt.C2S_DisbandCivPacket::decode)
                .consumerMainThread(net.reminitous.mineciv.net.pkt.C2S_DisbandCivPacket::handle)
                .add();

        CH.messageBuilder(net.reminitous.mineciv.net.pkt.C2S_ClaimCurrentChunkPacket.class, id++)
                .encoder(net.reminitous.mineciv.net.pkt.C2S_ClaimCurrentChunkPacket::encode)
                .decoder(net.reminitous.mineciv.net.pkt.C2S_ClaimCurrentChunkPacket::decode)
                .consumerMainThread(net.reminitous.mineciv.net.pkt.C2S_ClaimCurrentChunkPacket::handle)
                .add();

        CH.messageBuilder(net.reminitous.mineciv.net.pkt.C2S_ClaimAdjacentToMonumentPacket.class, id++)
                .encoder(net.reminitous.mineciv.net.pkt.C2S_ClaimAdjacentToMonumentPacket::encode)
                .decoder(net.reminitous.mineciv.net.pkt.C2S_ClaimAdjacentToMonumentPacket::decode)
                .consumerMainThread(net.reminitous.mineciv.net.pkt.C2S_ClaimAdjacentToMonumentPacket::handle)
                .add();

        CH.messageBuilder(net.reminitous.mineciv.net.pkt.C2S_ProposeWarPacket.class, id++)
                .encoder(net.reminitous.mineciv.net.pkt.C2S_ProposeWarPacket::encode)
                .decoder(net.reminitous.mineciv.net.pkt.C2S_ProposeWarPacket::decode)
                .consumerMainThread(net.reminitous.mineciv.net.pkt.C2S_ProposeWarPacket::handle)
                .add();

        CH.messageBuilder(net.reminitous.mineciv.net.pkt.S2C_OpenWarProposalScreenPacket.class, id++)
                .encoder(net.reminitous.mineciv.net.pkt.S2C_OpenWarProposalScreenPacket::encode)
                .decoder(net.reminitous.mineciv.net.pkt.S2C_OpenWarProposalScreenPacket::decode)
                .consumerMainThread(net.reminitous.mineciv.net.pkt.S2C_OpenWarProposalScreenPacket::handle)
                .add();

        CH.messageBuilder(net.reminitous.mineciv.net.pkt.C2S_AcceptWarPacket.class, id++)
                .encoder(net.reminitous.mineciv.net.pkt.C2S_AcceptWarPacket::encode)
                .decoder(net.reminitous.mineciv.net.pkt.C2S_AcceptWarPacket::decode)
                .consumerMainThread(net.reminitous.mineciv.net.pkt.C2S_AcceptWarPacket::handle)
                .add();

        CH.messageBuilder(net.reminitous.mineciv.net.pkt.C2S_DeclineWarPacket.class, id++)
                .encoder(net.reminitous.mineciv.net.pkt.C2S_DeclineWarPacket::encode)
                .decoder(net.reminitous.mineciv.net.pkt.C2S_DeclineWarPacket::decode)
                .consumerMainThread(net.reminitous.mineciv.net.pkt.C2S_DeclineWarPacket::handle)
                .add();

    }
}
