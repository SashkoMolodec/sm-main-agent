package com.sashkomusic.mainagent.ai.service;

import com.sashkomusic.mainagent.domain.model.MusicSearchQuery;
import dev.langchain4j.service.UserMessage;

public interface AiService {

    @UserMessage("Extract information on author with his album title from msg: {{it}}")
    MusicSearchQuery extractSearchQuery(String message);
}
