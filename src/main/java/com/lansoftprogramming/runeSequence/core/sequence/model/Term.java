package com.lansoftprogramming.runeSequence.core.sequence.model;


import java.util.List;

public class Term {

	private final List<Alternative> alternatives;

	public Term(List<Alternative> alternatives) {
		this.alternatives = List.copyOf(alternatives);
	}

	public List<Alternative> getAlternatives() {
		return alternatives;
	}

	@Override
	public String toString() {
		String result = String.join(" / ", alternatives.stream().map(Object::toString).toList());
		return alternatives.size() > 1 ? "(" + result + ")" : result;
	}
}