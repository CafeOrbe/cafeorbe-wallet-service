package com.cafeorbe.wallet;

import com.cafeorbe.contracts.Cabeceras;
import com.cafeorbe.contracts.EventoEnvelope;
import com.cafeorbe.contracts.Eventos;
import com.cafeorbe.contracts.eventos.OrbesAbonados;
import com.cafeorbe.contracts.eventos.OrbesCobrados;
import com.cafeorbe.contracts.eventos.SubastaCerrada;
import com.cafeorbe.contracts.eventos.UsuarioRegistrado;
import com.cafeorbe.wallet.consumer.SubastaCerradaConsumer;
import com.cafeorbe.wallet.consumer.UsuarioRegistradoConsumer;
import com.cafeorbe.wallet.repository.CuentaRepository;
import com.cafeorbe.wallet.repository.EventoProcesadoRepository;
import com.cafeorbe.wallet.repository.MovimientoRepository;
import com.cafeorbe.wallet.service.Ledger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** HU-20: cobro de Orbes al comprador ganador cuando cierra la subasta. El saldo inicial de prueba es 1000. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CobroTest {

    @Autowired UsuarioRegistradoConsumer registro;
    @Autowired SubastaCerradaConsumer cierre;
    @Autowired Ledger ledger;
    @Autowired CuentaRepository cuentas;
    @Autowired MovimientoRepository movimientos;
    @Autowired EventoProcesadoRepository procesados;
    @Autowired MockMvc mvc;
    @MockitoBean RabbitTemplate rabbit;

    UUID ana;
    UUID bruno;

    @BeforeEach
    void compradoresConSaldo() {
        movimientos.deleteAll();
        cuentas.deleteAll();
        procesados.deleteAll();
        ana = comprador("Ana");
        bruno = comprador("Bruno");
    }

    private UUID comprador(String nombre) {
        UUID id = UUID.randomUUID();
        registro.alRecibir(new EventoEnvelope<>(UUID.randomUUID(), Eventos.USUARIO_REGISTRADO, 1, Instant.now(),
                new UsuarioRegistrado(id, nombre, "COMPRADOR")));
        return id;
    }

    private EventoEnvelope<SubastaCerrada> cerrada(UUID eventId, UUID subasta, UUID ganador, Long monto) {
        String estado = ganador == null ? "DESIERTA" : "FINALIZADA";
        return new EventoEnvelope<>(eventId, Eventos.SUBASTA_CERRADA, 1, Instant.now(),
                new SubastaCerrada(subasta, "Lote", estado, ganador, ganador == null ? null : "Ana", monto,
                        ganador == null ? 0 : 3, Instant.now()));
    }

    @Test
    @DisplayName("HU-20 · Cobro al ganador: puja de 300 con 1000 Orbes → queda con 700 y el movimiento en su historial")
    void cobroAlGanador() throws Exception {
        UUID subasta = UUID.randomUUID();

        cierre.alRecibir(cerrada(UUID.randomUUID(), subasta, ana, 300L));

        assertThat(ledger.saldoDe(ana)).isEqualTo(700);
        var historial = ledger.ultimosMovimientos(ana, 10);
        assertThat(historial).hasSize(2);
        var cobro = historial.stream().filter(m -> m.getTipo().name().equals("COBRO")).findFirst().orElseThrow();
        assertThat(cobro.getMonto()).isEqualTo(-300);
        assertThat(cobro.getSaldoResultante()).isEqualTo(700);
        assertThat(cobro.getReferencia()).isEqualTo(subasta.toString());
        mvc.perform(get("/api/orbes/saldo").header(Cabeceras.USUARIO_ID, ana.toString()))
                .andExpect(jsonPath("$.saldo").value(700));
    }

    @Test
    @DisplayName("HU-20 · Compradores no ganadores: su saldo permanece igual que antes de la subasta")
    void noGanadores() {
        cierre.alRecibir(cerrada(UUID.randomUUID(), UUID.randomUUID(), ana, 300L));

        assertThat(ledger.saldoDe(bruno)).isEqualTo(1000);
        assertThat(ledger.ultimosMovimientos(bruno, 10)).hasSize(1);
    }

    @Test
    @DisplayName("HU-20 · Sin doble cobro: ni el mismo evento repetido ni un evento nuevo de la misma subasta vuelven a cobrar")
    void sinDobleCobro() {
        UUID subasta = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        cierre.alRecibir(cerrada(eventId, subasta, ana, 300L));
        cierre.alRecibir(cerrada(eventId, subasta, ana, 300L));
        cierre.alRecibir(cerrada(UUID.randomUUID(), subasta, ana, 300L));

        assertThat(ledger.saldoDe(ana)).isEqualTo(700);
        assertThat(ledger.ultimosMovimientos(ana, 10)).hasSize(2);
        verify(rabbit, times(1)).convertAndSend(eq(Eventos.EXCHANGE), eq(Eventos.ORBES_COBRADOS), any(Object.class));
    }

    @Test
    @DisplayName("HU-20 · Dos subastas ganadas por la misma persona se cobran las dos, una vez cada una")
    void dosSubastas() {
        cierre.alRecibir(cerrada(UUID.randomUUID(), UUID.randomUUID(), ana, 300L));
        cierre.alRecibir(cerrada(UUID.randomUUID(), UUID.randomUUID(), ana, 200L));

        assertThat(ledger.saldoDe(ana)).isEqualTo(500);
    }

    @Test
    @DisplayName("HU-20 · Una subasta desierta no cobra a nadie")
    void desierta() {
        cierre.alRecibir(cerrada(UUID.randomUUID(), UUID.randomUUID(), null, null));

        assertThat(ledger.saldoDe(ana)).isEqualTo(1000);
        assertThat(ledger.saldoDe(bruno)).isEqualTo(1000);
        verify(rabbit, never()).convertAndSend(anyString(), eq(Eventos.ORBES_COBRADOS), any(Object.class));
    }

    @Test
    @DisplayName("HU-20 · Saldo insuficiente al cierre: no se cobra nada y el saldo nunca queda negativo")
    void saldoInsuficiente() {
        cierre.alRecibir(cerrada(UUID.randomUUID(), UUID.randomUUID(), ana, 1500L));

        assertThat(ledger.saldoDe(ana)).isEqualTo(1000);
        assertThat(ledger.ultimosMovimientos(ana, 10)).hasSize(1);
        verify(rabbit, never()).convertAndSend(anyString(), eq(Eventos.ORBES_COBRADOS), any(Object.class));
    }

    // ── HU-24: lo cobrado al ganador se abona al Subastador ───────────────

    private EventoEnvelope<SubastaCerrada> vendida(UUID eventId, UUID subasta, UUID ganador, long monto, UUID subastador) {
        return new EventoEnvelope<>(eventId, Eventos.SUBASTA_CERRADA, 1, Instant.now(),
                new SubastaCerrada(subasta, "Lote", "FINALIZADA", ganador, "Ana", monto, 3, Instant.now(), subastador));
    }

    @Test
    @DisplayName("HU-24 · Abono al Subastador: lo que se descuenta al ganador le llega completo, con su movimiento y su aviso")
    void abonoAlSubastador() throws Exception {
        UUID luis = UUID.randomUUID();
        UUID subasta = UUID.randomUUID();

        cierre.alRecibir(vendida(UUID.randomUUID(), subasta, ana, 300L, luis));

        assertThat(ledger.saldoDe(ana)).isEqualTo(700);
        assertThat(ledger.saldoDe(luis)).isEqualTo(300);
        var venta = ledger.ultimosMovimientos(luis, 10).getFirst();
        assertThat(venta.getTipo().name()).isEqualTo("ABONO_VENTA");
        assertThat(venta.getMonto()).isEqualTo(300);
        assertThat(venta.getSaldoResultante()).isEqualTo(300);
        assertThat(venta.getReferencia()).isEqualTo(subasta.toString());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventoEnvelope<OrbesAbonados>> aviso = ArgumentCaptor.forClass(EventoEnvelope.class);
        verify(rabbit).convertAndSend(eq(Eventos.EXCHANGE), eq(Eventos.ORBES_ABONADOS), aviso.capture());
        assertThat(aviso.getValue().datos()).isEqualTo(new OrbesAbonados(subasta, luis, 300, 300));

        mvc.perform(get("/api/orbes/ganancias").header(Cabeceras.USUARIO_ID, luis.toString()))
                .andExpect(jsonPath("$.total").value(300))
                .andExpect(jsonPath("$.ventas.length()").value(1))
                .andExpect(jsonPath("$.ventas[0].monto").value(300))
                .andExpect(jsonPath("$.ventas[0].referencia").value(subasta.toString()));
    }

    @Test
    @DisplayName("HU-24 · Sin doble abono: repetir el cierre de una subasta no vuelve a abonar, y varias ventas se suman")
    void sinDobleAbono() throws Exception {
        UUID luis = UUID.randomUUID();
        UUID subasta = UUID.randomUUID();
        UUID mismoEvento = UUID.randomUUID();

        cierre.alRecibir(vendida(mismoEvento, subasta, ana, 300L, luis));
        cierre.alRecibir(vendida(mismoEvento, subasta, ana, 300L, luis));
        cierre.alRecibir(vendida(UUID.randomUUID(), subasta, ana, 300L, luis));
        assertThat(ledger.saldoDe(luis)).isEqualTo(300);
        assertThat(ledger.abonar(luis, 300, subasta.toString())).isEmpty();

        cierre.alRecibir(vendida(UUID.randomUUID(), UUID.randomUUID(), bruno, 200L, luis));

        assertThat(ledger.saldoDe(luis)).isEqualTo(500);
        assertThat(ledger.totalGanado(luis)).isEqualTo(500);
        mvc.perform(get("/api/orbes/ganancias").header(Cabeceras.USUARIO_ID, luis.toString()))
                .andExpect(jsonPath("$.total").value(500))
                .andExpect(jsonPath("$.ventas.length()").value(2))
                .andExpect(jsonPath("$.ventas[0].monto").value(200));
    }

    @Test
    @DisplayName("HU-24 · Si no se pudo cobrar al ganador, el Subastador no recibe nada")
    void sinCobroNoHayAbono() {
        UUID luis = UUID.randomUUID();

        cierre.alRecibir(vendida(UUID.randomUUID(), UUID.randomUUID(), ana, 1500L, luis));

        assertThat(ledger.saldoDe(ana)).isEqualTo(1000);
        assertThat(ledger.saldoDe(luis)).isZero();
        assertThat(ledger.ultimosMovimientos(luis, 10)).isEmpty();
        verify(rabbit, never()).convertAndSend(anyString(), eq(Eventos.ORBES_ABONADOS), any(Object.class));
    }

    @Test
    @DisplayName("HU-24 · Un cierre publicado antes de esta historia (sin Subastador) cobra pero no abona a nadie")
    void cierreSinSubastador() throws Exception {
        cierre.alRecibir(cerrada(UUID.randomUUID(), UUID.randomUUID(), ana, 300L));

        assertThat(ledger.saldoDe(ana)).isEqualTo(700);
        verify(rabbit, never()).convertAndSend(anyString(), eq(Eventos.ORBES_ABONADOS), any(Object.class));
        // Un Comprador no tiene ventas: sus ganancias son 0.
        mvc.perform(get("/api/orbes/ganancias").header(Cabeceras.USUARIO_ID, ana.toString()))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.ventas.length()").value(0));
    }

    @Test
    @DisplayName("HU-20 · Un ganador sin cuenta no se cobra ni rompe el cierre, aunque el monto sea 0")
    void ganadorSinCuenta() {
        UUID sinCuenta = UUID.randomUUID();

        assertThat(ledger.cobrar(sinCuenta, 300, "subasta-1")).isEqualTo(new Ledger.Cobro(Ledger.Cobro.Estado.SALDO_INSUFICIENTE, 0));
        assertThat(ledger.cobrar(sinCuenta, 0, "subasta-2")).isEqualTo(new Ledger.Cobro(Ledger.Cobro.Estado.SALDO_INSUFICIENTE, 0));

        assertThat(ledger.ultimosMovimientos(sinCuenta, 10)).isEmpty();
        assertThat(cuentas.findById(sinCuenta)).isEmpty();
    }

    @Test
    @DisplayName("HU-20 · Tras el cobro se publica OrbesCobrados con el saldo nuevo; si el broker falla, el cobro se conserva")
    void avisoDelCobro() {
        UUID subasta = UUID.randomUUID();
        cierre.alRecibir(cerrada(UUID.randomUUID(), subasta, ana, 300L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventoEnvelope<OrbesCobrados>> aviso = ArgumentCaptor.forClass(EventoEnvelope.class);
        verify(rabbit).convertAndSend(eq(Eventos.EXCHANGE), eq(Eventos.ORBES_COBRADOS), aviso.capture());
        assertThat(aviso.getValue().datos()).isEqualTo(new OrbesCobrados(subasta, ana, 300, 700));

        doThrow(new IllegalStateException("broker caído")).when(rabbit)
                .convertAndSend(anyString(), anyString(), any(Object.class));
        cierre.alRecibir(cerrada(UUID.randomUUID(), UUID.randomUUID(), bruno, 100L));
        assertThat(ledger.saldoDe(bruno)).isEqualTo(900);
    }
}
