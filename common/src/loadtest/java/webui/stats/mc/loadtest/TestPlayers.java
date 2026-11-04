package webui.stats.mc.loadtest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

import java.util.UUID;

/**
 * Players logged in the way a real client is (PlayerList.placeNewPlayer), over a connection in memory instead of the
 * network. Used by the game tests and the load test; Minecraft's own makeMockServerPlayerInLevel is deprecated for
 * removal without a successor.
 */
public final class TestPlayers {
	private TestPlayers() {
	}

	/** A logged-in player; {@link #answer} keeps them connected for longer than the 15 s keep-alive timeout. */
	public record Joined(ServerPlayer player, EmbeddedChannel channel) {
	}

	public static Joined join(MinecraftServer server, ServerLevel level, UUID uuid, String name) {
		GameProfile profile = new GameProfile(uuid, name);
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
		ServerPlayer player = new ServerPlayer(server, level, profile, cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		EmbeddedChannel channel = new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		return new Joined(player, channel);
	}

	/**
	 * Server thread: throws away what the server sent the player (it would pile up in memory) and answers its
	 * keep-alive requests, as a client would.
	 */
	public static void answer(Joined joined) {
		Object packet;
		while ((packet = joined.channel().readOutbound()) != null) {
			if (packet instanceof ClientboundKeepAlivePacket keepAlive && joined.player().connection != null) {
				joined.player().connection.handleKeepAlive(new ServerboundKeepAlivePacket(keepAlive.getId()));
			}
		}
	}
}
