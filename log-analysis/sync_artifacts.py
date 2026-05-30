import hashlib
import io
import os
import json
import joblib
import logging
from typing import Dict, Any
from minio import Minio
from minio.error import S3Error

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger("sync_artifacts")

# --- Configuration ---
MINIO_CONFIG = {
    "endpoint": "localhost:9000",
    "access_key": "minioadmin",
    "secret_key": "minioadmin",
    "secure": False,
    "bucket": "models"
}

# Mapping of Training Output Files -> MinIO Keys
# Format: (Source Local Path, Target MinIO Object Key)
ARTIFACT_MAP = [
    # Flow Track (UC2 & UC4)
    ("training/ddos/models/ddos_xgboost.pkl",              "flow/ddos/xgboost.pkl"),
    ("training/ddos/data/class_stats.json",                "flow/ddos/class_stats.json"),
    ("training/bruteforce/models/brute_force_xgboost.pkl", "flow/bruteforce/xgboost.pkl"),
    ("training/bruteforce/data/class_stats.json",          "flow/bruteforce/class_stats.json"),
    ("training/ddos/data/flow_feature_cols.json",          "flow/feature_cols.json"),

    # HTTP Track (UC3)
    ("training/webattack/models/web_attack_xgboost.pkl", "webattack/xgboost.pkl"),
    ("training/webattack/models/web_vocab.json",        "webattack/vocab.json"),
    ("training/webattack/models/web_attack_if.pkl",     "webattack/isolation_forest.pkl"),
    ("training/webattack/models/web_attack_ocsvm.pkl",  "webattack/one_class_svm.pkl"),
    
    # Traffic Track (UC1)
    ("training/trafficspike/outputs/ensemble_calibration.json", "trafficspike/ensemble_calibration.json"),
]

def compute_sha256(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def get_minio_client():
    return Minio(
        MINIO_CONFIG["endpoint"],
        access_key=MINIO_CONFIG["access_key"],
        secret_key=MINIO_CONFIG["secret_key"],
        secure=MINIO_CONFIG["secure"]
    )

def sync():
    client = get_minio_client()
    
    # Ensure bucket exists
    if not client.bucket_exists(MINIO_CONFIG["bucket"]):
        logger.info(f"Creating bucket: {MINIO_CONFIG['bucket']}")
        client.make_bucket(MINIO_CONFIG["bucket"])

    base_dir = os.getcwd()
    success_count = 0
    checksums: Dict[str, str] = {}

    for local_rel_path, minio_key in ARTIFACT_MAP:
        local_path = os.path.join(base_dir, local_rel_path.replace("/", os.sep))

        if not os.path.exists(local_path):
            logger.warning(f"SKIPPING: Local file not found: {local_rel_path}")
            continue

        try:
            logger.info(f"UPLOADING: {local_rel_path} -> s3://{MINIO_CONFIG['bucket']}/{minio_key}")
            client.fput_object(
                MINIO_CONFIG["bucket"],
                minio_key,
                local_path,
            )
            if minio_key.endswith(".pkl"):
                checksums[minio_key] = compute_sha256(local_path)
                logger.info(f"Checksum for {minio_key}: {checksums[minio_key]}")
            success_count += 1
        except Exception as e:
            logger.error(f"FAILED to upload {local_rel_path}: {e}")

    # Upload checksum manifest so the server can verify pickles before deserializing
    if checksums:
        try:
            manifest = json.dumps(checksums, indent=2).encode()
            client.put_object(
                MINIO_CONFIG["bucket"],
                "checksums.json",
                io.BytesIO(manifest),
                length=len(manifest),
                content_type="application/json",
            )
            logger.info(f"Uploaded checksums.json with {len(checksums)} entries.")
        except Exception as e:
            logger.error(f"FAILED to upload checksums.json: {e}")

    logger.info(f"Sync complete. {success_count}/{len(ARTIFACT_MAP)} artifacts deployed.")

if __name__ == "__main__":
    sync()
