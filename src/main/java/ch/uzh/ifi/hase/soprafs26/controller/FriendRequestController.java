package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.entity.FriendRequest;
import ch.uzh.ifi.hase.soprafs26.rest.dto.FriendRequestGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.FriendRequestPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.FriendRequestPutDTO;
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

    @PostMapping("/friend-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    public FriendRequestGetDTO sendFriendRequest(
            @RequestBody FriendRequestPostDTO friendRequestPostDTO) {

        FriendRequest created = friendRequestService.sendFriendRequest(
                friendRequestPostDTO.getSenderId(),
                friendRequestPostDTO.getReceiverUsername()
        );

        return DTOMapper.INSTANCE.convertEntityToFriendRequestGetDTO(created);
    }


    @PutMapping("/friend-requests/{requestId}")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public FriendRequestGetDTO respondToFriendRequest(
            @PathVariable long requestId,
            @RequestBody FriendRequestPutDTO friendRequestPutDTO) {

        FriendRequest updated = friendRequestService.respondToFriendRequest(
                requestId,
                friendRequestPutDTO.getReceiverId(),
                friendRequestPutDTO.getAction()
        );
        return DTOMapper.INSTANCE.convertEntityToFriendRequestGetDTO(updated);
    }


    }


