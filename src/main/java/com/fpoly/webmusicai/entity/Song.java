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
@Table(name = "Songs", schema = "dbo")
public class Song implements Serializable {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	private String title;
	private String prompt;

	@Column(name = "audio_url")
	private String audioUrl;

	private String status; // PENDING, COMPLETED, FAILED

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

	@ManyToOne
	@JoinColumn(name = "username")
	private User user;
}