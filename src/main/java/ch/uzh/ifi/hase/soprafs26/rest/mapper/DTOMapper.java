package ch.uzh.ifi.hase.soprafs26.rest.mapper;

import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserPostDTO;
import ch.uzh.ifi.hase.soprafs26.entity.EventCard;
import ch.uzh.ifi.hase.soprafs26.rest.dto.EventCardGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.EventCardRevealDTO;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameGetDTO;
/**
 * DTOMapper
 * This class is responsible for generating classes that will automatically
 * transform/map the internal representation
 * of an entity (e.g., the User) to the external/API representation (e.g.,
 * UserGetDTO for getting, UserPostDTO for creating)
 * and vice versa.
 * Additional mappers can be defined for new entities.
 * Always created one mapper for getting information (GET) and one mapper for
 * creating information (POST).
 */
@Mapper
public interface DTOMapper {

	DTOMapper INSTANCE = Mappers.getMapper(DTOMapper.class);

	@Mapping(source = "name", target = "name")
	@Mapping(source = "username", target = "username")
	User convertUserPostDTOtoEntity(UserPostDTO userPostDTO);

	@Mapping(source = "id", target = "id")
	@Mapping(source = "name", target = "name")
	@Mapping(source = "username", target = "username")
	@Mapping(source = "status", target = "status")
	UserGetDTO convertEntityToUserGetDTO(User user);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "title", target = "title")
    @Mapping(source = "imageUrl", target = "imageUrl")
    EventCardGetDTO convertEntityToEventCardGetDTO(EventCard eventCard);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "title", target = "title")
    @Mapping(source = "year", target = "year")
    @Mapping(source = "imageUrl", target = "imageUrl")
    EventCardRevealDTO convertEntityToEventCardRevealDTO(EventCard eventCard);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "lobbyCode", target = "lobbyCode")
    @Mapping(source = "era", target = "era")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "deckSize", target = "deckSize")
    @Mapping(source = "hostId", target = "hostId")
    @Mapping(target = "cardsRemaining", ignore = true)
    @Mapping(target = "playerIds", ignore = true)
    GameGetDTO convertEntityToGameGetDTO(Game game);
}
