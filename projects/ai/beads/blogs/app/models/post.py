from datetime import datetime, timezone
from app.extensions import db


class Post(db.Model):
    __tablename__ = "posts"

    id = db.Column(db.Integer, primary_key=True)
    title = db.Column(db.String(200), nullable=False)
    body = db.Column(db.Text, nullable=False)
    created_at = db.Column(db.DateTime, default=lambda: datetime.now(timezone.utc))
    updated_at = db.Column(
        db.DateTime,
        default=lambda: datetime.now(timezone.utc),
        onupdate=lambda: datetime.now(timezone.utc),
    )
    author_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=False)

    author = db.relationship("User", back_populates="posts")
    comments = db.relationship(
        "Comment", back_populates="post", cascade="all, delete-orphan", lazy="dynamic"
    )
