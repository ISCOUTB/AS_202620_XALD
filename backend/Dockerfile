FROM python:3.12-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

# Crear usuario no privilegiado (resuelve docker:S6471)
RUN adduser --disabled-password --gecos "" appuser

COPY requirements.lock .

# Instalación desde el archivo de dependencias bloqueado (.lock)
RUN pip install --no-cache-dir --only-binary :all: -r requirements.lock

COPY app ./app

# Asignar propiedad del directorio al usuario no root
RUN chown -R appuser:appuser /app
USER appuser

EXPOSE 8000

CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000} --no-access-log"]
