package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.audit.AuditService;
import com.att.tdp.issueflow.dto.request.AddCommentRequest;
import com.att.tdp.issueflow.dto.request.UpdateCommentRequest;
import com.att.tdp.issueflow.dto.response.CommentResponse;
import com.att.tdp.issueflow.dto.response.MentionedUserDto;
import com.att.tdp.issueflow.dto.response.MentionsPageResponse;
import com.att.tdp.issueflow.entity.*;
import com.att.tdp.issueflow.exception.ConflictException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.CommentMentionRepository;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CommentService {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\w+)", Pattern.CASE_INSENSITIVE);

    private final CommentRepository commentRepository;
    private final CommentMentionRepository commentMentionRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional
    public CommentResponse addComment(Long ticketId, AddCommentRequest request, String authorUsername) {
        Ticket ticket = ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));

        User author = userRepository.findByUsername(authorUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + authorUsername));

        Comment comment = Comment.builder()
                .content(request.getContent())
                .ticket(ticket)
                .author(author)
                .build();

        Comment saved = commentRepository.save(comment);

        List<CommentMention> mentions = saveMentions(saved, request.getContent());

        auditService.logAction("COMMENT", saved.getId(), "CREATE",
                author.getId(), AuditActorType.USER,
                "{\"ticketId\":" + ticketId + "}");

        return toResponse(saved, mentions);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsForTicket(Long ticketId) {
        return commentRepository.findByTicketIdOrderByCreatedAtDesc(ticketId).stream()
                .map(c -> toResponse(c, commentMentionRepository.findByCommentId(c.getId())))
                .toList();
    }

    @Transactional
    public CommentResponse updateComment(Long commentId, UpdateCommentRequest request, String currentUsername) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));

        User actor = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUsername));

        comment.setContent(request.getContent());

        Comment saved;
        try {
            saved = commentRepository.save(comment);
        } catch (OptimisticLockingFailureException e) {
            throw new ConflictException("Comment is being updated by another user");
        }

        List<CommentMention> existingMentions = commentMentionRepository.findByCommentId(commentId);
        Set<String> existingUsernames = new HashSet<>();
        for (CommentMention m : existingMentions) {
            existingUsernames.add(m.getMentionedUser().getUsername().toLowerCase());
        }

        Set<String> newUsernames = parseMentions(request.getContent());

        for (String username : newUsernames) {
            if (!existingUsernames.contains(username)) {
                userRepository.findByUsernameIgnoreCase(username).ifPresent(user ->
                        commentMentionRepository.save(CommentMention.builder()
                                .comment(saved)
                                .mentionedUser(user)
                                .build()));
            }
        }

        for (CommentMention existing : existingMentions) {
            if (!newUsernames.contains(existing.getMentionedUser().getUsername().toLowerCase())) {
                commentMentionRepository.delete(existing);
            }
        }

        auditService.logAction("COMMENT", commentId, "UPDATE",
                actor.getId(), AuditActorType.USER,
                "{\"ticketId\":" + saved.getTicket().getId() + "}");

        return toResponse(saved, commentMentionRepository.findByCommentId(commentId));
    }

    @Transactional
    public void deleteComment(Long commentId, String currentUsername) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));

        User actor = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUsername));

        Long ticketId = comment.getTicket().getId();

        commentMentionRepository.deleteByCommentId(commentId);
        commentRepository.delete(comment);

        auditService.logAction("COMMENT", commentId, "DELETE",
                actor.getId(), AuditActorType.USER,
                "{\"ticketId\":" + ticketId + "}");
    }

    @Transactional(readOnly = true)
    public MentionsPageResponse getMentionsForUser(Long userId, int page, int pageSize) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        PageRequest pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CommentMention> mentionPage = commentMentionRepository.findByMentionedUserId(userId, pageable);

        List<CommentResponse> data = mentionPage.getContent().stream()
                .map(m -> toResponse(m.getComment(),
                        commentMentionRepository.findByCommentId(m.getComment().getId())))
                .toList();

        return new MentionsPageResponse(data, mentionPage.getTotalElements(), page);
    }

    // ── helpers ──

    Set<String> parseMentions(String content) {
        Set<String> mentions = new LinkedHashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            mentions.add(matcher.group(1).toLowerCase());
        }
        return mentions;
    }

    private List<CommentMention> saveMentions(Comment comment, String content) {
        List<CommentMention> result = new ArrayList<>();
        for (String username : parseMentions(content)) {
            userRepository.findByUsernameIgnoreCase(username).ifPresent(user -> {
                CommentMention mention = commentMentionRepository.save(CommentMention.builder()
                        .comment(comment)
                        .mentionedUser(user)
                        .build());
                result.add(mention);
            });
        }
        return result;
    }

    private CommentResponse toResponse(Comment comment, List<CommentMention> mentions) {
        List<MentionedUserDto> mentionedUsers = mentions.stream()
                .map(m -> new MentionedUserDto(
                        m.getMentionedUser().getId(),
                        m.getMentionedUser().getUsername(),
                        m.getMentionedUser().getFullName()))
                .toList();
        return new CommentResponse(
                comment.getId(),
                comment.getTicket().getId(),
                comment.getAuthor().getId(),
                comment.getAuthor().getUsername(),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                mentionedUsers
        );
    }
}
