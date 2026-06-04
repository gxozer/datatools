"""
database.py — SQLAlchemy database wrapper.

Provides a Database class that encapsulates the SQLAlchemy instance.
Attribute access is proxied to the inner SQLAlchemy object so callers
can use db.Model, db.Column, db.session, etc. as normal.
"""

from flask_mail import Mail
from flask_sqlalchemy import SQLAlchemy


class Database:
    def __init__(self):
        self._db = SQLAlchemy()

    def init_app(self, app):
        self._db.init_app(app)

    def __getattr__(self, name):
        return getattr(self._db, name)


db = Database()
mail = Mail()
