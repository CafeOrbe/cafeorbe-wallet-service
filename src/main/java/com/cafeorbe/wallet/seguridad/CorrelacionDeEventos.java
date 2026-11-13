package com.cafeorbe.wallet.seguridad;

import org.slf4j.MDC;
import org.springframework.amqp.core.MessagePostProcessor;

import java.util.UUID;

/**
 * OBS-01: el {@code correlationId} también viaja por el broker (cabecera {@code x-correlation-id}). Al consumir un
 * evento queda en el MDC, así cada línea del log JSON de wallet lo lleva, y al publicar se copia en la cabecera del
 * evento nuevo. Si el evento no lo trae se usa su {@code eventId}: ningún evento crítico queda sin identificador.
 */
public final class CorrelacionDeEventos {

    public static final String CABECERA = "x-correlation-id";

    private CorrelacionDeEventos() {
    }

    /** Ejecuta el procesamiento del evento con su correlationId en el MDC y lo quita al terminar. */
    public static void con(String correlationId, UUID eventId, Runnable procesamiento) {
        String id = correlationId != null && !correlationId.isBlank() ? correlationId : "evento-" + eventId;
        String anterior = MDC.get(FiltroDeCorrelacion.MDC_CLAVE);
        MDC.put(FiltroDeCorrelacion.MDC_CLAVE, id);
        try {
            procesamiento.run();
        } finally {
            if (anterior == null) {
                MDC.remove(FiltroDeCorrelacion.MDC_CLAVE);
            } else {
                MDC.put(FiltroDeCorrelacion.MDC_CLAVE, anterior);
            }
        }
    }

    /** Copia en la cabecera del mensaje el correlationId que se está procesando (si hay uno). */
    public static MessagePostProcessor propagar(String correlationId) {
        return mensaje -> {
            if (correlationId != null) {
                mensaje.getMessageProperties().setHeader(CABECERA, correlationId);
            }
            return mensaje;
        };
    }
}
