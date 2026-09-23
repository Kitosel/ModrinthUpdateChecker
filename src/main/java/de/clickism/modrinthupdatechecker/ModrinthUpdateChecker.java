/*
 * MIT License
 *
 * Copyright (c) 2025 Clickism
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package de.clickism.modrinthupdatechecker;

import com.google.gson.*;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Utility class to check for newer versions of a project hosted on Modrinth.
 * Backported to Java 8.
 */
public class ModrinthUpdateChecker {

    private static final String API_URL = "https://api.modrinth.com/v2/project/{id}/version";

    private final String projectId;
    private final String loader;
    @Nullable
    private final String minecraftVersion;

    /**
     * Create a new update checker for the given project.
     * This will check the latest version for the given loader and any minecraft version.
     *
     * @param projectId the project ID
     * @param loader    the loader
     */
    public ModrinthUpdateChecker(String projectId, String loader) {
        this(projectId, loader, null);
    }

    /**
     * Create a new update checker for the given project.
     * This will check the latest version for the given loader and minecraft version.
     *
     * @param projectId        the project ID
     * @param loader           the loader
     * @param minecraftVersion the minecraft version, or null for any version
     */
    public ModrinthUpdateChecker(String projectId, String loader, @Nullable String minecraftVersion) {
        this.projectId = projectId;
        this.loader = loader;
        this.minecraftVersion = minecraftVersion;
    }

    /**
     * Check the latest version of the project for the given loader and minecraft version
     * and call the consumer with it.
     *
     * @param consumer the consumer
     */
    public void checkVersion(Consumer<String> consumer) {
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL(API_URL.replace("{id}", projectId));
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "ModrinthUpdateChecker-Java8");

                int statusCode = connection.getResponseCode();
                if (statusCode != 200) {
                    connection.disconnect();
                    return;
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder responseBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBuilder.append(line);
                    }

                    JsonArray versionsArray = JsonParser.parseString(responseBuilder.toString()).getAsJsonArray();
                    String latestVersion = getLatestVersion(versionsArray);
                    if (latestVersion == null) return;
                    consumer.accept(latestVersion);
                } finally {
                    connection.disconnect();
                }
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Get the latest compatible version from the versions array.
     *
     * @param versions the versions array
     * @return the latest compatible version
     */
    @Nullable
    protected String getLatestVersion(JsonArray versions) {
        String maxVersion = null;

        for (JsonElement element : versions) {
            if (!element.isJsonObject()) continue;
            JsonObject version = element.getAsJsonObject();

            if (isVersionCompatible(version)) {
                String versionNumber = version.get("version_number").getAsString();
                String rawVersion = getRawVersion(versionNumber);

                if (maxVersion == null || rawVersion.compareTo(maxVersion) > 0) {
                    maxVersion = rawVersion;
                }
            }
        }

        return maxVersion;
    }

    /**
     * Check if the version is compatible for the given loader and minecraft version.
     *
     * @param version the version
     * @return true if the version is valid
     */
    protected boolean isVersionCompatible(JsonObject version) {
        JsonArray versions = version.get("game_versions").getAsJsonArray();
        JsonArray loaders = version.get("loaders").getAsJsonArray();
        return (minecraftVersion == null || versions.contains(new JsonPrimitive(minecraftVersion)))
                && loaders.contains(new JsonPrimitive(loader));
    }

    /**
     * Gets the raw version from a version string.
     * i.E: "fabric-1.2+1.17.1" -> "1.2"
     *
     * @param version the version string
     * @return the raw version string
     */
    public static String getRawVersion(String version) {
        if (version.isEmpty()) return version;
        version = version.replaceAll("^\\D+", "");
        String[] split = version.split("\\+");
        return split[0];
    }
}
