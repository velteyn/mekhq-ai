/*
 * Copyright (C) 2026 The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MekHQ.
 *
 * MekHQ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPL),
 * version 3 or (at your option) any later version,
 * as published by the Free Software Foundation.
 */
package mekhq.service.ai;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import megamek.logging.MMLogger;
import mekhq.MekHQ;

/**
 * Service to interface with a local LLM (e.g., LM Studio) to act as a BattleTech Dungeon Master.
 */
public class AIService {
    private static final MMLogger LOGGER = MMLogger.create(AIService.class);
    private static final String DEFAULT_URL = "http://127.0.0.1:1234/v1/chat/completions";
    private static final String MODEL = "local-model"; // LM Studio usually ignores this or uses the loaded one
    private static final int MAX_RETRIES = 2;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = 
        "You are the 'Dungeon Master' for a BattleTech mercenary campaign manager called MekHQ. " +
        "Your role is to create immersive, lore-accurate BattleTech campaigns and missions. " +
        "You have deep knowledge of BattleTech history, factions (Great Houses, Clans, Periphery), and military operations.\n\n" +
        "Factions: Federated Suns (FS), Lyran Commonwealth (LC), Draconis Combine (DC), Capellan Confederation (CC), " +
        "Free Worlds League (FWL), ComStar (CS), Word of Blake (WOB), various Clans (CJF, CGB, CCY, etc.), and Pirates.\n\n" +
        "Planets: You MUST use real, canon BattleTech planets (e.g. Solaris VII, Outreach, Galatea, Tortuga Prime, Astrokaszy). Do not invent new planets.\n\n" +
        "Mission Types: GARRISON_DUTY, CADRE_DUTY, SECURITY_DUTY, RIOT_DUTY, PLANETARY_ASSAULT, RELIEF_DUTY, " +
        "GUERRILLA_WARFARE, PIRATE_HUNTING, DIVERSIONARY_RAID, OBJECTIVE_RAID, RECON_RAID, EXTRACTION_RAID, " +
        "ASSASSINATION, ESPIONAGE, MOLE_HUNTING, OBSERVATION_RAID, RETAINER, SABOTAGE, TERRORISM.\n\n" +
        "OUTPUT INSTRUCTIONS:\n" +
        "1. Respond ONLY with a single valid JSON object.\n" +
        "2. DO NOT include any comments (// or /*) inside the JSON.\n" +
        "3. DO NOT include any 'Thinking Process', reasoning, preamble, or post-script text.\n" +
        "4. DO NOT use markdown code blocks (no ```json).\n" +
        "5. If you fail to follow these instructions, the system will crash.";

    public AIService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_1_1) // Use HTTP/1.1 for local servers
            .proxy(ProxySelector.of(null)) // Disable system proxy for local AI service
            .build();
        this.objectMapper = JsonMapper.builder()
            .enable(JsonParser.Feature.ALLOW_COMMENTS)
            .enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES)
            .enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES)
            .enable(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
            .enable(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();
    }

    public CompletableFuture<MissionProposal> generateMission(String userPrompt, String context) {
        String fullPrompt = "Context: " + context + "\n\nUser Request: " + userPrompt + 
            "\n\nGenerate a mission proposal in JSON format with the following fields: " +
            "title, briefing (a detailed narrative explaining WHY we are fighting, WHO hired us, WHAT the specific objective is, and ANY notable lore/historical context based on the current year and location), " +
            "missionType (one of the enums), employerCode, enemyCode, planetName (canon planet name), difficulty (1-10), lengthWeeks.";

        return callLLM(fullPrompt, MissionProposal.class);
    }

    public CompletableFuture<String> generateAfterActionReport(String context, String scenarioResult) {
        String fullPrompt = "Context: " + context + "\n\nScenario Results: " + scenarioResult + 
            "\n\nGenerate an After-Action Report (AAR) written from the perspective of the mercenary commander or the surviving lance leader. " +
            "It should be a highly narrative, immersive 2-3 paragraph summary of the battle, mentioning specific casualties, salvage, and the overall outcome. " +
            "OUTPUT INSTRUCTIONS:\n" +
            "1. Respond ONLY with a single valid JSON object.\n" +
            "2. DO NOT include any comments (// or /*) inside the JSON.\n" +
            "3. DO NOT include any 'Thinking Process', reasoning, preamble, or post-script text.\n" +
            "4. The JSON must contain exactly one field: 'reportText' (a string).";

        return callLLMWithRetry(fullPrompt, AARResponse.class, 0).thenApply(aar -> aar.reportText);
    }

    public static class AARResponse {
        @JsonProperty("reportText") public String reportText;
    }

    public static class StoryArcProposal {
        @JsonProperty("title") public String title;
        @JsonProperty("description") public String description;
        @JsonProperty("missions") public List<MissionProposal> missions;
    }

    public CompletableFuture<StoryArcProposal> generateStoryArc(String userPrompt, String context, String campaignBackstory, List<String> previousMissions) {
        StringBuilder fullPrompt = new StringBuilder();
        fullPrompt.append("Campaign Background Story: ").append(campaignBackstory).append("\n\n");
        fullPrompt.append("Current Campaign Context: ").append(context).append("\n\n");
        
        if (previousMissions != null && !previousMissions.isEmpty()) {
            fullPrompt.append("Previous Missions in this Story Arc:\n");
            for (int i = 0; i < previousMissions.size(); i++) {
                fullPrompt.append(i + 1).append(". ").append(previousMissions.get(i)).append("\n");
            }
            fullPrompt.append("\n");
        }

        if (userPrompt != null && !userPrompt.trim().isEmpty()) {
            fullPrompt.append("User's Specific Suggestion/Focus: ").append(userPrompt).append("\n\n");
        }
        
        fullPrompt.append("Generate a coherent 3-mission story arc in JSON format that is STYLISTICALLY AND NARRATIVELY COHERENT with the Campaign Background Story and any Previous Missions provided above. ")
            .append("The arc should represent the NEXT LOGICAL STEP in the unit's journey, building upon the events already established. ")
            .append("The story should have a clear beginning, middle, and end. ")
            .append("Each mission must be linked to the previous one narratively. ")
            .append("Respond ONLY with a valid JSON object containing: ")
            .append("title (the name of this campaign arc), ")
            .append("description (a summary of the overarching plot), ")
            .append("missions (a list of exactly 3 mission objects, each with: title, briefing, missionType, employerCode, enemyCode, planetName, difficulty, lengthWeeks).");

        return callLLMWithRetry(fullPrompt.toString(), StoryArcProposal.class, 0);
    }

    public CompletableFuture<CampaignProposal> generateCampaign(String userPrompt) {
        String fullPrompt = "User Request: " + userPrompt + 
            "\n\nGenerate a new campaign proposal in JSON format with the following fields: " +
            "campaignName (the title of the story), mercenaryUnitName (the actual name of the player's unit), " +
            "startYear (e.g. 3025, 3050, 3145), startingFactionCode (use ONLY valid short codes: FS, LC, DC, CC, FWL, CS, WOB, PIR, MERC). " +
            "CRITICAL: The startYear MUST match the startingFactionCode's active period in lore. " +
            "Example: ComStar (CS) is only active 2781-3081. Word of Blake (WOB) is 3052-3081. " +
            "If the startYear is 3151 (ilClan era), ComStar and Word of Blake are NOT available. " +
            "startingPlanetName, backgroundStory, startingFunds (amount of C-Bills, typically 10000000 to 50000000), " +
            "startingUnits (a list of 4 units, each with modelName (e.g. 'Thunderbolt TDR-5S', 'Shadow Hawk SHD-2H', 'Stinger STG-3G'), " +
            "pilotName, pilotSkills (e.g. 4/5), and backstory), " +
            "and initialContract (an object with employerCode, enemyCode, missionType, difficulty 1-10, lengthMonths (3, 6, 9, or 12)).";

        return callLLMWithRetry(fullPrompt, CampaignProposal.class, 0);
    }

    public static class BiographiesResponse {
        @JsonProperty("biographies") public List<Biography> biographies;
    }

    public static class Biography {
        @JsonProperty("id") public String id;
        @JsonProperty("bio") public String bio;
    }

    public static class NewsResponse {
        @JsonProperty("newsItems") public List<String> newsItems;
    }

    public CompletableFuture<NewsResponse> generateGalacticNews(String context) {
        String prompt = "Context: " + context + "\n\n" +
            "Generate 3 short, immersive 'Galactic News' or 'Rumors' headlines/snippets for the BattleTech universe. " +
            "The news should be era-appropriate (current year) and location-aware. " +
            "One item should ideally reference the player's recent activities (victory, losses, repairs) in a 'rumor at the local bar' or 'mercenary bulletin' style. " +
            "The other two should be general galactic news matching the current year's major lore events or local regional tensions.\n\n" +
            "OUTPUT INSTRUCTIONS:\n" +
            "1. Respond ONLY with a single valid JSON object.\n" +
            "2. DO NOT include any comments.\n" +
            "3. DO NOT include preamble or reasoning.\n" +
            "4. The JSON must contain exactly one field: 'newsItems' (a list of 3 strings).";

        return callLLMWithRetry(prompt, NewsResponse.class, 0);
    }

    public CompletableFuture<BiographiesResponse> generateBiographies(String context, List<mekhq.campaign.personnel.Person> personnel, java.time.LocalDate date) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Context: ").append(context).append("\n\n");
        prompt.append("Generate lore-accurate, short 2-3 sentence biographies for the following mercenary personnel. ");
        prompt.append("Make each backstory distinct, fitting their role, name, and the current year/faction context.\n\n");
        prompt.append("Personnel List:\n");

        for (mekhq.campaign.personnel.Person p : personnel) {
            prompt.append("- ID: ").append(p.getId().toString())
                  .append(" | Name: ").append(p.getFullName())
                  .append(" | Role: ").append(p.getPrimaryRole().toString())
                  .append(" | Origin: ").append(p.getOriginPlanet() != null ? p.getOriginPlanet().getName(date) : "Unknown")
                  .append("\n");
        }

        prompt.append("\nOUTPUT INSTRUCTIONS:\n")
              .append("1. Respond ONLY with a single valid JSON object.\n")
              .append("2. DO NOT include any comments (// or /*) inside the JSON.\n")
              .append("3. DO NOT include any 'Thinking Process', reasoning, preamble, or post-script text.\n")
              .append("4. DO NOT use markdown code blocks (no ```json).\n")
              .append("5. The JSON must contain exactly one field: 'biographies' (a list of objects).\n")
              .append("6. Each object in the list MUST have: 'id' (the exact ID string provided) and 'bio' (the generated backstory).");

        return callLLMWithRetry(prompt.toString(), BiographiesResponse.class, 0);
    }

    private <T> CompletableFuture<T> callLLM(String prompt, Class<T> responseType) {
        return callLLMWithRetry(prompt, responseType, 0);
    }

    private <T> CompletableFuture<T> callLLMWithRetry(String prompt, Class<T> responseType, int retryCount) {
        LOGGER.info("AIService: Preparing LLM call (Attempt " + (retryCount + 1) + ")...");
        LOGGER.info("AIService: Prompt sent to LLM: \n" + prompt);
        
        // Some models (like older Mistral variants) do not support the "system" role.
        // We combine the system prompt and user prompt into a single "user" message.
        String combinedPrompt = SYSTEM_PROMPT + "\n\n" + prompt;
        
        ChatRequest request = new ChatRequest(MODEL, List.of(
            new Message("user", combinedPrompt)
        ));

        try {
            String jsonRequest = objectMapper.writeValueAsString(request);
            String url = MekHQ.getMHQOptions().getAiServiceUrl(DEFAULT_URL);
            
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "MekHQ/AI-Storyteller")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                .timeout(Duration.ofSeconds(60))
                .build();

            return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    if (response.statusCode() != 200) {
                        String error = "LLM API returned status " + response.statusCode() + ": " + response.body();
                        LOGGER.error("AIService: LLM API error: " + error);
                        return CompletableFuture.failedFuture(new RuntimeException(error));
                    }
                    try {
                        String body = response.body();
                        LOGGER.info("AIService: Raw response from LLM: \n" + body);
                        ChatResponse chatResponse = objectMapper.readValue(body, ChatResponse.class);
                        if (chatResponse.choices == null || chatResponse.choices.isEmpty()) {
                            return CompletableFuture.failedFuture(new RuntimeException("No choices returned from LLM"));
                        }
                        Message message = chatResponse.choices.get(0).message;
                        String content = message.content != null ? message.content : "";
                        String reasoning = message.reasoningContent != null ? message.reasoningContent : "";
                        LOGGER.info("AIService: Content extracted from response: \n" + content);
                        
                        String jsonContent = extractJson(content);
                        if (jsonContent == null) {
                            jsonContent = extractJson(reasoning);
                        }

                        if (jsonContent == null) {
                            LOGGER.error("AIService: No valid JSON found in response. Content: " + content);
                            throw new RuntimeException("No valid JSON structure found");
                        }

                        LOGGER.info("AIService: Sanitized JSON for parsing: \n" + jsonContent);
                        T result = objectMapper.readValue(jsonContent.trim(), responseType);
                        LOGGER.info("AIService: Successfully parsed JSON into " + responseType.getSimpleName());
                        return CompletableFuture.completedFuture(result);
                    } catch (Exception e) {
                        LOGGER.error("AIService: Deserialization failed on attempt " + (retryCount + 1), e);
                        return CompletableFuture.failedFuture(e);
                    }
                })
                .handle((result, ex) -> {
                    if (ex != null) {
                        if (retryCount < MAX_RETRIES) {
                            LOGGER.warn("AIService: Attempt " + (retryCount + 1) + " failed. Retrying... Error: " + ex.getMessage());
                            return callLLMWithRetry(prompt, responseType, retryCount + 1);
                        }
                        return CompletableFuture.<T>failedFuture(ex);
                    }
                    return CompletableFuture.completedFuture(result);
                })
                .thenCompose(future -> future);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    String extractJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1);
        }
        return null;
    }

    // region API Models
    private static class ChatRequest {
        @JsonProperty("model") String model;
        @JsonProperty("messages") List<Message> messages;
        @JsonProperty("temperature") double temperature = 0.2; // Lower temperature for more consistent JSON

        ChatRequest() {}
        ChatRequest(String model, List<Message> messages) {
            this.model = model;
            this.messages = messages;
        }
    }

    private static class Message {
        @JsonProperty("role") String role;
        @JsonProperty("content") String content;
        @JsonProperty("reasoning_content") String reasoningContent;

        Message() {}
        Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    private static class ChatResponse {
        @JsonProperty("choices") List<Choice> choices;
        ChatResponse() {}
    }

    private static class Choice {
        @JsonProperty("message") Message message;
        Choice() {}
    }
    // endregion
}
