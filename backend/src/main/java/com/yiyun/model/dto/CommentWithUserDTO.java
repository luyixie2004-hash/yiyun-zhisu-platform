package com.yiyun.model.dto;

import com.yiyun.model.entity.Comment;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CommentWithUserDTO extends Comment {
    
    private String username;
    
    private String nickname;
    
    private String avatar;
    
    private Integer likeCount = 0;
    private Boolean liked = false;

    public static CommentWithUserDTO from(Comment comment, String username, String nickname, String avatar) {
        CommentWithUserDTO dto = new CommentWithUserDTO();
        dto.setId(comment.getId());
        dto.setHeritageId(comment.getHeritageId());
        dto.setUserId(comment.getUserId());
        dto.setContent(comment.getContent());
        dto.setImages(comment.getImages());
        dto.setEmojiIds(comment.getEmojiIds());
        dto.setParentId(comment.getParentId());
        dto.setStatus(comment.getStatus());
        dto.setAuditNote(comment.getAuditNote());
        dto.setCreatedAt(comment.getCreatedAt());
        dto.setUsername(username);
        dto.setNickname(nickname != null && !nickname.isEmpty() ? nickname : username);
        dto.setAvatar(avatar);
        return dto;
    }
}
