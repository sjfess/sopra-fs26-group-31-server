package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.rest.dto.LoginPostDTO;
import ch.uzh.ifi.hase.soprafs26.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    public void login_validInput_returnsUserGetDTO() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("alex");
        user.setToken("token-1");
        user.setStatus(UserStatus.ONLINE);
        user.setBio("bio");
        user.setCreationDate(Instant.parse("2026-03-01T10:15:30Z"));
        user.setTotalGamesPlayed(3);
        user.setTotalWins(2);
        user.setTotalPoints(20);

        LoginPostDTO loginPostDTO = new LoginPostDTO();
        loginPostDTO.setUsername("alex");
        loginPostDTO.setPassword("secretPassword");

        given(authService.login(eq("alex"), eq("secretPassword"))).willReturn(user);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(loginPostDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.username", is("alex")))
                .andExpect(jsonPath("$.token", is("token-1")))
                .andExpect(jsonPath("$.status", is("ONLINE")))
                .andExpect(jsonPath("$.bio", is("bio")))
                .andExpect(jsonPath("$.totalGamesPlayed", is(3)))
                .andExpect(jsonPath("$.totalWins", is(2)))
                .andExpect(jsonPath("$.totalPoints", is(20)));
    }

    @Test
    public void logout_validToken_returnsNoContent() throws Exception {
        doNothing().when(authService).logout("valid-token");

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "valid-token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    private String asJsonString(final Object object) {
        try {
            return new ObjectMapper().writeValueAsString(object);
        } catch (JacksonException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    String.format("The request body could not be created. %s", e)
            );
        }
    }
}