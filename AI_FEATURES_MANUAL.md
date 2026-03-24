# MekHQ AI Features Manual: The AI Dungeon Master

Welcome to the future of mercenary management. This manual describes the cutting-edge AI features implemented in the `ai-support` branch, designed to turn your MekHQ experience into a living, breathing narrative saga.

## 1. AI Campaign Storyteller (The Foundation)
**Location:** Startup Screen (New Campaign)

When starting a new campaign, the AI Storyteller acts as your initial historian. By providing a few prompts or letting the AI decide, it generates a deep background story for your mercenary unit. This lore is not just flavor text—it is stored in your campaign's "DNA" and serves as the foundation for all future AI interactions.

- **Lore Coherency:** All future missions and reports will respect the origin, tone, and character of the story generated here.
- **Persistent Context:** The backstory is saved to your campaign options, ensuring that even if you take a break, the AI "Dungeon Master" remembers who you are.

## 2. AI After-Action Debriefs (The Immersive Report)
**Location:** Briefing Room (After Scenario Resolution)

Gone are the days of dry salvage lists and cold casualty numbers. The AI AAR (After-Action Report) button takes the raw data from your latest battle and translates it into a 2-3 paragraph commander's report.

- **Commander's Perspective:** Reports are written from an in-universe perspective, highlighting key tactical moments, heroic sacrifices, and the overall strategic impact of the engagement.
- **Auto-Archiving:** The report is automatically prepended to the mission's official report field, creating a permanent narrative log of your unit's combat history.

## 3. AI Story Arc Generator (The Dungeon Master)
**Location:** Briefing Room (Mission Management)

The crown jewel of our AI integration. This feature allows you to generate a linked, 3-mission "Mini-Campaign" with a single click.

- **Narrative Chaining:** The AI proposes three missions that follow a logical progression (e.g., *Infiltration -> Sabotage -> Extraction*).
- **Intelligent Contextualization:** The generator analyzes your current location, the current year, your faction standing, and your unit's overall strength to propose missions that are both lore-friendly and mechanically appropriate.
- **Lore-Driven Subplots:** While the system defaults to your main "Storyteller" backstory, you can provide optional suggestions to nudge the AI toward specific subplots (e.g., *"Focus on our rivalry with the local pirate lord"*).
- **Trash & Re-generate:** Don't like the draft? Click the button again. The system intelligently detects unstarted AI missions and offers to "trash" them, allowing you to cycle through new drafts until you find the perfect story.

## 4. Technical Robustness & Standards
- **Non-Invasive Implementation:** All AI features are isolated within dedicated service packages and UI hooks, ensuring zero interference with core MekHQ combat or campaign logic.
- **Flexible Deserialization:** Our custom AI parser can handle natural language from LLMs (like "Medium" difficulty or "3 months" length), converting them into precise game data automatically.
- **Clean Architecture:** We strictly adhere to MekHQ developer standards, avoiding deprecated features and maintaining a clean build environment.

---
*Created with pride for the MekHQ Community.*
