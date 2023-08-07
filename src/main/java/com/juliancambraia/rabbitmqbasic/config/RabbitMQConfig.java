package com.juliancambraia.rabbitmqbasic.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliancambraia.rabbitmqbasic.consumer.receivers.ErrorMessageResolver;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

@Configuration
public class RabbitMQConfig {
  private final AppProperties properties;

  public RabbitMQConfig(AppProperties properties) {
    this.properties = properties;
  }

  @Bean
  public Exchange directExchange() {
    return new DirectExchange(properties.getDirectExchange(), true, false);
  }

  @Bean
  public Queue requestQueue() {
    return QueueBuilder
        .durable(properties.getRequestQueue())
        .withArgument("x-dead-letter-exchange", properties.getDirectExchange())
        .withArgument("x-dead-letter-routing-key", properties.getRequestDeadLetterQueue())
        .build();
  }

  @Bean
  public Binding bindRequestQueue() {
    return BindingBuilder
        .bind(requestQueue())
        .to(directExchange())
        .with(properties.getRequestQueue())
        .noargs();
  }

  @Bean
  public Queue responseQueue() {
    return QueueBuilder
        .durable(properties.getResponseQueue())
        .build();
  }

  @Bean
  public Binding bindResponseQueue() {
    return BindingBuilder
        .bind(responseQueue())
        .to(directExchange())
        .with(properties.getResponseQueue())
        .noargs();
  }
  @Bean
  public Queue requestDLQueue() {
    return QueueBuilder
        .durable(properties.getRequestDeadLetterQueue())
        .build();
  }
  @Bean
  public Binding bindRequestDLQueue() {
    return BindingBuilder
        .bind(requestDLQueue())
        .to(directExchange())
        .with(properties.getRequestDeadLetterQueue())
        .noargs();
  }

  @Bean
  Jackson2JsonMessageConverter messageConverter(ObjectMapper mapper) {
    var converter = new Jackson2JsonMessageConverter(mapper);
    converter.setCreateMessageIds(true); // cria id de mensagem unica para qualquer mensagem
    return converter;
  }

  @Bean
  public RabbitTemplate rabbitTemplate(ConnectionFactory factory, ObjectMapper objectMapper) {
    RabbitTemplate template = new RabbitTemplate();
    template.setConnectionFactory(factory);
    template.setMessageConverter(messageConverter(objectMapper));
    return template;
  }

  @Bean
  public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
      ConnectionFactory connectionFactory,
      RetryOperationsInterceptor retryInterceptor,
      ObjectMapper objectMapper) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(messageConverter(objectMapper));
    factory.setPrefetchCount(0);
    factory.setConcurrentConsumers(properties.getConcurrentConsumers());
    factory.setMaxConcurrentConsumers(properties.getMaxConcurrentConsumers());
    factory.setAdviceChain(retryInterceptor);
    factory.setAcknowledgeMode(AcknowledgeMode.AUTO); // por padrão o spring seta como AUTO

    return factory;
  }

  @Bean
  public RetryOperationsInterceptor messageRetryInterceptor(MessageRecoverer messageRecoverer) {
    return RetryInterceptorBuilder.StatelessRetryInterceptorBuilder
        .stateless()
        .maxAttempts(properties.getRetryAttempts())
        .backOffOptions(
            properties.getBackoffInterval(),
            properties.getBackoffMultiplier(),
            properties.getBackoffMaxInterval()
        )
        .recoverer(messageRecoverer)
        .build();
  }

  @Bean
  public MessageRecoverer messageRecoverer(RabbitTemplate template, AppProperties properties, ObjectMapper objectMapper) {
    return new ErrorMessageResolver(
        template,
        properties,
        messageConverter(objectMapper)
    );
  }
}
