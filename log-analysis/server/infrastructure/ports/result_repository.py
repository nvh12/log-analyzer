import json
from application.ports.result_repository_port import ResultRepositoryPort
from domain.models.results import DetectionResult
from infrastructure.config import postgres

_INSERT = """
    INSERT INTO analysis.detection_results (
        detection_type, severity, anomaly, confidence, network_layer,
        source_ip, dest_ip, dest_port, method_flags,
        log_timestamp, window_start, window_end, detected_at
    ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9::jsonb, $10, $11, $12, $13)
"""


class PostgresDetectionResultRepository(ResultRepositoryPort):
    async def save(self, result: DetectionResult) -> None:
        if postgres.pool is None:
            raise RuntimeError("PostgreSQL pool is not initialized")

        network_layer = result.network_layer.value
        method_flags = getattr(result, "method_flags", None)

        async with postgres.pool.acquire() as conn:
            await conn.execute(
                _INSERT,
                result.detection_type.value,
                result.severity.value,
                result.anomaly,
                getattr(result, "confidence", None),
                network_layer,
                result.source_ip,
                getattr(result, "dest_ip", None),
                getattr(result, "dest_port", None),
                json.dumps(method_flags) if method_flags is not None else None,
                result.log_timestamp,
                result.window_start,
                result.window_end,
                result.detected_at,
            )
