FROM python:3.11-slim
WORKDIR /app
COPY ISCS.py .
COPY requirements.txt* ./
RUN if [ -f requirements.txt ]; then pip install --no-cache-dir -r requirements.txt; fi
EXPOSE 14002
ENTRYPOINT ["python3", "ISCS.py", "/app/config.json"]
