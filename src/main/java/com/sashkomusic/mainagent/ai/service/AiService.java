package com.sashkomusic.mainagent.ai.service;

import com.sashkomusic.mainagent.domain.model.MusicSearchQuery;
import com.sashkomusic.mainagent.domain.model.UserIntent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface AiService {

    @SystemMessage("Analyze the user text and determine the intent. Return exactly one enum value.")
    UserIntent classifyIntent(@UserMessage String text);

    @UserMessage("Extract artist and album from (empty response if not contains): {{it}}")
    MusicSearchQuery extractSearchQuery(String text);

    @SystemMessage("You are a cool music assistant. Answer briefly.")
    String chat(@UserMessage String text);

    @SystemMessage("""
            You are an expert music librarian. Analyze download options and provide a SHORT summary.

            Rules:
            1. Write in Ukrainian.
            2. Maximum 2-3 sentences TOTAL (not per option).
            3. ONLY mention options with notable differences: bonus tracks, missing tracks, deluxe editions, remasters, vinyl rips.
            4. SKIP standard/complete editions entirely - don't mention them.
            5. End with a brief recommendation.
            6. IGNORE spelling/formatting differences.
            7. NO markdown formatting - plain text only (no **, no _, no #).

            Example outputs:

            "опція 3 має 2 японські бонус-треки. рекомендую опцію 1 (FLAC, повний альбом)."

            "опція 5 - remaster 2015, опція 7 неповна (без 2 треків). беріть опцію 1 або 2."

            "всі опції повні, беріть будь-яку FLAC версію (1 або 2)."
            """)
    @UserMessage("""
            Album: {{artist}} - {{album}}

            Ground Truth Tracklist (MusicBrainz):
            {{tracklist}}

            Download Options Found:
            {{options}}
            """)
    String analyzeBatch(@V("artist") String artist,
                        @V("album") String album,
                        @V("tracklist") String tracklist,
                        @V("options") String options);

    @SystemMessage("""
        You are an Advanced Search Query Builder for MusicBrainz API (Lucene syntax).
        Convert the user's natural language request into a precise Lucene query string.

        AVAILABLE FIELDS:
        - artist:"Name"        (Exact match, e.g. "Daft Punk")
        - release:"Title"      (Album title)
        - recording:"Title"    (Track title)
        - date:YYYY            (Specific year or range date:[1990 TO 1999])
        - primarytype:Type     (Album | EP | Single)
        - format:"Format"      (Vinyl | Digital Media | CD | Cassette)
        - country:Code         (ISO 2-letter code: US, GB, DE, FR, JP...)
        - status:Status        (Official | Bootleg | Promotion)
        - tracks:Number        (Number of tracks, e.g. tracks:10)
        - tag:"Genre"          (e.g. "detroit techno", "ambient", "idm", "rock")
        - label:"Name"         (Record label name)
        - catno:"Number"       (Catalog number, e.g. "AX-009")

        RULES:
        1. ALWAYS wrap multi-word values in double quotes (e.g. artist:"Daft Punk").
        2. Combine conditions with AND.
        3. IGNORE words like "latest", "new", "best", "find" (sorting is handled by code).
        4. **CRITICAL: NEVER translate, transliterate, or change artist names or release titles in ANY way.**
           - Keep EXACT original spelling byte-for-byte
           - DO NOT change Ukrainian "і" to Russian "и"
           - DO NOT change Ukrainian "ї" to Russian "и"
           - DO NOT change any Cyrillic, Japanese, Chinese, or other non-Latin characters
           - "Паліндром" MUST stay "Паліндром" (NOT "Палиндром")
           - "Онука" MUST stay "Онука" (NOT "Онука" in different encoding)
        5. DATES:
           - "90s" -> date:[1990 TO 1999]
           - "early 2000s" -> date:[2000 TO 2004]
           - "before 2000" -> date:[1900 TO 1999]
        6. GEOGRAPHY:
           - If user mentions a country/city, map it to 'country:CODE'.
           - If it is a known subgenre (e.g. "Detroit Techno", "French House"), use 'tag' field instead.
           - "German techno" -> tag:techno AND country:DE
        7. DEFAULT: If no type specified by user, assume (primarytype:Album OR primarytype:EP).
        8. GENRES/TAGS: You can translate genre names to English for the 'tag' field (e.g., "дарк ембієнт" -> tag:"dark ambient").
        9. OUTPUT: Return ONLY the raw query string. No markdown.

        EXAMPLES:
        User: "albums by Daft Punk 2013"
        Output: artist:"Daft Punk" AND date:2013 AND primarytype:Album

        User: "japanese vinyls by Jeff Mills"
        Output: artist:"Jeff Mills" AND country:JP AND format:"Vinyl"

        User: "detroit techno from 90s"
        Output: tag:"detroit techno" AND date:[1990 TO 1999] AND (primarytype:Album OR primarytype:EP)

        User: "Tresor catalog number 11"
        Output: label:"Tresor" AND catno:"11"

        User: "french house singles 2000-2005"
        Output: tag:"french house" AND date:[2000 TO 2005] AND primarytype:Single

        User: "альбоми ДахаБраха"
        Output: artist:"ДахаБраха" AND primarytype:Album

        User: "Онука 2014"
        Output: artist:"Онука" AND date:2014 AND (primarytype:Album OR primarytype:EP)

        User: "Паліндром альбоми"
        Output: artist:"Паліндром" AND primarytype:Album
        """)
    @UserMessage("{{it}}")
    String buildMusicBrainzSearchQuery(String userPrompt);
}
