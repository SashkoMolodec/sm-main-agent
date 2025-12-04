package com.sashkomusic.mainagent.domain.util;

import com.sashkomusic.mainagent.domain.model.ReleaseMetadata;

public class ReleaseCardFormatter {

    private ReleaseCardFormatter() {
        // Utility class
    }

    /**
     * Formats release metadata into Telegram card text
     *
     * @param release Release metadata
     * @return Formatted card text (lowercase)
     */
    public static String formatCardText(ReleaseMetadata release) {
        String metadataLine = buildMetadataLine(release);

        return """
                💿 %s
                👤 %s
                %s
                """.formatted(
                release.title(),
                release.artist(),
                metadataLine
        ).toLowerCase();
    }

    private static String buildMetadataLine(ReleaseMetadata release) {
        String line = "%s • %s".formatted(release.getYearsDisplay(), release.getTypesDisplay());

        // Add label if available
        if (!release.getLabelDisplay().isEmpty()) {
            line += " • " + release.getLabelDisplay();
        }

        // Add track count if available
        if (!release.getTrackCountDisplay().isEmpty()) {
            line += " • " + release.getTrackCountDisplay() + " тр.";
        }

        // Add tags if available
        if (!release.getTagsDisplay().isEmpty()) {
            line += " • " + release.getTagsDisplay();
        }

        return line;
    }
}
