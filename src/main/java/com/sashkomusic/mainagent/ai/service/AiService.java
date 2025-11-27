package com.sashkomusic.mainagent.ai.service;

import com.sashkomusic.mainagent.domain.model.MusicSearchQuery;
import com.sashkomusic.mainagent.domain.model.SearchRequest;
import com.sashkomusic.mainagent.domain.model.UserIntent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface AiService {

    @SystemMessage("""
            Analyze the user text and determine the intent. Return exactly one enum value.

            Intents:
            1. SEARCH_FOR_RELEASE - User wants to search for music (artist, album, track, year, genre, etc.)
               Examples:
               - "Daft Punk" (just artist name)
               - "Онука" (just artist name in Ukrainian/other language)
               - "Aphex Twin ambient tracks"
               - "German techno 90s"
               - "найди альбом Хвороба"
               - Any unique artist/album name WITHOUT explicit download request

            2. CHOOSE_DOWNLOAD_OPTION - User is selecting a download option (typically a single digit 1-5)
               Examples:
               - "1" (single digit is MOST LIKELY download choice)
               - "2"
               - "3"
               - "перший"
               - "second"
               - "варіант 3"
               - "option 2"

            3. GENERAL_CHAT - Greetings, questions about bot, casual conversation
               Examples:
               - "hi"
               - "how are you"
               - "що ти вмієш?"
               - "дякую"

            4. UNKNOWN - Cannot determine intent

            Default behavior: If user writes artist name, album name, or music-related keywords -> SEARCH_FOR_RELEASE
            """)
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
            Parse user input to extract download option number.
            Support various formats:
            - Just a digit: "1", "2", "3" (most common)
            - Ukrainian words: "перший", "другий", "третій", "четвертий", "п'ятий"
            - English words: "first", "second", "third", "fourth", "fifth"
            - With prefix: "option 2", "варіант 3", "номер 4"

            Examples:
            "1" -> 1
            "2" -> 2
            "перший" -> 1
            "другий" -> 2
            "second" -> 2
            "варіант 3" -> 3

            Return the number as Integer (1-indexed).
            Return null if no valid number found.
            """)
    @UserMessage("{{it}}")
    Integer parseDownloadOptionNumber(String userInput);

    @SystemMessage("""
        You are an Advanced Search Query Builder for MusicBrainz API (Lucene syntax).
        Convert the user's natural language request into a structured SearchRequest.

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
        2. Combine conditions with AND in luceneQuery field.
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

        OUTPUT STRUCTURE:
        - id: null (will be generated later)
        - artist: extracted artist name (WITHOUT quotes, empty string if not found)
        - album: extracted release/album title (WITHOUT quotes, empty string if not found)
        - recording: extracted recording/track title (WITHOUT quotes, empty string if not found)
        - language: detected language of the user query (UA for Ukrainian, EN for English)
        - luceneQuery: complete Lucene query string with all conditions

        LANGUAGE DETECTION:
        - If query contains Cyrillic Ukrainian characters (і, ї, є) or Ukrainian words -> UA
        - If query is in English -> EN
        - Default to EN if uncertain

        EXAMPLES:
        User: "albums by Daft Punk 2013"
        Output: {
          "id": null,
          "artist": "Daft Punk",
          "album": "",
          "recording": "",
          "language": "EN",
          "luceneQuery": "artist:\\"Daft Punk\\" AND date:2013 AND primarytype:Album"
        }

        User: "Онука 2014"
        Output: {
          "id": null,
          "artist": "Онука",
          "album": "",
          "recording": "",
          "language": "UA",
          "luceneQuery": "artist:\\"Онука\\" AND date:2014 AND (primarytype:Album OR primarytype:EP)"
        }

        User: "Паліндром альбом Хвороба"
        Output: {
          "id": null,
          "artist": "Паліндром",
          "album": "Хвороба",
          "recording": "",
          "language": "UA",
          "luceneQuery": "artist:\\"Паліндром\\" AND release:\\"Хвороба\\" AND primarytype:Album"
        }
        """)
    @UserMessage("{{it}}")
    SearchRequest buildSearchRequest(String userPrompt);
}
