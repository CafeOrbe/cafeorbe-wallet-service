package com.cafeorbe.wallet.service;

import com.cafeorbe.contracts.EventoEnvelope;
import com.cafeorbe.contracts.Rol;
import com.cafeorbe.contracts.eventos.UsuarioRegistrado;
import com.cafeorbe.wallet.model.EventoProcesado;
import com.cafeorbe.wallet.repository.EventoProcesadoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aplica los eventos de forma idempotente: el id del evento se guarda en la misma transacción
 * que el efecto, así un evento repetido se descarta sin volver a tocar el ledger.
 */
@Service
public class ProcesadorDeEventos {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorDeEventos.class);

    private final EventoProcesadoRepository procesados;
    private final Ledger ledger;

    public ProcesadorDeEventos(EventoProcesadoRepository procesados, Ledger ledger) {
        this.procesados = procesados;
        this.ledger = ledger;
    }

    @Transactional
    public void usuarioRegistrado(EventoEnvelope<UsuarioRegistrado> evento) {
        if (procesados.existsById(evento.eventId())) {
            log.debug("Evento {} ya procesado, se descarta", evento.eventId());
            return;
        }
        UsuarioRegistrado datos = evento.datos();
        if (Rol.COMPRADOR.name().equals(datos.rol())) {
            boolean cargado = ledger.cargarSaldoInicial(datos.usuarioId());
            log.info("Usuario {} registrado: carga automática {}", datos.usuarioId(), cargado ? "aplicada" : "ya existía");
        }
        procesados.save(new EventoProcesado(evento.eventId()));
    }
}
