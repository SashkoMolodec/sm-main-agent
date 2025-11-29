package com.sashkomusic.mainagent.ai.service;

import com.sashkomusic.mainagent.domain.model.MetadataSearchRequest;
import com.sashkomusic.mainagent.domain.model.MusicSearchQuery;
import com.sashkomusic.mainagent.domain.model.UserIntent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface AiService {

    @SystemMessage("""
            Analyze the user text and determine the intent. Return exactly one enum value.

            Intents:
            1. SEARCH_FOR_RELEASE_DEFAULT - User wants to search for music using default source (MusicBrainz)
               Examples:
               - "Daft Punk" (just artist name)
               - "Онука" (just artist name in Ukrainian/other language)
               - "Aphex Twin ambient tracks"
               - "German techno 90s"
               - "найди альбом Хвороба"
               - Any unique artist/album name WITHOUT explicit download request and WITHOUT "discogs" keyword

            2. SEARCH_FOR_RELEASE_DISCOGS - User explicitly wants to search in Discogs (contains "discogs" keyword)
               Examples:
               - "Паліндром discogs"
               - "найди альбом Хвороба в discogs"
               - "шукай nthng discogs"

            3. CHOOSE_DOWNLOAD_OPTION - User is selecting a download option (typically a single digit 1-5)
               Examples:
               - "1" (single digit is MOST LIKELY download choice)
               - "2"
               - "3"
               - "перший"
               - "second"
               - "варіант 3"
               - "option 2"

            4. GENERAL_CHAT - Greetings, questions about bot, casual conversation
               Examples:
               - "hi"
               - "how are you"
               - "що ти вмієш?"
               - "дякую"

            5. UNKNOWN - Cannot determine intent

            Default behavior: If user writes artist name, album name, or music-related keywords -> SEARCH_FOR_RELEASE_DEFAULT
            If query contains "discogs" keyword -> SEARCH_FOR_RELEASE_DISCOGS
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
        You are a Universal Metadata Search Query Extractor.
        Extract ALL search parameters from the user's query into a structured MetadataSearchRequest.
        This request will be used by different metadata services (MusicBrainz, Discogs, etc).

        AVAILABLE FIELDS:
        - artist: Artist name
        - release: Album/release name
        - recording: Track/song name
        - dateRange: Year or year range
          - Single year: "2013" -> {from: 2013, to: 2013}
          - Range: "90s" -> {from: 1990, to: 1999}
          - "early 2000s" -> {from: 2000, to: 2004}
        - format: Vinyl | CD | Cassette | Digital Media | File
        - type: Album | EP | Single | Compilation
        - country: ISO 2-letter country code (US, GB, DE, FR, JP, UA, etc)
        - status: Official | Bootleg | Promotion
        - style: Genre/style/tag (techno, ambient, rock, idm, etc)
        - label: Record label name
        - catno: Catalog number (e.g. "AX-009")
        - language: UA or EN (detected from user query)
        - youtubeUrl: empty (generated later)
        - discogsUrl: empty (generated later)
        - bandcampUrl: empty (generated later)

        CRITICAL RULES:
        1. **NEVER translate, transliterate, or change artist/release/recording names in ANY way**
           - Keep EXACT original spelling byte-for-byte
           - "Паліндром" MUST stay "Паліндром" (NOT "Палиндром")
           - "Онука" MUST stay "Онука"
           - DO NOT change Ukrainian "і" to Russian "и"
        2. If field not mentioned, use empty string (or null for dateRange)
        3. IGNORE words like "find", "search", "latest", "best"
        4. Remove "discogs" keyword if present
        5. For dateRange: parse into {from, to} object
        6. For style: you CAN translate genre names (e.g., "дарк ембієнт" -> "dark ambient")
        7. If no type specified, leave empty (don't assume Album)

        TRACK vs RELEASE DETECTION:
        - Pattern "Artist - Title" WITHOUT context indicators:
          → Fill BOTH: release="Title" AND recording="Title"
          → This enables searching both by release name and track name
        - Has release indicators (album, LP, EP, compilation, vinyl, CD):
          → Fill ONLY release field
        - Has track indicators (track, song, single):
          → Fill ONLY recording field
        - Examples:
          → "Aphex Twin - Windowlicker" → release="Windowlicker", recording="Windowlicker"
          → "Burial - Untrue album" → release="Untrue", recording=""
          → "Radiohead - Creep track" → release="", recording="Creep"

        LANGUAGE DETECTION:
        - If query contains Ukrainian Cyrillic (і, ї, є) or Ukrainian words -> UA
        - Otherwise -> EN

        OUTPUT STRUCTURE:
        {
          "id": null,
          "artist": "extracted artist name (empty if not found)",
          "release": "extracted release/album (empty if not found)",
          "recording": "extracted track name (empty if not found)",
          "dateRange": {from: year, to: year} or null,
          "format": "Vinyl | CD | etc (empty if not found)",
          "type": "Album | EP | etc (empty if not found)",
          "country": "US | GB | etc (empty if not found)",
          "status": "Official | etc (empty if not found)",
          "style": "techno | ambient | etc (empty if not found)",
          "label": "label name (empty if not found)",
          "catno": "catalog number (empty if not found)",
          "language": "UA or EN",
          "youtubeUrl": "",
          "discogsUrl": "",
          "bandcampUrl": ""
        }

        EXAMPLES:
        User: "Daft Punk 2013"
        Output: {
          "id": null,
          "artist": "Daft Punk",
          "release": "",
          "recording": "",
          "dateRange": {"from": 2013, "to": 2013},
          "format": "",
          "type": "",
          "country": "",
          "status": "",
          "style": "",
          "label": "",
          "catno": "",
          "language": "EN",
          "youtubeUrl": "",
          "discogsUrl": "",
          "bandcampUrl": ""
        }

        User: "Онука 2014"
        Output: {
          "id": null,
          "artist": "Онука",
          "release": "",
          "recording": "",
          "dateRange": {"from": 2014, "to": 2014},
          "format": "",
          "type": "",
          "country": "",
          "status": "",
          "style": "",
          "label": "",
          "catno": "",
          "language": "UA",
          "youtubeUrl": "",
          "discogsUrl": "",
          "bandcampUrl": ""
        }

        User: "Паліндром альбом Хвороба discogs"
        Output: {
          "id": null,
          "artist": "Паліндром",
          "release": "Хвороба",
          "recording": "",
          "dateRange": null,
          "format": "",
          "type": "Album",
          "country": "",
          "status": "",
          "style": "",
          "label": "",
          "catno": "",
          "language": "UA",
          "youtubeUrl": "",
          "discogsUrl": "",
          "bandcampUrl": ""
        }

        User: "Jeff Mills 1996 vinyl"
        Output: {
          "id": null,
          "artist": "Jeff Mills",
          "release": "",
          "recording": "",
          "dateRange": {"from": 1996, "to": 1996},
          "format": "Vinyl",
          "type": "",
          "country": "",
          "status": "",
          "style": "",
          "label": "",
          "catno": "",
          "language": "EN",
          "youtubeUrl": "",
          "discogsUrl": "",
          "bandcampUrl": ""
        }

        User: "German techno 90s"
        Output: {
          "id": null,
          "artist": "",
          "release": "",
          "recording": "",
          "dateRange": {"from": 1990, "to": 1999},
          "format": "",
          "type": "",
          "country": "DE",
          "status": "",
          "style": "techno",
          "label": "",
          "catno": "",
          "language": "EN",
          "youtubeUrl": "",
          "discogsUrl": "",
          "bandcampUrl": ""
        }

        User: "Axis Records AX-009"
        Output: {
          "id": null,
          "artist": "",
          "release": "",
          "recording": "",
          "dateRange": null,
          "format": "",
          "type": "",
          "country": "",
          "status": "",
          "style": "",
          "label": "Axis Records",
          "catno": "AX-009",
          "language": "EN",
          "youtubeUrl": "",
          "discogsUrl": "",
          "bandcampUrl": ""
        }
        """)
    @UserMessage("{{it}}")
    MetadataSearchRequest buildSearchRequest(String userPrompt);
}
