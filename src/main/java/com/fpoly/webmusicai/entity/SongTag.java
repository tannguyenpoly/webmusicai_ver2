package com.fpoly.webmusicai.entity;

import java.io.Serializable;
import jakarta.persistence.*;
import lombok.*;

@SuppressWarnings("serial")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "Song_Tags", schema = "dbo")
public class SongTag implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "song_id")
    private Integer songId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tag_id")
    private Tag tag;
}
