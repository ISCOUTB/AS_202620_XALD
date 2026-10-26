FROM python:3.12-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

# Crear usuario no privilegiado (docker:S6471)
RUN adduser --disabled-password --gecos "" appuser

# Instalación directa de dependencias con versión exacta (elimina la alerta de SonarQube)
RUN pip install --no-cache-dir --only-binary :all: \
    fastapi==0.115.0 \
    "uvicorn[standard]==0.30.0" \
    pydantic==2.9.0 \
    prometheus-client==0.20.0 \
    httpx==0.27.0

COPY app ./app

# Asignar propiedad del directorio al usuario no root y cambiar de usuario
RUN chown -R appuser:appuser /app
USER appuser

EXPOSE 8000

CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000} --no-access-log"]
