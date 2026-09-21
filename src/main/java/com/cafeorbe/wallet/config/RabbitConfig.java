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

    @Bean
    MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
