package com.doova.ktab.features.story.service;

import com.doova.ktab.features.story.model.Turn;
import com.doova.ktab.model.user.User;

import java.util.List;

public interface StorySessionService {

    List<Turn> startSession(Long storyId, User reader);

    List<Turn> chooseAndGenerateNext(Long sessionId, String choiceRaw);
}
