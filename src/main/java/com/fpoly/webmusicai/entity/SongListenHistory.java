package com.fpoly.webmusicai.entity;

import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Internal event used to calculate music trends; it is not shown as a user-facing library item. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "Song_Listen_History", schema = "dbo")
public class SongListenHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @ManyToOne(optional = true)
    @JoinColumn(name = "username")
    private User user;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "listened_at", nullable = false)
    private Date listenedAt = new Date();
}
