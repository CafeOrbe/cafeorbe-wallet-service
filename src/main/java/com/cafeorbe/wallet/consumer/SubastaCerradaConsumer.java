package com.cafeorbe.wallet.consumer;

import com.cafeorbe.contracts.EventoEnvelope;
import com.cafeorbe.contracts.eventos.SubastaCerrada;
import com.cafeorbe.wallet.config.RabbitConfig;
import com.cafeorbe.wallet.service.ProcesadorDeEventos;
import com.cafeorbe.wallet.seguridad.CorrelacionDeEventos;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** HU-20: al cerrar una subasta con ganador, se le cobran los Orbes de la puja ganadora. */
@Component
public class SubastaCerradaConsumer {

    private final ProcesadorDeEventos procesador;

    public SubastaCerradaConsumer(ProcesadorDeEventos procesador) {
        this.procesador = procesador;
    }

    /** OBS-01: el correlationId del evento queda en cada línea del log mientras se procesa. */
    @RabbitListener(queues = RabbitConfig.COLA_SUBASTA_CERRADA)
    public void alRecibir(EventoEnvelope<SubastaCerrada> evento,
                          @Header(name = CorrelacionDeEventos.CABECERA, required = false) String correlationId) {
        CorrelacionDeEventos.con(correlationId, evento.eventId(), () -> procesador.subastaCerrada(evento));
    }

    /** Un evento sin cabecera de correlación (eventos anteriores al Sprint 2): se usa su eventId. */
    public void alRecibir(EventoEnvelope<SubastaCerrada> evento) {
        alRecibir(evento, null);
    }
}
