from pydantic import BaseModel, Field, model_validator


class TrafficThresholds(BaseModel):
    """Configurable thresholds for traffic spike detection.
    Constructed either from hardcoded Settings (fallback) or from calibrated MinIO artifacts.
    """

    z_score_flag: float = Field(gt=0)
    iqr_multiplier: float = Field(gt=0)
    ema_alpha: float = Field(gt=0, le=1)
    ema_dev_threshold: float = Field(gt=0)
    min_history: int = Field(ge=1)
    seasonal_z_threshold: float = Field(gt=0)
    seasonal_min_bucket_size: int = Field(ge=1)
    min_weighted_chosen: float = Field(gt=0)
    weight_ema: float = Field(ge=0)
    weight_zscore: float = Field(ge=0)
    weight_iqr: float = Field(ge=0)
    weight_seasonal: float = Field(ge=0)
    absolute_min_floor: float = Field(default=15.0, ge=0)
    z_score_variance_floor: float = Field(default=5.0, ge=0)
    iqr_variance_floor: float = Field(default=5.0, ge=0)
    ema_variance_floor: float = Field(default=5.0, ge=0)
    seasonal_scale_floor: float = Field(default=5.0, ge=0)
    low_volume_jump_multiplier: float = Field(default=3.0, gt=0)
    # Effective floor is max(absolute floor, pct * recent baseline mean), so the floor
    # scales up at higher baselines instead of staying pinned to the absolute value —
    # otherwise unusually steady traffic at a high baseline gets an ordinary few-percent
    # fluctuation amplified past threshold once the absolute floor clamps the measured std.
    z_score_variance_floor_pct: float = Field(default=0.03, ge=0)
    iqr_variance_floor_pct: float = Field(default=0.03, ge=0)
    ema_variance_floor_pct: float = Field(default=0.03, ge=0)


    @model_validator(mode="after")
    def _check_weight_sum(self) -> "TrafficThresholds":
        total = self.weight_ema + self.weight_zscore + self.weight_iqr + self.weight_seasonal
        if abs(total - 3.0) > 1e-6:
            raise ValueError(
                f"Detector weights must sum to 3.0 (got {total:.6f}). "
                "Canonical split: weight_ema=0.5, weight_zscore=0.5, "
                "weight_iqr=1.0, weight_seasonal=1.0"
            )
        return self

    @model_validator(mode="after")
    def _check_min_weighted_chosen_requires_corroboration(self) -> "TrafficThresholds":
        max_single_weight = max(self.weight_ema, self.weight_zscore, self.weight_iqr, self.weight_seasonal)
        if self.min_weighted_chosen <= max_single_weight:
            raise ValueError(
                f"min_weighted_chosen ({self.min_weighted_chosen}) must exceed the largest "
                f"single detector weight ({max_single_weight}), otherwise that one detector "
                "firing alone is enough to declare an anomaly with no corroboration from a "
                "second axis. A calibration artifact that picks an operating point this low "
                "defeats the ensemble design — re-tune pick_operating_point() instead of "
                "lowering this bar."
            )
        return self
