package com.lansoftprogramming.runeSequence.config;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for ability categories.
 * Maps category names to lists of ability keys.
 *
 * The JSON structure is a simple map: { "Melee": [...], "Magic": [...], ... }
 */
public class AbilityCategoryConfig {
	private Map<String, List<String>> categories = new HashMap<>();

	public AbilityCategoryConfig() {
	}

	public AbilityCategoryConfig(Map<String, List<String>> categories) {
		this.categories = categories;
	}

	@JsonAnySetter
	public void addCategory(String key, List<String> value) {
		categories.put(key, value);
	}

	public Map<String, List<String>> getCategories() {
		return categories;
	}

	public void setCategories(Map<String, List<String>> categories) {
		this.categories = categories;
	}

	public List<String> getAbilitiesForCategory(String categoryName) {
		return categories != null ? categories.get(categoryName) : null;
	}
}
