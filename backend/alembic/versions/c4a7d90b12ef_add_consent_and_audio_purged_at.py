"""add recordings.consent_confirmed and recordings.audio_purged_at

Revision ID: c4a7d90b12ef
Revises: 9c5e2b7d41a0
Create Date: 2026-07-23 00:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision: str = 'c4a7d90b12ef'
down_revision: Union[str, Sequence[str], None] = '9c5e2b7d41a0'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    # Pre-existing rows were captured under the in-app consent dialog but
    # never sent the flag; they stay false (unattested) on purpose.
    op.add_column(
        'recordings',
        sa.Column('consent_confirmed', sa.Boolean(), nullable=False, server_default=sa.false()),
    )
    op.add_column(
        'recordings',
        sa.Column('audio_purged_at', sa.DateTime(timezone=True), nullable=True),
    )


def downgrade() -> None:
    """Downgrade schema."""
    op.drop_column('recordings', 'audio_purged_at')
    op.drop_column('recordings', 'consent_confirmed')
