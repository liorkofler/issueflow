package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.CommentMention;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentMentionRepository extends JpaRepository<CommentMention, Long> {
    List<CommentMention> findByCommentId(Long commentId);

    @Modifying
    @Query("DELETE FROM CommentMention m WHERE m.comment.id = :commentId")
    void deleteByCommentId(@Param("commentId") Long commentId);

    Page<CommentMention> findByMentionedUserId(Long userId, Pageable pageable);
}
