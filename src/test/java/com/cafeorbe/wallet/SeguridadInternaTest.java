package com.cafeorbe.wallet;

import com.cafeorbe.contracts.Cabeceras;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SEG-02: los endpoints /internal de wallet y su API solo atienden a quien presenta la credencial interna. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeguridadInternaTest {

    @Autowired MockMvc mvc;
    @Autowired MeterRegistry metricas;
    @MockitoBean RabbitTemplate rabbit;

    private double rechazos() {
        return metricas.find("cafeorbe.seguridad.eventos").tag("evento", "acceso_interno_rechazado").counters()
                .stream().mapToDouble(c -> c.count()).sum();
    }

    @Test
    @DisplayName("SEG-02 · /internal sin credencial, con una inventada o vacía: 403 y queda registrado")
    void internalSinCredencial() throws Exception {
        String ruta = "/internal/orbes/" + UUID.randomUUID() + "/saldo";
        double antes = rechazos();
        mvc.perform(get(ruta)).andExpect(status().isForbidden());
        mvc.perform(get(ruta).header("X-Internal-Token", "adivinada")).andExpect(status().isForbidden());
        mvc.perform(get(ruta).header("X-Internal-Token", "")).andExpect(status().isForbidden());
        // Un Comprador que llama directo con su identidad fabricada tampoco entra.
        mvc.perform(get("/api/orbes/saldo").header(Cabeceras.USUARIO_ID, UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
        assertThat(rechazos() - antes).isEqualTo(4);

        mvc.perform(get(ruta).header("X-Internal-Token", PeticionesInternas.CREDENCIAL)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("SEG-02 · El tiempo de la verificación queda medido, con un bucket en 5 ms para comparar con la meta")
    void verificacionMedida() throws Exception {
        String ruta = "/internal/orbes/" + UUID.randomUUID() + "/saldo";
        mvc.perform(get(ruta)).andExpect(status().isForbidden());
        mvc.perform(get(ruta).header("X-Internal-Token", PeticionesInternas.CREDENCIAL)).andExpect(status().isOk());

        for (String resultado : new String[] { "permitida", "rechazada" }) {
            var timer = metricas.find("cafeorbe.seguridad.verificacion").tag("etapa", "credencial_interna")
                    .tag("resultado", resultado).timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isPositive();
            assertThat(java.util.Arrays.stream(timer.takeSnapshot().histogramCounts())
                    .map(b -> b.bucket(java.util.concurrent.TimeUnit.MILLISECONDS))).contains(5.0);
            // La comparación en tiempo constante es de microsegundos: muy por debajo de la meta.
            assertThat(timer.max(java.util.concurrent.TimeUnit.MILLISECONDS)).isLessThan(5.0);
        }
    }

    @Test
    @DisplayName("OBS-01 · Devuelve el correlationId recibido y la salud no exige credencial")
    void correlacionYSalud() throws Exception {
        mvc.perform(get("/actuator/health/liveness").header("X-Correlation-Id", "corr-wallet-01"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "corr-wallet-01"));
    }
}
