package com.fpoly.webmusicai.entity;

import java.io.Serializable;
import jakarta.persistence.*;
import lombok.*;

@SuppressWarnings("serial")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "Packages", schema = "dbo")
public class Package implements Serializable {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;
	private String name;
	private Integer tokens;
	private Integer price;
	private String description;
	
	@Column(name = "old_price")
	private Integer oldPrice;

	@Column(name = "badge")
	private String badge;

	@Column(name = "tier_code")
	private String tierCode = "CREATOR";

	@Column(name = "duration_days")
	private Integer durationDays = 30;
}
