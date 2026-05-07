package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.entity.ChatMessage;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.ChatMessageRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ChatMessageGetDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameChatServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private GameStartService gameStartService;

    @InjectMocks private GameChatService gameChatService;

    private Game game;
    private User user;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setId(1L);

        user = new User();
        user.setId(42L);
        user.setUsername("testuser");
    }

    // =========================================================================
    // addChatMessage
    // =========================================================================

    @Test
    void addChatMessage_validInput_returnsDTO() {
        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        ChatMessageGetDTO result = gameChatService.addChatMessage(1L, 42L, "Hello!");

        assertEquals(42L, result.getPlayerId());
        assertEquals("testuser", result.getUsername());
        assertEquals("Hello!", result.getMessage());
        assertNotNull(result.getTimestamp());
    }

    @Test
    void addChatMessage_savesChatMessageToRepository() {
        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        gameChatService.addChatMessage(1L, 42L, "Hello!");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(captor.capture());

        ChatMessage saved = captor.getValue();
        assertEquals(1L, saved.getGameId());
        assertEquals(42L, saved.getPlayerId());
        assertEquals("testuser", saved.getUsername());
        assertEquals("Hello!", saved.getMessage());
        assertNotNull(saved.getTimestamp());
    }

    @Test
    void addChatMessage_gameNotFound_throws404() {
        when(gameStartService.findGameOrThrow(99L))
                .thenThrow(new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Game 99 not found"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> gameChatService.addChatMessage(99L, 42L, "Hi"));

        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    void addChatMessage_userNotFound_throws404() {
        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> gameChatService.addChatMessage(1L, 99L, "Hi"));

        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    void addChatMessage_timestampIsCurrentMillis() {
        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        long before = System.currentTimeMillis();
        ChatMessageGetDTO result = gameChatService.addChatMessage(1L, 42L, "time check");
        long after = System.currentTimeMillis();

        long ts = Long.parseLong(result.getTimestamp());
        assertTrue(ts >= before && ts <= after);
    }

    // =========================================================================
    // getChatMessages
    // =========================================================================

    @Test
    void getChatMessages_gameNotFound_throws404() {
        when(gameStartService.findGameOrThrow(99L))
                .thenThrow(new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Game 99 not found"));

        assertThrows(ResponseStatusException.class,
                () -> gameChatService.getChatMessages(99L));

        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    void getChatMessages_noMessages_returnsEmptyList() {
        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);
        when(chatMessageRepository.findAllByGameIdOrderByTimestampAsc(1L))
                .thenReturn(List.of());

        List<ChatMessageGetDTO> result = gameChatService.getChatMessages(1L);

        assertTrue(result.isEmpty());
    }

    @Test
    void getChatMessages_withMessages_returnsAllMapped() {
        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);

        ChatMessage m1 = makeChatMessage(1L, 42L, "alice", "Hello", "1000");
        ChatMessage m2 = makeChatMessage(1L, 7L,  "bob",   "Hi",    "2000");
        when(chatMessageRepository.findAllByGameIdOrderByTimestampAsc(1L))
                .thenReturn(List.of(m1, m2));

        List<ChatMessageGetDTO> result = gameChatService.getChatMessages(1L);

        assertEquals(2, result.size());

        assertEquals(42L,     result.get(0).getPlayerId());
        assertEquals("alice", result.get(0).getUsername());
        assertEquals("Hello", result.get(0).getMessage());
        assertEquals("1000",  result.get(0).getTimestamp());

        assertEquals(7L,    result.get(1).getPlayerId());
        assertEquals("bob", result.get(1).getUsername());
        assertEquals("Hi",  result.get(1).getMessage());
        assertEquals("2000",result.get(1).getTimestamp());
    }

    @Test
    void getChatMessages_messagesReturnedInRepositoryOrder() {
        when(gameStartService.findGameOrThrow(1L)).thenReturn(game);

        ChatMessage m1 = makeChatMessage(1L, 1L, "a", "first",  "100");
        ChatMessage m2 = makeChatMessage(1L, 2L, "b", "second", "200");
        ChatMessage m3 = makeChatMessage(1L, 3L, "c", "third",  "300");
        when(chatMessageRepository.findAllByGameIdOrderByTimestampAsc(1L))
                .thenReturn(List.of(m1, m2, m3));

        List<ChatMessageGetDTO> result = gameChatService.getChatMessages(1L);

        assertEquals("first",  result.get(0).getMessage());
        assertEquals("second", result.get(1).getMessage());
        assertEquals("third",  result.get(2).getMessage());
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private ChatMessage makeChatMessage(Long gameId, Long playerId,
                                        String username, String message,
                                        String timestamp) {
        ChatMessage m = new ChatMessage();
        m.setGameId(gameId);
        m.setPlayerId(playerId);
        m.setUsername(username);
        m.setMessage(message);
        m.setTimestamp(timestamp);
        return m;
    }
}