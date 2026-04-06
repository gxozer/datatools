"""
run.py — Application entry point.

Run with: python run.py
Or via Flask CLI: flask --app run:app run
"""

import os
from app import create_app

# Create the Flask app using the factory
app = create_app()

# In E2E test environments, skip Alembic and create tables directly.
# Set CREATE_TABLES=1 to enable (never use in production).
if os.environ.get("CREATE_TABLES") == "1":
    from app.database import db
    with app.app_context():
        db.create_all()

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5001))
    debug = os.environ.get("FLASK_DEBUG", "false").lower() == "true"
    app.run(host="0.0.0.0", port=port, debug=debug)
