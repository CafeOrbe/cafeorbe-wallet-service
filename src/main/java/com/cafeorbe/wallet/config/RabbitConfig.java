package com.cafeorbe.wallet.config;

import com.cafeorbe.contracts.Eventos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String COLA_USUARIO_REGISTRADO = "wallet.usuario-registrado";
    public static final String COLA_SUBASTA_CERRADA = "wallet.subasta-cerrada";

    @Bean
    TopicExchange eventosExchange() {
        return new TopicExchange(Eventos.EXCHANGE, true, false);
    }

    @Bean
    Queue colaUsuarioRegistrado() {
        return QueueBuilder.durable(COLA_USUARIO_REGISTRADO).build();
    }

    @Bean
    Binding bindingUsuarioRegistrado(Queue colaUsuarioRegistrado, TopicExchange eventosExchange) {
        return BindingBuilder.bind(colaUsuarioRegistrado).to(eventosExchange).with(Eventos.USUARIO_REGISTRADO);
    }

    /** HU-20: cola propia de wallet para el cierre de subastas; realtime recibe el mismo evento por la suya. */
    @Bean
    Queue colaSubastaCerrada() {
        return QueueBuilder.durable(COLA_SUBASTA_CERRADA).build();
    }

    @Bean
    Binding bindingSubastaCerrada(Queue colaSubastaCerrada, TopicExchange eventosExchange) {
        return BindingBuilder.bind(colaSubastaCerrada).to(eventosExchange).with(Eventos.SUBASTA_CERRADA);
    }

    @Bean
    MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
