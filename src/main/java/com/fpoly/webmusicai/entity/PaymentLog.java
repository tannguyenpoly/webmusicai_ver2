package com.fpoly.webmusicai.entity;

import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.*;
import lombok.*;

@SuppressWarnings("serial")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "Payment_Logs", schema = "dbo")
public class PaymentLog implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "order_code")
    private String orderCode;

    @Column(name = "gateway_name")
    private String gatewayName;

    @Column(name = "transaction_id", unique = true)
    private String transactionId;

    private Integer amount;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String content;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at")
    private Date createdAt = new Date();
}
