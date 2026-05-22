package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.Difficulty;
import ch.uzh.ifi.hase.soprafs26.constant.GameMode;
import ch.uzh.ifi.hase.soprafs26.constant.HistoricalEra;
import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.GamePlayer;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;


import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@WebAppConfiguration
@SpringBootTest
public class GameLobbyServiceIntegrationTest {

    @Autowired private GameLobbyService gameLobbyService;
    @Autowired private GameRepository gameRepository;
    @Autowired private GamePlayerRepository gamePlayerRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private GameInviteRepository gameInviteRepository;

    @MockitoBean
    private CardPreloader cardPreloader;

    private User host;
    private User guest;

    @BeforeEach
    void setup() {
        gamePlayerRepository.deleteAll();
        gameRepository.deleteAll();
        userRepository.deleteAll();

        host = new User();
        host.setUsername("lobby_host");
        host.setPassword("hashed");
        host.setSalt("salt1");
        host.setToken("token-lobby-host");
        host.setStatus(UserStatus.ONLINE);
        host.setBio("");
        host.setCreationDate(Instant.now());
        host.setTotalGamesPlayed(0);
        host.setTotalWins(0);
        host.setTotalPoints(0);
        host.setTotalCorrectPlacements(0);
        host.setTotalIncorrectPlacements(0);
        host = userRepository.saveAndFlush(host);

        guest = new User();
        guest.setUsername("lobby_guest");
        guest.setPassword("hashed");
        guest.setSalt("salt2");
        guest.setToken("token-lobby-guest");
        guest.setStatus(UserStatus.ONLINE);
        guest.setBio("");
        guest.setCreationDate(Instant.now());
        guest.setTotalGamesPlayed(0);
        guest.setTotalWins(0);
        guest.setTotalPoints(0);
        guest.setTotalCorrectPlacements(0);
        guest.setTotalIncorrectPlacements(0);
        guest = userRepository.saveAndFlush(guest);
    }

    @AfterEach
    void tearDown() {
        gamePlayerRepository.deleteAll();
        gameRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createGame_persistsGameAndPlayer_inDB() {
        Game game = gameLobbyService.createGame(HistoricalEra.MODERN, Difficulty.EASY, host.getId());

        Game reloaded = gameRepository.findById(game.getId()).orElseThrow();
        assertEquals("WAITING", reloaded.getStatus());
        assertEquals(HistoricalEra.MODERN, reloaded.getEra());
        assertEquals(Difficulty.EASY, reloaded.getDifficulty());
        assertNotNull(reloaded.getLobbyCode());

        List<GamePlayer> players = gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(reloaded);
        assertEquals(1, players.size());
        assertEquals(host.getId(), players.get(0).getUser().getId());

        User reloadedHost = userRepository.findById(host.getId()).orElseThrow();
        assertEquals(UserStatus.IN_GAME, reloadedHost.getStatus());
    }

    @Test
    void createGame_lobbyCodeIsUnique_inDB() {
        Game g1 = gameLobbyService.createGame(HistoricalEra.MODERN, Difficulty.EASY, host.getId());

        User host2 = new User();
        host2.setUsername("lobby_host2");
        host2.setPassword("hashed"); host2.setSalt("s"); host2.setToken("t2");
        host2.setStatus(UserStatus.ONLINE); host2.setBio("");
        host2.setCreationDate(Instant.now());
        host2.setTotalGamesPlayed(0); host2.setTotalWins(0); host2.setTotalPoints(0);
        host2.setTotalCorrectPlacements(0); host2.setTotalIncorrectPlacements(0);
        host2 = userRepository.saveAndFlush(host2);

        Game g2 = gameLobbyService.createGame(HistoricalEra.MEDIEVAL, Difficulty.HARD, host2.getId());

        assertNotEquals(g1.getLobbyCode(), g2.getLobbyCode());
    }


    @Test
    void joinGame_persistsGuestPlayer_inDB() {
        Game game = gameLobbyService.createGame(HistoricalEra.MODERN, Difficulty.EASY, host.getId());

        gameLobbyService.joinGame(game.getLobbyCode(), guest.getId());

        List<GamePlayer> players = gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(
                gameRepository.findById(game.getId()).orElseThrow());
        assertEquals(2, players.size());

        User reloadedGuest = userRepository.findById(guest.getId()).orElseThrow();
        assertEquals(UserStatus.IN_GAME, reloadedGuest.getStatus());
    }

    @Test
    void joinGame_gameNotWaiting_throwsConflict() {
        Game game = gameLobbyService.createGame(HistoricalEra.MODERN, Difficulty.EASY, host.getId());
        game.setStatus("IN_PROGRESS");
        gameRepository.saveAndFlush(game);

        assertThrows(ResponseStatusException.class,
                () -> gameLobbyService.joinGame(game.getLobbyCode(), guest.getId()));
    }

    @Test
    void joinGame_alreadyInLobby_throwsConflict() {
        Game game = gameLobbyService.createGame(HistoricalEra.MODERN, Difficulty.EASY, host.getId());

        assertThrows(ResponseStatusException.class,
                () -> gameLobbyService.joinGame(game.getLobbyCode(), host.getId()));
    }

    @Test
    void joinGame_invalidLobbyCode_throwsNotFound() {
        assertThrows(ResponseStatusException.class,
                () -> gameLobbyService.joinGame("INVALID", guest.getId()));
    }

    @Test
    void leaveGame_lastPlayer_deletesGame_fromDB() {
        Game game = gameLobbyService.createGame(HistoricalEra.MODERN, Difficulty.EASY, host.getId());
        String code = game.getLobbyCode();

        gameLobbyService.leaveGame(code, host.getId());

        assertTrue(gameRepository.findByLobbyCode(code).isEmpty());
        User reloadedHost = userRepository.findById(host.getId()).orElseThrow();
        assertEquals(UserStatus.ONLINE, reloadedHost.getStatus());
    }

    @Test
    void leaveGame_hostLeaves_reassignsHost_inDB() {
        Game game = gameLobbyService.createGame(HistoricalEra.MODERN, Difficulty.EASY, host.getId());
        gameLobbyService.joinGame(game.getLobbyCode(), guest.getId());

        gameLobbyService.leaveGame(game.getLobbyCode(), host.getId());

        Game reloaded = gameRepository.findByLobbyCode(game.getLobbyCode()).orElseThrow();
        assertEquals(guest.getId(), reloaded.getHostId());

        List<GamePlayer> remaining = gamePlayerRepository.findAllByGameOrderByTurnOrderAsc(reloaded);
        assertEquals(1, remaining.size());
        assertEquals(guest.getId(), remaining.get(0).getUser().getId());
    }

    @Test
    void leaveGame_guestLeaves_hostRemainsHost_inDB() {
        Game game = gameLobbyService.createGame(HistoricalEra.MODERN, Difficulty.EASY, host.getId());
        gameLobbyService.joinGame(game.getLobbyCode(), guest.getId());

        gameLobbyService.leaveGame(game.getLobbyCode(), guest.getId());

        Game reloaded = gameRepository.findByLobbyCode(game.getLobbyCode()).orElseThrow();
        assertEquals(host.getId(), reloaded.getHostId());
    }


    @Test
    void updateSettings_persistsChanges_inDB() {
        Game game = gameLobbyService.createGame(HistoricalEra.MODERN, Difficulty.EASY, host.getId());

        ch.uzh.ifi.hase.soprafs26.rest.dto.GameSettingsPutDTO dto =
                new ch.uzh.ifi.hase.soprafs26.rest.dto.GameSettingsPutDTO();
        dto.setDifficulty(Difficulty.HARD);
        dto.setEra(HistoricalEra.ANCIENT);

        gameLobbyService.updateSettings(game.getId(), dto);

        Game reloaded = gameRepository.findById(game.getId()).orElseThrow();
        assertEquals(Difficulty.HARD, reloaded.getDifficulty());
        assertEquals(HistoricalEra.ANCIENT, reloaded.getEra());
    }


    @Test
    void closeFinishedGame_deletesGame_fromDB() {
        Game game = gameLobbyService.createGame(HistoricalEra.MODERN, Difficulty.EASY, host.getId());
        game.setStatus("FINISHED");
        gameRepository.saveAndFlush(game);

        gameLobbyService.closeFinishedGame(game.getId(), host.getId());

        assertTrue(gameRepository.findById(game.getId()).isEmpty());
    }

    @Test
    void closeFinishedGame_notHost_throwsForbidden() {
        Game game = gameLobbyService.createGame(HistoricalEra.MODERN, Difficulty.EASY, host.getId());
        game.setStatus("FINISHED");
        gameRepository.saveAndFlush(game);

        assertThrows(ResponseStatusException.class,
                () -> gameLobbyService.closeFinishedGame(game.getId(), guest.getId()));
    }


    @Test
    void createRematch_createsNewGame_withSameSettings_inDB() {
        Game game = gameLobbyService.createGame(HistoricalEra.MEDIEVAL, Difficulty.HARD, host.getId());
        gameLobbyService.joinGame(game.getLobbyCode(), guest.getId());
        game.setStatus("FINISHED");
        game.setGameMode(GameMode.TIMELINE);
        gameRepository.saveAndFlush(game);

        Game rematch = gameLobbyService.createRematch(game.getId(), host.getId());

        assertNotNull(rematch.getId());
        assertEquals("WAITING", rematch.getStatus());
        assertEquals(HistoricalEra.MEDIEVAL, rematch.getEra());
        assertEquals(Difficulty.HARD, rematch.getDifficulty());
        assertEquals(game.getId(), rematch.getRematchFromGameId());
    }

    @Test
    void createRematch_calledTwice_returnsSameRematch() {
        Game game = gameLobbyService.createGame(HistoricalEra.MODERN, Difficulty.EASY, host.getId());
        gameLobbyService.joinGame(game.getLobbyCode(), guest.getId());
        game.setStatus("FINISHED");
        game.setGameMode(GameMode.TIMELINE);
        gameRepository.saveAndFlush(game);

        Game rematch1 = gameLobbyService.createRematch(game.getId(), host.getId());
        Game rematch2 = gameLobbyService.createRematch(game.getId(), host.getId());

        assertEquals(rematch1.getId(), rematch2.getId());
    }
}