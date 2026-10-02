package com.cafeorbe.wallet.consumer;

import com.cafeorbe.contracts.EventoEnvelope;
import com.cafeorbe.contracts.eventos.SubastaCerrada;
import com.cafeorbe.wallet.config.RabbitConfig;
import com.cafeorbe.wallet.service.ProcesadorDeEventos;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** HU-20: al cerrar una subasta con ganador, se le cobran los Orbes de la puja ganadora. */
@Component
public class SubastaCerradaConsumer {

    private final ProcesadorDeEventos procesador;

    public SubastaCerradaConsumer(ProcesadorDeEventos procesador) {
        this.procesador = procesador;
    }

    @RabbitListener(queues = RabbitConfig.COLA_SUBASTA_CERRADA)
    public void alRecibir(EventoEnvelope<SubastaCerrada> evento) {
        procesador.subastaCerrada(evento);
    }
}
