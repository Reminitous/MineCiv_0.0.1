import net.minecraftforge.network.SimpleChannel

public class ModNetworking {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL =
            NetworkRegistry.ChannelBuilder
                    .named(new ResourceLocation("yourmodid", "main"))
                    .networkProtocolVersion(() -> PROTOCOL_VERSION)
                    .clientAcceptedVersions(PROTOCOL_VERSION::equals)
                    .serverAcceptedVersions(PROTOCOL_VERSION::equals)
                    .simpleChannel();

    public static void register() {
        // register packets here
    }
}
