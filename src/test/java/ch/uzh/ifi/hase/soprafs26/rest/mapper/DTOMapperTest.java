package ch.uzh.ifi.hase.soprafs26.rest.mapper;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserPostDTO;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DTOMapperTest {

    @Test
    public void testCreateUser_fromUserPostDTO_toUser_success() {
        UserPostDTO userPostDTO = new UserPostDTO();
        userPostDTO.setUsername("username");
        userPostDTO.setPassword("password");

        User user = DTOMapper.INSTANCE.convertUserPostDTOtoEntity(userPostDTO);

        assertEquals(userPostDTO.getUsername(), user.getUsername());
        assertEquals(userPostDTO.getPassword(), user.getPassword());
    }

    @Test
    public void testGetUser_fromUser_toUserGetDTO_success() {
        User user = new User();
        user.setId(1L);
        user.setUsername("firstname@lastname");
        user.setToken("1");
        user.setStatus(UserStatus.OFFLINE);
        user.setBio("bio");
        user.setCreationDate(Instant.parse("2026-03-01T10:15:30Z"));
        user.setTotalGamesPlayed(7);
        user.setTotalWins(5);
        user.setTotalPoints(42);

        UserGetDTO userGetDTO = DTOMapper.INSTANCE.convertEntityToUserGetDTO(user);

        assertEquals(user.getId(), userGetDTO.getId());
        assertEquals(user.getUsername(), userGetDTO.getUsername());
        assertEquals(user.getToken(), userGetDTO.getToken());
        assertEquals(user.getStatus(), userGetDTO.getStatus());
        assertEquals(user.getBio(), userGetDTO.getBio());
        assertEquals(user.getCreationDate(), userGetDTO.getCreationDate());
        assertEquals(user.getTotalGamesPlayed(), userGetDTO.getTotalGamesPlayed());
        assertEquals(user.getTotalWins(), userGetDTO.getTotalWins());
        assertEquals(user.getTotalPoints(), userGetDTO.getTotalPoints());
    }
}