FROM python:3.12-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

# 1. Crear usuario no privilegiado para evitar ejecutar como root (docker:S6471)
RUN adduser --disabled-password --gecos "" appuser

COPY requirements.txt .

# 2 y 3. Usar --only-binary :all: para evitar scripts de setup no seguros
RUN pip install --no-cache-dir --only-binary :all: -r requirements.txt

COPY app ./app

# Asignar propiedad del directorio al usuario no root y cambiar de usuario
RUN chown -R appuser:appuser /app
USER appuser

EXPOSE 8000

CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000} --no-access-log"]
