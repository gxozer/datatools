#!/bin/sh
set -e

# Wait for MySQL to be ready before running migrations.
# No-op when DATABASE_URL is SQLite or unset.
if echo "${DATABASE_URL:-}" | grep -q "mysql"; then
  echo "Waiting for MySQL..."
  until python -c "
import os, sys, socket
url = os.environ['DATABASE_URL']
host = url.split('@')[1].split(':')[0].split('/')[0]
try:
    socket.create_connection((host, 3306), timeout=1).close()
    sys.exit(0)
except OSError:
    sys.exit(1)
"; do
    sleep 1
  done
  echo "MySQL is ready."
fi

python -m alembic upgrade head
exec python run.py
