package com.fpoly.webmusicai.entity;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
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
@Table(name = "Users")
public class User implements Serializable {
    @Id
    private String username;
    @JsonIgnore
    private String password;
    private String fullname;
    private String photo;
    
    @Column(name = "token_balance")
    @JsonIgnore
    private Integer tokenBalance = 0;
    
    @JsonIgnore
    private Boolean enabled = true;

    @JsonIgnore
    @OneToMany(mappedBy = "user", fetch = FetchType.EAGER)
    private List<Authority> authorities;

    @JsonIgnore
    @OneToMany(mappedBy = "user")
    private List<Song> songs;

    @JsonIgnore
    @OneToMany(mappedBy = "user")
    private List<Transaction> transactions;
    
    @Column(name = "email")
    @JsonIgnore
    private String email;

    @Column(name = "auth_provider")
    @JsonIgnore
    private String authProvider = "LOCAL"; // LOCAL, GOOGLE, BOTH
    
    @Column(name = "account_tier")
    @JsonIgnore
    private String accountTier = "FREE";

    @JsonIgnore
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "pro_expired_at")
    private Date proExpiredAt;

    @Column(name = "token_version")
    @JsonIgnore
    private Integer tokenVersion = 0;

    @JsonIgnore
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "last_seen_at")
    private Date lastSeenAt;
}
