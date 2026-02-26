FROM python:3.11-slim

WORKDIR /app

# Copy ISCS files
COPY ISCS.py .
COPY requirements.txt . 2>/dev/null || echo "No requirements.txt"

# Install dependencies if requirements.txt exists
RUN if [ -f requirements.txt ]; then pip install --no-cache-dir -r requirements.txt; fi

# Expose port
EXPOSE 14002

# Run ISCS
ENTRYPOINT ["python3", "ISCS.py", "/app/config.json"]
