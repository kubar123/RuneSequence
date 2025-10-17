package com.lansoftprogramming.runeSequence.ui.presetManager.model;

/**
 * Represents a visual element in a sequence display.
 * Can be either an ability or a separator (arrow).
 */
public class SequenceElement {

	public enum Type {
		ABILITY,
		ARROW,
		PLUS,
		SLASH
	}

	private final Type type;
	private final String value;

	private SequenceElement(Type type, String value) {
		this.type = type;
		this.value = value;
	}

	public static SequenceElement ability(String abilityKey) {
		return new SequenceElement(Type.ABILITY, abilityKey);
	}

	public static SequenceElement arrow() {
		return new SequenceElement(Type.ARROW, "→");
	}

	public static SequenceElement plus() {
		return new SequenceElement(Type.PLUS, "+");
	}

	public static SequenceElement slash() {
		return new SequenceElement(Type.SLASH, "/");
	}

	public Type getType() {
		return type;
	}

	public String getValue() {
		return value;
	}

	public boolean isAbility() {
		return type == Type.ABILITY;
	}

	public boolean isSeparator() {
		return type == Type.ARROW || type == Type.PLUS || type == Type.SLASH;
	}

	public boolean isPlus() {
		return type == Type.PLUS;
	}

	public boolean isSlash() {
		return type == Type.SLASH;
	}

	@Override
	public String toString() {
		return "SequenceElement{type=" + type + ", value='" + value + "'}";
	}
}