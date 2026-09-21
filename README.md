# cafeorbe-wallet-service

Billetera de Orbes. **Arquitectura:** capas + patrón Ledger. Puerto `8083`.

> Este servicio reemplaza a `cafeorbe-payment-service` (renombre indicado en `ARQUITECTURA_CafeOrbe_MVP.md`). En el MVP no hay pagos con dinero real.

| Historia | Qué hace |
|---|---|
| HU-06 | `GET /api/orbes/saldo` devuelve el saldo del usuario autenticado (0 si aún no tiene cuenta). `GET /api/orbes/movimientos` lista su historial. |
| HU-07 | Consume `UsuarioRegistrado` y, si el rol es Comprador, asigna `WALLET_SALDO_INICIAL` (1000 por defecto) registrando el movimiento `CARGA_AUTOMATICA`. |
| HU-14 (apoyo) | `GET /internal/orbes/{usuarioId}/saldo`: consulta síncrona de auction. **No se enruta por el gateway.** |

## Garantías

- **Ledger:** cada operación es un movimiento inmutable y el saldo materializado se actualiza en la misma transacción. El saldo nunca queda negativo (`CHECK`).
- **Idempotencia:** el `eventId` de cada evento se guarda en la misma transacción que su efecto, y `unique(usuario_id, tipo, referencia)` impide una segunda carga inicial aunque llegue un evento distinto del mismo usuario.
- Los eventos que fallan 5 veces se descartan (sin cola de mensajes muertos en el MVP).

Pendiente del Sprint 2: cobro al cierre (HU-20), `Movimiento.Tipo.COBRO` ya está previsto con `referencia = subastaId`.

## Ejecutar

```bash
mvn spring-boot:run      # requiere Postgres (wallet_db) y RabbitMQ
mvn test                 # H2, sin infraestructura
```
