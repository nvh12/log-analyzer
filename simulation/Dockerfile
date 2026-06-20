FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt
RUN useradd --system --no-create-home appuser
COPY --chown=appuser:appuser . ./
USER appuser
EXPOSE 8001
# `exec` replaces this shell with gunicorn so it becomes PID 1 — otherwise
# `sh` stays PID 1 and never forwards docker's SIGTERM to gunicorn, so the
# container is hard-killed on every restart without running ASGI shutdown
# (orphaning the baseline's Redis lock instead of releasing it).
CMD ["sh", "-c", "exec gunicorn main:app -k uvicorn.workers.UvicornWorker --workers ${UVICORN_WORKERS:-1} --bind 0.0.0.0:8001 --pid /tmp/gunicorn.pid --timeout 120"]
