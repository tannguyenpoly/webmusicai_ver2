package com.fpoly.webmusicai.entity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@SuppressWarnings("serial")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "Songs", schema = "dbo")
public class Song implements Serializable {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	private String title;
	private String prompt;

    @Column(name = "audio_url", columnDefinition = "VARCHAR(MAX)")
    private String audioUrl;

	private String status; // PENDING, COMPLETED, FAILED, CANCELLED

	@Column(name = "is_public")
	private Boolean isPublic = false;

	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "created_at")
	private Date createdAt = new Date();

	@Column(name = "lyrics")
	private String lyrics;

	@Column(name = "model_ver")
	private String modelVer;

	@Column(name = "is_remix")
	private Boolean isRemix = false;

	@Column(name = "parent_id")
	private Integer parentId;

	@Column(name = "cover_url", columnDefinition = "VARCHAR(500)")
	private String coverUrl;

	@Column(name = "listen_count")
	private Integer listenCount = 0;

	@ManyToOne
	@JoinColumn(name = "username")
	private User user;
	
	@ManyToMany
	@JoinTable(name = "songgenres", joinColumns = @JoinColumn(name = "song_id"), inverseJoinColumns = @JoinColumn(name = "genre_id"))
	private List<Genre> genres = new ArrayList<>();

	public Map<String, Object> toMap() {
		Map<String, Object> map = new HashMap<>();
		map.put("id", this.getId());
		map.put("title", this.getTitle());
		map.put("prompt", this.getPrompt());
		map.put("audioUrl", this.getAudioUrl());
		map.put("status", this.getStatus());
		map.put("isPublic", this.getIsPublic());
		map.put("createdAt", this.getCreatedAt());
		map.put("isRemix", this.getIsRemix());
		map.put("parentId", this.getParentId());
		map.put("coverUrl", this.getCoverUrl());
		map.put("listenCount", this.getListenCount() != null ? this.getListenCount() : 0);

		if (this.getUser() != null) {
			map.put("username", this.getUser().getUsername());
		}
		return map;
	}

}
