package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.entity.FriendRequest;
import ch.uzh.ifi.hase.soprafs26.rest.dto.FriendRequestGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.FriendRequestPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.mapper.DTOMapper;
import ch.uzh.ifi.hase.soprafs26.service.FriendRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
public class FriendRequestController {

    private final FriendRequestService friendRequestService;

    FriendRequestController(FriendRequestService friendRequestService) {
        this.friendRequestService = friendRequestService;
    }

    @PostMapping("/friendRequests")
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    public FriendRequestGetDTO sendFriendRequest(
            @RequestHeader("Authorization") String token,
            @RequestBody FriendRequestPostDTO friendRequestPostDTO) {

        FriendRequest createdRequest = friendRequestService.sendFriendRequest(
                token,
                friendRequestPostDTO.getReceiverId()
        );

        return DTOMapper.INSTANCE.convertEntityToFriendRequestGetDTO(createdRequest);
    }

    @PutMapping("/friendRequests/{requestId}/accept")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public FriendRequestGetDTO acceptFriendRequest(
            @RequestHeader("Authorization") String token,
            @PathVariable Long requestId) {

        FriendRequest acceptedRequest = friendRequestService.acceptFriendRequest(token, requestId);
        return DTOMapper.INSTANCE.convertEntityToFriendRequestGetDTO(acceptedRequest);
    }

    @PutMapping("/friendRequests/{requestId}/decline")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public FriendRequestGetDTO declineFriendRequest(
            @RequestHeader("Authorization") String token,
            @PathVariable Long requestId) {

        FriendRequest declinedRequest = friendRequestService.declineFriendRequest(token, requestId);
        return DTOMapper.INSTANCE.convertEntityToFriendRequestGetDTO(declinedRequest);
    }

    @GetMapping("/friendRequests/incoming")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public List<FriendRequestGetDTO> getIncomingPendingRequests(
            @RequestHeader("Authorization") String token) {

        List<FriendRequest> requests = friendRequestService.getIncomingPendingRequests(token);
        List<FriendRequestGetDTO> friendRequestGetDTOs = new ArrayList<>();

        for (FriendRequest request : requests) {
            friendRequestGetDTOs.add(DTOMapper.INSTANCE.convertEntityToFriendRequestGetDTO(request));
        }

        return friendRequestGetDTOs;
    }

    @GetMapping("/friendRequests/outgoing")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public List<FriendRequestGetDTO> getOutgoingPendingRequests(
            @RequestHeader("Authorization") String token) {

        List<FriendRequest> requests = friendRequestService.getOutgoingPendingRequests(token);
        List<FriendRequestGetDTO> friendRequestGetDTOs = new ArrayList<>();

        for (FriendRequest request : requests) {
            friendRequestGetDTOs.add(DTOMapper.INSTANCE.convertEntityToFriendRequestGetDTO(request));
        }

        return friendRequestGetDTOs;
    }
}