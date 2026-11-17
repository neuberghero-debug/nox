FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY main.py .
ENV NOX_DB=/data/licenses.db NOX_PORT=8787
VOLUME /data
EXPOSE 8787
USER nobody
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8787"]
