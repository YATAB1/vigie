FROM python:3.13-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app
COPY pyproject.toml ./
COPY src ./src
RUN pip install --no-cache-dir .

# Scans handle untrusted network responses: never run them as root.
RUN useradd --system --no-create-home vigie
USER vigie

CMD ["vigie-worker"]
