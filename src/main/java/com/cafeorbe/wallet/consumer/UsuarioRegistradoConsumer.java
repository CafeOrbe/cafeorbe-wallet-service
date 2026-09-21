package com.cafeorbe.wallet.consumer;

import com.cafeorbe.contracts.EventoEnvelope;
import com.cafeorbe.contracts.eventos.UsuarioRegistrado;
import com.cafeorbe.wallet.config.RabbitConfig;
import com.cafeorbe.wallet.service.ProcesadorDeEventos;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class UsuarioRegistradoConsumer {

    private final ProcesadorDeEventos procesador;

    public UsuarioRegistradoConsumer(ProcesadorDeEventos procesador) {
        this.procesador = procesador;
    }

    @RabbitListener(queues = RabbitConfig.COLA_USUARIO_REGISTRADO)
    public void alRecibir(EventoEnvelope<UsuarioRegistrado> evento) {
        procesador.usuarioRegistrado(evento);
    }
}
