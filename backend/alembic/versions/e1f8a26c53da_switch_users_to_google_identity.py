"""switch users to google sign-in identity

Revision ID: e1f8a26c53da
Revises: c4a7d90b12ef
Create Date: 2026-07-24 00:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision: str = 'e1f8a26c53da'
down_revision: Union[str, Sequence[str], None] = 'c4a7d90b12ef'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    # Phone-OTP identities are incompatible with Google subjects; no data to
    # carry over (pre-pilot), so rename the uid column and swap phone for email.
    op.drop_index(op.f('ix_users_firebase_uid'), table_name='users')
    op.alter_column('users', 'firebase_uid', new_column_name='google_sub')
    op.create_index(op.f('ix_users_google_sub'), 'users', ['google_sub'], unique=True)
    op.drop_column('users', 'phone_number')
    op.add_column('users', sa.Column('email', sa.String(length=320), nullable=True))
    op.add_column('users', sa.Column('display_name', sa.String(length=200), nullable=True))


def downgrade() -> None:
    """Downgrade schema."""
    op.drop_column('users', 'display_name')
    op.drop_column('users', 'email')
    op.add_column('users', sa.Column('phone_number', sa.String(length=20), nullable=True))
    op.drop_index(op.f('ix_users_google_sub'), table_name='users')
    op.alter_column('users', 'google_sub', new_column_name='firebase_uid')
    op.create_index(op.f('ix_users_firebase_uid'), 'users', ['firebase_uid'], unique=True)
