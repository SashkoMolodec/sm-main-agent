package com.sashkomusic.mainagent.ai.service;

import com.sashkomusic.mainagent.domain.model.MetadataSearchRequest;
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

            2. SEARCH_FOR_RELEASE_DISCOGS - User explicitly wants to search in Discogs OR wants to dig deeper/search more
               Examples:
               - "Паліндром discogs"
               - "найди альбом Хвороба в discogs"
               - "шукай nthng discogs"
               - "discogs" (just the word)
               - "копай глибше" (dig deeper)
               - "шукай ще" (search more)
               - "пошукай в іншому місці"

            3. SEARCH_FOR_RELEASE_BANDCAMP - User explicitly wants to search in Bandcamp
               Examples:
               - "Паліндром bandcamp"
               - "найди альбом Хвороба в bandcamp"
               - "шукай nthng bandcamp"
               - "bandcamp" (just the word)
               - Any query with "bandcamp" keyword

            4. DIG_DEEPER - User wants to search in alternative sources (dig deeper into search results)
               Examples:
               - "копай" / "копай глибше"
               - "ше" / "ще"
               - "блять" (expressing frustration, wants more results)
               - "пошукай ще"
               - "шукай в іншому місці"
               - Any short frustrated phrase suggesting "try again elsewhere"

            5. CHOOSE_DOWNLOAD_OPTION - User is selecting a download option (typically a single digit 1-5)
               Examples:
               - "1" (single digit is MOST LIKELY download choice)
               - "2"
               - "3"
               - "перший"
               - "second"
               - "варіант 3"
               - "option 2"

            6. DIRECT_DOWNLOAD_REQUEST - User explicitly requests to download music with "скачай" or "завантаж" prefix
               Examples:
               - "скачай кому вниз - in kastus"
               - "завантаж Aphex Twin ambient tracks"
               - "скачай Паліндром Машина для трансляції снів"
               - "download Daft Punk"
               - Any query starting with download command words ("скачай", "завантаж", "download")
               IMPORTANT: This is HIGHEST PRIORITY - if query starts with download words, it's ALWAYS DIRECT_DOWNLOAD_REQUEST

            7. GENERAL_CHAT - Greetings, questions about bot, casual conversation
               Examples:
               - "hi"
               - "how are you"
               - "що ти вмієш?"
               - "дякую"

            8. UNKNOWN - Cannot determine intent

            Default behavior:
            - If user writes artist name, album name, or music-related keywords -> SEARCH_FOR_RELEASE_DEFAULT
            - If query contains "discogs" keyword -> SEARCH_FOR_RELEASE_DISCOGS
            - If query contains "bandcamp" keyword -> SEARCH_FOR_RELEASE_BANDCAMP
            - If query contains dig deeper phrases ("копай", "ще", "блять") -> DIG_DEEPER
            """)
    UserIntent classifyIntent(@UserMessage String text);

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
            Parse music folder name to extract artist, album, and ALL additional metadata filters.

            This handles folder names like:
            - "Artist - Album"
            - "Artist - Album (Year)"
            - "Artist - Album (kaseta, 1990)"
            - "Artist - Album (vinyl, 1985-1990, україна)"
            - "[Label] Artist - Album (cd, 2000)"

            EXTRACTION RULES:
            1. Extract artist and album from main folder name (before parentheses)
            2. Extract ALL filters from text in parentheses (format, year, country, type, label, etc.)
            3. Common folder patterns:
               - "Artist - Album (filters)"
               - "Artist - Year - Album (filters)"
               - "[Label] Artist - Album (filters)"
               - "Year Artist - Album (filters)"

            AVAILABLE FILTER FIELDS (from parentheses):
            - dateRange: Year or year range
              - Single year: "1990" -> {from: 1990, to: 1990}
              - Range: "1985-1990" -> {from: 1985, to: 1990}
              - "90s" -> {from: 1990, to: 1999}
            - format: Vinyl | CD | Cassette | Digital Media | File
              - Recognize: vinyl, вініл, платівка, грамплатівка
              - Recognize: cd, диск, компакт-диск
              - Recognize: cassette, kaseta, касета, tape, плівка
            - type: Album | EP | Single | Compilation
            - country: ISO 2-letter country code (UA, US, GB, DE, FR, JP, etc)
              - ukraine, україна -> UA
              - usa, америка -> US
              - uk, britain, англія -> GB
            - status: Official | Bootleg | Promotion
            - style: Genre/style (techno, ambient, rock, etc)
            - label: Record label name (if in parentheses)
            - catno: Catalog number

            CRITICAL RULES:
            1. **Artist and album are REQUIRED** - extract from folder name before parentheses
            2. Keep exact spelling for artist/album - do NOT translate or transliterate
            3. Filters in parentheses are OPTIONAL - extract what's present
            4. If year appears BOTH in folder name AND parentheses, use the one from parentheses
            5. Ignore label in square brackets [Label]
            6. Multiple filters in parentheses are comma-separated
            7. Return empty strings for missing fields (null for dateRange)

            OUTPUT STRUCTURE (MetadataSearchRequest format):
            Return ONLY valid JSON without any markdown formatting or code blocks.
            DO NOT wrap JSON in ```json or ``` blocks.
            {
              "id": null,
              "artist": "extracted artist name",
              "release": "extracted album/release name",
              "recording": "",
              "dateRange": {from: year, to: year} or null,
              "format": "Vinyl | CD | etc (empty if not found)",
              "type": "Album | EP | etc (empty if not found)",
              "country": "US | GB | etc (empty if not found)",
              "status": "Official | etc (empty if not found)",
              "style": "techno | ambient | etc (empty if not found)",
              "label": "label name (empty if not found)",
              "catno": "catalog number (empty if not found)"
            }

            EXAMPLES:
            Input: "Aphex Twin - Selected Ambient Works 85-92 (1992)"
            Output: {
              "artist": "Aphex Twin",
              "release": "Selected Ambient Works 85-92",
              "recording": "",
              "dateRange": {"from": 1992, "to": 1992},
              "format": "", "type": "", "country": "", "status": "", "style": "", "label": "", "catno": ""
            }

            Input: "Кому Вниз - Мекка (касета, 1990, україна)"
            Output: {
              "artist": "Кому Вниз",
              "release": "Мекка",
              "recording": "",
              "dateRange": {"from": 1990, "to": 1990},
              "format": "Cassette",
              "type": "",
              "country": "UA",
              "status": "", "style": "", "label": "", "catno": ""
            }

            Input: "Kraftwerk - Autobahn (vinyl, 1974, germany)"
            Output: {
              "artist": "Kraftwerk",
              "release": "Autobahn",
              "recording": "",
              "dateRange": {"from": 1974, "to": 1974},
              "format": "Vinyl",
              "type": "",
              "country": "DE",
              "status": "", "style": "", "label": "", "catno": ""
            }

            Input: "[Warp Records] Aphex Twin - Drukqs (cd, 2001)"
            Output: {
              "artist": "Aphex Twin",
              "release": "Drukqs",
              "recording": "",
              "dateRange": {"from": 2001, "to": 2001},
              "format": "CD",
              "type": "",
              "country": "",
              "status": "", "style": "", "label": "", "catno": ""
            }

            Input: "Burial - Untrue"
            Output: {
              "artist": "Burial",
              "release": "Untrue",
              "recording": "",
              "dateRange": null,
              "format": "", "type": "", "country": "", "status": "", "style": "", "label": "", "catno": ""
            }
            """)
    @UserMessage("{{it}}")
    MetadataSearchRequest parseFolderName(String folderName);

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
        5. IGNORE and remove download command words ("скачай", "завантаж", "download", "dl") if present at start
        6. For dateRange: parse into {from, to} object
        7. For style: you CAN translate genre names (e.g., "дарк ембієнт" -> "dark ambient")
        8. If no type specified, leave empty (don't assume Album)
        9. **QUOTED STRINGS: Text in quotes "..." is a SINGLE LITERAL ENTITY**
           - DO NOT parse/split quoted text into separate fields
           - If contains label indicators (Records, Tapes, Recordings, Music, Label) → label field
           - Otherwise → treat as artist or release depending on context
           - Examples:
             → "bloomed in september tapes" → label="bloomed in september tapes"
             → "axis records" → label="axis records"
             → "unknown artist" → artist="unknown artist"

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
        Return ONLY valid JSON without any markdown formatting or code blocks.
        DO NOT wrap JSON in ```json or ``` blocks.
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
          "language": "UA or EN"
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
          "language": "EN"
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
          "language": "UA"
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
          "language": "UA"
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
          "language": "EN"
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

        User: "bloomed in september tapes"
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
          "label": "bloomed in september tapes",
          "catno": "",
          "language": "EN",
          "youtubeUrl": "",
          "discogsUrl": "",
          "bandcampUrl": ""
        }
        """)
    @UserMessage("{{it}}")
    MetadataSearchRequest buildSearchRequest(String userPrompt);
}
