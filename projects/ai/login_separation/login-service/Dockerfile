FROM python:3.11-slim AS base

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

RUN chmod +x entrypoint.sh \
    && addgroup --system --gid 1000 app \
    && adduser --system --uid 1000 --ingroup app app \
    && mkdir -p /app/instance \
    && chown -R app:app /app

USER app

EXPOSE 5001

HEALTHCHECK --interval=10s --timeout=3s --start-period=15s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:5001/api/health')" || exit 1

CMD ["./entrypoint.sh"]

# Test stage — base + dev dependencies (pytest, etc.)
FROM base AS test

USER root
COPY requirements-dev.txt .
RUN pip install --no-cache-dir -r requirements-dev.txt
USER app

# Debug stage — adds remote-pdb on top of the test image
FROM test AS debug

EXPOSE 4444
