#!/bin/sh
set -e

# Wait for MySQL to be ready before running migrations.
# No-op when DATABASE_URL is SQLite or unset.
if echo "${DATABASE_URL:-}" | grep -q "mysql"; then
  echo "Waiting for MySQL..."
  RETRIES=60
  until python -c "
import os, sys, socket
from urllib.parse import urlparse
host = urlparse(os.environ['DATABASE_URL']).hostname
try:
    socket.create_connection((host, 3306), timeout=1).close()
    sys.exit(0)
except OSError:
    sys.exit(1)
"; do
    RETRIES=$((RETRIES - 1))
    if [ "$RETRIES" -le 0 ]; then
      echo "MySQL did not become ready in time. Exiting." >&2
      exit 1
    fi
    sleep 1
  done
  echo "MySQL is ready."
fi

python -m alembic upgrade head
exec python run.py
