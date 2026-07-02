package com.fpoly.webmusicai.entity;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

import jakarta.persistence.*;
import lombok.*;

@SuppressWarnings("serial")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "Song_Comments", schema = "dbo")
public class SongComment implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "parent_id")
    private Integer parentId;

    private String content;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at")
    private Date createdAt = new Date();

    @ManyToOne
    @JoinColumn(name = "song_id")
    private Song song;

    @ManyToOne
    @JoinColumn(name = "username")
    private User user;

    @Transient // Không ánh xạ vào DB, chỉ dùng ở tầng logic
    private List<SongComment> replies;

    // Phương thức chuyển đổi sang Map, có khả năng đệ quy cho replies
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", this.getId());
        map.put("content", this.getContent());
        map.put("parentId", this.getParentId());
        map.put("createdAt", this.getCreatedAt());

        if (this.getUser() != null) {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("username", this.getUser().getUsername());
            userMap.put("fullname", this.getUser().getFullname());
            userMap.put("photo", this.getUser().getPhoto());
            map.put("user", userMap);
        }

        if (this.replies != null && !this.replies.isEmpty()) {
            map.put("replies", this.replies.stream().map(SongComment::toMap).collect(Collectors.toList()));
        } else {
            map.put("replies", List.of());
        }
        return map;
    }
}
