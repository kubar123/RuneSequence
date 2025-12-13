package com.lansoftprogramming.runeSequence.infrastructure.config.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Rotation-wide ability defaults for a preset. Null fields mean "inherit global defaults".
 * These values act as defaults for abilities within the rotation unless overridden per-instance.
 */
@JsonIgnoreProperties({"empty"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PresetRotationDefaults {
	@JsonProperty("ezk")
	private Boolean ezk;

	@JsonProperty("always_gbarge")
	private Boolean alwaysGBarge;

	public Boolean getEzk() {
		return ezk;
	}

	public void setEzk(Boolean ezk) {
		this.ezk = ezk;
	}

	public Boolean getAlwaysGBarge() {
		return alwaysGBarge;
	}

	public void setAlwaysGBarge(Boolean alwaysGBarge) {
		this.alwaysGBarge = alwaysGBarge;
	}

	@JsonIgnore
	public boolean isEmpty() {
		return ezk == null && alwaysGBarge == null;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PresetRotationDefaults that = (PresetRotationDefaults) o;
		return Objects.equals(ezk, that.ezk) && Objects.equals(alwaysGBarge, that.alwaysGBarge);
	}

	@Override
	public int hashCode() {
		return Objects.hash(ezk, alwaysGBarge);
	}
}
