/*
 * Copyright (C) 2026 isXander
 * This file is part of Controlify.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package dev.isxander.controlify.compatibility.plasmovoice;

import dev.isxander.controlify.api.bind.ControlifyBindApi;
import dev.isxander.controlify.api.bind.InputBindingSupplier;
import dev.isxander.controlify.api.event.ControlifyEvents;
import dev.isxander.controlify.bindings.BindContext;
import dev.isxander.controlify.utils.CUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Optional Plasmo Voice compatibility.
 *
 * Plasmo opens its settings from its own keyboard event bus, so a normal
 * Controlify key-emulation/fake press does not open the screen. This binding
 * calls the Plasmo settings screen directly when selected from Controlify.
 *
 * Reflection keeps Plasmo Voice an optional runtime dependency.
 */
public final class PlasmoVoiceCompat {
	private static InputBindingSupplier settingsSupplier;

	private PlasmoVoiceCompat() {
	}

	public static void init() {
		settingsSupplier = ControlifyBindApi.get().registerBinding(builder -> builder
				.id("plasmovoice", "settings")
				.name(Component.translatable("controlify.binding.plasmovoice.settings"))
				.category(Component.literal("Plasmo Voice"))
				.allowedContexts(BindContext.IN_GAME));

		ControlifyEvents.ACTIVE_CONTROLLER_TICKED.register(event -> {
			if (settingsSupplier.on(event.controller()).justPressed()) {
				// Run after the radial menu has had a chance to close.
				Minecraft.getInstance().execute(PlasmoVoiceCompat::openSettings);
			}
		});
	}

	private static void openSettings() {
		try {
			Class<?> clientClass = Class.forName("su.plo.voice.client.ModVoiceClient");
			Object voiceClient = clientClass.getField("INSTANCE").get(null);
			if (voiceClient == null) {
				throw new IllegalStateException("Plasmo Voice client is not initialized");
			}

			Class<?> screensClass = Class.forName("su.plo.voice.client.gui.settings.VoiceScreens");
			Object screens = screensClass.getField("INSTANCE").get(null);

			Method openSettings = Arrays.stream(screensClass.getMethods())
					.filter(method -> method.getName().equals("openSettings"))
					.filter(method -> method.getParameterCount() == 1)
					.filter(method -> method.getParameterTypes()[0].isAssignableFrom(voiceClient.getClass()))
					.findFirst()
					.orElseThrow(() -> new NoSuchMethodException("VoiceScreens.openSettings(client)"));

			openSettings.invoke(screens, voiceClient);
		} catch (Throwable t) {
			CUtil.LOGGER.error("Failed to open Plasmo Voice settings from Controlify", t);
		}
	}
}
