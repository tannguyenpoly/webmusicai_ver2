package com.fpoly.webmusicai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "SongGenres")
@Data
@NoArgsConstructor
public class SongGenre {

    @EmbeddedId
    private SongGenreId id;

    @ManyToOne
    @MapsId("songId") // This maps the songId from the EmbeddedId to the Song entity's ID
    @JoinColumn(name = "song_id")
    private Song song;

    @ManyToOne
    @MapsId("genreId") // This maps the genreId from the EmbeddedId to the Genre entity's ID
    @JoinColumn(name = "genre_id")
    private Genre genre;

    public SongGenre(Song song, Genre genre) {
        this.song = song;
        this.genre = genre;
        this.id = new SongGenreId(song.getId(), genre.getId());
    }

    // Lombok @Data will generate getters and setters for id, song, genre
    // We need to manually implement equals and hashCode for the entity itself
    // if we want to use it in collections, based on the composite ID.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SongGenre songGenre = (SongGenre) o;
        return Objects.equals(id, songGenre.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Embeddable
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SongGenreId implements Serializable {
        private static final long serialVersionUID = 1L;

        @Column(name = "song_id")
        private Integer songId;

        @Column(name = "genre_id")
        private Integer genreId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SongGenreId that = (SongGenreId) o;
            return Objects.equals(songId, that.songId) &&
                   Objects.equals(genreId, that.genreId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(songId, genreId);
        }
    }
}