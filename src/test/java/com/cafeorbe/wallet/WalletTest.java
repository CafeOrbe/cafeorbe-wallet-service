package com.cafeorbe.wallet;

import com.cafeorbe.contracts.Cabeceras;
import com.cafeorbe.contracts.EventoEnvelope;
import com.cafeorbe.contracts.Eventos;
import com.cafeorbe.contracts.eventos.UsuarioRegistrado;
import com.cafeorbe.wallet.consumer.UsuarioRegistradoConsumer;
import com.cafeorbe.wallet.repository.CuentaRepository;
import com.cafeorbe.wallet.repository.EventoProcesadoRepository;
import com.cafeorbe.wallet.repository.MovimientoRepository;
import com.cafeorbe.wallet.service.Ledger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WalletTest {

    @Autowired UsuarioRegistradoConsumer consumidor;
    @Autowired Ledger ledger;
    @Autowired CuentaRepository cuentas;
    @Autowired MovimientoRepository movimientos;
    @Autowired EventoProcesadoRepository procesados;
    @Autowired MockMvc mvc;

    @BeforeEach
    void limpiar() {
        movimientos.deleteAll();
        cuentas.deleteAll();
        procesados.deleteAll();
    }

    private EventoEnvelope<UsuarioRegistrado> evento(UUID eventId, UUID usuarioId, String rol) {
        return new EventoEnvelope<>(eventId, Eventos.USUARIO_REGISTRADO, 1, Instant.now(),
                new UsuarioRegistrado(usuarioId, "Ana", rol));
    }

    @Test
    @DisplayName("HU-07 · Carga automática en el primer ingreso: saldo inicial y movimiento Carga automática")
    void cargaAutomatica() {
        UUID ana = UUID.randomUUID();
        consumidor.alRecibir(evento(UUID.randomUUID(), ana, "COMPRADOR"));

        assertThat(ledger.saldoDe(ana)).isEqualTo(1000);
        var historial = ledger.ultimosMovimientos(ana, 10);
        assertThat(historial).hasSize(1);
        assertThat(historial.get(0).getTipo().name()).isEqualTo("CARGA_AUTOMATICA");
        assertThat(historial.get(0).getSaldoResultante()).isEqualTo(1000);
    }

    @Test
    @DisplayName("HU-07 · Sin doble carga: el mismo evento repetido no vuelve a cargar")
    void eventoRepetido() {
        UUID ana = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        consumidor.alRecibir(evento(eventId, ana, "COMPRADOR"));
        consumidor.alRecibir(evento(eventId, ana, "COMPRADOR"));

        assertThat(ledger.saldoDe(ana)).isEqualTo(1000);
        assertThat(ledger.ultimosMovimientos(ana, 10)).hasSize(1);
    }

    @Test
    @DisplayName("HU-07 · Sin doble carga: un evento nuevo del mismo usuario (reintento de identity) conserva el saldo")
    void reintentoDeIdentity() {
        UUID ana = UUID.randomUUID();
        consumidor.alRecibir(evento(UUID.randomUUID(), ana, "COMPRADOR"));
        consumidor.alRecibir(evento(UUID.randomUUID(), ana, "COMPRADOR"));

        assertThat(ledger.saldoDe(ana)).isEqualTo(1000);
        assertThat(ledger.ultimosMovimientos(ana, 10)).hasSize(1);
    }

    @Test
    @DisplayName("Un Subastador no recibe Orbes")
    void subastadorNoRecibe() {
        UUID luis = UUID.randomUUID();
        consumidor.alRecibir(evento(UUID.randomUUID(), luis, "SUBASTADOR"));

        assertThat(cuentas.findById(luis)).isEmpty();
        assertThat(ledger.saldoDe(luis)).isZero();
    }

    @Test
    @DisplayName("HU-06 · GET /api/orbes/saldo devuelve el saldo del usuario autenticado")
    void consultaDeSaldo() throws Exception {
        UUID ana = UUID.randomUUID();
        consumidor.alRecibir(evento(UUID.randomUUID(), ana, "COMPRADOR"));

        mvc.perform(get("/api/orbes/saldo").header(Cabeceras.USUARIO_ID, ana.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(1000));
        mvc.perform(get("/internal/orbes/" + ana + "/saldo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(1000));
    }

    @Test
    @DisplayName("HU-06 · Un usuario sin cuenta ve 0 Orbes, nunca un valor negativo; sin sesión responde 401")
    void sinCuentaYSinSesion() throws Exception {
        mvc.perform(get("/api/orbes/saldo").header(Cabeceras.USUARIO_ID, UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(0));
        mvc.perform(get("/api/orbes/saldo")).andExpect(status().isUnauthorized());
    }
}
