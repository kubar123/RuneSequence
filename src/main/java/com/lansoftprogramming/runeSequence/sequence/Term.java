package com.lansoftprogramming.runeSequence.sequence;


import java.util.List;

public class Term {

	private final List<Alternative> alternatives;

	public Term(List<Alternative> alternatives) {
		this.alternatives = List.copyOf(alternatives);
	}

	public List<Alternative> getAlternatives() {
		return alternatives;
	}
}
