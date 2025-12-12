package com.lansoftprogramming.runeSequence.ui.presetManager.model;

import com.lansoftprogramming.runeSequence.core.sequence.model.AbilitySettingsOverrides;
import com.lansoftprogramming.runeSequence.core.sequence.model.AbilityToken;

/**
 * Represents a visual element in a sequence display.
 * Can be either an ability or a separator (arrow).
 */
public class SequenceElement {

	public enum Type {
		ABILITY,
		ARROW,
		PLUS,
		SLASH,
		TOOLTIP
	}

	private final Type type;
	private final String value;
	private final String instanceLabel;
	private final AbilitySettingsOverrides abilitySettingsOverrides;

	private SequenceElement(Type type,
	                        String value,
	                        String instanceLabel,
	                        AbilitySettingsOverrides abilitySettingsOverrides) {
		this.type = type;
		this.value = value;
		this.instanceLabel = instanceLabel;
		this.abilitySettingsOverrides = abilitySettingsOverrides;
	}

	public static SequenceElement ability(String abilityKey) {
		return new SequenceElement(Type.ABILITY, abilityKey, null, null);
	}

	public static SequenceElement ability(String abilityKey,
	                                      String instanceLabel,
	                                      AbilitySettingsOverrides abilitySettingsOverrides) {
		return new SequenceElement(Type.ABILITY, abilityKey, instanceLabel, abilitySettingsOverrides);
	}

	public static SequenceElement arrow() {
		return new SequenceElement(Type.ARROW, "→", null, null);
	}

	public static SequenceElement plus() {
		return new SequenceElement(Type.PLUS, "+", null, null);
	}

	public static SequenceElement slash() {
		return new SequenceElement(Type.SLASH, "/", null, null);
	}

	public static SequenceElement tooltip(String message) {
		return new SequenceElement(Type.TOOLTIP, message, null, null);
	}

	public Type getType() {
		return type;
	}

	public String getValue() {
		return value;
	}

	public String getAbilityKey() {
		return type == Type.ABILITY ? value : null;
	}

	public String getInstanceLabel() {
		return instanceLabel;
	}

	public AbilitySettingsOverrides getAbilitySettingsOverrides() {
		return abilitySettingsOverrides;
	}

	/**
	 * Returns the canonical ability key for this element, falling back to value for legacy callers.
	 */
	public String getResolvedAbilityKey() {
		if (!isAbility()) {
			return null;
		}
		if (value != null && !value.isBlank()) {
			return value;
		}
		return getValue();
	}

	public boolean isAbility() {
		return type == Type.ABILITY;
	}

	public boolean isSeparator() {
		return type == Type.ARROW || type == Type.PLUS || type == Type.SLASH;
	}

	public boolean isTooltip() {
		return type == Type.TOOLTIP;
	}

	public boolean isPlus() {
		return type == Type.PLUS;
	}

	public boolean isSlash() {
		return type == Type.SLASH;
	}

	public boolean hasOverrides() {
		return abilitySettingsOverrides != null && !abilitySettingsOverrides.isEmpty();
	}

	public SequenceElement withInstanceLabel(String label) {
		if (type != Type.ABILITY) {
			return this;
		}
		return new SequenceElement(type, value, label, abilitySettingsOverrides);
	}

	public SequenceElement withOverrides(AbilitySettingsOverrides overrides) {
		if (type != Type.ABILITY) {
			return this;
		}
		return new SequenceElement(type, value, instanceLabel, overrides);
	}

	public String formatAbilityToken() {
		if (type != Type.ABILITY) {
			return value;
		}
		return AbilityToken.format(value, instanceLabel);
	}

	@Override
	public String toString() {
		if (type == Type.ABILITY) {
			return "SequenceElement{type=" + type +
					", abilityKey='" + value + '\'' +
					", instanceLabel='" + instanceLabel + '\'' +
					", overrides=" + abilitySettingsOverrides +
					'}';
		}
		return "SequenceElement{type=" + type + ", value='" + value + "'}";
	}
}
