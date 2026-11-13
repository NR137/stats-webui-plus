package de.nr137.statswebuiplus.neoforge.gametest;

import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import de.nr137.statswebuiplus.StatsWebuiPlus;
import de.nr137.statswebuiplus.gametest.LiveTests;

/** Registers the shared {@link LiveTests} with NeoForge: each test function, then a test instance for it. */
@EventBusSubscriber(modid = StatsWebuiPlus.MOD_ID)
public final class NeoForgeGameTests {
	// Ticks, not time: the test server ticks as fast as it can. Enough for a few cache refreshes of up to 5 s each
	// (LiveTests.Refresh); a test that passes ends as soon as its checks do.
	private static final int MAX_TICKS = 4_000;

	private NeoForgeGameTests() {
	}

	@SubscribeEvent
	static void registerFunctions(RegisterEvent event) {
		event.register(Registries.TEST_FUNCTION, helper ->
			LiveTests.all().forEach((name, test) -> helper.register(StatsWebuiPlus.id(name), test)));
	}

	@SubscribeEvent
	static void registerTests(RegisterGameTestsEvent event) {
		Holder<TestEnvironmentDefinition<?>> shared = event.registerEnvironment(StatsWebuiPlus.id("default"));
		// See LiveTests.ISOLATED: a batch of their own.
		Holder<TestEnvironmentDefinition<?>> isolated = event.registerEnvironment(StatsWebuiPlus.id("isolated"));
		for (String name : LiveTests.all().keySet()) {
			Holder<TestEnvironmentDefinition<?>> environment = LiveTests.ISOLATED.contains(name) ? isolated : shared;
			ResourceKey<Consumer<GameTestHelper>> function = ResourceKey.create(Registries.TEST_FUNCTION, StatsWebuiPlus.id(name));
			TestData<Holder<TestEnvironmentDefinition<?>>> data =
				new TestData<>(environment, Identifier.withDefaultNamespace("empty"), MAX_TICKS, 1, true);
			event.registerTest(StatsWebuiPlus.id(name), new FunctionGameTestInstance(function, data));
		}
	}
}
