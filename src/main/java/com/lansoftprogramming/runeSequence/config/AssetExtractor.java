package com.lansoftprogramming.runeSequence.config;

import java.io.IOException;
import java.io.InputStream;
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
		try (FileSystem fs = FileSystems.newFileSystem(jarUri, Collections.emptyMap())) {
			Path root = fs.getPath(resourceRoot);
			Files.walk(root).forEach(source -> {
				try {
					Path relativePath = root.relativize(source);
					Path dest = targetDir.resolve(relativePath);  // Remove .toString()
					if (Files.isDirectory(source)) {
						Files.createDirectories(dest);
					} else {
						Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
	}

	private static void extractFromFileSystem(Path source, Path targetDir) throws IOException {
		Files.walk(source).forEach(srcPath -> {
			try {
				Path relativePath = source.relativize(srcPath);
				Path dest = targetDir.resolve(relativePath);  // Remove .toString()
				if (Files.isDirectory(srcPath)) {
					Files.createDirectories(dest);
				} else {
					Files.copy(srcPath, dest, StandardCopyOption.REPLACE_EXISTING);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}

	public static void extractSubfolder(String resourcePath, Path targetDir) throws IOException {
		extractDefaultAssets(resourcePath, targetDir);
	}
}