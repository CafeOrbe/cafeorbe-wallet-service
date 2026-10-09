package com.cafeorbe.wallet.service;

import com.cafeorbe.contracts.EventoEnvelope;
import com.cafeorbe.contracts.Eventos;
import com.cafeorbe.contracts.Rol;
import com.cafeorbe.contracts.eventos.OrbesAbonados;
import com.cafeorbe.contracts.eventos.OrbesCobrados;
import com.cafeorbe.contracts.eventos.SubastaCerrada;
import com.cafeorbe.contracts.eventos.UsuarioRegistrado;
import com.cafeorbe.wallet.model.EventoProcesado;
import com.cafeorbe.wallet.repository.EventoProcesadoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

/**
 * Aplica los eventos de forma idempotente: el id del evento se guarda en la misma transacción
 * que el efecto, así un evento repetido se descarta sin volver a tocar el ledger.
 */
@Service
public class ProcesadorDeEventos {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorDeEventos.class);

    private final EventoProcesadoRepository procesados;
    private final Ledger ledger;
    private final RabbitTemplate rabbit;

    public ProcesadorDeEventos(EventoProcesadoRepository procesados, Ledger ledger, RabbitTemplate rabbit) {
        this.procesados = procesados;
        this.ledger = ledger;
        this.rabbit = rabbit;
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

    /**
     * HU-20: cobra al ganador la puja con la que ganó. Los compradores que no ganaron no se tocan, y una
     * subasta desierta no cobra a nadie.
     *
     * <p>HU-24: lo cobrado se abona al Subastador en la misma transacción, así los Orbes nunca salen de una
     * cuenta sin llegar a la otra. Si no se pudo cobrar, no hay abono.
     */
    @Transactional
    public void subastaCerrada(EventoEnvelope<SubastaCerrada> evento) {
        if (procesados.existsById(evento.eventId())) {
            log.debug("Evento {} ya procesado, se descarta", evento.eventId());
            return;
        }
        SubastaCerrada datos = evento.datos();
        if (datos.ganadorId() != null && datos.montoFinal() != null) {
            Ledger.Cobro cobro = ledger.cobrar(datos.ganadorId(), datos.montoFinal(), datos.subastaId().toString());
            switch (cobro.estado()) {
                case COBRADO -> {
                    log.info("Subasta {}: cobrados {} Orbes a {}", datos.subastaId(), datos.montoFinal(), datos.ganadorId());
                    avisarTrasConfirmar(Eventos.ORBES_COBRADOS, datos.subastaId(),
                            new OrbesCobrados(datos.subastaId(), datos.ganadorId(), datos.montoFinal(), cobro.saldo()));
                    abonarAlSubastador(datos);
                }
                case YA_COBRADO -> log.info("Subasta {}: el cobro a {} ya existía", datos.subastaId(), datos.ganadorId());
                // Los Orbes no se reservan al pujar: el ganador pudo gastar su saldo en otra subasta antes del cierre.
                // No se cobra parcialmente ni se reintenta: el saldo no va a aparecer solo. Queda registrado para revisarlo.
                case SALDO_INSUFICIENTE -> log.error("Subasta {}: no se pudo cobrar {} Orbes a {}, su saldo es {}",
                        datos.subastaId(), datos.montoFinal(), datos.ganadorId(), cobro.saldo());
            }
        }
        procesados.save(new EventoProcesado(evento.eventId()));
    }

    /** HU-24: los eventos anteriores a esta historia no traen al Subastador; esos cierres no generan abono. */
    private void abonarAlSubastador(SubastaCerrada datos) {
        if (datos.subastadorId() == null) {
            log.info("Subasta {}: el cierre no indica el Subastador, no se abona", datos.subastaId());
            return;
        }
        ledger.abonar(datos.subastadorId(), datos.montoFinal(), datos.subastaId().toString()).ifPresent(saldo -> {
            log.info("Subasta {}: abonados {} Orbes a {}", datos.subastaId(), datos.montoFinal(), datos.subastadorId());
            avisarTrasConfirmar(Eventos.ORBES_ABONADOS, datos.subastaId(),
                    new OrbesAbonados(datos.subastaId(), datos.subastadorId(), datos.montoFinal(), saldo));
        });
    }

    /**
     * Avisa del cobro o del abono para que la pantalla refresque el saldo. Se publica solo si la operación quedó
     * confirmada. Es un aviso, no una garantía: si el broker falla, la pantalla igual consulta el saldo cada pocos segundos.
     */
    private void avisarTrasConfirmar(String tipo, UUID subastaId, Object datos) {
        Runnable aviso = () -> {
            try {
                rabbit.convertAndSend(Eventos.EXCHANGE, tipo, new EventoEnvelope<>(UUID.randomUUID(), tipo, 1,
                        Instant.now(), datos));
            } catch (RuntimeException e) {
                log.warn("No se pudo publicar {} de la subasta {}: {}", tipo, subastaId, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    aviso.run();
                }
            });
        } else {
            aviso.run();
        }
    }
}
