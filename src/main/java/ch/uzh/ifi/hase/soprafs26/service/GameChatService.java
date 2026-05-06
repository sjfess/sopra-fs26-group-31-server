package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.entity.ChatMessage;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ChatMessageGetDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ch.uzh.ifi.hase.soprafs26.repository.ChatMessageRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;



import java.util.List;

@Service
public class GameChatService {
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final GameStartService gameStartService;

    public GameChatService(
            UserRepository userRepository,
            ChatMessageRepository chatMessageRepository, GameStartService gameStartService) {
        this.userRepository = userRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.gameStartService = gameStartService;
    }

    @Transactional
    public ChatMessageGetDTO addChatMessage(Long gameId, Long playerId, String message) {
        // Game existiert?
        gameStartService.findGameOrThrow(gameId);

        User user = userRepository.findById(playerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));

        ChatMessage msg = new ChatMessage();
        msg.setGameId(gameId);
        msg.setPlayerId(playerId);
        msg.setUsername(user.getUsername());
        msg.setMessage(message);
        msg.setTimestamp(String.valueOf(System.currentTimeMillis()));
        chatMessageRepository.save(msg);

        ChatMessageGetDTO dto = new ChatMessageGetDTO();
        dto.setPlayerId(playerId);
        dto.setUsername(user.getUsername());
        dto.setMessage(message);
        dto.setTimestamp(msg.getTimestamp());
        return dto;
    }

    public List<ChatMessageGetDTO> getChatMessages(Long gameId) {
        gameStartService.findGameOrThrow(gameId); // 404 falls Game nicht existiert
        return chatMessageRepository.findAllByGameIdOrderByTimestampAsc(gameId)
                .stream()
                .map(m -> {
                    ChatMessageGetDTO dto = new ChatMessageGetDTO();
                    dto.setPlayerId(m.getPlayerId());
                    dto.setUsername(m.getUsername());
                    dto.setMessage(m.getMessage());
                    dto.setTimestamp(m.getTimestamp());
                    return dto;
                })
                .toList();
    }
}

