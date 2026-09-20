from fastapi import FastAPI, status
from app.dtos import TransaccionDTO, RespuestaSincronizacion

app = FastAPI(
    title="API de Sincronización Financiera - Proyecto XALD",
    version="1.0.0"
)

@app.post(
    "/api/v1/transacciones",
    status_code=status.HTTP_202_ACCEPTED,
    response_model=RespuestaSincronizacion,
    summary="Recibir transacción financiera desde la cola de sincronización"
)
async def registrar_transaccion(transaccion: TransaccionDTO):
    return RespuestaSincronizacion(
        estado="ACEPTADO",
        id_transaccion=transaccion.id_transaccion,
        mensaje="Transacción recibida y encolada exitosamente para persistencia."
    )