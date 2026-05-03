"""String keys used to retrieve artifacts from ModelRepository.

All services must import from here instead of using bare string literals,
so that key names have a single authoritative definition.
"""

DDOS_MODEL = "ddos_xgb"
DDOS_FEATURE_COLS = "ddos_feature_cols"

# UC4 intentionally reuses the same feature column list as UC2 so both
# classifiers can run from a single feature-extraction pass.
BRUTE_FORCE_MODEL = "brute_force_xgb"
BRUTE_FORCE_FEATURE_COLS = DDOS_FEATURE_COLS

WEB_MODEL = "web_xgb"
WEB_VOCAB = "web_vocab"
