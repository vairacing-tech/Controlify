/*
 * Copyright (C) 2026 isXander
 * This file is part of Controlify.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package dev.isxander.controlify.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermission.*;
import static org.junit.jupiter.api.Assertions.*;

public class ConfigPermissionUtilTest {

	@Test
	void testEnsureAndroidPermissionsFrom0600() {
		Set<PosixFilePermission> input = PosixFilePermissions.fromString("rw-------"); // 0600
		Set<PosixFilePermission> result = ConfigPermissionUtil.ensureAndroidPermissions(input);
		Set<PosixFilePermission> expected = PosixFilePermissions.fromString("rw-rw----"); // 0660

		assertEquals(expected, result);
	}

	@Test
	void testEnsureAndroidPermissionsFrom0640() {
		Set<PosixFilePermission> input = PosixFilePermissions.fromString("rw-r-----"); // 0640
		Set<PosixFilePermission> result = ConfigPermissionUtil.ensureAndroidPermissions(input);
		Set<PosixFilePermission> expected = PosixFilePermissions.fromString("rw-rw----"); // 0660

		assertEquals(expected, result);
	}

	@Test
	void testEnsureAndroidPermissionsFrom0660() {
		Set<PosixFilePermission> input = PosixFilePermissions.fromString("rw-rw----"); // 0660
		Set<PosixFilePermission> result = ConfigPermissionUtil.ensureAndroidPermissions(input);
		Set<PosixFilePermission> expected = PosixFilePermissions.fromString("rw-rw----"); // 0660

		assertEquals(expected, result);
	}

	@Test
	void testEnsureAndroidPermissionsPreservesOthers() {
		Set<PosixFilePermission> input = PosixFilePermissions.fromString("rw-rw-r--"); // 0664
		Set<PosixFilePermission> result = ConfigPermissionUtil.ensureAndroidPermissions(input);
		Set<PosixFilePermission> expected = PosixFilePermissions.fromString("rw-rw-r--"); // 0664

		assertEquals(expected, result);
	}

	@Test
	void testEnsureAndroidPermissionsPreservesExecuteBit() {
		Set<PosixFilePermission> input = PosixFilePermissions.fromString("rwx------"); // 0700
		Set<PosixFilePermission> result = ConfigPermissionUtil.ensureAndroidPermissions(input);
		Set<PosixFilePermission> expected = PosixFilePermissions.fromString("rwxrw----"); // 0760

		assertEquals(expected, result);
	}

	@Test
	void testEnsureAndroidPermissionsDoesNotAddOthersPermissions() {
		Set<PosixFilePermission> input = PosixFilePermissions.fromString("rw-------");
		Set<PosixFilePermission> result = ConfigPermissionUtil.ensureAndroidPermissions(input);

		assertFalse(result.contains(OTHERS_READ), "Should not add OTHERS_READ");
		assertFalse(result.contains(OTHERS_WRITE), "Should not add OTHERS_WRITE");
		assertFalse(result.contains(OTHERS_EXECUTE), "Should not add OTHERS_EXECUTE");
	}

	@Test
	void testDetermineWritePermissionsForNewAndroidFile(@TempDir Path tempDir) {
		Path target = tempDir.resolve("controlify.json");
		assertFalse(Files.exists(target));

		boolean isPosix = ConfigPermissionUtil.isPosixSupported(target);
		Set<PosixFilePermission> permissions = ConfigPermissionUtil.determineWritePermissions(target, true);

		if (isPosix) {
			assertNotNull(permissions);
			Set<PosixFilePermission> expected0660 = PosixFilePermissions.fromString("rw-rw----");
			assertEquals(expected0660, permissions);
			assertFalse(permissions.contains(OTHERS_READ));
			assertFalse(permissions.contains(OTHERS_WRITE));
			assertFalse(permissions.contains(OTHERS_EXECUTE));
		} else {
			assertNull(permissions);
		}
	}

	@Test
	void testDetermineWritePermissionsPreservesDesktopLinuxExistingPermissions(@TempDir Path tempDir) throws IOException {
		Path target = tempDir.resolve("controlify.json");
		Files.writeString(target, "{}");

		boolean isPosix = ConfigPermissionUtil.isPosixSupported(target);
		if (isPosix) {
			Set<PosixFilePermission> originalPerms = PosixFilePermissions.fromString("rw-r--r--");
			Files.setPosixFilePermissions(target, originalPerms);

			// On desktop Linux (isAndroid = false), existing permissions should be preserved exactly
			Set<PosixFilePermission> permissions = ConfigPermissionUtil.determineWritePermissions(target, false);
			assertNotNull(permissions);
			assertEquals(originalPerms, permissions);
		} else {
			Set<PosixFilePermission> permissions = ConfigPermissionUtil.determineWritePermissions(target, false);
			assertNull(permissions);
		}
	}

	@Test
	void testDetermineWritePermissionsForNonAndroidNewFile(@TempDir Path tempDir) {
		Path target = tempDir.resolve("new-file.json");
		assertFalse(Files.exists(target));

		// On non-Android POSIX or non-POSIX, new files return null (use system default creation)
		Set<PosixFilePermission> permissions = ConfigPermissionUtil.determineWritePermissions(target, false);
		assertNull(permissions);
	}

	@Test
	void testCreateTempConfigFileCleanCreation(@TempDir Path tempDir) throws IOException {
		Path target = tempDir.resolve("profile-1.json");
		Path tempFile = ConfigPermissionUtil.createTempConfigFile(tempDir, target, true);

		try {
			assertTrue(Files.exists(tempFile));
			assertEquals(tempDir, tempFile.getParent());
			assertTrue(tempFile.getFileName().toString().startsWith("profile-1.json"));
			assertTrue(tempFile.getFileName().toString().endsWith(".tmp"));
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}
}
