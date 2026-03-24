package mekhq.service.ai;

import javax.swing.JOptionPane;
import mekhq.MekHQ;
import mekhq.campaign.Campaign;
import mekhq.campaign.mission.AtBContract;
import mekhq.campaign.mission.AtBDynamicScenario;
import mekhq.campaign.mission.enums.AtBContractType;
import mekhq.campaign.mission.enums.ScenarioStatus;
import mekhq.campaign.universe.Planet;
import mekhq.campaign.universe.PlanetarySystem;
import mekhq.campaign.universe.Systems;
import megamek.logging.MMLogger;

public class AIHelper {
    private static final MMLogger LOGGER = MMLogger.create(AIHelper.class);

    public static void addMissionFromProposal(Campaign campaign, MissionProposal proposal) {
        if (proposal == null) {
            LOGGER.error("AIHelper: Received null MissionProposal");
            return;
        }
        
        LOGGER.info("AIHelper: Starting to add mission: " + proposal.title);
        LOGGER.info("AIHelper: Mission Details - Type: " + proposal.missionType + 
                    ", Employer: " + proposal.employerCode + 
                    ", Enemy: " + proposal.enemyCode + 
                    ", Planet: " + proposal.planetName);

        try {
            // 1. Title and Description
            String title = (proposal.title != null && !proposal.title.isBlank()) ? proposal.title : "AI Generated Mission";
            AtBContract contract = new AtBContract(title);
            String aiDescription = (proposal.briefing != null ? proposal.briefing : "No briefing provided.") + "\n\n<!-- AI_ARC_MISSION -->";
            contract.setDesc(aiDescription);
            
            // 2. Mission Type Mapping (be flexible with AI output)
            AtBContractType type = AtBContractType.GARRISON_DUTY;
            if (proposal.missionType != null) {
                String normalizedType = proposal.missionType.toUpperCase().replace(" ", "_");
                try {
                    type = AtBContractType.valueOf(normalizedType);
                } catch (Exception e) {
                    LOGGER.warn("AIHelper: Unknown mission type '" + proposal.missionType + "', falling back to GARRISON_DUTY");
                    // Try to find a match by contains
                    for (AtBContractType t : AtBContractType.values()) {
                        if (normalizedType.contains(t.name())) {
                            type = t;
                            break;
                        }
                    }
                }
            }
            contract.setContractType(type);
            
            // 3. Faction Mapping
            String employer = (proposal.employerCode != null) ? proposal.employerCode : "MERC";
            String enemy = (proposal.enemyCode != null) ? proposal.enemyCode : "PIR";
            contract.setEmployerCode(employer, campaign.getGameYear());
            contract.setEnemyCode(enemy);
            
            // 4. Difficulty and Length
            contract.setDifficulty(proposal.difficulty > 0 ? proposal.difficulty : 5);
            contract.setLength(proposal.lengthWeeks > 0 ? proposal.lengthWeeks : 12);
            
            // 5. Planet and System (with robust null handling)
            Planet targetPlanet = null;
            if (proposal.planetName != null && !proposal.planetName.isBlank()) {
                PlanetarySystem sys = Systems.getInstance().getSystemByName(proposal.planetName, campaign.getLocalDate());
                if (sys != null) {
                    targetPlanet = sys.getPrimaryPlanet();
                }
            }
            
            if (targetPlanet == null && campaign.getCurrentSystem() != null) {
                targetPlanet = campaign.getCurrentSystem().getPrimaryPlanet();
            }
            
            if (targetPlanet != null && targetPlanet.getParentSystem() != null) {
                contract.setSystemId(targetPlanet.getParentSystem().getId());
            } else {
                LOGGER.warn("AIHelper: Could not determine target system, using fallback");
                contract.setSystemId("Unknown System");
            }
            
            // 6. Initialization
            contract.initContractDetails(campaign);
            contract.setDesc(aiDescription); 
            contract.setPartsAvailabilityLevel(contract.getContractType().calculatePartsAvailabilityLevel());
            contract.setAtBSharesPercent(0); 
            contract.setStartDate(null);
            contract.calculateContract(campaign);
            
            // 7. Scenario Creation
            AtBDynamicScenario scenario = new AtBDynamicScenario();
            scenario.setName("Opening Engagement: " + title);
            scenario.setDesc(proposal.briefing != null ? proposal.briefing : "");
            scenario.setDate(campaign.getLocalDate());
            scenario.setStatus(ScenarioStatus.CURRENT);
            contract.addScenario(scenario);
            
            // 8. Add to Campaign
            // Note: addMission internally triggers MissionNewEvent
            campaign.addMission(contract);
            
            LOGGER.info("AIHelper: Successfully added mission: " + title);
            
        } catch (Exception e) {
            String errorMsg = (e.getMessage() != null) ? e.getMessage() : e.getClass().getSimpleName();
            LOGGER.error("AIHelper: Failed to add AI mission", e);
            JOptionPane.showMessageDialog(null, "Failed to add mission: " + errorMsg, "Creation Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
