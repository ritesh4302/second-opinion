"""add users table and recordings.owner_id

Revision ID: 9c5e2b7d41a0
Revises: 37ecadaa3a8e
Create Date: 2026-07-21 00:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision: str = '9c5e2b7d41a0'
down_revision: Union[str, Sequence[str], None] = '37ecadaa3a8e'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    op.create_table('users',
    sa.Column('id', sa.Uuid(), nullable=False),
    sa.Column('firebase_uid', sa.String(length=128), nullable=False),
    sa.Column('phone_number', sa.String(length=20), nullable=True),
    sa.Column('role', sa.Enum('PHARMACIST', 'ADMIN', name='userrole', native_enum=False, length=20), nullable=False),
    sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
    sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_users_firebase_uid'), 'users', ['firebase_uid'], unique=True)
    op.add_column('recordings', sa.Column('owner_id', sa.Uuid(), nullable=True))
    op.create_index(op.f('ix_recordings_owner_id'), 'recordings', ['owner_id'], unique=False)
    op.create_foreign_key(
        'fk_recordings_owner_id_users', 'recordings', 'users', ['owner_id'], ['id'],
        ondelete='SET NULL',
    )


def downgrade() -> None:
    """Downgrade schema."""
    op.drop_constraint('fk_recordings_owner_id_users', 'recordings', type_='foreignkey')
    op.drop_index(op.f('ix_recordings_owner_id'), table_name='recordings')
    op.drop_column('recordings', 'owner_id')
    op.drop_index(op.f('ix_users_firebase_uid'), table_name='users')
    op.drop_table('users')
