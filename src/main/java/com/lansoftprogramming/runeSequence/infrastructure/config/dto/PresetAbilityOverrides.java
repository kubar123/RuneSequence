package com.lansoftprogramming.runeSequence.infrastructure.config.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Persistence DTO for a single ability instance override within a preset.
 * Only non-null fields are serialized.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PresetAbilityOverrides {
	@JsonProperty("type")
	private String type;

	@JsonProperty("level")
	private Integer level;

	@JsonProperty("triggers_gcd")
	private Boolean triggersGcd;

	@JsonProperty("cast_duration")
	private Short castDuration;

	@JsonProperty("cooldown")
	private Short cooldown;

	@JsonProperty("detection_threshold")
	private Double detectionThreshold;

	@JsonProperty("mask")
	private String mask;

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public Integer getLevel() {
		return level;
	}

	public void setLevel(Integer level) {
		this.level = level;
	}

	public Boolean getTriggersGcd() {
		return triggersGcd;
	}

	public void setTriggersGcd(Boolean triggersGcd) {
		this.triggersGcd = triggersGcd;
	}

	public Short getCastDuration() {
		return castDuration;
	}

	public void setCastDuration(Short castDuration) {
		this.castDuration = castDuration;
	}

	public Short getCooldown() {
		return cooldown;
	}

	public void setCooldown(Short cooldown) {
		this.cooldown = cooldown;
	}

	public Double getDetectionThreshold() {
		return detectionThreshold;
	}

	public void setDetectionThreshold(Double detectionThreshold) {
		this.detectionThreshold = detectionThreshold;
	}

	public String getMask() {
		return mask;
	}

	public void setMask(String mask) {
		this.mask = mask;
	}
}
