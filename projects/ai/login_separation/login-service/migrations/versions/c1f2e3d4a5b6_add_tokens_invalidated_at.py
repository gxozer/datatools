"""add tokens_invalidated_at to users for session invalidation on password reset

Revision ID: c1f2e3d4a5b6
Revises: b3f9d2e1a4c7
Create Date: 2026-06-01

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = 'c1f2e3d4a5b6'
down_revision: Union[str, Sequence[str], None] = 'b3f9d2e1a4c7'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        'users',
        sa.Column('tokens_invalidated_at', sa.DateTime(timezone=True), nullable=True),
    )


def downgrade() -> None:
    op.drop_column('users', 'tokens_invalidated_at')
