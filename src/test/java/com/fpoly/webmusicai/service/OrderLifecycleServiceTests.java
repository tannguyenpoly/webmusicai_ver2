package com.fpoly.webmusicai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Date;

import org.junit.jupiter.api.Test;

import com.fpoly.webmusicai.entity.Order;
import com.fpoly.webmusicai.repository.OrderRepository;

class OrderLifecycleServiceTests {

    @Test
    void pendingOrderOlderThanFifteenMinutesExpires() {
        OrderRepository orders = mock(OrderRepository.class);
        OrderLifecycleService service = new OrderLifecycleService(orders);
        Order order = pendingOrderMinutesAgo(16);

        assertTrue(service.expireIfNeeded(order));
        assertEquals("EXPIRED", order.getStatus());
        verify(orders).save(order);
    }

    @Test
    void freshPendingOrderRemainsOpen() {
        OrderRepository orders = mock(OrderRepository.class);
        OrderLifecycleService service = new OrderLifecycleService(orders);
        Order order = pendingOrderMinutesAgo(1);

        assertFalse(service.expireIfNeeded(order));
        assertEquals("PENDING", order.getStatus());
        verify(orders, never()).save(order);
    }

    @Test
    void successfulOrderNeverExpires() {
        OrderRepository orders = mock(OrderRepository.class);
        OrderLifecycleService service = new OrderLifecycleService(orders);
        Order order = pendingOrderMinutesAgo(60);
        order.setStatus("SUCCESS");

        assertFalse(service.expireIfNeeded(order));
        assertEquals("SUCCESS", order.getStatus());
        verify(orders, never()).save(order);
    }

    private Order pendingOrderMinutesAgo(int minutes) {
        Order order = new Order();
        order.setStatus("PENDING");
        order.setCreatedAt(new Date(System.currentTimeMillis() - minutes * 60_000L));
        return order;
    }
}
