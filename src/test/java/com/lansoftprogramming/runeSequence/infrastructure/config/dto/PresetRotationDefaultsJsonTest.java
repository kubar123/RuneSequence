package com.lansoftprogramming.runeSequence.infrastructure.config.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PresetRotationDefaultsJsonTest {
	@Test
	void shouldIgnoreEmptyFieldFromOlderConfigs() {
		ObjectMapper mapper = new ObjectMapper();
		assertDoesNotThrow(() -> mapper.readValue("{\"empty\":true,\"ezk\":true}", PresetRotationDefaults.class));
	}
}

