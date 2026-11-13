package com.cafeorbe.wallet.consumer;

import com.cafeorbe.contracts.EventoEnvelope;
import com.cafeorbe.contracts.eventos.UsuarioRegistrado;
import com.cafeorbe.wallet.config.RabbitConfig;
import com.cafeorbe.wallet.service.ProcesadorDeEventos;
import com.cafeorbe.wallet.seguridad.CorrelacionDeEventos;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class UsuarioRegistradoConsumer {

    private final ProcesadorDeEventos procesador;

    public UsuarioRegistradoConsumer(ProcesadorDeEventos procesador) {
        this.procesador = procesador;
    }

    /** OBS-01: el correlationId del evento queda en cada línea del log mientras se procesa. */
    @RabbitListener(queues = RabbitConfig.COLA_USUARIO_REGISTRADO)
    public void alRecibir(EventoEnvelope<UsuarioRegistrado> evento,
                          @Header(name = CorrelacionDeEventos.CABECERA, required = false) String correlationId) {
        CorrelacionDeEventos.con(correlationId, evento.eventId(), () -> procesador.usuarioRegistrado(evento));
    }

    /** Un evento sin cabecera de correlación (eventos anteriores al Sprint 2): se usa su eventId. */
    public void alRecibir(EventoEnvelope<UsuarioRegistrado> evento) {
        alRecibir(evento, null);
    }
}
