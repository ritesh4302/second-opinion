import asyncio
from functools import lru_cache
from typing import Protocol

import boto3
from botocore.client import Config
from botocore.exceptions import ClientError

from app.settings import get_settings


class ObjectStorage(Protocol):
    """Port for audio blob storage; tests provide an in-memory fake."""

    async def put(self, key: str, data: bytes, content_type: str) -> None: ...

    # Sync on purpose: only the Celery worker (sync context) reads audio back.
    def get(self, key: str) -> bytes: ...


class S3ObjectStorage:
    """boto3 is sync; calls are offloaded so the event loop never blocks."""

    def __init__(self) -> None:
        settings = get_settings()
        self._bucket = settings.s3_bucket
        self._client = boto3.client(
            "s3",
            endpoint_url=settings.s3_endpoint_url,
            aws_access_key_id=settings.s3_access_key,
            aws_secret_access_key=settings.s3_secret_key,
            config=Config(signature_version="s3v4"),
        )

    def ensure_bucket(self) -> None:
        try:
            self._client.head_bucket(Bucket=self._bucket)
        except ClientError:
            self._client.create_bucket(Bucket=self._bucket)

    async def put(self, key: str, data: bytes, content_type: str) -> None:
        await asyncio.to_thread(
            self._client.put_object,
            Bucket=self._bucket,
            Key=key,
            Body=data,
            ContentType=content_type,
        )

    def get(self, key: str) -> bytes:
        response = self._client.get_object(Bucket=self._bucket, Key=key)
        return response["Body"].read()


@lru_cache
def _default_storage() -> S3ObjectStorage:
    return S3ObjectStorage()


def get_storage() -> ObjectStorage:
    """FastAPI dependency; overridden in tests."""
    return _default_storage()
