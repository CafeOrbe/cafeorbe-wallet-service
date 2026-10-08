package com.cafeorbe.wallet;

import com.cafeorbe.contracts.EventoEnvelope;
import com.cafeorbe.contracts.Eventos;
import com.cafeorbe.contracts.eventos.SubastaCerrada;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ResolvableType;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato entre servicios: auction publica sus eventos desde la outbox como JSON crudo, sin cabecera de tipo.
 * Wallet debe poder leer ese mensaje tal como llega del broker, con el tipo que declara su consumidor.
 * Las demás pruebas llaman al consumidor directamente y no pasan por la conversión del mensaje.
 */
@SpringBootTest
@ActiveProfiles("test")
class MensajeDeAuctionTest {

    @Autowired MessageConverter conversor;
    @Autowired ObjectMapper json;

    /** Arma el mensaje igual que el OutboxPublisher de auction y lo convierte como lo hace el consumidor. */
    private Object comoLlegaDelBroker(String cuerpo) {
        MessageProperties propiedades = new MessageProperties();
        Message mensaje = MessageBuilder.withBody(cuerpo.getBytes(StandardCharsets.UTF_8))
                .andProperties(propiedades)
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(UUID.randomUUID().toString())
                .build();
        // El contenedor de listeners deduce este tipo del parámetro del método @RabbitListener.
        mensaje.getMessageProperties().setInferredArgumentType(
                ResolvableType.forClassWithGenerics(EventoEnvelope.class, SubastaCerrada.class).getType());
        return conversor.fromMessage(mensaje);
    }

    @Test
    @DisplayName("HU-20 · Wallet lee SubastaCerrada con ganador tal como la publica la outbox de auction")
    void cierreConGanador() throws Exception {
        UUID subasta = UUID.randomUUID();
        UUID ganador = UUID.randomUUID();
        Instant cerradaEn = Instant.parse("2026-10-05T15:10:30Z");
        var evento = new EventoEnvelope<>(UUID.randomUUID(), Eventos.SUBASTA_CERRADA, 1, cerradaEn,
                new SubastaCerrada(subasta, "Lote", "FINALIZADA", ganador, "Ana", 300L, 5, cerradaEn));

        Object recibido = comoLlegaDelBroker(json.writeValueAsString(evento));

        assertThat(recibido).isEqualTo(evento);
        @SuppressWarnings("unchecked")
        var sobre = (EventoEnvelope<SubastaCerrada>) recibido;
        assertThat(sobre.datos().ganadorId()).isEqualTo(ganador);
        assertThat(sobre.datos().montoFinal()).isEqualTo(300L);
        assertThat(sobre.datos().cerradaEn()).isEqualTo(cerradaEn);
    }

    @Test
    @DisplayName("HU-20 · Wallet lee SubastaCerrada de una subasta desierta: ganador y monto nulos")
    void cierreDesierto() {
        String cuerpo = """
                {"eventId":"6b1f0c1e-2a57-4f0a-9d3b-0f6f2f7a9c11","tipo":"subasta.cerrada","version":1,
                 "ocurridoEn":"2026-10-05T15:10:30Z",
                 "datos":{"subastaId":"c2a90b1e-2a57-4f0a-9d3b-0f6f2f7a9c11","nombre":"Lote","estado":"DESIERTA",
                          "ganadorId":null,"ganadorNombre":null,"montoFinal":null,"cantidadPujas":0,
                          "cerradaEn":"2026-10-05T15:10:30Z"}}
                """;

        @SuppressWarnings("unchecked")
        var sobre = (EventoEnvelope<SubastaCerrada>) comoLlegaDelBroker(cuerpo);

        assertThat(sobre.datos().estado()).isEqualTo("DESIERTA");
        assertThat(sobre.datos().ganadorId()).isNull();
        assertThat(sobre.datos().montoFinal()).isNull();
    }

    @Test
    @DisplayName("Un campo nuevo en el evento no rompe a wallet: los cambios compatibles no exigen desplegar a la vez")
    void campoDesconocido() {
        String cuerpo = """
                {"eventId":"6b1f0c1e-2a57-4f0a-9d3b-0f6f2f7a9c11","tipo":"subasta.cerrada","version":1,
                 "ocurridoEn":"2026-10-05T15:10:30Z","campoDelFuturo":"x",
                 "datos":{"subastaId":"c2a90b1e-2a57-4f0a-9d3b-0f6f2f7a9c11","nombre":"Lote","estado":"FINALIZADA",
                          "ganadorId":"0b6f0c1e-2a57-4f0a-9d3b-0f6f2f7a9c11","ganadorNombre":"Ana","montoFinal":120,
                          "cantidadPujas":2,"cerradaEn":"2026-10-05T15:10:30Z","otroCampoNuevo":true}}
                """;

        @SuppressWarnings("unchecked")
        var sobre = (EventoEnvelope<SubastaCerrada>) comoLlegaDelBroker(cuerpo);

        assertThat(sobre.datos().montoFinal()).isEqualTo(120L);
    }
}
