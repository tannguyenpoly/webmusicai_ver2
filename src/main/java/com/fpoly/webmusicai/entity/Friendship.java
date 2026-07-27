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
@Table(name = "Friendships", schema = "dbo")
public class Friendship implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "requester", referencedColumnName = "username")
    private User requester;

    @ManyToOne
    @JoinColumn(name = "addressee", referencedColumnName = "username")
    private User addressee;

    private String status = "PENDING";

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at")
    private Date createdAt = new Date();

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "responded_at")
    private Date respondedAt;
}
