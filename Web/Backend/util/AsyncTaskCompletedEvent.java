package com.example.capstone.util;

import com.example.capstone.dto.NotificationDto;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AsyncTaskCompletedEvent extends ApplicationEvent {
    private final NotificationDto result;

    public AsyncTaskCompletedEvent(Object source, NotificationDto result) {
        super(source);
        this.result = result;
    }

}
