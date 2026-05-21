package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.audit.AuditService;
import com.att.tdp.issueflow.dto.request.AddCommentRequest;
import com.att.tdp.issueflow.dto.request.UpdateCommentRequest;
import com.att.tdp.issueflow.dto.response.CommentResponse;
import com.att.tdp.issueflow.entity.*;
import com.att.tdp.issueflow.repository.CommentMentionRepository;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private CommentMentionRepository commentMentionRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private CommentService commentService;

    // ── helpers ──

    private User user(long id, String username) {
        return User.builder().id(id).username(username).fullName(username + " Full").build();
    }

    private Ticket ticket() {
        return Ticket.builder().id(1L).build();
    }

    private Comment comment(long id, String content) {
        return Comment.builder()
                .id(id)
                .content(content)
                .ticket(ticket())
                .author(user(1L, "author"))
                .version(0L)
                .build();
    }

    private CommentMention mention(Comment comment, User mentionedUser) {
        return CommentMention.builder()
                .id(99L)
                .comment(comment)
                .mentionedUser(mentionedUser)
                .build();
    }

    // ── parseMentions (unit) ──

    @Test
    void parseMentions_singleMention_returnsOneUsername() {
        Set<String> result = commentService.parseMentions("Hello @alice, how are you?");
        assertThat(result).containsExactly("alice");
    }

    @Test
    void parseMentions_multipleMentions_returnsAllUsernames() {
        Set<String> result = commentService.parseMentions("@alice and @bob should review this");
        assertThat(result).containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void parseMentions_caseInsensitive_normalizesToLowercase() {
        Set<String> result = commentService.parseMentions("Hey @ALICE and @Bob!");
        assertThat(result).containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void parseMentions_noMentions_returnsEmptySet() {
        Set<String> result = commentService.parseMentions("No mentions here");
        assertThat(result).isEmpty();
    }

    @Test
    void parseMentions_duplicateMentions_deduplicates() {
        Set<String> result = commentService.parseMentions("@alice and @alice again");
        assertThat(result).containsExactly("alice");
    }

    // ── addComment mention saving ──

    @Test
    void addComment_singleMention_savesOneMention() {
        User alice = user(2L, "alice");
        Comment saved = comment(10L, "Hello @alice");
        Ticket t = ticket();

        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(t));
        when(userRepository.findByUsername("author")).thenReturn(Optional.of(user(1L, "author")));
        when(commentRepository.save(any())).thenReturn(saved);
        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(alice));
        when(commentMentionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AddCommentRequest req = new AddCommentRequest();
        req.setContent("Hello @alice");

        CommentResponse response = commentService.addComment(1L, req, "author");

        ArgumentCaptor<CommentMention> captor = ArgumentCaptor.forClass(CommentMention.class);
        verify(commentMentionRepository).save(captor.capture());
        assertThat(captor.getValue().getMentionedUser().getUsername()).isEqualTo("alice");
        assertThat(response.mentionedUsers()).hasSize(1);
        assertThat(response.mentionedUsers().get(0).username()).isEqualTo("alice");
    }

    @Test
    void addComment_multipleMentions_savesAllMentions() {
        User alice = user(2L, "alice");
        User bob = user(3L, "bob");
        Comment saved = comment(10L, "@alice and @bob");
        Ticket t = ticket();

        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(t));
        when(userRepository.findByUsername("author")).thenReturn(Optional.of(user(1L, "author")));
        when(commentRepository.save(any())).thenReturn(saved);
        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(alice));
        when(userRepository.findByUsernameIgnoreCase("bob")).thenReturn(Optional.of(bob));
        when(commentMentionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AddCommentRequest req = new AddCommentRequest();
        req.setContent("@alice and @bob");

        commentService.addComment(1L, req, "author");

        verify(commentMentionRepository, times(2)).save(any(CommentMention.class));
    }

    @Test
    void addComment_caseInsensitiveMention_matchesUserByIgnoreCase() {
        User alice = user(2L, "alice");
        Comment saved = comment(10L, "Hey @ALICE");
        Ticket t = ticket();

        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(t));
        when(userRepository.findByUsername("author")).thenReturn(Optional.of(user(1L, "author")));
        when(commentRepository.save(any())).thenReturn(saved);
        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(alice));
        when(commentMentionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AddCommentRequest req = new AddCommentRequest();
        req.setContent("Hey @ALICE");

        commentService.addComment(1L, req, "author");

        // lookup is with lowercased username
        verify(userRepository).findByUsernameIgnoreCase("alice");
        verify(commentMentionRepository).save(any(CommentMention.class));
    }

    @Test
    void addComment_unknownMentionedUser_skipsWithoutError() {
        Comment saved = comment(10L, "Hey @nobody");
        Ticket t = ticket();

        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(t));
        when(userRepository.findByUsername("author")).thenReturn(Optional.of(user(1L, "author")));
        when(commentRepository.save(any())).thenReturn(saved);
        when(userRepository.findByUsernameIgnoreCase("nobody")).thenReturn(Optional.empty());

        AddCommentRequest req = new AddCommentRequest();
        req.setContent("Hey @nobody");

        commentService.addComment(1L, req, "author");

        verify(commentMentionRepository, never()).save(any());
    }

    // ── updateComment mention diff ──

    @Test
    void updateComment_removedMention_deletesMention() {
        User alice = user(2L, "alice");
        Comment existing = comment(10L, "Hello @alice");
        Comment saved = comment(10L, "Hello world");
        CommentMention aliceMention = mention(existing, alice);

        when(commentRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(userRepository.findByUsername("author")).thenReturn(Optional.of(user(1L, "author")));
        when(commentRepository.save(any())).thenReturn(saved);
        // first call: existing mentions; second call: final mentions for response
        when(commentMentionRepository.findByCommentId(10L))
                .thenReturn(List.of(aliceMention))
                .thenReturn(List.of());

        UpdateCommentRequest req = new UpdateCommentRequest();
        req.setContent("Hello world");

        commentService.updateComment(10L, req, "author");

        verify(commentMentionRepository).delete(aliceMention);
        verify(commentMentionRepository, never()).save(any());
    }

    @Test
    void updateComment_addedMention_savesMention() {
        User alice = user(2L, "alice");
        Comment existing = comment(10L, "Hello world");
        Comment saved = comment(10L, "Hello @alice");

        when(commentRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(userRepository.findByUsername("author")).thenReturn(Optional.of(user(1L, "author")));
        when(commentRepository.save(any())).thenReturn(saved);
        when(commentMentionRepository.findByCommentId(10L))
                .thenReturn(List.of())
                .thenReturn(List.of());
        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(alice));
        when(commentMentionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateCommentRequest req = new UpdateCommentRequest();
        req.setContent("Hello @alice");

        commentService.updateComment(10L, req, "author");

        ArgumentCaptor<CommentMention> captor = ArgumentCaptor.forClass(CommentMention.class);
        verify(commentMentionRepository).save(captor.capture());
        assertThat(captor.getValue().getMentionedUser().getUsername()).isEqualTo("alice");
        verify(commentMentionRepository, never()).delete(any());
    }
}
