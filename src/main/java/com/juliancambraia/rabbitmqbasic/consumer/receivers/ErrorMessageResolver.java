package com.juliancambraia.rabbitmqbasic.consumer.receivers;

import com.juliancambraia.rabbitmqbasic.config.AppProperties;
import com.juliancambraia.rabbitmqbasic.exceptions.FailedProcessException;
import com.juliancambraia.rabbitmqbasic.models.FailedMessage;
import com.juliancambraia.rabbitmqbasic.models.MyMessage;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

public class ErrorMessageResolver implements MessageRecoverer {
  private final RabbitTemplate template;
  private final AppProperties properties;
  private final Jackson2JsonMessageConverter converter;

  public ErrorMessageResolver(RabbitTemplate template, AppProperties properties, Jackson2JsonMessageConverter converter) {
    this.template = template;
    this.properties = properties;
    this.converter = converter;
  }

  @Override
  public void recover(Message message, Throwable cause) {
    if (cause instanceof ListenerExecutionFailedException && cause.getCause() instanceof FailedProcessException) {
      try {
        // recupera a original queue
        message.getMessageProperties().setInferredArgumentType(MyMessage.class);
        MyMessage originalRequest = (MyMessage) converter.fromMessage(message, MyMessage.class);

        FailedMessage failedMessage = new FailedMessage(
            originalRequest.getMessageId(),
            originalRequest.getMessage(),
            cause.getCause().getMessage()
        );

        // envia a mensagem para a fila de response
        this.template.convertAndSend(properties.getDirectExchange(), properties.getResponseQueue(), failedMessage);
      } catch (Exception ex) {
        // envia a mensagem para a dead letter queue
        throw new AmqpRejectAndDontRequeueException("Não foi possível recuperar a mensagem - enviando para a fila de DLQ", ex);
      }
    } else {
      throw new AmqpRejectAndDontRequeueException("Não foi possível recuperar a mensagem");
    }
  }
}
