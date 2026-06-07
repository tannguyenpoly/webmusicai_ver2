package com.fpoly.webmusicai.entity;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
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
    private Integer tokenBalance = 0;
    
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
    private String email;
}