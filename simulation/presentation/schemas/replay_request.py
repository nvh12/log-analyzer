from ipaddress import ip_address

from pydantic import BaseModel, Field, field_validator


class ReplayRequest(BaseModel):
    source_key: str = Field(description="MinIO object key of the CSV to replay (e.g. 'flow/ddos/train.csv')")
    count: int = Field(default=0, ge=0, description="Rows to send; 0 = all rows once")
    rate_per_second: float = Field(default=10.0, gt=0, le=10000)
    source_ip: str | None = Field(default=None, description="Override source IP for all rows; random if omitted")
    dest_ip: str | None = Field(default=None, description="Override destination IP for all rows; random if omitted")

    @field_validator("source_key")
    @classmethod
    def validate_source_key(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("source_key must not be empty")
        return v

    @field_validator("source_ip", "dest_ip", mode="before")
    @classmethod
    def validate_ip(cls, v: str | None) -> str | None:
        if v is None:
            return None
        try:
            ip_address(v)
        except ValueError:
            raise ValueError("must be a valid IPv4 or IPv6 address")
        return v
