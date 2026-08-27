"""add worker retry and dead-letter metadata

Revision ID: a83b97d1e246
Revises: e1f8a26c53da
Create Date: 2026-08-27 00:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = "a83b97d1e246"
down_revision: Union[str, Sequence[str], None] = "e1f8a26c53da"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.alter_column(
        "recordings",
        "status",
        existing_type=sa.String(length=12),
        type_=sa.String(length=20),
        existing_nullable=False,
    )
    op.add_column(
        "recordings",
        sa.Column("retry_count", sa.Integer(), nullable=False, server_default="0"),
    )
    op.add_column(
        "recordings", sa.Column("last_error_type", sa.String(length=100), nullable=True)
    )
    op.add_column(
        "recordings",
        sa.Column("dead_lettered_at", sa.DateTime(timezone=True), nullable=True),
    )


def downgrade() -> None:
    op.drop_column("recordings", "dead_lettered_at")
    op.drop_column("recordings", "last_error_type")
    op.drop_column("recordings", "retry_count")
    op.execute(
        "UPDATE recordings SET status = 'failed' "
        "WHERE status IN ('retrying', 'dead_lettered')"
    )
    op.alter_column(
        "recordings",
        "status",
        existing_type=sa.String(length=20),
        type_=sa.String(length=12),
        existing_nullable=False,
    )