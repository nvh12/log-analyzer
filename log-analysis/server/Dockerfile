FROM python:3.12-slim
WORKDIR /app
COPY server/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt
RUN useradd --system --no-create-home appuser
COPY --chown=appuser:appuser server/ ./
USER appuser
EXPOSE 8000
CMD ["sh", "-c", "uvicorn main:app --host 0.0.0.0 --port 8000 --workers ${UVICORN_WORKERS:-1}"]
