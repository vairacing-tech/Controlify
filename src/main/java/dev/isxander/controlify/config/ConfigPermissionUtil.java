/*
 * Copyright (C) 2026 isXander
 * This file is part of Controlify.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package dev.isxander.controlify.config;

import com.mojang.logging.LogUtils;
import dev.isxander.controlify.utils.Platform;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermission.*;

/**
 * Utility for handling POSIX file permissions for Controlify configuration files.
 * Ensures proper group read/write permissions (0660) on Android environments
 * while preserving existing permissions on desktop Linux and other POSIX platforms,
 * and safely no-oping on non-POSIX systems like Windows.
 */
public final class ConfigPermissionUtil {
	private static final Logger LOGGER = LogUtils.getLogger();

	public static final Set<PosixFilePermission> ANDROID_CONFIG_PERMISSIONS = Collections.unmodifiableSet(
			EnumSet.of(OWNER_READ, OWNER_WRITE, GROUP_READ, GROUP_WRITE)
	);

	private ConfigPermissionUtil() {
	}

	public static boolean isAndroid() {
		return Platform.current() == Platform.ANDROID;
	}

	public static boolean isPosixSupported(Path path) {
		return path.getFileSystem().supportedFileAttributeViews().contains("posix");
	}

	/**
	 * Ensures the returned permission set includes at least OWNER_READ, OWNER_WRITE,
	 * GROUP_READ, and GROUP_WRITE, while preserving any other existing bits (e.g. execute or others)
	 * and without adding any new permissions for others.
	 */
	public static Set<PosixFilePermission> ensureAndroidPermissions(Set<PosixFilePermission> existing) {
		Set<PosixFilePermission> perms = EnumSet.copyOf(existing);
		perms.add(OWNER_READ);
		perms.add(OWNER_WRITE);
		perms.add(GROUP_READ);
		perms.add(GROUP_WRITE);
		return perms;
	}

	/**
	 * Determines the target POSIX permissions for writing a configuration file.
	 *
	 * @param targetPath the final destination path
	 * @param isAndroid whether the current runtime environment is Android
	 * @return target POSIX permissions, or null if POSIX is not supported or default behavior applies
	 */
	public static @Nullable Set<PosixFilePermission> determineWritePermissions(Path targetPath, boolean isAndroid) {
		if (!isPosixSupported(targetPath)) {
			return null;
		}

		if (Files.exists(targetPath)) {
			try {
				Set<PosixFilePermission> existing = Files.getPosixFilePermissions(targetPath);
				if (isAndroid) {
					return ensureAndroidPermissions(existing);
				} else {
					return EnumSet.copyOf(existing);
				}
			} catch (IOException e) {
				if (isAndroid) {
					LOGGER.warn("Failed to read permissions of existing config file: {}", targetPath, e);
					return EnumSet.copyOf(ANDROID_CONFIG_PERMISSIONS);
				}
				return null;
			}
		} else {
			if (isAndroid) {
				return EnumSet.copyOf(ANDROID_CONFIG_PERMISSIONS);
			} else {
				return null;
			}
		}
	}

	public static @Nullable Set<PosixFilePermission> determineWritePermissions(Path targetPath) {
		return determineWritePermissions(targetPath, isAndroid());
	}

	/**
	 * Creates a temporary file in the specified directory configured with the appropriate
	 * target permissions prior to writing and moving atomically.
	 */
	public static Path createTempConfigFile(Path directory, Path targetPath, boolean isAndroid) throws IOException {
		Set<PosixFilePermission> targetPermissions = determineWritePermissions(targetPath, isAndroid);
		Path temporary;
		if (targetPermissions != null && isPosixSupported(directory)) {
			FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(targetPermissions);
			temporary = Files.createTempFile(directory, targetPath.getFileName().toString(), ".tmp", attr);
			try {
				Files.setPosixFilePermissions(temporary, targetPermissions);
			} catch (Exception e) {
				if (isAndroid) {
					LOGGER.warn("Failed to set POSIX permissions on temporary config file {}: {}", temporary, e.getMessage());
				}
			}
		} else {
			temporary = Files.createTempFile(directory, targetPath.getFileName().toString(), ".tmp");
		}
		return temporary;
	}

	public static Path createTempConfigFile(Path directory, Path targetPath) throws IOException {
		return createTempConfigFile(directory, targetPath, isAndroid());
	}

	/**
	 * Repairs POSIX permissions of an existing configuration file on Android by adding GROUP_READ
	 * and GROUP_WRITE while preserving any other existing bits. Best-effort: errors are logged as warnings.
	 */
	public static void repairConfigFilePermissions(Path path, boolean isAndroid) {
		if (!isAndroid || !Files.exists(path) || !isPosixSupported(path)) {
			return;
		}

		try {
			Set<PosixFilePermission> current = Files.getPosixFilePermissions(path);
			Set<PosixFilePermission> desired = ensureAndroidPermissions(current);
			if (!desired.equals(current)) {
				Files.setPosixFilePermissions(path, desired);
				LOGGER.info("Repaired permissions for {} from {} to {}", path.getFileName(), PosixFilePermissions.toString(current), PosixFilePermissions.toString(desired));
			}
		} catch (Exception e) {
			LOGGER.warn("Failed to repair POSIX permissions for {}: {}", path, e.getMessage());
		}
	}

	public static void repairConfigFilePermissions(Path path) {
		repairConfigFilePermissions(path, isAndroid());
	}

	/**
	 * Repairs POSIX permissions for all existing Controlify JSON configuration files in the config directory.
	 */
	public static void repairConfigDirectory(Path configDirectory, Path sharedPath, Path legacyPath, boolean isAndroid) {
		if (!isAndroid || !isPosixSupported(configDirectory)) {
			return;
		}

		repairConfigFilePermissions(sharedPath, isAndroid);
		repairConfigFilePermissions(legacyPath, isAndroid);

		if (Files.isDirectory(configDirectory)) {
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDirectory, "profile-*.json")) {
				for (Path profilePath : stream) {
					repairConfigFilePermissions(profilePath, isAndroid);
				}
			} catch (Exception e) {
				LOGGER.warn("Failed to scan profile configs in {} for permission repair: {}", configDirectory, e.getMessage());
			}
		}
	}

	public static void repairConfigDirectory(Path configDirectory, Path sharedPath, Path legacyPath) {
		repairConfigDirectory(configDirectory, sharedPath, legacyPath, isAndroid());
	}
}
