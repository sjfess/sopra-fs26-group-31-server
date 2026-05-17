package ch.uzh.ifi.hase.soprafs26.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GameInviteTest {

    /** getters & setters */

    @Test
    void setAndGetId() {
        GameInvite invite = new GameInvite();
        invite.setId(1L);
        assertEquals(1L, invite.getId());
    }

    @Test
    void setAndGetGameId() {
        GameInvite invite = new GameInvite();
        invite.setGameId(42L);
        assertEquals(42L, invite.getGameId());
    }

    @Test
    void setAndGetLobbyCode() {
        GameInvite invite = new GameInvite();
        invite.setLobbyCode("ABC123");
        assertEquals("ABC123", invite.getLobbyCode());
    }

    @Test
    void setAndGetFromUserId() {
        GameInvite invite = new GameInvite();
        invite.setFromUserId(7L);
        assertEquals(7L, invite.getFromUserId());
    }

    @Test
    void setAndGetFromUsername() {
        GameInvite invite = new GameInvite();
        invite.setFromUsername("alice");
        assertEquals("alice", invite.getFromUsername());
    }

    @Test
    void setAndGetToUserId() {
        GameInvite invite = new GameInvite();
        invite.setToUserId(99L);
        assertEquals(99L, invite.getToUserId());
    }

    /** default state */

    @Test
    void newInvite_allFieldsNullByDefault() {
        GameInvite invite = new GameInvite();
        assertNull(invite.getId());
        assertNull(invite.getGameId());
        assertNull(invite.getLobbyCode());
        assertNull(invite.getFromUserId());
        assertNull(invite.getFromUsername());
        assertNull(invite.getToUserId());
    }

    /** field independence */

    @Test
    void twoInvites_areIndependent() {
        GameInvite a = new GameInvite();
        GameInvite b = new GameInvite();
        a.setGameId(1L);
        b.setGameId(2L);
        assertNotEquals(a.getGameId(), b.getGameId());
    }

    @Test
    void overwritingField_updatesToNewValue() {
        GameInvite invite = new GameInvite();
        invite.setLobbyCode("FIRST");
        invite.setLobbyCode("SECOND");
        assertEquals("SECOND", invite.getLobbyCode());
    }

    /** object construction */

    @Test
    void fullyPopulatedInvite_allFieldsCorrect() {
        GameInvite invite = new GameInvite();
        invite.setId(1L);
        invite.setGameId(10L);
        invite.setLobbyCode("XYZ");
        invite.setFromUserId(2L);
        invite.setFromUsername("bob");
        invite.setToUserId(3L);

        assertAll(
                () -> assertEquals(1L,    invite.getId()),
                () -> assertEquals(10L,   invite.getGameId()),
                () -> assertEquals("XYZ", invite.getLobbyCode()),
                () -> assertEquals(2L,    invite.getFromUserId()),
                () -> assertEquals("bob", invite.getFromUsername()),
                () -> assertEquals(3L,    invite.getToUserId())
        );
    }
}