package com.cafeorbe.wallet.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ModeloTest {

    @Test
    @DisplayName("EventoProcesado recuerda el id del evento que ya se procesó")
    void eventoProcesado() {
        UUID eventId = UUID.randomUUID();

        assertThat(new EventoProcesado(eventId).getEventId()).isEqualTo(eventId);
    }

    @Test
    @DisplayName("Movimiento guarda quién, qué tipo, cuánto y el saldo resultante; y genera su propio id")
    void movimiento() {
        UUID usuario = UUID.randomUUID();

        Movimiento movimiento = new Movimiento(usuario, Movimiento.Tipo.values()[0], 300, 700, "subasta-1");

        assertThat(movimiento.getId()).isNotNull();
        assertThat(movimiento.getUsuarioId()).isEqualTo(usuario);
        assertThat(movimiento.getTipo()).isEqualTo(Movimiento.Tipo.values()[0]);
        assertThat(movimiento.getMonto()).isEqualTo(300);
        assertThat(movimiento.getSaldoResultante()).isEqualTo(700);
        assertThat(movimiento.getReferencia()).isEqualTo("subasta-1");
        assertThat(movimiento.getCreadoEn()).isNotNull();
    }
}
