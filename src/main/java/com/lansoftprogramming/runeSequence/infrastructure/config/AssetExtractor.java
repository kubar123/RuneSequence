package com.lansoftprogramming.runeSequence.infrastructure.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.Collections;

public class AssetExtractor {

	public static void extractDefaultAssets(String resourceRoot, Path targetDir) throws IOException {
		Files.createDirectories(targetDir);

		try {
			URI uri = AssetExtractor.class.getResource(resourceRoot).toURI();

			if (uri.getScheme().equals("jar")) {
				extractFromJar(uri, resourceRoot, targetDir);
			} else {
				extractFromFileSystem(Paths.get(uri), targetDir);
			}
		} catch (URISyntaxException | NullPointerException e) {
			throw new IOException("Resource not found: " + resourceRoot, e);
		}
	}

	private static void extractFromJar(URI jarUri, String resourceRoot, Path targetDir) throws IOException {
		FileSystem fs = null;
		boolean shouldClose = false;
		try {
			try {
				fs = FileSystems.newFileSystem(jarUri, Collections.emptyMap());
				shouldClose = true;
			} catch (FileSystemAlreadyExistsException e) {
				fs = FileSystems.getFileSystem(jarUri);
			}

			Path root = fs.getPath(resourceRoot);
			try (var stream = Files.walk(root)) {
				stream.forEach(source -> {
					try {
						Path relativePath = root.relativize(source);
						Path dest = targetDir.resolve(relativePath.toString());
						if (Files.isDirectory(source)) {
							Files.createDirectories(dest);
						} else {
							Files.createDirectories(dest.getParent());
							Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
						}
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				});
			} catch (UncheckedIOException e) {
				throw e.getCause();
			}
		} finally {
			if (shouldClose && fs != null) {
				try {
					fs.close();
				} catch (Exception ignored) {
					// Best-effort; failing to close should not fail extraction.
				}
			}
		}
	}

	private static void extractFromFileSystem(Path source, Path targetDir) throws IOException {
		try (var stream = Files.walk(source)) {
			stream.forEach(srcPath -> {
				try {
					Path relativePath = source.relativize(srcPath);
					Path dest = targetDir.resolve(relativePath.toString());
					if (Files.isDirectory(srcPath)) {
						Files.createDirectories(dest);
					} else {
						Files.createDirectories(dest.getParent());
						Files.copy(srcPath, dest, StandardCopyOption.REPLACE_EXISTING);
					}
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
	}

	public static void extractSubfolder(String resourcePath, Path targetDir) throws IOException {
		extractDefaultAssets(resourcePath, targetDir);
	}
}
