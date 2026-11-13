package de.nr137.statswebuiplus.assets;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Set;

/**
 * The downloaded client JAR's code, for what only it knows (the shapes of entity models): a class loader that asks the
 * running server first, so the game's shared classes are the server's own, and takes the client-only ones (models,
 * rendering) from the client JAR. Our reading code (package assets.client) is defined inside it, so it can use them.
 * Nothing of the client runs outside this loader; it's closed when done.
 */
final class ClientClasses extends URLClassLoader {
	private static final String OWN = "de.nr137.statswebuiplus.assets.client.";

	private ClientClasses(Path clientJar, ClassLoader server) throws IOException {
		super(new URL[] {clientJar.toUri().toURL()}, server);
	}

	/**
	 * Client-only packages: always from the client JAR. A development server's class loader has them too (the game JAR
	 * there is client and server in one) but refuses to load them on a server; a real server has none.
	 */
	private static final String[] CLIENT_ONLY = {"net.minecraft.client.", "com.mojang.blaze3d."};

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		for (String prefix : CLIENT_ONLY) {
			if (name.startsWith(prefix)) {
				synchronized (getClassLoadingLock(name)) {
					Class<?> loaded = findLoadedClass(name);
					if (loaded == null) {
						loaded = findClass(name);
					}
					if (resolve) {
						resolveClass(loaded);
					}
					return loaded;
				}
			}
		}
		if (!name.startsWith(OWN)) {
			return super.loadClass(name, resolve);
		}
		synchronized (getClassLoadingLock(name)) {
			Class<?> loaded = findLoadedClass(name);
			if (loaded == null) {
				try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
					if (in == null) {
						throw new ClassNotFoundException(name);
					}
					byte[] bytes = in.readAllBytes();
					loaded = defineClass(name, bytes, 0, bytes.length);
				} catch (IOException e) {
					throw new ClassNotFoundException(name, e);
				}
			}
			if (resolve) {
				resolveClass(loaded);
			}
			return loaded;
		}
	}

	/** All faces of these model layers in 3D (ClientModels.boxes): block entities as inventory icons. */
	static String boxes(Path clientJar, Set<String> layers) throws IOException {
		try (ClientClasses loader = new ClientClasses(clientJar, ClientClasses.class.getClassLoader())) {
			Class<?> reader = loader.loadClass(OWN + "ClientModels");
			return (String) reader.getMethod("boxes", Set.class).invoke(null, layers);
		} catch (ReflectiveOperationException | LinkageError e) {
			throw new IOException("Could not read the block entity models of the client JAR", e);
		}
	}

	/** The names of the client JAR's model layers (for development). */
	static String layers(Path clientJar) throws IOException {
		try (ClientClasses loader = new ClientClasses(clientJar, ClientClasses.class.getClassLoader())) {
			Class<?> reader = loader.loadClass(OWN + "ClientModels");
			return (String) reader.getMethod("layers").invoke(null);
		} catch (ReflectiveOperationException | LinkageError e) {
			throw new IOException("Could not read the model layers of the client JAR", e);
		}
	}

	/** The head geometry of these model layers, read by the client JAR's own model code (ClientModels). */
	static String models(Path clientJar, Set<String> layers) throws IOException {
		try (ClientClasses loader = new ClientClasses(clientJar, ClientClasses.class.getClassLoader())) {
			Class<?> reader = loader.loadClass(OWN + "ClientModels");
			return (String) reader.getMethod("json", Set.class).invoke(null, layers);
		} catch (ReflectiveOperationException | LinkageError e) {
			throw new IOException("Could not read the entity models of the client JAR", e);
		}
	}
}
