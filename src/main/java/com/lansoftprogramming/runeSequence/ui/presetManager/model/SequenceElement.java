package com.lansoftprogramming.runeSequence.ui.presetManager.model;

import com.lansoftprogramming.runeSequence.core.sequence.model.AbilitySettingsOverrides;
import com.lansoftprogramming.runeSequence.core.sequence.model.AbilityToken;
import com.lansoftprogramming.runeSequence.core.sequence.parser.TooltipGrammar;

import java.util.List;

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
	private final List<String> abilityModifiers;

	private SequenceElement(Type type,
	                        String value,
	                        String instanceLabel,
	                        AbilitySettingsOverrides abilitySettingsOverrides,
	                        List<String> abilityModifiers) {
		this.type = type;
		this.value = value;
		this.instanceLabel = instanceLabel;
		this.abilitySettingsOverrides = abilitySettingsOverrides;
		this.abilityModifiers = abilityModifiers != null ? List.copyOf(abilityModifiers) : List.of();
	}

	public static SequenceElement ability(String abilityKey) {
		return new SequenceElement(Type.ABILITY, abilityKey, null, null, List.of());
	}

	public static SequenceElement ability(String abilityKey,
	                                      String instanceLabel,
	                                      AbilitySettingsOverrides abilitySettingsOverrides) {
		return new SequenceElement(Type.ABILITY, abilityKey, instanceLabel, abilitySettingsOverrides, List.of());
	}

	public static SequenceElement ability(String abilityKey,
	                                      String instanceLabel,
	                                      AbilitySettingsOverrides abilitySettingsOverrides,
	                                      List<String> abilityModifiers) {
		return new SequenceElement(Type.ABILITY, abilityKey, instanceLabel, abilitySettingsOverrides, abilityModifiers);
	}

	public static SequenceElement arrow() {
		return new SequenceElement(Type.ARROW, String.valueOf(TooltipGrammar.ARROW), null, null, List.of());
	}

	public static SequenceElement plus() {
		return new SequenceElement(Type.PLUS, String.valueOf(TooltipGrammar.AND), null, null, List.of());
	}

	public static SequenceElement slash() {
		return new SequenceElement(Type.SLASH, String.valueOf(TooltipGrammar.OR), null, null, List.of());
	}

	public static SequenceElement tooltip(String message) {
		return new SequenceElement(Type.TOOLTIP, message, null, null, List.of());
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

	public List<String> getAbilityModifiers() {
		if (!isAbility()) {
			return List.of();
		}
		return abilityModifiers != null ? abilityModifiers : List.of();
	}

	public boolean hasAbilityModifiers() {
		return isAbility() && abilityModifiers != null && !abilityModifiers.isEmpty();
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
		return new SequenceElement(type, value, label, abilitySettingsOverrides, abilityModifiers);
	}

	public SequenceElement withOverrides(AbilitySettingsOverrides overrides) {
		if (type != Type.ABILITY) {
			return this;
		}
		return new SequenceElement(type, value, instanceLabel, overrides, abilityModifiers);
	}

	public SequenceElement withAbilityModifiers(List<String> modifiers) {
		if (type != Type.ABILITY) {
			return this;
		}
		return new SequenceElement(type, value, instanceLabel, abilitySettingsOverrides, modifiers);
	}

	public String formatAbilityToken() {
		if (type != Type.ABILITY) {
			return value;
		}
		String base = AbilityToken.format(value, instanceLabel);
		if (abilityModifiers == null || abilityModifiers.isEmpty()) {
			return base;
		}
		StringBuilder sb = new StringBuilder();
		for (String modifier : abilityModifiers) {
			if (modifier == null || modifier.isBlank()) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(TooltipGrammar.AND);
			}
			sb.append(modifier);
		}
		if (sb.length() > 0) {
			sb.append(TooltipGrammar.AND);
		}
		sb.append(base);
		return sb.toString();
	}

	@Override
	public String toString() {
		if (type == Type.ABILITY) {
			return "SequenceElement{type=" + type +
					", abilityKey='" + value + '\'' +
					", instanceLabel='" + instanceLabel + '\'' +
					", overrides=" + abilitySettingsOverrides +
					", modifiers=" + abilityModifiers +
					'}';
		}
		return "SequenceElement{type=" + type + ", value='" + value + "'}";
	}
}
