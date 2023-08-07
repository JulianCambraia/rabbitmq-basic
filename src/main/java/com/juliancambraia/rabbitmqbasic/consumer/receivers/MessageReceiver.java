package com.juliancambraia.rabbitmqbasic.consumer.receivers;

import com.juliancambraia.rabbitmqbasic.exceptions.FailedProcessException;
import com.juliancambraia.rabbitmqbasic.models.MyMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
public class MessageReceiver {

  //----------------------------------------------------------
  // Rabbit Listener para Acknowleggement mode igual a auto or none.
  //-
  @RabbitListener(queues = "#{requestQueue.name}", containerFactory = "rabbitListenerContainerFactory")
  public void receiveMessage(MyMessage message) throws Exception {
    log.info("---------------[{}] Received: {}", Instant.now(), message.toString());
    Thread.sleep(5000);

    if (message.getMessageId() > 5) {
      throw new Exception("Erro desconhecido!!!");
    } else if (message.getMessageId() == 5) {
      throw new FailedProcessException("Teste error");
    } else if (message.getMessageId() == 3) {
      log.info("Aguarde por mais 60 segundos...");
      Thread.sleep(60000);
      log.info("Listener método executado com sucesso após 65 segundos...");
    } else {
      log.info("Listener método executado com sucesso");
    }
  }
}
