"""add denied_tokens table for JWT revocation

Revision ID: b3f9d2e1a4c7
Revises: 845373ac03c6
Create Date: 2026-05-10

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = 'b3f9d2e1a4c7'
down_revision: Union[str, Sequence[str], None] = '845373ac03c6'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        'denied_tokens',
        sa.Column('id', sa.Integer(), autoincrement=True, nullable=False),
        sa.Column('jti', sa.String(length=36), nullable=False),
        sa.Column('expires_at', sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint('id'),
    )
    op.create_index(op.f('ix_denied_tokens_jti'), 'denied_tokens', ['jti'], unique=True)


def downgrade() -> None:
    op.drop_index(op.f('ix_denied_tokens_jti'), table_name='denied_tokens')
    op.drop_table('denied_tokens')
