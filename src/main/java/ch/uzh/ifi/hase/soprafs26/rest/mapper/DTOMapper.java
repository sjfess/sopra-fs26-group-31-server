package ch.uzh.ifi.hase.soprafs26.rest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.mapstruct.BeanMapping;

import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserPostDTO;

import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import ch.uzh.ifi.hase.soprafs26.rest.dto.EventCardGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.EventCardRevealDTO;

import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameGetDTO;

import ch.uzh.ifi.hase.soprafs26.entity.FriendRequest;
import ch.uzh.ifi.hase.soprafs26.rest.dto.FriendRequestGetDTO;

import ch.uzh.ifi.hase.soprafs26.rest.dto.PlayerSummaryDTO;
import ch.uzh.ifi.hase.soprafs26.entity.GamePlayer;

/**
 * DTOMapper
 * This class is responsible for generating classes that will automatically
 * transform/map the internal representation
 * of an entity to the external/API representation and vice versa.
 */
@Mapper
public interface DTOMapper {

    DTOMapper INSTANCE = Mappers.getMapper(DTOMapper.class);

    // User
    @BeanMapping(ignoreByDefault = true)
    @Mapping(source = "username", target = "username")
    @Mapping(source = "password", target = "password")
    User convertUserPostDTOtoEntity(UserPostDTO userPostDTO);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "username", target = "username")
    @Mapping(source = "token", target = "token")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "bio", target = "bio")
    @Mapping(source = "creationDate", target = "creationDate")
    @Mapping(source = "totalGamesPlayed", target = "totalGamesPlayed")
    @Mapping(source = "totalWins", target = "totalWins")
    @Mapping(source = "totalPoints", target = "totalPoints")
    UserGetDTO convertEntityToUserGetDTO(User user);

    // FriendRequest
    @Mapping(source = "id", target = "id")
    @Mapping(source = "sender.id", target = "senderId")
    @Mapping(source = "sender.username", target = "senderUsername")
    @Mapping(source = "receiver.id", target = "receiverId")
    @Mapping(source = "receiver.username", target = "receiverUsername")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "createdAt", target = "createdAt")
    FriendRequestGetDTO convertEntityToFriendRequestGetDTO(FriendRequest friendRequest);

    // EventCard
    @Mapping(source = "id", target = "id")
    @Mapping(source = "title", target = "title")
    @Mapping(source = "imageUrl", target = "imageUrl")
    EventCardGetDTO convertEntityToEventCardGetDTO(EventCard eventCard);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "title", target = "title")
    @Mapping(source = "year", target = "year")
    @Mapping(source = "imageUrl", target = "imageUrl")
    EventCardRevealDTO convertEntityToEventCardRevealDTO(EventCard eventCard);

    // Game
    @Mapping(source = "id", target = "id")
    @Mapping(source = "difficulty", target = "difficulty")
    @Mapping(source = "lobbyCode", target = "lobbyCode")
    @Mapping(source = "era", target = "era")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "deckSize", target = "deckSize")
    @Mapping(source = "hostId", target = "hostId")
    @Mapping(target = "cardsRemaining", ignore = true)
    @Mapping(source = "gamePlayers", target = "players")
    @Mapping(target = "timelineSize", ignore = true)
    @Mapping(source = "maxPlayers", target = "maxPlayers")
    @Mapping(source = "gameMode", target = "gameMode")
    GameGetDTO convertEntityToGameGetDTO(Game game);

    @Mapping(source = "user.id", target = "id")
    @Mapping(source = "user.username", target = "username")
    PlayerSummaryDTO convertGamePlayerToPlayerSummaryDTO(GamePlayer gamePlayer);
}