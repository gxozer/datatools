"""
run.py — Application entry point.

Run with: python run.py
Or via Flask CLI: flask --app run:app run
"""

import os
from app import create_app

# Create the Flask app using the factory
app = create_app()

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5001))
    debug = os.environ.get("FLASK_DEBUG", "false").lower() == "true"
    app.run(host="0.0.0.0", port=port, debug=debug)
