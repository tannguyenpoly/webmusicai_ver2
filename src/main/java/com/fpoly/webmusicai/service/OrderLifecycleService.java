package com.fpoly.webmusicai.service;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fpoly.webmusicai.entity.Order;
import com.fpoly.webmusicai.repository.OrderRepository;

@Service
public class OrderLifecycleService {

    public static final int PAYMENT_TIMEOUT_MINUTES = 15;

    private final OrderRepository orderRepository;

    public OrderLifecycleService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public boolean expireIfNeeded(Order order) {
        if (order == null || !"PENDING".equals(order.getStatus()) || !isPastDeadline(order)) {
            return false;
        }
        order.setStatus("EXPIRED");
        orderRepository.save(order);
        return true;
    }

    @Transactional
    public int expirePendingOrders() {
        Calendar cutoff = Calendar.getInstance();
        cutoff.add(Calendar.MINUTE, -PAYMENT_TIMEOUT_MINUTES);
        List<Order> orders = orderRepository.findByStatusAndCreatedAtBefore("PENDING", cutoff.getTime());
        for (Order order : orders) {
            order.setStatus("EXPIRED");
        }
        orderRepository.saveAll(orders);
        return orders.size();
    }

    public long remainingSeconds(Order order) {
        if (order == null || order.getCreatedAt() == null) {
            return 0;
        }
        long deadline = order.getCreatedAt().getTime() + PAYMENT_TIMEOUT_MINUTES * 60_000L;
        return Math.max(0L, (deadline - new Date().getTime()) / 1000L);
    }

    private boolean isPastDeadline(Order order) {
        return remainingSeconds(order) <= 0;
    }
}
