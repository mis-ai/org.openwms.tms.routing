/*
 * Copyright 2018 Heiko Scherrer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openwms.tms.routing.app;

import org.openwms.core.SpringProfiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SerializerMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.support.RetryTemplate;

import static org.ameba.LoggingCategories.BOOT;

/**
 * A RoutingAsyncConfiguration is activated when the service uses asynchronous
 * communication to access other services.
 *
 * @author Heiko Scherrer
 */
@Profile(SpringProfiles.ASYNCHRONOUS_PROFILE)
@EnableRabbit
@Configuration
public class RoutingAsyncConfiguration {

    private static final Logger BOOT_LOGGER = LoggerFactory.getLogger(BOOT);

    @ConditionalOnExpression("'${owms.routing.serialization}'=='json'")
    @Bean
    MessageConverter messageConverter() {
        Jackson2JsonMessageConverter messageConverter = new Jackson2JsonMessageConverter();
        BOOT_LOGGER.info("Using JSON serialization over AMQP");
        return messageConverter;
    }

    @ConditionalOnExpression("'${owms.routing.serialization}'=='barray'")
    @Bean
    MessageConverter serializerMessageConverter() {
        SerializerMessageConverter messageConverter = new SerializerMessageConverter();
        BOOT_LOGGER.info("Using byte array serialization over AMQP");
        return messageConverter;
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setMultiplier(2);
        backOffPolicy.setMaxInterval(15000);
        backOffPolicy.setInitialInterval(500);
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setBackOffPolicy(backOffPolicy);
        rabbitTemplate.setRetryTemplate(retryTemplate);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

    @Bean
    TopicExchange tmsRequestsExchange(@Value("${owms.requests.routing.to.exchange-name}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    Queue tmsRequestsQueue(@Value("${owms.requests.routing.to.queue-name}") String queueName,
            @Value("${owms.routing.dead-letter.exchange-name}") String exchangeName) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", exchangeName)
                .withArgument("x-dead-letter-routing-key", "poison-message")
                .build();
    }

    @Bean
    Binding tmsRequestsBinding(TopicExchange tmsRequestsExchange, Queue tmsRequestsQueue, @Value("${owms.requests.routing.to.routing-key}") String routingKey) {
        return BindingBuilder
                .bind(tmsRequestsQueue)
                .to(tmsRequestsExchange)
                .with(routingKey);
    }

    /*~ -------------- Location Updates --------------- */
    @Bean
    TopicExchange locExchange(@Value("${owms.events.common.loc.exchange-name}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    Queue locQueue(@Value("${owms.events.common.loc.queue-name}") String queueName,
            @Value("${owms.routing.dead-letter.exchange-name}") String exchangeName) {
        return QueueBuilder.nonDurable(queueName)
                .withArgument("x-dead-letter-exchange", exchangeName)
                .withArgument("x-dead-letter-routing-key", "poison-message")
                .build();
    }

    @Bean
    Binding locBinding(TopicExchange locExchange, Queue locQueue, @Value("${owms.events.common.loc.routing-key}") String routingKey) {
        return BindingBuilder.bind(locQueue)
                .to(locExchange)
                .with(routingKey);
    }

    /*~ -------------- LocationGroup Updates --------------- */
    @Bean
    TopicExchange lgExchange(@Value("${owms.events.common.lg.exchange-name}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    Queue lgQueue(@Value("${owms.events.common.lg.queue-name}") String queueName,
            @Value("${owms.routing.dead-letter.exchange-name}") String exchangeName) {
        return QueueBuilder.nonDurable(queueName)
                .withArgument("x-dead-letter-exchange", exchangeName)
                .withArgument("x-dead-letter-routing-key", "poison-message")
                .build();
    }

    @Bean
    Binding lgBinding(TopicExchange lgExchange, Queue lgQueue, @Value("${owms.events.common.lg.routing-key}") String routingKey) {
        return BindingBuilder.bind(lgQueue)
                .to(lgExchange)
                .with(routingKey);
    }

    @Bean
    DirectExchange dlExchange(@Value("${owms.routing.dead-letter.exchange-name}") String exchangeName) {
        return new DirectExchange(exchangeName);
    }

    @Bean
    Queue dlq(@Value("${owms.routing.dead-letter.queue-name}") String queueName) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    Binding dlBinding(@Value("${owms.routing.dead-letter.queue-name}") String queueName,
            @Value("${owms.routing.dead-letter.exchange-name}") String exchangeName) {
        return BindingBuilder.bind(dlq(queueName)).to(dlExchange(exchangeName)).with("poison-message");
    }
}
