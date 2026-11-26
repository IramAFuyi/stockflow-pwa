package com.stockflow.event;

import com.stockflow.service.WebPushService;

import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Component
public class LowStockListener {

    private final WebPushService pushService;

    public LowStockListener(WebPushService pushService) {
        this.pushService = pushService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLowStock(LowStockEvent event) {

        String message = "Un producto alcanzo su stock minimo. " +
                "Consulta StockFlow para revisar las existencias.";

        pushService.sendToAll(message);
    }
}