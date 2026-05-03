package com.nvh12.log_processing.infrastructure.service;

import com.nvh12.log_processing.domain.model.ProcessingResult;
import com.nvh12.log_processing.domain.service.EventService;
import com.nvh12.log_processing.infrastructure.config.RabbitMqConfig;
import lombok.AllArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class EventServiceImpl implements EventService {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publish(ProcessingResult result) {
        switch (result) {
            case ProcessingResult.Http http ->
                    rabbitTemplate.convertAndSend(RabbitMqConfig.QUEUE_NORMALIZED_HTTP, http.log());
            case ProcessingResult.Flow flow ->
                    rabbitTemplate.convertAndSend(RabbitMqConfig.QUEUE_NORMALIZED_FLOW, flow.record());
        }
    }
}
