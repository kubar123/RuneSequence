package com.lansoftprogramming.runeSequence.ui.shared.service;

import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.Objects;

/**
 * Factory responsible for building {@link AbilityItem} instances from ability config data.
 */
public class AbilityItemFactory {
	private static final Logger logger = LoggerFactory.getLogger(AbilityItemFactory.class);

	private final AbilityConfig abilityConfig;
	private final AbilityIconLoader iconLoader;

	public AbilityItemFactory(AbilityConfig abilityConfig, AbilityIconLoader iconLoader) {
		this.abilityConfig = Objects.requireNonNull(abilityConfig, "Ability config cannot be null");
		this.iconLoader = Objects.requireNonNull(iconLoader, "Icon loader cannot be null");
	}

	public AbilityItem createAbilityItem(String abilityKey) {
		try {
			AbilityConfig.AbilityData abilityData = abilityConfig.getAbility(abilityKey);

			if (abilityData == null) {
				logger.debug("Ability data not found for key '{}'", abilityKey);
				ImageIcon icon = iconLoader.loadIcon(abilityKey);
				return new AbilityItem(abilityKey, abilityKey, 0, "Unknown", icon);
			}

			String displayName = getDisplayName(abilityData, abilityKey);
			int level = abilityData.getLevel() != null ? abilityData.getLevel() : 0;
			String type = abilityData.getType() != null ? abilityData.getType() : "Unknown";
			ImageIcon icon = iconLoader.loadIcon(abilityKey);

			return new AbilityItem(abilityKey, displayName, level, type, icon);
		} catch (Exception e) {
			logger.warn("Failed to create ability item for key: {}", abilityKey, e);
			return null;
		}
	}

	private String getDisplayName(AbilityConfig.AbilityData abilityData, String fallbackKey) {
		String commonName = abilityData.getCommonName();
		if (commonName != null && !commonName.isEmpty()) {
			return commonName;
		}
		return fallbackKey;
	}
}
