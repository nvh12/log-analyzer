import unittest
from unittest.mock import MagicMock
from datetime import datetime, timezone, timedelta
import numpy as np

from domain.models.input import WebAttackInput, DDoSInput, BruteForceInput, TrafficInput, TrafficThresholds
from domain.models.results import Severity
from domain.services.web_attack_service import detect as detect_web_attack
from domain.services.ddos_service import detect as detect_ddos
from domain.services.brute_force_service import detect as detect_brute_force
from domain.services.traffic_service import detect as detect_traffic

class TestDetectionPipelines(unittest.TestCase):
    def setUp(self):
        self.repo = MagicMock()

    # --- Web Attack (UC3) ---

    def test_web_attack_rule_engine(self):
        """Verify Layer 1: Rule Engine catches signatures."""
        req = WebAttackInput(
            source_ip="1.2.3.4",
            timestamp=datetime.now(),
            url="/api/data",
            method="GET",
            query_string="id=1' OR '1'='1"  # SQLi
        )
        result = detect_web_attack(req, self.repo)
        
        self.assertTrue(result.anomaly)
        self.assertEqual(result.layer_triggered, "rule_engine:sqli")

    def test_web_attack_xgboost(self):
        """Verify Layer 2: XGBoost catches residual attacks."""
        req = WebAttackInput(
            source_ip="1.2.3.4",
            timestamp=datetime.now(),
            url="/api/data",
            method="POST",
            body="malicious_payload=true"
        )
        
        mock_model = MagicMock()
        mock_model.predict_proba.return_value = np.array([[0.1, 0.9]])
        mock_scaler = MagicMock()
        mock_scaler.transform.side_effect = lambda x: x
        
        xgb_art = {
            "model": mock_model,
            "scaler": mock_scaler,
            "threshold": 0.5,
            "vocab": MagicMock(known_names={"id"})
        }
        self.repo.get.side_effect = lambda k: xgb_art if k == "web_xgb" else None
        
        result = detect_web_attack(req, self.repo)
        self.assertTrue(result.anomaly)
        self.assertEqual(result.layer_triggered, "xgboost")

    # --- DDoS (UC2) ---

    def test_ddos_xgboost_anomaly(self):
        """Verify DDoS detection with scaling and optimized threshold."""
        inp = DDoSInput(
            timestamp=datetime.now().timestamp(),
            source_ip="10.0.0.1",
            dest_ip="10.0.0.2",
            source_port=12345,
            dest_port=80,
            features={"f1": 10.0}
        )
        
        mock_model = MagicMock()
        mock_model.predict_proba.return_value = np.array([[0.6, 0.4]]) # 40% prob
        mock_scaler = MagicMock()
        mock_scaler.transform.side_effect = lambda x: x

        ddos_art = {
            "model": mock_model,
            "scaler": mock_scaler,
            "threshold": 0.35 # Should trigger since 0.4 > 0.35
        }
        self.repo.get.side_effect = lambda k: {
            "ddos_xgb": ddos_art,
            "ddos_feature_cols": ["f1"]
        }.get(k)
        
        result = detect_ddos(inp, self.repo)
        self.assertTrue(result.anomaly)
        self.assertGreaterEqual(result.confidence, 0.4)

    # --- Brute Force (UC4) ---

    def test_brute_force_xgboost_anomaly(self):
        """Verify Brute Force detection with scaling and optimized threshold."""
        inp = BruteForceInput(
            timestamp=datetime.now().timestamp(),
            source_ip="192.168.1.50",
            dest_ip="192.168.1.100",
            source_port=54321,
            dest_port=22,
            features={"f1": 5.0}
        )
        
        mock_model = MagicMock()
        mock_model.predict_proba.return_value = np.array([[0.9, 0.1]]) # 10% prob
        mock_scaler = MagicMock()
        mock_scaler.transform.side_effect = lambda x: x

        bf_art = {
            "model": mock_model,
            "scaler": mock_scaler,
            "threshold": 0.05 # Should trigger since 0.1 > 0.05
        }
        self.repo.get.side_effect = lambda k: {
            "brute_force_xgb": bf_art,
            "ddos_feature_cols": ["f1"]
        }.get(k)
        
        result = detect_brute_force(inp, self.repo)
        self.assertTrue(result.anomaly)

    # --- Traffic Spike (UC1) ---

    def test_traffic_spike_ensemble(self):
        """Verify Traffic Spike Ensemble (V2) detection."""
        # History: [10, 10, 10, 10, 10, 10, 10, 10, 10, 100] (spike at the end)
        counts = [10.0] * 9 + [100.0]
        inp = TrafficInput(
            window_start=datetime.now(),
            window_end=datetime.now() + timedelta(minutes=5),
            req_counts=counts
        )
        
        thresholds = TrafficThresholds(
            min_history=5,
            z_score_extreme=10.0,
            z_score_high=5.0,
            z_score_flag=2.0,
            iqr_multiplier=1.5,
            ema_alpha=0.3,
            ema_warmup=3,
            ema_dev_threshold=2.0,
            seasonal_z_threshold=2.5,
            seasonal_min_bucket_size=3,
            min_weighted_chosen=1.5, # Need at least 1.5 weights to fire
            weight_zscore=1.0,
            weight_iqr=1.0,
            weight_ema=0.5,
            weight_seasonal=1.0
        )
        
        # Seasonal bucket: median=10, IQR=2
        # (100 - 10) / (0.7413 * 2) = 60.7 (very high robust z)
        seasonal_bucket = [8.0, 10.0, 10.0, 12.0]
        
        result = detect_traffic(inp, thresholds, seasonal_bucket)
        
        self.assertTrue(result.anomaly)
        self.assertIn("z_score", result.method_flags)
        self.assertTrue(result.method_flags["seasonal"])
        self.assertGreaterEqual(result.severity, Severity.HIGH)

if __name__ == "__main__":
    unittest.main()
