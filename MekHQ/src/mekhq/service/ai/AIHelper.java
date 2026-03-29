package mekhq.service.ai;

import javax.swing.JOptionPane;
import mekhq.MekHQ;
import mekhq.campaign.Campaign;
import mekhq.campaign.mission.AtBContract;
import mekhq.campaign.mission.AtBDynamicScenario;
import mekhq.campaign.mission.AtBDynamicScenarioFactory;
import mekhq.campaign.mission.ScenarioMapParameters;
import mekhq.campaign.mission.ScenarioTemplate;
import mekhq.campaign.mission.enums.AtBContractType;
import mekhq.campaign.mission.enums.ScenarioStatus;
import mekhq.campaign.stratCon.StratConCampaignState;
import mekhq.campaign.stratCon.StratConContractDefinition;
import mekhq.campaign.stratCon.StratConContractInitializer;
import mekhq.campaign.stratCon.StratConCoords;
import mekhq.campaign.stratCon.StratConScenario;
import mekhq.campaign.stratCon.StratConScenarioFactory;
import mekhq.campaign.stratCon.StratConTrackState;
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

        try {
            // 1. Create Contract
            String title = (proposal.title != null && !proposal.title.isBlank()) ? proposal.title : "AI Generated Mission";
            AtBContract contract = new AtBContract(title);
            String aiDescription = (proposal.briefing != null ? proposal.briefing : "No briefing provided.") + "\n\n<!-- AI_ARC_MISSION -->";
            contract.setDesc(aiDescription);
            
            // 2. Mission Type Mapping
            AtBContractType type = AtBContractType.GARRISON_DUTY;
            if (proposal.missionType != null) {
                String normalizedType = proposal.missionType.toUpperCase().replace(" ", "_");
                try {
                    type = AtBContractType.valueOf(normalizedType);
                } catch (Exception e) {
                    LOGGER.warn("AIHelper: Unknown mission type '" + proposal.missionType + "', falling back to GARRISON_DUTY");
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
            
            // 5. Planet and System
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
                contract.setSystemId("Unknown System");
            }
            
            // 6. Initialization
            contract.setSalvagePct(50);
            contract.setBattleLossComp(50);
            contract.setTransportComp(50);
            contract.setStraightSupport(50);
            contract.setAdvancePct(25);
            contract.setMRBCFee(true);
            
            contract.initContractDetails(campaign);
            contract.setDesc(aiDescription); 
            contract.setPartsAvailabilityLevel(contract.getContractType().calculatePartsAvailabilityLevel());
            contract.setAtBSharesPercent(0); 
            contract.setStartDate(campaign.getLocalDate());
            contract.calculateContract(campaign);
            
            // 7. Add Mission to Campaign FIRST
            campaign.addMission(contract);

            // 8. Initialize StratCon State (Properly)
            StratConContractDefinition def = StratConContractDefinition.getContractDefinition(type);
            if (def != null) {
                StratConContractInitializer.initializeCampaignState(contract, campaign, def);
                LOGGER.info("AIHelper: StratCon state initialized via contract definition");
            } else {
                LOGGER.warn("AIHelper: No StratCon contract definition found for type: " + type);
            }
            
            // 9. Scenario Creation (using StratCon templates)
            String templateName = getTemplateNameForType(type);
            ScenarioTemplate template = null;
            try {
                template = StratConScenarioFactory.getSpecificScenario(templateName);
            } catch (Exception e) {
                LOGGER.warn("AIHelper: Could not find template " + templateName + ", using fallback");
            }
            
            if (template == null) {
                template = StratConScenarioFactory.getRandomScenario(ScenarioMapParameters.MapLocation.AllGroundTerrain);
            }

            AtBDynamicScenario scenario = AtBDynamicScenarioFactory.initializeScenarioFromTemplate(template, contract, campaign);
            scenario.setName("Opening Engagement: " + title);
            scenario.setDesc(proposal.briefing != null ? proposal.briefing : "");
            scenario.setDate(campaign.getLocalDate());
            scenario.setStatus(ScenarioStatus.CURRENT);
            // 10. Add Scenario to Campaign — this assigns the scenario its permanent ID
            if (contract.getId() == -1) {
                LOGGER.warn("AIHelper: Contract ID not set before adding scenario, forcing assignment");
                campaign.addMission(contract);
            }
            scenario.setMissionId(contract.getId()); // Double check link
            AtBDynamicScenarioFactory.setScenarioMapSize(scenario, campaign);
            
            campaign.addScenario(scenario, contract, true);
            LOGGER.info("AIHelper: Scenario added with ID: " + scenario.getId() + " linked to Mission: " + scenario.getMissionId());
            
            // 11. Link Scenario to StratCon track
            StratConCampaignState state = contract.getStratconCampaignState();
            
            // Ensure we have a valid StratCon state with at least one track
            if (state == null) {
                LOGGER.warn("AIHelper: No StratCon state exists after initialization, creating fallback");
                state = new StratConCampaignState(contract);
                contract.setStratConCampaignState(state);
            }
            
            if (state.getTracks().isEmpty()) {
                LOGGER.warn("AIHelper: StratCon state has no tracks, creating fallback track");
                StratConTrackState fallbackTrack = StratConContractInitializer.initializeTrackState(
                    1, 30, 0, 25);
                fallbackTrack.setDisplayableName("Primary Operation");
                state.addTrack(fallbackTrack);
            }
            
            StratConTrackState track = state.getTracks().get(0);
            LOGGER.info("AIHelper: Using track '" + track.getDisplayableName() 
                + "' (size: " + track.getWidth() + "x" + track.getHeight() + ")");
            
            // Find unoccupied coords for the scenario (don't hardcode 0,0 which may be occupied)
            StratConCoords scenarioCoords = StratConContractInitializer.getUnoccupiedCoords(track);
            if (scenarioCoords == null) {
                // Fallback to (0,0) if no unoccupied coords found
                scenarioCoords = new StratConCoords(0, 0);
                LOGGER.warn("AIHelper: No unoccupied coords found, using (0,0)");
            }
            
            StratConScenario stratConScenario = new StratConScenario();
            stratConScenario.setCoords(scenarioCoords);
            stratConScenario.setBackingScenario(scenario);
            stratConScenario.setActionDate(scenario.getDate());
            stratConScenario.setDeploymentDate(scenario.getDate()); // Essential for StratCon deployment logic
            stratConScenario.setReturnDate(scenario.getDate().plusDays(track.getDeploymentTime())); // Essential for StratCon force return
            stratConScenario.setRequiredPlayerLances(1);
            
            // Use the proper API which also updates the backingScenariosMap
            track.addScenario(stratConScenario);
            
            LOGGER.info("AIHelper: Linked StratConScenario at " + scenarioCoords 
                + " (Backing ID: " + stratConScenario.getBackingScenarioID() 
                + ", Scenario ID: " + scenario.getId() + ") to track '" 
                + track.getDisplayableName() + "'");
            
            // Finalize scenario generation (OpFor, etc.)
            AtBDynamicScenarioFactory.finalizeScenario(scenario, contract, campaign);
            
            LOGGER.info("AIHelper: Successfully added mission and linked StratCon for: " + title);
            
        } catch (Exception e) {
            LOGGER.error("AIHelper: Failed to add AI mission", e);
            JOptionPane.showMessageDialog(null, "Failed to add mission: " + e.getMessage(), "Creation Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String getTemplateNameForType(AtBContractType type) {
        return switch (type) {
            case GARRISON_DUTY, CADRE_DUTY, RELIEF_DUTY -> "Frontline Engagement.xml";
            case SECURITY_DUTY -> "Base Defense.xml";
            case RIOT_DUTY -> "Irregular Force Suppression.xml";
            case PLANETARY_ASSAULT -> "Frontline Breakthrough.xml";
            case GUERRILLA_WARFARE, ESPIONAGE, SABOTAGE -> "Covert Strike.xml";
            case PIRATE_HUNTING -> "Annihilation.xml";
            case DIVERSIONARY_RAID -> "Diversion Engagement.xml";
            case OBJECTIVE_RAID -> "Deep Raid.xml";
            case RECON_RAID -> "Recon Evasion.xml";
            case EXTRACTION_RAID -> "Decoy Engagement.xml";
            case ASSASSINATION -> "Assassination.xml";
            default -> "Frontline Engagement.xml";
        };
    }
}
