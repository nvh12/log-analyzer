FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt
RUN useradd --system --no-create-home appuser
COPY --chown=appuser:appuser . ./
USER appuser
EXPOSE 8001
CMD ["sh", "-c", "uvicorn main:app --host 0.0.0.0 --port 8001 --workers ${UVICORN_WORKERS:-1}"]
